package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.sync.ClientTime;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every time packet a player is sent goes through here and leaves as that player's local time (see ClientTime). */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerMixin {
	@ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
	private Packet<?> daybreak$localTime(Packet<?> packet, Packet<?> original, @Nullable ChannelFutureListener listener) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game) {
			return ClientTime.personalize(game.player, packet);
		}
		return packet;
	}
}
