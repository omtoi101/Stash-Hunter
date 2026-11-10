# Handoff an Claude Code

**Projekt:** Stash-Hunter (Meteor-Client-Addon, Fabric, Minecraft 26.2, Java 25, Mojang-Mappings)

**Stand 2026-11-10:** Version 26.2.2 ist release-fertig und enthält jetzt auch Trip-Save im
Hintergrund (PLAN 96), den ChestIndex-Ausbau (53-Rest, komplett), benannte Trips (55-Rest) und
`cave-disturbance-detector` (57), siehe die ersten beiden Abschnitte unter „Erledigte
Änderungen“. Alles ist committet. `gradlew build` läuft mit JDK 25 grün (inkl. Spotless + 157
Unit-Tests in 28 Suites). **Offen ist nur noch der In-game-Test** (Checkliste in
[PLAN.md](PLAN.md), Abschnitt 1; sie enthält jetzt auch alle Punkte der Liste unten).

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
- [ ] **AutoElytraRepair landet zum Reparieren** (Mending, XP-Flaschen, keine Blöcke nötig), mit und
      ohne Baritone. Erwartet: „Stash hunter flight stopped“, „Landing to repair it“, senkrechter
      Sinkflug (ElytraFly Vanilla + Shift) **ohne Fallschaden**, „Landed“, AutoEXP wirft am Boden,
      „Elytra repaired“ bzw. „Repair stopped at … durability“ (AutoEXP `max-threshold`). Mit Baritone
      startet Baritone selbst (Raketen nötig); ohne Baritone „Taking off“, Steigen mit Sprungtaste
      bis `flight-altitude`, dann „Resuming flight“. ElytraFly danach wieder im alten Modus/aus,
      AltitudeLossDetector/AutoFirework/StuckDetector still während des Sinkflugs.
- [ ] **Reparatur über Wasser/Lava** (z. B. Ozean, Nether-Lavasee): „No safe ground below … Moving
      on“, gleitet geradeaus bis festem Boden. Im End über dem Void ebenso.
- [ ] **XP gehen am Boden aus:** „Repair made no progress“ → Flug bleibt am Boden pausiert, Discord
      „Elytra Repair Failed - Paused“, kein Disconnect; nach Auffüllen `.sh resume`.
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
- [ ] **Trips:** `.sh list` zeigt höchstens 20 unbenannte (benannte bleiben); Pause → Resume →
      Pause ergibt einen Eintrag.
- [ ] **Baritone landet mitten im Scan** (z. B. Raketen leer mit `land-on-low-fireworks` aus):
      keine Elytra-Pose am Boden; mit Raketen startet Baritone nach ≤ 5 s neu (Waypoint im Umkreis
      von 64 Blöcken zählt als erreicht), ohne Raketen bzw. nach 3 Fehlstarts „Flight Paused After
      Landing“ und `.sh resume` geht.
- [ ] **Während einer Reparatur:** `.sh stop` → Landung und Reparatur laufen zu Ende, Flug bleibt am
      Boden aus. Kick mitten in der
      Reparatur → nach Rejoin kein Weiterreparieren, ElytraFly/AutoEXP wieder im alten Zustand. Tod
      während der Reparatur → „Repair aborted“, kein Weiterflug vom Spawn. Eigenes AutoEXP bleibt
      nach der Reparatur an.

- [ ] **Trail-Following** (NewerNewChunks an, `dynamic-trail-detection`): Mit Baritone wird der
      Pfad nur noch alle ~25 Blöcke neu gesetzt statt jeden Tick; Flug folgt der Spur trotzdem.
      Edge-Following bei ungeladenen Chunks gibt es nicht mehr (PLAN 100).
- [ ] **NewerNewChunks + activated-spawner-detector gleichzeitig an:** neue/alte Chunks und aktivierte
      Spawner/Trial-Spawner werden weiter erkannt; keine doppelt tickenden Spawner/Truhen.
- [ ] **NewerNewChunks mit großen Datendateien:** Aktivieren lädt einmal (kein doppelter Hänger),
      Rendern bleibt flüssig.

## Punkte aus dem Code-Review (2026-10-31)

