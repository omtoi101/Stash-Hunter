package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SignalScoringTest {
    private static Signal sig(int x, int y, int z, SignalKind kind) {
        return new Signal(new BlockPos(x, y, z), kind);
    }

    private static List<Signal> repeat(SignalKind kind, int count, int offset) {
        List<Signal> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(sig(offset + i, 64, 0, kind));
        return list;
    }

    @SafeVarargs
    private static List<Signal> of(List<Signal>... parts) {
        List<Signal> list = new ArrayList<>();
        for (List<Signal> p : parts) list.addAll(p);
        return list;
    }

    /** Container positions = all signals of CONTAINER category, as the caller would pass them. */
    private static List<BlockPos> containers(List<Signal> signals) {
        return signals.stream()
                .filter(s -> s.kind().category() == SignalCategory.CONTAINER)
                .map(Signal::pos)
                .toList();
    }

    private static Verdict eval(List<Signal> signals, Sensitivity sensitivity, int setting, boolean shapeOk) {
        return SignalScoring.evaluate(signals, containers(signals), shapeOk, sensitivity, setting);
    }

    @Test
    void threeBuriedShulkersAreAHint() {
        Verdict v = eval(repeat(SignalKind.SHULKER_BOX, 3, 0), Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.HINT, v.tier());
        assertFalse(v.byContainers());
    }

    @Test
    void oneEnderChestIsAHint() {
        Verdict v = eval(repeat(SignalKind.ENDER_CHEST, 1, 0), Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.HINT, v.tier());
    }

    @Test
    void signBedChestIsAHint() {
        List<Signal> s = of(
                List.of(sig(0, 64, 0, SignalKind.WRITTEN_SIGN)),
                List.of(sig(1, 64, 0, SignalKind.BED)),
                List.of(sig(2, 64, 0, SignalKind.CHEST)));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.HINT, v.tier());
        assertEquals(6, v.score());
    }

    @Test
    void tenDenseChestsAreAStashByContainers() {
        Verdict v = eval(repeat(SignalKind.CHEST, 10, 0), Sensitivity.NORMAL, 10, true);
        assertEquals(Tier.STASH, v.tier());
        assertTrue(v.byContainers());
    }

    @Test
    void tenDenseShulkersAreAStashByContainers() {
        List<Signal> shulkers = repeat(SignalKind.SHULKER_BOX, 10, 0);
        Verdict v = SignalScoring.evaluate(shulkers, List.of(), true, Sensitivity.NORMAL, 10);
        assertEquals(Tier.STASH, v.tier());
        assertTrue(v.byContainers());
    }

    @Test
    void tenShulkersAreAStashByContainersInClassic() {
        List<Signal> shulkers = repeat(SignalKind.SHULKER_BOX, 10, 0);
        Verdict v = SignalScoring.evaluate(shulkers, List.of(), true, Sensitivity.CLASSIC, 10);
        assertEquals(Tier.STASH, v.tier());
        assertTrue(v.byContainers());
    }

    @Test
    void sixChestsAreAStashInNormalButNotInClassic() {
        List<Signal> chests = repeat(SignalKind.CHEST, 6, 0);
        assertEquals(Tier.STASH, eval(chests, Sensitivity.NORMAL, 10, true).tier());
        assertEquals(Tier.NONE, eval(chests, Sensitivity.CLASSIC, 10, true).tier());
    }

    @Test
    void classicNeverGivesHints() {
        Verdict v = eval(repeat(SignalKind.SHULKER_BOX, 3, 0), Sensitivity.CLASSIC, 10, false);
        assertEquals(Tier.NONE, v.tier());
    }

    @Test
    void classicStashExactlyAtSettingThreshold() {
        assertEquals(Tier.STASH, eval(repeat(SignalKind.CHEST, 10, 0), Sensitivity.CLASSIC, 10, true).tier());
        assertEquals(Tier.NONE, eval(repeat(SignalKind.CHEST, 9, 0), Sensitivity.CLASSIC, 10, true).tier());
        assertEquals(Tier.NONE, eval(repeat(SignalKind.CHEST, 10, 0), Sensitivity.CLASSIC, 10, false).tier());
    }

    @Test
    void villageWithoutPlayersIsNothing() {
        List<Signal> s = of(
                repeat(SignalKind.BED, 8, 0),
                repeat(SignalKind.CRAFTING_TABLE, 4, 100),
                repeat(SignalKind.ANVIL, 1, 200),
                repeat(SignalKind.BREWING_STAND, 1, 300),
                repeat(SignalKind.CHEST, 2, 400));
        assertEquals(Tier.NONE, eval(s, Sensitivity.NORMAL, 10, false).tier());
    }

    @Test
    void mineshaftMinecartsAreNothing() {
        List<Signal> s = of(
                repeat(SignalKind.CHEST_MINECART, 6, 0),
                repeat(SignalKind.SPAWNER, 1, 100));
        assertEquals(Tier.NONE, eval(s, Sensitivity.NORMAL, 10, false).tier());
    }

    @Test
    void loneSignIsNothing() {
        assertEquals(Tier.NONE, eval(repeat(SignalKind.WRITTEN_SIGN, 1, 0), Sensitivity.NORMAL, 10, false).tier());
    }

    @Test
    void weakTracesAreCappedAtFour() {
        List<Signal> s = of(
                repeat(SignalKind.WRITTEN_SIGN, 1, 0),
                repeat(SignalKind.BED, 20, 100));
        assertEquals(7, eval(s, Sensitivity.NORMAL, 10, false).score());
    }

    @Test
    void manyTracesReachStashByScore() {
        List<Signal> s = of(
                repeat(SignalKind.MINECART_STACK, 4, 0),
                repeat(SignalKind.WRITTEN_SIGN, 3, 100),
                repeat(SignalKind.PLACED_BEDROCK, 1, 200));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.STASH, v.tier());
        assertTrue(v.score() >= 25);
        assertFalse(v.byContainers());
    }

    @Test
    void shulkerInADungeonIsStillAHint() {
        List<Signal> s = of(
                repeat(SignalKind.SPAWNER, 1, 0),
                repeat(SignalKind.SHULKER_BOX, 1, 100));
        assertEquals(Tier.HINT, eval(s, Sensitivity.NORMAL, 10, false).tier());
    }

    @Test
    void sensitiveLowersHintThreshold() {
        List<Signal> s = of(
                repeat(SignalKind.WRITTEN_SIGN, 1, 0),
                repeat(SignalKind.BED, 1, 100));
        assertEquals(Tier.NONE, eval(s, Sensitivity.NORMAL, 10, false).tier());
        assertEquals(Tier.HINT, eval(s, Sensitivity.SENSITIVE, 10, false).tier());
    }

    @Test
    void singleHeavyTraceIsNoHintEvenInSensitive() {
        List<Signal> s = repeat(SignalKind.PLACED_BEDROCK, 1, 0);
        assertEquals(Tier.NONE, eval(s, Sensitivity.SENSITIVE, 10, false).tier());
    }

    @Test
    void strongKindAloneNeverStashesByScore() {
        // 3 shulker boxes = 30 >= 25, but no TRACE kind: HINT, not STASH.
        Verdict v = eval(repeat(SignalKind.SHULKER_BOX, 3, 0), Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.HINT, v.tier());
    }
}
