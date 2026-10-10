package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Ticking block entities (beacons, hives, spawners, modded machines) run with their own position as the context. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BoundTickingBlockEntityMixin {
	@Shadow
	@Final
	private BlockEntity blockEntity;

	@WrapMethod(method = "tick")
	private void daybreak$blockEntityContext(Operation<Void> original) {
		if (this.blockEntity.getLevel() instanceof ServerLevel level) {
			RegionalContext.run(level, Vec3.atCenterOf(this.blockEntity.getBlockPos()), () -> original.call());
		} else {
			original.call();
		}
	}
}
