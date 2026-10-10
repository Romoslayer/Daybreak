package dev.romoslayer.daybreak.mc;

import dev.romoslayer.daybreak.compat.ec2.Ec2Bootstrap;
import dev.romoslayer.daybreak.context.RegionalContext;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Minecraft calls whose names or shapes differ between the versions Daybreak supports. Each generation has its own
 * copy of this class with the same methods: this one is for 26.x (world clocks, timelines, environment attributes);
 * Common/src/legacy has the one for 1.21.x and 1.20.x.
 */
public final class Versioned {
	private Versioned() {
	}

	/** Optional integrations that exist only for this generation (Enhanced Celestials 2). */
	public static void initCompat() {
		Ec2Bootstrap.init();
	}

	/** A registry key as "namespace:path". */
	public static String id(ResourceKey<?> key) {
		return key.identifier().toString();
	}

	/** "namespace:path" for a valid resource id (a bare path gets "minecraft:"), or null. */
	public static @Nullable String parseId(String text) {
		Identifier id = Identifier.tryParse(text);
		return id == null ? null : id.toString();
	}

	/** The level's world clock: its default clock's total ticks, or 0 for a dimension without a clock. */
	public static long worldClock(ServerLevel level) {
		return level.getDefaultClockTime();
	}

	/** The level a player is in. */
	public static ServerLevel level(ServerPlayer player) {
		return player.level();
	}

	/** Who may use /daybreak: operators at the game-master level. */
	public static Predicate<CommandSourceStack> operators() {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	/** A message over the hotbar. */
	public static void overlay(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message);
	}

	/**
	 * The sky darkening at a position, from the sky light level attribute sampled at the position's local time (the same
	 * formula as Level.updateSkyBrightness, so weather blends in exactly as vanilla's).
	 */
	public static int skyDarkenAt(Level level, double x, double y, double z) {
		return (int) (15.0F - level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, new Vec3(x, y, z)));
	}

	/** Whether a bed would let this player sleep where they are now (local night, or a storm overhead). */
	public static boolean couldSleepHere(ServerLevel level, ServerPlayer player) {
		return RegionalContext.with(level, player.position(),
				() -> level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, player.blockPosition()).canSleep(level));
	}
}
