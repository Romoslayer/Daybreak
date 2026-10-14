package dev.romoslayer.daybreak.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaybreakConfigTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

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
	void oneBadOptionKeepsTheOthers() {
		List<String> problems = new ArrayList<>();
		DaybreakConfig config = DaybreakConfig.parse("""
				{"blocksPerDay": "far", "resyncTicks": 40, "enabled": "yes", "direction": "WEST_LATER", "sleepSkipCooldownTicks": [1]}
				""", problems);
		assertEquals(24000.0, config.blocksPerDay, "a word is not a number");
		assertTrue(config.enabled, "\"yes\" is not true or false (Gson alone would read it as false)");
		assertEquals(12000, config.sleepSkipCooldownTicks, "a list is not a number");
		assertEquals(40, config.resyncTicks, "the good options around them are kept");
		assertEquals(DaybreakConfig.Direction.WEST_LATER, config.direction);
		assertEquals(3, problems.size(), problems.toString());
	}

	@Test
	void unknownOptionsAndOtherShapesAreReported() {
		List<String> problems = new ArrayList<>();
		DaybreakConfig config = DaybreakConfig.parse("{\"blocksPerDya\": 5}", problems);
		assertEquals(24000.0, config.blocksPerDay);
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0).contains("blocksPerDya"), problems.toString());

		problems.clear();
		assertTrue(DaybreakConfig.parse("[1, 2]", problems).enabled, "not an object: the defaults");
		assertEquals(1, problems.size(), problems.toString());
		assertThrows(JsonParseException.class, () -> DaybreakConfig.parse("{ this is not json", new ArrayList<>()), "not JSON at all");
	}

	@Test
	void setChecksTheConfigLikeALoadedOne() {
		try {
			DaybreakConfig config = new DaybreakConfig();
			config.dimensions = new ArrayList<>(List.of("overworld", "Not An Id!"));
			config.resyncTicks = 0;
			DaybreakConfig.set(config);
			assertTrue(DaybreakConfig.get().isRegional(OVERWORLD), "a bare path is a minecraft: id");
			assertEquals(List.of(OVERWORLD), DaybreakConfig.get().dimensions);
			assertEquals(DaybreakConfig.MIN_RESYNC_TICKS, DaybreakConfig.get().resyncTicks);
		} finally {
			DaybreakConfig.set(new DaybreakConfig());
		}
	}

	@Test
	void reloadKeepsTheLastGoodSettings(@TempDir Path dir) throws IOException {
		try {
			DaybreakConfig.load(dir);
			Path file = dir.resolve("daybreak.json");
			assertTrue(Files.isRegularFile(file), "written on the first load");
			assertFalse(Files.exists(dir.resolve("daybreak.json.tmp")), "no temporary file left behind");
			Files.writeString(file, Files.readString(file).replace("\"blocksPerDay\": 24000.0", "\"blocksPerDay\": 4800.0"));
			int before = DaybreakConfig.generation();
			assertNull(DaybreakConfig.reload());
			assertEquals(4800.0, DaybreakConfig.get().blocksPerDay);
			int reloaded = DaybreakConfig.generation();
			assertTrue(reloaded != before, "a reload is a new generation (per-level answers are worked out again)");

			Files.writeString(file, "{ this is not json");
			assertNotNull(DaybreakConfig.reload(), "the problem is reported");
			assertEquals(4800.0, DaybreakConfig.get().blocksPerDay, "the last good settings stay");
			assertEquals(reloaded, DaybreakConfig.generation(), "a failed reload changes nothing");
			assertEquals("{ this is not json", Files.readString(file), "a file that cannot be read is not overwritten");
		} finally {
			DaybreakConfig.set(new DaybreakConfig());
		}
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
		assertTrue(back.isRegional("mypack:moonbase"));
		assertEquals(text, back.serialize(), "stable when rewritten");
	}
}
