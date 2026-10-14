package dev.romoslayer.daybreak.testharness;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.Locale;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Dev only: a stand-in for another mod listening to NeoForge's sleep-finished event (NeoForge 21.1). Counts the events, and
 * can push the proposed time later or hold the clock where it is. {@code /daybreak test sleepevent [reset|pass|hold|later <ticks>]}.
 */
public final class NeoForgeSleepProbe {
	private static int count;
	private static long lastProposed = -1;
	private static String mode = "pass";
	private static long later;

	private NeoForgeSleepProbe() {
	}

	public static void install() {
		NeoForge.EVENT_BUS.addListener(NeoForgeSleepProbe::onSleepFinished);
		DaybreakCommands.addExtension(root -> root.then(Commands.literal("test").then(Commands.literal("sleepevent")
				.executes(ctx -> report(ctx.getSource(), "sleepevent"))
				.then(Commands.literal("reset").executes(ctx -> {
					count = 0;
					lastProposed = -1;
					return report(ctx.getSource(), "sleepevent reset");
				}))
				.then(Commands.literal("pass").executes(ctx -> setMode(ctx.getSource(), "pass", 0)))
				.then(Commands.literal("hold").executes(ctx -> setMode(ctx.getSource(), "hold", 0)))
				.then(Commands.literal("later").then(Commands.argument("ticks", LongArgumentType.longArg(1))
						.executes(ctx -> setMode(ctx.getSource(), "later", LongArgumentType.getLong(ctx, "ticks"))))))));
	}

	private static void onSleepFinished(SleepFinishedTimeEvent event) {
		count++;
		lastProposed = event.getNewTime();
		if (event.getLevel() instanceof ServerLevel level) {
			switch (mode) {
				case "hold" -> event.setTimeAddition(RegionalTime.worldClock(level));
				case "later" -> event.setTimeAddition(event.getNewTime() + later);
				default -> {
				}
			}
		}
	}

	private static int setMode(net.minecraft.commands.CommandSourceStack source, String newMode, long ticks) {
		mode = newMode;
		later = ticks;
		return report(source, "sleepevent mode " + newMode);
	}

	private static int report(net.minecraft.commands.CommandSourceStack source, String what) {
		String text = String.format(Locale.ROOT, "%s count=%d lastProposed=%d mode=%s later=%d", what, count, lastProposed, mode, later);
		source.sendSuccess(() -> Component.literal(text), false);
		Daybreak.LOGGER.info("[test] {}", text);
		return 1;
	}
}
