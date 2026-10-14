package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Natural spawning. A chunk's spawning runs with the chunk as the context (other mods' position-less checks); each
 * candidate mob's own spawn checks run with the candidate's position. Light reads at a position use that position
 * anyway (LevelMixin), so this is for the position-less checks. Nothing is wrapped outside regional dimensions.
 */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
	@WrapMethod(method = "spawnForChunk")
	private static void daybreak$chunkContext(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, boolean friendlies, boolean monsters,
			boolean persistent, Operation<Void> original) {
		if (!RegionalTime.isRegional(level)) {
			original.call(level, chunk, state, friendlies, monsters, persistent);
			return;
		}
		Vec3 middle = new Vec3(chunk.getPos().getMiddleBlockX() + 0.5, 64.0, chunk.getPos().getMiddleBlockZ() + 0.5);
		RegionalContext.run(level, middle, () -> original.call(level, chunk, state, friendlies, monsters, persistent));
	}

	@WrapMethod(method = "isValidPositionForMob")
	private static boolean daybreak$candidateContext(ServerLevel level, Mob mob, double distance, Operation<Boolean> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level, mob, distance);
		}
		return RegionalContext.with(level, mob.position(), () -> original.call(level, mob, distance));
	}
}
