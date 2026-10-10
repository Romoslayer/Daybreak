package dev.romoslayer.daybreak.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.sync.ClientTime;
import dev.romoslayer.daybreak.time.RegionalTime;
import dev.romoslayer.daybreak.time.SolarTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /daybreak} (operators): {@code time [x]} local time and day at a position or at you, {@code info} settings and
 * what is active, {@code debug <player>} the time that player's game is showing, {@code reload} re-reads the config.
 */
public final class DaybreakCommands {
	/** Extra subcommands (the dev-only test harness adds its own). */
	private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> EXTENSIONS = new ArrayList<>();
	/** Extra text for /daybreak time at a position (the lunar event there, with Enhanced Celestials). */
	private static final List<java.util.function.BiFunction<ServerLevel, Double, String>> TIME_EXTRAS = new ArrayList<>();
	/** Lines for /daybreak info from the compatibility modules. */
	private static final List<java.util.function.Supplier<String>> INFO = new ArrayList<>();

	private DaybreakCommands() {
	}

	public static void addExtension(Consumer<LiteralArgumentBuilder<CommandSourceStack>> extension) {
		EXTENSIONS.add(extension);
	}

	public static void addTimeExtra(java.util.function.BiFunction<ServerLevel, Double, String> extra) {
		TIME_EXTRAS.add(extra);
	}

	public static void addInfo(java.util.function.Supplier<String> line) {
		INFO.add(line);
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Daybreak.MOD_ID)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("time")
						.executes(ctx -> time(ctx, ctx.getSource().getPosition().x))
						.then(Commands.argument("x", DoubleArgumentType.doubleArg()).executes(ctx -> time(ctx, DoubleArgumentType.getDouble(ctx, "x")))))
				.then(Commands.literal("info").executes(DaybreakCommands::info))
				.then(Commands.literal("debug").then(Commands.argument("player", EntityArgument.player()).executes(DaybreakCommands::debug)))
				.then(Commands.literal("reload").executes(DaybreakCommands::reload));
		EXTENSIONS.forEach(extension -> extension.accept(root));
		dispatcher.register(root);
	}

	/** "18:30" for a time of day in ticks (0 = 6 AM). */
	public static String clock(long timeOfDay) {
		long minutes = (Math.floorMod(timeOfDay, RegionalTime.DAY_TICKS) * 60 / 1000 + 6 * 60) % (24 * 60);
		return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
	}

	private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int time(CommandContext<CommandSourceStack> ctx, double x) {
		return reply(ctx, describeTime(ctx.getSource().getLevel(), x));
	}

	/** The text of /daybreak time for a position. */
	public static String describeTime(ServerLevel level, double x) {
		long world = RegionalTime.worldClock(level);
		if (!RegionalTime.isRegional(level)) {
			return "No regional time in " + level.dimension().identifier() + ": " + clock(RegionalTime.timeOfDay(world)) + " everywhere";
		}
		long solar = RegionalTime.solarClock(world, x);
		StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "At x = %.0f: %s (ticks %d), day %d. At x = 0: %s.", x,
				clock(RegionalTime.timeOfDay(solar)), RegionalTime.timeOfDay(solar), RegionalTime.day(solar),
				clock(RegionalTime.timeOfDay(RegionalTime.solarClock(world, 0)))));
		for (var extra : TIME_EXTRAS) {
			String line = extra.apply(level, x);
			if (line != null) {
				text.append(' ').append(line);
			}
		}
		return text.toString();
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		DaybreakConfig config = DaybreakConfig.get();
		StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "Daybreak %s, %s blocks per day, later towards the %s, dimensions %s",
				config.enabled ? "enabled" : "disabled", config.blocksPerDay, config.direction == DaybreakConfig.Direction.EAST_LATER ? "east" : "west",
				config.dimensions));
		text.append(String.format(Locale.ROOT, "\nDaytime is %.0f%% of the day", SolarTime.daytimeFraction() * 100));
		INFO.forEach(line -> text.append('\n').append(line.get()));
		return reply(ctx, text.toString());
	}

	private static int debug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Long client = ClientTime.clientClock(player);
		ServerLevel level = player.level();
		long expected = RegionalTime.isRegional(level) ? RegionalTime.solarClock(RegionalTime.worldClock(level), player.getX()) : RegionalTime.worldClock(level);
		return reply(ctx, String.format(Locale.ROOT, "%s at x = %.0f: their game shows %s, local time is %s", player.getName().getString(), player.getX(),
				client == null ? "nothing yet" : clock(RegionalTime.timeOfDay(client)) + " (ticks " + RegionalTime.timeOfDay(client) + ")",
				clock(RegionalTime.timeOfDay(expected)) + " (ticks " + RegionalTime.timeOfDay(expected) + ")"));
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		String error = DaybreakConfig.reload();
		if (error != null) {
			ctx.getSource().sendFailure(Component.literal("Could not reload config/daybreak.json: " + error));
			return 0;
		}
		return reply(ctx, "Reloaded config/daybreak.json (changes to \"dimensions\" need a restart)");
	}
}
