# Auswertung: StorageLooter und Chest-Index-GUI — Design

**Stand:** 2026-11-28. Vierte Etappe des Releases 26.3.0 auf Branch `port/26.3` (nach Port,
Signal-Score und Struktur-Locator). Deckt PLAN-Punkte 63 (StorageLooter) und 73 (ChestTracker mit
GUI) ab. Punkt 74 (Shulker-Tooltip) entfällt: Meteors Modul `better-tooltips` zeigt Shulker-Inhalte
schon an.

## Warum

Nach einem Fund packt der Nutzer heute von Hand alles Wertvolle in Shulker-Kisten und legt diese in
seine Endertruhe. Auf einem Anarchy-Server ist nur die Endertruhe sicher; je schneller die Beute dort
ist, desto besser. Der StorageLooter übernimmt genau diesen Ablauf. Die GUI macht den vorhandenen
Chest-Index bequemer nutzbar als Chat-Befehle.

## Ziele

- Neues Modul `storage-looter` (Default aus), das einen gefundenen Stash ausräumt:
  1. Shulker-Kisten im Stash abbauen und aufsammeln (sie sind schon gepackt).
  2. Container nacheinander öffnen und passende Items ins Inventar ziehen.
  3. Ist das Inventar voll: eine leere Shulker-Kiste aus dem Inventar setzen, füllen, abbauen,
     aufsammeln.
  4. Volle Shulker (eigene und aufgesammelte) in die Endertruhe legen: Endertruhe aus dem Inventar
     setzen bzw. eine in Reichweite nutzen.
  5. Stoppen, wenn nichts Passendes mehr übrig ist, keine leere Shulker-Kiste mehr da ist oder die
     Endertruhe voll ist; Grund im Chat.
- Welche Items mitkommen, steuert `loot-mode`:
  - **whitelist** (Default): nur Items aus `loot-list`. Vorbelegung: Shulker-Kisten (alle Farben),
    Netherite-Teile/-Barren/-Blöcke, Diamant-Ausrüstung/-Blöcke, Elytra, Totems, verzauberte
    Goldäpfel und Goldäpfel, Verzauberungsbücher, End-Kristalle, Leuchtfeuer, Netherstern.
  - **blacklist**: alles außer Items aus `ignore-list`. Vorbelegung: Erde, Stein, Bruchstein,
    Netherrack, Kies, Sand, verrottetes Fleisch, Knochen, Fadenreste u. Ä.
  - Beide Listen sind normale Meteor-Item-Listen und vom Nutzer frei bearbeitbar.
- Mit Baritone läuft der Looter selbst zum nächsten Container. Ohne Baritone bearbeitet er nur, was in
  Reichweite ist; der Nutzer läuft selbst weiter.
- Klick- und Aktionsgeschwindigkeit einstellbar (`action-delay` in Ticks), weil Anti-Cheat zu schnelle
  Inventar-Klicks kickt.
- Alles, was der Looter öffnet, landet nebenbei im Chest-Index.
- Neues Fenster für den Chest-Index (`.chest-index gui` und optional eine Taste): Suchfeld, Filter
  „nur Shulker“ und „nur diese Dimension“, Liste mit Item, Anzahl, Container (mit Shulker-Name),
  Koordinaten und Entfernung; pro Zeile *Hinlaufen* (Baritone, sonst Koordinaten kopieren),
  *Waypoint*, *Löschen*. Gleiche Funktionen wie `.chest-index search`/`shulker`, nur als Fenster.

## Nicht-Ziele

- Kein Sortieren der Endertruhe oder der Shulker nach Item-Art.
- Kein Looten von Entities (Kisten-Minecarts, Esel) in dieser Etappe.
- Kein eigenes Pathfinding ohne Baritone.
- Kein Shulker-Tooltip (Meteor hat es).

## Anti-Ziele

- Items gehen verloren: Shulker werden mit falschem Werkzeug zerstört, Items fallen auf den Boden
  und bleiben liegen, die Endertruhe wird ohne Behutsamkeit abgebaut.
- Kick durch Anti-Cheat bei Default-Einstellungen.
- Der Looter macht weiter, während ein Spieler in der Nähe ist (Gefahr auf Anarchy-Servern).

