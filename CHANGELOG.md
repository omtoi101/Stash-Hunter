# Changelog

All notable changes to Stash-Hunter. Newest version first, format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

One line per change. How a feature works and what each setting does is in the [README](README.md); the full reasoning behind a change is in the commit history. Build, test and code changes that don't affect players are listed under **Development**.

## [Unreleased]

### Added

- **Signal score detection.** The scan now rates everything it finds by weight: containers, shulker boxes and ender chests, plus player traces (signs, unnatural blocks, portals, builds above Y 260 and on the Nether roof, placed bedrock, item frames, named and traded mobs, animals with chests, beds and other base blocks, and the findings of `activated-spawner-detector`, `stacked-minecarts` and `cave-disturbance-detector`). Natural look-alikes (skeleton and wither skeleton skulls, dragon heads, waxed copper, natural bedrock, End gateways, elytra in item frames) are ignored.
- **Hints.** Small finds, such as a single shulker box or ender chest or a few traces together, are reported as a hint: chat, a waypoint named `Hint: <what> at <x, y, z>`, yellow Stash ESP outline, and Discord with `discord-hints`. A single trace never makes a hint.
- **Each kind of trace counts once per candidate.** A portal (its blocks and its obsidian frame), a lit end portal, a concrete wall, map art or a Nether roof highway is one trace and never a hint or stash on its own; a hint without a shulker box or ender chest needs two different kinds of signals. Shulker boxes, ender chests and containers still count per block.
- **`sensitivity`** (`classic`, `normal`, `sensitive`; default `normal`) and `discord-hints` settings; `classic` keeps the 26.2.2 container-only detection without hints.
- **Signals settings group** with one switch per trace group (`signs`, `unnatural-blocks`, `portals`, `high-builds`, `bedrock`, `entities`, `other-modules`, `base-blocks`), and `hint-side-color` / `hint-line-color` in the Stash ESP group.
- **Hint to stash:** a hint that grows into a stash reports **"Stash Updated"** (also on Discord) and replaces the hint waypoint, even across sessions.
- **`.sh diag`** and a diagnostics log `meteor-client/StashHunter/diagnostics/<server>.log` (rejected candidates with reason, 5 MB then `.old`), plus a chat hint to raise `scan-radius` when the server sends chunks well beyond it.
- **`dropped-item-esp`**, **`mob-gear-esp`** and **`tunnel-esp`** (new modules, off by default, clean-room): outline valuable dropped items, mobs with player gear (also a weak trace) and shafts, tunnels and staircases dug by players (`chunks-per-tick` keeps the scan cost per tick small).

### Changed

- **Minecraft 26.3.** Stash-Hunter now requires Minecraft 26.3, Meteor Client 26.3 and, optionally, Baritone and Xaero's Minimap for 26.3. Stay on 26.2.2 for Minecraft 26.2.
- `block-detection-threshold` now means containers: `normal` needs at most 6, `sensitive` at most 4; `classic` uses it as before. `storage-only-mode` only acts in `classic`.
- Shulker boxes and ender chests count as containers for a stash; the structure filters (volume, density, dungeon, spawner distance) only apply to chests, barrels and the like, not to shulker boxes, ender chests or traces.
- `notification-density-threshold` only guards stashes found by the container count alone; stashes that reach the stash score always get their waypoint and Discord message.
- `activated-spawner-detector` also reports spawners of natural structures that have blocks placed or removed around them (farms) as traces.

### Fixed

- A one-thick obsidian wall or a solid obsidian block no longer counts as many portal frames.
- Pauses for `auto-elytra-repair` no longer print the diagnostics summary and the `scan-radius` advice each time; the flight period runs on through the repair.
- Moving entities no longer make a reported stash look grown ("Stash Updated").
- Mobs with player gear are no longer counted twice when `mob-gear-esp` is on.
- With the `bedrock` group on, the scan no longer walks the natural bedrock layers block by block every scan.
- `activated-spawner-detector` reads spawner tags null-safely: the 26.3 chunk packet can carry a spawner without a tag, which counts as an empty tag now.

### Development

