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
 * Players are ticked by their connection, not the level, so their tick gets the context here (this covers vanilla's
 * "woken up because it is day" check). Getting into a bed gets the bed's position. Whole methods are wrapped rather
 * than single calls, so NeoForge's rewritten bed checks get the same context.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@WrapMethod(method = "doTick")
	private void daybreak$playerContext(Operation<Void> original) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		RegionalContext.run(self.level(), self.position(), () -> original.call());
	}

	@WrapMethod(method = "startSleepInBed")
	private Either<Player.BedSleepingProblem, Unit> daybreak$bedContext(AbstractBedBlock bedBlock, BlockState bedBlockState, BedRule rule, BlockPos pos,
			Operation<Either<Player.BedSleepingProblem, Unit>> original) {
		return RegionalContext.with(((ServerPlayer) (Object) this).level(), Vec3.atCenterOf(pos), () -> original.call(bedBlock, bedBlockState, rule, pos));
	}
}
