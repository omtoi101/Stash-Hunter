# Flug & Komfort — Design

**Stand:** 2026-11-28. Fünfte Etappe des Releases 26.3.0 auf Branch `port/26.3` (nach Port,
Signal-Score, Struktur-Locator, Auswertung). Deckt PLAN-Punkte 64 (AutoPortal), 65 (Schilder
aufzeichnen) und 66 (Pitch40, AFK-Vanilla-Fly) ab. Gestrichen: 61 (ElytraFly++/Bounce, für
Nether-Highways statt zum Scannen) und „Highlight Old Lava“ (deckt `NewerNewChunks` ab).
ElytraSwap gibt es schon als `swap-to-spare` in `auto-elytra-repair`.

## Warum

Bei Testflügen von etwa 20 Minuten:

- **Raketen waren sehr schnell leer.** Ursachen im Code: Der Mod setzt keine Baritone-Optionen, also
  gelten Baritones Defaults (`elytraConserveFireworks = false`, `elytraFireworkSpeed = 1.2` Blöcke/Tick
  ≈ 24 B/s: im waagerechten Streifenflug zündet Baritone praktisch ständig). `auto-firework` zündet
  unter `min-speed` 20 B/s alle `use-delay` 40 Ticks, grob 400-600 Raketen in 20 Minuten.
- **Landen mit Baritone war nicht konsistent.**
- **Starten nach einer Landung war unzuverlässig; manchmal hing der Bot und startete nicht mehr von
  allein.** Baritones `elytraAutoJump` ist standardmäßig aus; ohne Kante kommt Baritone vom flachen
  Boden schlecht hoch. Der Mod versucht drei Neustarts, wartet aber, wenn ein Schritt nie endet,
  unter Umständen unbegrenzt.

Ziel des Nutzers: längere Flüge ohne Eingreifen.

## Ziele

- Deutlich weniger Raketenverbrauch, ohne den Flug unsicher zu machen.
- Landen und Starten über Baritone verlässlicher durch passende Baritone-Optionen.
- **Kein stilles Hängenbleiben mehr:** Ein Wächter erkennt fehlenden Fortschritt in jeder Flugphase,
  versucht es erneut und pausiert sonst mit Chat- und Discord-Meldung (Trip gespeichert).
- Pitch40 als raketensparender Reisemodus für lange Flüge.
- Schilder-Texte werden aufgezeichnet.
- AutoPortal: Nether-Portal bauen und betreten.

## Nicht-Ziele

- Keine eigene Lande- oder Startroutine statt Baritone (Ansatz A, nicht gewählt). Ohne Baritone
  bleibt es beim heutigen Verhalten (Reparatur-Sinkflug, ElytraFly).
- Kein Ausbau des eingebauten ElytraFly-Reglers.
- Kein Nether-Highway-Flug, kein Bounce.

## Anti-Ziele

- Der Bot stürzt ab oder landet in Lava/Wasser, weil Raketen gespart werden.
- Baritone-Optionen des Nutzers bleiben nach dem Flug verändert.
- Der Wächter greift bei normalem Flug ein (Fehlalarme) oder macht Endlosschleifen aus Neustarts.

## Ansätze

| Ansatz | Bewertung |
|---|---|
| A: Eigene Lande-/Startroutine, Baritone nur zum Reisen | Unzuverlässige Teile im eigenen, überwachten Code; spürbarer Umbau von `ElytraController`/`AutoElytraRepair`. Nicht gewählt. |
| **B: Baritone behalten, besser einstellen und überwachen** (gewählt) | Baritone-Optionen passend setzen, Wächter gegen Hänger, Pitch40 als zusätzlicher Modus. Kleiner; Baritones Landeverhalten bleibt eine Blackbox, Probleme werden seltener, aber nicht unmöglich. |
| C: Ganz ohne Baritone fliegen | Ein Flugsystem, aber kein Ausweichen in niedriger Höhe. Nicht gewählt. |

Der Nutzer hat **B** gewählt.

## Design

### Baritone-Optionen während des Flugs

- Beim Start eines Stash-Hunter-Flugs (und bei Fortsetzen, Neustart nach Landung) setzt der Mod über
  `BaritoneBridge` eigene Werte, merkt sich die vorherigen und stellt sie beim Ende des Flugs (Stop,
  Pause, Abschluss, Disconnect, Spielende per Shutdown-Hook) wieder her:
  - `elytraConserveFireworks = true`
  - `elytraFireworkSpeed` niedriger als Default (Setting `baritone-firework-speed` in `stash-hunter`)
  - `elytraAutoJump = true` (Start vom Boden)
  - `elytraMinFireworksBeforeLanding` = `low-firework-threshold` des Mods, damit Baritone und Mod
    dieselbe Schwelle haben
  - `elytraAllowEmergencyLand = true`
