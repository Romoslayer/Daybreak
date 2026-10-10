package dev.romoslayer.daybreak.compat.seasonfall;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.time.SolarTime;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Seasonal day length from Seasonfall, applied per place. Seasonfall (from the version that supports Daybreak) offers
 * {@code SeasonfallApi.daytimeFraction(ServerLevel)}: the share of the day that is daytime right now, and leaves the
 * world clock's speed alone while Daybreak is installed. Daybreak then stretches each place's own day (SolarTime).
 * Reflection only, so neither mod needs the other.
 */
public final class SeasonfallBridge {
	private static final String API = "dev.romoslayer.seasonfall.api.SeasonfallApi";
	private static @Nullable MethodHandle daytimeFraction;
	/** Dev-only override (the test harness sets it to test day lengths without Seasonfall). */
	private static @Nullable Double testOverride;

	private SeasonfallBridge() {
	}

	public static void init() {
		Class<?> api;
		try {
			api = Class.forName(API, false, SeasonfallBridge.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			return;
		}
		try {
			daytimeFraction = MethodHandles.publicLookup().findStatic(api, "daytimeFraction", MethodType.methodType(float.class, ServerLevel.class));
			Daybreak.LOGGER.info("Seasonfall found: each place gets its own seasonal day length");
		} catch (ReflectiveOperationException e) {
			Daybreak.LOGGER.warn("Seasonfall is installed but this version has no regional day length. If its seasonal day length is on, it "
					+ "stretches the wrong part of the day away from x = 0; update Seasonfall or switch its day length off.");
		}
		DaybreakCommands.addInfo(() -> "Seasonfall: " + (daytimeFraction == null ? "installed, without regional day length (needs a Seasonfall update)"
				: DaybreakConfig.get().seasonalDayLength ? "regional seasonal day length" : "installed, seasonal day length switched off in the config"));
	}

	/** Once per tick: the current daytime share, or equal day and night. */
	public static void tick(MinecraftServer server) {
		if (testOverride != null) {
			SolarTime.setDaytimeFraction(testOverride);
			return;
		}
		double fraction = SolarTime.EQUAL;
		if (daytimeFraction != null && DaybreakConfig.get().enabled && DaybreakConfig.get().seasonalDayLength) {
			try {
				fraction = (float) daytimeFraction.invokeExact(server.overworld());
			} catch (Throwable e) {
				Daybreak.LOGGER.error("Seasonfall's daytimeFraction failed; using equal day and night from now on", e);
				daytimeFraction = null;
			}
		}
		SolarTime.setDaytimeFraction(fraction);
	}

	public static void setTestOverride(@Nullable Double fraction) {
		testOverride = fraction;
		Daybreak.LOGGER.warn("Daybreak test: daytime share {}", fraction == null ? "back to normal" : String.format(Locale.ROOT, "forced to %.2f", fraction));
	}
}
