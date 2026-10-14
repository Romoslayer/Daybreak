package dev.romoslayer.daybreak.compat.ec2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import dev.romoslayer.daybreak.compat.ec2.Ec2Compat;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EC2's "set tonight's event" command starts the event at the moment it is run (on the world clock). With regional time
 * the night starts at different moments in different places, so in a regional dimension the event covers the whole
 * night (from its usual start) and each place gets all of its own night. Elsewhere, or with Daybreak switched off,
 * EC2's own behaviour is kept. Separate from LunarForecastMixin so a mismatch here never switches off the rest.
 */
@Mixin(LunarForecast.class)
abstract class LunarForecastTonightMixin {
	/** The forecast's level, noted where the command reads its clock (the value itself is left alone). */
	@WrapOperation(method = "setLunarEventTonight", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getDefaultClockTime()J"))
	private long daybreak$noteLevel(Level level, Operation<Long> original, @Share("level") LocalRef<Level> forecastLevel) {
		forecastLevel.set(level);
		return original.call(level);
	}

	@WrapOperation(method = "setLunarEventTonight", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(JJ)J"))
	private long daybreak$wholeNight(long nightStart, long now, Operation<Long> original, @Share("level") LocalRef<Level> forecastLevel) {
		Level level = forecastLevel.get();
		return level != null && Ec2Compat.enabled() && RegionalTime.isRegional(level) ? nightStart : original.call(nightStart, now);
	}
}
