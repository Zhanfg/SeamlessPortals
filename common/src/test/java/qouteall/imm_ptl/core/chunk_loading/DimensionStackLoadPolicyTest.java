package qouteall.imm_ptl.core.chunk_loading;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DimensionStackLoadPolicyTest {
    @Test void stationarySeamHasSmallCrossingZone() {
        assertEquals(32, DimensionStackLoadPolicy.crossingSafetyRange(32, 0));
        assertEquals(32, DimensionStackLoadPolicy.crossingSafetyRange(32, -1));
    }

    @Test void fallingPlayerGetsLookAhead() {
        assertEquals(52, DimensionStackLoadPolicy.crossingSafetyRange(32, 0.5));
        assertEquals(64, DimensionStackLoadPolicy.crossingSafetyRange(32, 3));
    }

    @Test void crossingIsSafeEvenWhenLookingAway() {
        assertTrue(DimensionStackLoadPolicy.shouldPrepare(28, false, -1, 128, 32, 0));
        assertTrue(DimensionStackLoadPolicy.shouldPrepare(50, false, -1, 128, 32, 1));
        assertFalse(DimensionStackLoadPolicy.shouldPrepare(80, false, -1, 128, 32, 0));
    }

    @Test void observedDistantStackStillShowsDestination() {
        assertTrue(DimensionStackLoadPolicy.shouldPrepare(100, true, 1, 128, 32, 0));
        assertFalse(DimensionStackLoadPolicy.shouldPrepare(200, true, 1, 128, 32, 0));
    }

    @Test void failOpenForUnknownPortalGeometry() {
        assertTrue(DimensionStackLoadPolicy.shouldPrepare(
            Double.NaN, false, -1, 128, 32, 0));
    }

    @Test void stackUsesSmallerLoadingRadiusThanOrdinaryPortal() {
        assertEquals(2, DimensionStackLoadPolicy.stackRadiusLimit(3, 2));
        assertEquals(1, DimensionStackLoadPolicy.stackRadiusLimit(3, 1));
        assertEquals(0, DimensionStackLoadPolicy.stackRadiusLimit(0, 2));
    }
}
