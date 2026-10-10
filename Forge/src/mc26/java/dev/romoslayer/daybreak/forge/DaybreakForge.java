package dev.romoslayer.daybreak.forge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge entry point (Forge 26.x). The night skip is adjusted by a mixin on Forge's sleep-finished hook (see mixin/). */
@Mod(Daybreak.MOD_ID)
public final class DaybreakForge {
	public DaybreakForge(FMLJavaModLoadingContext context) {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		// Forge 26.x events carry their own bus; listeners are added rather than annotated
		RegisterCommandsEvent.BUS.addListener(event -> DaybreakCommands.register(event.getDispatcher()));
	}
}
