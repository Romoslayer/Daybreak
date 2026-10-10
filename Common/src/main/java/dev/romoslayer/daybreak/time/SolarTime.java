package dev.romoslayer.daybreak.time;

/**
 * Seasonal day length, applied per place. The local clock runs evenly; solar time is the local clock stretched so that
 * daytime (solar 0 to 12000) takes {@code daytimeFraction} of each 24000-tick day and the night the rest. Each day
 * still starts and ends on the same tick, so day numbers, offsets and everything counting whole days are unchanged.
 * With a fraction of 0.5 (no seasonal day length) solar time is the local clock.
 */
public final class SolarTime {
	public static final double EQUAL = 0.5;
	/** Never let day or night shrink to nothing. */
	public static final double MIN_FRACTION = 0.05;
	public static final double MAX_FRACTION = 0.95;
	private static final long DAY = RegionalTime.DAY_TICKS;
	private static final long HALF = DAY / 2;

	private static volatile double daytimeFraction = EQUAL;

	private SolarTime() {
	}

	/** Set once per tick from Seasonfall (or reset to 0.5). */
	public static void setDaytimeFraction(double fraction) {
		daytimeFraction = Double.isFinite(fraction) ? Math.max(MIN_FRACTION, Math.min(MAX_FRACTION, fraction)) : EQUAL;
	}

	public static double daytimeFraction() {
		return daytimeFraction;
	}

	public static long solarClock(long localClock) {
		return solarClock(localClock, daytimeFraction);
	}

	public static long solarClock(long localClock, double fraction) {
		if (fraction == EQUAL) {
			return localClock;
		}
		long day = Math.floorDiv(localClock, DAY);
		long t = Math.floorMod(localClock, DAY);
		double sunset = DAY * fraction;
		double solar = t < sunset ? t * HALF / sunset : HALF + (t - sunset) * HALF / (DAY - sunset);
		return day * DAY + Math.min(DAY - 1, (long) Math.floor(solar));
	}

	/** How fast solar time runs against the local clock at this moment (1 when the day length is equal). */
	public static double rate(long localClock) {
		return rate(localClock, daytimeFraction);
	}

	public static double rate(long localClock, double fraction) {
		if (fraction == EQUAL) {
			return 1.0;
		}
		long t = Math.floorMod(localClock, DAY);
		return t < DAY * fraction ? 0.5 / fraction : 0.5 / (1.0 - fraction);
	}

	/** Local-clock ticks from {@code localClock} until the next local 6 AM (solar 0 and local 0 coincide). */
	public static long ticksUntilMorning(long localClock) {
		long t = Math.floorMod(localClock, DAY);
		return t == 0 ? 0 : DAY - t;
	}
}
