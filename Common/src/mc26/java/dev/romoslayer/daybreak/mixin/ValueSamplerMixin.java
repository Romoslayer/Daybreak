package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.romoslayer.daybreak.time.RegionalTrack;
import java.util.List;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one hook that makes positional attribute reads regional. An attribute driven by a regional timeline track
 * becomes position-dependent, so {@code getValue(attribute, pos)} no longer shortcuts to the cached dimension-wide
 * value; while the positional value is computed, regional tracks are sampled at the position's local time.
 * {@code getDimensionValue} is untouched and keeps answering for the world clock (x = 0).
 */
@Mixin(targets = "net.minecraft.world.attribute.EnvironmentAttributeSystem$ValueSampler")
abstract class ValueSamplerMixin {
	@Shadow
	@Final
	@Mutable
	private boolean isAffectedByPosition;

	@Shadow
	@Final
	private List<? extends EnvironmentAttributeLayer<?>> layers;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void daybreak$positionalWhenRegional(CallbackInfo ci) {
		for (EnvironmentAttributeLayer<?> layer : this.layers) {
			if (layer instanceof RegionalTrack track && track.daybreak$isRegional()) {
				this.isAffectedByPosition = true;
				return;
			}
		}
	}

	@WrapOperation(
			method = "computeValuePositional",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeLayer$TimeBased;applyTimeBased(Ljava/lang/Object;I)Ljava/lang/Object;")
	)
	private Object daybreak$sampleLocally(EnvironmentAttributeLayer.TimeBased<Object> layer, Object baseValue, int cacheTickId, Operation<Object> original,
			@Local(argsOnly = true) Vec3 pos) {
		if (layer instanceof RegionalTrack track && track.daybreak$isRegional()) {
			return track.daybreak$sampleAt(baseValue, pos.x);
		}
		return original.call(layer, baseValue, cacheTickId);
	}
}
