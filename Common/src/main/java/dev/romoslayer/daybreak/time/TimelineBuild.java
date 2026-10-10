package dev.romoslayer.daybreak.time;

import net.minecraft.world.level.Level;

/** The level whose environment attribute layers are being built on this thread (null outside that). */
public final class TimelineBuild {
	public static final ThreadLocal<Level> LEVEL = new ThreadLocal<>();

	private TimelineBuild() {
	}
}
