package dev.romoslayer.daybreak.forge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entry point (Forge 47, Minecraft 1.20.x; NeoForge for 1.20.1 is built from this too). The night skip goes
 * through Forge's sleep-finished event (see ServerLevelForgeSkipMixin).
 */
@Mod(Daybreak.MOD_ID)
public final class DaybreakForge {
	public DaybreakForge() {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		// Dev servers only: a stand-in listener for the sleep-finished event
		Daybreak.installTestPart("dev.romoslayer.daybreak.testharness.ForgeSleepProbe");
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DaybreakCommands.register(event.getDispatcher()));
	}
}
