package dev.romoslayer.daybreak.sleep;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.mc.Versioned;
import dev.romoslayer.daybreak.time.RegionalTime;
import dev.romoslayer.daybreak.time.SolarTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Sleeping with regional time. Beds work wherever it is night (or storming) for the player.
 * The night is skipped only when:
 * <ul>
 * <li>skipping is allowed in the config,</li>
 * <li>vanilla's own conditions hold (playersSleepingPercentage, everyone asleep long enough),</li>
 * <li>every player in the dimension is somewhere they could sleep (local night, or a storm overhead),</li>
 * <li>and nothing blocks it (an Enhanced Celestials blood moon over any of them).</li>
 * </ul>
 * The clock then moves forward until the sleeper furthest from morning reaches 6 AM; everyone moves by the same amount.
 * If any sleeper could only sleep because of a storm (local daytime), the storm is cleared and everyone wakes, but the
 * clock does not move.
 */
public final class SleepRules {
	/** Vanilla's clear-weather sleeping window (local time of day), used to tell a real night from a storm. */
	public static final long NIGHT_START = 12542L;
	public static final long NIGHT_END = 23460L;
	private static final int NOTE_INTERVAL = 100;
	/** The last "can't skip" note per level: sent again every few seconds, or at once when the reason changes. */
	private static final Map<ServerLevel, Note> NOTES = new WeakHashMap<>();
	/** Game time (real ticks, which skips do not move) of the last night skip, per loaded level (a new world starts afresh). */
	private static final Map<ServerLevel, Long> LAST_SKIP = new WeakHashMap<>();

	/** Things that keep anyone from skipping, with the reason shown to sleepers (Enhanced Celestials adds one). */
	private static final List<Function<ServerPlayer, @Nullable String>> BLOCKERS = new ArrayList<>();

	private record Note(long time, String reason) {
	}

	private SleepRules() {
	}

	public static void addBlocker(Function<ServerPlayer, @Nullable String> blocker) {
		BLOCKERS.add(blocker);
	}

	/** Vanilla says enough are sleeping: may the night actually end? Explains to the sleepers when it may not. */
	public static boolean mayFinishSleeping(ServerLevel level) {
		if (!RegionalTime.isRegional(level)) {
			return true;
		}
		String reason = blockingReason(level);
		long now = level.getGameTime();
		Note last = NOTES.get(level);
		if (reason == null) {
			NOTES.remove(level);
		} else if (last == null || !last.reason().equals(reason) || now - last.time() >= NOTE_INTERVAL || now < last.time()) {
			// Right away when the skip is first held back or the reason changes, then every few seconds while it stays so
			if (last == null || !last.reason().equals(reason)) {
				Daybreak.debug("{}: enough players asleep, but the night can't be skipped: {}", level.dimension(), reason);
			}
			NOTES.put(level, new Note(now, reason));
			Component note = note(reason);
			for (ServerPlayer player : level.players()) {
				if (player.isSleeping()) {
					Versioned.overlay(player, note);
				}
			}
		}
		return reason == null;
	}

	/** Why the night could not be skipped right now, or null if it could (always null outside regional dimensions). */
	public static @Nullable String reasonIfBlocked(ServerLevel level) {
		return RegionalTime.isRegional(level) ? blockingReason(level) : null;
	}

	public static Component note(String reason) {
		return Component.literal("The night can't be skipped: " + reason);
	}

	private static @Nullable String blockingReason(ServerLevel level) {
		if (!DaybreakConfig.get().allowSleepSkip) {
			return "this server doesn't skip nights";
		}
		int cooldown = DaybreakConfig.get().sleepSkipCooldownTicks;
		Long last = LAST_SKIP.get(level);
		long now = level.getGameTime();
		// Only skips that move the clock count: sleeping out a daytime storm moves nothing, so it is never held back
		if (cooldown > 0 && last != null && now >= last && now - last < cooldown && skipTicks(level) > 0) {
			long minutes = -Math.floorDiv(now - last - cooldown, 1200L);
			return "the last one was skipped too recently (about " + minutes + (minutes == 1 ? " more minute)" : " more minutes)");
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (!Versioned.couldSleepHere(level, player)) {
				return "some players are in daylight";
			}
			for (Function<ServerPlayer, String> blocker : BLOCKERS) {
				String reason = RegionalContext.with(level, player.position(), () -> blocker.apply(player));
				if (reason != null) {
					return reason;
				}
			}
		}
		return null;
	}

	/**
	 * The night ends: how many ticks the clock moves, until the sleeper furthest from morning reaches local 6 AM, or
	 * 0 when any sleeper is only sleeping because of a storm. A skip that moves the clock starts the skip cooldown.
	 * Where the loader's sleep event may still change the time, use skipTicks, then skipped once the clock has moved.
	 */
	public static long endNight(ServerLevel level) {
		long skip = skipTicks(level);
		if (skip > 0) {
			skipped(level);
		}
		return skip;
	}

	/** The clock has moved to end a night: starts the skip cooldown. */
	public static void skipped(ServerLevel level) {
		LAST_SKIP.put(level, level.getGameTime());
		Daybreak.debug("{}: night skipped; the next skip can come after {} ticks", level.dimension(), DaybreakConfig.get().sleepSkipCooldownTicks);
	}

	/** How many ticks the clock should move to end the night (see endNight), without starting the cooldown. */
	public static long skipTicks(ServerLevel level) {
		long world = RegionalTime.worldClock(level);
		long skip = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSleeping()) {
				continue;
			}
			long local = RegionalTime.localClock(world, player.getX());
			long timeOfDay = RegionalTime.timeOfDay(SolarTime.solarClock(local));
			if (timeOfDay < NIGHT_START || timeOfDay >= NIGHT_END) {
				return 0;
			}
			skip = Math.max(skip, SolarTime.ticksUntilMorning(local));
		}
		return skip;
	}
}
