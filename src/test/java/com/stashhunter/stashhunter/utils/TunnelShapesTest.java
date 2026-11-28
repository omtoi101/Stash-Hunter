package com.stashhunter.stashhunter.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TunnelShapesTest {
    @Test
    void verticalShaftIsAHole() {
        TunnelShapes.Passable p = (x, y, z) -> x == 0 && z == 0 && y >= 60 && y <= 64;
        assertTrue(TunnelShapes.isHole(p, 0, 63, 0, 4));
        assertFalse(TunnelShapes.isHole(p, 0, 63, 0, 6));
    }

    @Test
    void openAreaIsNoHole() {
        TunnelShapes.Passable p = (x, y, z) -> y >= 60;
        assertFalse(TunnelShapes.isHole(p, 0, 63, 0, 4));
    }

    @Test
    void oneWideTwoHighCorridorIsATunnel() {
        // corridor along +X at y=64..65, z=0, x 0..9
        TunnelShapes.Passable p = (x, y, z) -> z == 0 && x >= 0 && x <= 9 && (y == 64 || y == 65);
        assertTrue(TunnelShapes.isTunnel(p, 0, 64, 0, 8));
        assertFalse(TunnelShapes.isTunnel(p, 0, 64, 0, 12));
    }

    @Test
    void zTunnelAndOpenSpaceCases() {
        TunnelShapes.Passable p = (x, y, z) -> x == 0 && z >= 0 && z <= 9 && (y == 64 || y == 65);
        assertTrue(TunnelShapes.isTunnel(p, 0, 64, 0, 8));
        TunnelShapes.Passable open = (x, y, z) -> y >= 64;
        assertFalse(TunnelShapes.isTunnel(open, 0, 64, 0, 4));
    }

    @Test
    void stepsUpwardAreAStair() {
        // solid below the diagonal: step i stands at (i, 64+i, 0)
        TunnelShapes.Passable p = (x, y, z) -> z == 0 && y >= 64 + x;
        assertTrue(TunnelShapes.isStair(p, 0, 64, 0, 5));
        TunnelShapes.Passable flat = (x, y, z) -> z == 0 && y >= 64;
        assertFalse(TunnelShapes.isStair(flat, 0, 64, 0, 5));
    }
}
