# Erkennung: Signal-Score — Design

**Stand:** 2026-11-28. Zweite Etappe des Releases 26.3.0, auf Branch `port/26.3` nach dem Port
([2026-11-28-port-26.3-design.md](2026-11-28-port-26.3-design.md)). Danach folgen eigene Designs für
Struktur-Locator, Auswertung sowie Flug & Komfort.

## Warum

Bei Testflügen von etwa 20 Minuten weit vom Spawn auf einem Anarchy-Server hat Stash-Hunter so gut wie
nie etwas gefunden. Der Nutzer hatte gehofft, wenigstens ein paar Shulker-Kisten zu finden.

Ursachen im heutigen Code:

1. **Schwelle zu hoch für kleine Funde.** `findStashes` meldet erst ab `block-detection-threshold`
   (Default 10) Containern in einem Cluster. Zwei, drei vergrabene Shulker fallen ohne jede Meldung
   durch. Dabei ist eine Shulker-Kiste allein schon ein sicheres Signal: Sie kommt in Vanilla nie
   natürlich vor.
2. **Nur Container zählen.** Spuren wie Schilder, Betten, Portale, Item-Frames, Kisten-Minecarts oder
   Esel mit Truhe sieht der Scan nicht. Die Module `activated-spawner-detector`, `stacked-minecarts`
   und `cave-disturbance-detector` melden getrennt, ihre Funde ergeben zusammen mit anderen Spuren
   keinen Fund.
3. **Keine Sicht darauf, was passiert.** Ob nichts gesehen oder etwas verworfen wurde, und wie viel
   Fläche wirklich gescannt wurde, erfährt der Nutzer nicht. Bei `scan-radius` 64 ist der Streifen nur
   128 Blöcke breit.

## Ziele

- Kleine, sichere Funde werden gemeldet: schon eine Shulker-Kiste oder Endertruhe.
- Schwache Spuren ergeben gemeinsam einen Fund (z. B. Schild + Bett + Kiste in einem Bereich).
- Zwei Meldestufen:
  - **Hinweis** (kleiner Fund oder Spielerspur): Chat, Meteor-Waypoint (optional Xaero), ESP.
    Kein Discord (abschaltbar per Setting, Default aus).
  - **Stash**: wie heute (Chat, Waypoint, Xaero, ESP, Discord).
- Alle Detektoren liefern Signale in eine gemeinsame Auswertung; es gibt einen Meldeweg.
- Diagnose nach jedem Flug: was gescannt, gesehen und warum verworfen wurde.
- Hinweis auf einen besser passenden `scan-radius` anhand der Sichtweite des Servers.
- Neue ESP-Module zum Anschauen: Items am Boden, Mobs mit Spieler-Ausrüstung, Löcher/Tunnel/Treppen.

## Nicht-Ziele

- Struktur-Locator per Seed, Looten/Auswerten, Flugsteuerung: eigene Etappen.
- Kein Umbau des Flugmusters oder der Chunk-Trail-Logik (NewerNewChunks).
- Keine Abdeckungs-Karte als Overlay in dieser Etappe (nur Zahlen in der Diagnose).
- Seed-Cracking: bewusst ausgeschlossen.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es technisch läuft)

- Discord läuft mit Kleinkram voll.
- Spürbare Ruckler: Scan und Auswertung bleiben im heutigen Tick-Budget.
- Die Stufe „klassisch“ verhält sich anders als 26.2.2.
- Übernommener Code aus Quellen ohne kompatible Lizenz (siehe Lizenz).

## Ansätze

| Ansatz | Bewertung |
|---|---|
| **A: Gemeinsamer Signal-Score** (gewählt) | Alle Detektoren liefern Signale (Position, Art, Gewicht), `stash-hunter` clustert Signale und entscheidet die Stufe. Kombinationen über Detektoren hinweg, ein Meldeweg, Diagnose an einer Stelle. Größter Umbau am Kern von `StashHunterModule`; abgesichert durch Stufe „klassisch“ und Unit-Tests. |
| B: Eigenes Modul `base-finder` (wie PathSeeker) | Schnell, wenig Risiko. Aber zwei Meldewege, Doppel- oder Fehlmeldungen, keine Kombinationen, Diagnose verteilt. Verworfen, weil Kombinationen der Kern der Lösung sind. |
| C: Neue Blöcke/Entities direkt in den bestehenden Scan | Kombinationen nur für Blöcke und Entities, die übrigen Module melden weiter getrennt. Halbe Lösung. Verworfen. |

Der Nutzer hat **A** gewählt.

## Lizenz