## Ansätze

| Ansatz | Bewertung |
|---|---|
| **1: Eigenes Modul mit gemeinsamem Öffner** (gewählt) | Öffnen/Auslesen aus `chest-index` wird in eine gemeinsame Klasse gezogen; `storage-looter` hat seinen eigenen Ablauf. Klar getrennt, separat testbar; einmaliger Umbau von `chest-index`. |
| 2: Looten als Modus von `chest-index` | Weniger Code, aber `chest-index` (520 Zeilen) mischt zwei Aufgaben; Fehler im Looter stören das Indexieren. Verworfen. |
| Nur mit Baritone | Baritone ist optional; der Looter soll auch ohne laufen. Verworfen. |

## Design

### Gemeinsamer Öffner

- Die Logik aus `ChestIndexer` zum Öffnen eines Containers, Warten auf den Inhalt (Server sendet
  Slots), Auslesen und Schließen wird in eine eigene Klasse gezogen. `ChestIndexer` nutzt sie wie
  bisher (Verhalten unverändert, bestehende Tests bleiben grün); der Looter nutzt sie mit einem
  zusätzlichen Schritt „Items herausnehmen“ vor dem Schließen.
- Auto-Open von `chest-index` pausiert, solange der Looter läuft.

### Ablauf (Zustandsautomat)

- Start: Modul einschalten oder `.sh loot` in der Nähe eines gemeldeten Stashes (Bereich = Cluster des
  nächstgelegenen Funds dieser Sitzung, sonst alle Container im Umkreis `loot-radius`, Default 32).
- Reihenfolge: Shulker-Blöcke zuerst, dann Container nach Entfernung.
- **Shulker abbauen:** mit dem besten Werkzeug aus der Hotbar (Spitzhacke schneller, aber jedes
  Werkzeug erhält den Inhalt); auf das Drop-Item warten und es aufsammeln (hinlaufen in Reichweite
  bzw. mit Baritone).
- **Inventar voll:** Platz für das Setzen suchen (fester Boden, Luft darüber, nicht in Lava/Wasser),
  leere Shulker-Kiste setzen, passende Items hinein, abbauen, aufsammeln.
- **Endertruhe:** vorhandene in Reichweite nutzen, sonst aus dem Inventar setzen. Nach dem Einlagern
  stehen lassen, außer `pickup-ender-chest` ist an und ein Werkzeug mit Behutsamkeit vorhanden
  (ohne Behutsamkeit nie abbauen).
- Leere Shulker-Kisten aus dem Stash werden als Packbehälter mitbenutzt.
- Abbruch und Pause: neuer Spieler in `player-pause-radius` (Default 64, Wiederaufnahme manuell),
  Schaden genommen, Dimensionswechsel, Disconnect, Tod. Grund im Chat, optional Discord
  (`notify-discord`, Default aus).
- Am Ende eine Zusammenfassung: Anzahl geleerter Container, eingelagerte Shulker, wichtigste Items.

### Präzisierungen (aus der Gutachter-Prüfung, eigene Entscheidungen)

- **Eigene Items sind tabu.** Beim Start merkt sich der Looter das Inventar (Snapshot). Gepackt und
  eingelagert werden nur Items, die während dieses Laufs dazugekommen sind. Rüstung, Offhand, Hotbar
  und alle eigenen Items bleiben, wo sie sind. Eigene **leere** Shulker-Kisten dienen als
  Packbehälter; eigene gefüllte Shulker werden nicht angefasst.
- **Endertruhe:**
  - Hinein kommen nur Shulker-Kisten (gepackte und aus dem Stash aufgesammelte), keine losen Items.
  - Jede Shulker-Kiste wird eingelagert, sobald sie voll bzw. aufgesammelt ist, und nicht erst am
    Ende. Das ist auf Anarchy-Servern am sichersten.
  - Am Ende werden die restlichen erbeuteten Items in eine Shulker-Kiste gepackt und eingelagert,
    solange eine leere da ist. Sonst bleiben sie im Inventar, und die Zusammenfassung sagt es.
  - Ohne Endertruhe im Inventar oder in Reichweite startet der Looter nicht („No ender chest“).