Geprüft: die damals nicht committeten Änderungen unter `src/` sowie Speichern/Laden (`Config`,
`TripManager`, `AtomicFiles`, `ReportedStashes`, Datendateien von NewerNewChunks). Nach Priorität
sortiert. In [PLAN.md](PLAN.md) stehen sie als Abschnitt 0, Punkte 89–96 (1 = 89 … 8 = 96).

**Umgesetzt (1–7, in `d2f5f57`):**

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

8 ist am 2026-11-09 umgesetzt (Trip-Save im Hintergrund, siehe unten).

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

### ChestIndex fertig, benannte Trips (2026-11-09, `0236e2d`)

**PLAN 53-Rest – Waypoints, Öffnen nach dem Hinlaufen, Save im Hintergrund:**

- `ChestIndexCommand.hitLine(before, entry, label, server)`: hinter jedem Treffer `[+waypoint]`
  (`RunnableClickEvent`) → Meteor-`Waypoint` „`<label> (chest-index)`“. Dimension über
  `Dimension.toString()` (Meteor kennt nur Overworld/Nether/End, sonst kein Link). Gibt es an der
  Position schon einen Waypoint mit dem Suffix, wird keiner angelegt. Beide Links prüfen beim
  Klick `ServerKey.current()` gegen den Server der Suche (Chat-Links überleben einen
  Serverwechsel, Meteor-Waypoints sind pro Server).
- `ChestIndexer.walkTo(pos)` ersetzt den Aufruf im Befehl. Mit `open-after-walk` (Standard aus,
  Lizenz-/Opt-in-Regel) merkt sich ein eigener, im Konstruktor angemeldeter Listener
  (`WalkThenOpen`, läuft auch bei ausgeschaltetem Modul) das Ziel. Jeden Tick: solange
  `BaritoneBridge.isGoalActive()` (`ICustomGoalProcess.isActive()`, im Bytecode geprüft: `path()`
  setzt `PATH_REQUESTED`, Ankunft/Fehlschlag/`#stop` → `onLostControl()` → `NONE`) warten; dann in
  Reichweite und ohne offenen Screen (der Chat mit dem Link) → `autoTarget = null`, Klick mit
  Rotation. Nicht in Reichweite → Warnung, Abbruch. Timeout 3 min; Level-Wechsel, Tod und
  `GameLeftEvent` brechen ab. `canAutoOpen()` pausiert, solange ein Ziel offen ist. Der Container
  wird so wie ein selbst geöffneter gelesen (bleibt offen).
- `ChestIndex` speichert über einen eigenen `BackgroundSaver<Snapshot>` (lazy, ein Thread pro
  Index). `Snapshot(entries)`; `entries == null` löscht die Datei, so kann ein noch wartender
  Write ein `clear()` nicht rückgängig machen. Schlägt ein Write fehl, setzt der Thread
  `writeFailed`, der nächste `saveIfDirty()` speichert erneut (vorher blieb `dirty` stehen).
  `close()` (in `ChestIndexer.onGameLeft`, auch beim Schließen des Fensters aus der Welt)
  speichert und wartet bis 2 s, danach stoppt der Thread. Kein eigener Eintrag im Shutdown-Hook:
  der Index gehört dem Client-Thread, und `GameLeftEvent` kommt vorher.

**PLAN 55-Rest – benannte Trips (clean-room, jeff mod nicht angesehen):**

- `TripData.name` (null = unbenannt, Gson lässt das Feld dann weg; alte Dateien laden weiter).
  `TripHistory.withTrip` übernimmt den Namen des ersetzten Eintrags und kürzt nur unbenannte Trips
  auf `MAX_TRIPS`. Neu: `find` (Name ohne Groß-/Kleinschreibung, sonst Zeitstempel),
  `nameProblem` (leer, > 32 Zeichen, nur Ziffern, `§`, schon vergeben), `renamed`, `without`,
  `isComplete`, `progress` (über `WaypointPath`, Stand am Anfang des gespeicherten Abschnitts).
- `TripManager.findTrip`/`renameTrip`/`deleteTrip`, alle über den Saver.
- `ElytraController`: `resume()`/`resume(TripData)` geben `boolean` zurück und starten keinen
  fertigen Trip; `resume(long)` entfällt (der Befehl sucht selbst). `getTripTimestamp()`. Beim
  Abschluss wird der Trip mit `currentWaypoint = waypoints.size()` gespeichert (vorher blieb der
  Stand der letzten Pause stehen, `.sh resume` flog den Rest noch einmal).
