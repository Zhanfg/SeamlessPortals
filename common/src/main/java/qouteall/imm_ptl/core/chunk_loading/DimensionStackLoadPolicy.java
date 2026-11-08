package qouteall.imm_ptl.core.chunk_loading;

/**
 * Pure numeric guard for low-memory global horizontal dimension-stack seams.
 *
 * The destination world remains server-authoritative. This class only decides
 * when a seam needs remote chunk tickets; it never changes teleport/collision.
 */
public final class DimensionStackLoadPolicy {
    private DimensionStackLoadPolicy() {}

    /**
     * Velocity-aware minimum distance at which the destination should be
     * readied, based on motion *towards* the portal plane (blocks/tick).
     * With a 2-vCPU server the lookahead is deliberately conservative.
     */
    public static int crossingSafetyRange(
        int baseRangeBlocks, double velocityTowardsSeamBlocksPerTick
    ) {
        int base = Math.max(8, Math.min(64, baseRangeBlocks));
        if (!Double.isFinite(velocityTowardsSeamBlocksPerTick)
            || velocityTowardsSeamBlocksPerTick <= 0) {
            return base;
        }
        // Approx. 40 ticks of descent headroom, at most 64 blocks.
        double extra = Math.min(64.0 - base, velocityTowardsSeamBlocksPerTick * 40.0);
        return (int) Math.ceil(Math.max(base, base + Math.max(0, extra)));
    }

    /**
     * Avoid speculative loading beyond the observed portal reach, but always
     * protect fast falling / ascending players approaching a global seam.
     */
    public static boolean shouldPrepare(
        double distanceToSeam, boolean frontFacing,
        double lookDot, int normalObservationRange,
        int baseSafeRange, double speedTowardsSeam
    ) {
        int safe = crossingSafetyRange(baseSafeRange, speedTowardsSeam);
        return ServerPortalInterest.shouldLoad(
            distanceToSeam, frontFacing, lookDot,
            Math.max(safe, normalObservationRange), safe
        );
    }

    /** Stack loaders get a smaller cap than ordinary remote portal windows. */
    public static int stackRadiusLimit(int desired, int configuredCap) {
        if (desired <= 0) return 0;
        return Math.min(desired, Math.max(1, Math.min(8, configuredCap)));
    }
}
