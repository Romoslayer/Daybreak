package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Players are ticked by their connection, not the level, so their tick gets the context here (this covers vanilla's
 * "woken up because it is day" check). Getting into a bed is ServerPlayerBedMixin (src/26.2, src/26.3: the method's
 * parameters differ between them).
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@WrapMethod(method = "doTick")
	private void daybreak$playerContext(Operation<Void> original) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		RegionalContext.run(self.level(), self.position(), () -> original.call());
	}
}
