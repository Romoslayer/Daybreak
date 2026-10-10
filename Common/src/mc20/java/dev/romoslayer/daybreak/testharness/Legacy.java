package dev.romoslayer.daybreak.testharness;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The few calls the dev-only test harness needs that differ between 1.21.x and 1.20.x. This copy is for 1.20.x. */
final class Legacy {
	private Legacy() {
	}

	/** A new player placed through the player list on an in-memory connection, as GameTestHelper does. */
	static ServerPlayer connect(MinecraftServer server, ServerLevel level, GameProfile profile, Connection connection, double x, double y, double z) {
		ServerPlayer player = new ServerPlayer(server, level, profile);
		player.moveTo(x, y, z, 0.0F, 0.0F);
		server.getPlayerList().placeNewPlayer(connection, player);
		return player;
	}

	static ServerPlayer respawn(MinecraftServer server, ServerPlayer player) {
		return server.getPlayerList().respawn(player, false);
	}

	/** Uses a block with an empty hand, as a right click would. */
	static InteractionResult useBlock(BlockState state, ServerLevel level, ServerPlayer player, BlockHitResult hit) {
		return state.use(level, player, InteractionHand.MAIN_HAND, hit);
	}

	static double msPerTick(MinecraftServer server) {
		return server.getAverageTickTime();
	}

	static BlockPos at(double x, double y, double z) {
		return BlockPos.containing(x, y, z);
	}
}
