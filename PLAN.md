# Stash-Hunter — Offene Arbeiten (Plan für die nächste Iteration)

> Kontext für den nächsten Agenten (z. B. Claude Code): Dies ist der Plan der
> verbleibenden Verbesserungen nach einem Code-Review. Was unten **nicht** als
> „Offen“ aufgeführt ist, wurde bereits behoben (siehe „Bereits erledigt“).
>
> **Stand 2026-10-29:** Phase 0 (Quick Wins) und Phase 1 (Stabilität) sind
> vollständig erledigt und im CHANGELOG dokumentiert. Offen sind noch die Phasen
> 2–5 (Threading/Performance, Scan-Korrektheit, Robustheit, Architektur) sowie
> die neuen Vorschläge am Ende dieses Dokuments.

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

---

## Feature-Idee: Welt-Seed nutzen (Struktur-/Biom-/Erz-Vorhersage)

> Status: Idee / Recherche, noch nicht umgesetzt. Im Code existiert aktuell keine
> Seed-, Struktur- oder Biom-Logik (nur die heuristische `filterNaturalStructures` /
> `isNaturalStructure` über Volumen/Dichte).

### Ausgangslage & Machbarkeit

- **Wichtig:** Der Welt-Seed ist client-seitig im **Multiplayer nicht verfügbar**
  (bewusst versteckt). Es gibt kein `mc.level.getSeed()`. Drei Wege: (a) manuelles
  Seed-Setting, (b) Seed-Cracking, (c) seed-unabhängige Daten (Slime-Chunks seit 1.19).
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

46. **Slime-Chunk-Overlay (gering, ohne Seed).**
    - Seit 1.19 sind Slime-Chunks seed-unabhängig (fester „Slime-Seed") → ohne Seed
      berechenbar. Overlay „Slime-Chunks" → potentielle Farm-/Base-Standorte.

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

## Verifikation

- Nach jeder Änderung: `./gradlew build` (JDK 25 nötig).
- Phase 0/1 sind committed (Branch `code-revision`, Commits `adb7235` und
  `a29d64e`); PLAN.md ist ebenfalls bereits eingecheckt. Vor Weiterarbeit einfach
  auf `code-revision` weiterarbeiten.
- Funktionsprüfung in-game: Modul an/aus, Death/Respawn, Dimensionswechsel,
  /stashhunter start + stop, Stuck-Auto-Fix, Elytra-Reparatur.
