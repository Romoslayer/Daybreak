package dev.romoslayer.daybreak.sync;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.mc.Versioned;
import dev.romoslayer.daybreak.time.RegionalTime;
import dev.romoslayer.daybreak.time.SolarTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * What time each player's game shows. Clients of 1.21.x and 1.20.x run their own copy of the day time, one tick per tick
 * while the daylight cycle is on, and the server sends the real value every second. Every outgoing time packet is
 * rewritten on its way out to the player's local solar time (one choke point covers joins, /time, sleeping and other
 * mods); a fresh one is sent at once when the client's copy has drifted too far (a player travelling east or west).
 * <p>
 * Seasonal day length cannot be sent as a speed here: such a client keeps running one tick per tick, and the regular
 * resends keep it on time.
 */
public final class ClientTime {
	/** A loader's own time packet (NeoForge's for NeoForge clients): how to read it and rebuild it with another day time. */
	public interface TimePacketAdapter {
		/** The packet's (game time, day time, ticking) if it is one of this loader's time packets, else null. */
		long @Nullable [] read(Packet<?> packet);

		Packet<?> withDayTime(Packet<?> packet, long dayTime);
	}

	/** The clock a player's client is running, as last sent: enough to predict what it shows now. */
	private record Sent(boolean regional, long clock, boolean ticking, long gameTime) {
		long predicted(long gameTimeNow) {
			return this.ticking ? this.clock + (gameTimeNow - this.gameTime) : this.clock;
		}
	}

	private static final Map<UUID, Sent> SENT = new ConcurrentHashMap<>();
	private static final List<TimePacketAdapter> ADAPTERS = new ArrayList<>();

	private ClientTime() {
	}

	public static void addAdapter(TimePacketAdapter adapter) {
		ADAPTERS.add(adapter);
	}

	public static Packet<?> personalize(ServerPlayer player, Packet<?> packet) {
		if (packet instanceof ClientboundSetTimePacket time) {
			long dayTime = time.getDayTime();
			// Negative day times mean a stopped cycle (vanilla sends -1 for a cycle stopped at 0)
			boolean ticking = dayTime >= 0;
			long local = local(player, time.getGameTime(), ticking ? dayTime : dayTime == -1 ? 0 : -dayTime, ticking);
			return local == Long.MIN_VALUE ? packet : new ClientboundSetTimePacket(time.getGameTime(), local, ticking);
		}
		for (TimePacketAdapter adapter : ADAPTERS) {
			long[] read = adapter.read(packet);
			if (read != null) {
				long local = local(player, read[0], read[1], read[2] != 0);
				return local == Long.MIN_VALUE ? packet : adapter.withDayTime(packet, local);
			}
		}
		return packet;
	}

	/** Records what the player's client will run and returns the day time to send them, or MIN_VALUE to send it as is. */
	private static long local(ServerPlayer player, long gameTime, long worldDayTime, boolean ticking) {
		ServerLevel level = Versioned.level(player);
		if (!RegionalTime.isRegional(level)) {
			SENT.put(player.getUUID(), new Sent(false, worldDayTime, ticking, gameTime));
			return Long.MIN_VALUE;
		}
		long solar = RegionalTime.solarClock(worldDayTime, player.getX());
		SENT.put(player.getUUID(), new Sent(true, solar, ticking, gameTime));
		return RegionalTime.clientClock(solar);
	}

	public static void tick(MinecraftServer server) {
		long resync = DaybreakConfig.get().resyncTicks;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = Versioned.level(player);
			Sent sent = SENT.get(player.getUUID());
			boolean regional = RegionalTime.isRegional(level);
			boolean ticking = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
			boolean stale = sent == null || sent.regional != regional;
			if (!stale && regional) {
				long actual = RegionalTime.solarClock(level.getDayTime(), player.getX());
				stale = sent.ticking != ticking || Math.abs(sent.predicted(level.getGameTime()) - actual) >= resync;
			}
			if (stale) {
				// Sent as the plain world time; personalize() shifts it like every other time packet
				player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), ticking));
			}
		}
		if (server.getTickCount() % 200 == 0) {
			SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/** The clock this player's game is running now (for /daybreak debug), or null before the first time packet. */
	public static @Nullable Long clientClock(ServerPlayer player) {
		Sent sent = SENT.get(player.getUUID());
		return sent == null ? null : sent.predicted(Versioned.level(player).getGameTime());
	}
}
