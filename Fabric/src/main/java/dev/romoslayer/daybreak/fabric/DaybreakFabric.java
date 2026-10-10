package dev.romoslayer.daybreak.fabric;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public final class DaybreakFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Daybreak.init(FabricLoader.getInstance().getConfigDir());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DaybreakCommands.register(dispatcher));
	}
}
