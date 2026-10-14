package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A chunk's tick (lightning, snow, ice) runs with the chunk as the context, and each block's random tick (turtle eggs,
 * modded blocks) with that block as the context. Nothing is wrapped outside regional dimensions.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelChunkMixin {
	@WrapMethod(method = "tickChunk")
	private void daybreak$chunkContext(LevelChunk chunk, int randomTickSpeed, Operation<Void> original) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (!RegionalTime.isRegional(level)) {
			original.call(chunk, randomTickSpeed);
			return;
		}
		Vec3 middle = new Vec3(chunk.getPos().getMiddleBlockX() + 0.5, 64.0, chunk.getPos().getMiddleBlockZ() + 0.5);
		RegionalContext.run(level, middle, () -> original.call(chunk, randomTickSpeed));
	}

	@WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
	private void daybreak$blockContext(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
		if (!RegionalTime.isRegional(level)) {
			original.call(state, level, pos, random);
			return;
		}
		RegionalContext.run(level, pos, () -> original.call(state, level, pos, random));
	}
}
