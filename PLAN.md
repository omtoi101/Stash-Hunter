# Stash-Hunter — Offene Arbeiten (Plan für die nächste Iteration)

> Kontext für den nächsten Agenten (z. B. Claude Code): Dies ist der Plan der
> verbleibenden Verbesserungen nach einem Code-Review. Was unten **nicht** als
> „Offen“ aufgeführt ist, wurde bereits behoben (siehe „Bereits erledigt“).
>
> **Stand 2026-10-30:** Phasen 0–5 und die Punkte 33–43 sind erledigt bzw. bewertet
> (Details jeweils im Abschnitt und im CHANGELOG). Auch 34 ist jetzt erledigt (echtes
> Icon: Truhe + Zielerfassung). Offen sind nur noch die Feature-Ideen 44, 45 und 47
> (46 erledigt, siehe dort) sowie die Optimierungen 48–51 (52 durch den End-Detektor-Umbau
> hinfällig). Aus den Feature-Ideen ist 60 (OldChunkNotifier) erledigt.
> Alles ist ungetestet in-game - siehe „Verifikation“.

## Projekt-Kontext

- **Repo:** Meteor-Client-Addon (Fabric, Client-only), portiert auf **MC 26.2**.
- **Stack:** Loom `1.17-SNAPSHOT`, **Java 25** Toolchain, offizielle Mojang-Mappings
  (MC seit 26.1 unobfuskiert → kein mappings(...)), Meteor `26.2-SNAPSHOT`,
  optionaler Soft-Dependency auf Baritone `26.2-SNAPSHOT`, Gson.
- **Quellpfad:** src/main/java/com/stashhunter/stashhunter/
- **Build:** ./gradlew build (Windows: gradlew.bat build). Benötigt JDK 25
  (Foojay-Resolver lädt es ggf. nach) und Netzwerk für die Meteor/Baritone-Snapshots.
- **Wichtiger Hinweis:** Nach den Phase-0/1-Refactorings sind die **Zeilennummern**
  in den untenstehenden Phasen 2–5 teilweise veraltet (z. B. NewerNewChunks ist von
  994 auf 988, StashHunterModule von 717 auf 763 Zeilen). Vor jedem Fix die Stelle
  erneut per Suche (Symbol/Logstring) lokalisieren und nach dem Build
  `./gradlew build` laufen lassen.

---

## Bereits erledigt (nicht erneut machen)

### Phase 0 — Quick Wins (komplett, dokumentiert im CHANGELOG „Unreleased“)

1. **Tote Mixins/Events entfernt.** PlayerInventoryAccessor (Boot-Crash-Risiko),
   LivingEntityMixin (serverseitig, feuerte nie) und PlayerDeathEvent (null
   Subscriber) gelöscht; die leere addon-template.mixins.json wurde aus
   fabric.mod.json entfernt.
2. **Unerreichbaren Code & doppelte Logik aufgeräumt.** Der `!saveDataWasOn`-Block in
   NewerNewChunks wurde durch saubere Flanken-Erkennung ersetzt (CHANGELOG
   „SaveChunkData“); die identischen „Stash Found“-Ternary-Zweige in
   StashHunterModule sind vereinfacht.
3. **CI auf Java 25.** dev_build.yml und pull_request.yml nutzen jetzt
   `java-version: 25`.
4. **Config-Validierung aktiv.** `Config.validate()` wird in
   StashHunter.onInitialize() nach `Config.load()` aufgerufen; `max-volume-threshold`
   hat jetzt min 100 (konsistent mit validate()).
5. **Template-Reste.** Icon liegt unter assets/stash-hunter/icon.png,
   maven_group=com.stashhunter, mod_version mit CHANGELOG vereinheitlicht (26.2.1).
6. **Doku-Drift.** README flight-altitude (160), Clear-Kommandos
   (.clear-stashes/.clear-players) und Help-Präfix (.stashhunter) korrigiert.

### Phase 1 — Stabilität (komplett, Commit `a29d64e` „Harden flight, repair, and chunk-save handling“)

7. **Tod-/Weltwechsel-Teardown.** ElytraController pausiert (Trip gespeichert,
   fortsetzbar via .stashhunter resume) bei Tod, Dimensionswechsel und
   GameLeftEvent; Elytra/Jump-Zustand wird zurückgesetzt. Tod-Erkennung hängt nicht
   mehr an notify-on-death.
8. **Elytra bricht mitten in der Reparatur.** repair-threshold min 30; Abbruch via
   Emergency-Disconnect wenn Rest-Haltbarkeit ≤ 10; Positionierung wird übersprungen.
9. **Baritone/Controller-Kampf beendet.** Beide nutzen jetzt
   BaritoneBridge.isElytraActive(); isPathing()/hasPath() entfernt.
10. **Reparatur hing 300 s ohne XP.** Start nur mit XP-Flaschen; Abbruch wenn Schaden
    ~5 s nicht sinkt.