Alles clean-room. Recherche (2026-11-28): Der Base Finder in PathSeeker trägt keinen Credit, stammt aber
aus Trouser-Streak (etianl, keine Lizenz). Hole/Tunnel/Stairs-ESP und PotESP sind laut Dateikopf von
etianl, EntityClusterESP aus Meteorite, MobGearESP von „windoid“. Einen PortalPatternFinder gibt es in
PathSeeker nicht. Daher: nur Ideen, eigener Code; im Klassenkopf vermerken („Idee aus …, kein Code
übernommen“), wie bei `CaveAirPockets`. Neue Module sind standardmäßig aus.

## Design

### Signale

Ein Signal ist `(dimension, pos, kind, weight)`. `SignalKind` ist ein Enum mit Kategorie und
Default-Gewicht:

| Kategorie | Arten (Beispiele) | Bedeutung |
|---|---|---|
| **stark** | Shulker-Kiste (jede Farbe), Endertruhe | Allein ein Hinweis. Wird vom Strukturfilter nicht verworfen. |
| **Container** | Kiste, Fass, Trapped Chest, Hopper, Dispenser, Dropper, Öfen | Zählt für Stash; Kisten/Fässer können natürlich sein, Strukturfilter gilt. |
| **Spur** | beschriebenes Schild, Bett, Block, der nie natürlich vorkommt (Liste, z. B. Beton, Köpfe, Redstone-/Diamant-/Netherite-Block, gewachste Kupfer-Varianten), offenes Nether- oder End-Portal, Portalrahmen aus Obsidian ohne Crying Obsidian, Bau über Y 260 (Overworld), Block auf dem Nether-Dach, platziertes Bedrock außerhalb der natürlichen Schichten, Topf mit unnatürlichem Inhalt, Item-Frame mit Item, Entity mit Namensschild, Boot, Dorfbewohner mit Handelslevel > 1, Kisten-Minecart, Esel/Maultier/Lama mit Truhe, Mob mit Spieler-Ausrüstung, Minecart-Stapel (`stacked-minecarts`), aktivierter Spawner außerhalb natürlicher Strukturen (`activated-spawner-detector`), Cave-Disturbance-Tasche (`cave-disturbance-detector`) | Einzeln schwach, gemeinsam ein Hinweis. |

Die konkreten Gewichte und Listen legt der Umsetzende fest (siehe unten), mit diesen Vorgaben:
eine starke Art erreicht allein die Hinweis-Schwelle; eine einzelne Spur nie; drei verschiedene Spuren
im selben Cluster erreichen sie; ein Cluster mit nur Kisten/Fässern verhält sich bei Stufe „normal“
höchstens so empfindlich wie heute mit Schwelle 6 statt 10 (Strukturfilter gilt weiter).

### Quellen der Signale

- **Block-Entity-Durchlauf** (wie heute über `chunk.getBlockEntitiesPos()`): Container, starke Arten,
  Schilder (Text vorn oder hinten nicht leer), Töpfe, Spawner.
- **Block-Durchlauf** pro Section nur, wenn die Palette eine gesuchte Art enthalten kann
  (`section.maybeHas`, wie heute): Spur-Blöcke ohne Block-Entity, Portale, Obsidian-Rahmen, Bedrock;
  Sections über Y 260 (Overworld) bzw. über dem Nether-Dach nur auf „nicht Luft“.
- **Entity-Durchlauf** bei jedem Scan über die geladenen Entities im Scan-Radius. Entity-Signale gelten
  pro Chunk und werden beim nächsten Sehen des Chunks ersetzt (Entities bewegen sich und kommen nur in
  Entity-Reichweite an, das ist in Ordnung).
- **Bestehende Module** (`activated-spawner-detector`, `stacked-minecarts`, `cave-disturbance-detector`)
  veröffentlichen ihre Funde zusätzlich als Signale, wenn sie an sind. Ihr eigenes Verhalten (Chat,
  ESP, ggf. Discord) bleibt unverändert.

Der Index (`StashBlockIndex<V>` ist bereits generisch) hält Signale statt nur `Block` pro Chunk. Das
Neu-Scannen bleibt wie heute über `ChunkScanSchedule` und `MissedChunks`.

### Auswertung

1. `StashClustering.cluster` über die Signal-Positionen (Abstand/Größe wie heute über
   `max-cluster-distance` / `max-cluster-size`).
2. Strukturfilter (`filter-natural-structures`: Volumen, Dichte, Dungeon, Nähe zu Struktur-Spawnern)
   wirkt **nur auf Container**. Starke Arten und Spuren bleiben im Cluster.
