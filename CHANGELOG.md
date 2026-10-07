# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
