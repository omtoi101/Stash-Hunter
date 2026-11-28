# Struktur-Locator (Filter per Seed) — Design

**Stand:** 2026-11-28. Dritte Etappe des Releases 26.3.0 auf Branch `port/26.3`, baut auf dem
Signal-Score auf ([2026-11-28-detection-signal-score-design.md](2026-11-28-detection-signal-score-design.md)).
Ersetzt PLAN-Punkt 44; Punkt 47 (Erz-/Biom-Vorhersage) wird gestrichen, Punkt 45 (Seed-Cracking)
übernimmt SeedCracker X.

## Warum

Der heutige Strukturfilter (`filter-natural-structures`) schätzt über Volumen, Dichte, Dungeon-Form
und Nähe zu Struktur-Spawnern. Er muss streng sein, damit Dorf-, Mineshaft- oder Ancient-City-Kisten
nicht gemeldet werden, und verwirft dabei auch echte Funde. Mit dem Welt-Seed lässt sich für die
großen Strukturen genau sagen, wo sie liegen. Der Nutzer bekommt den Seed seines Servers mit
SeedCracker X.

## Ziele

- Container, die sicher zu einer großen Vanilla-Struktur gehören, zählen im Signal-Score nicht
  (gleiche Stelle wie der heutige Strukturfilter: nur die Container-Teilmenge, siehe Erkennungs-Design).
- Mit bekanntem Seed kann die Schätzung (Volumen/Dichte) lockerer werden, sodass mehr echte Funde
  durchkommen.
- Seed-Quelle: automatisch von SeedCracker X, im Singleplayer aus der Welt, sonst von Hand; gespeichert
  pro Server; `slime-chunks` nutzt denselben Seed.
- Die Diagnose nennt „Struktur (Seed): <Typ>“ als eigenen Ablehnungsgrund.

## Nicht-Ziele

- Keine Anzeige der Strukturen (Overlay/Waypoints) — vom Nutzer bewusst nicht gewählt.
- Kein Geländevergleich mit dem ursprünglichen Terrain.
- Keine Dungeons, Wüstenbrunnen oder andere Features (brauchen echtes Gelände). Dafür bleibt der
  bestehende Dungeon-Filter.
- Kein eigenes Seed-Cracking.

## Anti-Ziele

- Echte Stashes werden verworfen, weil sie zufällig im Radius eines Struktur-*Kandidaten* liegen, an
  dem gar keine Struktur steht.
- Spürbare Ruckler durch die Berechnung.
- Der Seed verlässt den Rechner (keine Übertragung, nur lokale Speicherung).

## Ansätze

| Ansatz | Bewertung |
|---|---|
| **1: Eigene Berechnung + Biom-Prüfung** (gewählt) | Abstandsregeln aus den Vanilla-Daten im Minecraft-Jar, Kandidaten-Chunks aus dem Seed, Bestätigung über das Biom aus dem empfangenen Chunk, Radius pro Strukturtyp. Klein, schnell, testbar; Radius ist eine Schätzung. |
| 2: Vanilla-Weltgenerierung auf dem Client | Exakt wie `/locate`, auch ohne geladene Chunks. Schwer (Speicher, Rechenzeit), hängt an Interna, die sich jede Version ändern. Verworfen. |
| Externe Seed-Bibliothek (KaptainWutax u. a.) | Seit Jahren nicht für aktuelle Versionen gepflegt. Verworfen. |

## Design

### Seed

- Neue Klasse für den Seed pro Server (Schlüssel `ServerKey`, persistent in der Stash-Hunter-Config,
  atomar gespeichert wie die übrigen Dateien). Reihenfolge: von Hand eingetragen > SeedCracker X >
  Singleplayer-Welt.
- **SeedCracker X** (MIT, optional wie Xaero): Stash-Hunter registriert einen Entrypoint `seedcrackerx`
  mit einer Klasse, die `kaptainwutax.seedcrackerX.api.SeedCrackerAPI` implementiert
  (`pushWorldSeed(long seed)`). Abhängigkeit nur `compileOnly` (Modrinth-Maven), `suggests` in
  `fabric.mod.json`. Der Aufruf kann von einem fremden Thread kommen: Übergabe per `mc.execute`.
  Bei Empfang Chat-Zeile „World seed received from SeedCracker X“.
