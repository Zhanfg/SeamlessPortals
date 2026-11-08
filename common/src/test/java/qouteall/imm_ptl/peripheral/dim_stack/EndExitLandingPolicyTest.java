package qouteall.imm_ptl.peripheral.dim_stack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndExitLandingPolicyTest {
    @Test void cruisesNormallyFarAboveLanding() {
        assertFalse(EndExitLandingPolicy.shouldBrake(180, -3.5, true));
        assertFalse(EndExitLandingPolicy.shouldBrake(49, -2, true));
    }
    @Test void brakesLateAndOnlyDuringDescent() {
        assertTrue(EndExitLandingPolicy.shouldBrake(48, -1.5, true));
        assertTrue(EndExitLandingPolicy.shouldBrake(12, -0.2, true));
        assertFalse(EndExitLandingPolicy.shouldBrake(12, 0.3, true));
    }
    @Test void neverAssumesVoidIsAPlatform() {
        assertFalse(EndExitLandingPolicy.shouldBrake(8, -1, false));
        assertFalse(EndExitLandingPolicy.shouldBrake(Double.NaN, -1, true));
    }
}
