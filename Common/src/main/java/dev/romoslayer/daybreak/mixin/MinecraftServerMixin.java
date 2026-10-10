package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.Daybreak;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The end of every server tick, the same on both loaders. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@Inject(method = "tickServer", at = @At("TAIL"))
	private void daybreak$endOfTick(BooleanSupplier haveTime, CallbackInfo ci) {
		Daybreak.onServerTickEnd((MinecraftServer) (Object) this);
	}
}
