package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Entity ticks (every AI goal of vanilla and modded mobs) run with the entity's position as the context. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	@WrapMethod(method = "tickNonPassenger")
	private void daybreak$entityContext(Entity entity, Operation<Void> original) {
		RegionalContext.run((ServerLevel) (Object) this, entity.position(), () -> original.call(entity));
	}

	@WrapMethod(method = "tickPassenger")
	private void daybreak$passengerContext(Entity vehicle, Entity entity, Operation<Void> original) {
		RegionalContext.run((ServerLevel) (Object) this, entity.position(), () -> original.call(vehicle, entity));
	}
}
