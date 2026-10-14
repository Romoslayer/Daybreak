package dev.romoslayer.daybreak.testharness;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.compat.seasonfall.SeasonfallBridge;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.LegacyTime;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only test commands ({@code /daybreak test ...}) for live tests driven over RCON, for 1.21.x and 1.20.x. They print
 * the same lines as the 26.x harness, so the same test scripts and checks run on every version. Never in a release jar:
 * the package is excluded from the jars and it only installs with -Ddaybreak.testHarness=true.
 */
public final class TestHarness {
	private TestHarness() {
	}

	public static void install() {
		Daybreak.LOGGER.warn("Daybreak test harness installed (dev only)");
		Daybreak.addTicker(Bots::tick);
		Daybreak.addTicker(TestHarness::runScheduled);
		DaybreakCommands.addExtension(root -> root.then(Commands.literal("test")
				.then(Commands.literal("probe").then(xyz(TestHarness::probe)))
				.then(Commands.literal("bot").then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
								.executes(TestHarness::bot)))))
				.then(Commands.literal("bots").executes(TestHarness::bots))
				.then(Commands.literal("botsreset").executes(ctx -> {
					Bots.BOTS.values().forEach(Bots.Bot::resetStats);
					return reply(ctx, "bot stats reset");
				}))
				.then(Commands.literal("chat").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::chat)))
				.then(Commands.literal("relog").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::relog)))
				.then(Commands.literal("walk").then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("blocksPerTick", DoubleArgumentType.doubleArg()).then(Commands.argument("ticks", IntegerArgumentType.integer(1))
								.executes(TestHarness::walk)))))
				.then(Commands.literal("count").then(Commands.argument("x", DoubleArgumentType.doubleArg())
						.then(Commands.argument("z", DoubleArgumentType.doubleArg()).then(Commands.argument("radius", IntegerArgumentType.integer(1))
								.executes(TestHarness::count)))))
				.then(Commands.literal("spawncheck").then(Commands.argument("n", IntegerArgumentType.integer(1)).then(xyz(TestHarness::spawnCheck))))
				.then(Commands.literal("lightread").then(Commands.argument("x", DoubleArgumentType.doubleArg())
						.then(Commands.argument("y", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
								.executes(ctx -> lightRead(ctx, false))
								.then(Commands.argument("contextX", DoubleArgumentType.doubleArg()).executes(ctx -> lightRead(ctx, true)))))))
				.then(Commands.literal("block").then(xyz(TestHarness::block)))
				.then(Commands.literal("capture").then(Commands.argument("label", StringArgumentType.word()).then(Commands.argument("command", StringArgumentType.greedyString())
						.executes(TestHarness::capture))))
				.then(Commands.literal("lightexact").then(Commands.argument("x0", IntegerArgumentType.integer()).then(Commands.argument("x1", IntegerArgumentType.integer())
						.executes(TestHarness::lightExact))))
				.then(Commands.literal("ctxbench").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(TestHarness::ctxBench)))
				.then(Commands.literal("claim").then(xyz(TestHarness::claim)))
				.then(Commands.literal("contextsafety").executes(TestHarness::contextSafety))
				.then(Commands.literal("activity").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::activity)))
				.then(Commands.literal("later").then(Commands.argument("ticks", IntegerArgumentType.integer(1))
						.then(Commands.argument("command", StringArgumentType.greedyString()).executes(TestHarness::schedule))))
				.then(Commands.literal("siege").then(Commands.argument("label", StringArgumentType.word()).executes(ctx -> reply(ctx,
						"siege " + StringArgumentType.getString(ctx, "label") + " " + dev.romoslayer.daybreak.sleep.SiegeClock.status(overworld(ctx))))))
				.then(Commands.literal("siegeforce").then(Commands.literal("on").executes(ctx -> {
					dev.romoslayer.daybreak.sleep.SiegeClock.setForceWin(true);
					return reply(ctx, "siege rolls always succeed");
				})).then(Commands.literal("off").executes(ctx -> {
					dev.romoslayer.daybreak.sleep.SiegeClock.setForceWin(false);
					return reply(ctx, "siege rolls normal");
				})))
				.then(Commands.literal("sleep").then(Commands.argument("name", StringArgumentType.word()).then(xyz(TestHarness::sleep))))
				.then(Commands.literal("stats").executes(TestHarness::stats))
				.then(Commands.literal("clock").executes(TestHarness::clock))
				.then(Commands.literal("crops").then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
						.then(Commands.argument("radius", IntegerArgumentType.integer(1)).executes(TestHarness::crops)))))
				.then(Commands.literal("daytime").then(Commands.literal("off").executes(ctx -> {
					SeasonfallBridge.setTestOverride(null);
					return reply(ctx, "daytime normal");
				})).then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0.05, 0.95)).executes(ctx -> {
					SeasonfallBridge.setTestOverride(DoubleArgumentType.getDouble(ctx, "fraction"));
					return reply(ctx, "daytime " + DoubleArgumentType.getDouble(ctx, "fraction"));
				})))
				.then(Commands.literal("time").then(Commands.argument("x", DoubleArgumentType.doubleArg()).executes(ctx ->
						reply(ctx, "time " + DaybreakCommands.describeTime(overworld(ctx), DoubleArgumentType.getDouble(ctx, "x"))))))
				.then(Commands.literal("phantomtick").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(ctx -> runSpawner(ctx, "phantom",
						PhantomSpawner::new))))
				.then(Commands.literal("patroltick").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(ctx -> runSpawner(ctx, "patrol",
						PatrolSpawner::new))))
				.then(Commands.literal("power").then(xyz(TestHarness::power)))
				.then(Commands.literal("mob").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::mob)))
				.then(Commands.literal("sleepinfo").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::sleepInfo)))
				.then(Commands.literal("insomnia").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::insomnia)))
				.then(Commands.literal("entities").then(Commands.argument("type", StringArgumentType.word()).then(Commands.argument("x", DoubleArgumentType.doubleArg())
						.then(Commands.argument("z", DoubleArgumentType.doubleArg()).then(Commands.argument("radius", IntegerArgumentType.integer(1))
								.executes(TestHarness::entities))))))));
	}

	/** Runs a fresh copy of a vanilla custom spawner n times; a fresh one always attempts on its first tick. */
	private static int runSpawner(CommandContext<CommandSourceStack> ctx, String name, Supplier<CustomSpawner> spawner) {
		ServerLevel level = overworld(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		int spawned = 0;
		for (int i = 0; i < n; i++) {
			spawned += spawner.get().tick(level, true, true);
		}
		// What the phantom spawner checks for each player, to tell a Daybreak problem from vanilla's own conditions
		for (ServerPlayer player : level.players()) {
			BlockPos at = player.blockPosition();
			Daybreak.LOGGER.info("[test] spawnerplayer {} y={} seaLevel={} canSeeSky={} darken={} difficulty={} sinceRest={} spectator={}",
					player.getName().getString(), at.getY(), level.getSeaLevel(), level.canSeeSky(at),
					RegionalLight.skyDarken(level, player.getX(), player.getY(), player.getZ()), level.getCurrentDifficultyAt(at).getEffectiveDifficulty(),
					player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), player.isSpectator());
		}
		return reply(ctx, name + "tick ran " + n + " spawned=" + spawned);
	}

	/** Wheat around (x, z) at surface level: how many are fully grown, and the average growth stage. */
	private static int crops(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		int cx = (int) Math.floor(DoubleArgumentType.getDouble(ctx, "x"));
		int cz = (int) Math.floor(DoubleArgumentType.getDouble(ctx, "z"));
		int r = IntegerArgumentType.getInteger(ctx, "radius");
		int total = 0;
		int ripe = 0;
		int stages = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockState state = level.getBlockState(new BlockPos(cx + dx, -60, cz + dz));
				if (state.getBlock() instanceof CropBlock crop && state.hasProperty(BlockStateProperties.AGE_7)) {
					total++;
					stages += state.getValue(BlockStateProperties.AGE_7);
					if (crop.isMaxAge(state)) {
						ripe++;
					}
				}
			}
		}
		return reply(ctx, String.format(Locale.ROOT, "crops x=%d local=%d wheat=%d ripe=%d averageStage=%.2f", cx, local(level, cx), total, ripe,
				total == 0 ? 0.0 : stages / (double) total));
	}

	private static int clock(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		long world = RegionalTime.worldClock(level);
		return reply(ctx, String.format(Locale.ROOT, "clock world=%d timeOfDay=%d day=%d raining=%s thundering=%s", world, RegionalTime.timeOfDay(world),
				RegionalTime.day(world), level.isRaining(), level.isThundering()));
	}

	private static int power(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		BlockState state = level.getBlockState(pos);
		Object power = state.hasProperty(BlockStateProperties.POWER) ? state.getValue(BlockStateProperties.POWER) : "none";
		return reply(ctx, String.format(Locale.ROOT, "power x=%d local=%d %s", pos.getX(), local(level, pos.getX()), power));
	}

	/** Fire ticks and health of the named mob (CustomName). */
	private static int mob(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && entity.hasCustomName() && name.equals(entity.getCustomName().getString())) {
				return reply(ctx, String.format(Locale.ROOT, "mob %s x=%.0f local=%d fire=%d health=%.1f", name, entity.getX(), local(level, entity.getX()),
						entity.getRemainingFireTicks(), living.getHealth()));
			}
		}
		return reply(ctx, "mob " + name + " not found");
	}

	private static int sleepInfo(CommandContext<CommandSourceStack> ctx) {
		Bots.Bot bot = Bots.BOTS.get(StringArgumentType.getString(ctx, "name"));
		if (bot == null) {
			return reply(ctx, "no such bot");
		}
		return reply(ctx, String.format(Locale.ROOT, "sleepinfo %s sleeping=%s timer=%d x=%.1f local=%d", StringArgumentType.getString(ctx, "name"),
				bot.player.isSleeping(), bot.player.getSleepTimer(), bot.player.getX(), local(bot.player.serverLevel(), bot.player.getX())));
	}

	/** Makes a bot as tired as a player who has not slept for ten days (phantoms may come). */
	private static int insomnia(CommandContext<CommandSourceStack> ctx) {
		Bots.Bot bot = Bots.BOTS.get(StringArgumentType.getString(ctx, "name"));
		if (bot == null) {
			return reply(ctx, "no such bot");
		}
		bot.player.getStats().setValue(bot.player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 240000);
		return reply(ctx, "insomnia " + StringArgumentType.getString(ctx, "name"));
	}

	private static int entities(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		String type = StringArgumentType.getString(ctx, "type");
		double x = DoubleArgumentType.getDouble(ctx, "x");
		double z = DoubleArgumentType.getDouble(ctx, "z");
		int r = IntegerArgumentType.getInteger(ctx, "radius");
		int n = 0;
		for (Entity entity : level.getEntities((Entity) null, box(level, x, z, r), e -> EntityType.getKey(e.getType()).getPath().equals(type))) {
			n++;
		}
		return reply(ctx, String.format(Locale.ROOT, "entities %s x=%.0f local=%d count=%d", type, x, local(level, x), n));
	}

	/** The clock a player's game should be showing: their local solar clock, or the world clock outside regional dimensions. */
	static long expectedClientClock(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		long world = RegionalTime.worldClock(level);
		return RegionalTime.isRegional(level) ? RegionalTime.solarClock(world, player.getX()) : world;
	}

	private static RequiredArgumentBuilder<CommandSourceStack, Double> xyz(com.mojang.brigadier.Command<CommandSourceStack> command) {
		return Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("y", DoubleArgumentType.doubleArg())
				.then(Commands.argument("z", DoubleArgumentType.doubleArg()).executes(command)));
	}

	private static ServerLevel overworld(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getServer().overworld();
	}

	private static BlockPos pos(CommandContext<CommandSourceStack> ctx) {
		return BlockPos.containing(DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"));
	}

	private static AABB box(ServerLevel level, double x, double z, int r) {
		return new AABB(x - r, level.getMinBuildHeight(), z - r, x + r, level.getMaxBuildHeight(), z + r);
	}

	private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		Daybreak.LOGGER.info("[test] {}", text);
		return 1;
	}

	private static long local(ServerLevel level, double x) {
		return RegionalTime.timeOfDay(RegionalTime.solarClock(RegionalTime.worldClock(level), x));
	}

	/**
	 * What vanilla's day/night checks answer at a position, asked the way the game asks them (with that position as the
	 * context): the same keys as the 26.x probe. Sun angle in degrees (0 = noon, 180 = midnight); monsters burn by day.
	 */
	private static int probe(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		Vec3 center = Vec3.atCenterOf(pos);
		Object[] here = RegionalContext.with(level, center, () -> new Object[] {level.isDay(), level.getSkyDarken(), level.getMaxLocalRawBrightness(pos),
				level.getTimeOfDay(1.0F) * 360.0F, level.getMoonPhase(), Schedule.VILLAGER_DEFAULT.getActivityAt((int) Math.floorMod(LegacyTime.forContext(
						level.getDayTime()), 24000L)).getName(), level.isNight()});
		boolean bright = (Boolean) here[0];
		return reply(ctx, String.format(Locale.ROOT,
				"probe x=%d y=%d z=%d local=%d global=%d | darken=%d (global %d) contextDarken=%d | rawMax=%d storedSky=%d block=%d"
						+ " | bright=%s (global %s) canSleep=%s | monstersBurn=%s sunAngle=%.1f moon=%s villager=%s bees=%s",
				pos.getX(), pos.getY(), pos.getZ(), local(level, center.x), local(level, 0), RegionalLight.skyDarken(level, center.x, center.y, center.z),
				level.getSkyDarken(), here[1], here[2], level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos),
				bright, level.isDay(), !bright, bright, here[3], here[4], here[5], here[6]));
	}

	private static int bot(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		double x = DoubleArgumentType.getDouble(ctx, "x");
		double z = DoubleArgumentType.getDouble(ctx, "z");
		level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		Bots.Bot bot = Bots.spawn(ctx.getSource().getServer(), level, name, x, y, z);
		return reply(ctx, String.format(Locale.ROOT, "bot %s at %.1f %.1f %.1f", name, bot.player.getX(), bot.player.getY(), bot.player.getZ()));
	}

	private static int bots(CommandContext<CommandSourceStack> ctx) {
		StringBuilder out = new StringBuilder("bots");
		for (Map.Entry<String, Bots.Bot> entry : Bots.BOTS.entrySet()) {
			Bots.Bot bot = entry.getValue();
			ServerLevel level = bot.player.serverLevel();
			long client = bot.clientClock(level.getGameTime());
			out.append(String.format(Locale.ROOT, " | %s dim=%s x=%.1f packets=%d maxDrift=%d rate=%.3f clientTimeOfDay=%d expected=%d", entry.getKey(),
					level.dimension().location().getPath(), bot.player.getX(), bot.timePackets, bot.maxDrift, bot.rate(),
					client == Long.MIN_VALUE ? -1 : RegionalTime.timeOfDay(client), RegionalTime.timeOfDay(expectedClientClock(bot.player))));
		}
		return reply(ctx, out.toString());
	}

	private static int chat(CommandContext<CommandSourceStack> ctx) {
		Bots.Bot bot = Bots.BOTS.get(StringArgumentType.getString(ctx, "name"));
		return reply(ctx, bot == null ? "no such bot" : "chat " + StringArgumentType.getString(ctx, "name") + " " + bot.chat);
	}

	private static int relog(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		if (!Bots.BOTS.containsKey(name)) {
			return reply(ctx, "no such bot");
		}
		Bots.Bot bot = Bots.relog(ctx.getSource().getServer(), name);
		long client = bot.clientClock(bot.player.serverLevel().getGameTime());
		return reply(ctx, String.format(Locale.ROOT, "relog %s x=%.1f timePacketsOnJoin=%d clientTimeOfDay=%d expected=%d", name, bot.player.getX(),
				bot.timePackets, client == Long.MIN_VALUE ? -1 : RegionalTime.timeOfDay(client), RegionalTime.timeOfDay(expectedClientClock(bot.player))));
	}

	private static int walk(CommandContext<CommandSourceStack> ctx) {
		Bots.Bot bot = Bots.BOTS.get(StringArgumentType.getString(ctx, "name"));
		if (bot == null) {
			return reply(ctx, "no such bot");
		}
		bot.walkPerTick = DoubleArgumentType.getDouble(ctx, "blocksPerTick");
		bot.walkTicksLeft = IntegerArgumentType.getInteger(ctx, "ticks");
		return reply(ctx, "walking");
	}

	private static int count(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		double x = DoubleArgumentType.getDouble(ctx, "x");
		double z = DoubleArgumentType.getDouble(ctx, "z");
		int r = IntegerArgumentType.getInteger(ctx, "radius");
		int total = 0;
		int surface = 0;
		int burning = 0;
		Map<String, Integer> types = new TreeMap<>();
		for (Monster monster : level.getEntitiesOfClass(Monster.class, box(level, x, z, r), m -> true)) {
			total++;
			if (level.canSeeSky(monster.blockPosition())) {
				surface++;
				Daybreak.LOGGER.info("[test] surface {} at {} age {} local {}", EntityType.getKey(monster.getType()).getPath(), monster.blockPosition(),
						monster.tickCount, local(level, monster.getX()));
			}
			if (monster.isOnFire()) {
				burning++;
			}
			types.merge(EntityType.getKey(monster.getType()).getPath(), 1, Integer::sum);
		}
		return reply(ctx, String.format(Locale.ROOT, "count x=%.0f z=%.0f r=%d local=%d monsters=%d surface=%d covered=%d burning=%d types=%s", x, z, r,
				local(level, x), total, surface, total - surface, burning, types));
	}

	/**
	 * Vanilla's zombie spawn rules, asked the way natural spawning asks them (through SpawnPlacements) and with no context
	 * of the harness's own, so the result shows what the game does at that exact position.
	 */
	private static int spawnCheck(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		RandomSource random = RandomSource.create(42L);
		int pass = 0;
		int dark = 0;
		for (int i = 0; i < n; i++) {
			if (SpawnPlacements.checkSpawnRules(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, pos, random)) {
				pass++;
			}
			if (Monster.isDarkEnoughToSpawn(level, pos, random)) {
				dark++;
			}
		}
		return reply(ctx, String.format(Locale.ROOT, "spawncheck x=%d y=%d z=%d local=%d n=%d zombieRulesPass=%d darkEnough=%d", pos.getX(), pos.getY(),
				pos.getZ(), local(level, pos.getX()), n, pass, dark));
	}

	/**
	 * Light at a position: read with no context, and (when a context x is given) from code running somewhere else. The
	 * two-argument read with an explicit darkening of 10 (vanilla's thunderstorm spawning) must not change.
	 */
	private static int lightRead(CommandContext<CommandSourceStack> ctx, boolean withContext) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		int bare = level.getMaxLocalRawBrightness(pos);
		int explicit = level.getMaxLocalRawBrightness(pos, 10);
		String inContext = "-";
		if (withContext) {
			double cx = DoubleArgumentType.getDouble(ctx, "contextX");
			inContext = Integer.toString(RegionalContext.with(level, new Vec3(cx, pos.getY() + 0.5, pos.getZ() + 0.5), () -> level.getMaxLocalRawBrightness(pos)));
		}
		return reply(ctx, String.format(Locale.ROOT, "lightread x=%d local=%d rawMax=%d inContext=%s explicit10=%d storedSky=%d", pos.getX(),
				local(level, pos.getX() + 0.5), bare, inContext, explicit, level.getBrightness(LightLayer.SKY, pos)));
	}

	/** A server command to run a number of ticks from now (scenarios that need exact timing, which RCON cannot give). */
	private record Scheduled(int at, String command) {
	}

	private static final java.util.List<Scheduled> SCHEDULED = new java.util.ArrayList<>();

	private static int schedule(CommandContext<CommandSourceStack> ctx) {
		int at = ctx.getSource().getServer().getTickCount() + IntegerArgumentType.getInteger(ctx, "ticks");
		SCHEDULED.add(new Scheduled(at, StringArgumentType.getString(ctx, "command")));
		return reply(ctx, "later at tick " + at);
	}

	private static void runScheduled(net.minecraft.server.MinecraftServer server) {
		for (Scheduled due : java.util.List.copyOf(SCHEDULED)) {
			if (server.getTickCount() >= due.at()) {
				SCHEDULED.remove(due);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), due.command());
			}
		}
	}

	/**
	 * Contexts come back even when the code run inside them throws (what the bed and tick wrappers rely on), and nested
	 * contexts unwind to the outer one.
	 */
	private static int contextSafety(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		Vec3 before = RegionalContext.get(level);
		try {
			RegionalContext.with(level, new Vec3(12000.5, -59.5, 0.5), () -> {
				throw new IllegalStateException("thrown inside a context");
			});
		} catch (IllegalStateException expected) {
			// what a mod catching its own exception would do
		}
		Vec3 afterThrow = RegionalContext.get(level);
		boolean nestedRestored = RegionalContext.with(level, new Vec3(1.5, 0.5, 0.5), () -> {
			RegionalContext.with(level, new Vec3(2.5, 0.5, 0.5), () -> 0);
			Vec3 now = RegionalContext.get(level);
			return now != null && now.x == 1.5;
		});
		return reply(ctx, String.format(Locale.ROOT, "contextsafety before=%s afterThrow=%s nestedRestored=%s after=%s", before, afterThrow, nestedRestored,
				RegionalContext.get(level)));
	}

	/** What the named mob's brain is doing now (a villager: work, meet, idle or rest), as its own AI decided it. */
	private static int activity(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && entity.hasCustomName() && name.equals(entity.getCustomName().getString())) {
				String activity = living.getBrain().getActiveNonCoreActivity().map(a -> a.getName()).orElse("none");
				return reply(ctx, String.format(Locale.ROOT, "activity %s x=%.0f local=%d %s", name, entity.getX(), local(level, entity.getX()), activity));
			}
		}
		return reply(ctx, "activity " + name + " not found");
	}

	/**
	 * Claims the point of interest at a position (a bell, bed or job site) as a villager would. Vanilla counts only claimed
	 * ones as a village, and villagers of their own would be killed by the very sieges being tested.
	 */
	private static int claim(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		boolean taken = level.getPoiManager().take(type -> true, (type, at) -> at.equals(pos), pos, 1).isPresent();
		return reply(ctx, String.format(Locale.ROOT, "claim x=%d z=%d %s", pos.getX(), pos.getZ(), taken ? "taken" : "nothing to take"));
	}

	/**
	 * Nanoseconds per call (median of 7 timed rounds after 3 warm-up rounds) of the context wrapper and the positional
	 * light lookup, in a regional dimension (the overworld) and outside one (the Nether): the measurements behind the
	 * optimisation decisions.
	 */
	private static int ctxBench(CommandContext<CommandSourceStack> ctx) {
		ServerLevel overworld = overworld(ctx);
		ServerLevel nether = ctx.getSource().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		long[] sink = {0};
		java.util.function.Supplier<Integer> work = () -> (int) (sink[0] & 1);
		double bare = nsPerCall(n, i -> sink[0] += work.get());
		double isRegional = nsPerCall(n, i -> sink[0] += dev.romoslayer.daybreak.time.RegionalTime.isRegional(overworld) ? 1 : 0);
		double withRegional = nsPerCall(n, i -> sink[0] += RegionalContext.with(overworld, new Vec3(i & 1023, 64, 0), work));
		double withOther = nsPerCall(n, i -> sink[0] += RegionalContext.with(nether, new Vec3(i & 1023, 64, 0), work));
		double darkenOneBucket = nsPerCall(n, i -> sink[0] += RegionalLight.skyDarken(overworld, 0.5 + (i & 7), 64, 0.5));
		double darken64Buckets = nsPerCall(n, i -> sink[0] += RegionalLight.skyDarken(overworld, 0.5 + (i & 63) * 1000, 64, 0.5));
		double darkenUncached = nsPerCall(n, i -> sink[0] += dev.romoslayer.daybreak.mc.Versioned.skyDarkenAt(overworld, 0.5 + (i & 63) * 1000, 64, 0.5));
		double darkenOther = nsPerCall(n, i -> sink[0] += RegionalLight.skyDarken(nether, 0.5 + (i & 63) * 1000, 64, 0.5));
		return reply(ctx, String.format(Locale.ROOT, "ctxbench n=%d bare=%.1f isRegional=%.1f withRegional=%.1f withOther=%.1f darkenOneBucket=%.1f"
				+ " darken64Buckets=%.1f darkenUncached=%.1f darkenOther=%.1f (sink %d)", n, bare, isRegional, withRegional, withOther, darkenOneBucket,
				darken64Buckets, darkenUncached, darkenOther, sink[0]));
	}

	private static double nsPerCall(int n, java.util.function.IntConsumer body) {
		double[] rounds = new double[7];
		for (int round = -3; round < rounds.length; round++) {
			long start = System.nanoTime();
			for (int i = 0; i < n; i++) {
				body.accept(i);
			}
			if (round >= 0) {
				rounds[round] = (System.nanoTime() - start) / (double) n;
			}
		}
		java.util.Arrays.sort(rounds);
		return rounds[rounds.length / 2];
	}

	/**
	 * The cached positional darkening against working it out afresh, at every block from x0 to x1 (in one tick, so one
	 * cache): how many differ, and how many different values the range covers (a whole day has dawn and dusk in it).
	 */
	private static int lightExact(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		int x0 = IntegerArgumentType.getInteger(ctx, "x0");
		int x1 = IntegerArgumentType.getInteger(ctx, "x1");
		int mismatches = 0;
		java.util.BitSet values = new java.util.BitSet();
		for (int x = x0; x < x1; x++) {
			int cached = RegionalLight.skyDarken(level, x + 0.5, 64.5, 0.5);
			int fresh = dev.romoslayer.daybreak.mc.Versioned.skyDarkenAt(level, x + 0.5, 64.5, 0.5);
			values.set(fresh);
			if (cached != fresh) {
				mismatches++;
			}
		}
		return reply(ctx, String.format(Locale.ROOT, "lightexact x0=%d x1=%d checked=%d mismatches=%d values=%d", x0, x1, x1 - x0, mismatches,
				values.cardinality()));
	}

	/**
	 * Runs a command and logs its answers, which otherwise only go to the sender (NeoForge's RCON often returns none), each
	 * as "capture <label> <text>" with its lines joined by " | ".
	 */
	private static int capture(CommandContext<CommandSourceStack> ctx) {
		String label = StringArgumentType.getString(ctx, "label");
		String command = StringArgumentType.getString(ctx, "command");
		// Logged as each answer arrives: since 1.20.3 a command run from inside another one only runs once that one is done
		net.minecraft.commands.CommandSource sink = new net.minecraft.commands.CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				Daybreak.LOGGER.info("[test] capture {} {}", label, message.getString().replace("\n", " | "));
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource().withSource(sink), command);
		return reply(ctx, "capturing " + label);
	}

	/** A block's full state, such as a turtle egg's hatch stage. */
	private static int block(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		return reply(ctx, String.format(Locale.ROOT, "block x=%d local=%d %s village=%s", pos.getX(), local(level, pos.getX() + 0.5), level.getBlockState(pos),
				level.isVillage(pos)));
	}

	/** Moves a bot next to a bed and uses it, as a right click would (as the bot's own action, so in its context). */
	private static int sleep(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		Bots.Bot bot = Bots.BOTS.get(name);
		if (bot == null) {
			return reply(ctx, "no such bot");
		}
		Bots.teleport(bot, level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.5);
		BlockState state = level.getBlockState(pos);
		var result = RegionalContext.with(level, bot.player.position(),
				() -> Legacy.useBlock(state, level, bot.player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
		return reply(ctx, String.format(Locale.ROOT, "sleep %s at x=%d local=%d result=%s sleeping=%s", name, pos.getX(), local(level, pos.getX()),
				result, bot.player.isSleeping()));
	}

	private static int stats(CommandContext<CommandSourceStack> ctx) {
		var server = ctx.getSource().getServer();
		return reply(ctx, String.format(Locale.ROOT, "stats tick=%d mspt=%.3f players=%d", server.getTickCount(), Legacy.msPerTick(server),
				server.getPlayerCount()));
	}
}
