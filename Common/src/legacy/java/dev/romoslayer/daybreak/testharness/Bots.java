package dev.romoslayer.daybreak.testharness;

import com.mojang.authlib.GameProfile;
import dev.romoslayer.daybreak.time.RegionalTime;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Dev-only headless players (1.21.x and 1.20.x), made the way vanilla's GameTestHelper does: a real ServerPlayer placed
 * through PlayerList with an in-memory connection. The spawner, mob caps, chunk tickets and sleep logic treat them as
 * players, and everything the server sends them can be read back.
 */
final class Bots {
	static final class Bot {
		ServerPlayer player;
		EmbeddedChannel channel;
		/** The last time packet's day time (negative: the cycle is stopped) and game time, as a vanilla client keeps them. */
		long lastDayTime;
		long lastGameTime;
		boolean hasTime;
		int timePackets;
		long maxDrift;
		double walkPerTick;
		int walkTicksLeft;
		final List<String> chat = new ArrayList<>();

		Bot(ServerPlayer player, EmbeddedChannel channel) {
			this.player = player;
			this.channel = channel;
		}

		/** The bot client's own day time now, run on one tick per tick between packets the way the vanilla client does. */
		long clientClock(long gameTimeNow) {
			if (!this.hasTime) {
				return Long.MIN_VALUE;
			}
			return this.lastDayTime >= 0 ? this.lastDayTime + (gameTimeNow - this.lastGameTime) : -this.lastDayTime;
		}

		/** 1 while the client's day runs, 0 while it is stopped (the client has no other speed on these versions). */
		float rate() {
			return this.hasTime && this.lastDayTime >= 0 ? 1.0F : 0.0F;
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

	private static Bot connect(MinecraftServer server, ServerLevel level, GameProfile profile, double x, double y, double z) {
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		ServerPlayer player = Legacy.connect(server, level, profile, connection, x, y, z);
		player.setGameMode(GameType.CREATIVE);
		Bot bot = new Bot(player, channel);
		drain(bot);
		return bot;
	}

	static Bot spawn(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
		Bot bot = BOTS.get(name);
		if (bot == null) {
			UUID id = UUID.nameUUIDFromBytes(("daybreak-bot:" + name).getBytes(StandardCharsets.UTF_8));
			bot = connect(server, level, new GameProfile(id, name), x, y, z);
			BOTS.put(name, bot);
		}
		teleport(bot, level, x, y, z);
		return bot;
	}

	/** Disconnects and reconnects at the same place, like a real logout and login. */
	static Bot relog(MinecraftServer server, String name) {
		Bot old = BOTS.get(name);
		ServerLevel level = old.player.serverLevel();
		double x = old.player.getX();
		double y = old.player.getY();
		double z = old.player.getZ();
		server.getPlayerList().remove(old.player);
		old.channel.close();
		Bot bot = connect(server, level, old.player.getGameProfile(), x, y, z);
		BOTS.put(name, bot);
		return bot;
	}

	static void teleport(Bot bot, ServerLevel level, double x, double y, double z) {
		bot.player.teleportTo(level, x, y, z, 0.0F, 0.0F);
		level.getChunkSource().move(bot.player);
	}

	static void tick(MinecraftServer server) {
		for (Bot bot : BOTS.values()) {
			if (bot.player.isDeadOrDying()) {
				bot.player = Legacy.respawn(server, bot.player);
				bot.player.setGameMode(GameType.CREATIVE);
			}
			if (bot.player.isRemoved()) {
				continue;
			}
			if (bot.walkTicksLeft > 0) {
				bot.walkTicksLeft--;
				teleport(bot, bot.player.serverLevel(), bot.player.getX() + bot.walkPerTick, bot.player.getY(), bot.player.getZ());
			}
			bot.player.doTick();
			drain(bot);
			measureDrift(bot);
		}
	}

	private static void drain(Bot bot) {
		bot.channel.runPendingTasks();
		Object message;
		while ((message = bot.channel.readOutbound()) != null) {
			if (message instanceof ClientboundSetTimePacket time) {
				bot.lastDayTime = time.getDayTime();
				bot.lastGameTime = time.getGameTime();
				bot.hasTime = true;
				bot.timePackets++;
			} else if (message instanceof ClientboundSystemChatPacket chat) {
				// The key is what a client shows translated; the server has no mod language files
				bot.chat.add((chat.overlay() ? "(hotbar) " : "") + (chat.content().getContents() instanceof TranslatableContents tr ? tr.getKey()
						: chat.content().getString()));
			}
			ReferenceCountUtil.release(message);
		}
	}

	private static void measureDrift(Bot bot) {
		ServerLevel level = bot.player.serverLevel();
		long client = bot.clientClock(level.getGameTime());
		// Dimensions with a fixed time (the Nether) show no time of day to compare
		if (client == Long.MIN_VALUE || level.dimensionType().hasFixedTime()) {
			return;
		}
		long expected = TestHarness.expectedClientClock(bot.player);
		// Compare times of day: the client value may be moved up by whole moon cycles to stay positive
		long diff = Math.floorMod(client - expected, RegionalTime.MOON_CYCLE_TICKS);
		diff = Math.min(diff, RegionalTime.MOON_CYCLE_TICKS - diff);
		bot.maxDrift = Math.max(bot.maxDrift, diff);
	}
}
