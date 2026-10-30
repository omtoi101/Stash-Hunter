# Handoff an Claude Code — OldChunkNotifier: Dimensionswechsel-Reset

> ## ⚠️ ZUERST ERLEDIGEN — vor jeder anderen Arbeit (Stand 2026-10-30)
>
> **Der Build ist seit mehreren Runden nicht mehr verifiziert.** Mehrere Änderungen
> (End-Detektor in NewerNewChunks, OldChunkNotifier-Dimensionsreset, neues Modul
> `activated-spawner-detector`) sind nur einzeln mit `javac`/`javap` geprüft und
> **nie mit `./gradlew build` gebaut**. Spotless und Unit-Tests sind dabei nicht gelaufen.
> Erst wenn der folgende Punkt erledigt ist, darf weitergearbeitet werden.
>
> 1. **In einem Netz ohne Mojang-Sperre bauen** (zu Hause oder per Handy-Hotspot, nicht im
>    Schulnetz):
>    ```bash
>    ./gradlew build
>    ```
>    Muss grün sein (JDK 25, inkl. Spotless + Unit-Tests). Bei Fehlern **diese zuerst
>    beheben**, bevor neue Features angefasst werden.
> 2. **Warum es hier nicht ging:** Das Schulnetz setzt jede Verbindung zu Mojang zurück
>    (`piston-meta.mojang.com`, `launchermeta.mojang.com`, `piston-data.mojang.com`,
>    `libraries.minecraft.net`). Loom braucht beim Konfigurieren das Versions-Manifest von
>    dort, `--offline` hilft nicht. Das gecachte Manifest
>    (`~/.gradle/caches/fabric-loom/mojang_versions_manifest.json`) wurde beim ersten
>    fehlgeschlagenen Cache-Rebuild gelöscht, und eine verwaiste Lock-Datei
>    (`~/.gradle/caches/fabric-loom/.bca99….lock`, Inhalt „disowned“) wurde entfernt.
>    **Der nächste Build braucht also auf jeden Fall Zugriff auf Mojang.** Auch das
>    Meteor-Jar ist auf diesem Rechner nicht im Gradle-Cache.
> 3. **Was bisher als Ersatz geprüft wurde:** `ActivatedSpawnerDetector.java` kompiliert
>    isoliert fehlerfrei gegen `minecraft-merged-deobf-26.2.jar` und das Meteor-26.2-Snapshot-
>    Jar (`26.2-20261009.074120-35`). Dabei wurde ein echter Fehler gefunden und behoben:
>    Die Spawner-Konstanten liegen in 26.2 in `BlockEntityTypes`, nicht in `BlockEntityType`.
>    Spotless-Regeln (keine Tabs, kein Trailing-Whitespace, Newline am Ende) nur von Hand
>    geprüft.
> 4. **Danach in-game testen:** `activated-spawner-detector` erscheint in der Kategorie
>    „Stash-Hunter“ und lässt sich an/aus schalten; Dungeon/Mineshaft/Trial-Chamber mit
>    aktiviertem Spawner werden gemeldet und gerendert. Ebenso End-Detektor und
>    OldChunkNotifier (siehe CLAUDE-HANDOFF.md / DSH-HANDOFF.md).
> 5. **Erst dann committen.** Noch nichts ist committet. Die untracked Dateien
>    `_ref_ActivatedSpawnerDetector.java.txt`, `_ref_AreaLoader.java.txt`,
>    `_ref_Firework.java.txt` und `_ref_GrimDuraFirework.java.txt` im Repo-Root sind
>    Referenzkopien und gehören **nicht** in den Commit.

> **Status (2026-10-30): eingebaut.** Offen: Build (`gradlew.bat build`) und In-game-Test.
>
> **Umsetzung** in `OldChunkNotifier.java`:
> - Feld `lastDimension` + Imports `ResourceKey`/`Level` wie unten beschrieben.
> - Prüfung in `onTick` direkt nach dem null-Guard und dem Cooldown-Countdown.
> - `reset()` setzt `lastDimension` mit auf null (läuft bei Aktivierung und `GameLeftEvent`).
>
> **Abweichung von Schritt 3:** Beim Dimensionswechsel wird **nicht** `reset()` aufgerufen,
> sondern nur `lastChunk = null` und `inOldArea = false`. `reset()` würde auch den Cooldown
> auf 0 setzen, dann gäbe jeder Gang durchs Portal (auf beiden Seiten alte Chunks) eine neue
> Discord-Meldung.
>
> **Optional Tod/Respawn: bewusst nicht umgesetzt.** Respawn passiert meist in derselben
> Dimension. Der nächste Übergang neu→alt wird ohnehin normal gemeldet.
>
> Mitbehoben in derselben Runde: End-Detektor in NewerNewChunks (siehe DSH-HANDOFF.md).