- New package `signals/` (`SignalKind` weights, `Sensitivity`, `SignalScoring`, `SignalClusterer`, `ContainerFilter`, `SignalHub`, `ReportGate`, `ScanDiagnostics`, `DiagnosticsLog`, `ScanRadiusAdvice`, `WaypointNames`, `NaturalBedrock`, `PortalFrames`, `EntitySignalIndex`, `SignalCollapse` (one trace signal per kind and chunk section), `ClassicRule` (the classic decision, tested on 26.2.2 fixtures), `FlightPeriods`) with unit tests; `StashReporter` reports hints and stashes, `StashHunterModule` feeds the scan into the score.
- `utils/SpawnerTags` (+ test) holds the spawner tag reading without Minecraft static initialisers, so it can be unit tested.
- `utils/MobGear` (+ test) and tests for the dirty-chunk tracking and scan budget of `tunnel-esp`.
- Pins: Minecraft 26.3, Baritone 26.3-SNAPSHOT, Xaero fabric-26.3-26.6.2, fabric-resource-loader-v1 3.0.4+ba0d6c0d5d; Loom stays at 1.17.21.
- `ClientboundLevelChunkWithLightPacket` is a record in 26.3 (`x()`, `z()`, `chunkData()`); block entity tags are read with `forEachBlockEntityTag`.
- `ChunkPacketDecoder` reads the chunk sections itself, because `LevelChunk.replaceWithPacketData` now also creates block entities.
- `utils/DimensionIds` (+ test): Meteor 26.3 dropped `Dimension.toString()` returning the vanilla id, which hid the `.chest-index` `[+waypoint]` link.

## [v26.2.2] - 2026-11-10

Builds and passes all unit tests; in-game testing is still pending.

**Highlights**

- Elytra repair lands on safe ground first and pauses instead of disconnecting when a repair stalls or is impossible.
- Shorter spiral search with progress in percent and km.
- New module `cave-disturbance-detector`.
- `.chest-index` finds are highlighted in the world, clickable (Baritone walks there) and can become waypoints.
- Named trips, and saving trips or the chest index no longer stutters.

### Added

- **`cave-disturbance-detector`** (new module): marks plain air among cave air, i.e. blocks a player broke in Nether caves, mineshafts, strongholds, dungeons and the caves of chunks generated in 1.13-1.17. Overworld caves generated since 1.18 contain no cave air, so it finds nothing there. Settings `min-air-blocks`, `max-pocket-size`, `max-y`, ESP and tracers.
- **`.chest-index search` highlights its finds** in the world (outline and tracer, `highlight-seconds`, also with the module off).
- **Clickable coordinates in `.chest-index` results:** with Baritone installed a click walks next to the container (like `#goto`); otherwise, or for another dimension, it copies the coordinates.
- **`.chest-index shulker <name>`** finds shulker boxes by their custom name.
- **`[+waypoint]` in `.chest-index` results** adds a Meteor waypoint for the container.
- **`open-after-walk`** (`chest-index`, opt-in): opens the container once Baritone has walked to a clicked search result.
- **Named trips:** `.sh name <name>` names the current or latest trip, `.sh resume <name>` continues it, `.sh delete <name|timestamp>` removes a trip. Named trips are not dropped by the 20-trip limit.

### Changed

#### Elytra repair

- **Elytra repair lands first.** `auto-elytra-repair` sinks to safe ground with ElytraFly, repairs there with XP bottles and then resumes the flight. Scaffold blocks are no longer needed.
- **Unsafe ground is skipped.** Over water, lava, fire, magma, cactus, powder snow or the End's void the bot glides on until the ground is safe.
- **A stalled repair pauses instead of disconnecting.** The flight stays saved on the ground; restock and `.sh resume`.
- **No disconnect when a repair is impossible without Baritone.** Without XP bottles or Mending the bot now lands on safe ground and pauses the flight there.
- **`altitude-loss-detector` ignores the repair descent.**

#### Spiral search