- **Chest-Index:**
  - Der Eintrag zeigt den Stand **nach** dem Looten, also das, was übrig ist.
  - Abgebaute Shulker-Blöcke werden aus dem Index entfernt.
  - Der `ChestIndex` gehört künftig nicht mehr dem Modul `chest-index`, sondern wird pro Server
    unabhängig geladen und gespeichert (Laden beim ersten Zugriff, Speichern periodisch und beim
    Verlassen des Servers, auch wenn das Modul aus ist). Looter, GUI und Befehle nutzen ihn auch bei
    ausgeschaltetem Modul. Das Modul steuert nur noch Auto-Open und das Mitlesen.
- **Pause und Abbruch:**
  - **Pause** (Fortschritt bleibt, weiter mit erneutem `.sh loot` bzw. erneutem Einschalten): ein
    anderer Spieler, der kein Meteor-Freund ist, kommt in `player-pause-radius`, oder man nimmt
    Schaden durch eine Entity oder einen Spieler. Fall-, Hunger- und Erstickungsschaden zählen nicht.
  - **Abbruch** (Zustand verworfen): Dimensionswechsel, Disconnect, Tod.
  - Der Looter merkt sich in der Sitzung, welche Container er geleert hat, und überspringt sie beim
    Fortsetzen. Er speichert nichts auf die Festplatte.
  - Ist beim Start schon ein Fremder in Reichweite, startet er nicht und sagt es im Chat.

### Einstellungen

`loot-mode`, `loot-list`, `ignore-list`, `loot-radius`, `action-delay`, `use-baritone`,
`pickup-ender-chest`, `player-pause-radius`, `notify-discord`. Alle mit konservativen Defaults.

### Chest-Index-GUI

- Meteor-`WindowScreen` mit Suchfeld (Item-ID oder Shulker-Name), zwei Filtern, sortierbarer Liste
  (Default nach Anzahl), Zeilen-Knöpfen. Liest denselben `ChestIndex` wie die Befehle; *Löschen*
  entfernt den Container aus dem Index (über den bestehenden Background-Save).
- Öffnen über `.chest-index gui` und eine optionale Keybind-Einstellung in `chest-index`.

### Tests

- Unit-Tests ohne Minecraft-Bootstrap für die reine Logik: Item-Filter (beide Modi), Auswahl der
  Packbehälter, Reihenfolge der Ziele, Abbruchbedingungen, Zustandsübergänge des Automaten mit
  simulierten Ereignissen.
- Test, dass `ChestIndexer` nach dem Herauslösen des Öffners gleich arbeitet (soweit testbar).
- GUI: Filter- und Sortierlogik als getestete Hilfsklasse; das Fenster selbst nur im In-game-Test.
- In-game-Checkliste in PLAN.md ergänzen (mit/ohne Baritone, volle Endertruhe, Spieler kommt,
  Anti-Cheat-Server).

## Entscheidungen, die ich getroffen habe (bitte prüfen)

- Endertruhe wird standardmäßig stehen gelassen; abgebaut nur mit `pickup-ender-chest` und
  Behutsamkeit.
- Pause, sobald ein Spieler in 64 Blöcken auftaucht.
- `.sh loot` als Startbefehl zusätzlich zum Modul-Schalter.
- Keine Entities looten in dieser Etappe.
- Vorbelegungen beider Listen wie oben.
- Die Punkte unter „Präzisierungen“: nur Erbeutetes wird gepackt, jede Shulker-Kiste sofort in die
  Endertruhe, Index zeigt den Rest nach dem Looten und läuft unabhängig vom Modul, Pause bei Fremden
  und Kampfschaden, Abbruch bei Dimensionswechsel, Disconnect und Tod.

## Dem Umsetzenden überlassen

- Struktur des Zustandsautomaten und genaue Zustände.
- Wie Drop-Items erkannt und aufgesammelt werden.
- Platzwahl beim Setzen von Shulker und Endertruhe im Detail.
- Default von `action-delay`.
- Layout-Details der GUI.
