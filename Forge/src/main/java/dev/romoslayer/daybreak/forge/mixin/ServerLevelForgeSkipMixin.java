package dev.romoslayer.daybreak.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge ends a slept-through night by firing its sleep-finished event with vanilla's proposed time (the next x = 0
 * morning) and setting the clock to whatever the event returns. With regional time the proposal is the sleepers' own
 * morning (SleepRules) instead; the event still fires, so other mods are told and may still move the time further
 * (Forge only lets them move it later than now). The skip cooldown starts only if the clock actually moves. The same
 * on every Minecraft version Daybreak supports.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelForgeSkipMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/event/ForgeEventFactory;onSleepFinished(Lnet/minecraft/server/level/ServerLevel;JJ)J"))
	private long daybreak$skipToSleepersMorning(ServerLevel level, long newTime, long minTime, Operation<Long> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level, newTime, minTime);
		}
		long time = original.call(level, minTime + SleepRules.skipTicks(level), minTime);
		if (time > minTime) {
			SleepRules.skipped(level);
		}
		return time;
	}
}
