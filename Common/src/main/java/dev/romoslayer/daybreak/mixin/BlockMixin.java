package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;

/** A block's drops are decided where the block was (explosions and pistons break blocks far from any player). */
@Mixin(Block.class)
abstract class BlockMixin {
	@WrapMethod(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V")
	private static void daybreak$dropContext(Level level, BlockPos pos, ItemStack stack, Operation<Void> original) {
		if (level instanceof ServerLevel) {
			RegionalContext.run(level, pos, () -> original.call(level, pos, stack));
		} else {
			original.call(level, pos, stack);
		}
	}
}