- Setting `manage-baritone-settings` (Default an); aus = der Mod fasst Baritones Optionen nicht an.
- Gilt nur, wenn Baritone installiert ist und `use-baritone-pathing` an ist.

### Wächter (Flight Watchdog)

- Eigene Klasse, die pro Tick den Flugzustand prüft. Für jede Phase gibt es ein Fortschrittskriterium
  und eine Frist:
  - **Reisen:** Abstand zum aktuellen Waypoint nimmt ab, oder der Waypoint wurde gewechselt.
  - **Landen:** Höhe nimmt ab oder der Spieler steht am Boden (bestehendes 2-min-Timeout geht hier auf).
  - **Starten nach Landung:** der Spieler ist in der Luft und gleitet (`isFallFlying`) und hat Höhe
    gewonnen.
  - **Reparatur:** bestehende Logik bleibt; der Wächter beobachtet nur, dass `AutoElytraRepair` nicht
    in einem Zustand stehen bleibt.
- Eskalation: 1. Baritone-Ziel neu setzen bzw. Start erneut auslösen; 2. nach weiteren Fehlschlägen
  (Anzahl einstellbar, Default 3 pro Phase) Flug pausieren, Trip speichern, Chat + Discord
  („Flight stuck while <Phase> - paused“). Nie Endlosschleifen: Zähler pro Phase, zurückgesetzt nach
  echtem Fortschritt.
- Ersetzt die verstreuten Einzel-Timeouts nicht sofort, sondern ergänzt sie; doppelte Logik darf
  der Umsetzende zusammenführen, wenn das Verhalten gleich bleibt.

### Pitch40 als Reisemodus

- Setting `cruise-mode` in `stash-hunter`: `baritone` (Default, wenn installiert), `pitch40`,
  `elytrafly` (heutiger eingebauter Regler, Default ohne Baritone).
- **Pitch40:** Der Bot steigt per Raketen auf `pitch40-altitude` (Default 320, über dem Gelände) und
  fliegt dann rein mit Vanilla-Gleitphysik: abwechselnd Sinkflug zum Beschleunigen und Steigflug mit
  −40° Pitch. Gezündet wird nur, wenn er unter `pitch40-min-altitude` fällt. Steuerung Richtung
  Waypoint über Yaw. Clean-room (Idee aus jeff mod, kein Code).
- Landen, Reparatur und Raketen-knapp verhalten sich in Pitch40 wie heute (mit Baritone über
  Baritone, sonst Reparatur-Sinkflug).
- Der Scan deckt die volle Welthöhe ab; die große Höhe schadet nicht. Der Chat warnt, wenn
  `scan-radius` größer ist, als der Server Chunks schickt (bestehende Warnung).

### `auto-firework` klüger

- Zündet nur, wenn Tempo **und** Höhe sinken (Höhenverlust über mehrere Ticks), nicht allein wegen
  `min-speed`. Neues Setting `min-altitude-loss` (Blöcke in der Prüfspanne). Default-Werte so, dass
  im normalen Gleitflug deutlich seltener gezündet wird.
- Weiter pausiert, solange Baritone fliegt oder Pitch40 aktiv ist (Pitch40 zündet selbst).

### Schilder aufzeichnen

- Neues Modul `sign-logger` (Default aus): beschriebene Schilder, die der Scan sieht (Signal-Score
  liest sie bereits), werden pro Server mit Position, Dimension, Text vorn/hinten und Datum in eine
  Datei geschrieben (`AppendFiles`), jedes Schild einmal. Befehl `.sh signs [text]` sucht darin.
  Clean-room (Idee aus Stardust, kein Code).

### AutoPortal

- Neues Modul `auto-portal` (Default aus): baut vor dem Spieler aus Obsidian im Inventar einen
  Nether-Portalrahmen (kleinste Form), zündet ihn mit Feuerzeug und läuft hinein. Abbruch mit Meldung,
  wenn Obsidian oder Feuerzeug fehlen oder kein Platz ist.
- Laut PLAN-Lizenzregel ist PathSeekers AutoPortal (von xqyet) übernehmbar. Vor der Übernahme
  prüft der Umsetzende den Dateikopf; nur wenn dort keine fremde Herkunft steht, wird portiert
  (Attribution im Klassenkopf), sonst clean-room.

### Präzisierungen (aus der Gutachter-Prüfung, eigene Entscheidungen)

- **Korrektur zum „Warum“:** `ElytraController.initiateFlight()` setzt `elytraAutoJump` bereits
  (sichern, einschalten, wiederherstellen). Das bleibt **immer** so, unabhängig von
  `manage-baritone-settings`; der Schalter betrifft nur die neuen Optionen (Raketen-Sparmodus,
  Zünd-Geschwindigkeit, Landeschwelle, Notlandung).
