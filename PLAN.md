# Stash-Hunter — Offene Arbeiten (Plan für die nächste Iteration)

> Kontext für den nächsten Agenten (z. B. Claude Code): Dies ist der Plan der
> verbleibenden Verbesserungen nach einem Code-Review. Was unten **nicht** aufgeführt
> ist, wurde in derselben Session bereits behoben (siehe „Bereits erledigt“).

## Projekt-Kontext

- **Repo:** Meteor-Client-Addon (Fabric, Client-only), portiert auf **MC 26.2**.
- **Stack:** Loom `1.17-SNAPSHOT`, **Java 25** Toolchain, offizielle Mojang-Mappings
  (MC seit 26.1 unobfuskiert → kein mappings(...)), Meteor `26.2-SNAPSHOT`,
  optionaler Soft-Dependency auf Baritone `26.2-SNAPSHOT`, Gson.
- **Quellpfad:** src/main/java/com/stashhunter/stashhunter/
- **Build:** ./gradlew build (Windows: gradlew.bat build). Benötigt JDK 25
  (Foojay-Resolver lädt es ggf. nach) und Netzwerk für die Meteor/Baritone-Snapshots.
- **Wichtiger Hinweis:** Ich konnte hier **nicht** bauen (kein JDK 25 auf dem PATH,
  Meteor-Artefakte nicht im lokalen Gradle-Cache). Alle Zeilenangaben stammen aus
  Code-Lektüre. Vor jedem Fix die genannte Stelle noch einmal lesen und nach einem
  Build ./gradlew build laufen lassen.

## Bereits erledigt (nicht erneut machen)

- StuckDetector: onDeactivate() stellt ElytraFly wieder her, wenn es mitten in der
  Auto-Fix-Sequenz deaktiviert wurde; Fix-Zustand wird zurückgesetzt.
- StuckDetector: Baritone-„Stuck“-Trigger feuert nur noch bei
  Config.useBaritonePathing && BaritoneBridge.isElytraPathingReady().
- StashHunterModule.onActivate(): setzt elytraBrokenTicks, wasElytraBroken,
  reEngagingElytraTicks, tickCounter zurück und gibt den Jump-Key frei.

---

## Priorisierung (empfohlene Reihenfolge)

### Phase 0 — Sofort & risikoarm (Quick Wins, < 1 h gesamt)

1. **Toten Mixin/Event entfernen.**
   - PlayerInventoryAccessor.java wird nirgends genutzt → löschen und aus
     addon-template.mixins.json entfernen ("PlayerInventoryAccessor"). Entfernt
     gleichzeitig ein Boot-Crash-Risiko (defaultRequire: 1).
   - LivingEntityMixin.java: LivingEntity#die läuft serverseitig, der Mixin feuert
     clientseitig vermutlich nie und loggt nur „Player death mixin called!“ (Zeile 20).
     Entfernen oder durch einen echten Client-Death-Hook ersetzen.
   - PlayerDeathEvent.java hat **null Subscriber** (nur gepostet, nie konsumiert).
     Entfernen, oder — falls der Teardown in Phase 1 gebaut wird — dort anbinden.

2. **Unerreichbaren Code & doppelte Logik aufräumen.**
   - NewerNewChunks.java:405,557-587: der !saveDataWasOn-Block ist unerreichbar
     (onActivate setzt saveDataWasOn = save.get()) → löschen oder Logik korrigieren.
   - StashHunterModule.java:672-673,690: beide Ternary-Zweige identisch
     ("Stash Found" : "Stash Found", "Stash Found!" : "Stash Found!") → vereinfachen.

3. **CI/Java-Version angleichen.**
   - .github/workflows/dev_build.yml und .github/workflows/pull_request.yml nutzen
     java-version: 21, das Projekt verlangt jdk_version=25. Auf 25 setzen.

4. **Config-Validierung aktivieren.**
   - Config.java:206 definiert validate(), wird aber nie aufgerufen. Nach Config.load()
     in StashHunter.onInitialize() (StashHunter.java:36) Config.validate() aufrufen.
   - Config.java:95 min(0) auf min(100) anheben (konsistent mit validate()).

5. **Template-Reste umbenennen.**
   - addon-template.mixins.json → stash-hunter.mixins.json (Referenz in fabric.mod.json
     anpassen).
   - assets/template/icon.png → assets/stash-hunter/icon.png (Pfad in fabric.mod.json).
   - gradle.properties: maven_group=com.example → com.stashhunter.
   - addon-template.mixins.json: compatibilityLevel von JAVA_21 auf JAVA_25 prüfen.

