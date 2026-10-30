# Handoff an Claude Code — NewerNewChunks: End-OldChunk-Detektor über Biome statt End-Stone

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

> **Status (2026-10-30): eingebaut** wie unten beschrieben (Schritte 1–5, ohne Abweichung).
> Offen: Build (`gradlew.bat build`) und In-game-Test der Akzeptanzkriterien.
>
> **Umsetzung** in `NewerNewChunks.java`:
> - Imports `Holder`, `Biome`, `Biomes` und die Konstante `END_MAIN_ISLAND_CHUNK_RADIUS_SQ` ergänzt.
> - End-Block in `classifyChunk` durch die Biome-Prüfung ersetzt. `try/catch`-Fallback entfernt.
> - Beschreibung von `endOldChunksDetector` angepasst.
> - CHANGELOG (*Fixed*) und PLAN.md ergänzt. PLAN-Punkt 52 („Allocation im End-Detektor“) ist
>   damit hinfällig, der `BlockPos`-Loop existiert nicht mehr.
>
> Ein kompletter Compile war nicht möglich: Loom kommt nicht an `piston-meta.mojang.com`,
> und die Meteor-Jar fehlt im Cache. Die Minecraft-Aufrufe sind per `javap` geprüft.

## Kontext

- **Projekt:** Stash-Hunter (Meteor-Client-Addon, Fabric, Minecraft 26.2, Java 25, Mojang-Mappings).
- **Repo-Root:** `C:\Users\Student\Desktop\stasdhunter addon\Stash-Hunter`
- **Betroffene Datei:** `src/main/java/com/stashhunter/stashhunter/modules/NewerNewChunks.java`,
  Methode `classifyChunk(...)`, Block `if (endOldChunksDetector.get() && level.dimension() == Level.END ...)`
  (aktuell ca. Zeile 767–790; vor dem Fix per Suche nach `endOldChunksDetector` lokalisieren).

## Problem

Die Einstellung `Pre 1.13 End OldChunk Detector` verspricht laut Beschreibung, Chunks mit dem
Biome `minecraft:the_end` zu erkennen. Der Code macht stattdessen das hier:

```java
BlockState surfaceBlock = chunk.getBlockState(new BlockPos(x, 64, z));
if (surfaceBlock.getBlock() == Blocks.END_STONE) hasOldEndStructure = true;
...
} catch (Exception e) {
    isOldGeneration = true; // "assume old generation"
}
```

Damit gilt **jeder** End-Chunk, der auf y=64 irgendwo End Stone hat, als „alte Version“. Das trifft
praktisch alle Insel-Chunks, egal wann sie generiert wurden. Ein Fehler beim Prüfen markiert den
Chunk ebenfalls als alt.

**Folgen:**
- Im End färbt NewerNewChunks fast alles gelbgrün („old-version“). Für die Trail-/Stash-Suche im
  End ist die Erkennung damit wertlos.
- Der neue `old-chunk-notifier` (Standard `include-old-generation = on`) meldet im End den ersten
  Insel-Chunk als „Old-version chunk“, also eine Falschmeldung an Discord.
- Die Einträge landen dauerhaft in `OldGenerationChunkData.txt`.

## Hintergrund (warum Biome funktioniert)

- Vor 1.13 hatte das ganze End das Biome `the_end` (die äußeren Inseln eingeschlossen).
- Seit 1.13 haben die äußeren Inseln `end_highlands`, `end_midlands`, `small_end_islands` oder
  `end_barrens`. Nur die Hauptinsel behält `the_end`, und zwar für Chunks mit
  `chunkX² + chunkZ² <= 4096` (64 Chunks ≈ 1024 Blöcke, siehe vanilla `TheEndBiomeSource`).
- Seit 1.18 schickt der Server die Biome-Palette im Chunk-Paket mit. `classifyChunk` dekodiert
  das Paket schon in eine private `LevelChunk`-Kopie (`replaceWithPacketData`), die Biome sind
  darin also enthalten.

**Daraus folgt:** Ein End-Chunk **außerhalb** des Hauptinsel-Radius, der das Biome `the_end` enthält,
wurde vor 1.13 generiert.

