package qouteall.imm_ptl.core.chunk_loading;

/**
 * Conservative, allocation-free server-side interest gate for remote portal
 * residency. The server never trusts a client-supplied chunk list.
 *
 * The gate runs ONLY in the opt-in low-memory profile. It does not affect
 * vanilla player view loading or the collision/teleport authority.
 */
public final class ServerPortalInterest {
    private ServerPortalInterest() {}

    /**
     * @param distanceBlocks nearest distance to the portal surface
     * @param inFront true if the observer is on the portal's visible side
     * @param lookDot normalized look direction dot direction toward nearest portal point
     * @param observationRangeBlocks maximum distance to proactively stream remote terrain
     * @param safeDistanceBlocks always keep the portal's near field for crossing safety
     */
    public static boolean shouldLoad(
        double distanceBlocks, boolean inFront, double lookDot,
        int observationRangeBlocks, int safeDistanceBlocks
    ) {
        if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0) {
            return true; // fail open on malformed custom portal geometry
        }
        if (distanceBlocks <= safeDistanceBlocks) {
            return true;
        }
        if (distanceBlocks > observationRangeBlocks) {
            return false;
        }
        // Wide angle to include ordinary peripheral vision and mitigate jitter.
        return inFront && Double.isFinite(lookDot) && lookDot > -0.25;
    }

    /**
     * Bound the number of simultaneously fed remote portal views per player.
     * Zero is intentionally not accepted: a player must be able to approach
     * and cross a portal even in the most conservative profile.
     */
    public static int activeViewLimit(int configuredLimit) {
        return Math.max(1, Math.min(configuredLimit, 8));
    }
}