6. **Doku-Drift beheben (README.md).**
   - flight-altitude Default ist im Code 160 (Config.java:24), README nennt 320.
   - README nennt .clearstashes/.clearplayers, der Code registriert
     clear-stashes/clear-players.
   - Help-Texte in StashHunterCommand.java schreiben /stashhunter, tatsächlich
     .stashhunter.
   - gradle.properties mod_version=0.26.2 vs. CHANGELOG v26.2.1 vereinheitlichen.

### Phase 1 — Stabilität / „Bot bleibt am Leben“ (höchster Nutzen)

7. **Tod-/Weltwechsel-Teardown in ElytraController.**
   - Problem: statische Felder (waypoints, currentWaypoint, currentTarget,
     visitedChunks, baritoneTargetPos in ElytraController.java:22-37) überleben
     Respawn/Dimensionswechsel → Bot fliegt nach dem Tod zu alten Koordinaten.
   - Fix: im bestehenden StashHunterModule.onDisconnect (StashHunterModule.java:289)
     zusätzlich ElytraController.stop() aufrufen; zusätzlich den Death-Pfad
     (lastHealth-Poll, StashHunterModule.java:377) um ElytraController.stop() erweitern.
     Idealerweise ein Meteor-GameLeftEvent/World-Change-Event abonnieren.

8. **Elytra kann mitten in der Reparatur brechen.**
   - AutoElytraRepair.java:523-526 Schwelle „Rest-Haltbarkeit ≤ N“ mit Minimum 1; die
     Sequenz braucht ≥ 60 Ticks Flug. Fix: bei getDamageValue() > getMaxDamage()-10
     abbrechen und in jedem SCAFFOLDING/POSITIONING-Tick die Haltbarkeit neu prüfen.

9. **Baritone/Controller-Kampf beenden.**
   - BaritoneImpl.java:42-48: isPathing()/hasPath() fragen getPathingBehavior() ab,
     nicht den Elytra-Prozess → eingebauter Controller und Baritone schreiben
     gleichzeitig Bewegung (ElytraController.java:588, 631-647).
   - Fix: BaritoneImpl.isElytraActive() (getElytraProcess().isActive()) ergänzen und
     in ElytraController nur darauf basierend fallen lassen.
   - Zusätzlich: ElytraController.stop() (:100-103) toggelt ElytraFly bedingungslos
     aus — vorher isActive() sichern und wiederherstellen.

10. **Reparatur hängt 300 s ohne XP.**
    - AutoElytraRepair.java:359-410: nur repairTimeout/Damage werden beobachtet.
      Fix: experienceLevel/XP-Flaschen vor Aktivierung prüfen; abbrechen, wenn sich der
      Schaden ~5 s lang nicht verringert.

11. **EMERGENCY_DISCONNECT toggelt das Modul aus dem eigenen Tick heraus.**
    - AutoElytraRepair.java:156-158,445-454: this.toggle() unsubscribt während
      TickEvent.Post läuft; Zustand wird nie zurückgesetzt. Fix: Zustand auf MONITORING
      setzen, toggle() einen Tick verzögern, getConnection() == null absichern.

### Phase 2 — Threading & Performance (Hauptursache für Hänger/Stutter)

12. **Netty-Thread-Arbeit in NewerNewChunks beenden.**
    - NewerNewChunks.java:658,662,681,693,716: PacketEvent.Receive läuft auf dem
      Connection-Thread; dort newChunks.add, mc.level.getBlockState, Datei-Writes.
      Fix: Treffer sammeln und per mc.execute(...) auf dem Client-Thread anwenden.

13. **Chunk-Paket async+join entfernen.**
    - NewerNewChunks.java:716-720: CompletableFuture.runAsync(...).join() auf dem
      Packet-Buffer (Race + sinnloser Context-Switch). Synchron dekodieren oder Buffer
      kopieren. taskExecutor (:276) ist statisch und wird nie heruntergefahren.

14. **Datei-Writes batchen.**
    - NewerNewChunks.java:964-978 (aufgerufen u. a. bei 671,687,700,869,881,893,905,922):
      pro Treffer eine open-write-close auf dem Netzwerk-Thread. Buffered Writer +
      Batch-Flush bei Disable/World-Leave/Dimensionswechsel.

