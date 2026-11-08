package qouteall.imm_ptl.core.render.context_management;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamAtmosphereCurveTest {
    @Test void endpointsAreExactAndLongFramesFinish() {
        assertEquals(0f, SeamAtmosphereCurve.smoothProgress(0, 1600));
        assertEquals(1f, SeamAtmosphereCurve.smoothProgress(1600, 1600));
        assertEquals(1f, SeamAtmosphereCurve.smoothProgress(9000, 1600));
        assertEquals(0f, SeamAtmosphereCurve.smoothProgress(-10, 1600));
    }

    @Test void curveIsMonotonicAndSymmetric() {
        float previous = 0;
        for(int ms=0; ms<=1600; ms+=10) {
            float value = SeamAtmosphereCurve.smoothProgress(ms, 1600);
            assertTrue(value >= previous);
            previous = value;
        }
        assertEquals(.5f, SeamAtmosphereCurve.smoothProgress(800, 1600), .000001f);
        assertEquals(1f - SeamAtmosphereCurve.smoothProgress(400, 1600),
            SeamAtmosphereCurve.smoothProgress(1200, 1600), .00001f);
    }

    @Test void disabledDurationMeansNoTransition() {
        assertEquals(1f, SeamAtmosphereCurve.smoothProgress(0, 0));
        assertEquals(1f, SeamAtmosphereCurve.smoothProgress(0, -1));
    }

    @Test void channelsBlendWithoutOvershoot() {
        assertEquals(0.5f, SeamAtmosphereCurve.blend(0f, 1f, .5f));
        assertEquals(.2f, SeamAtmosphereCurve.blend(.2f, .9f, 0f));
        assertEquals(.9f, SeamAtmosphereCurve.blend(.2f, .9f, 1f));
    }
}