- **Shorter spirals.** `.sh spiral` stops at the last ring the scan needs (e.g. 24 km less flight at radius 3000).
- **The spiral gap follows `scan-radius`** (default: twice the radius). A wider gap shows how much is left unscanned.
- **Progress in km.** `.sh spiral` shows the path length, `.sh status` the progress in percent and km.

#### Trips and chest index

- **Saving a trip no longer stutters.** Trips are written on a background thread; pausing, landing or starting a flight only copies the waypoints.
- **The chest index is saved in the background too**, so a big index doesn't stutter every minute.
- **`.sh list` shows each trip's name, progress, path length and save time** instead of bare timestamps.
- **A completed flight is saved as complete;** `.sh resume` no longer flies it again from the last pause.
- **`.sh resume` says when there is nothing to resume** instead of always reporting success.

### Development

- `utils/BackgroundSaver` (+ test): ordered background writes where a burst of saves collapses into the newest; used by `TripManager`, drained by the shutdown hook.
- `TripManager` reads the trips file once and keeps the list in memory (`saveCurrentTripAsync` replaces `saveTrip`).
- `utils/CaveAirPockets` (+ test): flood fill for small plain-air pockets touching cave air, clean-room (idea from Trouser-Streak, no code used).
- `ChestIndex.searchShulkerNames` (+ test); `BaritoneBridge.startGetToBlock` (Baritone `GoalGetToBlock`) and `isGoalActive`.
- `ChestIndex` writes through its own `BackgroundSaver` (`close()` on leaving the server, failed writes are retried, `clear()` deletes in order) (+ tests).
- `TripData.name`, `TripHistory.find`/`nameProblem`/`renamed`/`without`/`progress` (+ tests); `ElytraController.resume` returns whether a flight started.
- CI actions updated to their current major versions (`checkout` v7, `setup-java` v6, `setup-gradle` v5, `upload-artifact` v7, `action-gh-release` v3; Node 24 runtime).


## [v26.2.2-pre1] - 2026-10-31

Pre-release: builds and passes all unit tests, not yet tested in-game.

**Highlights**

- 9 new modules: `chest-index`, `stacked-minecarts`, `activated-spawner-detector`, `auto-firework`, `online-player-activity`, `player-alarms`, `auto-logout`, `slime-chunks`, `old-chunk-notifier`.
- Spiral search (`.sh spiral`), altitude command (`.sh altitude`), Stash ESP and Xaero's Minimap waypoints.
- Much more reliable stash detection: full world height, no double counting, stashes on strip borders and late-loading chunks are found.
- Safer long runs: spare elytra swap, landing on low rockets, saves that survive crashes, flights that pause on death or disconnect.
- Fabric API is no longer required.

### Added

#### New modules