- **Von Hand:** Befehl `.sh seed <seed>` / `.sh seed clear` / `.sh seed` (zeigt Quelle, nicht den
  vollen Seed, solange nicht ausdrücklich `.sh seed show`). Zum bisherigen Setting `world-seed` siehe
  Präzisierungen.
- **Singleplayer:** `getSingleplayerServer().overworld().getSeed()` wie heute in `SlimeChunkOverlay`.

### Strukturen berechnen

- Abstandsregeln (`structure_set`: Placement-Typ, Spacing, Separation, Salt, Frequenz, ggf.
  Ausschlusszonen) aus den Vanilla-Daten im Minecraft-Jar (`data/minecraft/worldgen/structure_set/`)
  lesen, einmal beim Start. Fehlen die Daten, Fallback auf eine eingebaute Tabelle für 26.3 und eine
  Warnung im Log.
- Für Random-Spread-Placements: mögliche Start-Chunks im Scan-Bereich nach dem Vanilla-Verfahren
  aus Seed und Salt (inkl. Frequenz-Reduktion, z. B. Mineshafts). Konzentrische Ringe (Strongholds)
  nach dem Vanilla-Verfahren; ihre Biom-Verschiebung ist nicht nachrechenbar, daher größerer Radius.
- Bei Structure-Sets mit mehreren Strukturen (z. B. Dörfer, Nether-Komplexe) entscheidet das Biom
  des Start-Chunks, welche gemeint ist.
- **Bestätigung:** Ein Kandidat gilt als Struktur, wenn sein Start-Chunk geladen ist und das Biom an
  der für den Typ relevanten Stelle passt (z. B. Ancient City: `deep_dark` in der Tiefe). Ist der
  Chunk nicht geladen, gilt der Kandidat als unbestätigt und filtert nicht (Anti-Ziel 1).
- Pro Strukturtyp ein Radius um den Start (Werte legt der Umsetzende fest und prüft sie an
  Singleplayer-Welten). Container innerhalb des Radius und im passenden Höhenbereich zählen als
  Struktur-Loot.
- Ergebnisse pro Chunk und Dimension zwischenspeichern; die Berechnung kostet pro Scan nur die neu
  hinzugekommenen Chunks.

### Einbindung in den Signal-Score

- `isNaturalStructure` (bzw. der Filter auf der Container-Teilmenge) fragt zuerst den Locator: liegt
  ein Container in einer bestätigten Struktur, fällt er raus (Grund „Struktur (Seed)“).
- Ist ein Seed bekannt, gelten lockerere Volumen-/Dichte-Grenzen (Setting `relax-with-seed`, Default
  an); Dungeon-Filter und Spawner-Nähe bleiben.
- Starke Signale (Shulker, Endertruhe) werden nie wegen einer Struktur verworfen (wie im
  Erkennungs-Design).

### Präzisierungen (aus der Gutachter-Prüfung, eigene Entscheidungen)

- **Code-Basis:** Der Plan setzt auf Branch `port/26.3` auf, nachdem Port und Signal-Score umgesetzt
  sind. Einhängepunkt ist der Container-Filter des Signal-Scores (heute
  `StashHunterModule.isNaturalStructure`), Ablehnungsgrund „Struktur (Seed)“ in dessen Diagnose.