- `StashHunterCommand`: `resume [trip]` (Name oder Zeitstempel, Vorschläge mit den Namen; vorher
  `LongArgumentType`), `name <name>` (laufender Flug, sonst neuester Trip), `delete <trip>` (nicht
  der laufende Flug), `list` mit Name, Waypoint, %, km und Zeit. Benutzertexte gehen als
  Format-Argument an `info`/`error` (ein `%` im Namen würde sonst `String.format` sprengen).

### Trip-Save im Hintergrund, ChestIndex-Ausbau, CaveDisturbanceDetector (2026-11-09, `0236e2d`)

**PLAN 96 – Trip-Save:**

- `utils/BackgroundSaver<T>` (+ Test): ein Daemon-Thread, `save(snapshot)` kehrt sofort zurück.
  `AtomicReference pending`: Ist schon ein Snapshot vorgemerkt, ersetzt der neue ihn nur (der
  wartende Task schreibt dann den neuesten); sonst wird ein Task eingereiht. Reihenfolge bleibt,
  veraltete Zwischenstände werden nie geschrieben. `flush()` über einen Marker-Task (FIFO),
  `shutdown(timeout)` wartet und schreibt Reste. Nach `shutdown()` schreibt `save()` direkt auf
  dem aufrufenden Thread. `RuntimeException` des Writers wird geloggt, der Thread bleibt.
- `TripManager`: liest die Datei einmal (`readTrips`, dabei `deleteLeftovers`), hält die Trips
  als unveränderliche Liste (Waypoint-Listen per `List.copyOf`/`toList()`, `null`-Waypoints aus
  handbearbeiteten Dateien fallen raus). `saveCurrentTripAsync` (ersetzt `saveTrip`) baut auf dem
  Client-Thread die neue Liste und übergibt sie dem Saver. `getTrips`/`getLatestTrip`/`getTrip`
  lesen nur noch den Speicher (vorher jedes Mal die Datei). Folge: Eine Änderung an der Datei
  von außen während des Spiels wird nicht mehr gelesen.
- Shutdown-Hook: `Config.shutdown()` → `TripManager.shutdown()` (max. 2 s) →
  `DiscordWebhook.shutdown()`.
- `ChestIndex.saveIfDirty()` schreibt inzwischen ebenfalls im Hintergrund (siehe oben, 53-Rest).

**PLAN 53-Rest – `.chest-index`:**

- `ChestIndex.searchShulkerNames(query)` (+ Test) → `NameHit(entry, shulkers)`, Teilstring ohne
  Groß-/Kleinschreibung, unbenannte Boxen zählen nicht. Befehl `.chest-index shulker <name>`.
- Trefferzeilen: Koordinaten als Link. Baritone installiert und gleiche Dimension → grün,
  `RunnableClickEvent` (Meteor, `ScreenMixin.onDefaultHandleClickEvent` führt das Runnable aus) →
  `BaritoneBridge.startGetToBlock` → `GoalGetToBlock` (läuft neben den Block). `#goto x y z` wäre
  `GoalBlock`: Baritone würde in den Block wollen und die Kiste abbauen. Verweigert, solange
  `ElytraController.isActive()`. Sonst türkis, `ClickEvent.CopyToClipboard`.
- Highlight: `ChestIndexer.highlight(entries)` (max. 256 Boxen, Doppelkiste als eine Box),
  Gruppe „Search Highlight“ (`highlight-seconds` 60, Form, Farben, Tracer). Der Render-Listener
  ist ein eigenes Objekt, im Konstruktor am `EVENT_BUS` angemeldet, weil der Befehl auch bei
  ausgeschaltetem Modul funktioniert. `GameLeftEvent` löscht die Boxen, `.chest-index clear` auch.

**PLAN 57 – `cave-disturbance-detector` (clean-room, kein Trouser-Streak-Code angesehen):**

- Im Bytecode 26.2 geprüft, wo `cave_air` entsteht: `NetherWorldCarver.carveBlock` (oberhalb
  `minGenY + 31`), `MineshaftPieces`, `StrongholdPieces`, `MonsterRoomFeature`, `LakeFeature`.
  `WorldCarver.getCarveState` (Overworld-Höhlen und Schluchten) nimmt
  `Aquifer.computeSubstance`, das `Blocks.AIR` liefert; Noise-Höhlen sind ebenfalls `AIR`. Die
  Annahme „unter Y 0 ist natürlicher Hohlraum `cave_air`“ stimmt für Chunks ab 1.18 also nicht.
  Funktioniert: Nether, Mineshafts/Strongholds/Dungeons, Overworld-Chunks aus 1.13–1.17.
