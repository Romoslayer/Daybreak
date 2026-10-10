package dev.romoslayer.daybreak.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge ends a slept-through night by setting the clock to whatever its sleep-finished event decides (the next x = 0
 * morning by default). With regional time the clock moves by the amount SleepRules works out for the sleepers instead,
 * as the Fabric and NeoForge builds do.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelForgeSkipMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/event/ForgeEventFactory;onSleepFinished(Lnet/minecraft/server/level/ServerLevel;JJ)J"))
	private long daybreak$skipToSleepersMorning(ServerLevel level, long newTime, long minTime, Operation<Long> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level, newTime, minTime);
		}
		return minTime + SleepRules.endNight(level);
	}
}
