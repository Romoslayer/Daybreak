package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pillager patrols appear by day near a random player. Vanilla checks the level-wide daylight before picking the player;
 * with regional time it is checked at the chosen player, where the "close to a village" check is made.
 */
@Mixin(PatrolSpawner.class)
abstract class PatrolSpawnerMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isBrightOutside()Z"))
	private boolean daybreak$checkedAtPlayer(ServerLevel level, Operation<Boolean> original) {
		return RegionalTime.isRegional(level) || original.call(level);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isCloseToVillage(Lnet/minecraft/core/BlockPos;I)Z"))
	private boolean daybreak$noPatrolAtNight(ServerLevel level, BlockPos playerPos, int distance, Operation<Boolean> original) {
		// "Close to a village" stops the patrol, and so does local night at the player
		if (RegionalTime.isRegional(level) && !RegionalLight.isBright(level, playerPos.getX() + 0.5, playerPos.getY() + 0.5, playerPos.getZ() + 0.5)) {
			return true;
		}
		return original.call(level, playerPos, distance);
	}
}
