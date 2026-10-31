# Handoff an Claude Code — NewerNewChunks: End-OldChunk-Detektor über Biome

**Stand 2026-10-31:** Eingebaut und committet. `gradlew clean build --rerun-tasks` läuft mit JDK 25
grün (inkl. Spotless + Unit-Tests). **Offen ist nur noch der In-game-Test.**

Die übrigen Änderungen dieser Runde beschreibt [CLAUDE-HANDOFF.md](CLAUDE-HANDOFF.md).

## In-game-Test (offen)

- [ ] Hauptinsel (`chunkX² + chunkZ² <= 4096`) wird **nie** als old-version markiert.
- [ ] Äußere Inseln in einer frisch erstellten 26.2-Welt werden **nicht** markiert.
- [ ] Äußere Inseln mit Biome `the_end` (z. B. 2b2t-End weit draußen) **werden** markiert.
- [ ] `old-chunk-notifier` meldet im End keine Falschtreffer mehr.

## Was geändert wurde

`modules/NewerNewChunks.java`, Methode `classifyChunk(...)`, Einstellung
`Pre 1.13 End OldChunk Detector` (`endOldChunksDetector`).

- **Vorher:** Jeder End-Chunk mit End Stone auf y=64 galt als alt, also praktisch jede Insel. Ein
  `try/catch` markierte bei Fehlern ebenfalls als alt. Im End war fast alles gelbgrün, und der
  `old-chunk-notifier` schickte Falschmeldungen an Discord.
- **Jetzt:** Ein End-Chunk außerhalb der Hauptinsel (`> END_MAIN_ISLAND_CHUNK_RADIUS_SQ = 4096`)
  gilt als alt, wenn seine Biome-Palette `Biomes.THE_END` enthält
  (`chunk.collectBiomesInPalette(...)` + `Holder.is(...)`). Der `try/catch`-Fallback ist entfernt.
- **Warum das stimmt:** Vor 1.13 hatte das ganze End `the_end`. Seit 1.13 haben die äußeren Inseln
  `end_highlands`/`end_midlands`/`small_end_islands`/`end_barrens`, nur die Hauptinsel behält
  `the_end` (vanilla `TheEndBiomeSource`). Seit 1.18 sind die Biome im Chunk-Paket enthalten, und
  `classifyChunk` dekodiert das Paket ohnehin in eine private `LevelChunk`-Kopie.
- Beschreibung der Einstellung, CHANGELOG (*Fixed*) und PLAN.md angepasst. PLAN-Punkt 52
  („Allocation im End-Detektor“) ist hinfällig, der `BlockPos`-Loop existiert nicht mehr.

## Offene Punkte (nicht Teil dieser Aufgabe)

- ~~Im End kann `isNewChunk` nie `true` werden~~: war nicht gewollt und betraf auch den Nether
  (jeder Chunk wurde „alt“). Behoben 2026-10-31.
- ~~Der „PaletteExploit“ ist nur eine Heuristik~~: Neu/alt kommt jetzt aus der echten
  Palette-Reihenfolge (`utils/PaletteOrder`), in allen Dimensionen. Nur die Being-Updated-Erkennung
  ist noch die alte Stichproben-Heuristik.
- Alte Falschtreffer in `OldGenerationChunkData.txt` bleiben stehen. Entfernen mit dem Button
  „DELETE CHUNK DATA“ (pro Dimension).
