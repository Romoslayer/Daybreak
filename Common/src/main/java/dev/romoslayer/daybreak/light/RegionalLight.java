package dev.romoslayer.daybreak.light;

import dev.romoslayer.daybreak.time.RegionalTime;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Gameplay light. Stored sky light never changes with the time of day: vanilla subtracts one level-wide darkening
 * ({@code Level.skyDarken}) whenever light is read. Daybreak subtracts the darkening of the position being read, from
 * the same sky light level attribute (so weather blends in exactly as vanilla's), sampled at that position's local time.
 * <p>
 * The darkening depends only on the local time and the weather, so on the server thread it is cached for the current
 * tick per 16 ticks of offset (an error of at most 16 ticks of time at any scale).
 */
public final class RegionalLight {
	private static final int BUCKET_TICKS = 16;

	private RegionalLight() {
	}

	/** Added to Level: the per-tick darkening cache. */
	public interface Holder {
		Cache daybreak$lightCache();
	}

	public static final class Cache {
		private final Long2IntOpenHashMap byBucket = new Long2IntOpenHashMap();
		private long gameTime = Long.MIN_VALUE;
		private long clock = Long.MIN_VALUE;

		public Cache() {
			this.byBucket.defaultReturnValue(-1);
		}
	}

	/** The darkening at this position: vanilla's level-wide value outside regional dimensions. */
	public static int skyDarken(Level level, double x, double y, double z) {
		if (!RegionalTime.isRegional(level)) {
			return level.getSkyDarken();
		}
		ServerLevel serverLevel = (ServerLevel) level;
		if (!serverLevel.getServer().isSameThread()) {
			return compute(level, x, y, z);
		}
		Cache cache = ((Holder) level).daybreak$lightCache();
		long gameTime = level.getGameTime();
		long clock = RegionalTime.worldClock(serverLevel);
		if (gameTime != cache.gameTime || clock != cache.clock) {
			// A new tick, or /time or a sleep skip changed the clock within this one
			cache.byBucket.clear();
			cache.gameTime = gameTime;
			cache.clock = clock;
		}
		long bucket = Math.floorDiv(RegionalTime.offsetTicks(x), BUCKET_TICKS);
		int darken = cache.byBucket.get(bucket);
		if (darken < 0) {
			darken = compute(level, x, y, z);
			cache.byBucket.put(bucket, darken);
		}
		return darken;
	}

	/** Same formula as Level.updateSkyBrightness, read at a position. */
	private static int compute(Level level, double x, double y, double z) {
		return (int) (15.0F - level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, new Vec3(x, y, z)));
	}

	/** Whether it is daytime-bright here: vanilla's isBrightOutside rule (darkening below 4) at a position. */
	public static boolean isBright(Level level, double x, double y, double z) {
		return !level.dimensionType().hasFixedTime() && skyDarken(level, x, y, z) < 4;
	}
}
