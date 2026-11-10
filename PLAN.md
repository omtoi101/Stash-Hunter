# Stash-Hunter — Plan

## Stand (2026-11-10)

- Branch `code-revision`. Version **26.2.2** ist release-fertig (CHANGELOG `[v26.2.2] - 2026-11-10`):
  101–103 plus **96** Trip-Save im Hintergrund, **53-Rest** für `.chest-index` komplett
  (Treffer-ESP, klickbare Koordinaten, Shulker-Namenssuche, `[+waypoint]`-Link, `open-after-walk`,
  Index-Save im Hintergrund), **55-Rest** benannte Trips (`.sh name`/`resume <name>`/`delete`,
  `.sh list` mit Fortschritt), **57** Modul `cave-disturbance-detector` (clean-room).
- `./gradlew clean build` mit JDK 25 ist grün: Compile, Spotless, **157 Unit-Tests in 28 Suites**,
  Jar `build/libs/stash-hunter-26.2.2.jar`. CI-Actions auf aktuelle Major-Versionen gehoben.
- Release (von Hand): Ziel-Branch `code-revision` (bzw. `main` nach dem Merge) wählen, nicht den
  alten `main`-Stand. Das Tag `v26.2.2-pre1` zeigt deshalb auf `3ec56f2` ohne den 26.2.2-Code.
  Beim Merge nach `main` gibt es einen Konflikt in den README-Credits (`3ec56f2`): die
  `code-revision`-Seite nehmen (`papxx` ohne Backslash, wie in `fabric.mod.json`).
- Abschnitt 0 ist komplett erledigt, alle angefangenen Punkte aus Abschnitt 3 auch.
- **Nichts davon ist in-game getestet.** Das ist der nächste Schritt (Checkliste in Abschnitt 1,
  enthält jetzt auch alle offenen Punkte aus den beiden Handoffs).
- Offen sind danach nur noch neue Feature-Ideen (Abschnitt 3).

Die letzten Änderungen beschreiben [CLAUDE-HANDOFF.md](CLAUDE-HANDOFF.md) (OldChunkNotifier,
Baritone-Cancel, Landungs-Timeout, Stashfinden, ActivatedSpawnerDetector) und
[DSH-HANDOFF.md](DSH-HANDOFF.md) (End-Detektor).

## Projekt-Kontext

- Meteor-Client-Addon (Fabric, client-only) für **MC 26.2**, Lizenz **Apache-2.0**.
- Loom 1.17.21, **Java 25**, offizielle Mojang-Mappings (MC seit 26.1 unobfuskiert, kein
  `mappings(...)`), Meteor `26.2-SNAPSHOT`, Baritone `26.2-SNAPSHOT` als Soft-Dependency, Gson.
  Keine Fabric API (nur `fabric-resource-loader-v1`, das Meteor bündelt).
- Quellen: `src/main/java/com/stashhunter/stashhunter/`
- Build: `./gradlew build` (Windows: `gradlew.bat build`). Braucht JDK 25 und Netzzugang zu Mojang
  und den Meteor-/Baritone-Snapshots. Im Schulnetz sind die Mojang-Hosts gesperrt.
- Zeilennummern in älteren Notizen sind veraltet. Stellen immer per Suche (Symbol/Logstring)
  finden.

---

## 0. Aus dem Code-Review

Review vom 2026-10-31 über die damals nicht committeten Änderungen und das Speichern/Laden.
Befunde und Umsetzung stehen in [CLAUDE-HANDOFF.md](CLAUDE-HANDOFF.md) („Punkte aus dem
Code-Review“, 1–8 = 89–96).

- [x] **89 Speichern unter Windows bei offener Datei** (2026-10-31): `AtomicFiles` wiederholt das
      Umbenennen bei jeder `FileSystemException` (5 × 30 ms) und überschreibt danach direkt.
      Nachgestellt: offene Zieldatei → `AccessDeniedException`, offene Temp-Datei →
      `FileSystemException` (Sharing Violation); in beiden Fällen klappt das direkte Schreiben.
      `Util.safeReplaceFile` nicht genommen (eigener Retry ist kleiner und ohne MC-Bootstrap testbar).
- [x] **90 Config-Save wird wiederholt**: nach einem Fehler bleibt `dirty` gesetzt, neuer Versuch
      nach 30 s, spätestens beim Beenden.
- [x] **91 Angehängte Dateien**: `AppendFiles.append()` ergänzt ein fehlendes Zeilenende
      (`ReportedStashes`, NewChunks-Dateien).
