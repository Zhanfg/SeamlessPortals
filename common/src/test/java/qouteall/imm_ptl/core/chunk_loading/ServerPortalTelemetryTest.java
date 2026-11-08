package qouteall.imm_ptl.core.chunk_loading;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerPortalTelemetryTest {
    @Test void parsesLinuxResidentMemoryLine() {
        assertEquals(123456L, ServerPortalTelemetry.parseVmRssKiB("VmRSS:\t  123456 kB"));
        assertEquals(-1L, ServerPortalTelemetry.parseVmRssKiB("VmSize:\t123456 kB"));
        assertEquals(-1L, ServerPortalTelemetry.parseVmRssKiB("VmRSS:\t? kB"));
    }
}
