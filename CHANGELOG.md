# Changelog

All notable changes to TrajectoryLens are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project uses [SemVer](https://semver.org/).

## [1.0.0] - 2026-09-12

First public release (renamed from the private "ItemTrajectory" build).

### Added
- **Drop trajectories** — per-tick faithful item physics (shadow `ItemEntity` driven by the real engine methods), translucent glass-like segments, always-on-top rendering, rest markers with ETA, per-item colours.
- **Projectile prediction** — arrows, tridents, snowballs, eggs, ender pearls, potions and fireballs with per-kind physics and impact labels (block or entity name).
- **Held-item aim preview** — bows, crossbows, tridents and throwables; pearls additionally show teleport destination, self-damage and a danger verdict.
- **Blast warning** — primed TNT / creepers / TNT minecarts with fuse countdown and inner+outer blast rings.
- **Causal-chain forecast** — chained detonations (including minecart TNT and TNT thrown by the blast), where launched entities land (lava / cactus / void / water / fall damage) and block consequences; depth and horizon configurable.
- **Falling-block landing** — sand, gravel, anvils, dripstone: landing spot plus a red warning when somebody is underneath.
- **Threat indicator** — client-side locked / alert / unaware readout for hostile mobs.
- **Walk-range overlay** — theoretical reachable area of watched entity types for the next N seconds.
- **Entity census** — nearby entity/item/projectile statistics with farm-capacity hints.
- **Chokepoint counters** — virtual counting planes with per-minute rate, 5-minute average, upstream backlog and a 3-minute trend sparkline.
- **Hopper jam detection** — items stalled on a hopper are highlighted (red = jammed, blue = redstone-locked).
- **Lost-item provenance** — why an item disappeared (player, hopper, lava, fire, cactus, explosion, merge, despawn, void) with ghost markers, plus a hopper highlight that fades over a configurable time.
- **Despawn warning** — a countdown for items that have been lying around for four and a half minutes.
- **In-game control panel** — eight tabbed pages (overview / drops / projectiles / blast / mobs / logistics / keys / tools), two-column layout on wide windows, key-chord binding by pressing the keys, search pickers for items and entity types.
- **Report export** — `/trajectorylens export` writes census, counters, jams and lost-item statistics to `config/trajectorylens-report-*.txt`.
- **Client-only multiplayer support** — every feature runs on the client, nothing is sent to the server.

### Notes
- Built for Minecraft 26.2 (unobfuscated, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Java 25).
- Commands: `/trajectorylens ...` with the short alias `/tl ...`.