3. Score = Summe der Gewichte. Stufe:
   - **Stash**, wenn die Container-Bedingung von heute erfüllt ist (Anzahl ≥ Schwelle der Stufe, Form
     passt) oder der Score die Stash-Schwelle erreicht.
   - **Hinweis**, wenn eine starke Art dabei ist oder der Score die Hinweis-Schwelle erreicht.
   - sonst nichts melden, aber in der Diagnose zählen (mit Grund).
4. Dedupe wie heute (`report-dedupe-radius`, `ReportedStashes`, Stash-Waypoints). Ein Hinweis kann
   später zum Stash aufsteigen: das läuft über den bestehenden „Stash Updated“-Weg (Waypoint wird
   ersetzt, jetzt mit Discord). Dafür merkt sich `ReportedStashes` die gemeldete Stufe; ältere Dateien
   ohne Stufe gelten als Stash.

### Präzisierungen (aus der Gutachter-Prüfung, eigene Entscheidungen)

- **Strukturfilter auf gemischten Clustern:** Geclustert wird über alle Signale. Volumen, Dichte,
  `tighten` und Dungeon-Filter laufen danach **nur auf der Container-Teilmenge** des Clusters. Fällt
  sie durch, werden die betroffenen Container entfernt; starke Arten und Spuren bleiben und werden
  normal bewertet. „Form passt“ in der Stash-Bedingung bezieht sich ebenfalls nur auf die Container.
  So ändern Dorf-Betten oder Mineshaft-Minecarts nichts daran, dass verstreute Struktur-Kisten
  verworfen werden; sie bleiben als einzelne schwache Spuren unter der Hinweis-Schwelle.
- **`block-detection-threshold`** bleibt ein Setting und gilt in allen Stufen als Container-Schwelle
  für Stash: `classic` nimmt den Wert direkt, `normal` `min(Wert, 6)`, `sensitive` `min(Wert, 4)`.
  Wer schon einen kleineren Wert eingestellt hat, behält ihn. Beschreibung des Settings anpassen.
- **`storage-only-mode`** wirkt nur in `classic` (heutiges Verhalten). In `normal`/`sensitive` sind
  die übrigen Blöcke aus `Config.stashBlocks` (Werkbank, Amboss, Zaubertisch, Braustand, Leuchtfeuer,
  Conduit, Betten …) immer Spur-Signale; Öfen, Hopper, Dispenser, Dropper sind Container. Beschreibung
  des Settings um „only in classic“ ergänzen.
- **Spawner:** Positionen aller Spawner werden immer erfasst, damit der Dungeon-Filter funktioniert.
  Als Signal zählt ein Spawner nur mit `include-spawners` (Spur) bzw. als aktivierter Spawner aus
  `activated-spawner-detector`.
- **Hinweis → Stash:** Steigt ein Cluster von Hinweis auf Stash, wird das als „Stash Updated“
  gemeldet (mit Discord), auch ohne das Wachstum aus `StashGrowth.grewEnough`, und auch, wenn der
  Hinweis aus einer früheren Sitzung stammt: Die Dedupe-Prüfung (`ReportedStashes`, Waypoints)
  blockiert nur gleiche oder höhere gemeldete Stufen. Ein gemeldeter Stash wird nie zum Hinweis
  herabgestuft.
- **`notification-density-threshold`** gilt nur für Stashes über die Container-Bedingung und wird über
  die Container berechnet (wie heute). Hinweise und Stashes über den Score bekommen immer Waypoint;
  der Discord-Schutz kommt von der hohen Score-Schwelle für Stash und `discord-hints` = aus.
- **Diagnose-Zähler:** pro Flug (von `.sh start`/`spiral`/`resume` bis Stop, Pause, Abschluss oder
  Landung), Zusammenfassung an diesen Punkten. Ohne Flug (Scanner im Stand bzw. Handflug) zählt der
  Zeitraum seit Einschalten des Moduls, Zusammenfassung beim Ausschalten. `.sh diag` zeigt den
  laufenden Zeitraum und die Summe der Sitzung. Die Zähler leben im Modul, nicht im `TripManager`.

### Empfindlichkeit

Neues Setting `sensitivity` in `stash-hunter`:

- **classic:** exakt das Verhalten von 26.2.2 (nur Container, `block-detection-threshold`, keine
  Hinweise). Muss durch Tests abgesichert sein.
- **normal** (Default, siehe Entscheidungen): Signale und zwei Stufen wie oben.
- **sensitive:** niedrigere Schwellen für Hinweis und Stash.

Dazu eine Settings-Gruppe **Signals** mit je einem Schalter pro Spur-Gruppe (Schilder, nie-natürliche
Blöcke, Portale, Bauten oben/Nether-Dach, Bedrock, Entities, andere Module), alle an. Einzelne Gewichte
sind keine Settings.

### Meldungen

