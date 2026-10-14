package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gameplay light and the position-less day/night checks. Vanilla reads one level-wide darkening ({@code skyDarken})
 * whenever light is read and for {@code isDay()}.
 * <ul>
 * <li>A light read at a position ({@code getMaxLocalRawBrightness(pos)}, which spawn rules, sun burning and every
 * light-level-dependent value go through) uses the darkening of that position, with or without a context.</li>
 * <li>The position-less reads ({@code getSkyDarken()}, {@code isDay()}) are answered for the context position while a
 * context is set, and for x = 0 without one.</li>
 * </ul>
 * No stored light ever changes. The two-argument {@code getMaxLocalRawBrightness(pos, darken)} is left alone, so callers
 * that pass their own darkening (vanilla's thunderstorm spawning) keep it.
 * <p>
 * The override is declared through LevelReader so that every loader's remapper renames it with the interface method it
 * overrides (1.21.x and 1.20.x run on obfuscated or SRG names).
 */
@Mixin(Level.class)
abstract class LevelMixin implements LevelReader, RegionalLight.Holder, RegionalTime.Cached {
	@Unique
	private final RegionalLight.Cache daybreak$lightCache = new RegionalLight.Cache();

	@Override
	public RegionalLight.Cache daybreak$lightCache() {
		return this.daybreak$lightCache;
	}

	@Unique
	private RegionalTime.@Nullable State daybreak$regionalState;

	@Override
	public RegionalTime.@Nullable State daybreak$regionalState() {
		return this.daybreak$regionalState;
	}

	@Override
	public void daybreak$setRegionalState(RegionalTime.State state) {
		this.daybreak$regionalState = state;
	}

	/** Overrides LevelReader#getMaxLocalRawBrightness(BlockPos). */
	@Override
	public int getMaxLocalRawBrightness(BlockPos pos) {
		Level level = (Level) (Object) this;
		return level.getMaxLocalRawBrightness(pos, RegionalLight.skyDarken(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/** isNight() is {@code !hasFixedTime && !isDay()}, so it follows. */
	@Inject(method = "isDay", at = @At("HEAD"), cancellable = true)
	private void daybreak$dayHere(CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		Vec3 pos = RegionalContext.get(level);
		if (pos != null && RegionalTime.isRegional(level)) {
			cir.setReturnValue(RegionalLight.isBright(level, pos.x, pos.y, pos.z));
		}
	}

	/** Light reads without a darkening of their own (getMaxLocalRawBrightness(pos), daylight detectors) come here. */
	@Inject(method = "getSkyDarken", at = @At("HEAD"), cancellable = true)
	private void daybreak$darkenHere(CallbackInfoReturnable<Integer> cir) {
		Level level = (Level) (Object) this;
		Vec3 pos = RegionalContext.get(level);
		if (pos != null && RegionalTime.isRegional(level)) {
			cir.setReturnValue(RegionalLight.skyDarken(level, pos.x, pos.y, pos.z));
		}
	}
}
