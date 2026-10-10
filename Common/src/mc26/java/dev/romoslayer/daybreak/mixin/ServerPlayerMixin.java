package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players are ticked by their connection, not the level, so their tick gets the context here (this covers vanilla's
 * "woken up because it is day" check). Getting into a bed gets the bed's position, set at the start of the whole method
 * and restored at its end, so NeoForge's and Forge's rewritten bed checks get the same context. (The method's
 * parameters differ between 26.2 and 26.3; only the bed position is picked out of them.)
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@WrapMethod(method = "doTick")
	private void daybreak$playerContext(Operation<Void> original) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		RegionalContext.run(self.level(), self.position(), () -> original.call());
	}

	@Inject(method = "startSleepInBed", at = @At("HEAD"))
	private void daybreak$bedContext(CallbackInfoReturnable<?> cir, @Local(argsOnly = true) BlockPos pos, @Share("previous") LocalRef<Vec3> previous) {
		RegionalContext context = RegionalContext.of(((ServerPlayer) (Object) this).level());
		previous.set(context.daybreak$context());
		context.daybreak$setContext(Vec3.atCenterOf(pos));
	}

	@Inject(method = "startSleepInBed", at = @At("RETURN"))
	private void daybreak$endBedContext(CallbackInfoReturnable<?> cir, @Share("previous") LocalRef<Vec3> previous) {
		RegionalContext.of(((ServerPlayer) (Object) this).level()).daybreak$setContext(previous.get());
	}
}