- [x] **92 `clearGroundGlide()`** läuft nur noch bei aktivem Flug oder laufender Landung. In-game
      noch prüfen (Abschnitt 1, Flug).
- [x] **93 Verwaiste `.tmp`-Dateien**: `AtomicFiles.deleteLeftovers()` beim Laden von Config und
      Trips.
- [x] **94 `stacked-minecarts`**: gemeldete Stapel werden über den Abstand zum Anker erkannt
      (`Stack.isNear`), nicht über die Blockposition.
- [x] **95 Leere Trips**: `loadTrips()` verwirft Trips ohne Waypoints.
- [x] **96 Trip-Save mit `fsync` auf dem Client-Thread** (2026-11-09): `TripManager` hält die Trips
      im Speicher (Datei einmal gelesen) und schreibt über `utils/BackgroundSaver` (+ Test) auf
      einem eigenen Thread. `saveCurrentTripAsync` kopiert die Waypoints auf dem Client-Thread
      (unveränderliche Liste), Reihenfolge bleibt, mehrere Saves während eines Schreibvorgangs
      ergeben nur einen weiteren. Der Shutdown-Hook wartet bis 2 s auf den letzten Save.

Zuverlässigeres Stash-Hunting (2026-10-31, Details in [CLAUDE-HANDOFF.md](CLAUDE-HANDOFF.md)):

- [x] **97 NPE in `onBlockUpdate`**: `BlockUpdateEvent.oldState` ist `null`, wenn sich der Block
      nicht ändert (`LevelChunk.setBlockState` gibt dann `null` zurück, im Bytecode geprüft). Die
      Exception hätte das Paket-Handling beendet → Disconnect „Network Protocol Error“. Ignoriert.
- [x] **98 Spät geladene Chunks**: Chunks im Scan-Radius, die noch nicht geladen sind, merkt sich
      `utils/MissedChunks` (+ Test, 60 s, max. 4096) und scannt sie beim Eintreffen; Cluster um weit
      zurückliegende Chunks werden separat geprüft. Nie geladene Chunks → Chat-Warnung (höchstens
      alle 10 min, mit Server-View-Distance).
- [x] **99 Zu große/dünne Cluster werden aufgeteilt statt verworfen**: `StashClustering.tighten`
      (+ Test) halbiert Link-Abstand und Radius bis 4 Blöcke; jeder Teil mit genug Blöcken, der
      Volumen und Dichte erfüllt, wird gemeldet (große Basis mit Ausreißern, Stash in/neben
      Ancient City usw.).
- [x] **100 Kein Edge-Following bei ungeladenen Chunks**: wich quer zum Streifen aus und übersprang
      nach 30 s den Waypoint (fast ein ganzer Streifen ungescannt). Der Bot bleibt jetzt auf dem
      Streifen, 98 holt die Chunks nach. Trail-Following (neue Chunks) bleibt.

Spiral-Suche (2026-10-31):

- [x] **101 `.stashhunter spiral` überarbeitet**: `SpiralWaypoints` nimmt nur so viele Ringe, bis
      der Scan des letzten (halber `gap` nach außen) den Radius erreicht. Vorher `ceil(radius/gap)`,
      der letzte Ring lag oft weniger als einen halben `gap` vor dem vorigen (Radius 3000, `gap` 128:
      24 km Umweg). Default-`gap` = 2 × `scan-radius` (50–500, bei 64 weiter 128), Warnung bei
      größerem `gap`. Neu `utils/WaypointPath` (+ Test): Pfadlänge im Start-Text, Fortschritt in
      % und km in `.sh status` (Waypoint-Zähler läuft bei der Spirale weit voraus).
      `ElytraController.start(List<Vec3>)` ersetzt `startSpiral`, `SpiralWaypoints.count` entfällt.

Elytra-Reparatur (2026-10-31, Details in [CLAUDE-HANDOFF.md](CLAUDE-HANDOFF.md)):

- [x] **102 `auto-elytra-repair` repariert nur noch am Boden**: Flug pausieren, senkrecht mit
      ElytraFly (Vanilla + Shift) sinken, über Flüssigkeit/Gefahrenblöcken/Void geradeaus weiter,
      am Boden AutoEXP, dann Start (Baritone selbst, sonst Steigen mit Sprungtaste bis
      `flight-altitude`). Scaffold entfällt. Stockt die Reparatur am Boden, bleibt der Flug dort
      pausiert statt Disconnect. Kein Fallschaden beim Sinken (Bytecode geprüft).
      `AltitudeLossDetector` ignoriert den Sinkflug.