15. **Render-/Trail-Loop-Allokationen.**
    - NewerNewChunks.java:595-649: 5 Set-Iterationen pro Frame, pro Chunk 2 Vec3 +
      AABB + BlockPos; getNewChunks() (:393-395) kopiert pro Tick das ganze Set (wird
      in ElytraController.java:494 zweimal pro Tick aufgerufen).
    - ElytraController.java:376-445: O(n³)-Triple-Loop in analyzeLinearPattern,
      analyzeDirectionalPattern sortiert die Liste des Callers in-place.
    - Fix: Snapshot cachen (Refresh alle N Ticks), Quadratdistanz auf Ints, Boxen cachen.

16. **scanForBlocks-Kosten.**
    - StashHunterModule.java:467-479,629-651: 17×17 Chunks, pro Position zwei
      Distanzchecks (sqrt/pow), new BlockPos, getBlockState über y=−64…79.
      Fix: Sektionspaletten/Block-Entities iterieren, ein Quadratdistanz-Check mit
      MutableBlockPos, activeBlocks einmal pro Scan cachen.

### Phase 3 — Scan-Korrektheit („findet mehr Stashes“)

17. **Doppelzählung im Full-Scan beheben.**
    - StashHunterModule.java:612-623 + 626-654: Container werden über
      getBlockEntitiesPos() und nochmal im Block-Loop gesammelt → cluster.size(),
      Dichte, Threshold und Report doppelt. Fix: LinkedHashSet<BlockPos> oder
      gefundene Positionen überspringen.

18. **Y-Bereich & Radius aus der Welt ableiten.**
    - StashHunterModule.java:636-639: hart [-64, 80) + 32 Blöcke horizontal →
      Nether-Dach (y≈128), End und Berge werden nie gefunden. Fix:
      mc.level.getMinY()/getMaxY() bzw. Settings, scanRadius respektieren.

19. **Chunk-Erkennung ist evtl. tot.**
    - NewerNewChunks.java:711: getChunk(..., false) == null laut eigenem Kommentar
      nie null → Analyse läuft nie. Bedingung prüfen/korrigieren.

20. **Cluster-/Volumen-Mathematik.**
    - StashHunterModule.java:496-529,535-548: transitives Verketten (bis 30 Blöcke/Link)
      verschmilzt Stashes und kollabiert die Dichte; harter 100-Block-Dedupe-Radius
      verwirft nahe echte Stashes. Fix: Grid/DBSCAN-Clustering mit hartem
      Durchmesser-Cap, Dedupe-Radius als Setting.

21. **Derselbe Stash alle 10 min erneut gemeldet.**
    - StashHunterModule.java:441-444: reportedStashes.clear() bei tickCounter % 12000
      → erneuter @everyone + doppeltes Waypoint. Fix: zeitbasiertes Wipe entfernen (nur
      bei Weltwechsel/clear-stashes), Dedupe über stabilen Key, vorhandene Waypoints
      prüfen.

### Phase 4 — Robustheit / Netzwerk / Config

22. **Discord-Webhook härten.**
    - DiscordWebhook.java:22-47,49-68: unbegrenzte Queue, Response-Code ignoriert
      (204/429/401 = „Erfolg“), kein Retry/Backoff, disconnect() fehlt im Fehlerfall,
      @everyone bei jeder Meldung. Fix: Status/retry_after auswerten, Queue begrenzen,
      @everyone opt-in, allowed_mentions setzen.

23. **Config entkoppeln/debouncen.**
    - StashHunterModule.java:43-234: jede onChanged → Config.save() (Slider = Dutzende
      Datei-Writes auf dem Client-Thread). Config.validate() nie aufgerufen.
      Fix: Meteor-Settings als einzige Persistenz oder Speichern debouncen + nach
      load() klemmen.

24. **AltitudeLossDetector falsche Auslösung.**
    - AltitudeLossDetector.java:54-57,62-73: über den 200-Tick-Cooldown wird gegen ein
      veraltetes lastYPosition verglichen → Fehlauslösung; feuert beim Laufen/Fallen
      und cancelt Baritone bedingungslos (:85). Fix: lastY/lastCheck während Cooldown
      aktualisieren, isFallFlying() fordern, Baritone nur bei
      ElytraController.isActive() canceln.

25. **Robustere Persistenz.**
    - NewerNewChunks.java:942-963,528-535: korrupte Zeile → NumberFormatException
      (nur IOException gefangen) → Load-Retry jeden Tick. Parsing über replaceAll auf
      ChunkPos.toString() ist fragil → "x,z" explizit speichern, pro Zeile
      RuntimeException abfangen und überspringen.
    - Vec3dAdapter.java:24-36: unbekannte Felder werden nicht konsumiert →
      IllegalStateException bricht TripManager.loadTrips ab; fehlende Felder ergeben
      still (0,0,0). Fix: default: in.skipValue(); + Double.isFinite-Check.

