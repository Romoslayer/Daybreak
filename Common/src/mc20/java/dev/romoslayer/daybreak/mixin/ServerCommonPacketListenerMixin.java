package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.sync.ClientTime;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every time packet a player is sent goes through here and leaves as that player's local time (see ClientTime). In
 * 1.20.1 the game connection sends its own packets (1.20.2 and later share the method with the configuration phase).
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerCommonPacketListenerMixin {
	@ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), argsOnly = true)
	private Packet<?> daybreak$localTime(Packet<?> packet, Packet<?> original, @Nullable PacketSendListener listener) {
		return ClientTime.personalize(((ServerGamePacketListenerImpl) (Object) this).player, packet);
	}
}
