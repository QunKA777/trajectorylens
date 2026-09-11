# TrajectoryLens

[English](README.md) · [中文](README.zh-CN.md)

> **See where things are going — before they get there.**
> Minecraft **26.2** · Fabric · client-side · MIT

[![build](https://github.com/QunKA777/trajectorylens/actions/workflows/build.yml/badge.svg)](https://github.com/QunKA777/trajectorylens/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-3C8527)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.3%2B-DBD0B4)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/QunKA777/trajectorylens?label=download)](https://github.com/QunKA777/trajectorylens/releases/latest)

TrajectoryLens predicts and renders where dropped items, arrows, TNT, falling blocks and even whole
explosion chains are going to end up — as translucent, glass-like coloured segments you can see
through terrain. No particles, no block changes, no world edits, and **nothing is ever sent to the
server**: install it on your client and it works on any server, vanilla or modded.

## Install

1. Install **Fabric Loader 0.19.3+** and **Fabric API** (26.2 build);
2. Drop `trajectorylens-1.1.0.jar` into your client `mods/` folder;
3. In game press **G** to toggle item trajectories, **H+J** to open the control panel, or type `/trajectorylens` (short alias `/tl`).

- **A client-side install is all you need.** Every feature is computed locally; the server does not have
  to install anything and never receives a single packet from this mod, so there is no permission or
  anti-cheat interaction. Vanilla servers, other Fabric servers and modpacks all work.
- Installing the same jar on a **server** only adds `/trajectorylens simulate …` (no rendering).
  That command has no permission check by default — mind that on public servers.
- Upgrading from the old `ItemTrajectory` (`itemtrajectory`) build: your settings are migrated
  automatically from `config/itemtrajectory.json` (watch lists, colours and counters are kept).
  **Delete the old `itemtrajectory-*.jar`** so the two mods do not load side by side.

## Features

| Area | What you get |
|---|---|
| **Dropped items** | Per-tick faithful trajectory prediction · rest markers and ETA · per-item colours · watch list (filter by item id) |
| **Projectiles** | Arrows / tridents / snowballs / eggs / ender pearls / potions / fireballs with flight path and impact target · held-item aim preview |
| **Explosions** | Fuse countdown and blast rings for TNT, creepers and TNT minecarts · **causal-chain forecast** (chained detonations, where launched entities land, block consequences) |
| **Blocks** | Landing prediction for falling blocks (sand, gravel, anvils, dripstone…), highlighted red when somebody is underneath |
| **Mobs** | Threat / aggro indicator (locked / alert / unaware) · walk-range reachability overlay |
| **Logistics** | Chokepoint counters (rate / backlog / 3-minute trend) · **hopper jam detection** · entity census · report export |
| **Lost items** | Provenance of disappearing items · hopper highlight · despawn countdown |
| **Interaction** | 8-page control panel · pure key-chord binding · search pickers for items and entity types · everything persisted |

## Control panel

Opens with **H+J** by default (rebindable on the Keys page). Eight tabs in two rows of four, and it
switches to a two-column layout on wide windows, so no page ever needs scrolling.

| Page | Contents |
|---|---|
| **Overview** | The three master switches + enable/disable everything + live status + hotkey reminder |
| **Drops** | Trajectory toggle · search-add item · clear watch list · current watch list (click a row to remove) · lost-item provenance · marker duration · hopper highlight duration · despawn warning |
| **Projectiles** | Projectile paths · held-item aim preview · blast warning |
| **Blast** | Causal-chain forecast · chain depth (1-4) · knock-back forecast window (3/6/9/12 s) · falling-block landing |
| **Mobs** | Threat indicator · walk-range overlay · forecast horizon · search-add entity type · watch list |
| **Logistics** | Entity census · lost-item summary · hopper jam detection · jam threshold · chokepoint counters · add counter · counter list (with trend) |
| **Keys** | Click a row, press the new keys (up to 3), release to bind; Esc cancels, Backspace clears |
| **Tools** | Dump colours/watch lists/switches · export report · vanilla key settings · lost-item log · command cheatsheet |

## Keybinds and commands

| Key | Action |
|---|---|
| **G** | Toggle drop-trajectory rendering (rebindable in vanilla Controls, search for `TrajectoryLens`) |
| **O+P** (hold together) | Toggle the walk-range overlay |
| **H+J** (hold together) | Open the control panel |

> `O+P` and `H+J` are **key chords**: hold the keys down together, do not press them one after another.

The root command is **`/trajectorylens`**, with the short alias **`/tl`** (every row below works with either).
Client and server register the same tree: with a client-only install the client implementation runs
(no packets sent), and when the server also has the mod the server tree takes over and replies through
a custom payload — completions and behaviour are identical.

| Command | Purpose |
|---|---|
| `/trajectorylens toggle` | Toggle trajectory rendering (same as G) |
| `/trajectorylens target <item id>` | Add an item to the watch list (namespace optional, e.g. `diamond`); repeated calls accumulate |
| `/trajectorylens target clear` | Clear the watch list and track every item again |
| `/trajectorylens color <item id> <color>` | Item path colour: 6-digit `RRGGBB` or 8-digit `AARRGGBB`, `#`/`0x` prefixes allowed, `auto` restores automatic colours |
| `/trajectorylens colors` / `status` | List current colours (including automatic ones) and switch/watch state |
| `/trajectorylens sim(ulate) <x> <y> <z> <vx> <vy> <vz>` | (server) Run the prediction engine for a given start position/velocity and print the end point — handy for designing farms |
| `/trajectorylens projectiles` / `tnt` / `aim` / `chain` / `falling` `on\|off\|toggle` | Projectile paths / blast warning / aim preview / causal chain / falling blocks |
| `/trajectorylens chaindepth [layers]` | Causal-chain depth 1-4 (cycles without an argument) |
| `/trajectorylens chainhorizon [seconds]` | Knock-back forecast window (cycles 3/6/9/12 without an argument) |
| `/trajectorylens threat on\|off\|toggle` | Threat indicator |
| `/trajectorylens census` | Print the nearby entity census (hostile/passive/items/XP/projectiles and the most common types) |
| `/trajectorylens flowcount on\|off\|toggle` | Chokepoint counter overlay |
| `/trajectorylens counter add <name>` | Place a counting plane where you are looking (items crossing it are counted, with rate and upstream backlog) |
| `/trajectorylens counter remove <name>` / `clear` / `list` | Remove / clear / list counters |
| `/trajectorylens jam on\|off\|toggle` · `jamtime [seconds]` | Hopper jam detection and its threshold (default 6 s) |
| `/trajectorylens losttrack on\|off\|toggle` | Lost-item provenance |
| `/trajectorylens lost` / `lost clear` | Print / clear the lost-item log |
| `/trajectorylens losttime [seconds]` | Ghost-marker lifetime (1-300; cycles 3/5/6/8/10/15/30/60) |
| `/trajectorylens glowtime [seconds]` | Hopper highlight duration (1-300; cycles 2/3/5/8/10/15/20) |
| `/trajectorylens despawn on\|off\|toggle` | Despawn warning for old items |
| `/trajectorylens export` | Write census / counters (with trend) / jams / lost items to `config/trajectorylens-report-*.txt` |
| `/trajectorylens range add <entity type>` | Watch an entity type for the walk-range overlay (e.g. `zombie`, Tab completion) |
| `/trajectorylens range clear` / `list` / `toggle` | Clear / list / toggle the walk-range overlay |
| `/trajectorylens range time <seconds>` | Walk-range horizon 1-60 s (default 5) |
| `/trajectorylens range color <entity type> <color>` | Colour of one entity type in the overlay |
| `/trajectorylens gui` | Open the control panel |

## How accurate is it?

26.2 changed item physics a lot compared with older versions (water drag 0.99 / lava 0.95, no gravity
inside water, water flow push 0.014, ground friction straight from the block, collisions and bounces
inside `Entity.move`). Instead of re-implementing formulas, the mod creates a **shadow `ItemEntity`**
(never added to the world) and calls the very same engine methods every tick
(`updateFluidInteraction → fluid branch/gravity → noPhysics → move → applyEffectsFromBlocks → drag/friction`).
As long as no external event (player pushing, pickup, merging) interferes, the prediction matches
vanilla tick for tick. Paths are cleared the moment the item is picked up or despawns, and recomputed
every 20 ticks or whenever the state drifts.

Projectiles follow the real per-type constants: **arrows** gravity 0.05 / air 0.99 / water 0.6;
**snowballs, eggs, pearls, potions** 0.03 / 0.99 / 0.8; **fireballs** drag 0.95 plus 0.1 per-tick
acceleration; **TNT** 0.04 / 0.98 with a `(0.7, -0.5, 0.7)` ground bounce and an 80-tick fuse;
**falling blocks** 0.04 / 0.98. Impact points come from a block collision ray plus entity hitboxes
(inflated by 0.3), whichever is closer.

## What the paths look like

- Paths are translucent coloured block segments with a bright-to-dark gradient (stained-glass look),
  thick and fairly opaque, drawn through the Gizmo always-on-top channel so you can **see them through
  terrain**. No particles, no blocks placed;
- Rest marker: a green translucent disc plus `rest ~x.xs` (the predicted time until the item stops);
  removed immediately when the item is picked up or vanishes — no flickering leftovers;
- End reasons: burnt = orange dot + `burns`; prediction limit / chunk unload = purple dot.

## Projectiles, explosions and causal chains

- **Projectile paths** for arrows and spectral arrows, tridents, snowballs, eggs, ender pearls,
  potions, ghast/blaze fireballs and TNT within 48 blocks; the impact point is ringed and labelled with
  the **block** or **entity name** it will hit (so an arrow tells you who it is going to hit);
- **Held-item aim preview**: full trajectory while drawing a bow, holding a charged crossbow, charging
  a trident or holding a throwable;
- **Ender pearls**: the teleport destination (vanilla teleports to the position one tick before impact),
  the 5 points of self-damage and a danger verdict for the landing spot;
- **Blast warning**: primed TNT shows its fuse countdown, predicted landing spot and two blast rings
  (inner = guaranteed-break radius for power 4, outer = 2x radius damage/knock-back zone); creepers are
  predicted from their synced swell state; TNT minecarts are supported too;
- **Causal-chain forecast** keeps going after the blast:
  - *Chained detonations*: already-primed TNT in range, TNT that gets launched with fuse left, and
    TNT minecarts, each scheduled by its own remaining fuse;
  - *Who gets launched where*: pushed items and mobs each get a parabola (mobs gravity 0.08, items and
    TNT 0.04) that ends with a verdict — **→ lava!**, **→ cactus**, **→ void**, **→ water (safe)**,
    **→ N blocks of fall damage**, **→ safe landing**;
  - *Block consequences*: how many TNT blocks will be destroyed, whether falling blocks above will come
    down, and how many of those would land on a mob;
  - Depth (1-4) and forecast window (3/6/9/12 s) are configurable; up to 8 branches per layer; every
    stage is isolated so a failure only loses one part of the drawing.

> Knock-back and chaining are **approximations** (real knock-back depends on blast exposure, potion
> effects and resistance). The numbers tell you magnitude and direction, not a tick-perfect replay.

## Falling blocks

Falling sand, gravel, anvils, concrete powder and dripstone show a dashed drop line, a landing ring,
the block name and the estimated time to impact. If a mob or player stands underneath, everything turns
**red** and the label reads `hits Zombie!` / `hits you!`.

## Mobs: threat indicator and walk range

- **Threat indicator**: hostile mobs within 32 blocks are labelled **locked** (line of sight + facing you
  + close), **alert** or **unaware**, with a red line and distance for locked targets (closest 12 only).
  It is inferred on the client (real AI targets live on the server) from distance, follow-range
  attribute, line-of-sight ray and facing — good enough to answer "is that zombie coming for me?";
- **Walk-range overlay** (**O+P**): the theoretical reachable area of a watched entity type over the next
  N seconds (default 5 s, options 5/10/15/20/30/60), drawn as ground outlines plus a centre ring,
  aligned to integer block edges, with per-type colours for multiple types at once.

## Logistics: counters, jams and census

- **Chokepoint counters**: place a 3x3 virtual counting plane where you look (normal follows your
  facing), items crossing it are counted; shows the **last minute rate**, the **5-minute average**, the
  **upstream backlog within 8 blocks** and a **3-minute trend sparkline** `▁▂▄▆█` with an ↑/↓ arrow —
  perfect for "is this water channel or hopper line slowing down?";
- **Hopper jam detection**: an item that stays **motionless** on a hopper for longer than the threshold
  (default 6 s) is boxed: **red = jammed** (destination full, sorter stuck, item can never be taken;
  labelled `jammed 3 items 8.4s <item>`), **blue = redstone-locked** (the hopper has `enabled=false`,
  i.e. you switched it off on purpose). Detection uses the vanilla suck volume
  (`x..x+1, y+0.1875..y+1.5, z..z+1`) and the `enabled` property, so it agrees with the game; hopper
  minecarts are recognised as well;
- **Entity census**: totals plus hostile / passive / other, items / XP orbs / projectiles and the six
  most common types, with a warning when items exceed 200 or hostiles approach the mob cap — the fastest
  way to answer "why did my farm slow down?".

## Lost items: provenance, hopper highlight, despawn warning

- **Provenance** records why a nearby item disappeared: picked up by a player, **sucked by a hopper or
  hopper minecart**, destroyed by lava / fire / cactus, destroyed by an explosion, merged into a stack,
  despawned after five minutes, or fell into the void. The logistics page shows a summary and a **ghost
  marker** is left at the spot (colour per reason, fades out after 6 s by default);
- **Hopper attribution follows the vanilla geometry**: if the item's last position is inside a hopper's
  suck volume (0.35 tolerance) or it was dropping straight into one from up to 3 blocks above, it is
  attributed to that hopper; redstone-locked hoppers are excluded. The order is lava/fire/cactus/blast →
  **hopper** → player pickup → despawn → merge → unknown, so "player standing next to a hopper" is no
  longer mis-reported as a player pickup;
- **Hopper highlight**: the hopper that swallowed an item glows for **5 s** by default, fading from
  bright to dim, and **the timer resets on every new pickup** — you can see exactly which hopper is
  eating your items. Duration is configurable in the panel or with `glowtime`;
- **Despawn warning**: vanilla items disappear after five minutes and the client cannot see the real
  timer, so the mod uses **the time this client has watched the item**: after four and a half minutes an
  amber countdown `≤30s` appears above it, turns red below 30 seconds and prints a single chat warning;
- **Report export**: `/trajectorylens export` writes the census, all counters (rate, trend, totals),
  jams and the lost-item log to `config/trajectorylens-report-<timestamp>.txt`, so you can export one
  every few days and compare farm throughput.

## What a client-only install can see in multiplayer

| Works | Limited to |
|---|---|
| Every trajectory, prediction, highlight, panel and command (all local, nothing sent to the server) | Only entities the server sends you: items beyond entity-tracking range or in unloaded chunks are invisible |
| Block, fluid and hopper `enabled` state (the client has the chunk data) | Other players' inventories, chest contents, how full a hopper actually is |
| Disappearance reasons the client can observe (hopper, lava, despawn, void…) | Items picked up by **another player** can only be reported as "unknown" |
| Despawn countdown (derived from the observed age — a lower bound on the real 5 minutes) | Server-private fields such as the real item age or fuse (observed values are used instead) |

> Predicting items thrown by other players, or items far away, would need the mod on the server too
> (the "server-authoritative broadcast" phase 2 in the design document).

## Building from source

All you need is **JDK 25**; the bundled Gradle wrapper downloads Gradle 9.5.1 on first run.

```bash
./gradlew build        # Windows: gradlew.bat build  -> build/libs/trajectorylens-1.1.0.jar
./gradlew test         # pure-logic unit tests (path decimation / colour helpers)
./gradlew genSources   # optional: decompiled 26.2 sources for API reference
```

> Minecraft 26.2 is unobfuscated (Yarn stopped at 1.21.11), so Loom 1.17 compiles against the official
> names and dependencies are declared with plain `implementation`; you need Fabric Loader ≥ 0.19.3,
> Fabric API 0.160.0+26.2 and Java 25.

## Project layout

```
trajectorylens/
├── src/main/java/dev/soityy/trajectorylens/        common (safe on both sides)
│   ├── TrajectoryLensMod.java                      entry point + server command tree (payload relay)
│   ├── physics/                                    shadow-entity prediction engine (vanilla per tick)
│   ├── network/TargetPayload.java                  server -> client settings payload
│   └── util/PathTools.java                         path decimation / colour helpers (unit tested)
├── src/client/java/dev/soityy/trajectorylens/client/
│   ├── TrajectoryLensClient.java                   client entry point: trackers, renderers, config
│   ├── track/                                      tracking and simulation (items, projectiles, TNT, chains, logistics, provenance)
│   ├── render/                                     6 Gizmo renderers (paths, ranges, projectiles, flow, threats, lost items)
│   ├── ui/                                         control panel, search picker, config, chords, report export
│   └── command/ClientCommands.java                 client-side command fallback (works without the server mod)
├── src/test/java/dev/soityy/trajectorylens/        JUnit tests
├── src/main/resources/fabric.mod.json + assets/trajectorylens/icon.png
├── docs/DESIGN-zh.md                               design notes, version research and decisions (Chinese)
├── .github/workflows/build.yml                     CI: JDK 25 build + artifact upload
├── CHANGELOG.md / LICENSE / README.md
└── gradlew(.bat) / gradle/wrapper/                 bundled Gradle wrapper
```

## Languages

The in-game interface follows your game language: `assets/trajectorylens/lang/en_us.json` provides
English, `zh_cn.json` the Chinese originals, and any other language falls back to English. The panel,
the search pickers and every chat message are translated today; the labels drawn on top of the world
follow in an upcoming update. Translation keys are the Chinese source strings (`gettext` style), so a
missing entry can never render as a raw key — it simply shows the Chinese text. Pull requests adding
`assets/trajectorylens/lang/<locale>.json` are very welcome.

## Known limitations

- Player/mob pushing, item merging and pickups cannot be predicted: affected paths recompute or vanish;
- Prediction stops at unloaded chunks (the real item stops there too);
- Items picked up by other players and container contents are server-private, so they are inferred or
  reported as unknown;
- The glass look comes from translucent filled Gizmos drawn on the always-on-top channel (visible
  through terrain by design);
- The panel switches between a one- and two-column layout based on GUI scale; on very small windows a
  row may be clipped, so a GUI scale of 2 or more is recommended.

## License and credits

- Author **soityy** · MIT license (see [LICENSE](LICENSE));
- Release history in [CHANGELOG.md](CHANGELOG.md), design decisions in [docs/DESIGN-zh.md](docs/DESIGN-zh.md) (Chinese);
- Minecraft 26.2 is unobfuscated, so this project compiles against the official names, contains **no
  Mixin** and modifies no vanilla class;
- Issues and PRs are welcome: <https://github.com/QunKA777/trajectorylens>.
