package dev.romoslayer.daybreak.forge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge entry point (Forge 26.x). The night skip goes through Forge's sleep-finished event (see ServerLevelForgeSkipMixin). */
@Mod(Daybreak.MOD_ID)
public final class DaybreakForge {
	public DaybreakForge(FMLJavaModLoadingContext context) {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		// Dev servers only: a stand-in listener for the sleep-finished event
		Daybreak.installTestPart("dev.romoslayer.daybreak.testharness.ForgeSleepProbe");
		// Forge 26.x events carry their own bus; listeners are added rather than annotated
		RegisterCommandsEvent.BUS.addListener(event -> DaybreakCommands.register(event.getDispatcher()));
	}
}
