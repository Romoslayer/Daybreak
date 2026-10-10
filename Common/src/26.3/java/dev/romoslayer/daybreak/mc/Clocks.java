package dev.romoslayer.daybreak.mc;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;

/** World clock access, which changed shape between 26.2 and 26.3. This copy is for 26.3. */
public final class Clocks {
	private Clocks() {
	}

	public static long totalTicks(ClockManager clocks, Holder<WorldClock> clock) {
		return clocks.getInstance(clock).totalTicks();
	}

	/** What the server would send clients for this clock right now (ticks, partial tick, rate). */
	public static ClockNetworkState networkState(MinecraftServer server, Holder<WorldClock> clock) {
		return server.clockManager().getInstance(clock).packNetworkState(server);
	}
}