## Kontext

- **Projekt:** Stash-Hunter (Meteor-Client-Addon, Fabric, Minecraft 26.2, Java 25).
- **Repo-Root:** `C:\Users\Student\Desktop\stasdhunter addon\Stash-Hunter`
- **Betroffenes Modul (neu):**
  `src/main/java/com/stashhunter/stashhunter/modules/OldChunkNotifier.java`
  (bereits in `StashHunter.java` Zeile 45 registriert.)

Das Modul meldet (Chat + Discord), wenn der Spieler in einen Chunk fliegt, den
`NewerNewChunks` als „alt" markiert hat. Nur der **Übergang** wird gemeldet; ein
zusammenhängender Alt-Bereich erzeugt genau eine Meldung (Zustand `inOldArea` +
`cooldown`).

## Aufgabe

Fehlenden **Reset bei Dimensionswechsel** ergänzen (optional zusätzlich bei Tod/Respawn).

## Problem

- `GameLeftEvent` feuert **nicht** bei einem Dimensionswechsel (nur beim Verlassen von
  Server/Welt). `OldChunkNotifier.reset()` läuft bei einem Dimensionswechsel also nicht.
- `ChunkPos` ist nur `(x, z)`. Wechselt man in eine Dimension, in der der Chunk zufällig
  dieselben `(x, z)` hat, liefert `chunk.equals(lastChunk)` in `onTick` `true` → das Modul
  macht **gar nichts** mehr (early return).
- Ein veraltetes `inOldArea = true` aus der alten Dimension **unterdrückt** die erste
  Alt-Chunk-Meldung in der neuen Dimension.

## Fix

Das Muster existiert bereits in `StashHunterModule` (Feld `lastDimension`, Zeilen 282 und
393–398): `ResourceKey<Level>` tracken und bei Änderung zurücksetzen.

### 1. Imports ergänzen (OldChunkNotifier.java)

```java
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
```

### 2. Feld ergänzen

```java
private ResourceKey<Level> lastDimension;
```

### 3. In `onTick` nach dem null-Guard die Dimension prüfen

Vor dem `ChunkPos chunk = mc.player.chunkPosition();` einfügen:

```java
ResourceKey<Level> dim = mc.level.dimension();
if (lastDimension != null && !dim.equals(lastDimension)) reset();
lastDimension = dim;
```

### 4. In `reset()` das Feld mit auf null setzen

```java
private void reset() {
    lastChunk = null;
    lastDimension = null;
    inOldArea = false;
    cooldownTicks = 0;
}
```

## Optional: Reset bei Tod/Respawn

Im Projekt gibt es **kein** Death-Event mehr (der frühere `PlayerDeathEvent` war tot und
wurde entfernt). Tod/Respawn wird in `StashHunterModule` (Zeilen 457–487) über
`mc.player.getHealth()`-Tracking erkannt. Für `OldChunkNotifier` ist das weniger kritisch
(Tod i. d. R. in derselben Dimension), kann aber nach demselben Muster ergänzt werden, falls
gewünscht.

## Akzeptanzkriterien

- Dimensionswechsel (z. B. Overworld → Nether) setzt den Zustand zurück; die erste
  Alt-Chunk-Meldung in der neuen Dimension funktioniert wieder.
- Kein doppeltes Zurücksetzen beim normalen Spielen in derselben Dimension.
- Build grün: `gradlew.bat build`.

## Hinweis zur Build-Umgebung

