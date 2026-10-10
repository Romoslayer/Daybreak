package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.sleep.SiegeClock;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zombie sieges. Vanilla rolls once a night, on the tick the level's time of day is exactly midnight, and only while the
 * level is dark. With regional time a roll happens whenever a player's local time passes midnight, the siege may run
 * while any player is in local night, and it is set up only around players who are in local night.
 */
@Mixin(VillageSiege.class)
abstract class VillageSiegeMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isDay()Z"))
	private boolean daybreak$brightEverywhere(ServerLevel level, Operation<Boolean> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level);
		}
		return level.players().stream().noneMatch(p -> !RegionalLight.isBright(level, p.getX(), p.getY(), p.getZ()));
	}

	/** Vanilla rolls when this is exactly 0.5 (midnight). */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getTimeOfDay(F)F"))
	private float daybreak$someonesMidnight(ServerLevel level, float partialTick, Operation<Float> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level, partialTick);
		}
		return SiegeClock.someoneReachedMidnight(level) ? 0.5F : 0.0F;
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