11. **EMERGENCY_DISCONNECT.** Zustand wird auf MONITORING zurückgesetzt, Modul
    deaktiviert sich im nächsten Tick; AutoPilot-Werte werden nur bei tatsächlich
    erfassten Werten zurückgeschrieben.

---

## Offene Arbeiten

### Phase 2 — Threading & Performance (Hauptursache für Hänger/Stutter)

> **Erledigt (2026-10-29, Punkte 12–16)**, Build grün, in-game noch ungetestet.
> Abweichungen vom Plan: Chunk-Paket-Dekodierung/-Analyse bleibt auf dem Netty-Thread
> (arbeitet nur auf einer privaten LevelChunk-Kopie), nur das Ergebnis geht per
> mc.execute auf den Client-Thread; Block-/Section-Updates komplett per mc.execute.
> Flush alle 100 Ticks statt Buffered Writer. getNewChunks() durch isNewChunk() ersetzt.

12. **Netty-Thread-Arbeit in NewerNewChunks beenden.** PacketEvent.Receive läuft auf
    dem Connection-Thread (newChunks.add, getBlockState, Datei-Writes). Treffer per
    mc.execute(...) auf den Client-Thread legen.
13. **Chunk-Paket async+join entfernen.** CompletableFuture.runAsync(...).join() auf
    dem Packet-Buffer (Race + sinnloser Context-Switch); synchron dekodieren oder
    Buffer kopieren. taskExecutor ist statisch und wird nie heruntergefahren.
14. **Datei-Writes batchen.** Pro Treffer open-write-close auf dem Netzwerk-Thread.
    Buffered Writer + Batch-Flush bei Disable/World-Leave/Dimensionswechsel.
15. **Render-/Trail-Loop-Allokationen.** 5 Set-Iterationen pro Frame, getNewChunks()
    kopiert pro Tick das ganze Set (wird in ElytraController zweimal pro Tick
    aufgerufen); O(n³)-Triple-Loop in analyzeLinearPattern. Snapshot cachen,
    Quadratdistanz auf Ints, Boxen cachen.
16. **scanForBlocks-Kosten.** 17×17 Chunks mit zwei Distanzchecks (sqrt/pow) pro
    Position. Sektionspaletten/Block-Entities iterieren, ein Quadratdistanz-Check mit
    MutableBlockPos, activeBlocks einmal cachen.

### Phase 3 — Scan-Korrektheit („findet mehr Stashes“)

> **Erledigt (2026-10-29, Punkte 17–21)**, Build grün, in-game noch ungetestet.
> 18 war gravierender als beschrieben: kugelförmige Distanz → beim Fliegen auf Y=160
> wurden Stashes am Boden nie gefunden; jetzt Zylinder über volle Welthöhe.
> 19 war ein Fehlalarm (getChunk(..., false) liefert null, solange nicht geladen) —
> nur Kommentar korrigiert, dafür Section-Indizes aus dem Level abgeleitet.
> 17 über Aufteilung gelöst: Voll-Scan nur noch für Blöcke ohne Block-Entity.
> Zusätzlich: Waypoints bekommen jetzt die richtige Dimension.
> Neue Settings: max-cluster-size (64), report-dedupe-radius (48).

17. **Doppelzählung im Full-Scan.** Container werden über getBlockEntitiesPos() und
    nochmal im Block-Loop gesammelt. LinkedHashSet<BlockPos> oder Positionen
    überspringen.
18. **Y-Bereich & Radius aus der Welt ableiten.** Hart [-64, 80) + 32 Blöcke
    horizontal → Nether-Dach, End und Berge werden nie gefunden. getMinY()/getMaxY()
    bzw. Settings, scanRadius respektieren.
19. **Chunk-Erkennung evtl. tot.** getChunk(..., false) == null laut eigenem
    Kommentar nie null → Analyse läuft nie. Bedingung prüfen.
20. **Cluster-/Volumen-Mathematik.** Transitives Verketten verschmilzt Stashes;
    harter 100-Block-Dedupe-Radius verwirft nahe echte Stashes. Grid/DBSCAN mit
    Durchmesser-Cap, Dedupe-Radius als Setting.
21. **Derselbe Stash alle 10 min erneut gemeldet.** reportedStashes.clear() bei
    tickCounter % 12000 → erneuter @everyone + doppeltes Waypoint. Zeitbasiertes Wipe
    entfernen, Dedupe über stabilen Key, vorhandene Waypoints prüfen.

### Phase 4 — Robustheit / Netzwerk / Config

> **Erledigt (2026-10-29, Punkte 22–27)**, Build grün, in-game noch ungetestet.
> Abweichungen: 23 debounced (Config.saveLater, 1 s) statt Meteor-Settings als einzige
> Persistenz; 24 fordert isFallFlying() ODER aktiven Bot-Flug (sonst würde genau der
> Fall „Elytra zu, Bot fällt“ nicht mehr erkannt); 25 zusätzlich TripManager: kaputte
> JSON wird nach .corrupt verschoben statt beim nächsten addTrip überschrieben.
> @everyone ist jetzt opt-in (Setting mention-everyone, Default aus).

