package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.time.LegacyTime;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every sun angle and moon phase on the server is the dimension type's answer for the level's day time
 * (Level.getTimeOfDay, getSunAngle, getMoonPhase, getMoonBrightness): with a context in a regional level, it answers
 * for the local solar time instead. Daylight detectors, cats' morning gifts, slimes under the full moon, turtle eggs and
 * the difficulty's moon factor follow.
 */
@Mixin(DimensionType.class)
abstract class DimensionTypeMixin {
	@ModifyVariable(method = "timeOfDay", at = @At("HEAD"), argsOnly = true)
	private long daybreak$localTimeOfDay(long dayTime) {
		return LegacyTime.forDimension((DimensionType) (Object) this, dayTime);
	}

	@ModifyVariable(method = "moonPhase", at = @At("HEAD"), argsOnly = true)
	private long daybreak$localMoonPhase(long dayTime) {
		return LegacyTime.forDimension((DimensionType) (Object) this, dayTime);
	}
}
