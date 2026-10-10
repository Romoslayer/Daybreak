package dev.romoslayer.daybreak.forge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge entry point (Forge 52, Minecraft 1.21.x). Everything else is the shared mixins. */
@Mod(Daybreak.MOD_ID)
public final class DaybreakForge {
	public DaybreakForge(FMLJavaModLoadingContext context) {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DaybreakCommands.register(event.getDispatcher()));
	}
}