- [x] **103 Reparatur unmöglich ohne Baritone** (2026-11-02): keine XP/kein Mending → eigener
      Sinkflug wie bei 102, am Boden bleibt der Flug pausiert (vorher Disconnect). Gebaut und
      getestet am 2026-11-09 (grün, 131 Tests), in-game offen.

## 1. In-game-Test (als Nächstes)

Grundfunktionen:
- [ ] Modul an/aus, Tod/Respawn, Dimensionswechsel, `.stashhunter start` + `stop`
- [ ] Stuck-Auto-Fix
- [ ] Elytra-Reparatur am Boden (102), mit und ohne Baritone: Sinkflug ohne Fallschaden, Reparatur
      am Boden, Start und Weiterflug; über Wasser/Lava/Void wird weitergeglitten; XP leer → Flug
      bleibt am Boden pausiert; ohne XP/Mending landet er, mit Baritone über Baritone, ohne
      Baritone selbst (103). ElytraFly danach wieder im alten Modus/aus; AltitudeLossDetector,
      AutoFirework und StuckDetector bleiben während des Sinkflugs still
- [ ] Während einer Reparatur: `.sh stop` → Landung und Reparatur laufen zu Ende, Flug bleibt aus;
      Kick mitten in der Reparatur → nach Rejoin kein Weiterreparieren, ElytraFly/AutoEXP im alten
      Zustand; Tod → „Repair aborted“, kein Weiterflug vom Spawn; eigenes AutoEXP bleibt an
- [ ] Ersatz-Elytra: zweite Elytra im Inventar → „Swapped to a spare elytra“, Flug läuft weiter;
      ohne Ersatz startet die Reparatur
- [ ] Raketen knapp (Baritone-Flug, ≤ `low-firework-threshold`): Chat + Discord „Fireworks low“,
      Trip gespeichert, Baritone landet ~64 Blöcke voraus; nach Auffüllen setzt `.sh resume` fort
- [ ] Baritone landet mitten im Scan (Raketen leer, `land-on-low-fireworks` aus): keine
      Elytra-Pose am Boden; mit Raketen Neustart nach ≤ 5 s, ohne bzw. nach 3 Fehlstarts
      „Flight Paused After Landing“ und `.sh resume` geht
- [ ] `.sh altitude <y>` während eines Flugs: Baritone plant auf neuer Höhe neu, GUI-Wert
      `flight-altitude` zieht mit, `.sh resume` nutzt die aktuelle Höhe

Scan und Stashes:
- [ ] `scan-by-distance` (CPU-Last beim Flug), `scan-only-while-flying`
- [ ] Scan-Cache: Flug mit `scan-radius` 256 findet weiter Stashes, mit `spark` weniger Tick-Zeit;
      Kiste in einem schon gescannten Chunk setzen → nächster Scan meldet sie
- [ ] Stash auf einer Streifengrenze wird gemeldet; ein zuerst halb gesehener Stash bekommt
      „Stash Updated“ und einen verschobenen Waypoint
- [ ] `reported-stashes`-Datei hält nach Reconnect (keine doppelten Meldungen)
- [ ] Spät geladene Chunks (98): schneller Flug auf einem Server mit wenig Chunk-Rate; Stash am
      Streifenrand wird trotzdem gemeldet. Mit `scan-radius` über der Server-View-Distance kommt
      die Warnung „chunks inside scan-radius never loaded“ (höchstens alle 10 min)
- [ ] Aufteilen (99): große Basis (Lagerraum + einzelne Kisten weit weg) wird gemeldet; Ancient
      City / Village / Stronghold bleiben weiter still; ein Stash neben einer Ancient City wird
      gemeldet
- [ ] Redstone-/Kolben-Basis überfliegen mit `stash-hunter` an: kein „Network Protocol Error“ (97)
- [ ] `include-spawners`, `structure-filter-radius`, `structure-waypoints`
- [ ] Stash-ESP (Outlines, Boxen, Tracer)
- [ ] `chest-index` (zuerst Singleplayer, dann Server): `auto-open` öffnet Kisten/Fässer/Shulker
      in Reichweite nacheinander und schließt sie; Doppelkiste ergibt einen Eintrag; Kiste unter
      einem Block wird übersprungen; nicht beim Schleichen, mit offenem Chat oder im Flug; selbst
      geöffnete Kiste bleibt offen, Herausnehmen landet im Index; JSON in
      `meteor-client/StashHunter/chest-index/` nach Verlassen der Welt aktuell;
      `.chest-index search diamond` zählt Shulker-Inhalte mit; kein Anti-Cheat-Kick bei `delay` 10
