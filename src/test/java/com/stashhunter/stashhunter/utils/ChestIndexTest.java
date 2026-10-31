package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.utils.ChestIndex.Entry;
import com.stashhunter.stashhunter.utils.ChestIndex.ItemCount;
import com.stashhunter.stashhunter.utils.ChestIndex.Pos;
import com.stashhunter.stashhunter.utils.ChestIndex.Stack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestIndexTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final Instant TIME = Instant.parse("2026-10-31T12:00:00Z");

    @TempDir
    Path dir;

    private static Stack item(String id, int count) {
        return new Stack(id, count, null, null);
    }

    private static Stack shulker(String name, Stack... contents) {
        return new Stack("minecraft:shulker_box", 1, name, List.of(contents));
    }

    private static Entry chest(Pos pos, Pos pair, Stack... stacks) {
        return ChestIndex.entry(OVERWORLD, pos, pair, "minecraft:chest", pair == null ? 27 : 54, TIME, List.of(stacks));
    }

    @Test
    void sumsStacksPerItemAndKeepsShulkerContents() {
        Entry entry = chest(new Pos(1, 64, 2), null,
            item("minecraft:diamond", 64), item("minecraft:dirt", 3), item("minecraft:diamond", 10),
            shulker("Kit", item("minecraft:totem_of_undying", 1), item("minecraft:totem_of_undying", 1)),
            shulker(null));

        assertEquals(List.of(new ItemCount("minecraft:diamond", 74), new ItemCount("minecraft:dirt", 3),
            new ItemCount("minecraft:shulker_box", 2)), entry.items());
        assertEquals(2, entry.shulkers().size());
        assertEquals("Kit", entry.shulkers().get(0).name());
        assertEquals(List.of(new ItemCount("minecraft:totem_of_undying", 2)), entry.shulkers().get(0).items());
        assertTrue(entry.shulkers().get(1).items().isEmpty(), "empty shulker");
        assertEquals(74 + 3 + 2 + 2, entry.totalItems());
    }

    @Test
    void doubleChestIsFoundFromBothHalvesAndReplacedAsAWhole() {
        ChestIndex index = ChestIndex.load(dir.resolve("server.json"));
        Pos left = new Pos(0, 64, 0), right = new Pos(1, 64, 0);
        index.put(chest(left, right, item("minecraft:diamond", 1)));
        assertTrue(index.contains(OVERWORLD, right));
        assertEquals(1, index.size());

        // The chest was broken down to a single one and indexed again from the other half.
        index.put(chest(right, null, item("minecraft:dirt", 1)));
        assertFalse(index.contains(OVERWORLD, left));
        assertEquals("minecraft:dirt", index.get(OVERWORLD, right).items().get(0).id());
        assertEquals(1, index.size());
    }

    @Test
    void dimensionsAreSeparate() {
        ChestIndex index = ChestIndex.load(dir.resolve("server.json"));
        index.put(chest(new Pos(0, 64, 0), null, item("minecraft:diamond", 1)));
        assertFalse(index.contains("minecraft:the_nether", new Pos(0, 64, 0)));
    }

    @Test
    void survivesSaveAndLoad() {
        Path file = dir.resolve("server.json");
        ChestIndex index = ChestIndex.load(file);
        Entry entry = chest(new Pos(0, 64, 0), new Pos(0, 64, 1), item("minecraft:diamond", 5), shulker("Kit", item("minecraft:elytra", 1)));
        index.put(entry);
        index.saveIfDirty();

        ChestIndex reloaded = ChestIndex.load(file);
        assertEquals(entry, reloaded.get(OVERWORLD, new Pos(0, 64, 1)));
        assertEquals(1, reloaded.size());
    }

    @Test
    void searchCountsShulkerContentsAndSortsByCount() {
        ChestIndex index = ChestIndex.load(dir.resolve("server.json"));
        index.put(chest(new Pos(0, 64, 0), null, item("minecraft:diamond", 5)));
        index.put(chest(new Pos(5, 64, 0), null, item("minecraft:diamond_block", 2), shulker(null, item("minecraft:diamond", 64))));
        index.put(chest(new Pos(9, 64, 0), null, item("minecraft:dirt", 64)));

        List<ChestIndex.Hit> hits = index.search(" Diamond ");
        assertEquals(2, hits.size());
        assertEquals(new Pos(5, 64, 0), hits.get(0).entry().pos());
        assertEquals(66, hits.get(0).count());
        assertTrue(index.search("").isEmpty());
    }

    @Test
    void sameContentsIgnoresWhenItWasSeen() {
        Entry first = chest(new Pos(0, 64, 0), null, item("minecraft:diamond", 5));
        Entry later = ChestIndex.entry(OVERWORLD, new Pos(0, 64, 0), null, "minecraft:chest", 27, TIME.plusSeconds(60),
            List.of(item("minecraft:diamond", 5)));
        assertTrue(first.sameContents(later));
        assertFalse(first.sameContents(chest(new Pos(0, 64, 0), null, item("minecraft:diamond", 4))));
    }

    @Test
    void brokenFileIsMovedAsideInsteadOfOverwritten() throws IOException {
        Path file = dir.resolve("server.json");
        Files.writeString(file, "{\"version\": 1, \"containers\": [");
        ChestIndex index = ChestIndex.load(file);
        assertEquals(0, index.size());
        assertTrue(Files.exists(dir.resolve("server.json.corrupt")));
    }

    @Test
    void incompleteEntriesAreSkipped() throws IOException {
        Path file = dir.resolve("server.json");
        Files.writeString(file, """
            {"version": 1, "containers": [
              {"dimension": "minecraft:overworld", "slots": 27},
              {"dimension": "minecraft:overworld", "pos": {"x": 1, "y": 2, "z": 3}, "items": [{"count": 4}, null]}
            ]}""");
        ChestIndex index = ChestIndex.load(file);
        assertEquals(1, index.size());
        Entry entry = index.get(OVERWORLD, new Pos(1, 2, 3));
        assertTrue(entry.items().isEmpty());
        assertTrue(entry.shulkers().isEmpty());
    }
}
