package qouteall.imm_ptl.core.chunk_loading;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerPortalInterestTest {
    @Test void alwaysKeepsNearFieldForCrossing() {
        assertTrue(ServerPortalInterest.shouldLoad(8, false, -1, 64, 16));
        assertTrue(ServerPortalInterest.shouldLoad(16, false, -1, 64, 16));
    }

    @Test void doesNotPreloadDistantUnobservedPortal() {
        assertFalse(ServerPortalInterest.shouldLoad(48, true, -0.8, 64, 16));
        assertFalse(ServerPortalInterest.shouldLoad(48, false, 1, 64, 16));
        assertFalse(ServerPortalInterest.shouldLoad(80, true, 1, 64, 16));
    }

    @Test void eagerlyStreamsPortalInWideForwardView() {
        assertTrue(ServerPortalInterest.shouldLoad(40, true, 0.85, 64, 16));
        assertTrue(ServerPortalInterest.shouldLoad(40, true, -0.20, 64, 16));
    }

    @Test void failsOpenOnUnusualPortalGeometry() {
        assertTrue(ServerPortalInterest.shouldLoad(Double.NaN, false, -1, 64, 16));
        assertTrue(ServerPortalInterest.shouldLoad(-1, false, -1, 64, 16));
    }

    @Test void clampsActiveViewCount() {
        assertEquals(1, ServerPortalInterest.activeViewLimit(0));
        assertEquals(2, ServerPortalInterest.activeViewLimit(2));
        assertEquals(8, ServerPortalInterest.activeViewLimit(200));
    }
}