- [ ] `.chest-index search diamond`: Treffer leuchten `highlight-seconds` lang (auch bei
      ausgeschaltetem Modul, nur in der passenden Dimension); Klick auf die grünen Koordinaten →
      Baritone läuft neben die Kiste (baut sie nicht ab); ohne Baritone/andere Dimension
      (türkis) kopiert der Klick die Koordinaten; während `.sh start` verweigert. `.chest-index
      shulker kit` findet benannte Shulker
- [ ] `[+waypoint]` hinter einem Treffer: Meteor-Waypoint „64x diamond (chest-index)“ in der
      richtigen Dimension, zweiter Klick legt keinen zweiten an; Klick nach Serverwechsel →
      „from another server“
- [ ] `open-after-walk` an: Klick auf grüne Koordinaten → Baritone läuft hin, nach dem Schließen
      des Chats öffnet sich die Kiste und bleibt offen (Index wird aktualisiert); `#stop` unterwegs
      → „Baritone stopped before reaching …“, nichts öffnet sich; Auto-Open öffnet unterwegs
      nichts dazwischen; mit ausgeschaltetem Modul öffnet sich die Kiste trotzdem
- [ ] Index-Save im Hintergrund: großer Index (Stash mit Hunderten Kisten), kein Ruckler im
      Minutentakt; Welt verlassen bzw. Spiel aus der Welt heraus schließen → JSON aktuell;
      `.chest-index clear` direkt nach einem Save → Datei bleibt gelöscht
- [ ] `save-to-xaero` mit Xaero's Minimap 26.2: Stash bekommt einen goldenen `S`-Waypoint im
      aktuellen Set, er übersteht einen Neustart, die World Map zeigt ihn nach dem Öffnen;
      „Stash Updated“ verschiebt ihn; ohne Xaero kein Fehler im Log

Chunks:
- [ ] NewerNewChunks markiert Overworld/Nether weiter korrekt (nach Umbau 48)
- [ ] PaletteExploit (neu über die Palette-Reihenfolge): frisch generierte Chunks werden in allen drei
      Dimensionen „neu“, schon besuchte „alt“; Nether/End sind nicht mehr komplett alt. Auf einem
      Paper-Server mit Anti-Xray gegenprüfen.
- [ ] End-Detektor: Hauptinsel nie alt, frische äußere Inseln nicht alt, äußere `the_end`-Inseln alt
- [ ] `old-chunk-notifier`: erste Meldung nach Dimensionswechsel kommt, kein Portal-Spam, im End
      keine Falschtreffer mehr
- [ ] NewerNewChunks und `activated-spawner-detector` gleichzeitig an: beide erkennen weiter
      (neue/alte Chunks, aktivierte Spawner/Trial-Spawner), keine doppelt tickenden Spawner/Truhen
- [ ] NewerNewChunks mit großen Datendateien: Aktivieren lädt einmal (kein doppelter Hänger),
      Rendern bleibt flüssig
- [ ] NewChunks-Daten wurden in den Meteor-Ordner umgezogen
- [ ] `activated-spawner-detector`: in Kategorie „Stash-Hunter“, an/aus; Dungeon/Mineshaft/
      Trial-Chamber mit aktiviertem Spawner werden gemeldet und gerendert
- [ ] `structure-waypoints`: großer Mineshaft bekommt nur einen Waypoint, zwei nahe Dungeons zwei
- [ ] Slime-Chunk-Overlay
- [ ] `cave-disturbance-detector`: in einem Mineshaft ein paar Spinnweben/Schienen abbauen,
      neu einloggen bzw. Chunks neu laden → Meldung + orange Boxen. Nether: Gang in eine
      Carver-Höhle graben → Meldung. Unberührte Mineshafts, die eine Noise-Höhle kreuzen, und
      Overworld-Höhlen ab 1.18 bleiben still (Falschtreffer zählen, ggf. `max-pocket-size`/
      `min-air-blocks` anpassen). Kein Ruckeln beim schnellen Flug über alte 1.13–1.17-Chunks

Flug:
- [ ] Mit NewerNewChunks an und langsamem Server: kein Ausweichen mehr bei ungeladenen Chunks
      voraus, kein übersprungener Waypoint (100); Trail-Following bei neuen Chunks geht weiter
      (`dynamic-trail-detection`, mit Baritone neuer Pfad nur alle ~25 Blöcke)
- [ ] `.stashhunter stop` / Dimensionswechsel / AltitudeLoss-Recovery beendet den Baritone-
      Elytra-Flug wirklich (kein Re-Path)
