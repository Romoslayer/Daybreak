package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.romoslayer.daybreak.sleep.SleepRules;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where vanilla decides that the night is over because enough players are asleep. Daybreak adds its own conditions; the
 * clock adjustment itself is loader-specific (a mixin on Fabric, NeoForge's sleep-finished event).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSleepMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
	private boolean daybreak$regionalNight(boolean enoughAsleep) {
		return enoughAsleep && SleepRules.mayFinishSleeping((ServerLevel) (Object) this);
	}
}
