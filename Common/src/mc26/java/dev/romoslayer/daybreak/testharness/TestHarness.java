package dev.romoslayer.daybreak.testharness;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.romoslayer.daybreak.Daybreak;
import dev.romoslayer.daybreak.command.DaybreakCommands;
import dev.romoslayer.daybreak.context.RegionalContext;
import dev.romoslayer.daybreak.light.RegionalLight;
import dev.romoslayer.daybreak.time.RegionalTime;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only test commands ({@code /daybreak test ...}) for live tests driven over RCON. Never in a release jar:
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
				.then(Commands.literal("bench").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(TestHarness::bench)))
				.then(Commands.literal("stats").executes(TestHarness::stats))
				.then(Commands.literal("clock").executes(TestHarness::clock))
				.then(Commands.literal("crops").then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
						.then(Commands.argument("radius", IntegerArgumentType.integer(1)).executes(TestHarness::crops)))))
				.then(Commands.literal("daytime").then(Commands.literal("off").executes(ctx -> {
					dev.romoslayer.daybreak.compat.seasonfall.SeasonfallBridge.setTestOverride(null);
					return reply(ctx, "daytime normal");
				})).then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0.05, 0.95)).executes(ctx -> {
					dev.romoslayer.daybreak.compat.seasonfall.SeasonfallBridge.setTestOverride(DoubleArgumentType.getDouble(ctx, "fraction"));
					return reply(ctx, "daytime " + DoubleArgumentType.getDouble(ctx, "fraction"));
				})))
				.then(Commands.literal("time").then(Commands.argument("x", DoubleArgumentType.doubleArg()).executes(ctx ->
						reply(ctx, "time " + DaybreakCommands.describeTime(overworld(ctx), DoubleArgumentType.getDouble(ctx, "x"))))))
				.then(Commands.literal("phantomtick").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(ctx -> runSpawner(ctx, "phantom",
						() -> new net.minecraft.world.level.levelgen.PhantomSpawner()))))
				.then(Commands.literal("patroltick").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(ctx -> runSpawner(ctx, "patrol",
						() -> new net.minecraft.world.level.levelgen.PatrolSpawner()))))
				.then(Commands.literal("power").then(xyz(TestHarness::power)))
				.then(Commands.literal("mob").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::mob)))
				.then(Commands.literal("sleepinfo").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::sleepInfo)))
				.then(Commands.literal("insomnia").then(Commands.argument("name", StringArgumentType.word()).executes(TestHarness::insomnia)))
				.then(Commands.literal("entities").then(Commands.argument("type", StringArgumentType.word()).then(Commands.argument("x", DoubleArgumentType.doubleArg())
						.then(Commands.argument("z", DoubleArgumentType.doubleArg()).then(Commands.argument("radius", IntegerArgumentType.integer(1))
								.executes(TestHarness::entities))))))));
	}

	/** Runs a fresh copy of a vanilla custom spawner n times; a fresh one always attempts on its first tick. */
	private static int runSpawner(CommandContext<CommandSourceStack> ctx, String name,
			java.util.function.Supplier<net.minecraft.world.level.CustomSpawner> spawner) {
		ServerLevel level = overworld(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		for (int i = 0; i < n; i++) {
			spawner.get().tick(level, true);
		}
		return reply(ctx, name + "tick ran " + n);
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
				if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) {
					total++;
					int age = crop.getAge(state);
					stages += age;
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
		Object power = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER)
				? state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER) : "none";
		return reply(ctx, String.format(Locale.ROOT, "power x=%d local=%d %s", pos.getX(), local(level, pos.getX()), power));
	}

	/** Fire ticks and health of the named mob (CustomName). */
	private static int mob(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
			if (entity instanceof net.minecraft.world.entity.LivingEntity living && entity.hasCustomName()
					&& name.equals(entity.getCustomName().getString())) {
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
				bot.player.isSleeping(), bot.player.getSleepTimer(), bot.player.getX(), local(bot.player.level(), bot.player.getX())));
	}

	/** Makes a bot as tired as a player who has not slept for ten days (phantoms may come). */
	private static int insomnia(CommandContext<CommandSourceStack> ctx) {
		Bots.Bot bot = Bots.BOTS.get(StringArgumentType.getString(ctx, "name"));
		if (bot == null) {
			return reply(ctx, "no such bot");
		}
		bot.player.getStats().setValue(bot.player, net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST), 240000);
		return reply(ctx, "insomnia " + StringArgumentType.getString(ctx, "name"));
	}

	private static int entities(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		String type = StringArgumentType.getString(ctx, "type");
		double x = DoubleArgumentType.getDouble(ctx, "x");
		double z = DoubleArgumentType.getDouble(ctx, "z");
		int r = IntegerArgumentType.getInteger(ctx, "radius");
		int n = 0;
		for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null,
				new AABB(x - r, level.getMinY(), z - r, x + r, level.getMaxY(), z + r), e -> EntityType.getKey(e.getType()).getPath().equals(type))) {
			n++;
		}
		return reply(ctx, String.format(Locale.ROOT, "entities %s x=%.0f local=%d count=%d", type, x, local(level, x), n));
	}

	/** The clock a player's game should be showing: their local solar clock, or the world clock outside regional dimensions. */
	static long expectedClientClock(ServerPlayer player) {
		ServerLevel level = player.level();
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

	private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		Daybreak.LOGGER.info("[test] {}", text);
		return 1;
	}

	private static long local(ServerLevel level, double x) {
		return RegionalTime.timeOfDay(RegionalTime.solarClock(RegionalTime.worldClock(level), x));
	}

	private static int probe(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		Vec3 center = Vec3.atCenterOf(pos);
		boolean[] here = new boolean[2];
		RegionalContext.run(level, center, () -> {
			here[0] = level.isBrightOutside();
			here[1] = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos).canSleep(level);
		});
		return reply(ctx, String.format(Locale.ROOT,
				"probe x=%d y=%d z=%d local=%d global=%d | skyLight=%.3f (global %.3f) darken=%d (global %d) | rawMax=%d effSky=%d storedSky=%d block=%d"
						+ " | bright=%s (global %s) canSleep=%s | monstersBurn=%s sunAngle=%.1f moon=%s villager=%s bees=%s",
				pos.getX(), pos.getY(), pos.getZ(), local(level, center.x), local(level, 0),
				level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, center),
				level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL),
				RegionalLight.skyDarken(level, center.x, center.y, center.z), level.getSkyDarken(), level.getMaxLocalRawBrightness(pos),
				level.getEffectiveSkyBrightness(pos), level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos),
				here[0], level.isBrightOutside(), here[1], level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, center),
				level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, center),
				level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, center),
				level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, center),
				level.environmentAttributes().getValue(EnvironmentAttributes.BEES_STAY_IN_HIVE, center)));
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
			ServerLevel level = bot.player.level();
			long client = bot.clientClock(level.getGameTime());
			out.append(String.format(Locale.ROOT, " | %s dim=%s x=%.1f packets=%d maxDrift=%d rate=%.3f clientTimeOfDay=%d expected=%d", entry.getKey(),
					level.dimension().identifier().getPath(), bot.player.getX(), bot.timePackets, bot.maxDrift, bot.lastClock == null ? 0f : bot.lastClock.rate(),
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
		long client = bot.clientClock(bot.player.level().getGameTime());
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
		for (Monster monster : level.getEntitiesOfClass(Monster.class, new AABB(x - r, level.getMinY(), z - r, x + r, level.getMaxY(), z + r), m -> true)) {
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

	private static int spawnCheck(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		BlockPos pos = pos(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		RandomSource random = RandomSource.create(42L);
		int pass = 0;
		int dark = 0;
		for (int i = 0; i < n; i++) {
			if (Monster.checkMonsterSpawnRules(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, pos, random)) {
				pass++;
			}
			if (Monster.isDarkEnoughToSpawn(level, pos, random)) {
				dark++;
			}
		}
		return reply(ctx, String.format(Locale.ROOT, "spawncheck x=%d y=%d z=%d local=%d n=%d zombieRulesPass=%d darkEnough=%d", pos.getX(), pos.getY(),
				pos.getZ(), local(level, pos.getX()), n, pass, dark));
	}

	/** Moves a bot next to a bed and uses it, as a right click would (bots stay in creative, which may sleep). */
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
		var result = state.useWithoutItem(level, bot.player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		return reply(ctx, String.format(Locale.ROOT, "sleep %s at x=%d local=%d result=%s sleeping=%s", name, pos.getX(), local(level, pos.getX()),
				result.getClass().getSimpleName(), bot.player.isSleeping()));
	}

	private static int bench(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = overworld(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		BlockPos[] positions = Bots.BOTS.values().stream().map(b -> b.player.blockPosition()).toArray(BlockPos[]::new);
		if (positions.length == 0) {
			positions = new BlockPos[] {BlockPos.ZERO};
		}
		long sink = 0;
		for (int warm = 0; warm < 3; warm++) {
			for (int i = 0; i < n; i++) {
				BlockPos p = positions[i % positions.length].offset(i & 15, 0, (i >> 4) & 15);
				sink += level.getMaxLocalRawBrightness(p) + level.getMaxLocalRawBrightness(p, level.getSkyDarken());
			}
		}
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			BlockPos p = positions[i % positions.length].offset(i & 15, 0, (i >> 4) & 15);
			sink += level.getMaxLocalRawBrightness(p, level.getSkyDarken());
		}
		long t1 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			BlockPos p = positions[i % positions.length].offset(i & 15, 0, (i >> 4) & 15);
			sink += level.getMaxLocalRawBrightness(p);
		}
		long t2 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			BlockPos p = positions[i % positions.length].offset(i & 15, 0, (i >> 4) & 15);
			sink += level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, Vec3.atCenterOf(p)) ? 1 : 0;
		}
		long t3 = System.nanoTime();
		return reply(ctx, String.format(Locale.ROOT, "bench n=%d vanillaLight=%.1fns regionalLight=%.1fns monstersBurnAttr=%.1fns (sink %d)", n,
				(t1 - t0) / (double) n, (t2 - t1) / (double) n, (t3 - t2) / (double) n, sink));
	}

	private static int stats(CommandContext<CommandSourceStack> ctx) {
		var server = ctx.getSource().getServer();
		return reply(ctx, String.format(Locale.ROOT, "stats tick=%d mspt=%.3f players=%d", server.getTickCount(),
				server.getAverageTickTimeNanos() / 1_000_000.0, server.getPlayerCount()));
	}
}
