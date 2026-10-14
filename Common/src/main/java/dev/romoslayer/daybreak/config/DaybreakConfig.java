package dev.romoslayer.daybreak.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.romoslayer.daybreak.mc.Versioned;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/daybreak.json. Each option is read on its own: anything missing, of the wrong type or out of range falls back
 * to its default (with a warning) without affecting the other options, and unknown options are reported. Only a file
 * that is not JSON at all is rejected as a whole (the last good settings stay). The file is rewritten on every load, in
 * one step, so that options added by an update appear in it with their explanations.
 */
public final class DaybreakConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("Daybreak");
	private static final String FILE_NAME = "daybreak.json";
	public static final double MIN_BLOCKS_PER_DAY = 1.0;
	/** Larger than any world is wide, so the offset can still be noticed somewhere. */
	public static final double MAX_BLOCKS_PER_DAY = 1.0E9;
	public static final int MIN_RESYNC_TICKS = 1;
	public static final int MAX_RESYNC_TICKS = 1200;
	/** Ten in-game days. */
	public static final int MAX_SKIP_COOLDOWN_TICKS = 240000;

	public enum Direction {
		EAST_LATER,
		WEST_LATER
	}

	private static volatile DaybreakConfig instance = new DaybreakConfig().validated(new ArrayList<>());
	/** Counts every change of the live config, so values worked out from it can tell when to work them out again. */
	private static volatile int generation;
	private static @Nullable Path file;

	@Comment("Daybreak runs entirely on the server: players join with a normal, unmodded game. false = vanilla time everywhere.")
	public boolean enabled = true;

	@Comment("Dimensions where the time of day depends on position (dimension ids). Changing this needs a server restart.")
	public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));

	@Comment("""
			How many blocks east or west make one full day of difference. 24000: walking 6000 blocks east moves the time six
			hours later. Smaller numbers make the time change faster with distance; below a few thousand, players can see
			distant mobs follow a noticeably different time than their own sky. At least 1.""")
	public double blocksPerDay = 24000.0;

	@Comment("EAST_LATER: going east makes it later (like the real sun). WEST_LATER: the other way round.")
	public Direction direction = Direction.EAST_LATER;

	@Comment("""
			Whether sleeping can skip the night. The night is only skipped when every player in the dimension is in local night
			(and enough of them sleep, see the playersSleepingPercentage game rule). false: beds still work but never skip.""")
	public boolean allowSleepSkip = true;

	@Comment("""
			After a night is skipped, how many ticks of normal time must pass before another skip (20 ticks = 1 second). Without
			it, a player could skip, travel west to where it is night again and skip again, racing the world clock (and seasons
			and lunar events) ahead. 12000 (10 minutes) is about the wait vanilla has between nights. 0 = no cooldown.""")
	public int sleepSkipCooldownTicks = 12000;

	@Comment("How far (in ticks of time) a player's sky may drift before they are sent their local time again (1-1200).")
	public int resyncTicks = 20;

	@Comment("With Seasonfall installed: give every place its own seasonally longer or shorter day.")
	public boolean seasonalDayLength = true;

	@Comment("With Enhanced Celestials installed: each place gets the night's lunar event during its own night.")
	public boolean enhancedCelestials = true;

	@Comment("Extra logging for finding problems: why nights are or are not skipped, and village siege rolls.")
	public boolean debug = false;

	private transient Set<String> regionalDimensions = Set.of();

	public static DaybreakConfig get() {
		return instance;
	}

	/** Changes whenever the live config does (read it before get(), so a change in between is never missed). */
	public static int generation() {
		return generation;
	}

	private static void use(DaybreakConfig config) {
		instance = config;
		generation++;
	}

	/** For tests and for loaders that load the config themselves. The config is checked first, as a loaded one is. */
	public static void set(DaybreakConfig config) {
		List<String> problems = new ArrayList<>();
		use(config.validated(problems));
		problems.forEach(problem -> LOGGER.warn("Daybreak config: {}", problem));
	}

	/** The dimensions with regional time right now (a changed list in the file waits for a restart). */
	public Set<String> activeDimensions() {
		return this.regionalDimensions;
	}

	public boolean isRegional(String dimension) {
		return this.enabled && this.regionalDimensions.contains(dimension);
	}

	/** Whether the dimension is listed, whether or not Daybreak is currently enabled. */
	public boolean isListed(String dimension) {
		return this.regionalDimensions.contains(dimension);
	}

	public static void load(Path configDir) {
		file = configDir.resolve(FILE_NAME);
		try {
			use(read(file));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not read {}, using the defaults until it is fixed: {}", file, e.getMessage());
			use(new DaybreakConfig().validated(new ArrayList<>()));
			return;
		}
		write(file, instance);
	}

	/** Re-reads the file. The current settings stay if it cannot be read. Returns an error message, or null. */
	public static @Nullable String reload() {
		if (file == null) {
			return "The config was never loaded";
		}
		DaybreakConfig candidate;
		try {
			candidate = read(file);
		} catch (IOException | RuntimeException e) {
			return e.getMessage();
		}
		if (!candidate.regionalDimensions.equals(instance.regionalDimensions)) {
			// Timelines are set up when a world loads, so a changed list only takes effect after a restart; until then the
			// running list stays active everywhere (the file keeps the new one)
			LOGGER.warn("{}: the changed dimensions list takes effect after a restart", FILE_NAME);
			candidate.regionalDimensions = instance.regionalDimensions;
		}
		use(candidate);
		write(file, candidate);
		return null;
	}

	private static DaybreakConfig read(Path path) throws IOException {
		List<String> problems = new ArrayList<>();
		DaybreakConfig config;
		if (!Files.isRegularFile(path)) {
			config = new DaybreakConfig().validated(problems);
		} else {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = parse(reader, problems);
			}
		}
		for (String problem : problems) {
			LOGGER.warn("{}: {}", path.getFileName(), problem);
		}
		return config;
	}

	/**
	 * Reads and checks a config. Anything unusable is replaced and reported to {@code problems}; only text that is not
	 * JSON at all throws (JsonParseException).
	 */
	public static DaybreakConfig parse(Reader reader, List<String> problems) {
		// The parser is lenient, so it accepts the // comments the file is written with
		JsonElement json = JsonParser.parseReader(reader);
		DaybreakConfig config = new DaybreakConfig();
		if (json == null || json.isJsonNull()) {
			return config.validated(problems);
		}
		if (!json.isJsonObject()) {
			problems.add("the file holds no settings ({ ... }); using the defaults");
			return config.validated(problems);
		}
		for (Map.Entry<String, JsonElement> entry : ((JsonObject) json).entrySet()) {
			Field field = option(entry.getKey());
			if (field == null) {
				problems.add("\"" + entry.getKey() + "\" is not a Daybreak option; ignored");
				continue;
			}
			try {
				field.set(config, read(field, entry.getValue()));
			} catch (JsonParseException | IllegalArgumentException | IllegalAccessException e) {
				Object fallback;
				try {
					fallback = field.get(config);
				} catch (IllegalAccessException impossible) {
					throw new IllegalStateException(impossible);
				}
				problems.add(entry.getKey() + ": " + entry.getValue() + " cannot be used here; using " + GSON.toJson(fallback));
			}
		}
		return config.validated(problems);
	}

	/** An option by its name in the file, or null. */
	private static @Nullable Field option(String name) {
		for (Field field : options()) {
			if (field.getName().equals(name)) {
				return field;
			}
		}
		return null;
	}

	/** The options in the file, in order: the public, non-static, non-transient fields. */
	private static List<Field> options() {
		List<Field> fields = new ArrayList<>();
		for (Field field : DaybreakConfig.class.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
				fields.add(field);
			}
		}
		return fields;
	}

	/** One option's value. Strict about the kind of value: Gson alone would read "yes" as false and "12" as a number. */
	private static Object read(Field field, JsonElement value) {
		Class<?> type = field.getType();
		boolean primitive = value instanceof JsonPrimitive;
		if (type == boolean.class && !(primitive && ((JsonPrimitive) value).isBoolean())) {
			throw new JsonParseException("not true or false");
		}
		if ((type == int.class || type == double.class) && !(primitive && ((JsonPrimitive) value).isNumber())) {
			throw new JsonParseException("not a number");
		}
		Object result = GSON.fromJson(value, field.getGenericType());
		if (result == null && type.isPrimitive()) {
			throw new JsonParseException("empty");
		}
		return result;
	}

	public static DaybreakConfig parse(String json, List<String> problems) {
		return parse(new StringReader(json), problems);
	}

	/** Writes the whole file next to the old one, then replaces it in one step, so a crash never leaves half a file. */
	private static void write(Path path, DaybreakConfig config) {
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(temporary, config.serialize(), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not write {}", path, e);
		}
	}

	/** Fills in anything missing and keeps every value within its documented range. */
	DaybreakConfig validated(List<String> problems) {
		DaybreakConfig defaults = new DaybreakConfig();
		if (!Double.isFinite(this.blocksPerDay) || this.blocksPerDay < MIN_BLOCKS_PER_DAY || this.blocksPerDay > MAX_BLOCKS_PER_DAY) {
			problems.add("blocksPerDay must be a number from " + (long) MIN_BLOCKS_PER_DAY + " to " + (long) MAX_BLOCKS_PER_DAY + ", was "
					+ this.blocksPerDay + "; using " + defaults.blocksPerDay);
			this.blocksPerDay = defaults.blocksPerDay;
		}
		if (this.direction == null) {
			problems.add("direction must be EAST_LATER or WEST_LATER; using EAST_LATER");
			this.direction = defaults.direction;
		}
		if (this.resyncTicks < MIN_RESYNC_TICKS || this.resyncTicks > MAX_RESYNC_TICKS) {
			int clamped = Math.max(MIN_RESYNC_TICKS, Math.min(MAX_RESYNC_TICKS, this.resyncTicks));
			problems.add("resyncTicks must be from " + MIN_RESYNC_TICKS + " to " + MAX_RESYNC_TICKS + ", was " + this.resyncTicks + "; using " + clamped);
			this.resyncTicks = clamped;
		}
		if (this.sleepSkipCooldownTicks < 0 || this.sleepSkipCooldownTicks > MAX_SKIP_COOLDOWN_TICKS) {
			int clamped = Math.max(0, Math.min(MAX_SKIP_COOLDOWN_TICKS, this.sleepSkipCooldownTicks));
			problems.add("sleepSkipCooldownTicks must be from 0 to " + MAX_SKIP_COOLDOWN_TICKS + ", was " + this.sleepSkipCooldownTicks + "; using " + clamped);
			this.sleepSkipCooldownTicks = clamped;
		}
		if (this.dimensions == null) {
			this.dimensions = defaults.dimensions;
		}
		Set<String> ids = new LinkedHashSet<>();
		List<String> kept = new ArrayList<>();
		for (String dimension : this.dimensions) {
			String id = dimension == null ? null : Versioned.parseId(dimension.trim());
			if (id == null) {
				problems.add("dimensions: \"" + dimension + "\" is not a dimension id; ignored");
			} else if (ids.add(id)) {
				kept.add(id);
			}
		}
		this.dimensions = kept;
		this.regionalDimensions = Set.copyOf(ids);
		return this;
	}

	/** The file's text: every setting with its explanation as // comments above it. */
	public String serialize() {
		StringBuilder out = new StringBuilder("{\n");
		List<Field> fields = options();
		for (int i = 0; i < fields.size(); i++) {
			Field field = fields.get(i);
			Comment comment = field.getAnnotation(Comment.class);
			if (comment != null) {
				for (String line : comment.value().split("\n")) {
					out.append("  // ").append(line.strip()).append('\n');
				}
			}
			Object value;
			try {
				value = field.get(this);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
			String json = GSON.toJson(value).replace("\n", "\n  ");
			out.append("  \"").append(field.getName()).append("\": ").append(json).append(i < fields.size() - 1 ? ",\n" : "\n");
			if (i < fields.size() - 1) {
				out.append('\n');
			}
		}
		return out.append("}\n").toString();
	}
}
