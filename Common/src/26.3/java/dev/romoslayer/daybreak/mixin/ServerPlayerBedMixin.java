package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Getting into a bed runs with the bed's position as the context. The whole method is wrapped, so NeoForge's and Forge's
 * rewritten bed checks get the same context, and the previous context comes back even if something in it throws. This
 * copy is for 26.3 (26.2's method takes only the position).
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerBedMixin {
	@WrapMethod(method = "startSleepInBed")
	private Either<Player.BedSleepingProblem, Unit> daybreak$bedContext(AbstractBedBlock bedBlock, BlockState bedBlockState, BedRule rule, BlockPos pos,
			Operation<Either<Player.BedSleepingProblem, Unit>> original) {
		return RegionalContext.with(((ServerPlayer) (Object) this).level(), Vec3.atCenterOf(pos), () -> original.call(bedBlock, bedBlockState, rule, pos));
	}
}