22. **Discord-Webhook härten.** Unbegrenzte Queue, Response-Code ignoriert, kein
    Retry/Backoff, disconnect() fehlt, @everyone bei jeder Meldung. Status/retry_after
    auswerten, Queue begrenzen, @everyone opt-in, allowed_mentions setzen.
23. **Config entkoppeln/debouncen.** Jede onChanged → Config.save() (Slider =
    Dutzende Datei-Writes auf dem Client-Thread). Speichern debouncen + nach load()
    klemmen.
24. **AltitudeLossDetector falsche Auslösung.** Über den 200-Tick-Cooldown wird gegen
    veraltetes lastYPosition verglichen; feuert beim Laufen/Fallen. lastY/lastCheck
    während Cooldown aktualisieren, isFallFlying() fordern, Baritone nur bei
    ElytraController.isActive() canceln.
25. **Robustere Persistenz.** Korrupte Zeile → NumberFormatException (nur
    IOException gefangen); Parsing über replaceAll auf ChunkPos.toString() ist
    fragil. "x,z" explizit speichern, pro Zeile abfangen. Vec3dAdapter: unbekannte
    Felder überspringen (in.skipValue()), Double.isFinite-Check.
26. **justCompleted immer konsumieren.** notifyOnCompletion.get() && justCompleted()
    short-circuited → Flag bleibt bei deaktivierter Einstellung hängen.
27. **Command-Start absichern.** Startet Flug ohne aktives Modul; estimatedWaypoints
    nutzt X-Ausdehnung statt Streifenanzahl; stripWidth ohne Untergrenze (≤ 0 =
    Endlosschleife im Waypoint-Generator).

### Phase 5 — Architektur / Wartbarkeit

> **Erledigt (2026-10-29, Punkte 28–32)**, Build + 17 Unit-Tests grün, in-game ungetestet.
> 28 teilweise: Clustering (StashClustering), Waypoint-Generierung (GridWaypoints),
> Trail-Analyse (TrailAnalysis) und Chunk-Dateiformat (ChunkDataFormat) sind eigene,
> getestete Klassen. Die Detektoren in NewerNewChunks und die Zustandsautomaten in
> AutoElytraRepair/ElytraController sind weiter groß und statisch - ein Umbau dort ändert
> Verhalten und sollte erst nach In-Game-Tests passieren.
> 30: Die lastForwardPressed-Reflection hat nie funktioniert (Feld liegt in der Oberklasse
> ElytraFlightMode) und ist entfernt. keyUp/keyShift.setDown in AutoElytraRepair bleiben
> (Zusammenspiel mit ElytraFly-Autopilot, nur in-game sicher änderbar).
> 32: Spotless nur für Whitespace; Zeilenenden ausgenommen (Git normalisiert per
> .gitattributes, sonst schlägt der Check auf Windows-Checkouts fehl).

28. **God Classes & statischen Zustand auflösen.** NewerNewChunks, StashHunterModule,
    ElytraController, AutoElytraRepair. Reine Logik (Clustering, Waypoint-Generierung,
    Trail-Analyse, Trip-Konvertierung) in testbare Klassen extrahieren.
29. **Statischen Modul-Lookup im Konstruktor entfernen.** ElytraController holt
    NewerNewChunks beim Class-Load in static final → kann dauerhaft null sein.
30. **Reflection & manuelle Eingaben ersetzen.** Reflection auf private
    Meteor-Interna bricht bei jedem Snapshot-Update. Meteor-Setting-API nutzen;
    KeyMapping.setDown → Meteor-Input/ElytraFly.autoPilot, KeyHold refcounten.
31. **Toten Code entfernen & Logging vereinheitlichen.** WorldScanner zu ~80 %
    ungenutzt; calculateCenter liefert bei leerer Liste BlockPos.ZERO; 13×
    printStackTrace() und 8 leere catch-Blöcke; NewerNewChunks nutzt Tabs (Rest
    4 Spaces).
32. **Build/Test-Infrastruktur.** Spotless/Formatter; JUnit-Tests (Clustering,
    Waypoint-Generierung, Vec3dAdapter, Trip-Serialisierung); Gson auf compileOnly
    oder entfernen; Dependabot/Renovate für die beweglichen 26.2-SNAPSHOTs.

---

## Neue Verbesserungsvorschläge (nicht im ursprünglichen Review)

