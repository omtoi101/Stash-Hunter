# Erkennung: Signal-Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stash-Hunter meldet kleine sichere Funde und Spuren-Kombinationen in zwei Stufen (Hinweis/Stash), mit Diagnose, Radius-Hinweis und drei neuen ESP-Modulen.

**Architecture:** Neues Paket `com.stashhunter.stashhunter.signals` mit reiner, ohne Minecraft-Bootstrap testbarer Logik (Arten, Gewichte, Clustering, Bewertung, Diagnose, Hub für Fremd-Module). Eine dünne Minecraft-Schicht übersetzt Blöcke/Entities in Signale. `StashHunterModule` ersetzt die Container-Pipeline in `findStashes`/`processCluster` durch diese Logik; `classic` reproduziert 26.2.2.

**Tech Stack:** Java 25, Minecraft 26.3 (Mojang-Namen), Meteor Client 26.3, JUnit 5, Spotless.

**Spec:** `docs/superpowers/specs/2026-11-28-detection-signal-score-design.md`

## Global Constraints

- Branch `port/26.3`, auf dem fertigen Port aufsetzend. Build: `./gradlew build` mit JDK 25 (`JAVA_HOME` auf `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.10002-hotspot`).
- Unit-Tests ohne Minecraft-Bootstrap: kein `Blocks.*`, `Items.*`, `Level` in getesteter Logik; `BlockPos`, `ChunkPos`, `CompoundTag` und Meteors `Dimension` sind erlaubt (werden schon so getestet).
- Alles clean-room; Klassenkopf neuer Detektor-/ESP-Klassen: „Idee aus <Quelle>, kein Code übernommen“ (Quellen laut Spec: Trouser-Streak/etianl, Meteorite, windoid).
- Neue Module standardmäßig aus. Setting-Namen und Chat-Texte englisch.
- `sensitivity` Default `normal`; `classic` = exakt 26.2.2-Verhalten (nur Container, `block-detection-threshold`, keine Hinweise).
- `block-detection-threshold` als Container-Schwelle: `classic` direkt, `normal` `min(Wert, 6)`, `sensitive` `min(Wert, 4)`.
- `storage-only-mode` wirkt nur in `classic`.
- Strukturfilter (Volumen, Dichte, `tighten`, Dungeon, Spawner-Nähe) nur auf der Container-Teilmenge; starke Arten und Spuren bleiben.
- Hinweis: Chat, Waypoint (optional Xaero), ESP, Discord nur mit `discord-hints` (Default aus). Stash: wie heute plus Spurenliste in Discord.
- `notification-density-threshold` nur für Stash über die Container-Bedingung.
- Hinweis → Stash meldet „Stash Updated“ mit Discord, auch ohne `StashGrowth.grewEnough` und sitzungsübergreifend; nie herabstufen.
- Tick-Budget: Scan bleibt pro Section auf `maybeHas`-Vorprüfung; Entity-Durchlauf nur im Scan-Radius.

### Gewichte und Schwellen (Festlegung dieses Plans)

| Art (`SignalKind`) | Kategorie | Gewicht |
|---|---|---|
| `SHULKER_BOX`, `ENDER_CHEST` | STRONG | 10 |
| `CHEST`, `TRAPPED_CHEST`, `BARREL` | CONTAINER | 2 |
| `HOPPER`, `DISPENSER`, `DROPPER`, `FURNACE` (alle Öfen) | CONTAINER | 1 |
| `WRITTEN_SIGN`, `UNNATURAL_BLOCK`, `OPEN_PORTAL`, `OBSIDIAN_FRAME`, `SKY_BUILD`, `NETHER_ROOF_BUILD`, `UNNATURAL_POT`, `ITEM_FRAME`, `NAMED_ENTITY`, `TRADED_VILLAGER`, `CHESTED_ANIMAL`, `ACTIVATED_SPAWNER`, `CAVE_DISTURBANCE` | TRACE | 3 |
| `PLACED_BEDROCK`, `MINECART_STACK`, `BEACON`, `CONDUIT` | TRACE | 4 |
| `BED`, `CRAFTING_TABLE`, `ANVIL`, `ENCHANTING_TABLE`, `BREWING_STAND`, `BOAT`, `CHEST_MINECART`, `MOB_GEAR`, `SPAWNER` | WEAK_TRACE | 1 |

| Stufe | Hinweis-Score | Stash-Score | Container-Kappe |
|---|---|---|---|
| `classic` | – (keine Hinweise) | – | Setting direkt |
| `normal` | 6 | 25 | 6 |
| `sensitive` | 4 | 15 | 4 |

Regeln (`SignalScoring`): Score = Summe der Gewichte, WEAK_TRACE zusammen höchstens 4. **Hinweis**, wenn eine STRONG-Art dabei ist, oder wenn mindestens eine TRACE-Art dabei ist und Score ≥ Hinweis-Score. **Stash**, wenn die Container-Bedingung gilt (gefilterte Container ≥ Kappe und `hasStashShape`), oder Score ≥ Stash-Score und mindestens eine STRONG- oder TRACE-Art dabei ist. WEAK_TRACE und CONTAINER allein ergeben nie einen Hinweis (Dörfer, Mineshafts).

## Review Focus

