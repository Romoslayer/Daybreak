package dev.romoslayer.daybreak.compat.ec2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import dev.romoslayer.daybreak.compat.ec2.Ec2Compat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EC2's "set tonight's event" command starts the event at the moment it is run (on the world clock). With regional time
 * the night starts at different moments in different places, so the event covers the whole night (from its usual start)
 * and each place gets all of its own night. Separate from LunarForecastMixin so a mismatch here never switches off the rest.
 */
@Mixin(LunarForecast.class)
abstract class LunarForecastTonightMixin {
	@WrapOperation(method = "setLunarEventTonight", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(JJ)J"))
	private long daybreak$wholeNight(long nightStart, long now, Operation<Long> original) {
		return Ec2Compat.enabled() ? nightStart : original.call(nightStart, now);
	}
}