26. **justCompleted immer konsumieren.**
    - StashHunterModule.java:447: notifyOnCompletion.get() && justCompleted()
      short-circuited → Flag bleibt bei deaktivierter Einstellung hängen. Fix: immer
      konsumieren, nur den Send gaten.

27. **Command-Start absichern.**
    - StashHunterCommand.java:246-276: startet einen Flug, den ohne aktives Modul
      niemand tickt (ElytraController.onTick nur aus StashHunterModule.java:304).
      Fix: Modul aktivieren bzw. ElytraController selbst an TickEvent hängen.
    - StashHunterCommand.java:264-265: estimatedWaypoints nutzt X-Ausdehnung statt
      Streifenanzahl. Korrekte Formel verwenden.
    - ElytraController.start(..., stripWidth) hat keine Untergrenze → <= 0 =
      Endlosschleife im Waypoint-Generator (ElytraController.java:71-89).

### Phase 5 — Architektur / Wartbarkeit (nach den Bugfixes)

28. **God Classes & statischen Zustand auflösen.**
    - NewerNewChunks (994), StashHunterModule (717), ElytraController (707),
      AutoElytraRepair (590). Reine Logik (Clustering, Waypoint-Generierung,
      Trail-Analyse, Trip-Konvertierung) in testbare Klassen extrahieren.

29. **Statischen Modul-Lookup im Konstruktor entfernen.**
    - ElytraController.java:21: NewerNewChunks wird beim Class-Load in static final
      geholt → kann dauerhaft null sein. Pro Aufruf Modules.get().get(...) mit
      Null-Guard (StashHunterModule.java:268 zeigt das bekannte Ordering-Problem).

30. **Reflection & manuelle Eingaben ersetzen.**
    - AutoElytraRepair.java:539-553,556-588: Reflection auf private Meteor-Interna
      (currentMode, lastForwardPressed) bricht bei jedem Snapshot-Update; Fehler werden
      verschluckt. Meteor-Setting/Settings-API nutzen, Fehler einmal als WARN loggen.
    - KeyMapping.setDown (AutoElytraRepair.java:439,481-482, KeyHold.java:16-49,
      StashHunterModule.java:274-278, StuckDetector.java:190) → Meteor-Input/
      ElytraFly.autoPilot nutzen; KeyHold pro Key refcounten und bei Deaktivierung
      canceln.

31. **Toten Code entfernen & Logging vereinheitlichen.**
    - WorldScanner.java ist zu ~80 % ungenutzt (countBlocks, calculateDensity,
      getDensityRating, getMaxDistance, filterByRadius, createBlockSummary haben keine
      Caller) → löschen oder integrieren. calculateCenter (:99-125) liefert bei leerer
      Liste BlockPos.ZERO → Optional<BlockPos>.
    - Logger.java: nicht threadsafe, hartkodierter Pfad ~/.meteor-client/ statt
      MeteorClient.FOLDER, wird nie geschlossen, kaum genutzt. Richtig anbinden
      (SLF4J/StashHunter.LOG) oder entfernen; 13× printStackTrace() und 8 leere
      catch-Blöcke ersetzen.
    - NewerNewChunks.java nutzt Tabs, der Rest 4 Spaces (.editorconfig) → formatieren.

32. **Build/Test-Infrastruktur.**
    - Spotless/Formatter ergänzen; Unit-Tests (JUnit) für Clustering,
      Waypoint-Generierung, Vec3dAdapter, Trip-Serialisierung.
    - implementation("com.google.code.gson:gson:2.10.1") in build.gradle.kts auf
      compileOnly umstellen oder entfernen (MC bündelt Gson bereits).
    - Dependabot/Renovate für die beweglichen 26.2-SNAPSHOT-Abhängigkeiten.

---

## Verifikation

- Nach jeder Änderung: ./gradlew build (JDK 25 nötig).
- Die drei bereits erledigten Fixes sind bereits committed (Branch `code-revision`,
  Commit `adb7235` „Reset module state and restore Elytra on disable“). Dieser Plan
  (PLAN.md) ist neu und noch untracked → vor Weiterarbeit `git add PLAN.md` + Commit.
- Funktionsprüfung in-game: Modul an/aus, Death/Respawn, Dimensionswechsel,
  /stashhunter start + stop, Stuck-Auto-Fix, Elytra-Reparatur.
