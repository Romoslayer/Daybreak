package dev.romoslayer.daybreak.neoforge;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.sync.ClientTime;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.payload.ClientboundCustomSetTimePayload;
import org.jspecify.annotations.Nullable;

/** NeoForge entry point (NeoForge 21.1, Minecraft 1.21.x). The night skip goes through NeoForge's sleep-finished event (see ServerLevelNeoForgeSkipMixin). */
@Mod(Daybreak.MOD_ID)
public final class DaybreakNeoForge {
	public DaybreakNeoForge() {
		Daybreak.init(FMLPaths.CONFIGDIR.get());
		// Dev servers only: a stand-in listener for the sleep-finished event
		Daybreak.installTestPart("dev.romoslayer.daybreak.testharness.NeoForgeSleepProbe");
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DaybreakCommands.register(event.getDispatcher()));
		ClientTime.addAdapter(new NeoForgeTimePackets());
	}

	/**
	 * NeoForge sends players who have NeoForge its own time packet instead of the vanilla one: the day time plus its speed
	 * (day time per tick, which mods may change) and the fraction of a tick it has run. Daybreak rewrites only the day time.
	 */
	private static final class NeoForgeTimePackets implements ClientTime.TimePacketAdapter {
		@Override
		public ClientTime.@Nullable TimePacket read(Packet<?> packet) {
			if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ClientboundCustomSetTimePayload time) {
				return new ClientTime.TimePacket(time.gameTime(), time.dayTime(), time.gameRule(), time.dayTimePerTick());
			}
			return null;
		}

		@Override
		public Packet<?> withDayTime(Packet<?> packet, long dayTime) {
			ClientboundCustomSetTimePayload time = (ClientboundCustomSetTimePayload) ((ClientboundCustomPayloadPacket) packet).payload();
			return new ClientboundCustomPayloadPacket(new ClientboundCustomSetTimePayload(time.gameTime(), dayTime, time.gameRule(), time.dayTimeFraction(),
					time.dayTimePerTick()));
		}

		/** The same packet NeoForge's own time sync would send this player. */
		@Override
		public @Nullable Packet<?> fresh(ServerPlayer player, ServerLevel level) {
			if (!player.connection.hasChannel(ClientboundCustomSetTimePayload.TYPE)) {
				return null;
			}
			return new ClientboundCustomPayloadPacket(new ClientboundCustomSetTimePayload(level.getGameTime(), level.getDayTime(),
					level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT), level.getDayTimeFraction(), level.getDayTimePerTick()));
		}
	}
}
