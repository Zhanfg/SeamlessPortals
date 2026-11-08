package qouteall.imm_ptl.core.chunk_loading;

/**
 * Server-owned budget for portal destination chunk residency.
 *
 * This intentionally does not reference client renderers or Minecraft classes:
 * it only limits how many authoritative destination chunks the server can request.
 * The client remains responsible for portal rendering and mesh caching.
 */
public final class ServerPortalLoadBudget {
    private ServerPortalLoadBudget() {}

    /**
     * Limit a requested destination radius by the player and server performance
     * tiers, the global user limit, and an optional low-memory server hard cap.
     * Scale multipliers preserve the established portal behavior except that the
     * opt-in low-memory cap is always final and cannot be bypassed by scaling.
     */
    public static int capRadius(
        int requestedRadius, int playerCap, int configuredCap, int serverCap,
        int multiplier, boolean lowMemoryMode, int lowMemoryRadius
    ) {
        if (requestedRadius <= 0) {
            return 0;
        }
        int cap = Math.min(playerCap, Math.min(configuredCap, serverCap));
        cap = Math.max(1, cap);
        long scaledCap = (long) cap * Math.max(1, multiplier);
        if (lowMemoryMode) {
            scaledCap = Math.min(scaledCap, Math.max(1, lowMemoryRadius));
        }
        return (int) Math.max(1L, Math.min((long) requestedRadius, scaledCap));
    }

    /**
     * Stable server-side retention is optional and must not defeat the
     * resource-pressure guard on a low-memory host.
     */
    public static boolean allowPersistentServerRetention(
        boolean playerGood, boolean serverGood, boolean lowMemoryMode
    ) {
        return playerGood && serverGood && !lowMemoryMode;
    }
}
