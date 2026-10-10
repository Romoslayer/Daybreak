package dev.romoslayer.daybreak.time;

import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft 1.21.x and 1.20.x run the day on one number, the level's day time. Everything that turns it into a sun
 * angle or a moon phase goes through the dimension type; Daybreak hands it the local solar clock of the current context
 * instead (DimensionTypeMixin), and a villager's brain gets the same for its schedule (BrainMixin).
 */
public final class LegacyTime {
	private LegacyTime() {
	}

	/**
	 * The day time to use for this dimension type right now: the local solar clock at the context position when the
	 * context is in a regional level of this dimension type, otherwise {@code dayTime} unchanged.
	 */
	public static long forDimension(DimensionType type, long dayTime) {
		Level level = RegionalContext.activeLevel();
		if (level == null || level.dimensionType() != type) {
			return dayTime;
		}
		return forContext(level, dayTime);
	}

	/** The day time for the current context's level and position, or {@code dayTime} unchanged without a regional one. */
	public static long forContext(long dayTime) {
		Level level = RegionalContext.activeLevel();
		return level == null ? dayTime : forContext(level, dayTime);
	}

	private static long forContext(Level level, long dayTime) {
		Vec3 pos = RegionalContext.activePos();
		if (pos == null || !RegionalTime.isRegional(level)) {
			return dayTime;
		}
		return RegionalTime.clientClock(RegionalTime.solarClock(dayTime, pos.x));
	}

	/** Vanilla's DimensionType.timeOfDay (0 = noon, 0.5 = midnight), without Daybreak's adjustment. */
	public static float timeOfDay(DimensionType type, long dayTime) {
		double d0 = Mth.frac((double) type.fixedTime().orElse(dayTime) / 24000.0 - 0.25);
		double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
		return (float) (d0 * 2.0 + d1) / 3.0F;
	}

	/** Vanilla's Level.updateSkyBrightness formula for a given time of day, with the level's current weather. */
	public static int skyDarken(Level level, float timeOfDay) {
		double rain = 1.0 - (double) (level.getRainLevel(1.0F) * 5.0F) / 16.0;
		double thunder = 1.0 - (double) (level.getThunderLevel(1.0F) * 5.0F) / 16.0;
		double sun = 0.5 + 2.0 * Mth.clamp((double) Mth.cos(timeOfDay * (float) (Math.PI * 2)), -0.25, 0.25);
		return (int) ((1.0 - sun * rain * thunder) * 11.0);
	}
}
