package dev.romoslayer.daybreak.neoforge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.sleep.SleepRules;
import dev.romoslayer.daybreak.time.RegionalTime;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

@Mod(Daybreak.MOD_ID)
public final class DaybreakNeoForge {
	public DaybreakNeoForge() {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DaybreakCommands.register(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener(DaybreakNeoForge::onSleepFinished);
	}

	/** NeoForge replaces vanilla's "move the clock to morning" with this event; Daybreak moves it by the sleepers' amount. */
	private static void onSleepFinished(SleepFinishedTimeEvent event) {
		if (event.getLevel() instanceof ServerLevel level && RegionalTime.isRegional(level)) {
			event.setAdjustment(new ClockAdjustment.Relative(SleepRules.skipTicks(level)));
		}
	}
}
