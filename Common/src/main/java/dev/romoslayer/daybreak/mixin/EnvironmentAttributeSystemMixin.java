package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.time.TimelineBuild;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Remembers which level timeline layers are being built for, so TimelineMixin can tell regional levels apart. */
@Mixin(EnvironmentAttributeSystem.class)
abstract class EnvironmentAttributeSystemMixin {
	@WrapMethod(method = "addDynamicLayers")
	private static void daybreak$rememberLevel(EnvironmentAttributeSystem.Builder builder, Level level, Operation<Void> original) {
		Level previous = TimelineBuild.LEVEL.get();
		TimelineBuild.LEVEL.set(level);
		try {
			original.call(builder, level);
		} finally {
			TimelineBuild.LEVEL.set(previous);
		}
	}
}
