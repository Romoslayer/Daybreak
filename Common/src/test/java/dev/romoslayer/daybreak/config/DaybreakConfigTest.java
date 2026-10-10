package dev.romoslayer.daybreak.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class DaybreakConfigTest {
	private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
	private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");

	@Test
	void defaultsAreTheDocumentedOnes() {
		List<String> problems = new ArrayList<>();
		DaybreakConfig config = DaybreakConfig.parse("{}", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertTrue(config.enabled);
		assertEquals(24000.0, config.blocksPerDay);
		assertEquals(DaybreakConfig.Direction.EAST_LATER, config.direction);
		assertEquals(20, config.resyncTicks);
		assertTrue(config.allowSleepSkip && config.seasonalDayLength && config.enhancedCelestials);
		assertTrue(config.isRegional(OVERWORLD), "the overworld is regional by default");
		assertFalse(config.isRegional(NETHER), "the nether is not");
		assertTrue(DaybreakConfig.get().isRegional(OVERWORLD), "the built-in defaults work before any file is loaded");
	}

	@Test
	void invalidValuesFallBackWithAWarning() {
		List<String> problems = new ArrayList<>();
		DaybreakConfig config = DaybreakConfig.parse("""
				{"blocksPerDay": 0, "resyncTicks": 99999, "direction": "NORTH", "dimensions": ["minecraft:overworld", "Not An Id!", "minecraft:overworld"]}
				""", problems);
		assertEquals(24000.0, config.blocksPerDay, "zero would divide by zero");
		assertEquals(DaybreakConfig.MAX_RESYNC_TICKS, config.resyncTicks, "clamped");
		assertEquals(DaybreakConfig.Direction.EAST_LATER, config.direction);
		assertEquals(List.of("minecraft:overworld"), config.dimensions, "bad and duplicate ids dropped");
		assertEquals(4, problems.size(), problems.toString());

		assertEquals(24000.0, DaybreakConfig.parse("{\"blocksPerDay\": -5}", new ArrayList<>()).blocksPerDay, "negative");
		assertEquals(24000.0, DaybreakConfig.parse("{\"blocksPerDay\": 1e12}", new ArrayList<>()).blocksPerDay, "absurdly large");
	}

	@Test
	void sleepSkipCooldown() {
		assertEquals(12000, DaybreakConfig.parse("{}", new ArrayList<>()).sleepSkipCooldownTicks, "10 minutes by default");
		assertEquals(0, DaybreakConfig.parse("{\"sleepSkipCooldownTicks\": 0}", new ArrayList<>()).sleepSkipCooldownTicks, "0 turns it off");
		List<String> problems = new ArrayList<>();
		assertEquals(0, DaybreakConfig.parse("{\"sleepSkipCooldownTicks\": -5}", problems).sleepSkipCooldownTicks, "negative clamped");
		assertEquals(DaybreakConfig.MAX_SKIP_COOLDOWN_TICKS, DaybreakConfig.parse("{\"sleepSkipCooldownTicks\": 99999999}", problems).sleepSkipCooldownTicks);
		assertEquals(2, problems.size(), problems.toString());
	}

	@Test
	void disabledMeansNoRegionalDimension() {
		DaybreakConfig config = DaybreakConfig.parse("{\"enabled\": false}", new ArrayList<>());
		assertFalse(config.isRegional(OVERWORLD));
	}

	@Test
	void writtenFileReadsBackTheSameWithComments() {
		DaybreakConfig original = DaybreakConfig.parse("""
				{"blocksPerDay": 4800.5, "direction": "WEST_LATER", "dimensions": ["minecraft:overworld", "mypack:moonbase"], "debug": true}
				""", new ArrayList<>());
		String text = original.serialize();
		assertTrue(text.contains("// Daybreak runs entirely on the server"), "explanations are written");
		List<String> problems = new ArrayList<>();
		DaybreakConfig back = DaybreakConfig.parse(text, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(original.blocksPerDay, back.blocksPerDay);
		assertEquals(original.direction, back.direction);
		assertEquals(original.dimensions, back.dimensions);
		assertEquals(original.debug, back.debug);
		assertTrue(back.isRegional(Identifier.fromNamespaceAndPath("mypack", "moonbase")));
		assertEquals(text, back.serialize(), "stable when rewritten");
	}
}
