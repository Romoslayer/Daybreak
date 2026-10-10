package dev.romoslayer.daybreak.time;

import dev.romoslayer.daybreak.config.DaybreakConfig;
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
	 * The clock value to send a client: the same time of day and moon phase, moved up by whole moon cycles if needed
	 * so it is never negative (Bedrock players through Geyser get the time as a 32-bit number).
	 */
	public static long clientClock(long localClock) {
		if (localClock >= 0) {
			return localClock;
		}
		long cycles = Math.ceilDiv(-localClock, MOON_CYCLE_TICKS);
		return localClock + cycles * MOON_CYCLE_TICKS;
	}

	/** Whether this level has regional time: a server level of a configured dimension, with Daybreak enabled. */
	public static boolean isRegional(@Nullable Level level) {
		return level instanceof ServerLevel serverLevel && DaybreakConfig.get().isRegional(serverLevel.dimension().identifier());
	}

	/** Whether this server level's dimension is configured for regional time, even while Daybreak is disabled. */
	public static boolean isListed(@Nullable Level level) {
		return level instanceof ServerLevel serverLevel && DaybreakConfig.get().isListed(serverLevel.dimension().identifier());
	}

	/** The level's world clock (its default clock's total ticks), or 0 for a dimension without a clock. */
	public static long worldClock(ServerLevel level) {
		return level.getDefaultClockTime();
	}
}
