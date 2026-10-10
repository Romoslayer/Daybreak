package dev.romoslayer.daybreak.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolarTimeTest {
	@Test
	void equalDayAndNightChangesNothing() {
		for (long clock : new long[] {-30000, -1, 0, 6000, 12000, 18000, 23999, 24000, 1_234_567}) {
			assertEquals(clock, SolarTime.solarClock(clock, 0.5));
			assertEquals(1.0, SolarTime.rate(clock, 0.5));
		}
	}

	@Test
	void longSummerDay() {
		// Daytime is 60% of the day: sunset (solar 12000) comes at 14400 ticks, noon (solar 6000) at 7200
		assertEquals(6000, SolarTime.solarClock(7200, 0.6));
		assertEquals(12000, SolarTime.solarClock(14400, 0.6));
		assertEquals(18000, SolarTime.solarClock(14400 + 4800, 0.6), "midnight half way through the shorter night");
		assertEquals(0.5 / 0.6, SolarTime.rate(1000, 0.6), 1e-9, "the sun runs slower by day");
		assertEquals(0.5 / 0.4, SolarTime.rate(20000, 0.6), 1e-9, "and faster by night");
	}

	@Test
	void eachDayStartsAndEndsOnTheSameTickWhateverTheSeason() {
		for (double fraction : new double[] {0.05, 0.3, 0.5, 0.7, 0.95}) {
			for (long day = -3; day <= 3; day++) {
				assertEquals(day * 24000, SolarTime.solarClock(day * 24000, fraction), "day start, fraction " + fraction);
				// A shortened part of the day runs faster than one solar tick per tick, so the last tick may land just short of
				// 23999, but never in the next day
				long lastTick = SolarTime.solarClock(day * 24000 + 23999, fraction);
				assertEquals(day, Math.floorDiv(lastTick, 24000), "last tick of the day stays in that day, fraction " + fraction);
				long fastestStep = (long) Math.ceil(0.5 / Math.min(fraction, 1 - fraction));
				assertTrue(lastTick >= day * 24000 + 23999 - fastestStep, "and ends the day, fraction " + fraction);
			}
		}
	}

	@Test
	void neverRunsBackwardsAndHasNoBigJumps() {
		for (double fraction : new double[] {0.05, 0.35, 0.65, 0.95}) {
			long previous = SolarTime.solarClock(-24000, fraction);
			for (long clock = -23999; clock <= 48000; clock++) {
				long solar = SolarTime.solarClock(clock, fraction);
				long step = solar - previous;
				assertTrue(step >= 0, "backwards at " + clock + " with " + fraction);
				assertTrue(step <= Math.ceil(0.5 / Math.min(fraction, 1 - fraction)) + 1, "jump of " + step + " at " + clock + " with " + fraction);
				previous = solar;
			}
		}
	}

	@Test
	void morningIsSixAmLocal() {
		assertEquals(6000, SolarTime.ticksUntilMorning(18000));
		assertEquals(0, SolarTime.ticksUntilMorning(24000));
		assertEquals(1, SolarTime.ticksUntilMorning(-1));
	}

	@Test
	void fractionIsKeptSane() {
		SolarTime.setDaytimeFraction(2.0);
		assertEquals(SolarTime.MAX_FRACTION, SolarTime.daytimeFraction());
		SolarTime.setDaytimeFraction(Double.NaN);
		assertEquals(SolarTime.EQUAL, SolarTime.daytimeFraction());
		SolarTime.setDaytimeFraction(SolarTime.EQUAL);
	}
}
