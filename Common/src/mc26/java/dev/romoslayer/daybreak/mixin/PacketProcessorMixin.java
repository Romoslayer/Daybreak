package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.romoslayer.daybreak.context.RegionalContext;
import net.minecraft.network.PacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Everything a player does (breaking blocks, using items, menus) is a packet handled on the server thread here; it runs
 * with the player's position as the context.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
abstract class PacketProcessorMixin {
	@Shadow
	@Final
	private PacketListener listener;

	@WrapMethod(method = "handle")
	private void daybreak$playerContext(Operation<Void> original) {
		if (this.listener instanceof ServerGamePacketListenerImpl game) {
			ServerPlayer player = game.player;
			RegionalContext.run(player.level(), player.position(), () -> original.call());
		} else {
			original.call();
		}
	}
}