- **Hinweis:** eine Chat-Zeile mit den wichtigsten Signalen (z. B. `Hint: 2 shulker boxes, 1 sign at
  x, y, z`), Meteor-Waypoint mit eigenem Namensschema (unterscheidbar vom Stash-Schema, damit
  `hasStashWaypointNear` beide erkennt), optional Xaero (eigene Farbe), ESP in eigener Farbe (Settings
  im Stash-ESP-Block). Setting `discord-hints` (Default aus).
- **Stash:** unverändert, die Discord-Nachricht listet zusätzlich die beteiligten Spuren.

### Diagnose

- Am Ende jedes Flugs (Stop, Pause, fertig, Landung) und auf `.sh diag`: Chat-Zusammenfassung mit
  gescannten Chunks, nie geladenen Chunks, erkannter Sichtweite, Anzahl Signale je Kategorie,
  Kandidaten je Ablehnungsgrund (zu wenig Score, Strukturfilter mit Unterart, schon gemeldet),
  Hinweise und Stashes.
- Ausführlich (jeder verworfene Kandidat mit Position, Signalen und Grund) in einer Logdatei pro
  Server unter dem Stash-Hunter-Ordner, angehängt über `AppendFiles`, Größe begrenzt.
- Beim Start eines Flugs: Ist `scan-radius` deutlich kleiner als die Sichtweite, schlägt der Chat
  einen größeren Wert vor; ist er größer, gibt es die bestehende Warnung.

### Neue ESP-Module (alle Default aus, Kategorie Stash-Hunter)

- `dropped-item-esp`: hebt Items am Boden aus einer einstellbaren Liste hervor (Default: Netherite-/
  Diamant-Ausrüstung, Elytra, Totem, Shulker-Kisten, End-Kristalle). Kein Signal (Items liegen auch
  nach Toden herum).
- `mob-gear-esp`: hebt Mobs mit Spieler-Ausrüstung hervor (verzauberte Diamant-/Netherite-Teile,
  Elytra, Totem). Liefert zusätzlich das Spur-Signal „Mob mit Spieler-Ausrüstung“.
- `tunnel-esp`: 1×1-Löcher, Tunnel (2-3 hoch) und Treppen. Kein Signal (Höhlen erzeugen zu viele
  Fehltreffer).

### Tests

- Unit-Tests ohne Minecraft-Bootstrap wie bisher, für Scoring und Stufen mit nachgebauten Lagen:
  3 Shulker vergraben, 1 Endertruhe, Schild + Bett + Kiste, 10 Kisten dicht (Stash), Dungeon (Spawner
  + 2 Kisten → nichts), Dorf-Kisten verstreut (nichts), Mineshaft-artige Kisten (nichts), Shulker in
  einem Dungeon (Hinweis).
- Test, dass `classic` auf denselben Lagen dieselben Meldungen ergibt wie die Logik von 26.2.2.
- Tests für Diagnose-Zählung und `ReportedStashes` mit Stufe (inkl. alte Datei ohne Stufe).
- Eine Anleitung für eine Singleplayer-Testwelt (welche Lagen wo bauen) für den In-game-Test, als
  Abschnitt in PLAN.md.

## Entscheidungen, die ich getroffen habe (bitte prüfen)

- **Default `sensitivity = normal`**, obwohl neue Settings laut PLAN opt-in sind: Mit `classic` als
  Default würde sich am Problem „findet nichts“ nichts ändern.
- Löcher/Tunnel und Items am Boden gehen nicht in den Score ein.
- Diagnose als Chat-Zusammenfassung plus Logdatei, kein HUD-Element.
- Gewichte sind Konstanten im Code, keine Settings; der Nutzer stellt über `sensitivity` und die
  Spur-Schalter ein.
- Setting-Namen und Chat-Texte auf Englisch wie der Rest des Mods.
- Die Punkte unter „Präzisierungen“ (Strukturfilter nur auf Containern, Schwelle per `min`,
  `storage-only-mode` nur in `classic`, Hinweis → Stash auch sitzungsübergreifend, Dichte-Schwelle
  nur für Container-Stashes, Diagnose pro Flug).

## Dem Umsetzenden überlassen

- Genaue Gewichte und Schwellen je Stufe (innerhalb der Vorgaben oben) und die genauen Blocklisten.
- Datenstruktur für Signale im Index und wie Entity-Signale pro Chunk ersetzt werden.
- Erkennung des Obsidian-Portalrahmens und der natürlichen Bedrock-Schichten im Detail.
- Aufteilung von `StashHunterModule` in kleinere Klassen, soweit es den Umbau erleichtert.
- Format und Größenlimit der Diagnose-Logdatei.
- Namensschema der Hinweis-Waypoints.
