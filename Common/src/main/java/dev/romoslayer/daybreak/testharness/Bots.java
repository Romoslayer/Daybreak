package dev.romoslayer.daybreak.testharness;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only headless players, made the way vanilla's GameTestHelper.makeMockServerPlayerInLevel does: a real
 * ServerPlayer placed through PlayerList with an in-memory connection. The spawner, mob caps, chunk tickets and sleep
 * logic treat them as players, and everything the server sends them can be read back.
 */
final class Bots {
	static final class Bot {
		ServerPlayer player;
		EmbeddedChannel channel;
		ClockNetworkState lastClock;
		long lastClockGameTime;
		int timePackets;
		long maxDrift;
		double walkPerTick;
		int walkTicksLeft;
		final List<String> chat = new ArrayList<>();

		Bot(ServerPlayer player, EmbeddedChannel channel) {
			this.player = player;
			this.channel = channel;
		}

		/** The bot client's own clock now, extrapolated the way the vanilla client does between packets. */
		long clientClock(long gameTimeNow) {
			if (this.lastClock == null) {
				return Long.MIN_VALUE;
			}
			return this.lastClock.totalTicks()
					+ (long) Math.floor(this.lastClock.partialTick() + (double) this.lastClock.rate() * (gameTimeNow - this.lastClockGameTime));
		}

		void resetStats() {
			this.timePackets = 0;
			this.maxDrift = 0;
			this.chat.clear();
		}
	}

	static final Map<String, Bot> BOTS = new LinkedHashMap<>();

	private Bots() {
	}

	private static Bot connect(MinecraftServer server, ServerLevel level, GameProfile profile, Vec3 at) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
		// What PrepareSpawnTask does with a saved position before a real login is placed
		player.snapTo(at, 0.0F, 0.0F);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.CREATIVE);
		Bot bot = new Bot(player, channel);
		drain(level, bot);
		return bot;
	}

	static Bot spawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
		Bot bot = BOTS.get(name);
		if (bot == null) {
			UUID id = UUID.nameUUIDFromBytes(("daybreak-bot:" + name).getBytes(StandardCharsets.UTF_8));
			bot = connect(server, level, new GameProfile(id, name), new Vec3(x, y, z));
			BOTS.put(name, bot);
		}
		teleport(bot, level, x, y, z);
		return bot;
	}

	/** Disconnects and reconnects at the same place, like a real logout and login. */
	static Bot relog(MinecraftServer server, String name) {
		Bot old = BOTS.get(name);
		Vec3 at = old.player.position();
		ServerLevel level = old.player.level();
		server.getPlayerList().remove(old.player);
		old.channel.close();
		Bot bot = connect(server, level, old.player.getGameProfile(), at);
		BOTS.put(name, bot);
		return bot;
	}

	static void teleport(Bot bot, ServerLevel level, double x, double y, double z) {
		bot.player.teleportTo(level, x, y, z, Set.of(), 0.0F, 0.0F, true);
		level.getChunkSource().move(bot.player);
	}

	static void tick(MinecraftServer server) {
		for (Bot bot : BOTS.values()) {
			if (bot.player.isDeadOrDying()) {
				bot.player = server.getPlayerList().respawn(bot.player, false, Entity.RemovalReason.KILLED);
				bot.player.setGameMode(GameType.CREATIVE);
			}
			if (bot.player.isRemoved()) {
				continue;
			}
			if (bot.walkTicksLeft > 0) {
				bot.walkTicksLeft--;
				teleport(bot, bot.player.level(), bot.player.getX() + bot.walkPerTick, bot.player.getY(), bot.player.getZ());
			}
			bot.player.doTick();
			drain(bot.player.level(), bot);
			measureDrift(bot);
		}
	}

	private static void drain(ServerLevel level, Bot bot) {
		Holder<WorldClock> clock = level.dimensionType().defaultClock().orElse(null);
		bot.channel.runPendingTasks();
		Object message;
		while ((message = bot.channel.readOutbound()) != null) {
			if (message instanceof ClientboundSetTimePacket time && clock != null) {
				ClockNetworkState state = time.clockUpdates().get(clock);
				if (state != null) {
					bot.lastClock = state;
					bot.lastClockGameTime = time.gameTime();
					bot.timePackets++;
				}
			} else if (message instanceof ClientboundSystemChatPacket chat) {
				// The key is what a client shows translated; the server has no mod language files
				bot.chat.add((chat.overlay() ? "(hotbar) " : "") + (chat.content().getContents() instanceof TranslatableContents tr ? tr.getKey()
						: chat.content().getString()));
			}
			ReferenceCountUtil.release(message);
		}
	}

	private static void measureDrift(Bot bot) {
		ServerLevel level = bot.player.level();
		long client = bot.clientClock(level.getGameTime());
		// Dimensions without a clock of their own (the Nether) show no time of day to compare
		if (client == Long.MIN_VALUE || level.dimensionType().defaultClock().isEmpty()) {
			return;
		}
		long expected = TestHarness.expectedClientClock(bot.player);
		// Compare times of day: the client value may be moved up by whole moon cycles to stay positive
		long diff = Math.floorMod(client - expected, dev.romoslayer.daybreak.time.RegionalTime.MOON_CYCLE_TICKS);
		diff = Math.min(diff, dev.romoslayer.daybreak.time.RegionalTime.MOON_CYCLE_TICKS - diff);
		bot.maxDrift = Math.max(bot.maxDrift, diff);
	}
}
