package dev.romoslayer.daybreak.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RegionalTimeTest {
	@AfterEach
	void restoreDefaults() {
		DaybreakConfig.set(DaybreakConfig.parse("{}", new ArrayList<>()));
	}

	@Test
	void offsetMatchesTheDocumentedExamples() {
		// 24000 blocks per day, with x = 0 at noon (6000)
		long noon = 6000;
		assertEquals(0, RegionalTime.timeOfDay(noon + RegionalTime.offsetTicks(-6000, 24000, true)), "6 AM at x = -6000");
		assertEquals(6000, RegionalTime.timeOfDay(noon + RegionalTime.offsetTicks(0, 24000, true)), "noon at x = 0");
		assertEquals(9000, RegionalTime.timeOfDay(noon + RegionalTime.offsetTicks(3000, 24000, true)), "3 PM at x = 3000");
		assertEquals(12000, RegionalTime.timeOfDay(noon + RegionalTime.offsetTicks(6000, 24000, true)), "6 PM at x = 6000");
		assertEquals(18000, RegionalTime.timeOfDay(noon + RegionalTime.offsetTicks(12000, 24000, true)), "midnight at x = 12000");
	}

	@Test
	void directionAndScale() {
		assertEquals(-6000, RegionalTime.offsetTicks(6000, 24000, false), "west later flips the sign");
		assertEquals(24000, RegionalTime.offsetTicks(1000, 1000, true), "1000 blocks per day: a day per 1000 blocks");
		assertEquals(1, RegionalTime.offsetTicks(0.6, 24000, true), "rounded to whole ticks");
	}

	@Test
	void continuousAcrossZeroAndNegative() {
		// No jumps anywhere: neighbouring blocks are at most one tick apart (default scale), on both sides of zero
		for (int x = -50; x < 50; x++) {
			long step = RegionalTime.offsetTicks(x + 1, 24000, true) - RegionalTime.offsetTicks(x, 24000, true);
			assertEquals(1, step, "step at x = " + x);
		}
		assertEquals(23999, RegionalTime.timeOfDay(-1), "time of day wraps the right way below zero");
		assertEquals(-1, RegionalTime.day(-1), "day number rounds down below zero");
	}

	@Test
	void worldBorderCoordinatesDoNotOverflow() {
		double border = 29_999_984;
		long offset = RegionalTime.offsetTicks(border, DaybreakConfig.MIN_BLOCKS_PER_DAY, true);
		assertEquals(Math.round(border * 24000), offset, "smallest scale at the world border is still exact");
		assertTrue(offset > 0 && RegionalTime.offsetTicks(-border, DaybreakConfig.MIN_BLOCKS_PER_DAY, true) < 0);
	}

	@Test
	void clientClockIsNeverNegativeAndKeepsTimeAndMoonPhase() {
		for (long clock : new long[] {-1, -23999, -24000, -191999, -192000, -1_000_000_007L, 0, 5, 1_000_000}) {
			long sent = RegionalTime.clientClock(clock);
			assertTrue(sent >= 0, "non-negative for " + clock);
			assertEquals(Math.floorMod(clock, RegionalTime.MOON_CYCLE_TICKS), Math.floorMod(sent, RegionalTime.MOON_CYCLE_TICKS),
					"same time of day and moon phase for " + clock);
			assertTrue(sent < Math.max(clock, 0) + RegionalTime.MOON_CYCLE_TICKS, "moved by less than a cycle past zero for " + clock);
		}
		assertEquals(1_000_000, RegionalTime.clientClock(1_000_000), "positive clocks are sent unchanged");
	}

	@Test
	void clientClockFitsIn32Bits() {
		// The furthest a clock can get: blocksPerDay 1 at the world border (x = 29 999 984) gives 719 999 616 000 ticks
		long border = RegionalTime.offsetTicks(29_999_984, 1, true);
		for (long clock : new long[] {RegionalTime.CLIENT_CLOCK_LIMIT, RegionalTime.CLIENT_CLOCK_LIMIT + 1, RegionalTime.CLIENT_CLOCK_LIMIT + 191_999,
				RegionalTime.CLIENT_CLOCK_LIMIT + 192_000, border, -border, Long.MAX_VALUE / 2, -(Long.MAX_VALUE / 2)}) {
			long sent = RegionalTime.clientClock(clock);
			assertTrue(sent >= 0 && sent <= Integer.MAX_VALUE, "fits a signed 32-bit number for " + clock + ": " + sent);
			assertEquals(Math.floorMod(clock, RegionalTime.MOON_CYCLE_TICKS), Math.floorMod(sent, RegionalTime.MOON_CYCLE_TICKS),
					"same time of day and moon phase for " + clock);
		}
		assertEquals(RegionalTime.CLIENT_CLOCK_LIMIT, RegionalTime.clientClock(RegionalTime.CLIENT_CLOCK_LIMIT), "the limit itself is sent unchanged");
		assertTrue(RegionalTime.clientClock(RegionalTime.CLIENT_CLOCK_LIMIT + 1) > RegionalTime.CLIENT_CLOCK_LIMIT - RegionalTime.MOON_CYCLE_TICKS,
				"moved down by just one cycle past the limit");
	}

	@Test
	void usesTheConfiguredSettings() {
		DaybreakConfig.set(DaybreakConfig.parse("{\"blocksPerDay\": 12000, \"direction\": \"WEST_LATER\"}", new ArrayList<>()));
		assertEquals(-12000, RegionalTime.offsetTicks(6000));
		assertEquals(100 - 12000, RegionalTime.localClock(100, 6000));
	}
}
