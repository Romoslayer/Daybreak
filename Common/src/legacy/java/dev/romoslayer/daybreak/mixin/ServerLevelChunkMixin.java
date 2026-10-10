package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** A chunk's random ticks (turtle eggs, modded blocks) run with the chunk as the context. */
@Mixin(ServerLevel.class)
abstract class ServerLevelChunkMixin {
	@WrapMethod(method = "tickChunk")
	private void daybreak$chunkContext(LevelChunk chunk, int randomTickSpeed, Operation<Void> original) {
		Vec3 middle = new Vec3(chunk.getPos().getMiddleBlockX() + 0.5, 64.0, chunk.getPos().getMiddleBlockZ() + 0.5);
		RegionalContext.run((ServerLevel) (Object) this, middle, () -> original.call(chunk, randomTickSpeed));
	}
}
