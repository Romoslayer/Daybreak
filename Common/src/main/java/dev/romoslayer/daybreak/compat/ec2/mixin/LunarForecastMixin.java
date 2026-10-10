package dev.romoslayer.daybreak.compat.ec2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast;
import dev.romoslayer.daybreak.compat.ec2.Ec2Compat;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Only the "what is happening now" lookups read the local clock. Building the year's schedule, commands and the
 * forecast stay on the world clock, so there is still exactly one roll for the whole dimension.
 */
@Mixin(LunarForecast.class)
abstract class LunarForecastMixin {
	@WrapOperation(
			method = {"currentScheduledLunarEvent", "currentLunarEventOrDefault", "lastLunarEventHolder", "getBlend", "getLastScheduledLunarEvent",
					"getLastLunarEvent", "getNextScheduledLunarEvent"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getDefaultClockTime()J")
	)
	private long daybreak$localClock(Level level, Operation<Long> original) {
		return Ec2Compat.clock(level, original.call(level));
	}

	/** The world-wide announcement loop gets no players; Ec2Compat tells each player at their own night instead. */
	@WrapOperation(method = "notifyEventSwitched", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
	private List<ServerPlayer> daybreak$noWorldWideAnnouncement(ServerLevel level, Operation<List<ServerPlayer>> original) {
		return Ec2Compat.enabled() && RegionalTime.isRegional(level) ? List.of() : original.call(level);
	}
}
