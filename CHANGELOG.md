# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- **Jump holds from different modules cut each other off.** `KeyHold` released the key unconditionally, so e.g. the altitude-loss recovery ending released a jump the stash module was still holding. Presses are now counted per key, and `AltitudeLossDetector`, `StuckDetector` and `AutoElytraRepair` cancel their holds when deactivated (the hold's callback was otherwise the only thing that cleared their fix-in-progress state).
- **Compilation failure in `ElytraController`**: `BlockPos` was used at three sites (the `baritoneTargetPos` field, `startGroundPath(...)`, and the landing-target call) but `net.minecraft.core.BlockPos` was never imported, so `compileJava` failed with `cannot find symbol` on all three. Added the missing import.
- **Compilation failure in `StashHunterModule`**: `onActivate()` reset health tracking via `lastHealthCheck = -1;`, but no such field exists - the declared field is `lastHealth` (line 240). The leftover name from an earlier rename broke the build with `cannot find symbol: variable lastHealthCheck`. Corrected to `lastHealth`.
- **`NewerNewChunks`: enabling `SaveChunkData` mid-session only dumped the already detected chunks the first time.** The one-shot counter was never reset when saving was switched off again, so a second off/on cycle silently skipped the dump. Replaced with plain on-edge detection.
- **`Config.validate()` is now actually called** after `Config.load()`, so out-of-range values in `stash-hunter.properties` are clamped on startup. `max-volume-threshold` now has the same minimum (100) in the UI as in validation.
- **Documentation drift**: README listed the `flight-altitude` default as 320 (code: 160) and the clear commands as `.clearstashes`/`.clearplayers` (registered: `.clear-stashes`/`.clear-players`); the `.stashhunter help` output printed `/stashhunter` instead of the Meteor prefix.
- **Bot kept flying to stale coordinates after death, a dimension change or leaving the server.** `ElytraController`'s waypoints and target are static and survived all three. `StashHunterModule` now pauses the flight (trip saved, `.stashhunter resume` continues it) on death, on a dimension change and on `GameLeftEvent`, and resets its elytra/jump state. Death detection no longer depends on `notify-on-death`; only the Discord message does.
- **Built-in controller and Baritone steered at the same time.** The "Baritone dropped its path" check asked Baritone's generic pathing behavior, which is idle during elytra flight, so `ElytraController` overrode Baritone every tick and `StuckDetector` treated every Baritone flight as stuck. Both now ask the elytra process itself (`BaritoneBridge.isElytraActive()`); the unused `isPathing()`/`hasPath()` were removed.
- **Stopping a flight switched off a user-enabled ElytraFly.** `ElytraController.stop()` now only disables ElytraFly if the flight enabled it.
- **Elytra could break mid-repair.** `repair-threshold` minimum raised from 1 to 30 so the sequence has headroom. If remaining durability drops to 10 while still gliding (waiting/scaffolding), the repair aborts via emergency disconnect; once the block is placed, positioning is skipped and the repair starts in place.
- **Repair hung for the full `repair-timeout` (300 s) without XP.** The sequence no longer starts without XP bottles in the inventory, and aborts if the damage value has not dropped for 5 s - with an emergency disconnect if the elytra is still below the threshold, otherwise by resuming flight.
- **`NewerNewChunks` mutated chunk sets, read `mc.level` and wrote files from the Netty connection thread.** `PacketEvent.Receive` is posted from `Connection.channelRead0`. Block/section updates are now handled on the client thread via `mc.execute(...)`; the chunk-packet decoding and analysis stay on the Netty thread (they only touch a private `LevelChunk` copy), and only the resulting classification is applied on the client thread.
- **`NewerNewChunks` decoded every chunk packet through `CompletableFuture.runAsync(...).join()`** on a static, never shut down thread pool - a pointless context switch that blocked the connection thread anyway. Decoding is now synchronous; a failed decode skips the chunk instead of analysing a half-filled one. The executor is gone.
- **One file open/append/close per detected chunk.** Detections are queued and appended per file in one write: every 5 s, on dimension change, on disable, on leaving the server and before reloading data. Deleting a dimension's data also drops its queued writes, and a chunk promoted from block-exploit to new is no longer queued for both files. The chunk sets are now plain `HashSet`s, since only the client thread touches them.
- **Emergency disconnect toggled `AutoElytraRepair` off from inside its own tick handler** and left its state on `EMERGENCY_DISCONNECT`. The state is now reset to `MONITORING` and the module deactivates on the next tick. Early aborts no longer write default values into ElytraFly's `autoPilot`/`autoHover`/`autoTakeOff` when those were never captured.
- **Stashes below the bot were never found while flying.** Both scan passes used a spherical radius around the player, so at the default flight altitude (160) and `scan-radius` 64 only containers above y≈96 were seen, and the block-by-block pass (radius 32, y < 80) found nothing at all. The scan is now a cylinder: horizontal `scan-radius`, full world height (`getMinY()`..`getMaxY()`), so Nether roof, End and mountains are covered too.
- **Containers were counted twice.** They were collected from the block-entity list and again by the block-by-block pass, doubling cluster size, density and the reported block count. The block-by-block pass now only looks for active blocks without a block entity (crafting tables, anvils); it checks every block of a candidate section instead of every 2nd, since sections whose palette can't contain such a block are skipped anyway.
- **Neighbouring stashes merged into one cluster, close stashes were discarded.** Clustering chained blocks transitively (up to `max-cluster-distance` per link) without limit, collapsing the density, and a fixed 100-block dedupe radius swallowed nearby real stashes. Clusters now grow from the densest blocks outward and are capped by the new `max-cluster-size` setting (diameter, default 64); the dedupe radius is the new `report-dedupe-radius` setting (default 48). The grid-based neighbour lookup also replaces the old O(n²) loop.
- **The same stash was reported again every 10 minutes** (another `@everyone` and a duplicate waypoint), because the reported list was wiped on a timer. It is now cleared only on dimension change and on leaving the server, and existing stash waypoints nearby also suppress a report - so toggling the module or reconnecting doesn't re-report either.
- **Stash waypoints were always created in the Overworld.** The waypoint builder never set a dimension, so Nether/End stashes got an Overworld waypoint at the wrong coordinates. The current dimension is now set.
- **`NewerNewChunks` used hard-coded section indices** (`< 17`, `== 4`, `< 8`) that assume vanilla heights; a dimension with fewer sections would throw on the connection thread. They are derived from the level now. The misleading comment claiming `getChunk(x, z, false)` never returns `null` was corrected - it returns `null` until the chunk is loaded, so the chunk analysis does run.
- **Discord webhook treated every answer as success.** The response code was ignored, so rate limits (429), deleted webhooks (404) and bad tokens (401) failed silently, and the connection was not closed on errors. Now 429 waits for `retry_after`, 5xx and network errors retry with backoff (up to 4 attempts), other 4xx are logged once; the send queue is bounded (50) instead of growing without limit while Discord is down.
- **Every slider step wrote the config file on the client thread.** `onChanged` now calls `Config.saveLater()`, which coalesces changes into one write after 1 s on a background thread.
- **`AltitudeLossDetector` fired on stale or irrelevant drops.** After the 200-tick cooldown it compared against a Y from before the cooldown, and it also fired while walking or falling without an elytra. The reference is now kept fresh while not checking, only elytra flight (or an active bot flight) counts, and Baritone is only cancelled when the drop happened during the bot's own flight.
- **Unreadable lines in `NewerNewChunks` data files aborted the whole load**, which was then retried every tick (only `IOException` was caught). Bad lines are now skipped and counted; lines are written as `[x, z]` explicitly instead of relying on `ChunkPos.toString()`.
- **A corrupt `stashhunter_trips.json` threw out of `pause()`/`resume()`**, e.g. inside the death teardown, and the next `addTrip()` would have overwritten it. It is now moved to `stashhunter_trips.json.corrupt` with a warning. Waypoints with a missing or non-finite coordinate are rejected instead of silently becoming `(0, 0, 0)`, and unknown fields are skipped instead of aborting the read.
- **The scan-completed flag could get stuck** when `notify-on-completion` was off, because the `&&` skipped consuming it; a later completion message then fired at the wrong time.
- **`.stashhunter start`/`resume` without the module enabled started a flight that never moved**, since only `StashHunterModule` ticks `ElytraController`. The commands now enable the module. `ElytraController.start` rejects a `stripWidth <= 0` (endless waypoint loop), and the estimated waypoint count now includes the last strip.

