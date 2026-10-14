package dev.romoslayer.daybreak.context;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the code currently running on a level "is". Vanilla asks most day/night questions without a position
 * ({@code isDay()}, the sun angle, the moon phase, a villager's schedule); while a context is set they are answered for
 * that position. It is set around entity ticks, player ticks, player actions (packets), bed use, block entity ticks,
 * chunk ticks, natural spawning per chunk and block drops. Without one, answers are for x = 0, like vanilla's single
 * clock.
 * <p>
 * Some of those questions reach code that does not know the level (the sun angle and moon phase come from the
 * dimension type, a schedule from the mob's brain), so the context is kept in one place with the level and thread it
 * belongs to. Only the server thread sets it; any other thread (a single-player client rendering its sky) never sees it.
 */
public final class RegionalContext {
	private static @Nullable Level level;
	private static @Nullable Vec3 pos;
	private static @Nullable Thread thread;

	private RegionalContext() {
	}

	/** The context position on this level, or null. */
	public static @Nullable Vec3 get(Level level) {
		return level == RegionalContext.level && Thread.currentThread() == thread ? pos : null;
	}

	/** The level of the current context on this thread, or null. */
	public static @Nullable Level activeLevel() {
		return Thread.currentThread() == thread ? level : null;
	}

	/** The position of the current context on this thread, or null. */
	public static @Nullable Vec3 activePos() {
		return Thread.currentThread() == thread ? pos : null;
	}

	/** Runs {@code action} with {@code pos} as the context, restoring the previous one afterwards (contexts nest). */
	public static <T> T with(Level level, Vec3 pos, Supplier<T> action) {
		// Only the level's own server thread has contexts; code on any other thread runs as it would without Daybreak (and
		// never disturbs the server thread's context)
		if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
			return action.get();
		}
		Level previousLevel = RegionalContext.level;
		Vec3 previousPos = RegionalContext.pos;
		Thread previousThread = RegionalContext.thread;
		RegionalContext.level = level;
		RegionalContext.pos = pos;
		RegionalContext.thread = Thread.currentThread();
		try {
			return action.get();
		} finally {
			RegionalContext.level = previousLevel;
			RegionalContext.pos = previousPos;
			RegionalContext.thread = previousThread;
		}
	}

	public static void run(Level level, Vec3 pos, Runnable action) {
		with(level, pos, () -> {
			action.run();
			return null;
		});
	}

	public static void run(Level level, BlockPos pos, Runnable action) {
		run(level, Vec3.atCenterOf(pos), action);
	}
}
