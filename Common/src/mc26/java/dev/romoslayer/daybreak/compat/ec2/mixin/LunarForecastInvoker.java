package dev.romoslayer.daybreak.compat.ec2.mixin;

import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarTextComponents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** EC2's own way of showing an event's start or end message to a player. */
@Mixin(LunarForecast.class)
public interface LunarForecastInvoker {
	@Invoker("sendNotification")
	static void daybreak$send(ServerPlayer player, LunarTextComponents.Notification notification) {
		throw new AssertionError();
	}
}
