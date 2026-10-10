package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Everything a player does (breaking blocks, using items, menus) is a packet handed to the server thread here; it is
 * handled with the player's position as the context. The call is inside a lambda, whose name differs between the
 * loaders' builds of the game, so it is found by what it calls.
 */
@Mixin(PacketUtils.class)
abstract class PacketUtilsMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
	private static void daybreak$playerContext(Packet<PacketListener> packet, PacketListener listener, Operation<Void> original) {
		if (listener instanceof ServerGamePacketListenerImpl game) {
			ServerPlayer player = game.player;
			RegionalContext.run(player.serverLevel(), player.position(), () -> original.call(packet, listener));
		} else {
			original.call(packet, listener);
		}
	}
}
