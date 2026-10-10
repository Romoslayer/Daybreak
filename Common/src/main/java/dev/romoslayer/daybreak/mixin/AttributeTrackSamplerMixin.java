package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.time.RegionalTime;
import dev.romoslayer.daybreak.time.RegionalTrack;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyframeTrackSampler;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AttributeTrackSampler.class)
abstract class AttributeTrackSamplerMixin<Value, Argument> implements RegionalTrack {
	@Shadow
	@Final
	private Holder<WorldClock> clock;

	@Shadow
	@Final
	private AttributeModifier<Value, Argument> modifier;

	@Shadow
	@Final
	private KeyframeTrackSampler<Argument> argumentSampler;

	@Shadow
	@Final
	private ClockManager clockManager;

	@Unique
	private boolean daybreak$regional;

	@Override
	public void daybreak$markRegional() {
		this.daybreak$regional = true;
	}

	@Override
	public boolean daybreak$isRegional() {
		return this.daybreak$regional;
	}

	/** The track's value at this x: sampled at the local solar time there (the world clock while Daybreak is off). */
	@Override
	@SuppressWarnings("unchecked")
	public Object daybreak$sampleAt(Object baseValue, double x) {
		long world = this.clockManager.getInstance(this.clock).totalTicks();
		long ticks = DaybreakConfig.get().enabled ? RegionalTime.solarClock(world, x) : world;
		return this.modifier.apply((Value) baseValue, this.argumentSampler.sample(ticks));
	}
}
