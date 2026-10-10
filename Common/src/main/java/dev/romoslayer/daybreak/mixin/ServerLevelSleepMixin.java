package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.sleep.SleepRules;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

	/** Vanilla announces "Sleeping through this night" as soon as enough are in bed; not when Daybreak holds the skip back. */
	@WrapOperation(method = "announceSleepStatus", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;sendOverlayMessage(Lnet/minecraft/network/chat/Component;)V"))
	private void daybreak$honestAnnouncement(ServerPlayer player, Component message, Operation<Void> original) {
		if (message.getContents() instanceof TranslatableContents translatable && "sleep.skipping_night".equals(translatable.getKey())) {
			String reason = SleepRules.reasonIfBlocked((ServerLevel) (Object) this);
			if (reason != null) {
				original.call(player, SleepRules.note(reason));
				return;
			}
		}
		original.call(player, message);
	}
}