> **Stand 2026-10-29:**
> - 33 erledigt (Logger.java entfernt, alles über StashHunter.LOG; hatte doch 8 Caller).
> - 34 erledigt (2026-10-30): `icon.png` durch ein echtes Icon ersetzt (Truhe auf
>   dunklem Hintergrund mit rotem Zielring), passend zur Mod-Farbe `225,25,25`.
> - 35 nicht gemacht: bin/ ist der Build-Output der VS-Code-Java-Erweiterung (enthält
>   bereits die neuen Klassen) und entsteht beim Öffnen sofort neu; ist ignoriert.
> - 36 kein Problem: reportedStashes wird nur auf dem Client-Thread benutzt (Scan im
>   Tick, Commands, GameLeft/Dimensionswechsel) - nicht vom Netty-Thread.
> - 37 nicht gemacht: Locking pinnt bei SNAPSHOTs nur „26.2-SNAPSHOT“, nicht den
>   Build; Dependency-Verification würde bei jedem neuen Meteor-Build den Build brechen.
>   Sinnvoll erst mit festen Meteor-Releases oder gepinnten Timestamp-Snapshots.
> - 38 erledigt, aber mit `~26.2-` statt `>=26.2`: Meteor versioniert als `26.2-35`
>   (Pre-Release), `>=26.2` hätte jede Meteor-Version abgelehnt (per Fabric-Loader geprüft).
> - 39 erledigt (0.20.1). 40 entfällt durch 42. 41: auf Release 1.17.21 gepinnt;
>   1.18.4 braucht Gradle auf JDK 25, 1.19.3 zusätzlich Gradle ≥ 9.8.
> - 42 weiter als geplant: Fabric API komplett entfernt (Disconnect über Meteors
>   GameLeftEvent); kompiliert nur gegen fabric-resource-loader-v1, das Meteor bündelt.
> - 43 nicht gemacht: Bei unobfuskiertem MC verlangt modImplementation weiter Mappings;
>   das offizielle meteor-addon-template 26.2 nutzt ebenfalls implementation.

33. **`utils/Logger.java` komplett entfernen.** Hat **null Caller** (geprüft),
    ist nicht threadsafe, hartkodierter Pfad `~/.meteor-client/`, FileWriter wird nie
    geschlossen. Stattdessen überall `StashHunter.LOG` (SLF4J) verwenden. (Konkretisiert
    Punkt 31.)

34. **`icon.png` prüfen/ersetzen.** Das Mod-Icon ist nur ~2 KB (vermutlich der
    Template-Platzhalter). Echtes Stash-Hunter-Icon einsetzen und ggf. im
    meteor-addon-list.json (raw-URL) konsistent halten.

35. **Stale `bin/`-Verzeichnis aufräumen.** Im Repo-Root liegt ein altes
    Eclipse/IDE-Output-Verzeichnis mit einer Kopie von fabric.mod.json und kompiliertem
    Layout. Es ist zwar ignoriert, aber verwirrend → löschen (kein Gradle-Artefakt;
    Loom schreibt nach build/).

36. **Thread-Safety von `reportedStashes`.** Die ArrayList wird auf Client- und
    Netzwerk-Threads gelesen/geschrieben (alreadyReported-Stream vs. add/clear) → Race
    bei Meldungen. CopyOnWriteArrayList, oder besser: alle Mutationen über
    mc.execute(...) auf den Client-Thread legen (hängt mit Phase 2 zusammen).

37. **Reproduzierbare Builds für bewegliche Snapshots.** meteor-client und baritone
    sind `26.2-SNAPSHOT` (beweglich). Gradle Dependency Locking oder
    dependency-verification (dependencyVerification/gradle-write-verification-metadata)
    aktivieren, damit Builds reproduzierbar sind und sich nicht still ändern.
    (Ergänzt Punkt 32.)

38. **`fabric.mod.json`-Versionen präzisieren.** `"meteor-client": "*"` ist ein
    Wildcard; auf `">=26.2"` (oder die konkrete getestete Version) festlegen, um
    ungetestete Meteor-Versionen auszuschließen. `fabric-api-base` wird nicht direkt
    genutzt (nur transitiv via `fabric-networking-api-v1`) — siehe Punkt 42.

---

## Fabric- & Toolchain-Updates (Versionsstände geprüft 2026-10-29)

> Alle Versionen gegen die offizielle Fabric-Meta-API bzw. maven.fabricmc.net verifiziert.
>
> **Stand-Nachtrag (Code-Nachprüfung 2026-10-29):** Die Punkte 39–43 unten beschreiben den
> Zustand *vor* dem Phase-5-Refactor und sind damit teils überholt: 39 erledigt (Loader 0.20.1),
> 40 hinfällig (Fabric API komplett entfernt), 41 erledigt (Loom 1.17.21), 42 übererfüllt
> (keine Fabric API mehr), 43 verworfen (`implementation` bleibt bewusst, siehe Build-Kommentar).

39. **Fabric Loader aktualisieren.** `loader_version` steht auf `0.19.3`; aktuell
    stabile Version ist `0.20.1`. In gradle.properties anheben und Build prüfen.

40. **Fabric API aktualisieren.** `fabric_version` steht auf `0.160.0+26.2`; die
    neueste für MC 26.2 veröffentlichte Version ist `0.161.0+26.2`. (Neuer ist
    `0.165.1+26.4`, aber das ist für MC 26.4 — nicht für das 26.2-Target.)

