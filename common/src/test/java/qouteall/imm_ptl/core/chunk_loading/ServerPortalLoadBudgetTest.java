package qouteall.imm_ptl.core.chunk_loading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerPortalLoadBudgetTest {
    @Test
    void leavesHealthyUnscaledLoadersUnchanged() {
        assertEquals(8, ServerPortalLoadBudget.capRadius(8, 32, 8, 32, 1, false, 3));
    }

    @Test
    void serverPressureCapsNormalPortal() {
        assertEquals(2, ServerPortalLoadBudget.capRadius(8, 32, 8, 2, 1, false, 3));
    }

    @Test
    void serverPressureCapsGlobalPortal() {
        assertEquals(4, ServerPortalLoadBudget.capRadius(8, 32, 16, 4, 1, false, 3));
    }

    @Test
    void scaledPortalCannotEscapeLowMemoryLimit() {
        assertEquals(3, ServerPortalLoadBudget.capRadius(32, 32, 8, 32, 2, true, 3));
    }

    @Test
    void serverGuardAppliesToScaledPortal() {
        assertEquals(4, ServerPortalLoadBudget.capRadius(32, 32, 8, 2, 2, false, 3));
    }

    @Test
    void zeroRequestedRadiusRemainsZero() {
        assertEquals(0, ServerPortalLoadBudget.capRadius(0, 32, 8, 32, 1, true, 3));
    }

    @Test
    void lowMemoryModeDisablesPersistentServerRetention() {
        assertTrue(ServerPortalLoadBudget.allowPersistentServerRetention(true, true, false));
        assertFalse(ServerPortalLoadBudget.allowPersistentServerRetention(true, true, true));
        assertFalse(ServerPortalLoadBudget.allowPersistentServerRetention(true, false, false));
    }
}
