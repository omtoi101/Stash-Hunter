# Handoff an Claude Code

**Projekt:** Stash-Hunter (Meteor-Client-Addon, Fabric, Minecraft 26.2, Java 25, Mojang-Mappings)

**Stand 2026-10-31:** Alles unten ist eingebaut und bis `ec9efdb` committet, außer den
Review-Fixes 1–7 und den Xaero-Waypoints (umgesetzt, noch nicht committet). `gradlew build` läuft mit JDK 25 grün (inkl.
Spotless + 108 Unit-Tests). **Offen ist nur noch der In-game-Test.**

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
- [ ] **AutoElytraRepair mit Baritone-Flug:** Elytra unter `repair-threshold` (Mending, XP-Flaschen,
      Blöcke in der Hotbar). Erwartet: „Stash hunter flight stopped“, Spieler schwebt (ElytraFly
      Vanilla), „Player has stopped moving“ statt Timeout, Block, Reparatur, danach geht der Flug
      weiter und ElytraFly ist wieder aus. Ohne Mending: sofort „no Mending“ + Disconnect.
- [ ] **`.sh altitude <y>`** während eines Flugs: Baritone plant zum nächsten Waypoint auf neuer
      Höhe neu; GUI-Wert `flight-altitude` zieht mit; `.sh resume` nutzt die aktuelle Höhe.
- [ ] **Raketen knapp** (Baritone-Flug, ≤ `low-firework-threshold`): Chat + Discord „Fireworks low“,
      Trip gespeichert, Baritone landet ~64 Blöcke voraus, AutoFirework/AltitudeLoss bleiben still;
      nach Auffüllen setzt `.sh resume` fort.
- [ ] **Scan-Cache:** Flug mit `scan-radius` 256, Stash wird weiterhin gefunden; mit `spark` weniger
      Tick-Zeit als vorher. Kiste in einem gescannten Chunk setzen → nächster Scan meldet sie.
- [ ] **Ersatz-Elytra:** zweite Elytra im Inventar → bei Schwelle „Swapped to a spare elytra“, Flug
      läuft ohne Stopp weiter; ohne Ersatz startet die Reparatur.
- [ ] **Reparatur unmöglich** (keine XP/kein Mending) im Baritone-Flug → Landung statt Disconnect.
- [ ] **Trips:** `.sh list` zeigt höchstens 20; Pause → Resume → Pause ergibt einen Eintrag.
- [ ] **Baritone landet mitten im Scan** (z. B. Raketen leer mit `land-on-low-fireworks` aus):
      keine Elytra-Pose am Boden; mit Raketen startet Baritone nach ≤ 5 s neu (Waypoint im Umkreis
      von 64 Blöcken zählt als erreicht), ohne Raketen bzw. nach 3 Fehlstarts „Flight Paused After
      Landing“ und `.sh resume` geht.
- [ ] **Während einer Reparatur:** `.sh stop` → Reparatur endet, Flug bleibt aus. Kick mitten in der
      Reparatur → nach Rejoin kein Weiterreparieren, ElytraFly/AutoEXP wieder im alten Zustand. Tod
      während der Reparatur → „Repair aborted“, kein Weiterflug vom Spawn. Eigenes AutoEXP bleibt
      nach der Reparatur an.

- [ ] **Edge-/Trail-Following** (NewerNewChunks an, `dynamic-trail-detection`): Mit Baritone wird der
      Pfad nur noch alle ~25 Blöcke neu gesetzt statt jeden Tick; Flug folgt der Kante/Spur trotzdem.
- [ ] **NewerNewChunks + activated-spawner-detector gleichzeitig an:** neue/alte Chunks und aktivierte
      Spawner/Trial-Spawner werden weiter erkannt; keine doppelt tickenden Spawner/Truhen.
- [ ] **NewerNewChunks mit großen Datendateien:** Aktivieren lädt einmal (kein doppelter Hänger),
      Rendern bleibt flüssig.

## Punkte aus dem Code-Review (2026-10-31)