- `utils/CaveAirPockets` (+ Test, rein): Seeds = `AIR` mit `CAVE_AIR`-Nachbar im Suchquader;
  Flood-Fill (6er-Nachbarschaft, Zellliste als Queue). Gruppe > `maxSize` → verworfen (natürliche
  Höhle, Himmel über einer Schlucht). `seen` merkt die Fill-ID; trifft ein Fill auf Blöcke eines
  anderen, gehört er zu einer schon verworfenen großen Gruppe → ebenfalls verworfen (Aufwand
  linear). `UNKNOWN` (Nachbarchunk) → `resolved = false`.
- Modul: Netty-Thread, Chunk-Kopie aus `ChunkPacketDecoder`. Nur Chunks mit einer Section, deren
  Palette `CAVE_AIR` enthalten kann (`maybeHas`); Seed-Bereich diese Sections ±1, bis `max-y`.
  Offene Taschen am Chunkrand prüft der Client-Thread alle 20 Ticks in `mc.level` erneut
  (ungeladene Chunks = `UNKNOWN`), nach 400 Ticks verworfen. Pro Chunk eine `Detection`
  (max. 128 Blöcke); gemeldet ab `min-air-blocks` (Standard 2), Chat höchstens einmal pro Chunk
  und Level, nicht innerhalb von 48 Blöcken um einen schon gemeldeten. Entfernt außerhalb von
  `render-distance`, geleert bei Level-Wechsel/`GameLeftEvent`.
- Unsicher (in-game prüfen): Falschtreffer, wo Noodle-Höhlen (dünn, ab 1.18) einen Mineshaft
  kreuzen; fallender Kies; Creeper-Explosionen in Mineshafts (sind aber auch Spieleraktivität).
  Aufwand pro Chunk in alten 1.13–1.17-Gebieten (dort hat fast jede Section `cave_air`).

### AutoElytraRepair: Reparatur am Boden statt in der Luft, PLAN-Punkt 102 (`aeec164`)

`modules/AutoElytraRepair.java` neu aufgebaut. Zustände nur noch `MONITORING`, `LANDING`,
`REPAIRING`, `TAKING_OFF`, `EMERGENCY_DISCONNECT`. Scaffold, Schweben, Stopp-Warten,
Abbremsen und Positionieren sind weg (samt `readBool`/`writeBool` für Scaffold-Settings).

- `initiateRepairSequence`: XP-/Mending-Prüfung wie vorher, `ElytraController.pause()`. Am Boden
  direkt `startRepairing()`, sonst `borrowElytraFly()` (Vanilla, Autopilot aus, Auto-Takeoff an;
  idempotent, `restoreElytraFly()` setzt `elytraFlySettingsSaved` zurück) und `LANDING`.
- `LANDING`: jeden Tick `safeGroundY()` (Spalte unter dem Spieler bis `getMinY()`; erste Kollision
  = Boden; Flüssigkeit, `HARMFUL_GROUND` oder kein Boden → `NaN`). Sicher → Shift (ElytraFly sinkt
  0,5 × `vertical-speed` Blöcke/Tick), sonst Vorwärts-Taste (gleitet weiter, keine Höhe verloren).
  `deployElytra()` jeden Tick, falls die Elytra zu ist. `onGround()` → `REPAIRING`.
  Timeout 90 s → Emergency-Disconnect. Kritische Haltbarkeit: weiter sinken, wenn
  `Höhe / (10 × vertical-speed) < Haltbarkeit − 1` Sekunden, sonst Disconnect.
- **Fallschaden** (Bytecode 26.2 geprüft): `LivingEntity.updateFallFlying()` →
  `checkFallDistanceAccumulation()` setzt `fallDistance` auf 1, solange `getDeltaMovement().y > -0.5`.
  `ServerPlayer` überschreibt `getDeltaMovement` nicht, die Client-Bewegung landet nur in
  `setKnownMovement`; serverseitig bleibt der Wert ≈ 0, also Reset jeden Tick beim Gleiten. Die
  Sinkgeschwindigkeit ist deshalb egal, `vertical-speed` wird nicht verändert.
