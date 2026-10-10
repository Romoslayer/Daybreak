package dev.romoslayer.daybreak.neoforge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.sync.ClientTime;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.payload.ClientboundCustomSetTimePayload;
import org.jspecify.annotations.Nullable;

/** NeoForge entry point (NeoForge 21.1, Minecraft 1.21.x). The night skip is a shared mixin on this version. */
@Mod(Daybreak.MOD_ID)
public final class DaybreakNeoForge {
	public DaybreakNeoForge() {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DaybreakCommands.register(event.getDispatcher()));
		// NeoForge sends players with NeoForge installed its own time packet instead of the vanilla one
		ClientTime.addAdapter(new ClientTime.TimePacketAdapter() {
			@Override
			public long @Nullable [] read(Packet<?> packet) {
				if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ClientboundCustomSetTimePayload time) {
					return new long[] {time.gameTime(), time.dayTime(), time.gameRule() ? 1 : 0};
				}
				return null;
			}

			@Override
			public Packet<?> withDayTime(Packet<?> packet, long dayTime) {
				ClientboundCustomSetTimePayload time = (ClientboundCustomSetTimePayload) ((ClientboundCustomPayloadPacket) packet).payload();
				return new ClientboundCustomPayloadPacket(new ClientboundCustomSetTimePayload(time.gameTime(), dayTime, time.gameRule(), 0.0F,
						time.dayTimePerTick()));
			}
		});
	}
}
