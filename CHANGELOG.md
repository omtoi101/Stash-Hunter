# Changelog

All notable changes to Stash-Hunter. Newest version first, format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

One line per change. How a feature works and what each setting does is in the [README](README.md); the full reasoning behind a change is in the commit history. Build, test and code changes that don't affect players are listed under **Development**.

## [v26.2.2] - 2026-11-02

First release of 26.2.2. Builds and passes all unit tests; in-game testing is still pending.

### Changed

- **Elytra repair lands first.** `auto-elytra-repair` sinks to safe ground with ElytraFly, repairs there with XP bottles and then resumes the flight. Scaffold blocks are no longer needed.
- **Unsafe ground is skipped.** Over water, lava, fire, magma, cactus, powder snow or the End's void the bot glides on until the ground is safe.
- **A stalled repair pauses instead of disconnecting.** The flight stays saved on the ground; restock and `.sh resume`.
- **No disconnect when a repair is impossible without Baritone.** Without XP bottles or Mending the bot now lands on safe ground and pauses the flight there.
- **`altitude-loss-detector` ignores the repair descent.**
- **Shorter spirals.** `.sh spiral` stops at the last ring the scan needs (e.g. 24 km less flight at radius 3000).
- **The spiral gap follows `scan-radius`** (default: twice the radius). A wider gap shows how much is left unscanned.
- **Progress in km.** `.sh spiral` shows the path length, `.sh status` the progress in percent and km.

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
[v26.2.2-pre1]: https://github.com/omtoi101/Stash-Hunter/releases/tag/v26.2.2-pre1
[v26.2.1]: https://github.com/omtoi101/Stash-Hunter/releases/tag/v26.2.1
