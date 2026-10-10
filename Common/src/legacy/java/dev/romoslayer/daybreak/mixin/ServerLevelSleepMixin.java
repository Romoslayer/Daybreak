package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where vanilla decides that the night is over because enough players are asleep. Daybreak adds its own conditions, and
 * the day time moves by the amount SleepRules works out for the sleepers instead of to the next x = 0 morning. This is
 * the same on every loader: Forge and NeoForge only change the value handed to the same call.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSleepMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
	private boolean daybreak$regionalNight(boolean enoughAsleep) {
		return enoughAsleep && SleepRules.mayFinishSleeping((ServerLevel) (Object) this);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
	private void daybreak$skipToSleepersMorning(ServerLevel level, long morning, Operation<Void> original) {
		if (!RegionalTime.isRegional(level)) {
			original.call(level, morning);
			return;
		}
		original.call(level, level.getDayTime() + SleepRules.endNight(level));
	}

	/** Vanilla announces "Sleeping through this night" as soon as enough are in bed; not when Daybreak holds the skip back. */
	@WrapOperation(method = "announceSleepStatus", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
	private void daybreak$honestAnnouncement(ServerPlayer player, Component message, boolean overlay, Operation<Void> original) {
		if (message.getContents() instanceof TranslatableContents translatable && "sleep.skipping_night".equals(translatable.getKey())) {
			String reason = SleepRules.reasonIfBlocked((ServerLevel) (Object) this);
			if (reason != null) {
				original.call(player, SleepRules.note(reason), overlay);
				return;
			}
		}
		original.call(player, message, overlay);
	}
}
