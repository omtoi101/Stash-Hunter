# Stash-Hunter — Plan

## Stand (2026-10-31)

- Branch `code-revision`, zuletzt `d2f5f57` (Review-Punkte 89–95 und 54 Xaero-Waypoints). Noch
  nicht committet: **53 ChestIndex** (`chest-index`).
- `./gradlew build` mit JDK 25 ist grün: Compile, Spotless, **116 Unit-Tests in 24 Suites**.
- Von Abschnitt 0 ist nur 96 offen (nur beobachten).
- **Nichts davon ist in-game getestet.** Das ist der nächste Schritt (Checkliste in Abschnitt 1).
- Offen sind danach nur noch Feature-Ideen (Abschnitt 3).

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
- [ ] **96 Trip-Save mit `fsync` auf dem Client-Thread** (niedrig, beobachten): nur angehen, wenn
      es beim Pausieren/Landen spürbar ruckelt.

## 1. In-game-Test (als Nächstes)

Grundfunktionen:
- [ ] Modul an/aus, Tod/Respawn, Dimensionswechsel, `.stashhunter start` + `stop`
- [ ] Stuck-Auto-Fix, Elytra-Reparatur

Scan und Stashes:
- [ ] `scan-by-distance` (CPU-Last beim Flug), `scan-only-while-flying`
- [ ] Stash auf einer Streifengrenze wird gemeldet; ein zuerst halb gesehener Stash bekommt
      „Stash Updated“ und einen verschobenen Waypoint
- [ ] `reported-stashes`-Datei hält nach Reconnect (keine doppelten Meldungen)
- [ ] `include-spawners`, `structure-filter-radius`, `structure-waypoints`
- [ ] Stash-ESP (Outlines, Boxen, Tracer)
- [ ] `chest-index` (zuerst Singleplayer, dann Server): `auto-open` öffnet Kisten/Fässer/Shulker
      in Reichweite nacheinander und schließt sie; Doppelkiste ergibt einen Eintrag; Kiste unter
      einem Block wird übersprungen; nicht beim Schleichen, mit offenem Chat oder im Flug; selbst
      geöffnete Kiste bleibt offen, Herausnehmen landet im Index; JSON in
      `meteor-client/StashHunter/chest-index/` nach Verlassen der Welt aktuell;
      `.chest-index search diamond` zählt Shulker-Inhalte mit; kein Anti-Cheat-Kick bei `delay` 10
- [ ] `save-to-xaero` mit Xaero's Minimap 26.2: Stash bekommt einen goldenen `S`-Waypoint im
      aktuellen Set, er übersteht einen Neustart, die World Map zeigt ihn nach dem Öffnen;
      „Stash Updated“ verschiebt ihn; ohne Xaero kein Fehler im Log

Chunks:
- [ ] NewerNewChunks markiert Overworld/Nether weiter korrekt (nach Umbau 48)
- [ ] PaletteExploit (neu über die Palette-Reihenfolge): frisch generierte Chunks werden in allen drei
      Dimensionen „neu“, schon besuchte „alt“; Nether/End sind nicht mehr komplett alt. Auf einem
      Paper-Server mit Anti-Xray gegenprüfen.
- [ ] End-Detektor: Hauptinsel nie alt, frische äußere Inseln nicht alt, äußere `the_end`-Inseln alt
- [ ] `old-chunk-notifier`: erste Meldung nach Dimensionswechsel kommt, kein Portal-Spam
- [ ] NewChunks-Daten wurden in den Meteor-Ordner umgezogen
- [ ] `activated-spawner-detector`: in Kategorie „Stash-Hunter“, an/aus; Dungeon/Mineshaft/
      Trial-Chamber mit aktiviertem Spawner werden gemeldet und gerendert
- [ ] `structure-waypoints`: großer Mineshaft bekommt nur einen Waypoint, zwei nahe Dungeons zwei
- [ ] Slime-Chunk-Overlay

Flug:
- [ ] `.stashhunter stop` / Dimensionswechsel / AltitudeLoss-Recovery beendet den Baritone-
      Elytra-Flug wirklich (kein Re-Path)
- [ ] `land-after-completion` mit Baritone; festsitzende Landung bricht nach 2 min ab
- [ ] `auto-firework` (feuert nicht während Baritone-Flug und nicht während einer Elytra-Reparatur,
      ignoriert Sternraketen)
- [ ] `.stashhunter spiral` inkl. pause/resume
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

- **53-Rest ChestIndex-Ausbau** (nur bei Bedarf): Waypoint/ESP für Treffer von `search`, Suche
  nach Shulker-Namen, Hinlaufen zu Containern außer Reichweite (Baritone, Überschneidung mit 63).
- **63 StorageLooter:** Container automatisch looten und sortieren. PathSeeker ✅.
- **73 ChestTracker mit GUI:** live gepflegtes Container-Verzeichnis (erweitert 53). BepHax ⚠️.
- **74 ShulkerOverview:** Shulker-Inhalt im Tooltip, ohne Platzieren. BepHax ⚠️.

### Erkennung

- **56 PortalPatternFinder:** entfernte/kaputte Nether-Portale als Base-Signal. Trouser-Streak ⛔.
- **57 CaveDisturbanceDetector:** einzelne Luft-Blöcke in Cave-Air als Spuren. Trouser-Streak ⛔.
- **59 Base Finder:** Base-Erkennung über Spieleraktivität. PathSeeker ✅.
- **62 ESP-Suite:** PotESP, MobGearESP, DroppedItemESP, EntityClusterESP, Hole/Tunnel/Stair-ESP.
  Nur die von PathSeeker selbst geschriebenen ✅ übernehmen, Rest clean-room.

### Flug und Komfort

- **55-Rest benannte Pfade:** Suchgebiete unter einem Namen speichern und fortsetzen (Spiral-Modus
  ist erledigt, siehe 69). jeff mod ⛔.
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
- **30 Rest:** `keyUp`/`keyShift.setDown` in AutoElytraRepair bleiben (Zusammenspiel mit
  ElytraFly-Autopilot, nur in-game sicher änderbar).
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
- **53** ChestIndex (2026-10-31, clean-room): Modul `chest-index` + Befehl `.chest-index`
  (`search`, `clear`), Logik in `utils/ChestIndex` (+ Test), JSON pro Server unter
  `StashHunter/chest-index/`. Server-Schlüssel nach `utils/ServerKey` ausgelagert.
- **54** Xaero-Waypoints (2026-10-31): Setting `save-to-xaero` (opt-in). `xaero/XaeroBridge` +
  `XaeroImpl` analog zu Baritone; `compileOnly` gegen `maven.modrinth:xaeros-minimap:fabric-26.2-26.6.2`
  (Modrinth-Maven, `xaero_minimap_version` in `gradle.properties`), `suggests` in `fabric.mod.json`.
  Weg wie Xaeros eigene Todespunkte: `BuiltInHudModules.MINIMAP.getCurrentSession()` →
  `getWorldManager().getCurrentWorld()` → `getCurrentWaypointSet().add(...)` →
  `getWorldManagerIO().saveWorld(world)`. Das im Plan genannte
  `SupportMods.xaeroMinimap.requestWaypointsRefresh()` ist World-Map-Code und unnötig: `GuiMap.init()`
  ruft es beim Öffnen der Karte selbst auf.
