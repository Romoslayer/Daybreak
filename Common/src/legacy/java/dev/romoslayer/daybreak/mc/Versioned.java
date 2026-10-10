package dev.romoslayer.daybreak.mc;

import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.LegacyTime;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Minecraft calls whose names or shapes differ between the versions Daybreak supports. Each generation has its own
 * copy of this class with the same methods: this one is for 1.21.x and 1.20.x (the old day time); Common/src/mc26 has
 * the one for 26.x.
 */
public final class Versioned {
	private Versioned() {
	}

	/** Optional integrations that exist only for this generation: none (Enhanced Celestials 2 support is 26.x only). */
	public static void initCompat() {
	}

	/** A registry key as "namespace:path". */
	public static String id(ResourceKey<?> key) {
		return key.location().toString();
	}

	/** "namespace:path" for a valid resource id (a bare path gets "minecraft:"), or null. */
	public static @Nullable String parseId(String text) {
		ResourceLocation id = ResourceLocation.tryParse(text);
		return id == null ? null : id.toString();
	}

	/** The level's world clock: its day time. */
	public static long worldClock(ServerLevel level) {
		return level.getDayTime();
	}

	/** The level a player is in. */
	public static ServerLevel level(ServerPlayer player) {
		return player.serverLevel();
	}

	/** Who may use /daybreak: operators at the game-master level. */
	public static Predicate<CommandSourceStack> operators() {
		return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	/** A message over the hotbar. */
	public static void overlay(ServerPlayer player, Component message) {
		player.displayClientMessage(message, true);
	}

	/** The sky darkening at a position: vanilla's formula, for the local solar time there and the current weather. */
	public static int skyDarkenAt(Level level, double x, double y, double z) {
		long local = RegionalTime.solarClock(level.getDayTime(), x);
		return LegacyTime.skyDarken(level, LegacyTime.timeOfDay(level.dimensionType(), local));
	}

	/** Whether a bed would let this player sleep where they are now: vanilla's "not day" (local night, or a storm). */
	public static boolean couldSleepHere(ServerLevel level, ServerPlayer player) {
		return !RegionalLight.isBright(level, player.getX(), player.getY(), player.getZ());
	}
}