Geprüft: die damals nicht committeten Änderungen unter `src/` sowie Speichern/Laden (`Config`,
`TripManager`, `AtomicFiles`, `ReportedStashes`, Datendateien von NewerNewChunks). Nach Priorität
sortiert. In [PLAN.md](PLAN.md) stehen sie als Abschnitt 0, Punkte 89–96 (1 = 89 … 8 = 96).

**Umgesetzt (1–7, nicht committet):**

- 1: `AtomicFiles.replace()` wiederholt das Umbenennen bei jeder `FileSystemException` (offene
  Zieldatei → `AccessDeniedException`, offene Temp-Datei → Sharing Violation als
  `FileSystemException`, beides mit JDK 25 nachgestellt), 5 × 30 ms, danach `overwrite()` direkt
  in die Zieldatei (mit `fsync`). Scheitert das Löschen der Temp-Datei im `finally`, wird das
  geschluckt (vorher hätte es einen erfolgreichen Save als Fehler gemeldet). Eigener Retry statt
  `Util.safeReplaceFile`: kleiner und ohne Minecraft-Bootstrap testbar. Test:
  `AtomicFilesTest.replacesFileThatIsHeldOpen`.
- 2: `Config.save()` setzt bei `IOException` `dirty` wieder und plant über `schedule()` einen
  neuen Versuch nach 30 s (`RETRY_DELAY_MS`, damit eine dauerhaft kaputte Platte das Log nicht
  flutet); nach `shutdown()` nicht mehr.
- 3: neues `utils/AppendFiles.append()` (+ Test): prüft das letzte Byte, setzt bei fehlendem `\n`/`\r`
  einen Zeilenumbruch voran. Benutzt von `ReportedStashes.add()` und
  `NewerNewChunks.flushPendingWrites()`. Das Bruchstück selbst bleibt als unlesbare Zeile stehen.
- 4: `ElytraController.onTick()` ruft `clearGroundGlide()` nur bei `active || landingAfterCompletion`.
  In-game noch prüfen. Während einer Reparatur ist der Flug pausiert (`active = false`); die
  Reparatur nutzt `deployElytra()` mit echtem `START_FALL_FLYING`-Paket, das erzeugt keinen
  rein clientseitigen Gleitflug.
- 5: `AtomicFiles.deleteLeftovers(target)` löscht `<Dateiname><Zahl>.tmp`; aufgerufen am Anfang von
  `Config.load()` und beim ersten `TripManager.loadTrips()`.
- 6: `StackedMinecartDetector` merkt sich gemeldete `MinecartStacks.Stack`s und vergleicht über
  `Stack.isNear(other, detection-radius)`. Jeder Wagen liegt im Radius seines Ankers, also passt das
  bei jedem Anker derselben Wagen. Zwei Stapel in Nachbarblöcken bleiben getrennt (Test).
- 7: `loadTrips()` verwirft auch `waypoints.isEmpty()`.

**Offen:** 8 (nur beobachten).

Die ursprünglichen Befunde:

1. **`AtomicFiles`: Ersetzen scheitert unter Windows, solange die Datei offen ist** (mittel, getestet).
   `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` wirft `AccessDeniedException`, wenn ein anderer
   Prozess Ziel- oder Temp-Datei ohne `FILE_SHARE_DELETE` offen hat (Virenscanner, OneDrive/Cloud-Sync,
   Editor). Mit JDK 25 nachgestellt: Ziel per `FileInputStream` offen → Move schlägt fehl, direktes
   Schreiben in dieselbe Datei klappt. Vor der Umstellung lief das Speichern in dem Fall also durch.
   Folge: nur eine Log-Warnung; bei der Config geht die Änderung verloren (siehe 2.), bei Trips der
   gespeicherte Fortschritt.
   Fix: Move bei `AccessDeniedException` einige Male mit kurzer Pause wiederholen (z. B. 5 × 20–50 ms).
   Minecraft 26.2 hat `net.minecraft.util.Util.safeReplaceFile(current, latest, backup)` (wiederholt
   das Umbenennen, legt ein Backup an); vorher prüfen, ob `Util` in Unit-Tests ohne Bootstrap lädt.
   Test: Zieldatei mit `FileInputStream` offen halten, nach kurzer Zeit schließen.

