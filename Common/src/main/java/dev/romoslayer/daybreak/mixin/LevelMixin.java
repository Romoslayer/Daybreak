package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gameplay light and the position-less day/night checks. The two methods below override LevelReader's default methods,
 * which vanilla answers with the level-wide darkening; this is all natural spawning, sun burning, daylight detectors and
 * light predicates read, so no stored light ever changes.
 */
@Mixin(Level.class)
abstract class LevelMixin implements RegionalContext, RegionalLight.Holder {
	@Unique
	private @Nullable Vec3 daybreak$context;

	@Unique
	private final RegionalLight.Cache daybreak$lightCache = new RegionalLight.Cache();

	@Override
	public @Nullable Vec3 daybreak$context() {
		return this.daybreak$context;
	}

	@Override
	public void daybreak$setContext(@Nullable Vec3 pos) {
		this.daybreak$context = pos;
	}

	@Override
	public RegionalLight.Cache daybreak$lightCache() {
		return this.daybreak$lightCache;
	}

	/** Overrides LevelReader#getMaxLocalRawBrightness(BlockPos). */
	public int getMaxLocalRawBrightness(BlockPos pos) {
		Level level = (Level) (Object) this;
		return level.getMaxLocalRawBrightness(pos, RegionalLight.skyDarken(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/** Overrides LevelReader#getEffectiveSkyBrightness(BlockPos) (daylight detectors, spawner sky light limits). */
	public int getEffectiveSkyBrightness(BlockPos pos) {
		Level level = (Level) (Object) this;
		return Math.max(level.getBrightness(LightLayer.SKY, pos) - RegionalLight.skyDarken(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), 0);
	}

	/** isDarkOutside() is {@code !hasFixedTime && !isBrightOutside()}, so it follows. */
	@Inject(method = "isBrightOutside", at = @At("HEAD"), cancellable = true)
	private void daybreak$brightHere(CallbackInfoReturnable<Boolean> cir) {
		Vec3 pos = this.daybreak$context;
		Level level = (Level) (Object) this;
		if (pos != null && RegionalTime.isRegional(level)) {
			cir.setReturnValue(RegionalLight.isBright(level, pos.x, pos.y, pos.z));
		}
	}
}
