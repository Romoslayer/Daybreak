package dev.romoslayer.daybreak.sleep;

import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Notices when a player's local time passes midnight (vanilla's siege roll moment, local 18000). */
public final class SiegeClock {
	private static final long MIDNIGHT = 18000L;
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private SiegeClock() {
	}

	/** Called once per siege tick; true if any player's local solar time passed midnight since the previous call. */
	public static boolean someoneReachedMidnight(ServerLevel level) {
		long world = RegionalTime.worldClock(level);
		boolean reached = false;
		for (ServerPlayer player : level.players()) {
			long now = RegionalTime.solarClock(world, player.getX());
			Long before = LAST.put(player.getUUID(), now);
			// Passed when the number of midnights so far went up (and time moved forward, not a teleport west)
			if (before != null && now > before && Math.floorDiv(now - MIDNIGHT, RegionalTime.DAY_TICKS) > Math.floorDiv(before - MIDNIGHT, RegionalTime.DAY_TICKS)
					&& now - before < RegionalTime.DAY_TICKS / 4) {
				reached = true;
			}
		}
		if (level.getGameTime() % 1200 == 0) {
			LAST.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
		}
		return reached;
	}
}
