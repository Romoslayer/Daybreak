package dev.romoslayer.daybreak.sleep;

import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Zombie sieges with regional time. Vanilla runs one siege at a time per level, rolls for it once a night at the world's
 * midnight and runs it while the level is dark. With regional time (see the VillageSiegeMixins):
 * <ul>
 * <li>a roll happens when a player's local time passes midnight, at most once per player and local night, and a won
 * roll besieges a village around that player (not anyone else who happens to be at night);</li>
 * <li>every roll starts a fresh attempt (vanilla's own reset only happens at a level-wide morning, which a world with
 * players all around the globe never has);</li>
 * <li>a roll that comes while a siege is running is held until that siege is over, rather than cancelling it;</li>
 * <li>a running siege lasts while its own spot is dark; before it is set up, while any player is in local night;</li>
 * <li>spectators neither roll nor count as players at night (vanilla never sieges them either).</li>
 * </ul>
 */
public final class SiegeClock {
	private static final long MIDNIGHT = 18000L;
	/** Per level (so a new world starts afresh): each player's local solar clock at the previous check. */
	private static final Map<ServerLevel, Map<UUID, Long>> LAST = new WeakHashMap<>();
	/** Per level: the local night each player last rolled for (night n starts at local midnight n). */
	private static final Map<ServerLevel, Map<UUID, Long>> ROLLED = new WeakHashMap<>();
	/** Dev only (test harness): every roll succeeds, so siege behaviour can be tested without waiting for a 1-in-10. */
	private static volatile boolean forceWin;
	/** The siege each level ticked last (one per level), so the test harness can report on it. */
	private static final Map<ServerLevel, Status> SIEGES = new WeakHashMap<>();

	private SiegeClock() {
	}

	private static long night(long solar) {
		return Math.floorDiv(solar - MIDNIGHT, RegionalTime.DAY_TICKS);
	}

	/** Whether any player who is not a spectator is in local night (or a storm dark enough for it). */
	public static boolean anyoneAtNight(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && !RegionalLight.isBright(level, player.getX(), player.getY(), player.getZ())) {
				return true;
			}
		}
		return false;
	}

	/** Whether a siege may run at this spot: dark there. */
	public static boolean darkAt(ServerLevel level, int x, int y, int z) {
		return !RegionalLight.isBright(level, x + 0.5, y + 0.5, z + 0.5);
	}

	/**
	 * Called once per siege tick while someone is at night: the players whose local time passed midnight since the
	 * previous call, for a night they have not rolled for yet (usually none).
	 */
	public static Set<UUID> reachedMidnight(ServerLevel level) {
		long world = RegionalTime.worldClock(level);
		Map<UUID, Long> last = LAST.computeIfAbsent(level, l -> new HashMap<>());
		Map<UUID, Long> rolledFor = ROLLED.computeIfAbsent(level, l -> new HashMap<>());
		Set<UUID> reached = Set.of();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			UUID id = player.getUUID();
			long now = RegionalTime.solarClock(world, player.getX());
			Long before = last.put(id, now);
			long night = night(now);
			// Forward in time (not a jump or a teleport west), into a later night than before, and not one already rolled for
			if (before != null && now > before && now - before < RegionalTime.DAY_TICKS / 4 && night > night(before)) {
				Long rolled = rolledFor.get(id);
				if (rolled == null || night > rolled) {
					rolledFor.put(id, night);
					if (reached.isEmpty()) {
						reached = new HashSet<>();
					}
					reached.add(id);
				}
			}
		}
		if (level.getGameTime() % 1200 == 0) {
			last.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
			rolledFor.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
		}
		if (!reached.isEmpty()) {
			Daybreak.debug("{}: {} player(s) reached their local midnight; a village siege may be rolled", level.dimension(), reached.size());
		}
		return reached;
	}

	public static boolean forceWin() {
		return forceWin;
	}

	public static void setForceWin(boolean force) {
		forceWin = force;
		Daybreak.LOGGER.warn("Daybreak test: siege rolls {}", force ? "always succeed" : "back to normal");
	}

	/** Added to VillageSiege: what the regional siege is doing, for the test harness. */
	public interface Status {
		String daybreak$status();
	}

	public static void ticked(ServerLevel level, Status siege) {
		SIEGES.put(level, siege);
	}

	public static String status(ServerLevel level) {
		Status siege = SIEGES.get(level);
		return siege == null ? "no siege ticked yet" : siege.daybreak$status();
	}
}