- [ ] `land-after-completion` mit Baritone; festsitzende Landung bricht nach 2 min ab („Landing
      timed out“), danach arbeiten AltitudeLossDetector und AutoFirework wieder
- [ ] `auto-firework` (feuert nicht während Baritone-Flug und nicht während einer Elytra-Reparatur,
      ignoriert Sternraketen)
- [ ] `.stashhunter spiral` inkl. pause/resume; Start-Text mit Flugstrecke, `status` mit Fortschritt
      in % / km (101), Warnung bei `gap` > 2 × `scan-radius`
- [ ] AltitudeLossDetector-Discord
- [ ] ElytraFly `Bounce` manuell mit eingeschaltetem `stash-hunter`, ohne `.sh start`: kein
      Ausbremsen am Boden (92). Nach einer Baritone-Landung trotzdem keine Elytra-Pose am Boden.

Sonstige Module:
- [ ] `online-player-activity` (Discord aus/an sendet keine alten Änderungen nach),
      `player-alarms` (`action-bar`, kein Flattern am Radiusrand)
- [ ] `auto-logout` (vorsichtig, nur auf einem Testserver!)
- [ ] `stacked-minecarts`: gestapelte Chest-Minecarts werden einmal gemeldet und gerendert,
      `storage-only` ignoriert normale Minecarts, nach Dimensionswechsel wird neu gemeldet
- [ ] Config-Änderung kurz vor dem Beenden bleibt erhalten
- [ ] Trips (96): Pausieren/Landen/Reparatur ohne Ruckler; `stashhunter_trips.json` ist danach
      aktuell; `.sh pause` und sofort das Spiel schließen → `.sh resume` nach dem Neustart geht;
      Pause → Resume → Pause ergibt einen Eintrag, `.sh list` zeigt höchstens 20 unbenannte
- [ ] Benannte Trips (55-Rest): `.sh name nord` während des Flugs, Pause, `.sh resume nord`
      (auch per Tab-Vervollständigung); Name bleibt nach erneuter Pause; mehr als 20 neue Trips
      → „nord“ bleibt; `.sh list` zeigt Name, Waypoint, %, km, Zeit; `.sh delete nord`; zu Ende
      geflogener Trip steht als „complete“ und `.sh resume` verweigert ihn
- [ ] Config bei geöffneter `stash-hunter.properties` (z. B. im Editor) ändern: Änderung landet
      in der Datei, keine `.tmp`-Reste in `meteor-client/`

## 2. Bekannte Kleinigkeiten

Alle erledigt (2026-10-31, Details im CHANGELOG). `unfocused-fps-limiter` ist entfernt (siehe 4).
Was offen bleibt:

- PaletteExploit: Die Erkennung „wird gerade von ≤1.17 aktualisiert“ ist weiter die alte
  Stichproben-Heuristik. Neu/alt kommt jetzt aus der Palette-Reihenfolge (`PaletteOrder`).
- Alte Falschtreffer in den NewChunks-Dateien (z. B. „alles alt“ im Nether/End) bleiben stehen,
  bis sie mit „DELETE CHUNK DATA“ entfernt werden.

## 3. Offene Features

### Lizenzregel (gilt für alle Punkte)

Stash-Hunter ist Apache-2.0. Code darf nur aus kompatiblen Quellen übernommen werden:

