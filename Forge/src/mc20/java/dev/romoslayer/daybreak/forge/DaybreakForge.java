package dev.romoslayer.daybreak.forge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entry point (Forge 47, Minecraft 1.20.x; NeoForge for 1.20.1 is built from this too). Everything else is the
 * shared mixins.
 */
@Mod(Daybreak.MOD_ID)
public final class DaybreakForge {
	public DaybreakForge() {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DaybreakCommands.register(event.getDispatcher()));
	}
}
