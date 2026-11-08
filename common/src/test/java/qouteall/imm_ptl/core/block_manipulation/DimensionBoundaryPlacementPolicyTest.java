package qouteall.imm_ptl.core.block_manipulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DimensionBoundaryPlacementPolicyTest {
    @Test void overworldCeilingConnectsToSkylandFloor() {
        // Overworld's Y=320 is *outside* its own legal height; the bottom
        // Skyland cell is a real block position in the Skyland dimension.
        assertEquals(-64, DimensionBoundaryPlacementPolicy.firstCell(-64, 1, -64, 319, 1));
        assertTrue(DimensionBoundaryPlacementPolicy.isSourcePlaneAtBoundary(320, -64, 319));
    }

    @Test void skylandFloorConnectsBackToOverworldCeiling() {
        assertEquals(319, DimensionBoundaryPlacementPolicy.firstCell(320, -1, -64, 319, 1));
        assertTrue(DimensionBoundaryPlacementPolicy.isSourcePlaneAtBoundary(-64, -64, 319));
    }

    @Test void overworldFloorConnectsToNetherCeiling() {
        assertEquals(255, DimensionBoundaryPlacementPolicy.firstCell(256, -1, 0, 255, 1));
        assertEquals(-64, DimensionBoundaryPlacementPolicy.firstCell(-64, 1, -64, 319, 1));
    }

    @Test void rejectsInteriorEndExitAndUnsafeTransforms() {
        assertEquals(DimensionBoundaryPlacementPolicy.NO_CELL,
            DimensionBoundaryPlacementPolicy.firstCell(288, -1, -64, 319, 1));
        assertEquals(DimensionBoundaryPlacementPolicy.NO_CELL,
            DimensionBoundaryPlacementPolicy.firstCell(320, -0.5, -64, 319, 1));
        assertEquals(DimensionBoundaryPlacementPolicy.NO_CELL,
            DimensionBoundaryPlacementPolicy.firstCell(320, -1, -64, 319, 8));
        assertEquals(DimensionBoundaryPlacementPolicy.NO_CELL,
            DimensionBoundaryPlacementPolicy.firstCell(Double.NaN, -1, -64, 319, 1));
        assertFalse(DimensionBoundaryPlacementPolicy.isSourcePlaneAtBoundary(288, -64, 319));
    }

    @Test void doesNotOffByOneOrInventBlockOutsideDestination() {
        assertEquals(DimensionBoundaryPlacementPolicy.NO_CELL,
            DimensionBoundaryPlacementPolicy.firstCell(319, -1, -64, 319, 1));
        assertEquals(DimensionBoundaryPlacementPolicy.NO_CELL,
            DimensionBoundaryPlacementPolicy.firstCell(320, 1, -64, 319, 1));
    }
}
