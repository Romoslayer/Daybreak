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

	/** Vanilla's zombie spawn rules, asked inside the chunk's context as natural spawning asks them. */
	private static int spawnCheck(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		RandomSource random = RandomSource.create(42L);
		int[] counts = new int[2];
		RegionalContext.run(level, new Vec3((pos.getX() & ~15) + 8.5, 64.0, (pos.getZ() & ~15) + 8.5), () -> {
			for (int i = 0; i < n; i++) {
				if (Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, pos, random)) {
					counts[0]++;
				}
				if (Monster.isDarkEnoughToSpawn(level, pos, random)) {
					counts[1]++;
				}
			}
		});
		return reply(ctx, String.format(Locale.ROOT, "spawncheck x=%d y=%d z=%d local=%d n=%d zombieRulesPass=%d darkEnough=%d", pos.getX(), pos.getY(),
				pos.getZ(), local(level, pos.getX()), n, counts[0], counts[1]));
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
