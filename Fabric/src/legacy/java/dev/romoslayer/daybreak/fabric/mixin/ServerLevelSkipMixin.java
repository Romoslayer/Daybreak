package dev.romoslayer.daybreak.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla 1.21.x and 1.20.x end a slept-through night by setting the day time to the next x = 0 morning. With regional
 * time it moves by the amount SleepRules works out for the sleepers instead. (Forge and NeoForge route the same moment
 * through their sleep-finished events, see their skip mixins.)
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSkipMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
	private void daybreak$skipToSleepersMorning(ServerLevel level, long morning, Operation<Void> original) {
		if (!RegionalTime.isRegional(level)) {
			original.call(level, morning);
			return;
		}
		original.call(level, level.getDayTime() + SleepRules.endNight(level));
	}
}
