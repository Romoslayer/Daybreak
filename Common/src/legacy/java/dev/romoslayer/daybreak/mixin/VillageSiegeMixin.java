package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.sleep.SiegeClock;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zombie sieges with regional time (rules in SiegeClock). Vanilla's tick asks "is it day?", "is it exactly midnight?"
 * (the roll), rolls 1 in 10 and sets the siege up around a player; each of those is answered here for the regions.
 */
@Mixin(VillageSiege.class)
abstract class VillageSiegeMixin implements SiegeClock.Status {
	@Shadow
	private boolean hasSetupSiege;
	@Shadow
	private int zombiesToSpawn;
	@Shadow
	private int nextSpawnTime;
	@Shadow
	private int spawnX;
	@Shadow
	private int spawnY;
	@Shadow
	private int spawnZ;

	/** The last roll succeeded and the siege is not set up yet. */
	@Unique
	private boolean daybreak$won;
	/** Players who passed midnight while a siege was running: roll for them once it is over. */
	@Unique
	private final Set<UUID> daybreak$queued = new HashSet<>();
	/** The players whose midnight the current attempt was rolled for: it besieges a village around one of them. */
	@Unique
	private final Set<UUID> daybreak$rollers = new HashSet<>();
	/** End the current attempt on the next tick (a won roll with no village to besiege). */
	@Unique
	private boolean daybreak$end;

	@Unique
	private boolean daybreak$running() {
		return this.hasSetupSiege && this.zombiesToSpawn > 0;
	}

	/** "Is it day?": vanilla resets the siege when it is. Day at the siege's own spot, or everywhere before it is set up. */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isDay()Z"))
	private boolean daybreak$dayForTheSiege(ServerLevel level, Operation<Boolean> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level);
		}
		SiegeClock.ticked(level, this);
		boolean day = this.daybreak$end
				|| (this.daybreak$running() ? !SiegeClock.darkAt(level, this.spawnX, this.spawnY, this.spawnZ) : !SiegeClock.anyoneAtNight(level));
		this.daybreak$end = false;
		if (day) {
			this.daybreak$won = false;
		}
		return day;
	}

	/** Vanilla rolls when this is exactly 0.5 (midnight): a fresh attempt whenever a player reaches their own midnight. */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getTimeOfDay(F)F"))
	private float daybreak$someonesMidnight(ServerLevel level, float partialTick, Operation<Float> original) {
		if (!RegionalTime.isRegional(level)) {
			return original.call(level, partialTick);
		}
		Set<UUID> reached = SiegeClock.reachedMidnight(level);
		if (this.daybreak$running() || (this.daybreak$won && !this.hasSetupSiege)) {
			this.daybreak$queued.addAll(reached);
			return 0.0F;
		}
		if (reached.isEmpty() && this.daybreak$queued.isEmpty()) {
			return 0.0F;
		}
		this.daybreak$rollers.clear();
		this.daybreak$rollers.addAll(reached);
		this.daybreak$rollers.addAll(this.daybreak$queued);
		this.daybreak$queued.clear();
		this.hasSetupSiege = false;
		this.zombiesToSpawn = 0;
		this.nextSpawnTime = 0;
		return 0.5F;
	}

	/** The 1-in-10 roll itself (the only random number vanilla's tick draws). */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int daybreak$roll(RandomSource random, int bound, Operation<Integer> original, @Local(argsOnly = true) ServerLevel level) {
		int result = original.call(random, bound);
		if (RegionalTime.isRegional(level)) {
			if (SiegeClock.forceWin()) {
				result = 0;
			}
			this.daybreak$won = result == 0;
		}
		return result;
	}

	/** A won roll that finds no village around its players at night is over, rather than waiting and blocking later rolls. */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/village/VillageSiege;tryToSetupSiege(Lnet/minecraft/server/level/ServerLevel;)Z"))
	private boolean daybreak$setUpOrGiveUp(VillageSiege siege, ServerLevel level, Operation<Boolean> original) {
		boolean setUp = original.call(siege, level);
		if (RegionalTime.isRegional(level)) {
			this.daybreak$won = false;
			if (!setUp) {
				this.daybreak$end = true;
			}
		}
		return setUp;
	}

	/** The siege is set up only around the players whose midnight it was rolled for, if they are still in local night. */
	@WrapOperation(method = "tryToSetupSiege", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
	private List<ServerPlayer> daybreak$playersAtNight(ServerLevel level, Operation<List<ServerPlayer>> original) {
		List<ServerPlayer> players = original.call(level);
		if (!RegionalTime.isRegional(level)) {
			return players;
		}
		return players.stream().filter(p -> !p.isSpectator() && this.daybreak$rollers.contains(p.getUUID())
				&& !RegionalLight.isBright(level, p.getX(), p.getY(), p.getZ())).toList();
	}

	@Override
	public String daybreak$status() {
		return String.format(Locale.ROOT, "setUp=%s zombiesLeft=%d won=%s queued=%d rollers=%d spawn=%d,%d,%d", this.hasSetupSiege,
				this.zombiesToSpawn, this.daybreak$won, this.daybreak$queued.size(), this.daybreak$rollers.size(), this.spawnX, this.spawnY, this.spawnZ);
	}
}
