# Daybreak

Regional day and night for Minecraft servers: **26.3, 26.2, 1.21.1 and 1.20.1**, on **Fabric, NeoForge and Forge**. The
time of day depends on how far east or west you are: it can be noon in one place and midnight in another, in the same
world, at the same moment. Players join with a completely unmodified game: no client mod, resource pack or client
networking is needed.

There is still one world clock. Every place's local time is that clock plus an offset from its x coordinate (by default
24 000 blocks east is one day later). The change is continuous, with no time zones or jumps, and every place keeps
running through its own normal day and night.

## What follows the local time

| | |
|---|---|
| **Sky** | Each player's game is sent their own local time, so the sun, moon, stars and sky light are right where they stand. Walking east, the sun moves ahead smoothly. |
| **Monster spawning** | Natural spawning uses the light of the place itself, so monsters appear on the surface at local night and not by local day. Caves, mob caps, biomes and spawn rules are vanilla. |
| **Sunlight** | Zombies and skeletons burn in local daylight. Helmets, shade, rain and water still protect them, as in vanilla. |
| **Beds** | A bed works when it is night (or storming) where it stands. |
| **Daylight detectors** | Follow the local sun. |
| **Mobs and villagers** | Every behaviour that depends on day or night (villager schedules, bees, foxes, endermen, drowned, creaking hearts, eyeblossoms, cats' morning gifts) follows the local time, for modded mobs too. |
| **Phantoms, patrols, sieges** | Phantoms come at local night, pillager patrols by local day, and a village can be sieged at its own midnight. |

Unchanged: the tick rate, redstone, crops, furnaces, world age, seasons and weather. The Nether and the End keep their
normal time (other dimensions can be added in the config).

## Sleeping

There is one world clock, so skipping a night moves it for everyone. Daybreak's rules:

- You can sleep when it is night (or storming) where you are; spawn points and resetting phantoms work as usual.
- The night is skipped only when **every player in the dimension is in local night** and enough of them sleep (the
  `playersSleepingPercentage` game rule). Otherwise sleepers are told why it can't be skipped.
- The clock then moves until the sleeper furthest from morning reaches 6 AM. Everyone moves by the same amount, so
  players further east wake later in their morning.
- Sleeping through a daytime thunderstorm clears the storm and wakes everyone without moving the clock.
- After a skip, another one has to wait 10 minutes of normal time (`sleepSkipCooldownTicks`), so a player cannot
  skip, travel west to where it is night again and skip again to race the clock ahead.
- `allowSleepSkip: false` in the config: beds never skip the night.

## Settings

`config/daybreak.json`, with a comment above every option:

| Option | Default | |
|---|---|---|
| `enabled` | `true` | `false` gives vanilla time everywhere (takes effect on `/daybreak reload`) |
| `dimensions` | `["minecraft:overworld"]` | dimensions with regional time (restart to change) |
| `blocksPerDay` | `24000` | blocks east or west for one full day of difference |
| `direction` | `EAST_LATER` | or `WEST_LATER` |
| `allowSleepSkip` | `true` | whether sleeping can skip the night |
| `sleepSkipCooldownTicks` | `12000` | after a skip, ticks of normal time before another skip (10 minutes; 0 = none) |
| `resyncTicks` | `20` | how far a player's sky may drift before their time is resent |
| `seasonalDayLength` | `true` | use Seasonfall's seasonal day length per place |
| `enhancedCelestials` | `true` | regional lunar events with Enhanced Celestials |
| `debug` | `false` | extra logging |

With small `blocksPerDay` values (a few thousand blocks or less), the time changes noticeably within a player's view
distance: monsters at the edge of the view follow a different time than the player's own sky.

Commands (operators): `/daybreak time [x]` local time and day at a position, `/daybreak info`, `/daybreak debug <player>`
the time a player's game is showing, `/daybreak reload`.

## Compatibility

- **Seasonfall**: seasons are untouched. With a Seasonfall version that supports Daybreak, every place gets its own
  seasonally longer or shorter day; with older versions, switch Seasonfall's day length off.
- **Stormcell**: works alongside; storms over a player count as night for sleeping.
- **Enhanced Celestials 2** (Minecraft 26.x): lunar events are still decided once for the whole world, and each place
  gets the night's event during its own night, so a blood moon sweeps from east to west. If a future Enhanced Celestials
  version changes what Daybreak hooks into, Daybreak leaves its lunar events global instead of breaking anything.
- **Geyser**: Bedrock players should see their local time the same way (Geyser passes each player's time on).
- Other mods: anything that checks the light or day/night at a position, or from a mob's own tick, follows the local
  time automatically.

## Versions

| Minecraft | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.3 | ✓ | ✓ | ✓ |
| 26.2 | ✓ | ✓ | ✓ |
| 1.21.1 | ✓ | ✓ | ✓ |
| 1.20.1 | ✓ | ✓ (47.1) | ✓ |

Everything above works the same on every version, with two differences on 1.21.1 and 1.20.1: Enhanced Celestials 2
support is for 26.x only, and with Seasonfall's seasonal day length the players' games run their sky at normal speed and
are kept on their local time by the server's regular time updates (26.x clients are told the right speed instead).

## API

`dev.romoslayer.daybreak.api.DaybreakApi` (call directly or by reflection): `isRegional(level)`, `offsetTicks(level, x)`,
`localClock(level, x)`, `localTimeOfDay(level, x)`, `localDay(level, x)`.

## Building

```
gradlew build -Pmc=26.3
```

builds the Fabric, NeoForge and Forge jars for one Minecraft version (26.3, 26.2, 1.21.1 or 1.20.1; 26.3 if left out)
into `<Loader>/build/<generation>/libs` and runs the unit tests. Shared code is in `src/main`; what differs between
Minecraft generations is in `src/mc26` (world clocks and timelines), `src/legacy` with `src/mc21` and `src/mc20` (the
older day time), and the odd class that differs between 26.2 and 26.3 in `src/26.2` and `src/26.3`. `Stubs/` holds
compile-only stand-ins for the optional mods Daybreak works with; it is never part of a jar.

## License

[PolyForm Shield 1.0.0](LICENSE). In short: you may use Daybreak for anything, change it, include it in modpacks and
share it, as long as you keep the license and notices with it, but not to make or offer something that competes with
Daybreak (such as a re-upload or a copy under another name). The license text is what counts; this summary is not a
substitute for it.