- `REPAIRING`: AutoEXP an (nur zurück aus, wenn die Reparatur es eingeschaltet hat). Fertig bei
  Schaden 0; bei 5 s ohne Fortschritt oder `repair-timeout`: über der Schwelle → Start, sonst
  `stayOnGround()` (Flug bleibt pausiert, Discord „Elytra Repair Failed - Paused“, kein Disconnect).
  Elytra abgelegt → ebenso.
- `startTakeoff`: AutoEXP sofort zurück (Flaschen in der Luft wären verloren). `keepFlightPaused` →
  bleibt am Boden. Baritone → `resumeFlight()` (Baritone startet per `elytraAutoJump`). Ohne
  Baritone `TAKING_OFF`: Sprungtaste halten (ElytraFly Vanilla steigt nur mit Sprung, der
  Controller-Pitch hebt nicht) bis `Config.flightAltitude`, Timeout 60 s, dann `resumeFlight()`.
- Tasten über `setHeld()` / `releaseKeys()` (KeyHold-Refcount, Set `heldKeys`); keine Timed-Holds
  mehr, daher kein `KeyHold.cancel(this)`. `mc.options.keyUp.setDown(false)` nach dem Abschalten des
  ElytraFly-Autopiloten bleibt (PLAN 4, Punkt 30).
- `AltitudeLossDetector` zählt den Sinkflug nicht (`isRepairing()`); bei 10 Blöcken/s lag er genau
  an der Standard-Schwelle 10, der Sprung-Fix hätte gegen die Landung gearbeitet.
- `cannotRepair` (keine XP/kein Mending) unverändert: Baritone-Landung, ohne Baritone seit 2026-11-02 die
  eigene Landung (`landOnly`): Sinkflug wie bei der Reparatur, am Boden pausiert statt Disconnect.

### Zuverlässigeres Stash-Hunting, PLAN-Punkte 97–100 (`7b42e85`)

- 97: `StashHunterModule.onBlockUpdate` kehrt bei `event.oldState == null` zurück. Meteors
  `LevelChunkMixin` postet `BlockUpdateEvent` am `TAIL` von `LevelChunk.setBlockState` mit dem
  Rückgabewert als `oldState`; der ist `null`, wenn der Zustand gleich bleibt oder Luft in eine
  reine Luft-Section kommt (Bytecode 26.2 geprüft). Orbit fängt nichts ab, die NPE wäre bis in
  `PacketUtils` gelaufen → `onPacketError` → Disconnect. Meteors eigenes BlockESP hat dieselbe
  Lücke. `ChunkDataEvent`/`BlockUpdateEvent` laufen auf dem Client-Thread (geprüft), kein Race.
- 98: `utils/MissedChunks` (+ Test). `scanForBlocks` prüft zuerst den Kreis, dann `hasChunk`;
  ungeladene Chunks kommen in `MissedChunks` (erster Fehlversuch zählt, Ablauf `RESCAN_TICKS`,
  max. 4096, älteste fliegen raus). Jeder Pass holt geladene über `takeLoaded` und scannt sie
  (`scanChunkIfDue`). Liegen sie außerhalb des Radius, läuft `findStashes` um ihre Mitte (Bereich
  32, Mitten < 32 Blöcke zusammengefasst), sonst die normale Suche um den Spieler. Abgelaufene/
  verdrängte zählt `takeDropped`; ab 16 und höchstens alle 12 000 Ticks eine Chat-Warnung mit
  `options.getEffectiveRenderDistance() * 16`. Geleert bei Aktivieren, `GameLeftEvent` und
  Dimensionswechsel (ohne Zählung). Hintergrund: Der Client friert in ungeladenen Chunks nicht
  ein (`LocalPlayer.tick` prüft nur `hasClientLoaded`), der Bot fliegt einfach weiter.
