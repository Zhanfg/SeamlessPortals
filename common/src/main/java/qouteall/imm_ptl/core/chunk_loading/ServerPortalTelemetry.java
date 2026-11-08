package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;

/**
 * Low-frequency, server-only diagnostics. RSS is the entire process, not the
 * mod's exclusive footprint. Compare with an identical no-mod Fabric baseline
 * to test the 20 MiB incremental resident-memory goal.
 */
public final class ServerPortalTelemetry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long REPORT_INTERVAL_TICKS = 1200; // nominal 60s at 20 TPS
    private static long lastReportTick = Long.MIN_VALUE;

    private ServerPortalTelemetry() {}

    public static void tick(MinecraftServer server) {
        if (!IPGlobal.lowMemoryServerDiagnostics) return;
        long now = server.overworld().getGameTime();
        if (lastReportTick != Long.MIN_VALUE && now - lastReportTick < REPORT_INTERVAL_TICKS) {
            return;
        }
        lastReportTick = now;

        int remoteTicketPositions = 0;
        int totalLoadedChunks = 0;
        for (ServerLevel world : server.getAllLevels()) {
            totalLoadedChunks += world.getChunkSource().getLoadedChunksCount();
            ImmPtlChunkTickets ticketManager = ImmPtlChunkTickets.BY_DIMENSION.get(world);
            if (ticketManager != null) {
                remoteTicketPositions += ticketManager.getPortalFedEntryCount();
            }
        }

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long rssKiB = readLinuxVmRssKiB();
        LOGGER.info(
            "[SEAMLESS LOWMEM] players={} portalFedTicketPositions={} loadedChunks={} " +
                "heapUsedMiB={} heapCommittedMiB={} rssMiB={} avgMspt={} " +
                "profile={} maxRemoteRadius={}",
            server.getPlayerList().getPlayers().size(),
            remoteTicketPositions,
            totalLoadedChunks,
            heap.getUsed() / (1024 * 1024),
            heap.getCommitted() / (1024 * 1024),
            rssKiB < 0 ? "n/a" : Long.toString(rssKiB / 1024),
            String.format(java.util.Locale.ROOT, "%.2f", server.getAverageTickTimeNanos() / 1_000_000.0),
            IPGlobal.lowMemoryServerMode,
            IPGlobal.lowMemoryServerMaxPortalRadius
        );
    }

    /** Linux-only process RSS, to be compared with a no-mod reference run. */
    private static long readLinuxVmRssKiB() {
        Path path = Path.of("/proc/self/status");
        if (!Files.isReadable(path)) return -1L;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    return parseVmRssKiB(line);
                }
            }
        }
        catch (Exception e) {
            return -1L;
        }
        return -1L;
    }

    public static long parseVmRssKiB(String statusLine) {
        if (statusLine == null || !statusLine.startsWith("VmRSS:")) return -1L;
        String value = statusLine.substring("VmRSS:".length()).trim();
        int space = value.indexOf(' ');
        if (space >= 0) value = value.substring(0, space);
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException e) {
            return -1L;
        }
    }
}
