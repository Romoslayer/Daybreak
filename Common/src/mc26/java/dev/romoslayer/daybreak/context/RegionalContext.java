package dev.romoslayer.daybreak.context;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the code currently running on a level "is". Vanilla asks some day/night questions without a position
 * ({@code isBrightOutside()}, a bed's rule, other mods' "current event"); while a context is set they are answered for
 * that position. It is set around entity ticks, player ticks, player actions (packets), bed use, block entity ticks,
 * natural spawning per chunk and block drops. Without one, answers are for x = 0, like vanilla's single clock.
 * <p>
 * Implemented by Level (LevelMixin); only the thread that owns the level sets or reads it.
 */
public interface RegionalContext {
	@Nullable Vec3 daybreak$context();

	void daybreak$setContext(@Nullable Vec3 pos);

	static RegionalContext of(Level level) {
		return (RegionalContext) level;
	}

	static @Nullable Vec3 get(Level level) {
		return ((RegionalContext) level).daybreak$context();
	}

	/** Runs {@code action} with {@code pos} as the context, restoring the previous one afterwards (contexts nest). */
	static <T> T with(Level level, Vec3 pos, Supplier<T> action) {
		RegionalContext context = (RegionalContext) level;
		Vec3 previous = context.daybreak$context();
		context.daybreak$setContext(pos);
		try {
			return action.get();
		} finally {
			context.daybreak$setContext(previous);
		}
	}

	static void run(Level level, Vec3 pos, Runnable action) {
		with(level, pos, () -> {
			action.run();
			return null;
		});
	}

	static void run(Level level, BlockPos pos, Runnable action) {
		run(level, Vec3.atCenterOf(pos), action);
	}
}