In der aktuellen Umgebung ist **kein Netzzugang** zu den Minecraft-/Loom-Artefakten
möglich (Loom bricht mit „Failed download after 3 attempts" ab). `compileJava`/`build`
daher ggf. in einer Umgebung mit Netzzugang ausführen. Die statische API-Prüfung des Moduls
ist sauber — alle aufgerufenen Methoden (`isOldChunk`, `isOldGenerationChunk`,
`isBeingUpdatedChunk`, `isNewChunk`, `DiscordWebhook.sendMessage`, `DiscordEmbed`,
`dimension().identifier().getPath()`) existieren im Projekt.

---

# Handoff an Claude Code — Baritone-Integration verbessert (Cancel-Fix + Landungs-Timeout)

> **Status (2026-10-30): eingebaut und gebaut.** `gradlew.bat build` ist auf dieser Maschine
> **grün** (compileJava, spotlessCheck, Unit-Tests) — damit ist die alte „Build nicht
> verifiziert“-Warnung oben überholt. Der In-game-Test der beiden Punkte steht noch aus.

## Was geändert wurde

1. **`BaritoneImpl.cancel()` beendet den Elytra-Flug jetzt wirklich.**
   - Vorher: `getElytraProcess().resetState()` + `cancelEverything()`. `resetState()` ist laut
     Baritone-API **kein** Cancel — es behält das Ziel und pfadet erneut („will try to keep
     flying“). Jeder Stop hat den Flug also erst abgerissen, **neu gestartet** und erst danach
     gecancelt (inkl. verschwendeter Nether-Pathfinder-Neuberechnung).
   - Nachher: nur noch `primary().getPathingBehavior().cancelEverything()`.
   - Nachweis im Bytecode der echten Baritone-26.2-Jar: `cancelEverything()` →
     `PathingControlManager.a()` → `onLostControl()` auf dem Elytra-Prozess → räumt den
     nativen Nether-Pathfinder-Kontext und das Flug-Verhalten ab.

2. **Landungs-Timeout in `ElytraController`.**
   - Neu: Feld `landingStartTime` (long) + Konstante `LANDING_TIMEOUT_MS = 120_000` (2 Minuten).
   - Hängt Baritone bei `land-after-completion` fest (findet keinen sicheren Landeplatz), wird
     nach 2 Minuten abgebrochen: Baritone gecancelt, Chat-Meldung
     „Landing timed out - staying airborne.“. Sonst blieben AltitudeLossDetector und
     AutoFirework dauerhaft stumm (PLAN-Punkt 86 „Offen (low)“).
   - Reset/Zeitstempel an allen Stellen: Felder (L50–57), `startWith` (L95), `stop` (L116),
     `resume` (L173), Landungs-Start in `onTick` (L269), Timeout-Check (L239–251).

## Betroffene Dateien

- `src/main/java/com/stashhunter/stashhunter/baritone/BaritoneImpl.java`
- `src/main/java/com/stashhunter/stashhunter/utils/ElytraController.java`
- `CHANGELOG.md` (zwei Einträge unter `### Fixed`)

## Akzeptanzkriterien

- `.stashhunter stop` / Dimensionswechsel / AltitudeLoss-Recovery beendet den Baritone-Elytra-Flug
  tatsächlich (kein Re-Path/Re-Arm mehr).
- Festsitzende Landung bricht nach 2 min ab und reaktiviert AltitudeLossDetector/AutoFirework.
- Build grün: `gradlew.bat build`.

## ⚠️ Parallele Arbeit beobachtet

Während der Arbeit tauchten fremde, uncommittete Änderungen auf, die **nicht** von dieser
Aufgabe stammen und **nicht angefasst** wurden: `StashHunterModule.java` (modifiziert), neue
Dateien `StashBlockIndex.java` + `StashBlockIndexTest.java` und ein CHANGELOG-Eintrag
„Stashes on the border between two flight strips…“. Vor dem Commit klären, ob das parallel
geschehen ist.

> **Geklärt (2026-10-30):** Die oben als „parallel“ markierten Änderungen stammen aus einer
> zweiten Claude-Code-Sitzung (Überarbeitung des Stashfindens) und sind gewollt:
> `StashBlockIndex` (+Test), `StashGrowth` (+Test), Umbau von `scanForBlocks`/`processCluster`/
> `reportStash` in `StashHunterModule` und zwei CHANGELOG-Einträge („Stashes on the border…“,
> „A stash first seen only in part…“). `gradlew build` grün (inkl. beider Arbeiten), in-game
> ungetestet. In-game prüfen: Stash auf einer Streifengrenze wird gemeldet; ein zuerst halb
> gesehener Stash bekommt eine „Stash Updated“-Meldung und einen verschobenen Waypoint.
