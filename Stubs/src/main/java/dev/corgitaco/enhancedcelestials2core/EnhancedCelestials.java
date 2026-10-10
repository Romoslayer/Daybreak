package dev.corgitaco.enhancedcelestials2core;

import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import java.util.Optional;
import net.minecraft.world.level.Level;

/** Compile-time stand-in for Enhanced Celestials 2: Core (only the signatures Daybreak uses; never shipped). */
public final class EnhancedCelestials {
	public static Optional<LunarForecast> lunarForecastWorldData(Level level) {
		throw new AssertionError("stub");
	}
}
