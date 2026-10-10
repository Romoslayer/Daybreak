package dev.romoslayer.daybreak;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.sync.ClientTime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-independent entry point; the Fabric and NeoForge entrypoints call into here. */
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
		dev.romoslayer.daybreak.compat.ec2.Ec2Bootstrap.init();
		dev.romoslayer.daybreak.compat.seasonfall.SeasonfallBridge.init();
		if (Boolean.getBoolean("daybreak.testHarness")) {
			// Dev servers only: the harness package is not in the release jars
			try {
				Class.forName("dev.romoslayer.daybreak.testharness.TestHarness").getMethod("install").invoke(null);
			} catch (ReflectiveOperationException e) {
				LOGGER.error("daybreak.testHarness is set but the test harness is not available", e);
			}
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