### Removed

- **Dead utility code**: `WorldScanner` (6 of its 8 methods had no callers; the two used ones moved to `StashClustering`), the unused `Utils`, and the custom `Logger`, which wrote to a hard-coded `~/.meteor-client/stashhunter.log` instead of `MeteorClient.FOLDER`, was not thread-safe and never closed. Its messages now go to the normal game log via `StashHunter.LOG`.
- **The separate Gson dependency** - Minecraft already ships Gson (2.14.0) on the classpath; the declared 2.10.1 was redundant.
- **Dead `lastForwardPressed` reflection in `AutoElytraRepair`.** It looked the field up on the flight-mode subclass, but it is declared in `ElytraFlightMode`, so it always threw `NoSuchFieldException` and was silently ignored - removing it changes nothing at runtime.
- **Dead mixins and event**: `PlayerInventoryAccessor` (unused, and a boot-crash risk under `defaultRequire: 1`), `LivingEntityMixin` (`LivingEntity#die` runs server-side, so it never fired on the client) and the subscriber-less `PlayerDeathEvent`. With no mixins left, the `addon-template.mixins.json` config is dropped from `fabric.mod.json`.

### Changed

- **Fabric API is no longer required.** It was only used for its client-disconnect event; the Discord disconnect notification now hangs off Meteor's `GameLeftEvent` (fired on kicks, lost connections and own disconnects alike), and the internal `PlayerDisconnectEvent` is gone. `fabric.mod.json` now depends only on Java, Minecraft and Meteor; the build compiles against the single `fabric-resource-loader-v1` module Meteor bundles.
- **`meteor-client` dependency narrowed from `*` to `~26.2-`**, i.e. any Meteor build of the 26.2 line. (Plain `>=26.2` would reject every Meteor build, since they are versioned as pre-releases like `26.2-35`.)
- **Toolchain**: Fabric Loader 0.19.3 → 0.20.1; Loom pinned to the 1.17.21 release instead of the moving `1.17-SNAPSHOT`. Loom 1.18+/1.19 were not taken: they require Gradle itself to run on JDK 25 (and 1.19 Gradle ≥ 9.8).
- **Pure logic extracted into testable classes**: `StashClustering` (clustering, bounding box, center - now `Optional` instead of `BlockPos.ZERO` for an empty list), `GridWaypoints` (strip waypoints and their count), `TrailAnalysis` (trail detection, takes a `Predicate<ChunkPos>` instead of the module) and `ChunkDataFormat` (data file lines).
- **`AutoElytraRepair` no longer reads Meteor internals via reflection.** ElytraFly's `autoPilot`/`autoHover`/`autoTakeOff` are public settings; Scaffold's `air-place`/`auto-switch` go through Meteor's `settings.get(name)`. A missing setting is logged once as a warning instead of being swallowed.
- **`ElytraController` looks `NewerNewChunks` up per call** instead of in a `static final` field filled at class-load time, which stayed `null` for good if the class loaded before the module was registered.
- **Errors are logged instead of printed**: all `printStackTrace()`/`System.err` calls go through `StashHunter.LOG`; empty `catch` blocks around calls that can't throw were removed, the remaining intentional ones are commented.
- **`NewerNewChunks` is indented with spaces** like the rest of the code (`.editorconfig`).
- **Rendering and trail analysis no longer allocate per chunk per frame/tick.** `NewerNewChunks` renders and prunes with plain int/double distance math instead of creating `BlockPos`/`Vec3`/`AABB` per chunk. `getNewChunks()` (copied the whole set, twice per tick) is replaced by `isNewChunk(ChunkPos)`; `ElytraController` probes only its 5-chunk analysis radius, caps the O(n³) collinearity search at the 24 nearest chunks and no longer sorts its caller's list in place.
- **Faster `scanForBlocks`.** The active block list is turned into a set once per scan, sections whose palette cannot contain an active block are skipped, block states are read straight from the section, and the two `sqrt`/`pow` distance checks became one squared int check. Scanned positions (radius, Y range, step) are unchanged.
- **Removed template leftovers**: icon moved to `assets/stash-hunter/icon.png`, `maven_group` is now `com.stashhunter`, `mod_version` aligned with the changelog (`26.2.1`).
- **CI builds with Java 25** (was 21), matching `jdk_version` and the toolchain.

