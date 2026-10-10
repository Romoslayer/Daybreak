package dev.romoslayer.daybreak.mixin;

import dev.romoslayer.daybreak.time.LegacyTime;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Villager schedules (work, meet, rest) follow the local time of the villager whose brain is running. */
@Mixin(Brain.class)
abstract class BrainMixin {
	@ModifyVariable(method = "updateActivityFromSchedule", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private long daybreak$localSchedule(long dayTime) {
		return LegacyTime.forContext(dayTime);
	}
}