2. **`Config.save()` setzt `dirty = false` vor dem Schreiben** (niedrig, alt).
   Scheitert das Schreiben, versucht es niemand erneut; die Einstellung landet erst mit der nächsten
   Änderung auf der Platte. Fix: bei `IOException` `dirty` wieder auf `true` setzen und (außer nach
   `shutdown()`) `saveLater()` neu planen.

3. **Angehängte Datendateien: abgebrochene letzte Zeile frisst den nächsten Eintrag** (niedrig).
   `ReportedStashes.add()` und `NewerNewChunks.flushPendingWrites()` hängen an (`APPEND`). Endet die
   Datei nach einem Stromausfall/Systemabsturz mitten in einer Zeile, wird der nächste Eintrag an das
   Bruchstück geklebt (`Overworld 100 6Overworld 5 60 5` bzw. `[12, 3[5, 6]`). Beim Laden fallen beide
   weg: Der Stash wird erneut gemeldet bzw. der Chunk fehlt, und jedes Laden warnt „Skipped 1
   unreadable line(s)“.
   Fix: vor dem Anhängen das letzte Byte prüfen und bei fehlendem `\n` einen Zeilenumbruch voranstellen.
   Atomares Ersetzen wie bei Config/Trips brauchen diese Dateien nicht: Anhängen zerstört keine alten
   Einträge.

4. **`ElytraController.clearGroundGlide()` greift auch ohne laufenden Flug** (niedrig–mittel, ungeprüft).
   Läuft jeden Tick, solange `stash-hunter` an ist (`StashHunterModule.onTick` →
   `ElytraController.onTick`), und beendet jeden Gleitflug am Boden, auch ohne `.sh start`. Meteors
   ElytraFly-Modus `Bounce` bleibt bei Bodenkontakt absichtlich im Gleitflug (sendet jeden Tick
   `START_FALL_FLYING`). Wer manuell mit Bounce fliegt und das Modul nur scannen lässt, wird
   vermutlich ausgebremst.
   Fix: nur bei `active || landingAfterCompletion` ausführen. In-game prüfen: Bounce-Flug mit
   eingeschaltetem `stash-hunter`, ohne laufenden Scan.

5. **Verwaiste Temp-Dateien** (niedrig).
   Wird das Spiel zwischen `createTempFile` und `move` hart beendet (Taskmanager, JVM-Absturz), bleibt
   `stash-hunter.properties<Zahl>.tmp` bzw. `stashhunter_trips.json<Zahl>.tmp` in `meteor-client/`
   liegen; niemand räumt sie weg. Fix: beim Laden (`Config.load()`, `TripManager.loadTrips()`) alte
   `*.tmp` mit dem passenden Präfix löschen, z. B. über eine `AtomicFiles.cleanup(target)`.

6. **`StackedMinecartDetector` kann einen Stapel doppelt melden** (niedrig).
   `announced` merkt sich die Blockposition des ersten Wagens einer Gruppe. Die Reihenfolge von
   `entitiesForRendering()` ist nicht festgelegt; liegt ein Stapel auf einer Blockgrenze (x = 10,9 und
   11,1), wechselt der Anker zwischen zwei Blöcken, und Chat/Discord melden ihn zweimal. Fix: beim
   Prüfen auch die Nachbarblöcke des Ankers berücksichtigen oder die Blockpositionen aller Wagen der
   Gruppe als gemeldet merken (dafür muss `MinecartStacks.Stack` sie liefern).

7. **Trip mit leerer Waypoint-Liste blockiert den Flug** (niedrig).
   `TripManager.loadTrips()` verwirft nur Trips mit `waypoints == null`. Ein Trip mit `"waypoints": []`
   (nur durch Handbearbeitung oder kaputte Datei) setzt bei `.sh resume` `active = true`; `onTick`
   kehrt wegen `waypoints.isEmpty()` sofort zurück, `.sh start` meldet „already active“, bis `.sh stop`
   kommt. Fix: leere Listen in `loadTrips()` mit verwerfen.

8. **Trips werden auf dem Client-Thread gespeichert, jetzt mit `fsync`** (niedrig, beobachten).
   `TripManager.saveTrip()` läuft bei Start, Pause und Landungen im Tick; `fsync` kann auf HDDs einige
   zehn ms dauern → kurzer Ruckler. Nur angehen, falls spürbar (Speichern in einen Hintergrund-Thread,
   Reihenfolge der Saves beibehalten).

