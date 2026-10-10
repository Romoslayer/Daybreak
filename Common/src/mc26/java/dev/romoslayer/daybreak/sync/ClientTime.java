package dev.romoslayer.daybreak.sync;

import dev.romoslayer.daybreak.config.DaybreakConfig;
import dev.romoslayer.daybreak.mc.Clocks;
import dev.romoslayer.daybreak.time.RegionalTime;
import dev.romoslayer.daybreak.time.SolarTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import org.jspecify.annotations.Nullable;

/**
 * What time each player's game shows. Vanilla clients run their own copy of every clock (ticks and rate) and only hear
 * from the server when it changes, so a player's local sky is just their dimension's clock, shifted to their local solar
 * time and running at their local rate. Every outgoing time packet is rewritten on its way out (one choke point covers
 * joins, /time, sleeping, game rules and other mods); a fresh one is sent only when the clock the client is running has
 * drifted from the player's actual local time, or its speed no longer matches (sunrise or sunset with seasonal day length).
 */
public final class ClientTime {
	/** The clock a player's client is running, as last sent: enough to predict what it shows now. */
	private record Sent(boolean regional, long clock, float partialTick, float rate, long gameTime) {
		long predicted(long gameTimeNow) {
			return this.clock + (long) Math.floor(this.partialTick + (double) this.rate * (gameTimeNow - this.gameTime));
		}
	}

	private static final Map<UUID, Sent> SENT = new ConcurrentHashMap<>();
	private static final float RATE_TOLERANCE = 1.0E-4F;

	private ClientTime() {
	}

	private static @Nullable Holder<WorldClock> clockOf(ServerLevel level) {
		return level.dimensionType().defaultClock().orElse(null);
	}

	public static Packet<?> personalize(ServerPlayer player, Packet<?> packet) {
		if (!(packet instanceof ClientboundSetTimePacket time)) {
			return packet;
		}
		ServerLevel level = player.level();
		Holder<WorldClock> clock = clockOf(level);
		ClockNetworkState state = clock == null ? null : time.clockUpdates().get(clock);
		if (state == null) {
			return packet;
		}
		if (!RegionalTime.isRegional(level)) {
			SENT.put(player.getUUID(), new Sent(false, state.totalTicks(), state.partialTick(), state.rate(), time.gameTime()));
			return packet;
		}
		long local = state.totalTicks() + RegionalTime.offsetTicks(player.getX());
		long solar = SolarTime.solarClock(local);
		float rate = (float) (state.rate() * SolarTime.rate(local));
		SENT.put(player.getUUID(), new Sent(true, solar, state.partialTick(), rate, time.gameTime()));
		Map<Holder<WorldClock>, ClockNetworkState> clocks = new HashMap<>(time.clockUpdates());
		clocks.put(clock, new ClockNetworkState(RegionalTime.clientClock(solar), state.partialTick(), rate));
		return new ClientboundSetTimePacket(time.gameTime(), clocks);
	}

	public static void tick(MinecraftServer server) {
		long resync = DaybreakConfig.get().resyncTicks;
		// Each clock's state, worked out once per tick however many players share it
		Map<Holder<WorldClock>, ClockNetworkState> states = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			Holder<WorldClock> clock = clockOf(level);
			if (clock == null) {
				continue;
			}
			Sent sent = SENT.get(player.getUUID());
			boolean regional = RegionalTime.isRegional(level);
			boolean stale = sent == null || sent.regional != regional;
			if (!stale && regional) {
				ClockNetworkState world = states.computeIfAbsent(clock, c -> Clocks.networkState(server, c));
				long local = world.totalTicks() + RegionalTime.offsetTicks(player.getX());
				long actual = SolarTime.solarClock(local);
				float rate = (float) (world.rate() * SolarTime.rate(local));
				stale = Math.abs(sent.predicted(level.getGameTime()) - actual) >= resync || Math.abs(sent.rate - rate) > RATE_TOLERANCE;
			}
			if (stale) {
				// Sent as the plain world clock; personalize() shifts it like every other time packet
				player.connection.send(new ClientboundSetTimePacket(level.getGameTime(),
						Map.of(clock, states.computeIfAbsent(clock, c -> Clocks.networkState(server, c)))));
			}
		}
		if (server.getTickCount() % 200 == 0) {
			SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/** The clock this player's game is running now (for /daybreak debug), or null before the first time packet. */
	public static @Nullable Long clientClock(ServerPlayer player) {
		Sent sent = SENT.get(player.getUUID());
		return sent == null ? null : sent.predicted(player.level().getGameTime());
	}
}
