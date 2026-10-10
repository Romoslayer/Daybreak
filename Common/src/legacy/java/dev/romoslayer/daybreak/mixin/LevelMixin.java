package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gameplay light and the position-less day/night checks. Vanilla reads one level-wide darkening ({@code skyDarken})
 * whenever light is read and for {@code isDay()}; while a context is set, both are answered for the context position's
 * local time. That covers natural spawning, sun burning, daylight detectors, beds and every mob behaviour that asks
 * whether it is day or night. No stored light ever changes.
 */
@Mixin(Level.class)
abstract class LevelMixin implements RegionalLight.Holder {
	@Unique
	private final RegionalLight.Cache daybreak$lightCache = new RegionalLight.Cache();

	@Override
	public RegionalLight.Cache daybreak$lightCache() {
		return this.daybreak$lightCache;
	}

	/** isNight() is {@code !hasFixedTime && !isDay()}, so it follows. */
	@Inject(method = "isDay", at = @At("HEAD"), cancellable = true)
	private void daybreak$dayHere(CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		Vec3 pos = RegionalContext.get(level);
		if (pos != null && RegionalTime.isRegional(level)) {
			cir.setReturnValue(RegionalLight.isBright(level, pos.x, pos.y, pos.z));
		}
	}

	/** Light reads without a darkening of their own (getMaxLocalRawBrightness(pos), daylight detectors) come here. */
	@Inject(method = "getSkyDarken", at = @At("HEAD"), cancellable = true)
	private void daybreak$darkenHere(CallbackInfoReturnable<Integer> cir) {
		Level level = (Level) (Object) this;
		Vec3 pos = RegionalContext.get(level);
		if (pos != null && RegionalTime.isRegional(level)) {
			cir.setReturnValue(RegionalLight.skyDarken(level, pos.x, pos.y, pos.z));
		}
	}
}