- **Aufgeben in der Luft:** Gibt der Wächter auf, während der Spieler fliegt, wird nicht einfach
  gestoppt, sondern sicher gelandet und erst am Boden pausiert:
  - Hängt das Reisen: mit Baritone `landSafely()` (Landung kurz voraus), ohne Baritone der sichere
    Sinkflug aus `AutoElytraRepair` (ElytraFly Vanilla + Sneak, gleitet über unsicherem Boden weiter).
  - Hängt die Baritone-Landung selbst: Wechsel auf den sicheren Sinkflug, statt „staying airborne“.
  - Nach Abschluss des Scans (kein Trip mehr zum Pausieren): gleiche Landung, danach nur Meldung.
  - Der Sinkflug wird dafür aus `AutoElytraRepair` in eine gemeinsam nutzbare Klasse gezogen
    (Verhalten der Reparatur unverändert).
- **`cruise-mode` ersetzt `use-baritone-pathing`.** Migration: bestehender Wert `true` → `baritone`,
  `false` → `elytrafly`. Zuständigkeiten:

  | | Reisen | Start vom Boden | Landen (Raketen knapp, Scan-Ende) | Baritone-Optionen verwaltet |
  |---|---|---|---|---|
  | `baritone` | Baritone | Baritone (Auto-Jump) | Baritone `landSafely()` | ja |
  | `pitch40` mit Baritone | Pitch40 | Baritone (Auto-Jump), Übergabe an Pitch40 auf Höhe | Baritone `landSafely()` | ja |
  | `pitch40` ohne Baritone | Pitch40 | ElytraFly-Auto-Takeoff wie heute, dann Steigen per Rakete | sicherer Sinkflug, danach Pause | – |
  | `elytrafly` mit Baritone | ElytraFly | wie heute | Baritone `landSafely()` | ja |
  | `elytrafly` ohne Baritone | ElytraFly | wie heute | wie heute (kein Raketenbedarf) | – |

  `ElytraController.useBaritone()` wird dafür in getrennte Entscheidungen aufgeteilt (Reisen, Start,
  Landen).
- **Raketen knapp: der Mod führt.** Baritones `elytraMinFireworksBeforeLanding` wird auf
  `max(1, low-firework-threshold − 2)` gesetzt, sodass normalerweise der Mod zuerst landet. Landet
  Baritone trotzdem selbst (Notlandung), erkennt der Mod das (Baritone inaktiv, am Boden, Raketen
  ≤ Schwelle + 2) als geplante Landung: Trip pausieren, kein Neustart, kein Wächter-Alarm.
- **`sign-logger`** liest beschriebene Schilder selbst aus den geladenen Chunks, solange das Modul an
  ist, unabhängig von Scan und Signal-Schaltern. Das Auslesen des Schildtexts liegt in einer
  gemeinsamen Hilfsklasse, die auch der Signal-Score nutzt.

### Tests

- Unit-Tests: Sichern/Wiederherstellen der Baritone-Optionen (mit Fake-Settings), Wächter-Logik mit
  simulierten Flugverläufen (normaler Flug ohne Fehlalarm, Hänger beim Start, Hänger beim Landen,
  Zähler-Reset), Pitch40-Steuerentscheidungen (wann steigen, sinken, zünden), neue
  `auto-firework`-Entscheidung, Schilder-Datei (einmal pro Schild, Suche).
- In-game-Checkliste in PLAN.md ergänzen: Raketenverbrauch pro 20 Minuten vorher/nachher je Modus,
  Start nach Landung auf flachem Boden, Wächter löst bei absichtlich blockiertem Start aus.

## Entscheidungen, die ich getroffen habe (bitte prüfen)

- Baritone-Optionen werden standardmäßig vom Mod verwaltet und danach wiederhergestellt.
- `cruise-mode` bleibt standardmäßig `baritone` (mit Baritone); Pitch40 ist opt-in.
- Wächter: drei Versuche pro Phase, dann Pause mit Discord.
- Schilder-Aufzeichnung als eigenes Modul, standardmäßig aus.
- Die Punkte unter „Präzisierungen“: sicher landen statt stoppen, wenn der Wächter in der Luft
  aufgibt; `cruise-mode` ersetzt `use-baritone-pathing` (mit Migration und Zuständigkeitstabelle);
  der Mod landet bei Raketenmangel vor Baritone; `sign-logger` unabhängig vom Scan.

## Dem Umsetzenden überlassen

- Genaue Fristen des Wächters pro Phase.
- Wert für `baritone-firework-speed` und Pitch40-Höhen/-Winkel im Detail.
- Wie der Wächter den Zustand von Baritone abfragt (`BaritoneBridge`).
- Ob AutoPortal portiert oder clean-room gebaut wird (nach Prüfung des Dateikopfs).