- **Nicht-Vanilla-Platzierung (alte Chunks, geänderte Struktur-Seeds, ältere Server-Version):**
  - Ein Strukturtyp wird nur in Chunks gefiltert, die nicht als alt bekannt sind. Ist
    `NewerNewChunks` an und markiert einen Chunk als „old generation“, wird dort nicht per Seed
    gefiltert. Zusätzlich hat jeder Typ ein „seit Version“ (Ancient City 1.19, Trial Chamber 1.21 …);
    ist ein Chunk als älter erkannt, entfällt der Typ.
  - Selbstprüfung pro Server: Der Locator zählt bestätigte Kandidaten, an deren Stelle der Scan
    Struktur-Belege sieht (z. B. Spawner bei Mineshaft/Festung, Kisten im Radius), und solche ohne
    jeden Beleg. Liegt nach mindestens 10 bestätigten Kandidaten die Trefferquote unter 30 %, schaltet
    er sich für diesen Server ab und meldet das im Chat („seed placement does not match this server“).
    Die Zahlen sind Startwerte, der Umsetzende darf sie anhand von Tests anpassen.
  - Setting `use-seed-structures` (Default an) als manueller Schalter.
  - Restrisiko bewusst akzeptiert; begrenzt dadurch, dass starke Signale nie gefiltert werden.
  - Hinweis: SeedCracker X knackt den Seed selbst über Strukturpositionen. Hat es geklappt, passt die
    Platzierung auf diesem Server in aller Regel.
- **Biome werden beim Empfang eines Chunks zwischengespeichert** (Start-Chunk „irgendwann gesehen“,
  nicht „gerade geladen“), pro Dimension begrenzt.
- **Lockerung nur ohne offene Kandidaten:** `relax-with-seed` gilt für einen Cluster nur, wenn im
  Radius kein unbestätigter Kandidat (Start-Chunk nie gesehen) liegt. Sonst gelten die normalen
  Volumen-/Dichte-Grenzen.
- **Umfang:** alle Vanilla-Strukturen mit Containern und Random-Spread- bzw. Ring-Platzierung: Dorf,
  Mineshaft, Ancient City, Trial Chamber, Festung, Bastion, End City (inkl. Schiff), Stronghold,
  Woodland Mansion, Pillager Outpost, Wüsten- und Dschungeltempel, Schiffswrack, Vergrabener Schatz,
  Ruinierte Portale, Iglu, Ozeanruinen. Kleine Strukturen bekommen kleine Radien. Testdaten für alle.
- **Seed-Setting:** Das globale Setting `world-seed` in `slime-chunks` entfällt, weil ein einziger
  Wert für alle Server falsch ist und den Seed im Klartext zeigt. `slime-chunks` nutzt den Seed-Speicher
  und meldet ohne Seed „No world seed - use .sh seed <seed>“. Ein vorhandener alter Wert wird beim
  nächsten Server-Beitritt diesem Server zugeordnet (Quelle „manual“), sofern der noch keinen Seed hat,
  danach gelöscht; im Chat steht, welchem Server er zugeordnet wurde.

### Tests

- Kandidaten-Berechnung gegen bekannte Koordinaten: in einer Singleplayer-Welt mit festem Seed per
  `/locate` ermittelte Strukturen als Testdaten (Dorf, Mineshaft, Ancient City, Trial Chamber,
  Festung, Bastion, End City, Stronghold).
- Lesen der `structure_set`-Daten und Fallback-Tabelle.
- Seed-Speicher: Vorrang der Quellen, pro Server getrennt, Persistenz.
- Filter: Container in bestätigter Struktur raus, in unbestätigtem Kandidaten nicht, Shulker nie.

## Entscheidungen, die ich getroffen habe (bitte prüfen)

- Unbestätigte Kandidaten (Chunk nicht geladen) filtern nicht.
- `.sh seed` zeigt den vollen Seed nur mit `show` (er landet sonst in Screenshots/Streams).
- `relax-with-seed` standardmäßig an.
- Die Punkte unter „Präzisierungen“: Schutz vor Nicht-Vanilla-Platzierung (alte Chunks, Selbstprüfung
  mit Abschaltung, Schalter), alle Container-Strukturen im Umfang, `world-seed`-Setting entfällt
  zugunsten von `.sh seed`.

## Dem Umsetzenden überlassen

- Radius und Höhenbereich pro Strukturtyp.
- Genaue Biom-Stelle der Bestätigung pro Typ.
- Ob die Fallback-Tabelle nötig ist (falls die Daten sicher im Jar liegen, reicht ein Test dafür).
- Genaue gelockerte Volumen-/Dichte-Werte.
