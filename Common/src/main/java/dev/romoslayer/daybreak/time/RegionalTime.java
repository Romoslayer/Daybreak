package dev.romoslayer.daybreak.time;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.mc.Versioned;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The time model, in one place. There is one world clock (vanilla's, never rewritten by Daybreak); a position's local
 * clock is that clock plus an offset that depends only on how far east or west the position is.
 * <ul>
 * <li>offset: {@code x * 24000 / blocksPerDay} ticks, later towards the east (or west, if configured)</li>
 * <li>local clock: world clock + offset; continuous and unbounded, so far enough east is a whole day ahead</li>
 * <li>local time of day: local clock modulo 24000; local day: local clock divided by 24000, rounded down</li>
 * </ul>
 * Everything else asks here; nothing computes regional time on its own.
 */
public final class RegionalTime {
	public static final long DAY_TICKS = 24000L;
	/** One full moon cycle (8 days): adding it changes neither the time of day nor the moon phase. */
	public static final long MOON_CYCLE_TICKS = 8L * DAY_TICKS;
	/** The largest clock sent to clients: the largest signed 32-bit number. */
	public static final long CLIENT_CLOCK_LIMIT = Integer.MAX_VALUE;
	/** Dimension ids as text, made once per dimension: isRegional is asked on every light read. */
	private static final Map<ResourceKey<Level>, String> IDS = new ConcurrentHashMap<>();

	private RegionalTime() {
	}

	/** Ticks added to the world clock at this x for the given settings. */
	public static long offsetTicks(double x, double blocksPerDay, boolean eastLater) {
		long offset = Math.round(x * DAY_TICKS / blocksPerDay);
		return eastLater ? offset : -offset;
	}

	/** Ticks added to the world clock at this x, with the current settings. */
	public static long offsetTicks(double x) {
		DaybreakConfig config = DaybreakConfig.get();
		return offsetTicks(x, config.blocksPerDay, config.direction == DaybreakConfig.Direction.EAST_LATER);
	}

	public static long localClock(long worldClock, double x) {
		return worldClock + offsetTicks(x);
	}

	/** The local solar clock at this x: the local clock stretched by the seasonal day length (see SolarTime). */
	public static long solarClock(long worldClock, double x) {
		return SolarTime.solarClock(localClock(worldClock, x));
	}

	public static long timeOfDay(long clock) {
		return Math.floorMod(clock, DAY_TICKS);
	}

	public static long day(long clock) {
		return Math.floorDiv(clock, DAY_TICKS);
	}

	/**
	 * The clock value to send a client: the same time of day and moon phase, moved by whole moon cycles if needed so it
	 * stays from 0 to CLIENT_CLOCK_LIMIT (Bedrock players through Geyser get the time as a 32-bit number). Clocks in that
	 * range, which is everything up to about 89 000 days, are sent unchanged.
	 */
	public static long clientClock(long localClock) {
		if (localClock < 0) {
			long cycles = -Math.floorDiv(localClock, MOON_CYCLE_TICKS);
			return localClock + cycles * MOON_CYCLE_TICKS;
		}
		if (localClock > CLIENT_CLOCK_LIMIT) {
			long cycles = Math.floorDiv(localClock - CLIENT_CLOCK_LIMIT - 1, MOON_CYCLE_TICKS) + 1;
			return localClock - cycles * MOON_CYCLE_TICKS;
		}
		return localClock;
	}

	/** Whether this level has regional time: a server level of a configured dimension, with Daybreak enabled. */
	public static boolean isRegional(@Nullable Level level) {
		return level instanceof ServerLevel serverLevel && state(serverLevel).regional();
	}

	/** Whether this server level's dimension is configured for regional time, even while Daybreak is disabled. */
	public static boolean isListed(@Nullable Level level) {
		return level instanceof ServerLevel serverLevel && state(serverLevel).listed();
	}

	/**
	 * Added to Level (LevelMixin): its answers to isRegional and isListed, kept with the config generation they were
	 * worked out for. They are asked on every light read and context, and looking the dimension up each time showed in
	 * profiles.
	 */
	public interface Cached {
		@Nullable State daybreak$regionalState();

		void daybreak$setRegionalState(State state);
	}

	public record State(int generation, boolean regional, boolean listed) {
	}

	private static State state(ServerLevel level) {
		int generation = DaybreakConfig.generation();
		Cached cached = (Cached) level;
		State state = cached.daybreak$regionalState();
		if (state == null || state.generation() != generation) {
			DaybreakConfig config = DaybreakConfig.get();
			String id = id(level);
			state = new State(generation, config.isRegional(id), config.isListed(id));
			cached.daybreak$setRegionalState(state);
		}
		return state;
	}

	/** The level's world clock: the time of day vanilla runs on, never shifted by Daybreak. */
	public static long worldClock(ServerLevel level) {
		return Versioned.worldClock(level);
	}

	private static String id(ServerLevel level) {
		return IDS.computeIfAbsent(level.dimension(), Versioned::id);
	}
}