| Quelle | Lizenz | Regel |
|---|---|---|
| [PathSeeker](https://github.com/FaxHack/PathSeeker) | Apache-2.0 ✅ | Direkt portierbar mit Attribution, **aber nur** Module, die PathSeeker selbst geschrieben hat (nicht die „Ported From Jeff-Mod/TrouserStreak/Stardust“-Module) |
| [jeff mod](https://github.com/miles352/meteor-stashhunting-addon) | keine ⛔ | Nur clean-room (oder Autor `0x658` auf Discord fragen) |
| [Trouser-Streak](https://github.com/etianl/Trouser-Streak) | keine ⛔ | Nur clean-room |
| [Stardust](https://github.com/0xTas/stardust), [BepHax](https://github.com/dekrom/BepHaxAddon) | GPL-3.0 ⚠️ | Nur die Idee, kein Code |

Neue Module/Settings sind opt-in bzw. standardmäßig aus.

### Welt-Seed (44, 45, 47)

Im Multiplayer ist der Seed client-seitig nicht verfügbar. Im Singleplayer über
`getSingleplayerServer().overworld().getSeed()`. Das Setting `world-seed` existiert schon
(Slime-Chunks, 46) und kann als Basis dienen.

- **44 Struktur-Locator mit `world-seed`** (mittel, hoher Nutzen): Strukturen aus dem Seed
  vorausberechnen und als Overlay/Waypoints zeigen. Macht `filterNaturalStructures` exakt und
  liefert Flugziele.
- **45 Seed-Cracking** (hoch): Seed aus beobachteten Daten zurückrechnen (wie SeedCrackerX),
  z. B. mit `KaptainWutax/Seedfinding` (Lizenz prüfen). Nur im eigenen Thread, Fortschritt im HUD,
  opt-in und klar gekennzeichnet (auf öffentlichen Servern meist unerwünscht).
- **47 Erz-/Biom-Vorhersage** (baut auf 44 auf): Scan-Ziele priorisieren, uninteressante Biome
  überspringen.

### Port auf MC 26.3/26.4

Wegen der `26.2`-Pins (`minecraft_version`, `meteor-client: ~26.2-`, Meteor/Baritone
`26.2-SNAPSHOT`) lädt das Mod nicht auf neueren Versionen. Meteor gibt es schon als
`26.3-SNAPSHOT`. Bei Bedarf die Pins anheben, `fabric-resource-loader-v1` (hart gepinnt) prüfen,
`xaero_minimap_version` (gibt es als `fabric-26.3-…`) mitziehen, bauen und testen.

### Stash-Auswertung

- **63 StorageLooter:** Container automatisch looten und sortieren. PathSeeker ✅. Hinlaufen und
  Öffnen gibt es schon (`open-after-walk`, 53).
- **73 ChestTracker mit GUI:** live gepflegtes Container-Verzeichnis (erweitert 53). BepHax ⚠️.
- **74 ShulkerOverview:** Shulker-Inhalt im Tooltip, ohne Platzieren. BepHax ⚠️.

### Erkennung

- **56 PortalPatternFinder:** entfernte/kaputte Nether-Portale als Base-Signal. Trouser-Streak ⛔.
- **59 Base Finder:** Base-Erkennung über Spieleraktivität. PathSeeker ✅.
- **62 ESP-Suite:** PotESP, MobGearESP, DroppedItemESP, EntityClusterESP, Hole/Tunnel/Stair-ESP.
  Nur die von PathSeeker selbst geschriebenen ✅ übernehmen, Rest clean-room.

### Flug und Komfort

- **61 ElytraFlyPlusPlus:** Bounce, Hindernisse auf Highways. jeff mod ⛔. Y-Boost/Fake-Fly sind
  auf 2b2t gepatcht.
- **64 AutoPortal / ElytraSwap / GrimEfly:** AutoPortal/ElytraSwap PathSeeker (`xqyet`) ✅,
  GrimEfly jeff mod ⛔.
- **65 SignHistorian:** Schilder aufzeichnen. Stardust ⚠️.
- **66 Pitch40Util / GotoPosition / HighlightOldLava / AFKVanillaFly.** jeff mod ⛔.

---

## 4. Bewusst nicht gemacht (nicht erneut vorschlagen)

- **76 `unfocused-fps-limiter`:** wieder entfernt (2026-10-31). Wirkte in Menüs außerhalb einer Welt
  nicht, weil `FramerateLimitTracker` dort fest 60 FPS zurückgibt; ändern ginge nur per Mixin.

- **35 `bin/` löschen:** Build-Output der VS-Code-Java-Erweiterung, entsteht sofort neu, ist
  ignoriert.
- **36 `reportedStashes` threadsicher machen:** kein Problem, wird nur auf dem Client-Thread benutzt.
- **37 Dependency Locking/Verification:** pinnt bei SNAPSHOTs nur den Namen, nicht den Build;
  Verification würde bei jedem Meteor-Build brechen. Erst mit festen Releases sinnvoll.
- **43 `modImplementation`:** verlangt bei unobfuskiertem MC weiter Mappings; das offizielle
  meteor-addon-template 26.2 nutzt auch `implementation`.
- **78 Dimension-Vergleich mit `==`:** Fehlalarm, Meteors `Dimension` ist ein Enum.
- **79 Mineshaft-Heuristik:** sinnlos, Mineshaft-Loot liegt in Chest-Minecarts (Entities). Der tote
  Mansion-Zweig ist entfernt, Struktur-Erkennung läuft über 67/44.
- **30 Rest:** `keyUp.setDown(false)` nach dem Abschalten des ElytraFly-Autopiloten bleibt in
  AutoElytraRepair (Zusammenspiel mit dem Autopiloten, nur in-game sicher änderbar). Eigene
  Tasten (Shift, Vorwärts, Sprung) hält die Reparatur seit 102 über `KeyHold`.
- **28 Rest:** Detektoren in NewerNewChunks und die Zustandsautomaten in AutoElytraRepair/
  ElytraController sind weiter groß. Umbau erst nach In-game-Tests.
- **48 anders gelöst:** Die Chunk-Klassifizierung bleibt auf dem Netty-Thread (arbeitet auf einer
  privaten Chunk-Kopie, auf dem Client-Thread gäbe es nur mehr Ruckler), ist dafür deutlich
  schneller.

## 5. Erledigt (Kurzübersicht, Details im CHANGELOG)

- **Phase 0 Quick Wins (1–6):** tote Mixins/Events entfernt, CI auf Java 25, `Config.validate()`
  aktiv, Template-Reste und Doku-Drift bereinigt.
- **Phase 1 Stabilität (7–11):** Teardown bei Tod/Weltwechsel (Trip fortsetzbar), Elytra-Reparatur
  abgesichert, Baritone/Controller-Konflikt gelöst, EMERGENCY_DISCONNECT repariert.
- **Phase 2 Threading/Performance (12–16):** Ergebnisse per `mc.execute` auf den Client-Thread,
  kein async+join mehr, Writes gebündelt (Flush alle 100 Ticks), Render-/Scan-Allokationen reduziert.
- **Phase 3 Scan-Korrektheit (17–21):** keine Doppelzählung, Zylinder über volle Welthöhe statt
  Kugel (Stashes am Boden wurden beim Flug auf Y=160 nie gefunden), Clustering mit Größen-Cap,
  kein erneutes Melden alle 10 min. Settings `max-cluster-size`, `report-dedupe-radius`.
- **Phase 4 Robustheit (22–27):** Discord-Webhook mit Retry/Backoff, begrenzter Queue und opt-in
  `mention-everyone`; Config-Saves debounced; AltitudeLossDetector ohne Fehlauslösung; robuste
  Persistenz (kaputte Trip-JSON → `.corrupt`); Command-Start abgesichert.
- **Phase 5 Architektur (28–32):** Logik in getestete Klassen ausgelagert (StashClustering,
  GridWaypoints, TrailAnalysis, ChunkDataFormat), statischer Modul-Lookup und kaputte Reflection
  entfernt, Logging vereinheitlicht, Spotless + JUnit.
- **33** `Logger.java` entfernt (alles über `StashHunter.LOG`). **34** echtes Icon.
  **38** `meteor-client: ~26.2-` (`>=26.2` hätte jede Meteor-Pre-Release abgelehnt).
  **39** Loader 0.20.1. **40/42** Fabric API komplett entfernt. **41** Loom 1.17.21 (1.18.4 braucht
  Gradle auf JDK 25, 1.19.3 zusätzlich Gradle ≥ 9.8).
- **46** Slime-Chunk-Overlay (`slime-chunks`, Setting `world-seed`). **49** LCG inline.
  **50** `Math.pow`/`sqrt` im Flug-Tick entfernt. **51** Shutdown-Hook für Config und Discord.
  **52** hinfällig durch den Biome-basierten End-Detektor.
- **60** `old-chunk-notifier` inkl. Reset bei Dimensionswechsel. **67** `activated-spawner-detector`
  (aus PathSeeker portiert, Attribution im Klassenkopf; Positionen fließen in
  `filterNaturalStructures` und Waypoints). **68** `auto-firework`. **69** `.stashhunter spiral`.
  **70** `online-player-activity`. **71** `player-alarms`. **72** Setting `include-spawners`.
  **75** `auto-logout`. **58** `stacked-minecarts` (aus PathSeeker portiert, Attribution im
  Klassenkopf).
- **77** NewChunks-Daten unter `MeteorClient.FOLDER` (alte Daten werden einmalig verschoben).
  **80** letzter Grid-Streifen immer auf `maxX`. **81** `ReportedStashes` persistent pro Server.
  **82** Scan zusätzlich nach Flugstrecke (`scan-by-distance`). **83** Settings-Persistenz und
  Grenzen vereinheitlicht. **84** AltitudeLossDetector-Discord (opt-in). **85** Player-Radius/
  -Cooldown als Settings. **86** `land-after-completion` (opt-in) mit 2-min-Timeout.
  **87** `scan-only-while-flying`. **88** CI/Kosmetik.
- **Kleinigkeiten (Abschnitt 2):** AutoFirework pausiert während der Elytra-Reparatur; Config-Save
  beim Beenden nicht mehr doppelt; `structure-waypoints` mit Radius pro Strukturtyp und Verkettung,
  `NaturalStructures` verdrängt nach Nutzung; OnlinePlayerActivity verwirft Änderungen bei Discord
  aus; PlayerAlarms mit Hysterese, monotoner Uhr und `action-bar` statt `title`; Slime-Seed im
  Singleplayer gecacht; NewerNewChunks markiert Nether/End nicht mehr komplett alt, PaletteExploit
  liest die echte Palette-Reihenfolge (`PaletteOrder`).
- **Außerhalb des Plans:** End-Detektor über Biome, Baritone-Cancel-Fix, Stashes über Streifengrenzen
  (`StashBlockIndex`) und wachsende Stashes (`StashGrowth`), Stash-ESP, verbesserter Takeoff/Flug
  mit Wiederherstellen der Flug-Settings beim Stop. Config und Trips werden über eine Temp-Datei
  gespeichert (`AtomicFiles`), ein Absturz beim Schreiben lässt die alte Datei stehen.
- **89–95** (Code-Review, 2026-10-31): Speichern bei offener Datei unter Windows, Config-Save-Retry,
  Zeilenende vor dem Anhängen, `clearGroundGlide` nur im Flug, `.tmp`-Aufräumen,
  `stacked-minecarts` ohne Doppelmeldung, leere Trips verworfen.
- **97–100** (2026-10-31): NPE bei Block-Updates ohne Änderung, spät geladene Chunks werden
  nachgescannt, zu große/dünne Cluster aufgeteilt statt verworfen, kein Edge-Following mehr bei
  ungeladenen Chunks.
- **53** ChestIndex (2026-10-31, clean-room): Modul `chest-index` + Befehl `.chest-index`
  (`search`, `clear`), Logik in `utils/ChestIndex` (+ Test), JSON pro Server unter
  `StashHunter/chest-index/`. Server-Schlüssel nach `utils/ServerKey` ausgelagert.
- **96, 53-Rest, 57** (2026-11-09): Trip-Save im Hintergrund (`BackgroundSaver`); `.chest-index`
  mit Treffer-ESP, klickbaren Koordinaten (`RunnableClickEvent` → `BaritoneBridge.startGetToBlock`,
  `GoalGetToBlock`) und `shulker <name>`; `cave-disturbance-detector` (clean-room, Logik in
  `utils/CaveAirPockets` + Test). Im Bytecode 26.2 geprüft: `cave_air` erzeugen nur der
  Nether-Carver (ab Y 32), Mineshaft-/Stronghold-Teile, Dungeons und Seen. Overworld-Carver und
  Noise-Höhlen holen ihren Block vom Aquifer (`Blocks.AIR`), also findet das Modul in Overworld-
  Höhlen ab 1.18 nichts; in Chunks aus 1.13–1.17 schon. Details in
  [CLAUDE-HANDOFF.md](CLAUDE-HANDOFF.md).
- **53-Rest, 55-Rest** (2026-11-09): `.chest-index`-Treffer mit `[+waypoint]` (Meteor-Waypoint,
  ohne Duplikat, nur auf dem Server der Suche); `open-after-walk` (opt-in) öffnet die Kiste, sobald
  Baritone angekommen ist (`BaritoneBridge.isGoalActive`, im Bytecode geprüft); `ChestIndex`
  speichert über einen eigenen `BackgroundSaver` (`close()` beim Verlassen, Retry nach Fehler).
  Benannte Trips: `TripData.name`, `.sh name`/`resume <name>`/`delete`, benannte Trips fallen nicht
  unter das 20er-Limit, `.sh list` mit Fortschritt, fertige Flüge werden als „complete“ gespeichert.
  Clean-room (jeff mod nicht angesehen).
- **54** Xaero-Waypoints (2026-10-31): Setting `save-to-xaero` (opt-in). `xaero/XaeroBridge` +
  `XaeroImpl` analog zu Baritone; `compileOnly` gegen `maven.modrinth:xaeros-minimap:fabric-26.2-26.6.2`
  (Modrinth-Maven, `xaero_minimap_version` in `gradle.properties`), `suggests` in `fabric.mod.json`.
  Weg wie Xaeros eigene Todespunkte: `BuiltInHudModules.MINIMAP.getCurrentSession()` →
  `getWorldManager().getCurrentWorld()` → `getCurrentWaypointSet().add(...)` →
  `getWorldManagerIO().saveWorld(world)`. Das im Plan genannte
  `SupportMods.xaeroMinimap.requestWaypointsRefresh()` ist World-Map-Code und unnötig: `GuiMap.init()`
  ruft es beim Öffnen der Karte selbst auf.
