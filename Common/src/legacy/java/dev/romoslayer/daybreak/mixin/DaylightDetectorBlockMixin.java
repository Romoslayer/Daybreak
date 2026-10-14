package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A daylight detector reads the level-wide darkening and sun angle; it updates from its own tick but also when a player
 * flips it, so it always gets its own position as the context.
 */
@Mixin(DaylightDetectorBlock.class)
abstract class DaylightDetectorBlockMixin {
	@WrapMethod(method = "updateSignalStrength")
	private static void daybreak$detectorContext(BlockState state, Level level, BlockPos pos, Operation<Void> original) {
		if (!RegionalTime.isRegional(level)) {
			original.call(state, level, pos);
			return;
		}
		RegionalContext.run(level, pos, () -> original.call(state, level, pos));
	}
}
