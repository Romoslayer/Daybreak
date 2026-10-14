package dev.romoslayer.daybreak;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.mc.Versioned;
import dev.romoslayer.daybreak.sync.ClientTime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-independent entry point; the Fabric, NeoForge and Forge entrypoints call into here. */
public final class Daybreak {
	public static final String MOD_ID = "daybreak";
	public static final Logger LOGGER = LoggerFactory.getLogger("Daybreak");
	/** Things that run at the end of every server tick (compatibility modules add theirs). */
	private static final List<Consumer<MinecraftServer>> TICKERS = new ArrayList<>();

	private Daybreak() {
	}

	public static void init(Path configDir) {
		DaybreakConfig.load(configDir);
		DaybreakConfig config = DaybreakConfig.get();
		LOGGER.info("Daybreak {}: {} blocks per day, later towards the {}, in {}", config.enabled ? "enabled" : "disabled", config.blocksPerDay,
				config.direction == DaybreakConfig.Direction.EAST_LATER ? "east" : "west", config.dimensions);
		Versioned.initCompat();
		dev.romoslayer.daybreak.compat.seasonfall.SeasonfallBridge.init();
		installTestPart("dev.romoslayer.daybreak.testharness.TestHarness");
	}

	/**
	 * Dev servers only (-Ddaybreak.testHarness=true): installs a part of the test harness by name. The harness package is
	 * not in the release jars, so nothing of it is referenced directly; loaders use this for their own test hooks too.
	 */
	public static void installTestPart(String className) {
		if (!Boolean.getBoolean("daybreak.testHarness")) {
			return;
		}
		try {
			Class.forName(className).getMethod("install").invoke(null);
		} catch (ReflectiveOperationException e) {
			LOGGER.error("daybreak.testHarness is set but {} is not available", className, e);
		}
	}

	/** Logs only with "debug": true in the config (decisions worth tracing, never anything per tick). */
	public static void debug(String message, Object... args) {
		if (DaybreakConfig.get().debug) {
			LOGGER.info("[debug] " + message, args);
		}
	}

	public static void addTicker(Consumer<MinecraftServer> ticker) {
		TICKERS.add(ticker);
	}

	public static void onServerTickEnd(MinecraftServer server) {
		dev.romoslayer.daybreak.compat.seasonfall.SeasonfallBridge.tick(server);
		for (Consumer<MinecraftServer> ticker : TICKERS) {
			ticker.accept(server);
		}
		ClientTime.tick(server);
	}
}