Bewusst so gelassen: kein `fsync` des Ordners nach dem Umbenennen (Linux). Nach einem Stromausfall
kann die vorige Version zurückkommen, aber keine kaputte Datei. Unter Linux hat die Config nach dem
ersten Speichern die Rechte `0600` (von der Temp-Datei); wegen der Webhook-URL eher erwünscht.

## Erledigte Änderungen

### Xaero-Waypoints, PLAN-Punkt 54 (nicht committet)

- Neues Paket `xaero/`: `XaeroBridge` (öffentlich, ohne `xaero.*`-Typen, prüft
  `isModLoaded("xaerominimap")`) und `XaeroImpl` (einzige Klasse mit Xaero-Imports). Handles sind
  `Object` (`XaeroImpl.Added`: Session, Welt, Set, Waypoint). Im Bytecode geprüft:
  `StashHunterModule` verweist nur auf `XaeroBridge`, `FoundStash` auf keinen Xaero-Typ.
- `LinkageError` (andere Xaero-Version) → einmal Warnung, danach `incompatible` und Ruhe;
  `RuntimeException` → nur Warnung. `IOException` beim Speichern → Warnung, Waypoint bleibt im
  Speicher.
- `StashHunterModule.processCluster`: Xaero-Waypoint nur, wenn ein Meteor-Waypoint entstand und
  `save-to-xaero` an ist; bei „Stash Updated“ wird der alte Xaero-Waypoint mit entfernt. Entfernen
  nur, solange dieselbe Minimap-Session läuft.
- Übersprungen, wenn Xaeros aktuelle Welt eine andere Dimension hat als der Spieler (sonst wären
  Nether-Koordinaten zu skalieren).
- Gegen das Jar `fabric-26.2-26.6.2` gebaut, kompiliert ohne das eingebettete `xaerolib`. Weg wie
  `DeathpointHandler.createDeathpoint` (per `javap -c` gelesen). World Map: `GuiMap.init()` ruft
  `requestWaypointsRefresh()` selbst auf, eigener Aufruf unnötig.
- In-game offen: siehe PLAN Abschnitt 1 (`save-to-xaero`).

### Speichern über Temp-Datei

- `utils/AtomicFiles` (+ Test): schreibt in eine Temp-Datei im selben Ordner, `fsync`, dann
  `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` (ohne Atomic-Unterstützung normales Ersetzen). Legt
  fehlende Ordner an, löscht die Temp-Datei in jedem Fall. Ein Absturz mitten im Schreiben lässt die
  alte Datei stehen.
- `Config.save()` und `TripManager.saveTrips()` nutzen es. Trips werden ausdrücklich als UTF-8
  geschrieben; `JsonIOException` wird mit abgefangen. Review-Punkte dazu: 1, 2, 5 (erledigt) und 8 (offen).

### Performance-Durchgang

- `ElytraController`: Edge-/Trail-Ziel (`lookAheadTarget`) bleibt stehen, bis der Spieler halb dort
  ist. Vorher jeden Tick neues Ziel → jeden Tick `ElytraProcess.pathTo()` (neues `ElytraBehavior` +
  `repackChunks()`, im Bytecode geprüft). Totes `visitedChunks` (wuchs unbegrenzt) entfernt.
  Chunk-Koordinaten mit `Mth.floor` statt `(int)` (auch in `TrailAnalysis`).
- `NewerNewChunks`: `loadData()` beim Aktivieren nur noch einmal (`loadingticks=1`); Rendern prüft
  das Quadrat um den Spieler, sobald ein Set größer ist; Dimensionsname wird gecacht.
- `StashBlockIndex.near()` prüft nur das Chunk-Quadrat (+ Test), `StashClustering` zählt Nachbarn
  ohne Listen, `scanChunk` liest y/z/x, `isContainer` per Set, Spielerabstand ohne `sqrt`.