- **Dorf ohne Spieler** (Betten, Werkbänke, Ambosse, 2 Kisten verstreut): kein Hinweis, kein Stash. Test in Task 2.
- **Alte `reported_stashes`-Datei ohne Stufe** (Zeilen „OVERWORLD 1 2 3“): lädt, alle Einträge gelten als Stash, keine Doppelmeldung nach dem Update. Test in Task 4.
- **Stash-Waypoints aus 26.2.2** („10 Blocks at …“) verhindern weiter Neumeldungen; Hinweis-Waypoints verhindern nur Hinweise, nicht den Aufstieg zum Stash. Test in Task 5.
- **Leeres Spawner-Tag** (26.3 liefert `null` statt leerem Tag): kein NPE, die übrigen Spawner des Chunks werden weiter gefunden. Test in Task 9.
- **Riesige Diagnose-Logdatei** nach stundenlangem Flug: Datei bleibt ≤ 5 MB (Rotation auf `.old`). Test in Task 6.

---

### Task 1: Signal-Arten und Empfindlichkeit

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalCategory.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalKind.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/TraceGroup.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/Signal.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/Sensitivity.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/SignalKindTest.java`

**Interfaces:**
- Produces:
  - `enum SignalCategory { STRONG, CONTAINER, TRACE, WEAK_TRACE }`
  - `enum TraceGroup { SIGNS, UNNATURAL_BLOCKS, PORTALS, HIGH_BUILDS, BEDROCK, ENTITIES, OTHER_MODULES, BASE_BLOCKS, NONE }` (Schalter der Settings-Gruppe *Signals*; Container/Strong = `NONE`)
  - `enum SignalKind` mit allen Arten aus der Tabelle; `SignalCategory category()`, `int weight()`, `TraceGroup group()`, `String label()` (englisch, Plural-tauglich, z. B. `"shulker box"`).
  - `record Signal(BlockPos pos, SignalKind kind)`
  - `enum Sensitivity { CLASSIC, NORMAL, SENSITIVE }` mit `int hintScore()`, `int stashScore()`, `int containerThreshold(int setting)`, `boolean hints()` (Werte aus der Tabelle; `CLASSIC.hints() == false`).

- [ ] **Step 1: Failing test schreiben**

```java
@Test void strongKindAloneReachesHintInNormalAndSensitive() {
    for (Sensitivity s : List.of(Sensitivity.NORMAL, Sensitivity.SENSITIVE))
        for (SignalKind k : SignalKind.values())
            if (k.category() == SignalCategory.STRONG) assertTrue(k.weight() >= s.hintScore());
}
@Test void noSingleTraceReachesHintInNormal() {
    for (SignalKind k : SignalKind.values())
        if (k.category() == SignalCategory.TRACE || k.category() == SignalCategory.WEAK_TRACE)
            assertTrue(k.weight() < Sensitivity.NORMAL.hintScore(), k.name());
}
@Test void containerThresholdCapsTheSetting() {
    assertEquals(10, Sensitivity.CLASSIC.containerThreshold(10));
    assertEquals(6, Sensitivity.NORMAL.containerThreshold(10));
    assertEquals(4, Sensitivity.NORMAL.containerThreshold(4));
    assertEquals(4, Sensitivity.SENSITIVE.containerThreshold(10));
}
@Test void classicHasNoHints() { assertFalse(Sensitivity.CLASSIC.hints()); }
@Test void containersAndStrongKindsHaveNoTraceGroup() {
    for (SignalKind k : SignalKind.values())
        if (k.category() == SignalCategory.STRONG || k.category() == SignalCategory.CONTAINER) assertEquals(TraceGroup.NONE, k.group());
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `./gradlew test --tests '*SignalKindTest'`
Expected: FAIL (Compile-Fehler, Klassen fehlen).

- [ ] **Step 3: Enums und Record anlegen**

Werte exakt aus „Gewichte und Schwellen“. Gruppen: Schild → SIGNS; UNNATURAL_BLOCK, BEACON, CONDUIT, UNNATURAL_POT → UNNATURAL_BLOCKS; OPEN_PORTAL, OBSIDIAN_FRAME → PORTALS; SKY_BUILD, NETHER_ROOF_BUILD → HIGH_BUILDS; PLACED_BEDROCK → BEDROCK; ITEM_FRAME, NAMED_ENTITY, TRADED_VILLAGER, CHESTED_ANIMAL, BOAT, CHEST_MINECART, MOB_GEAR → ENTITIES; ACTIVATED_SPAWNER, MINECART_STACK, CAVE_DISTURBANCE → OTHER_MODULES; BED, CRAFTING_TABLE, ANVIL, ENCHANTING_TABLE, BREWING_STAND, SPAWNER → BASE_BLOCKS.

- [ ] **Step 4: Test grün**

Run: `./gradlew test --tests '*SignalKindTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/stashhunter/stashhunter/signals src/test/java/com/stashhunter/stashhunter/signals
git commit -m "Add signal kinds, weights and sensitivity levels"
```

### Task 2: Bewertung eines Clusters

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/Tier.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/Verdict.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalScoring.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/SignalScoringTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `enum Tier { NONE, HINT, STASH }` (Reihenfolge = Rang, `compareTo` nutzbar)
  - `record Verdict(Tier tier, int score, boolean byContainers)` (`byContainers` = Stash über die Container-Bedingung, steuert die Dichte-Schwelle)
  - `static Verdict SignalScoring.evaluate(List<Signal> signals, List<BlockPos> keptContainers, boolean containerShapeOk, Sensitivity sensitivity, int thresholdSetting)` — `signals` = alle Signale des Clusters **nach** Strukturfilter (gefilterte Container schon entfernt), `keptContainers` = deren Container-Positionen, `containerShapeOk` = Ergebnis von `hasStashShape` auf `keptContainers`.

- [ ] **Step 1: Failing tests schreiben** (Hilfsfunktion `sig(x,y,z,kind)` im Test)

```java
@Test void threeBuriedShulkersAreAHint()      // 3× SHULKER_BOX, NORMAL → HINT
@Test void oneEnderChestIsAHint()              // ENDER_CHEST → HINT
@Test void signBedChestIsAHint()               // WRITTEN_SIGN+BED+CHEST → HINT, score 6
@Test void tenDenseChestsAreAStashByContainers() // 10× CHEST, shapeOk, NORMAL → STASH, byContainers
@Test void sixChestsAreAStashInNormalButNotInClassic() // 6× CHEST, setting 10: NORMAL STASH, CLASSIC NONE
@Test void classicNeverGivesHints()            // 3× SHULKER_BOX, CLASSIC → NONE
@Test void classicStashExactlyAtSettingThreshold() // 10× CHEST shapeOk CLASSIC setting 10 → STASH; 9 → NONE; shapeOk=false → NONE
@Test void villageWithoutPlayersIsNothing()    // 8× BED, 4× CRAFTING_TABLE, ANVIL, BREWING_STAND, 2× CHEST → NONE
@Test void mineshaftMinecartsAreNothing()      // 6× CHEST_MINECART, 1× SPAWNER → NONE
@Test void loneSignIsNothing()                 // WRITTEN_SIGN → NONE
@Test void weakTracesAreCappedAtFour()         // WRITTEN_SIGN + 20× BED → score 7
@Test void manyTracesReachStashByScore()       // 4× MINECART_STACK, 3× WRITTEN_SIGN, PLACED_BEDROCK... score ≥ 25 → STASH, !byContainers
@Test void shulkerInADungeonIsStillAHint()     // SPAWNER + SHULKER_BOX (chests already filtered) → HINT
@Test void sensitiveLowersHintThreshold()      // WRITTEN_SIGN + BED → NORMAL NONE (4), SENSITIVE HINT
```
Jeder Test prüft `tier` und, wo angegeben, `score`/`byContainers`.

- [ ] **Step 2: Fehlschlag sehen**

Run: `./gradlew test --tests '*SignalScoringTest'`
Expected: FAIL (Klassen fehlen).

- [ ] **Step 3: `SignalScoring.evaluate` nach den Regeln unter „Gewichte und Schwellen“ implementieren**

`CLASSIC`: nur Container-Bedingung (`keptContainers.size() >= thresholdSetting && containerShapeOk`), sonst `NONE`.

- [ ] **Step 4: Grün**

Run: `./gradlew test --tests '*SignalScoringTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/stashhunter/stashhunter/signals src/test/java/com/stashhunter/stashhunter/signals
git commit -m "Score signal clusters into hints and stashes"
```

### Task 3: Cluster bilden und Container filtern

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/Rejection.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/ContainerFilter.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/Candidate.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalClusterer.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/SignalClustererTest.java`

**Interfaces:**
- Consumes: Task 1, `StashClustering.cluster/tighten/center`, `DungeonFilter.isDungeonOnly`.
- Produces:
  - `enum Rejection { LOW_SCORE, STRUCTURE_SHAPE, STRUCTURE_DUNGEON, STRUCTURE_SPAWNER, STRUCTURE_SEED, ALREADY_REPORTED }` (`STRUCTURE_SEED` wird erst vom Struktur-Locator benutzt)
  - `interface ContainerFilter { boolean filterNaturalStructures(); boolean hasStashShape(List<BlockPos> containers); boolean nearStructureSpawner(BlockPos center); }`
  - `record Candidate(BlockPos center, List<Signal> signals, List<BlockPos> keptContainers, boolean containerShapeOk, Map<Rejection, Integer> filtered)` — `filtered` zählt entfernte Container je Grund.
  - `static List<Candidate> SignalClusterer.candidates(Collection<Signal> signals, Set<BlockPos> spawners, int linkDistance, int maxRadius, int minContainers, ContainerFilter filter)`

Ablauf: `StashClustering.cluster` über alle Signal-Positionen; pro Cluster die Container-Teilmenge (Kategorie CONTAINER) nehmen; ist `filterNaturalStructures()` an: Dungeon-Prüfung (`isDungeonOnly` mit Spawnern aus `spawners`, Kisten = CHEST-Signale) → alle Container als `STRUCTURE_DUNGEON` entfernen; sonst `tighten(containers, linkDistance, maxRadius, minContainers, filter::hasStashShape)`, Container außerhalb der Teile als `STRUCTURE_SHAPE` entfernen; dann `nearStructureSpawner(center)` → übrige Container als `STRUCTURE_SPAWNER` entfernen. STRONG/TRACE/WEAK_TRACE bleiben immer. Leere Cluster fallen weg.

- [ ] **Step 1: Failing tests**

```java
@Test void bedBetweenTwoChestGroupsDoesNotRescueSparseChests() // 2 Kistengruppen à 2 weit auseinander + Bett dazwischen, shape lehnt ab → keptContainers leer, BED bleibt, filtered[STRUCTURE_SHAPE]==4
@Test void dungeonChestsAreFilteredButShulkerStays()       // SPAWNER(Set) + 2 CHEST + SHULKER_BOX → kept leer, SHULKER bleibt, filtered[STRUCTURE_DUNGEON]==2
@Test void spawnerProximityFiltersOnlyContainers()         // nearStructureSpawner=true → Container weg, WRITTEN_SIGN bleibt
@Test void filterOffKeepsAllContainers()                   // filterNaturalStructures=false → alle Container kept
@Test void separateGroupsGiveSeparateCandidates()          // zwei Gruppen 500 Blöcke auseinander → 2 Candidates
```

- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*SignalClustererTest'` — Expected: FAIL

- [ ] **Step 3: `SignalClusterer.candidates` implementieren** (Ablauf oben; `containerShapeOk = !kept.isEmpty() && filter.hasStashShape(kept)`)

- [ ] **Step 4: Grün** — Run: `./gradlew test --tests '*SignalClustererTest'` — Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/stashhunter/stashhunter/signals src/test/java/com/stashhunter/stashhunter/signals
git commit -m "Cluster signals and filter structure containers only"
```

### Task 4: Gemeldete Stufe merken

**Files:**
- Modify: `src/main/java/com/stashhunter/stashhunter/utils/ReportedStashes.java`
- Test: `src/test/java/com/stashhunter/stashhunter/utils/ReportedStashesTest.java`

**Interfaces:**
- Consumes: `Tier` (Task 2).
- Produces: `void add(String dimension, BlockPos pos, Tier tier)`; `Tier highestTierNear(String dimension, BlockPos pos, int radius)` (`Tier.NONE`, wenn nichts in der Nähe). `isNear` und `add(dimension,pos)` entfallen; Aufrufer in Task 8.
  Dateiformat: `dimension x y z TIER`; Zeilen mit 4 Feldern = `STASH`.

- [ ] **Step 1: Failing tests** (zu den bestehenden)

```java
@Test void legacyLinesCountAsStash()       // Datei "OVERWORLD 1 64 1" → highestTierNear(...)==STASH
@Test void hintIsStoredWithItsTier()       // add(HINT), neu laden → HINT
@Test void highestTierWins()               // HINT und STASH nah beieinander → STASH
@Test void nothingNearIsNone()
```
Bestehende Tests auf die neue API umstellen.

- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*ReportedStashesTest'` — Expected: FAIL
- [ ] **Step 3: Implementieren** (Speicher pro Dimension: Liste von `(BlockPos, Tier)`)
- [ ] **Step 4: Grün** — gleicher Befehl — Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Remember the reported tier of each stash"`

### Task 5: Waypoint-Namen und Fremd-Signale

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/WaypointNames.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalHub.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/WaypointNamesTest.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/SignalHubTest.java`

**Interfaces:**
- Produces:
  - `WaypointNames`: `static String stash(int blocks, String coords)` → `"<n> Blocks at <coords>"` (unverändert zu 26.2.2), `static String hint(String summary, String coords)` → `"Hint: <summary> at <coords>"`, `static Tier tierOf(String waypointName)` → `STASH` bei `" Blocks at "`, `HINT` bei Präfix `"Hint: "`, sonst `NONE`.
  - `SignalHub` (statisch, Client-Thread): `static void put(String source, String dimension, BlockPos pos, SignalKind kind)`, `static void remove(String source, String dimension, BlockPos pos)`, `static void clear(String source)`, `static List<Signal> near(String dimension, int x, int z, int radius)`, `static void clearAll()`. Begrenzung 4096 Einträge pro Quelle (älteste fliegen).

- [ ] **Step 1: Failing tests**

```java
@Test void stashNameIsUnchangedFrom2622() { assertEquals("10 Blocks at 1, 2, 3", WaypointNames.stash(10, "1, 2, 3")); }
@Test void tierOfRecognisesBothSchemes()  // stash → STASH, hint → HINT, "Home" → NONE
@Test void hubReturnsOnlyNearbySignalsOfTheDimension()
@Test void clearingASourceKeepsOthers()
@Test void hubCapsEachSource()            // 5000 put → near über alles liefert 4096 dieser Quelle
```
- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*WaypointNamesTest' --tests '*SignalHubTest'` — Expected: FAIL
- [ ] **Step 3: Implementieren**
- [ ] **Step 4: Grün** — gleicher Befehl — Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Add waypoint naming and a hub for module signals"`

### Task 6: Diagnose und Radius-Hinweis

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/ScanDiagnostics.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/DiagnosticsLog.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/ScanRadiusAdvice.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/ScanDiagnosticsTest.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/DiagnosticsLogTest.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/ScanRadiusAdviceTest.java`

**Interfaces:**
- Consumes: `Rejection`, `SignalCategory`, `Tier`, `AppendFiles`.
- Produces:
  - `ScanDiagnostics`: `void startPeriod(String label)`, `void chunkScanned()`, `void chunksNeverLoaded(int n)`, `void viewDistance(int chunks)`, `void signals(SignalCategory c, int n)`, `void rejected(Rejection r)`, `void reported(Tier t)`, `List<String> endPeriod()` (Chat-Zeilen der Periode, setzt sie zurück, addiert zur Sitzung), `List<String> current()` (laufende Periode + Sitzungssumme, für `.sh diag`), `void resetSession()`.
  - `DiagnosticsLog`: `DiagnosticsLog(Path file, long maxBytes)`, `void candidate(String dimension, BlockPos center, List<Signal> signals, Rejection reason)` — hängt eine Zeile an; überschreitet die Datei `maxBytes` (Default 5 MB), wird sie nach `<name>.old` verschoben (vorhandene `.old` ersetzt).
  - `ScanRadiusAdvice`: `static Optional<String> advise(int scanRadius, int viewDistanceChunks)` — Vorschlag, wenn `scanRadius < viewDistanceChunks * 16 - 32` (Text: `"The server sends chunks about <v> blocks out; scan-radius <r> could be raised to <v-16>."`), sonst leer.

- [ ] **Step 1: Failing tests**

```java
@Test void periodSummaryCountsRejectionsByReason() // 2× STRUCTURE_SHAPE, 1× LOW_SCORE → Zeile enthält "structure (shape): 2", "low score: 1"
@Test void endPeriodResetsButSessionKeepsTotals()
@Test void currentShowsPeriodAndSession()
@Test void logRotatesWhenTooBig()                // maxBytes 200, 10 Zeilen → Datei ≤ 200 Bytes, .old existiert
@Test void adviceWhenRadiusFarBelowViewDistance() // advise(64, 12) → present, enthält "176"
@Test void noAdviceWhenRadiusFits()               // advise(160, 12) → empty
```
- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*ScanDiagnosticsTest' --tests '*DiagnosticsLogTest' --tests '*ScanRadiusAdviceTest'` — Expected: FAIL
- [ ] **Step 3: Implementieren**
- [ ] **Step 4: Grün** — gleicher Befehl — Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Add scan diagnostics, log rotation and scan radius advice"`

### Task 7: Reine Geometrie-Helfer für Spuren und Tunnel

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/NaturalBedrock.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/PortalFrames.java`
- Create: `src/main/java/com/stashhunter/stashhunter/utils/TunnelShapes.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/NaturalBedrockTest.java`
- Test: `src/test/java/com/stashhunter/stashhunter/signals/PortalFramesTest.java`
- Test: `src/test/java/com/stashhunter/stashhunter/utils/TunnelShapesTest.java`

**Interfaces:**
- Produces:
  - `static boolean NaturalBedrock.isNatural(Dimension dimension, int y)` — Overworld `y <= -60`; Nether `y <= 4 || (y >= 123 && y <= 127)`; End: nie natürlich außer Exit-Portal-Bereich, dafür `isNatural(End, y)` → `false` und Aufrufer schließt `|x|,|z| <= 8` selbst aus (zweite Methode `static boolean isEndExitPortal(int x, int y, int z)` → `|x|<=4 && |z|<=4 && y>=50 && y<=80`).
  - `static List<BlockPos> PortalFrames.frames(Set<BlockPos> obsidian)` — liefert je erkanntem Rahmen eine Ecke: senkrechtes Rechteck (X- oder Z-Ebene), Innenmaß 2–21 breit × 3–21 hoch, alle Randblöcke außer den 4 Ecken in `obsidian`.
  - `TunnelShapes` mit `interface Passable { boolean at(int x, int y, int z); }`: `static boolean isHole(Passable p, int x, int y, int z, int minDepth)` (1×1-Schacht nach unten, Wände nicht passierbar), `static boolean isTunnel(Passable p, int x, int y, int z, int minLength)` (2–3 hoch, 1 breit, in X oder Z), `static boolean isStair(Passable p, int x, int y, int z, int minSteps)` (Diagonale, je Stufe +1 Y).

- [ ] **Step 1: Failing tests**

```java
@Test void overworldBedrockAboveMinus60IsPlaced()  // isNatural(Overworld, -60) true, -59 false
@Test void netherRoofBedrockIsNatural()            // 125 true, 128 false, 50 false
@Test void minimalPortalFrameIsFound()             // 4×5 Rahmen ohne Ecken → 1 Frame
@Test void frameWithGapIsNotFound()
@Test void randomObsidianBlobIsNotAFrame()         // 3×3×3 Würfel → keine Frames
@Test void verticalShaftIsAHole()                  // Passable: Säule x=0,z=0, y 60..64 frei → isHole(...,4) true
@Test void openAreaIsNoHole()
@Test void oneWideTwoHighCorridorIsATunnel()
@Test void stepsUpwardAreAStair()
```
- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*NaturalBedrockTest' --tests '*PortalFramesTest' --tests '*TunnelShapesTest'` — Expected: FAIL
- [ ] **Step 3: Implementieren**
- [ ] **Step 4: Grün** — gleicher Befehl — Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Add bedrock, portal frame and tunnel shape helpers"`

### Task 8: Minecraft-Schicht: Blöcke und Entities zu Signalen

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalBlocks.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignalEntities.java`
- Create: `src/main/java/com/stashhunter/stashhunter/signals/SignText.java`

**Interfaces:**
- Consumes: Tasks 1, 7.
- Produces:
  - `SignalBlocks.ChunkScan scan(LevelChunk chunk, Dimension dimension, Set<TraceGroup> enabled, boolean includeSpawners, boolean classicStorageOnly)` → `record ChunkScan(Map<BlockPos, SignalKind> signals, Set<BlockPos> spawners)`. `spawners` enthält immer alle Spawner (Dungeon-Filter). Im `classic`-Modus (`classicStorageOnly` gesetzt bzw. classic aktiv) nur Container/Shulker wie 26.2.2 (`Config.getActiveBlockList()`), keine Spuren.
    - Block-Entity-Durchlauf: Kisten/Fässer/Trapped/Öfen/Hopper/Dispenser/Dropper → CONTAINER-Arten; Shulker (alle Farben), Endertruhe → STRONG; Schild/Hängeschild mit Text (`SignText.hasText`) → `WRITTEN_SIGN`; Topf mit Item außerhalb der Vanilla-Liste (`string, emerald, iron_ingot, diamond, gold_ingot, …` als Konstante) → `UNNATURAL_POT`; Spawner → in `spawners`, mit `includeSpawners` zusätzlich `SPAWNER`; Leuchtfeuer/Conduit → `BEACON`/`CONDUIT`.
    - Block-Durchlauf (nur Sections, deren Palette eine gesuchte Art enthalten kann): `UNNATURAL_BLOCK`-Liste (Beton alle Farben, Köpfe/Schädel außer Wither-Skelett-Schädel, Redstone-/Diamant-/Netherite-/Smaragd-/Lapis-Block, gewachste Kupfer-Varianten), Betten (`BED`), Werkbank/Amboss/Zaubertisch/Braustand (`BASE_BLOCKS`), `NETHER_PORTAL`/`END_PORTAL` → `OPEN_PORTAL`, Obsidian → über `PortalFrames.frames` → `OBSIDIAN_FRAME` (eine Position je Rahmen), Bedrock mit `!NaturalBedrock.isNatural(...)` → `PLACED_BEDROCK` (End: Exit-Portal ausgenommen). Overworld-Sections über Y 260: jeder Nicht-Luft-Block → ein `SKY_BUILD` pro Section; Nether über Y 127: jeder Block außer Bedrock/Luft → ein `NETHER_ROOF_BUILD` pro Section.
  - `SignalEntities.Collected collect(ClientLevel level, BlockPos center, int radius, Set<TraceGroup> enabled)` → `record Collected(Map<Long, Map<BlockPos, SignalKind>> byChunk)`: Item-Frame/Glow-Item-Frame mit Item → `ITEM_FRAME`; Entity mit Custom Name (keine Spieler) → `NAMED_ENTITY`; Boot → `BOAT`; Dorfbewohner `getVillagerData().level() > 1` → `TRADED_VILLAGER`; Kisten-Minecart → `CHEST_MINECART`; Esel/Maultier/Lama/Händler-Lama mit Truhe → `CHESTED_ANIMAL`; Mob mit Spieler-Ausrüstung (`MobGear.isPlayerGear`, Task 10) → `MOB_GEAR`.
  - `SignText.hasText(SignBlockEntity sign)` → vorn oder hinten mindestens eine nicht leere Zeile.

Kein Unit-Test möglich (Minecraft-Bootstrap). Prüfung über Compile, Spotless und die In-game-Checkliste (Task 11).

- [ ] **Step 1: Klassen implementieren** (Struktur des Block-Durchlaufs wie `StashHunterModule.scanChunk` heute)
- [ ] **Step 2: Build** — Run: `./gradlew build` — Expected: `BUILD SUCCESSFUL`, alle Tests grün
- [ ] **Step 3: Commit** — `git commit -m "Translate blocks and entities into stash signals"`

### Task 9: Integration in `stash-hunter` und die drei Detektoren

**Files:**
- Modify: `src/main/java/com/stashhunter/stashhunter/modules/StashHunterModule.java` (Settings; `scanForBlocks`, `scanChunkIfDue`, `findStashes`, `processCluster`, `isNaturalStructure`, `reportStash`, `hasStashWaypointNear`, `FoundStash`, `onRender`, `onBlockUpdate`, Flug-Ende/Deaktivieren)
- Modify: `src/main/java/com/stashhunter/stashhunter/modules/ActivatedSpawnerDetector.java` (`report` → `SignalHub.put(..., ACTIVATED_SPAWNER)` nur für nicht-natürliche Struktur-Spawner; `spawnerCandidate` mit `null`-Tag)
- Modify: `src/main/java/com/stashhunter/stashhunter/modules/StackedMinecartDetector.java` (gemeldeter Stapel → `MINECART_STACK`)
- Modify: `src/main/java/com/stashhunter/stashhunter/modules/CaveDisturbanceDetector.java` (`reportNew` → `CAVE_DISTURBANCE` an der ersten Position)
- Modify: `src/main/java/com/stashhunter/stashhunter/commands/StashHunterCommand.java` (`.sh diag`; Hilfe-Text)
- Test: `src/test/java/com/stashhunter/stashhunter/modules/SpawnerCandidateTest.java`

**Interfaces:**
- Consumes: Tasks 1–8.
- Produces: Settings `sensitivity` (Enum `Sensitivity`, Default `NORMAL`), Gruppe **Signals** mit je einem `BoolSetting` pro `TraceGroup` außer `NONE` (alle an), `discord-hints` (false), in **Stash ESP** `hint-side-color`/`hint-line-color` (gelb). Jeder Detektor ruft beim Deaktivieren/Weltwechsel `SignalHub.clear("<modul-name>")`.

- [ ] **Step 1: Failing test für das Spawner-Tag**

`spawnerCandidate` selbst braucht `BlockEntityTypes` (Bootstrap), daher werden die Tag-Zugriffe getestet:

```java
@Test void nullTagHasNoMob()            { assertEquals("", ActivatedSpawnerDetector.mobIdOf(null)); }
@Test void nullTagCountsAsUntouched()   { assertEquals(ActivatedSpawnerDetector.UNTOUCHED_DELAY, ActivatedSpawnerDetector.delayOf(null)); }
@Test void mobIdIsReadFromSpawnData()   // CompoundTag mit SpawnData.entity.id = "minecraft:zombie" → "minecraft:zombie"
```
- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*SpawnerCandidateTest'` — Expected: FAIL (Compile-Fehler, Methoden fehlen)
- [ ] **Step 3: `static String mobIdOf(CompoundTag tag)` und `static int delayOf(CompoundTag tag)` (package-private, `null` = leeres Tag) aus `spawnerCandidate` herausziehen; `UNTOUCHED_DELAY` package-private** — gleicher Befehl → PASS
- [ ] **Step 4: Settings anlegen und Beschreibungen anpassen**

`block-detection-threshold`: „Containers for a stash. In normal at most 6, in sensitive at most 4 are needed.“; `storage-only-mode`: „... Only in classic sensitivity.“

- [ ] **Step 5: Scan umstellen**

`scanChunkIfDue` nutzt `SignalBlocks.scan`; der Block-Index wird `StashBlockIndex<SignalKind>`; ein zweiter `StashBlockIndex<SignalKind>` hält Entity-Signale pro Chunk (bei jedem Scan aus `SignalEntities.collect` für die Chunks im Radius ersetzt); Spawner-Positionen in einem dritten `StashBlockIndex<Boolean>`. `onBlockUpdate` invalidiert bei jedem Block, den `SignalBlocks` auswertet (Set aus `SignalBlocks.watchedBlocks()`). Diagnose: `chunkScanned`, `chunksNeverLoaded`, `viewDistance`, `signals` je Kategorie.

- [ ] **Step 6: `findStashes`/`processCluster` umstellen**

Signale = Block-Index + Entity-Index + `SignalHub.near` im selben Bereich wie heute (`range + maxClusterSize + 16`); `SignalClusterer.candidates(..., minContainers = sensitivity.containerThreshold(setting), filter)` mit `ContainerFilter` aus `filterNaturalStructures`, `hasStashShape`, `isNearDetectedStructure`. Je Candidate `SignalScoring.evaluate`. `Tier.NONE` → `diagnostics.rejected(LOW_SCORE)` + `DiagnosticsLog.candidate(...)` (nur wenn mindestens ein Signal gefiltert wurde oder Score > 0); gefilterte Container je Grund an die Diagnose. Dedupe: `max(reportedStashes.highestTierNear, Waypoint-Tier aus WaypointNames.tierOf in report-dedupe-radius, Tier früherer FoundStash dieser Sitzung)`; gemeldet wird nur, wenn neue Stufe > bekannte Stufe, oder gleiche Stufe STASH und `StashGrowth.grewEnough` (heutiger Update-Weg). Sonst `rejected(ALREADY_REPORTED)`.

- [ ] **Step 7: Meldungen**

`reportHint(...)`: Chat `"Hint: <summary> at <coords> (<dimension-id>)"`, Summary = bis zu 3 häufigste Arten (`"2 shulker boxes, 1 sign"`), Waypoint `WaypointNames.hint`, Xaero mit `save-to-xaero`, ESP in Hinweis-Farben, Discord nur mit `discord-hints` (Titel „Stash Hint“, ohne `@everyone`). `reportStash` wie heute; Waypoint `WaypointNames.stash`; Dichte-Schwelle nur bei `verdict.byContainers()`; Discord-Text zusätzlich „Traces: …“. Aufstieg Hinweis → Stash: alten Hinweis-Waypoint/-Xaero/-FoundStash entfernen, Titel „Stash Updated“, ohne `@everyone`. `reportedStashes.add(dim, pos, tier)`. `FoundStash` bekommt `Tier tier`.

- [ ] **Step 8: Diagnose-Zeitpunkte und `.sh diag`**

`diagnostics.startPeriod` bei Flugstart (`.sh start`/`spiral`/`resume`) und beim Einschalten ohne Flug; `endPeriod()` → Chat bei Stop, Pause, Abschluss, Landung und beim Ausschalten. Beim Flugstart `ScanRadiusAdvice.advise(scanRadius, mc.options.getEffectiveRenderDistance())` in den Chat. `.sh diag` gibt `diagnostics.current()` aus. Log-Datei: `<Stash-Hunter-Ordner>/diagnostics/<ServerKey>.log`.

- [ ] **Step 9: Detektoren an den Hub**

Die drei Module rufen `SignalHub.put/clear` wie unter Interfaces; ihr bisheriges Verhalten (Chat, ESP, Discord) bleibt.

- [ ] **Step 10: Build und Tests**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Step 11: Commit** — `git commit -m "Report hints and stashes from the signal score"`

### Task 10: ESP-Module

**Files:**
- Create: `src/main/java/com/stashhunter/stashhunter/utils/MobGear.java`
- Create: `src/main/java/com/stashhunter/stashhunter/modules/DroppedItemEsp.java`
- Create: `src/main/java/com/stashhunter/stashhunter/modules/MobGearEsp.java`
- Create: `src/main/java/com/stashhunter/stashhunter/modules/TunnelEsp.java`
- Modify: `src/main/java/com/stashhunter/stashhunter/StashHunter.java` (Module registrieren)
- Test: `src/test/java/com/stashhunter/stashhunter/utils/MobGearTest.java`

**Interfaces:**
- Consumes: `TunnelShapes` (Task 7), `SignalHub` (Task 5).
- Produces:
  - `static boolean MobGear.isPlayerGear(String itemId, boolean enchanted)` — `true` für `minecraft:elytra`, `minecraft:totem_of_undying`, `*shulker_box`, `minecraft:enchanted_golden_apple`, und für `minecraft:diamond_*`/`minecraft:netherite_*` Rüstung/Waffen/Werkzeuge nur mit `enchanted`.
  - `dropped-item-esp` (Item-Liste, Default wie Spec), `mob-gear-esp` (meldet zusätzlich `SignalHub.put("mob-gear-esp", …, MOB_GEAR)`), `tunnel-esp` (Settings `min-hole-depth` 4, `min-tunnel-length` 3, `min-stair-steps` 3, Scan-Radius in Chunks 4, Farben). Alle Default aus, Kategorie Stash-Hunter, Box + optional Tracer.

- [ ] **Step 1: Failing test**

```java
@Test void enchantedNetheriteIsPlayerGear()   { assertTrue(MobGear.isPlayerGear("minecraft:netherite_helmet", true)); }
@Test void plainDiamondSwordIsNot()            { assertFalse(MobGear.isPlayerGear("minecraft:diamond_sword", false)); }
@Test void totemAlwaysCounts()                 { assertTrue(MobGear.isPlayerGear("minecraft:totem_of_undying", false)); }
@Test void ironArmorNeverCounts()              { assertFalse(MobGear.isPlayerGear("minecraft:iron_chestplate", true)); }
```
- [ ] **Step 2: Fehlschlag sehen** — Run: `./gradlew test --tests '*MobGearTest'` — Expected: FAIL
- [ ] **Step 3: `MobGear` implementieren** — gleicher Befehl → PASS
- [ ] **Step 4: Die drei Module bauen** (Render wie `StashHunterModule.onRender`; `tunnel-esp` scannt nur neu geladene oder geänderte Chunks, mit `TunnelShapes` und Passierbarkeit = nicht-voller Kollisionsquader)
- [ ] **Step 5: Build** — Run: `./gradlew build` — Expected: `BUILD SUCCESSFUL`
- [ ] **Step 6: Commit** — `git commit -m "Add dropped item, mob gear and tunnel ESP modules"`

### Task 11: Doku und In-game-Checkliste

**Files:**
- Modify: `README.md` (Abschnitte *How it works* → Scoring/Hinweise, *Commands* → `.sh diag`, *Modules and settings* → neue Settings und drei Module, *Files it creates* → Diagnose-Log)
- Modify: `CHANGELOG.md` (`[Unreleased]`: *Added*, *Changed*, *Fixed* — Spawner-Tag, *Development*)
- Modify: `PLAN.md` (Abschnitt 1: In-game-Checkliste + Singleplayer-Testwelt; Abschnitt 3: 59/56/62 nach Erledigt)

**Interfaces:**
- Consumes: alle vorigen Tasks (Namen der Settings/Module/Befehle).

- [ ] **Step 1: README und CHANGELOG** nach den tatsächlichen Namen aus Tasks 9–10.
- [ ] **Step 2: PLAN.md-Testwelt** — Abschnitt „Singleplayer-Testwelt Erkennung“ mit diesen Lagen und erwarteter Meldung: 3 vergrabene Shulker (Hinweis), 1 Endertruhe in einer Höhle (Hinweis), Schild + Bett + Kiste (Hinweis), 10 Kisten dicht (Stash), Dungeon unverändert (nichts), Dorf unverändert (nichts), Shulker in einem Dungeon (Hinweis), Hinweis später auf 10 Kisten erweitern (Stash Updated), `classic` auf denselben Lagen (nur der 10-Kisten-Stash).
- [ ] **Step 3: Prüfen** — Run: `git grep -n "sensitivity\|discord-hints\|\.sh diag" README.md` — Expected: Treffer in Settings- und Command-Tabellen.
- [ ] **Step 4: Commit** — `git commit -m "Document the signal score detection"`
