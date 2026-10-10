package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phantoms come for tired players at night. Vanilla checks one level-wide darkness before going through the players;
 * with regional time the darkness is checked at each player instead (the same threshold, darkening of at least 5).
 * The player list is filtered rather than a check inside the loop, because NeoForge rewrites the loop body.
 */
@Mixin(PhantomSpawner.class)
abstract class PhantomSpawnerMixin {
	private static final int DARK_ENOUGH = 5;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSkyDarken()I"))
	private int daybreak$checkedPerPlayer(ServerLevel level, Operation<Integer> original) {
		return RegionalTime.isRegional(level) ? 15 : original.call(level);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
	private List<ServerPlayer> daybreak$playersAtNight(ServerLevel level, Operation<List<ServerPlayer>> original) {
		List<ServerPlayer> players = original.call(level);
		if (!RegionalTime.isRegional(level)) {
			return players;
		}
		return players.stream().filter(p -> RegionalLight.skyDarken(level, p.getX(), p.getY(), p.getZ()) >= DARK_ENOUGH).toList();
	}
}
