package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Every mob type's spawn rules (natural spawning, spawners, patrols, structures) are asked here with the spawn position:
 * they run with that position as the context, so their position-less day/night and moon checks (a slime's full moon)
 * are answered for the exact spot, not for the chunk around it.
 */
@Mixin(SpawnPlacements.class)
abstract class SpawnPlacementsMixin {
	@WrapMethod(method = "checkSpawnRules")
	private static boolean daybreak$spawnContext(EntityType<?> type, ServerLevelAccessor accessor, MobSpawnType reason, BlockPos pos, RandomSource random,
			Operation<Boolean> original) {
		ServerLevel level = accessor.getLevel();
		if (!RegionalTime.isRegional(level)) {
			return original.call(type, accessor, reason, pos, random);
		}
		return RegionalContext.with(level, Vec3.atCenterOf(pos), () -> original.call(type, accessor, reason, pos, random));
	}
}