## Fix

APIs per `javap` gegen `~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar` geprüft:
- `ChunkAccess.collectBiomesInPalette(Set<Holder<Biome>>)`
- `Holder.is(ResourceKey<T>)`
- `Biomes.THE_END` (`net.minecraft.world.level.biome.Biomes`)

### 1. Imports ergänzen

```java
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
```

### 2. Konstante ergänzen (bei den anderen statischen Feldern)

```java
// Vanilla TheEndBiomeSource: chunks with x² + z² <= 4096 belong to the main island, which is
// minecraft:the_end in every version. Only outside of it does the_end mean pre-1.13 terrain.
private static final long END_MAIN_ISLAND_CHUNK_RADIUS_SQ = 4096L;
```

### 3. Den End-Block in `classifyChunk` komplett ersetzen

```java
if (endOldChunksDetector.get() && level.dimension() == Level.END && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL) && !chunk.isEmpty()) {
    long cx = oldpos.x(), cz = oldpos.z();
    if (cx * cx + cz * cz > END_MAIN_ISLAND_CHUNK_RADIUS_SQ) {
        Set<Holder<Biome>> biomes = new HashSet<>();
        chunk.collectBiomesInPalette(biomes);
        for (Holder<Biome> biome : biomes) {
            if (biome.is(Biomes.THE_END)) {
                isOldGeneration = true;
                break;
            }
        }
    }
}
```

Die `try/catch`-Fallback-Logik („assume old generation“) **ersatzlos streichen**. Ein Fehler darf
keinen Chunk als alt markieren (vgl. den Kommentar beim Dekodieren: „a half-decoded chunk would
only produce false positives“).

### 4. Beschreibung der Einstellung anpassen

`endOldChunksDetector` → `.description(...)`:
„Marks chunks outside the main End island as generated in an old version (pre 1.13) if they have
the biome minecraft:the_end.“

### 5. Doku

- `CHANGELOG.md` → `[Unreleased]` → `### Fixed`: ein Eintrag (End-Detektor markierte jeden
  Insel-Chunk mit End Stone auf y=64 als alte Version; jetzt Biome-Prüfung außerhalb der Hauptinsel).
- `PLAN.md`: kurzer Erledigt-Vermerk analog zu Punkt 60.

## Nicht Teil dieser Aufgabe (nur notieren)

- Im End kann `isNewChunk` nie `true` werden: Der Prozentzweig im PaletteExploit-Block schließt
  NETHER/END aus, und `bewlian` ist im End gleich `isNewChunk`. Neue Chunks im End werden also
  nie als NEW erkannt. Ob das gewollt ist, muss separat geklärt werden.
- Der „PaletteExploit“ ist nur eine Heuristik (Anzahl verschiedener Blocktypen in Stichproben),
  nicht die echte Paletten-Reihenfolge-Analyse. Das ist eine größere eigene Baustelle.
- Bereits gespeicherte Falschtreffer in `OldGenerationChunkData.txt` bleiben stehen. Der Nutzer
  kann sie mit dem Button „DELETE CHUNK DATA“ (pro Dimension) entfernen. Ein Hinweis im CHANGELOG reicht.

## Akzeptanzkriterien

- Hauptinsel (|chunk| ≤ 64) wird **nie** als old-version markiert.
- Äußere Inseln in einer frisch erstellten 26.2-Welt werden **nicht** als old-version markiert.
- Äußere Inseln mit Biome `the_end` (z. B. 2b2t-End weit draußen) **werden** markiert.
- Kein `try/catch`-Fallback mehr, der Chunks als alt markiert.
- Build grün: `gradlew.bat build`.

## Hinweis zur Build-Umgebung

In der Sandbox der vorigen Session war `piston-meta.mojang.com` nicht erreichbar, Loom bricht dort
mit „Failed download after 3 attempts“ ab. Die Minecraft-Jar liegt aber im Loom-Cache, damit
lassen sich APIs per `javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar <Klasse>`
prüfen. Den Build in einer Umgebung mit Netzzugang ausführen.