- **Consolidated all repository and branding references onto the upstream monorepo `omtoi101/Stash-Hunter`**, removing the leftover fork identity ("StashhunterPort by _og3"):
  - `fabric.mod.json`: mod `name` changed from `StashhunterPort by _og3` to `StashHunter`; the description no longer describes the project as a third-party fork; `contact.sources` now points at `omtoi101/Stash-Hunter`.
  - `meteor-addon-list.json`: `homepage` and `icon` now point at `omtoi101/Stash-Hunter` instead of the fork repository, so the addon-scanner resolves the upstream project.
  - `README.md`: title changed to `Stash-Hunter`; removed the "maintained by _og3" line; clone/Releases links and the `cd` example now use the canonical `omtoi101/Stash-Hunter` casing; `_og3` credited in the Credits section, matching the `authors` array.

### Added

- **Unit tests** (JUnit 5, run by `./gradlew build` and CI) for clustering, waypoint generation, trail analysis, the chunk data format and trip/`Vec3` serialization.
- **Spotless** whitespace check (indentation, trailing whitespace, final newline; `./gradlew spotlessApply` fixes it) and a **Dependabot** config for Gradle plugins/libraries and GitHub Actions.
- **`mention-everyone` setting** (default: off). Discord messages no longer ping `@everyone` unless enabled; `allowed_mentions` is set explicitly either way.
- **`max-cluster-size` and `report-dedupe-radius` settings** for the stash clustering (see Fixed).

