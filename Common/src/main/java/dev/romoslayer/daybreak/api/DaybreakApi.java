package dev.romoslayer.daybreak.api;

import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.server.level.ServerLevel;

/**
 * Daybreak's public API, for other mods (call it directly, or by reflection to avoid a hard dependency). Kept stable:
 * methods are only ever added. Everything here is about the server; positions are block x coordinates.
 * <p>
 * In a dimension without regional time every method answers with the world clock, so callers need no special case.
 */
public final class DaybreakApi {
	private DaybreakApi() {
	}

	/** Whether this level's time of day depends on position. */
	public static boolean isRegional(ServerLevel level) {
		return RegionalTime.isRegional(level);
	}

	/** Ticks the local clock at this x is ahead of the world clock (negative: behind). 0 if not regional. */
	public static long offsetTicks(ServerLevel level, double x) {
		return RegionalTime.isRegional(level) ? RegionalTime.offsetTicks(x) : 0L;
	}

	/** The local clock at this x: the world clock plus the offset. Never wraps; its day count continues. */
	public static long localClock(ServerLevel level, double x) {
		return RegionalTime.worldClock(level) + offsetTicks(level, x);
	}

	/**
	 * Local time of day at this x, 0 to 23999 (0 = 6 AM, 6000 = noon, 18000 = midnight), on the evenly running local
	 * clock. With seasonal day length (Seasonfall) the sun is not where this says: use localSolarTimeOfDay for that.
	 */
	public static long localTimeOfDay(ServerLevel level, double x) {
		return RegionalTime.timeOfDay(localClock(level, x));
	}

	/**
	 * The local solar clock at this x: the local clock with seasonal day length applied, so day and night take their
	 * seasonal share of each day. This is what the sky, light, monsters and beds there follow. Equal to localClock when
	 * day and night are the same length; every day still starts on the same tick, so day numbers agree.
	 */
	public static long localSolarClock(ServerLevel level, double x) {
		return RegionalTime.isRegional(level) ? RegionalTime.solarClock(RegionalTime.worldClock(level), x) : RegionalTime.worldClock(level);
	}

	/** Local solar time of day at this x, 0 to 23999 (0 = sunrise at 6 AM, 6000 = noon, 18000 = midnight), as the sky shows it. */
	public static long localSolarTimeOfDay(ServerLevel level, double x) {
		return RegionalTime.timeOfDay(localSolarClock(level, x));
	}

	/** Local day number at this x (moon phases and lunar events follow it). */
	public static long localDay(ServerLevel level, double x) {
		return RegionalTime.day(localClock(level, x));
	}
}
