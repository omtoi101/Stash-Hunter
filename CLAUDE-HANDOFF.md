# Handoff an Claude Code

**Projekt:** Stash-Hunter (Meteor-Client-Addon, Fabric, Minecraft 26.2, Java 25, Mojang-Mappings)

**Stand 2026-10-31:** Alles unten ist eingebaut und committet. `gradlew clean build --rerun-tasks`
läuft mit JDK 25 grün (inkl. Spotless + Unit-Tests). **Offen ist nur noch der In-game-Test.**

Den End-Detektor in NewerNewChunks beschreibt [DSH-HANDOFF.md](DSH-HANDOFF.md).

## In-game-Test (offen)

- [ ] **activated-spawner-detector:** erscheint in der Kategorie „Stash-Hunter“, lässt sich an/aus
      schalten; Dungeon/Mineshaft/Trial-Chamber mit aktiviertem Spawner werden gemeldet und gerendert.
- [ ] **OldChunkNotifier:** Nach einem Dimensionswechsel (z. B. Overworld → Nether) kommt die erste
      Alt-Chunk-Meldung in der neuen Dimension wieder. Beim normalen Spielen in derselben Dimension
      wird nichts doppelt gemeldet. Ein Gang durchs Portal löst keine neue Discord-Meldung aus,
      solange der Cooldown läuft.
- [ ] **Baritone-Stop:** `.stashhunter stop` / Dimensionswechsel / AltitudeLoss-Recovery beendet den
      Elytra-Flug wirklich (kein Re-Path/Re-Arm).
- [ ] **Landungs-Timeout:** Eine festsitzende Landung bricht nach 2 min ab („Landing timed out -
      staying airborne.“), danach arbeiten AltitudeLossDetector und AutoFirework wieder.
- [ ] **Stash auf Streifengrenze** wird gemeldet.
- [ ] **Zuerst halb gesehener Stash** bekommt eine „Stash Updated“-Meldung und einen verschobenen
      Waypoint.

## Erledigte Änderungen

### OldChunkNotifier: Reset bei Dimensionswechsel

`modules/OldChunkNotifier.java`. Das Modul meldet (Chat + Discord) den Übergang in einen Chunk,
den `NewerNewChunks` als alt markiert hat; ein zusammenhängender Alt-Bereich gibt eine Meldung.

- **Problem:** `GameLeftEvent` feuert bei einem Dimensionswechsel nicht. `ChunkPos` ist nur
  `(x, z)`, bei gleichen Koordinaten in der neuen Dimension griff der Early-Return, und ein altes
  `inOldArea = true` unterdrückte die erste Meldung.
- **Fix:** Feld `lastDimension` (`ResourceKey<Level>`), geprüft in `onTick` nach dem null-Guard und
  dem Cooldown-Countdown. `reset()` setzt es mit auf null.
- **Bewusst:** Beim Dimensionswechsel werden nur `lastChunk` und `inOldArea` zurückgesetzt, nicht
  `reset()`. Sonst wäre der Cooldown 0 und jeder Portalgang gäbe eine neue Discord-Meldung.
- **Bewusst nicht umgesetzt:** Reset bei Tod/Respawn (Respawn meist in derselben Dimension; es gibt
  kein Death-Event, `StashHunterModule` erkennt Tod über `getHealth()`-Tracking).

### Baritone: Cancel-Fix

`baritone/BaritoneImpl.java`. `cancel()` ruft nur noch
`primary().getPathingBehavior().cancelEverything()` auf. Das frühere `getElytraProcess().resetState()`
ist kein Cancel, sondern startet den Flug mit demselben Ziel neu. `cancelEverything()` ruft
`onLostControl()` auf dem Elytra-Prozess auf und räumt den Nether-Pathfinder ab (im Bytecode der
Baritone-26.2-Jar geprüft).

### ElytraController: Landungs-Timeout

`utils/ElytraController.java`. Feld `landingStartTime` und `LANDING_TIMEOUT_MS = 120_000`. Findet
Baritone bei `land-after-completion` keinen Landeplatz, wird nach 2 min gecancelt. Sonst blieben
AltitudeLossDetector und AutoFirework dauerhaft stumm (PLAN-Punkt 86).

### Stashfinden überarbeitet

Neue Klassen `utils/StashBlockIndex` und `utils/StashGrowth` (je mit Unit-Test), Umbau von
`scanForBlocks`/`processCluster`/`reportStash` in `StashHunterModule`. Stashes auf der Grenze zweier
Flugstreifen werden erkannt, ein wachsender Stash wird als „Stash Updated“ nachgemeldet.

### ActivatedSpawnerDetector (neues Modul)

`modules/ActivatedSpawnerDetector.java`. Die Spawner-Konstanten liegen in 26.2 in
`BlockEntityTypes`, nicht in `BlockEntityType`.
