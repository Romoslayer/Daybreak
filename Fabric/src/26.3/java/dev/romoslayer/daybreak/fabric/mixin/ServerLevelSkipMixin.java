package dev.romoslayer.daybreak.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla ends a slept-through night by moving the clock to the next x = 0 morning. With regional time it moves by the
 * amount SleepRules works out for the sleepers instead (NeoForge does the same through its sleep-finished event).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSkipMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/clock/ServerClockManager$MoveResult;"))
	private ServerClockManager.MoveResult daybreak$skipToSleepersMorning(ServerClockManager clocks, Holder<WorldClock> clock,
			ResourceKey<ClockTimeMarker> marker, Operation<ServerClockManager.MoveResult> original) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (!RegionalTime.isRegional(level)) {
			return original.call(clocks, clock, marker);
		}
		long skip = SleepRules.endNight(level);
		if (skip > 0) {
			clocks.addTicks(clock, (int) skip);
		}
		return ServerClockManager.MoveResult.MOVED;
	}
}
