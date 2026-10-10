package dev.corgitaco.enhancedcelestials2core.api.lunarevent;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

/** Compile-time stand-in (never shipped). */
public class LunarEvent {
	public Optional<LunarTextComponents.Notification> startNotification() {
		throw new AssertionError("stub");
	}

	public Optional<LunarTextComponents.Notification> endNotification() {
		throw new AssertionError("stub");
	}

	public boolean blockSleeping(LivingEntity entity) {
		throw new AssertionError("stub");
	}
}
