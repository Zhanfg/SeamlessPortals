package qouteall.imm_ptl.peripheral.dim_stack;

/**
 * Whitelist for the independent one-way End-bottom escape gate.
 * The destination is intentionally restricted to already-supported dimensions:
 * users cannot turn a typo into a missing target dimension on world bootstrap.
 */
public final class EndBottomExitPolicy {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String SKYLAND = "immersive_portals:skyland";

    private EndBottomExitPolicy() {}

    /** An unset destination means "disabled" and preserves old worlds. */
    public static boolean isEnabled(String destinationId) {
        return destinationId != null && !destinationId.isBlank();
    }

    public static boolean isSupportedTarget(String destinationId) {
        return OVERWORLD.equals(destinationId) || SKYLAND.equals(destinationId);
    }

    /**
     * Preserve an air gap between the arrival and a possible existing world
     * ceiling portal. This is a portal-plane offset, NOT a safe-surface lookup;
     * the administrator must verify the landing area.
     */
    public static int destinationY(int destinationMaxExclusive, int ceilingOffsetBlocks) {
        int offset = Math.max(8, Math.min(64, ceilingOffsetBlocks));
        return destinationMaxExclusive - offset;
    }
}