41. **Fabric Loom aktualisieren (mit Vorsicht).** `id("net.fabricmc.fabric-loom")
    version "1.17-SNAPSHOT"` ist ein Snapshot; aktuelle stabile Version ist
    `1.19.3` (auch `1.18.4` verfügbar). Achtung: Loom-Major-Sprünge können das
    Unobfuscated-MC-Handling (kein mappings(...), `minecraft(...)` +
    `implementation(...)`) verändern — Build nach dem Bump gründlich prüfen,
    notfalls erst auf `1.18.4` statt `1.19.3`.

42. **Fabric-API-Dependency auf das nötige Modul verkleinern.** Das Projekt zieht das
    komplette `fabric-api`-Bundle (`implementation("net.fabricmc.fabric-api:
    fabric-api:...")`), nutzt aber nur **eine** Fabric-API-Klasse:
    `ClientPlayConnectionEvents` (StashHunter.java). Diese liegt im Modul
    **`fabric-networking-api-v1`** (Quelle: fabric-api-Repo). Fix: statt des
    Bundles nur `fabric-networking-api-v1` deklarieren (idealerweise
    `modImplementation`), um den Dev-Classpath und die Ladezeit zu verkleinern.
    Dabei `depends` in fabric.mod.json entsprechend anpassen: `fabric-api-base` ist
    nur eine transitive Abhängigkeit und kann ggf. entfallen (Fabric Loader löst
    transitive Mod-Deps automatisch auf).

43. **`modImplementation` statt `implementation` für Fabric-Module erwägen.** Der
    Kommentar in build.gradle.kts begründet `implementation` mit dem fehlenden
    Remapping (MC seit 26.1 unobfuskiert). Für `fabric-api`-`fabric-loader`-Module
    ist `modImplementation` dennoch idiomatischer (legt die Mod-Jars korrekt in den
    Dev-Runtime-Classpath und erzeugt fabric.mod.json-bewusste Metadaten). Prüfen, ob
    der Wechsel ohne Verhaltensänderung funktioniert.

44. **Port auf MC 26.3/26.4 prüfen (Mod steht auf 26.2).**
    - Code-Belegt: `minecraft_version=26.2`, `meteor-client: ~26.2-` (fabric.mod.json),
      `meteor-client: 26.2-SNAPSHOT` + `baritone: 26.2-SNAPSHOT` (build.gradle.kts).
      Wegen der `26.2`-Pins lädt das Mod nicht auf 26.3/26.4.
    - Extern verifiziert (2026-10-29): Meteor liegt bereits bei `26.3-SNAPSHOT`
      (maven.meteordev.org), Fabric API bei `0.165.1+26.4` (MC 26.4). Ein Port wäre nötig,
      um mit der neuesten Meteor-/MC-Version zu laufen.
    - Fix (bei Bedarf): `minecraft_version`, `meteor-client`, `baritone_version` und die
      `~26.2-`-Constraint anheben; `fabric-resource-loader-v1`-Version prüfen; Build + Tests.

---

## Feature-Idee: Welt-Seed nutzen (Struktur-/Biom-/Erz-Vorhersage)

> Status: Idee / Recherche, noch nicht umgesetzt. Im Code existiert aktuell keine
> Seed-, Struktur- oder Biom-Logik (nur die heuristische `filterNaturalStructures` /
> `isNaturalStructure` über Volumen/Dichte).

### Ausgangslage & Machbarkeit

- **Wichtig:** Der Welt-Seed ist client-seitig im **Multiplayer nicht verfügbar**
  (bewusst versteckt). Es gibt kein `mc.level.getSeed()`. Zwei Wege: (a) manuelles
  Seed-Setting, (b) Seed-Cracking. (Slime-Chunks sind in Java Edition **nicht**
  seed-unabhängig - das gilt nur für Bedrock; siehe 46.)
- Singleplayer: Seed lesbar via `Minecraft.getSingleplayerServer().overworld().getSeed()`.
- Seed-Cracking ist etabliert (Mod `SeedCrackerX`, Library `KaptainWutax/Seedfinding`).

### Vorschläge (gestaffelt nach Aufwand)

44. **Manuelles `world-seed`-Setting + Struktur-Locator (mittlerer Aufwand, hoher Nutzen).**
    - Setting `world-seed` (Long). Wenn gesetzt: generierte Strukturen (Dungeons,
      Strongholds, Villages, Tempel, Mineshafts, End-Städte, Fortresses, Bastions)
      exakt vorausberechnen und als Overlay/Waypoints rendern.
    - Nutzen: (1) „freies Loot" in Struktur-Chests finden; (2) `filterNaturalStructures`
      exakt statt heuristisch (Struktur-Chests nicht mehr als Spieler-Stash falsch
      melden); (3) ElytraController kann Struktur-Koordinaten als zusätzliche Ziele
      abfliegen. Waypoints über Meteors `Waypoints`-System.