### Security

- **Gradle distribution is now checksum-verified.** `gradle-wrapper.properties` had no `distributionSha256Sum`, so the wrapper downloaded the Gradle 9.6.1 distribution without verifying it - a supply-chain gap, since a tampered download would have been executed unopposed. Pinned the official SHA-256 (`9c0f7fae...a32c9e14`, taken from Gradle's published `gradle-9.6.1-bin.zip.sha256` and independently confirmed against the downloaded archive); the wrapper now aborts the build if the distribution does not match.
- **Regenerated the committed `gradle-wrapper.jar`.** The checked-in jar was not the Gradle 9.6.1 wrapper: `gradle-wrapper.properties` had been hand-edited from `8.14.3` to `9.6.1` (commit `6a0d4f4`) without regenerating the jar, leaving an older wrapper in the tree. Re-running the `wrapper` task replaced `gradlew`, `gradlew.bat` and `gradle-wrapper.jar` with the genuine 9.6.1 artifacts (jar SHA-256 now `497c8c2a...194a9c7`), so all four wrapper files are consistent with the declared distribution version.

## [v26.2.1] - 2026-09-11

### Added

- **Optional Baritone pathfinding integration** (`com.stashhunter.stashhunter.baritone`): when the [Baritone](https://github.com/MeteorDevelopment/baritone) mod (26.2 branch) is also installed, `ElytraController` hands its flight and landing targets to Baritone's elytra and ground pathfinding (`IElytraProcess`/`ICustomGoalProcess`) for more precise, obstacle-aware movement execution. Baritone only replaces movement *execution* - the existing grid/trail-following exploration strategy (`NewerNewChunks`, `WorldScanner`) is unchanged.
  - New `BaritoneBridge`/`BaritoneImpl` facade keeps Baritone a true soft/optional dependency: every call is gated behind `FabricLoader.isModLoaded("baritone-meteor")`, so the addon starts and runs normally without Baritone installed.
  - New `use-baritone-pathing` setting (default: on) to force the built-in flight controller even when Baritone is present.
  - `StuckDetector` now also treats an unexpectedly dropped Baritone path as a stuck condition.
  - `AltitudeLossDetector` cancels an in-progress Baritone path before attempting its own jump-hold recovery.
  - Added `compileOnly` dependency on `meteordevelopment:baritone:26.2-SNAPSHOT` and a `recommends` entry in `fabric.mod.json`.

### Fixed

- **Crash on startup**: `PlayerInventoryAccessor`'s generated `@Accessor` method had the same name and signature as a method `Inventory` already declares natively in 26.2 (`setSelectedSlot(int)`), producing two methods with an identical name/descriptor in the same class file. The JVM verifier rejects this (`ClassFormatError: Duplicate method name&signature`), crashing the game as soon as the mixin was applied. Renamed the accessor method so it no longer collides with the vanilla method.
- **`NewerNewChunks` old-chunk/palette-exploit detection was silently broken**: a double negation (`!!section.hasOnlyAir()`) in three detector loops meant they only ever scanned chunk sections that were pure air, instead of sections with actual content. Fixed to `!section.hasOnlyAir()` in all three places.
- **Build could fail even though the dependency existed**: Gradle would sometimes probe Mojang's `libraries.minecraft.net` for Meteor-published artifacts (`meteordevelopment:*`, `org.meteordev:*`) before trying `maven.meteordev.org`, and a hiccup on that unrelated host aborted resolution entirely. Restricted those groups to Meteor's own Maven repositories via Gradle's `exclusiveContent`.

### Removed

- Dead, unused `ElytraController.generateChunkBasedWaypoints(...)` stub (empty method body, already marked as unused).
- Dead `NewerNewChunks.getOldChunks()` public accessor (zero callers anywhere in the codebase).
- Redundant same-package import of `TripManager` in `ElytraController`.

### Changed

- Tightened misleading "simplified due to API compatibility issues" comments in `NewerNewChunks`'s End-dimension and palette-exploit detectors to accurately describe them as heuristic/best-effort, now that the underlying double-negation bug is fixed.
