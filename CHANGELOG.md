# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- **Compilation failure in `ElytraController`**: `BlockPos` was used at three sites (the `baritoneTargetPos` field, `startGroundPath(...)`, and the landing-target call) but `net.minecraft.core.BlockPos` was never imported, so `compileJava` failed with `cannot find symbol` on all three. Added the missing import.
- **Compilation failure in `StashHunterModule`**: `onActivate()` reset health tracking via `lastHealthCheck = -1;`, but no such field exists - the declared field is `lastHealth` (line 240). The leftover name from an earlier rename broke the build with `cannot find symbol: variable lastHealthCheck`. Corrected to `lastHealth`.

### Changed

- **Consolidated all repository and branding references onto the upstream monorepo `omtoi101/Stash-Hunter`**, removing the leftover fork identity ("StashhunterPort by _og3"):
  - `fabric.mod.json`: mod `name` changed from `StashhunterPort by _og3` to `StashHunter`; the description no longer describes the project as a third-party fork; `contact.sources` now points at `omtoi101/Stash-Hunter`.
  - `meteor-addon-list.json`: `homepage` and `icon` now point at `omtoi101/Stash-Hunter` instead of the fork repository, so the addon-scanner resolves the upstream project.
  - `README.md`: title changed to `Stash-Hunter`; removed the "maintained by _og3" line; clone/Releases links and the `cd` example now use the canonical `omtoi101/Stash-Hunter` casing; `_og3` credited in the Credits section, matching the `authors` array.

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