- **`chest-index`** and the `.chest-index` command: records what containers hold (shulker contents included) per server and makes it searchable.
- **`stacked-minecarts`**: marks minecarts stacked in one spot. Ported from [PathSeeker](https://github.com/FaxHack/PathSeeker).
- **`activated-spawner-detector`**: finds spawners a player has been near and names the structure. Ported from [PathSeeker](https://github.com/FaxHack/PathSeeker); reads the chunk packet instead of scanning every tick.
- **`auto-firework`** (opt-in): fires a rocket when your elytra speed drops below `min-speed`.
- **`online-player-activity`**: reports players joining and leaving the server (tab list).
- **`player-alarms`** (opt-in): local alarm sound while a player is nearby.
- **`auto-logout`** (opt-in, risky): disconnects on a nearby player or low health and saves the flight first.
- **`slime-chunks`**: marks slime chunks (needs the world seed in multiplayer).
- **`old-chunk-notifier`**: tells you in chat and on Discord when you fly into old chunks.

#### Flight

- **Spiral search:** `.sh spiral [<x> <z>] <radius> [gap]` scans outward from you or a given point.
- **`.sh altitude [y]`** shows or changes `flight-altitude` during a flight.
- **Landing on low rockets** (`land-on-low-fireworks`, `low-firework-threshold`): with Baritone the trip is saved and the bot lands.
- **Landing after a completed scan** (`land-after-completion`, Baritone only).
- **Spare elytra** (`swap-to-spare`): a worn elytra is swapped for a spare from the inventory instead of being repaired.

#### Stash detection

- **Stash ESP:** outlines the containers of stashes found this session, with optional bounding box and tracer.
- **Xaero's Minimap waypoints** (`save-to-xaero`), also shown on Xaero's World Map.
- **`scan-by-distance`:** also scans after flying half the scan radius, so fast flight leaves no gaps.
- **`scan-only-while-flying`:** only scan during a flight; off keeps the standing scanner.
- **`include-spawners`:** counts mob spawners as stash blocks; a plain dungeon is still ignored.
- **`structure-filter-radius`:** ignores stashes next to natural-structure spawners seen by `activated-spawner-detector`.
- **`structure-waypoints`** in `activated-spawner-detector`: one waypoint per structure.
- **`max-cluster-size` and `report-dedupe-radius`** to keep neighbouring stashes apart.

#### Notifications

- **`mention-everyone`** (off by default): Discord messages no longer ping `@everyone` unless you turn it on.
- **`player-detection-radius` and `player-report-cooldown`** replace fixed values (100 blocks, 5 minutes).
- **`altitude-loss-detector` can report to Discord** (`discord`, `discord-cooldown`).

### Changed

- **Fabric API is no longer required.** Only Java, Minecraft and Meteor are needed.
- **Version ranges:** Meteor `~26.2-` (any 26.2 build) and Baritone `~26.2-` instead of `*`.
- **`PaletteExploit` reads the palette order** and now works in the Overworld, Nether and End. Chunks it can't judge stay unmarked instead of "old".
- **Chunk data moved** to `meteor-client/StashHunter/NewChunks/` in the game folder. Old data is moved over automatically.
- **Reported stashes are remembered per server** on disk, so they aren't reported again after a reconnect.
- **Saved trips:** a flight keeps one entry that is updated on each pause; at most 20 trips are kept.

#### Performance

- The stash scan reads each chunk only once and skips clustering when nothing new was read, so a large `scan-radius` stays cheap.
- A chunk packet is decoded once for `NewerNewChunks` and `activated-spawner-detector` together.
- Baritone no longer gets a new path every tick during trail following.
- Faster block scan, chunk classification, overlay rendering and slime chunk check, with fewer allocations per frame.

### Fixed

#### Stash detection

- Stashes below the bot were never found while flying; the scan now covers the full world height.
- Containers were counted twice, doubling block count and density.
- Stashes on the border between two flight strips were missed or reported in halves.
- Chunks that loaded after the bot had passed were never scanned; they are now scanned when they arrive.
- Large bases and stashes next to natural structures were dropped as "natural"; such clusters are now split into tighter groups.
- A stash first seen only in part kept its wrong report; a later scan now sends a "Stash Updated" report.
- Neighbouring stashes merged into one, and nearby real stashes were swallowed by a fixed 100-block dedupe radius.
- The same stash was reported again every 10 minutes.
- Stash waypoints were always created in the Overworld.
- The far X edge of an area was never flown.
- Scans left gaps during fast flight (see `scan-by-distance`).
- A block update without a real change could disconnect the client ("Network Protocol Error").

#### Flight and Baritone

- Unloaded chunks ahead sent the bot off its strip; it now keeps its strip.
- Taking off and steering were unreliable, both with Baritone and with the built-in controller.
- The bot got stuck in the elytra pose after Baritone landed on its own; it now takes off again or pauses the flight.
- With Baritone, climbing after a repair or an altitude change made the bot land.
- Baritone and the built-in controller steered at the same time.
- The bot kept flying to old coordinates after death, a dimension change or leaving the server; the flight now pauses.
- Stopping a flight switched off an ElytraFly you had turned on yourself, and re-armed Baritone before cancelling it.
- A post-scan landing that never found a spot blocked other modules; it now gives up after 2 minutes.
- `.sh start` / `resume` without the module enabled started a flight that never moved.
- A saved trip without waypoints blocked new flights.
- The module ended every glide on the ground, even without a running flight.
- Jump holds from different modules cut each other off.

#### Elytra repair

- A repair during a Baritone flight didn't stop the flight and looped until it disconnected.
- `stuck-detector` interfered with repairs and could drop the player from cruise altitude.
- The elytra could break mid-repair; `repair-threshold` is now at least 30.
- Without XP bottles a repair hung for the full `repair-timeout`.
- `auto-firework` fired during a repair.

#### NewerNewChunks

- Every Nether and End chunk was marked as old while `PaletteExploit` was on.
- Nearly every End island chunk was marked as old-version.
- Block entities of new chunks leaked into the world.
- Work from the network thread touched the world and the files; it now runs on the client thread.
- Detections were written to disk one file open per chunk; they are now batched.
- A bad line in a data file aborted the whole load, and missing data files logged a warning with a stack trace.
- Turning `SaveChunkData` off and on again only saved the existing chunks the first time.

#### Saving and files

- A crash or power loss while saving could leave the config or the trips file truncated.
- Saving failed on Windows while a virus scanner, OneDrive or an editor had the file open.
- A failed config save was not retried, and on exit a save could be written twice.
- Pending config saves and queued Discord messages were lost on exit.
- Temp files of an interrupted save stayed in `meteor-client/`.
- An unfinished last line in a data file swallowed the next entry.
- A corrupt `stashhunter_trips.json` broke pause and resume; it is now moved to `.corrupt`.
- `max-cluster-size` and `report-dedupe-radius` were not saved, and some limits in the config file differed from the UI.
- Every slider step wrote the config file immediately; changes are now saved together after 1 second.

#### Discord

- Errors from Discord (rate limits, deleted webhooks) were ignored; requests are now retried and at most 50 messages are queued.
- `online-player-activity` sent old changes after Discord was switched off and on again.

#### Other modules

- `altitude-loss-detector` reacted to old or irrelevant drops, e.g. while walking.
- `stacked-minecarts` could report a stack on a block border twice.
- `player-alarms` used the system clock, so changing the time could stall the alarm.
- The "scan completed" message could fire at the wrong time when `notify-on-completion` was off.
- `.sh help` showed `/stashhunter` instead of Meteor's command prefix.

### Removed

- The Woodland Mansion check of the natural-structure filter (it could never match).

### Security

- The Gradle download is now checked against its official SHA-256, and the Gradle wrapper files were regenerated for 9.6.1.

### Development

- Unit tests (JUnit 5) and a Spotless whitespace check run with `./gradlew build` and in CI; Dependabot keeps dependencies current.
- CI builds with Java 25; the rolling dev build is only published from `main`.
- Fabric Loader 0.20.1, Loom pinned to 1.17.21.
- Pure logic moved into testable classes (`StashClustering`, `GridWaypoints`, `TrailAnalysis`, `ChunkDataFormat` and others).
- Errors go to the game log instead of `printStackTrace()`; reflection on Meteor internals removed.
- Removed dead code: `WorldScanner`, `Utils`, the custom `Logger`, unused mixins and events, and the separate Gson dependency.
- Fixed two compile errors in `ElytraController` and `StashHunterModule`.
- Repository, mod name and links point to `omtoi101/Stash-Hunter`; template leftovers cleaned up.

## [v26.2.1] - 2026-09-11

### Added

- **Optional Baritone support:** with the Baritone mod installed, flight and landing use its obstacle-aware pathfinding. `use-baritone-pathing` turns it off. Without Baritone nothing changes.

### Fixed

- The game crashed on startup (duplicate method from a mixin).
- `NewerNewChunks` old-chunk detection only looked at empty chunk sections.
- The build could fail when Mojang's library server was unreachable.

### Removed

- Unused code in `ElytraController` and `NewerNewChunks`.

[Unreleased]: https://github.com/omtoi101/Stash-Hunter/commits/main
[v26.2.2]: https://github.com/omtoi101/Stash-Hunter/releases/tag/v26.2.2
[v26.2.2-pre1]: https://github.com/omtoi101/Stash-Hunter/releases/tag/v26.2.2-pre1
[v26.2.1]: https://github.com/omtoi101/Stash-Hunter/releases/tag/v26.2.1
