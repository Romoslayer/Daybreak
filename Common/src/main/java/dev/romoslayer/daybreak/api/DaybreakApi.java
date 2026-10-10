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

	/** Local time of day at this x, 0 to 23999 (0 = 6 AM, 6000 = noon, 18000 = midnight). */
	public static long localTimeOfDay(ServerLevel level, double x) {
		return RegionalTime.timeOfDay(localClock(level, x));
	}

	/** Local day number at this x (moon phases and lunar events follow it). */
	public static long localDay(ServerLevel level, double x) {
		return RegionalTime.day(localClock(level, x));
	}
}