45. **Seed-Cracking (auto, hoher Aufwand, „Holy Grail" für Anarchy-Server).**
    - Seed aus beobachteten Daten reverse-engineeren: Dungeon-/End-Säulen-Positionen,
      Warped-Fungus, Biome-Samples (Technik wie SeedCrackerX).
    - Library `KaptainWutax/Seedfinding` (Lizenz prüfen). CPU-lastig → eigener
      Thread (passt zu Phase 2), Fortschritt im HUD. Ergebnis automatisch in 44
      übernehmen.

46. **Slime-Chunk-Overlay (gering).** — **Erledigt (2026-10-29)**, Build + Tests grün,
    in-game ungetestet.
    - Korrektur: Die ursprüngliche Annahme „seit 1.19 seed-unabhängig" stimmt nur für
      Bedrock. In Java Edition (auch 26.2) ist es
      `WorldgenRandom.seedSlimeChunk(x, z, worldSeed, 987234911L).nextInt(10) == 0`.
    - Umsetzung: Modul `slime-chunks` (SlimeChunkOverlay) + reine Logik
      `utils/SlimeChunks` (Unit-Test vergleicht gegen Vanilla). Seed im Singleplayer
      automatisch vom Integrated Server, im Multiplayer über das Setting `world-seed`
      (Zahl oder Text wie im „Welt erstellen"-Screen) - darauf kann 44 aufbauen.
      Nur Overworld; Chunk-Menge wird nur bei Wechsel von Spieler-Chunk/Seed/Radius
      neu berechnet.
    - Restunsicherheit (geprüft): Im Singleplayer wird der Seed vom Render-Thread aus
      beim Server abgefragt, also über Thread-Grenzen hinweg. Harmlos, weil sich der
      Seed nach dem Start nicht mehr ändert und `mc.player != null` vorher sicherstellt,
      dass der Server fertig gestartet ist; `overworld() == null` wird abgefangen.
      Hinweis: `currentSeed()` läuft pro Frame — in Singleplayer ein redundanter
      per-Frame-Lookup, der sich einmal cachen ließe.

47. **Erz-/Terrain-Vorhersage (mit Seed, baut auf 44 auf).**
    - Diamant-/Erzverteilung + Biome vorhersagen → Scan-/Base-Ziele priorisieren,
      „uninteressante" Biome beim Scan überspringen.

### Hinweise

- Seed-Cracking ist rechnerisch teuer und braucht viele Datenpunkte → nie auf dem
  Render-/Client-Thread; parallelisieren (Erinnerung an Phase 2).
- Grey-Hat-Aspekt: Auf öffentlichen Servern ist Seed-Cracking meist unerwünscht →
  als Opt-in mit klarer Kennzeichnung auslegen.
- Lizenz der Seedfinding-Library vor dem Bündeln prüfen (nicht blind als
  `implementation` ziehen).

---

## Weitere Optimierungen (geprüft 2026-10-29)

> Alle Punkte gegen den aktuellen Code verifiziert. Die frühere Annahme „Math.pow läuft
> pro Tick (Zeile 132)" war falsch — das ist die einmalige `resume()`-Methode; nur
> `handleNormalNavigation` (222) läuft pro Tick. God-Classes sind bereits Punkt 28.

48. **`classifyChunk`-Klassifizierung vom Netty-Thread holen.**
    - Belegt: `classifyChunk(...)` wird in `onReadPacket` (Netty-Thread, Kommentar
      Z.644–647) direkt aufgerufen (Z.674). Die Overworld-Schleife scannt bis zu 17
      Sektionen à 16³ Blöcke (Z.710–732) → ~70k Iterationen pro Erst-Chunk.
    - Fix: `replaceWithPacketData` (Decode) muss auf dem Netty-Thread bleiben
      (Packet-Buffer); die private `LevelChunk`-Kopie danach per `mc.execute(...)`
      auf dem Client-Thread klassifizieren.

49. **`SlimeChunkOverlay` allokiert pro Chunk ein `Random` und rechnet auf dem Render-Thread.**
    - Belegt: `recompute()` läuft in `onRender` und iteriert `(2·radius+1)²` Chunks
      (Default 16 → 1.089, Max 64 → 16.641). `SlimeChunks.isSlimeChunk` macht
      `new Random(seed)` pro Chunk (Z.23). Recomputation einmal pro Chunk-Wechsel (Cache).
    - Fix: einen `Random` wiederverwenden (`setSeed`) bzw. den 48-bit-LCG inline;
      Recomputation inkrementell oder auf Hintergrund-Thread.

50. **`ElytraController.handleNormalNavigation` rechnet pro Tick mit `Math.pow`/`Math.sqrt`.**
    - Belegt: Z.222–224 (läuft pro Tick während des Flugs). `Math.pow(x, 2)` → `x*x`;
      Vergleich `horizontalDistance < 20.0` → `dx²+dz² < 400` (sqrt entfällt).
    - Hinweis: dieselbe Form in `resume()` (Z.132–134) läuft nur einmal — dort egal.

51. **Executor-/Thread-Ressourcen werden nie heruntergefahren.**
    - `Config.SAVER` (ScheduledExecutor, Z.26) und `DiscordWebhook.EXECUTOR`
      (ThreadPoolExecutor) sind Daemon-Threads ohne `shutdown()`. Ein pending
      `saveLater` (1 s) kann beim Beenden verloren gehen → Flush bei Client-Shutdown.

52. **Allocation im End-Detektor.**
    - `new BlockPos(x, 64, z)` in `classifyChunk` (Z.766) im 16×16-Loop; funktional
      korrekt (`getBlockState` maskiert x/z auf 0–15), aber unnötig allokiert.
      Abfrage ohne `BlockPos`-Objekt ausdrücken (int-Variante bzw. Sektion direkt).
    > **Hinfällig (2026-10-30):** Der End-Stone-Loop war auch fachlich falsch (markierte fast
    > jeden Insel-Chunk als alt) und ist ersetzt: Der Detektor liest jetzt einmal die
    > Biome-Palette (`collectBiomesInPalette`) und prüft außerhalb der Hauptinsel auf
    > `minecraft:the_end`. Kein `BlockPos` mehr. Details: DSH-HANDOFF.md, CHANGELOG *Fixed*.

---

## Feature-Ideen aus ähnlichen Repos (recherchiert 2026-10-30)

> Vergleich mit anderen Stash-Hunting-/Anarchy-Addons. Für die Umsetzung ist die
> **Lizenz** der Quelle entscheidend: Stash-Hunter ist **Apache-2.0**, daher darf nur
> kompatibler Code übernommen werden.

**Lizenz-Status der Quellen:**

- ✅ **PathSeeker** ([FaxHack/PathSeeker](https://github.com/FaxHack/PathSeeker)) —
  **Apache-2.0**, kompatibel. Code darf mit Attribution übernommen werden (Apache-
  Copyright/NOTICE behalten, geänderte Dateien kennzeichnen). **Aber:** Module, die dort
  als „Ported From Jeff-Mod"/„TrouserStreak"/„Stardust" markiert sind, stammen aus Quellen
  ohne Lizenz bzw. GPL — nur die von PathSeeker **selbst** geschriebenen Module (z. B. von
  `xqyet`: AutoPortal, TridentAura, CollectorsESP, BaritonePathMacro) sauber übernehmen.
- ⛔ **jeff mod** ([miles352/meteor-stashhunting-addon](https://github.com/miles352/meteor-stashhunting-addon)) —
  **keine Lizenz** (`license: null`, Repo **archiviert**). Code **nicht** übernehmen.
  Nur als Referenz/Spezifikation für eine eigene (Clean-Room-)Implementierung; oder den
  Autor (Discord `0x658`) um Erlaubnis fragen. Unterstützt bereits MC 26.1.2.
- ⛔ **Trouser-Streak** ([etianl/Trouser-Streak](https://github.com/etianl/Trouser-Streak)) —
  **keine Lizenz** → nur Idee. (Enthält laut Autor zudem „ein paar Module, die nicht von
  mir stammen", also unklare Herkunft.)
- ⚠️ **Stardust** ([0xTas/stardust](https://github.com/0xTas/stardust)) und **BepHax**
  ([dekrom/BepHaxAddon](https://github.com/dekrom/BepHaxAddon)) — **GPL-3.0**, inkompatibel
  mit Apache-2.0. Nur die **Idee** übernehmen, **keinen** Code (sonst müsste Stash-Hunter
  auf GPL umgestellt oder die Autoren gefragt werden).

**Empfehlung:** Die untenstehenden Features **clean-room** (nach README/Wiki-Konzept, eigener
Code) implementieren; nur die von PathSeeker selbst geschriebenen Apache-2.0-Module direkt
portieren (mit Attribution). jeff mod ist die inhaltlich beste Quelle, aber ohne Lizenz nicht
legal kopierbar — bei Bedarf den Autor auf Discord (`0x658`) kontaktieren.

### Vorschläge (ab Nummer 53; Quelle + Lizenz jeweils angegeben)

53. **ChestIndex / Stash-Logging (höchster Wert).** Container in Reichweite automatisch
    öffnen, Inhalt indexieren und als JSON exportieren (`.sh export`), statt nur Container
    zu zählen. Quelle: jeff mod `ChestIndex` ⛔ → clean-room; ergänzt die bestehende
    Cluster-Erkennung. (Basis ist der unter 2/3 vorgeschlagene persistente Stash-Export.)

54. **Xaero-Minimap/World-Map-Integration.** Stash-Funde und Trails als Xaero-Marker
    statt nur Meteor-Waypoints (BetterStashFinder/TrailMaker). Quelle: jeff mod ⛔ → clean-room;
    Soft-Dependency auf XaeroPlus/Xaero (wie Baritone).

55. **SearchArea: Spiral-Modus + benannte Pfade.** `.sh start` kann aktuell nur Rechtecke;
    Spiral-Modus und das Speichern/Weiterlaufen unter einem Namen (jeff mod macht das
    automatisch). Quelle: jeff mod `SearchArea` ⛔ → clean-room.

56. **PortalPatternFinder.** Entfernte/kaputte Nether-Portale als Base-/Skip-Signal.
    Quelle: Trouser-Streak ⛔ (in PathSeeker als Port) → clean-room.

57. **CaveDisturbanceDetector.** Einzelne Luft-Blöcke in Cave-Air = Störungen/Spuren.
    Quelle: Trouser-Streak ⛔ → clean-room.

58. **StackedMinecartDetector.** Gestapelte (gedupte) Spawner erkennen — wichtiges
    Stash-Signal. Quelle: PathSeeker ✅ (Apache-2.0) → direkt portierbar (mit Attribution).

59. **Base Finder.** Breitere Base-Erkennung über Spieleraktivität (nicht nur Chest-Cluster).
    Quelle: PathSeeker ✅ (Apache-2.0) → direkt portierbar.

60. **OldChunkNotifier.** Separate Discord-Meldung beim Betreten alter Chunks
    (NewerNewChunks erkennt sie bereits, meldet aber nicht). Quelle: jeff mod ⛔ → clean-room.
    > **Erledigt (2026-10-30)**, nicht gebaut (Mojang-Download im Sandbox-Netz gesperrt),
    > in-game ungetestet.
    > - Eigenes Modul `old-chunk-notifier` (clean-room). Liest die Sets von NewerNewChunks über
    >   `isOldChunk`/`isOldGenerationChunk`/`isBeingUpdatedChunk`, meldet in Chat + Discord
    >   nur den Übergang neu→alt, mit Cooldown (Default 60 s).
    > - Bei Dimensionswechsel werden `lastChunk` und `inOldArea` zurückgesetzt, der Cooldown
    >   bleibt (gegen Portal-Spam). Details: CLAUDE-HANDOFF.md.
    > - Mitbehoben: End-OldChunk-Detektor (siehe 52), sonst hätte der Notifier im End
    >   Falschmeldungen geschickt. Details: DSH-HANDOFF.md.

61. **ElytraFlyPlusPlus (Bounce/Y-Boost/Fake-Fly).** Highway-Obstacle-Passing, Bounce-Mode,
    Y-Boost, Fake-Fly mit Chestplate. Quelle: jeff mod ⛔ → clean-room (Y-Boost/Fake-Fly sind
    auf 2b2t gepatcht).

62. **ESP-Suite.** PotESP, MobGearESP, DroppedItemESP, EntityClusterESP, VanityESP, Hole/Tunnel/
    Stair-ESP. Quellen gemischt: Trouser-Streak ⛔, jeff mod ⛔, `windoid/MobGearESP` (via
    PathSeeker). Einzelne ESP sind von PathSeeker selbst ✅ — nur die übernehmen; Rest clean-room.

63. **StorageLooter.** Container automatisch looten und Items sortieren. Quelle: PathSeeker ✅
    (Apache-2.0) → direkt portierbar.

64. **AutoPortal / ElytraSwap / GrimEfly.** Portal automatisch bauen; Elytra bei niedriger
    Haltbarkeit tauschen; Flug ohne Elytra-Verschleiß. Quelle: PathSeeker (`xqyet`) ✅
    (Apache-2.0) bzw. jeff mod ⛔ → AutoPortal/ElytraSwap direkt, GrimEfly clean-room.

65. **SignHistorian.** Schilder aufzeichnen/wiederherstellen. Quelle: Stardust ⚠️ (GPL-3.0,
    in PathSeeker als Port) → nur Idee, kein Code.

66. **Pitch40Util / GotoPosition / HighlightOldLava / AFKVanillaFly.** Movement-/QoL-Helfer.
    Quelle: jeff mod ⛔ → clean-room.

### Querverweise

- 53/54 bauen auf den früheren Vorschlägen „persistente Stash-Liste + `.sh export/import`"
  und „Discord um Server/Dimension ergänzen" auf (siehe Antwort-Kontext, nicht nummeriert).
- 58/59/62 überschneiden sich thematisch mit dem Feature-Block 44–47 (Seed/Struktur), bleiben
  aber unabhängig vom Welt-Seed.
- `NewerNewChunks`-Teilen (crash-resistent + Ordner kopieren) hat PathSeeker ebenfalls —
  Stash-Hunter kann das im Kern schon (SaveChunkData/LoadChunkData), nur der „Ordner teilen"-
  Komfort fehlt.

---

## Verifikation

- Nach jeder Änderung: `./gradlew build` (JDK 25 nötig).
- Phasen 0–4 sind committed (Branch `code-revision`), Phase 5 und 33–43 liegen
  noch uncommitted im Working Tree.
- `./gradlew build` führt jetzt auch die Unit-Tests und den Spotless-Check aus.
- Funktionsprüfung in-game: Modul an/aus, Death/Respawn, Dimensionswechsel,
  /stashhunter start + stop, Stuck-Auto-Fix, Elytra-Reparatur.
