# Handoff an Claude Code — OldChunkNotifier: Dimensionswechsel-Reset

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
