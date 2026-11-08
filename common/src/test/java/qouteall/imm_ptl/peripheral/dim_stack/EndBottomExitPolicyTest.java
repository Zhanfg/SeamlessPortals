package qouteall.imm_ptl.peripheral.dim_stack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndBottomExitPolicyTest {
    @Test void supportsOnlyOverworldOrSkyland() {
        assertTrue(EndBottomExitPolicy.isSupportedTarget("minecraft:overworld"));
        assertTrue(EndBottomExitPolicy.isSupportedTarget("immersive_portals:skyland"));
        assertFalse(EndBottomExitPolicy.isSupportedTarget("minecraft:the_nether"));
        assertFalse(EndBottomExitPolicy.isSupportedTarget("minecraft:the_end"));
        assertFalse(EndBottomExitPolicy.isSupportedTarget("other:dimension"));
    }

    @Test void featureIsOptIn() {
        assertFalse(EndBottomExitPolicy.isEnabled(null));
        assertFalse(EndBottomExitPolicy.isEnabled(""));
        assertTrue(EndBottomExitPolicy.isEnabled("minecraft:overworld"));
    }

    @Test void arrivalBelowExistingCeilingPortal() {
        assertEquals(288, EndBottomExitPolicy.destinationY(320, 32));
        assertEquals(312, EndBottomExitPolicy.destinationY(320, 0));
        assertEquals(256, EndBottomExitPolicy.destinationY(320, 100));
    }
}