- `Config.isLikelyNaturalStructure` (unbenutzt) entfernt.
- `utils/ChunkPacketDecoder`: Chunk-Paket wird für NewerNewChunks und ActivatedSpawnerDetector nur
  einmal dekodiert (Cache per Paket-Identität, geleert von einem `LOWEST`-Listener und bei
  `GameLeftEvent`; abonniert in `StashHunter.onInitialize`). Ohne Block-Entities: NewerNewChunks
  dekodierte vorher mit Block-Entity-Consumer, und bei einer `ClientLevel` ist `isInLevel()` immer
  true, also landeten `onBlockEntityAdded`/`addBlockEntityTicker` der Kopie in der echten Welt (vom
  Netty-Thread, nie entfernt; im Bytecode geprüft).

### StuckDetector nur bodennah, Baritone-Abhebezeit, Scaffold

- `StuckDetector.isNearGround()` (≤ 4 Blöcke bis Block/Flüssigkeit): nur dann `stopFallFlying()`
  (clientseitig, lässt den Spieler fallen). Höher: keine Aktion, der Controller übernimmt.
- `restartBaritoneAfterLanding`: erster Neustart nach 5 s, weitere erst nach 20 s
  (`BARITONE_TAKEOFF_GRACE_MS`), damit ein Gang zum Absprungpunkt nicht neu gestartet wird.
  Zähler zurück, sobald der Spieler wieder in der Luft ist. Pause spätestens nach ~60 s am Boden.
- AutoElytraRepair `restoreScaffold()`: Scaffold nur aus, wenn die Reparatur es eingeschaltet hat;
  `air-place`/`auto-switch` nur zurück, wenn gesichert (vorher konnte ein früher Abbruch ein eigenes
  Scaffold mit Standardwerten überschreiben).

### Fehlersuche AutoElytraRepair

- Reparatur unmöglich **am Boden** → nur pausieren statt Emergency-Disconnect (`onGround`, nicht
  `!isFallFlying`, sonst würde beim Fallen mit zugeklappter Elytra pausiert).
- Ersatz-Elytra: kein Tausch bei offenem fremdem Container (Slot-IDs); bei kritischer Haltbarkeit
  während einer Landung zuerst tauschen, dann erst Disconnect.
- `GameLeftEvent` → `abortRepair()`: geliehene Module zurück, Zustand auf MONITORING (vorher lief
  die Reparatur nach Rejoin mit altem Zustand weiter).
- Tod/Dimensionswechsel während der Reparatur → `abortRepair()`, Flug bleibt pausiert.
- AutoEXP wird nur ausgeschaltet, wenn die Reparatur es eingeschaltet hat.
- `.sh stop`/`pause` während einer Reparatur: `keepFlightPaused()`, danach kein `resume()` (vorher
  meldete der Befehl „not active“ und der Flug lief nach der Reparatur weiter).

### Fix: in Elytra-Pose am Boden gefangen nach Baritone-Landung

- Ursache: Landete Baritone selbst, blieb `ElytraController` aktiv und fiel auf `controlFlight`
  zurück. Das rief jeden Tick `startFallFlying()` nur clientseitig auf (Server korrigiert das nie),
  hielt W gedrückt; Baritone hielt den Spieler für fliegend, `.sh start/resume` → „already active“.
- `deployElytra()` macht es wie Vanilla (`LocalPlayer.aiStep`): am Boden springen, in der Luft
  `tryToStartFallFlying()` + `START_FALL_FLYING`-Paket. Auch in AutoElytraRepair verwendet.
- `clearGroundGlide()` (jeder Tick) beendet einen Gleitflug am Boden.
- `driveTowards`: Baritone inaktiv **am Boden** → `restartBaritoneAfterLanding` (alle 5 s, max. 3,
  Waypoint < 64 Blöcke gilt als erreicht; ohne Raketen pausieren). Nur in der Luft noch Fallback.
- `StuckDetector` wertet „Baritone inaktiv“ am Boden nicht mehr als Hängen.

### Optimierungen: Scan-Cache, Trips, Ersatz-Elytra, Baritone-Steigen

- **Scan-Cache:** `utils/ChunkScanSchedule` (+ Test). `scanForBlocks` liest nur Chunks, die neu sind,
  per `ChunkDataEvent`/`BlockUpdateEvent` (nur Stash-Blocktypen) invalidiert wurden oder deren Scan
  ≥ 60 s her ist. Ändert sich das Block-Set (Einstellungen), wird der Cache geleert. Ohne neu
  gescannten Chunk entfällt das Clustering.