- 99: `StashClustering.tighten(cluster, link, radius, minSize, accept)` (+ Test). Abgelehnter
  Cluster → `cluster()` mit halbem Link-Abstand und Radius, rekursiv bis `MIN_LINK_DISTANCE` 4
  (Standard 30 → 15 → 7). `findStashes` nutzt es nur mit `filter-natural-structures`; `accept` ist
  `hasStashShape` (Volumen ≤ `max-volume-threshold`, Dichte ≥ `min-density-threshold`), das auch
  `isNaturalStructure` statt der eigenen Prüfungen nutzt. Mehrere Teile derselben Basis: der erste
  wird gemeldet, die anderen fängt `report-dedupe-radius` ab. Risiko: dichte Teile natürlicher
  Strukturen (z. B. Trial-Chamber-Räume) können jetzt gemeldet werden; dafür gibt es
  `structure-filter-radius`.
- 100: `ElytraController`: `hasUnloadedArea`, `EDGE_FOLLOWING`, `handleEdgeFollowing`,
  `switchToEdgeFollowing` entfernt; `edgeFollowStartTime`/`MAX_EDGE_FOLLOW_TIME` heißen jetzt
  `trailFollowStartTime`/`MAX_TRAIL_FOLLOW_TIME`. `ChunkBoundaryInfo.description` steht jetzt im
  Log von `switchToTrailFollowing`.
- VS-Code-Problems: `AtomicFilesTest` nutzt `try (FileInputStream _ = ...)` (Java 22+);
  `land-on-low-fireworks` hat kein `@SuppressWarnings("unused")` mehr (wird von `.visible(...)`
  gelesen, JDT meldete die Unterdrückung deshalb als nicht analysierbar).

### ChestIndex, PLAN-Punkt 53

Clean-room: nur gegen die MC-/Meteor-APIs geschrieben, jeff-mod-Code nicht angesehen.

- `utils/ChestIndex` (+ Test, rein): Records `Stack` (Eingabe), `Entry` (Dimension, `pos`, `pair`
  der Doppelkiste, Block-ID, Slots, ISO-Zeit, `items` pro ID summiert, `shulkers` mit Name und
  Inhalt). Beide Hälften einer Doppelkiste zeigen auf denselben Eintrag; `put` ersetzt, was an
  einer der Positionen lag. `search` (ID enthält Suchtext, Shulker-Inhalt zählt mit). JSON über
  `AtomicFiles`; kaputte Datei → `.corrupt`; nicht lesbare Datei → `readFailed`, dann kein Speichern
  (sonst würde ein Teil-Index die Datei ersetzen).
- `modules/ChestIndexer` (`chest-index`): `InteractBlockEvent` auf einen Container merkt die
  Position (`pendingPos`). Ausgelesen wird, sobald `player.containerMenu` ein `ChestMenu`/
  `ShulkerBoxMenu` mit `getStateId() != 0` ist. Im Bytecode geprüft: ein neues Menü hat stateId 0,
  der Server schickt den Inhalt mit `incrementStateId()` (`ServerPlayer$1.sendInitialData`), also 1.
  Container-Slots = alle Slots, deren `container` nicht das Spielerinventar ist.
- Auto-open: nächster Container in `isWithinBlockInteractionRange(pos, 0)`, nicht in `handled`
  (Sitzung) bzw. mit `skip-indexed` nicht im Index; Kisten mit `ChestBlock.isChestBlockedAt`
  übersprungen. Klick über `BlockUtils.interact` (lässt Shift los, schwingt), optional nach
  `Rotations.rotate`. Nach dem Auslesen `closeContainer()`, dann `delay` Ticks Pause. Timeout
  40 Ticks. Pausiert bei offenem Screen, Schleichen, Gleitflug, `ElytraController.isActive()`.
- Selbst geöffnete Container: alle 10 Ticks neu gelesen, beim Schließen ein letztes Mal aus dem
  alten Menü (der Client behält dessen Inhalt). Geschrieben wird nur bei geändertem Inhalt.
- Speichern: alle 60 s, bei `GameLeftEvent` und beim Ausschalten. `GameLeftEvent` kommt auch beim
  Schließen des Fensters aus einer Welt (`Minecraft.exitWorldAndClose` → `disconnect`).
- `utils/ServerKey` aus `StashHunterModule.serverKey()` herausgezogen (gleiche Dateinamen).
- In-game offen: siehe PLAN Abschnitt 1 (`chest-index`). Unsicher sind vor allem Anti-Cheat
  (Klickrate, Rotation) und Server, die das Kisten-GUI anders öffnen (Plugins).

### Xaero-Waypoints, PLAN-Punkt 54

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
