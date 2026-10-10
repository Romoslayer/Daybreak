package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.sleep.SiegeClock;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zombie sieges. Vanilla rolls once a night at the world clock's midnight and only while the level is dark. With regional
 * time a roll happens whenever a player's local time passes midnight, the siege may run while any player is in local
 * night, and it is set up only around players who are in local night.
 */
@Mixin(VillageSiege.class)
abstract class VillageSiegeMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isBrightOutside()Z"))
	private boolean daybreak$brightEverywhere(ServerLevel level, Operation<Boolean> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level);
		}
		return level.players().stream().noneMatch(p -> !RegionalLight.isBright(level, p.getX(), p.getY(), p.getZ()));
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/clock/ServerClockManager;isAtTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"))
	private boolean daybreak$someonesMidnight(ServerClockManager clocks, Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> marker,
			Operation<Boolean> original, @Local(argsOnly = true) ServerLevel level) {
		if (!RegionalTime.isRegional(level) || marker != ClockTimeMarkers.ROLL_VILLAGE_SIEGE) {
			return original.call(clocks, clock, marker);
		}
		return SiegeClock.someoneReachedMidnight(level);
	}

	@WrapOperation(method = "tryToSetupSiege", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
	private List<ServerPlayer> daybreak$playersAtNight(ServerLevel level, Operation<List<ServerPlayer>> original) {
		List<ServerPlayer> players = original.call(level);
		if (!RegionalTime.isRegional(level)) {
			return players;
		}
		return players.stream().filter(p -> !RegionalLight.isBright(level, p.getX(), p.getY(), p.getZ())).toList();
	}
}
