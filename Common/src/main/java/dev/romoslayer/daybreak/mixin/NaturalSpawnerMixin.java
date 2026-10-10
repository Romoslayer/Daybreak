package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Natural spawning in a chunk runs with that chunk as the context (other mods' position-less spawn checks). */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
	@WrapMethod(method = "spawnForChunk")
	private static void daybreak$chunkContext(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, List<MobCategory> categories,
			Operation<Void> original) {
		Vec3 middle = new Vec3(chunk.getPos().getMiddleBlockX() + 0.5, 64.0, chunk.getPos().getMiddleBlockZ() + 0.5);
		RegionalContext.run(level, middle, () -> original.call(level, chunk, state, categories));
	}
}
