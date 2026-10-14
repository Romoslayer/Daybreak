package dev.romoslayer.daybreak.compat.ec2;

import dev.corgitaco.enhancedcelestials2core.EnhancedCelestials;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarEvent;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.compat.ec2.mixin.LunarForecastInvoker;
import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Enhanced Celestials 2. EC2 rolls its lunar events once per dimension, a year at a time, and ties each to a night
 * number on the clock. Daybreak leaves that schedule alone and only changes when EC2 looks it up: at the local time of
 * the position being asked about. So every place gets night N's event during its own night N, and a blood moon sweeps
 * across the world from east to west. Announcements go to each player when their own night's event starts.
 * <p>
 * Optional and fail-safe: the mixins only load with EC2 installed, and if they did not take hold (an EC2 update changed
 * what they hook) this module stays off and EC2 keeps working the way it does on its own.
 */
public final class Ec2Compat {
	private static final String FORECAST_CLASS = "dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast";
	private static boolean active;
	/** Whether EC2 can be asked to keep the years of far-away places (LunarForecastYearsMixin applied). */
	private static boolean years;
	/** The event each player was last told about. */
	private static final Map<UUID, Holder<LunarEvent>> TOLD = new HashMap<>();

	private Ec2Compat() {
	}

	/** Called by Ec2Bootstrap when EC2 is installed. */
	static void init() {
		active = hooked();
		DaybreakCommands.addInfo(() -> "Enhanced Celestials: " + (!active ? "installed, but Daybreak's hooks did not apply (lunar events stay global)"
				: enabled() ? "regional lunar events" : "installed, regional lunar events switched off in the config"));
		if (!active) {
			Daybreak.LOGGER.warn("Enhanced Celestials is installed but Daybreak could not hook into this version of it; its lunar events stay global");
			return;
		}
		Daybreak.LOGGER.info("Enhanced Celestials found: lunar events follow each place's own night");
		years = Boolean.getBoolean(Ec2MixinPlugin.APPLIED_PREFIX + "dev.romoslayer.daybreak.compat.ec2.mixin.LunarForecastYearsMixin");
		if (!years) {
			Daybreak.LOGGER.warn("Daybreak could not hook Enhanced Celestials' yearly schedule: places a whole lunar year ahead of or behind the world get no lunar events");
		}
		Daybreak.addTicker(Ec2Compat::tick);
		SleepRules.addBlocker(Ec2Compat::sleepBlocker);
		DaybreakCommands.addTimeExtra(Ec2Compat::describe);
	}

	/** For /daybreak time: the lunar event at this x right now (null when there is none). */
	private static String describe(ServerLevel level, double x) {
		if (!enabled()) {
			return null;
		}
		Holder<LunarEvent> event = at(level, new Vec3(x, 64, 0), LunarForecast::currentLunarEventHolder, null);
		String key = event == null ? null : event.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		return key == null || key.endsWith(":default") ? "No lunar event." : "Lunar event: " + key + ".";
	}

	/**
	 * Whether the hooks this module needs were applied in full. Loading EC2's forecast class applies them; the mixin
	 * plugin records each mixin that was applied completely (a failed one can leave part of itself behind, such as the
	 * interfaces it adds, so looking at the class proves nothing).
	 */
	private static boolean hooked() {
		try {
			Class.forName(FORECAST_CLASS, false, Ec2Compat.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
		String mixins = "dev.romoslayer.daybreak.compat.ec2.mixin.";
		return Boolean.getBoolean(Ec2MixinPlugin.APPLIED_PREFIX + mixins + "LunarForecastMixin")
				&& Boolean.getBoolean(Ec2MixinPlugin.APPLIED_PREFIX + mixins + "LunarForecastInvoker");
	}

	public static boolean enabled() {
		return active && DaybreakConfig.get().enhancedCelestials;
	}

	/** The clock EC2 should read: the local solar clock at the current context position, or the world clock. */
	public static long clock(Level level, long worldClock) {
		if (!enabled() || !RegionalTime.isRegional(level)) {
			return worldClock;
		}
		Vec3 pos = RegionalContext.get(level);
		return pos == null ? worldClock : RegionalTime.solarClock(worldClock, pos.x);
	}

	private static Optional<LunarForecast> forecast(Level level) {
		return EnhancedCelestials.lunarForecastWorldData(level);
	}

	private static <T> T at(ServerLevel level, Vec3 pos, Function<LunarForecast, T> query, T fallback) {
		return forecast(level).map(forecast -> RegionalContext.with(level, pos, () -> query.apply(forecast))).orElse(fallback);
	}

	private static String sleepBlocker(ServerPlayer player) {
		if (!enabled()) {
			return null;
		}
		boolean blocked = forecast(player.level()).map(forecast -> forecast.currentLunarEvent().blockSleeping(player)).orElse(false);
		return blocked ? "a lunar event is keeping some players awake" : null;
	}

	/** EC2 announces event changes to the whole dimension at once; here each player hears it at their own night. */
	private static void tick(MinecraftServer server) {
		if (years) {
			keepPlayersYears(server);
		}
		if (!enabled()) {
			TOLD.clear();
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (!RegionalTime.isRegional(level)) {
				TOLD.remove(player.getUUID());
				continue;
			}
			Holder<LunarEvent> now = at(level, player.position(), LunarForecast::currentLunarEventHolder, null);
			if (now == null) {
				continue;
			}
			Holder<LunarEvent> before = TOLD.put(player.getUUID(), now);
			if (before != null && !before.equals(now)) {
				before.value().endNotification().ifPresent(n -> LunarForecastInvoker.daybreak$send(player, n));
				now.value().startNotification().ifPresent(n -> LunarForecastInvoker.daybreak$send(player, n));
			}
		}
		if (server.getTickCount() % 200 == 0) {
			TOLD.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/**
	 * EC2 rolls and keeps the events of the years around the world clock's year only. Each regional dimension's forecast
	 * also keeps the years around every player's own local year (none once regional events are switched off).
	 */
	private static void keepPlayersYears(MinecraftServer server) {
		boolean enabled = enabled();
		for (ServerLevel level : server.getAllLevels()) {
			if (!RegionalTime.isRegional(level)) {
				continue;
			}
			forecast(level).ifPresent(forecast -> {
				Ec2Years kept = (Ec2Years) forecast;
				long yearTicks = kept.daybreak$yearTicks();
				LongOpenHashSet needed = new LongOpenHashSet();
				if (enabled && yearTicks > 0) {
					long world = RegionalTime.worldClock(level);
					for (ServerPlayer player : level.players()) {
						long year = Math.floorDiv(RegionalTime.solarClock(world, player.getX()), yearTicks);
						needed.add(year - 1);
						needed.add(year);
						needed.add(year + 1);
					}
				}
				kept.daybreak$keepYears(needed);
			});
		}
	}
}
