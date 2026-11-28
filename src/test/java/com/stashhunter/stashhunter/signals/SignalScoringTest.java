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
    void sixChestsAreAStashInNormal() {
        // NORMAL caps the container threshold at 6; the real classic rule is tested in ClassicRuleTest.
        List<Signal> chests = repeat(SignalKind.CHEST, 6, 0);
        assertEquals(Tier.STASH, eval(chests, Sensitivity.NORMAL, 10, true).tier());
        assertEquals(Tier.NONE, eval(repeat(SignalKind.CHEST, 5, 0), Sensitivity.NORMAL, 10, true).tier());
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
                repeat(SignalKind.BED, 20, 100),
                repeat(SignalKind.CRAFTING_TABLE, 2, 200),
                repeat(SignalKind.ANVIL, 1, 300),
                repeat(SignalKind.ENCHANTING_TABLE, 1, 400),
                repeat(SignalKind.BREWING_STAND, 1, 500),
                repeat(SignalKind.BOAT, 1, 600));
        assertEquals(7, eval(s, Sensitivity.NORMAL, 10, false).score());
    }

    @Test
    void eachWeakKindCountsOnce() {
        List<Signal> s = of(
                repeat(SignalKind.WRITTEN_SIGN, 1, 0),
                repeat(SignalKind.BED, 20, 100));
        assertEquals(4, eval(s, Sensitivity.NORMAL, 10, false).score());
    }

    @Test
    void manyDistinctTracesReachStashByScore() {
        // 4 + 4 + 4 + 3 + 3 + 3 + 3 + 3 = 27 from eight distinct TRACE kinds.
        List<Signal> s = of(
                repeat(SignalKind.MINECART_STACK, 1, 0),
                repeat(SignalKind.PLACED_BEDROCK, 1, 100),
                repeat(SignalKind.BEACON, 1, 200),
                repeat(SignalKind.WRITTEN_SIGN, 1, 300),
                repeat(SignalKind.UNNATURAL_BLOCK, 1, 400),
                repeat(SignalKind.SKY_BUILD, 1, 500),
                repeat(SignalKind.ITEM_FRAME, 1, 600),
                repeat(SignalKind.NAMED_ENTITY, 1, 700));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.STASH, v.tier());
        assertEquals(27, v.score());
        assertFalse(v.byContainers());
    }

    @Test
    void repeatedTracesCountOnceAndStayAHint() {
        List<Signal> s = of(
                repeat(SignalKind.MINECART_STACK, 4, 0),
                repeat(SignalKind.WRITTEN_SIGN, 3, 100),
                repeat(SignalKind.PLACED_BEDROCK, 1, 200));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.HINT, v.tier());
        assertEquals(11, v.score());
    }

    @Test
    void sensitiveStashesByScoreFromDistinctTraces() {
        // 4 + 4 + 4 + 3 = 15 = SENSITIVE stash score.
        List<Signal> s = of(
                repeat(SignalKind.MINECART_STACK, 1, 0),
                repeat(SignalKind.PLACED_BEDROCK, 1, 100),
                repeat(SignalKind.BEACON, 1, 200),
                repeat(SignalKind.WRITTEN_SIGN, 1, 300));
        assertEquals(Tier.STASH, eval(s, Sensitivity.SENSITIVE, 10, false).tier());
        assertEquals(Tier.HINT, eval(s, Sensitivity.NORMAL, 10, false).tier());
    }

    @Test
    void oneNetherPortalIsNothing() {
        // 2x3 portal: six portal blocks plus its obsidian frame are one trace.
        List<Signal> s = of(
                repeat(SignalKind.OPEN_PORTAL, 6, 0),
                repeat(SignalKind.OBSIDIAN_FRAME, 1, 100));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.NONE, v.tier());
        assertEquals(3, v.score());
        assertEquals(Tier.NONE, eval(s, Sensitivity.SENSITIVE, 10, false).tier());
    }

    @Test
    void litEndPortalIsNothing() {
        List<Signal> s = repeat(SignalKind.OPEN_PORTAL, 9, 0);
        assertEquals(Tier.NONE, eval(s, Sensitivity.NORMAL, 10, false).tier());
        assertEquals(Tier.NONE, eval(s, Sensitivity.SENSITIVE, 10, false).tier());
    }

    @Test
    void concreteWallAndBedIsNothing() {
        List<Signal> s = of(
                repeat(SignalKind.UNNATURAL_BLOCK, 30, 0),
                repeat(SignalKind.BED, 1, 100));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.NONE, v.tier());
        assertEquals(4, v.score());
    }

    @Test
    void portalNextToWrittenSignIsAHint() {
        List<Signal> s = of(
                repeat(SignalKind.OPEN_PORTAL, 6, 0),
                repeat(SignalKind.OBSIDIAN_FRAME, 1, 100),
                repeat(SignalKind.WRITTEN_SIGN, 1, 200));
        Verdict v = eval(s, Sensitivity.NORMAL, 10, false);
        assertEquals(Tier.HINT, v.tier());
        assertEquals(6, v.score());
    }

    @Test
    void containersStillCountPerBlock() {
        // Sign 3 + four chests 8 = 11.
        List<Signal> s = of(
                repeat(SignalKind.WRITTEN_SIGN, 1, 0),
                repeat(SignalKind.CHEST, 4, 100));
        assertEquals(11, eval(s, Sensitivity.NORMAL, 10, false).score());
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