- **Trips:** `TripManager.saveTrip(replaces, …)` statt `addTrip`, max. 20 (`utils/TripHistory` +
  Test). `ElytraController.tripTimestamp` merkt sich den Eintrag des laufenden Flugs (start, pause,
  resume, Landungen), so ersetzt jedes Pausieren ihn.
- **Ersatz-Elytra:** `AutoElytraRepair.swapToSpareElytra()` (`swap-to-spare`, Standard an) legt die
  Elytra mit der meisten Haltbarkeit über der Schwelle an (`InvUtils.move().toArmor(2)` wie
  Meteors ChestSwap).
- **Baritone-Steigen:** `climbToAltitude()` ist im Baritone-Modus ein No-op (ein Ziel direkt über
  dem Spieler wäre ein erreichtes Ziel → Baritone landet). Nach der Reparatur kein `setXRot(-30)`
  mehr bei Baritone-Flug.
- **Reparatur unmöglich:** `cannotRepair()` → `ElytraController.landSafely()` (aus der
  Raketen-Landung herausgezogen); nur wenn Baritone nicht übernimmt, Emergency-Disconnect. Während
  jeder Baritone-Landung prüft `handleMonitoring` auf kritische Haltbarkeit.

### Flughöhe per Befehl, Landung bei knappen Raketen

- `.sh altitude [y]` (`StashHunterCommand`) setzt über `StashHunterModule.setFlightAltitude` die
  Meteor-Einstellung, deren `onChanged` ruft `ElytraController.applyFlightAltitude()`: restliche
  Waypoints bekommen die neue Höhe (neue Liste, `withAltitude`), darunter wird gestiegen.
  `resume(TripData)` setzt die aktuelle Höhe ebenfalls ein.
- `ElytraController.landIfFireworksLow()` (1×/s, nur Baritone-Flug im Gleitflug): bei
  ≤ `Config.lowFireworkThreshold` Raketen Trip speichern, `handOffLandingToBaritone(Ziel 64 Blöcke
  voraus)`, dann derselbe Landezustand wie `land-after-completion` (`beginBaritoneLanding`, Timeout
  2 min). Klappt die Übergabe nicht: `stop()` + Warnung „land manually“. Fallback-Flug braucht keine
  Raketen (ElytraFly setzt die Geschwindigkeit) und wird nicht geprüft.

### AutoElytraRepair: Flug pausieren statt nur ElytraFly umstellen

`modules/AutoElytraRepair.java`. Log vom 2026-10-31: Mit `useBaritonePathing=true` flog Baritone
während der Reparatur weiter. Die alten ElytraFly-Schalter wirkten nicht, weil ElytraFly aus war;
`auto-hover` gilt ohnehin nur mit Shift knapp über dem Boden. Folge: Stopp-Timeout, Scaffold
verfehlt, Schleife Abbruch → „Flug fortsetzen“ → sofort neue Reparatur, am Ende Disconnect.

- `initiateRepairSequence` prüft Mending (AutoEXP repariert nichts ohne) und ruft
  `ElytraController.pause()`. `handleResumingFlight` setzte `resume()` schon voraus.
- Schweben: ElytraFly wird eingeschaltet und auf Vanilla gestellt (ohne Tasten horizontal 0,
  Fallen × `fall-multiplier`). Modus, Autopilot und Auto-Takeoff werden gesichert und
  wiederhergestellt; ElytraFly geht vor `resume()` wieder aus, sonst kämpft es mit Baritone.
- Scaffold-Fehlschlag: bis zu 3 Versuche, dann Emergency-Disconnect statt Weiterfliegen unter
  der Schwelle.
- `keyUp.setDown(true)` nach der Reparatur entfernt: Den Vorwärts-Key hält der Controller selbst.
- Deaktivieren mitten in der Reparatur setzt den Flug fort.
- `StuckDetector` pausiert während der Reparatur: Das Schweben sah wie Rubber-Banding aus, und
  der Fix hätte den Gleitflug beendet.

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
