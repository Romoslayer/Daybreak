package dev.romoslayer.daybreak.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.romoslayer.daybreak.time.RegionalTime;
import dev.romoslayer.daybreak.time.RegionalTrack;
import dev.romoslayer.daybreak.time.TimelineBuild;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.timeline.AttributeTrackSampler;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A timeline track is regional when it is built for a regional server level, repeats (day, moon, villager schedule) and
 * runs on that level's own clock. Timelines that do not repeat, like early_game, keep the world clock.
 */
@Mixin(Timeline.class)
abstract class TimelineMixin {
	@Shadow
	@Final
	private Holder<WorldClock> clock;

	@Shadow
	@Final
	private Optional<Integer> periodTicks;

	@ModifyReturnValue(method = "createTrackSampler", at = @At("RETURN"))
	private AttributeTrackSampler<?, ?> daybreak$markRegional(AttributeTrackSampler<?, ?> sampler) {
		Level level = TimelineBuild.LEVEL.get();
		// Listed rather than enabled: the switch is checked on every read, so a config reload can turn Daybreak on or off
		if (this.periodTicks.isPresent() && RegionalTime.isListed(level) && level.dimensionType().defaultClock().map(this.clock::equals).orElse(false)) {
			((RegionalTrack) sampler).daybreak$markRegional();
		}
		return sampler;
	}
}
