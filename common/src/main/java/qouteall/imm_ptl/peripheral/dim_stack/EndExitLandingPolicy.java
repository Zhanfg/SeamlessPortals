package qouteall.imm_ptl.peripheral.dim_stack;

/**
 * Choose a late, physically continuous fall brake.
 * Never changes position, cancels world ownership or generates terrain.
 */
public final class EndExitLandingPolicy {
    public static final double BRAKE_DISTANCE_BLOCKS = 48.0;
    private EndExitLandingPolicy() {}

    /** Uses a motion-blocking ground height. A void column is not a landing. */
    public static boolean shouldBrake(double distanceToGround, double verticalVelocity,
                                      boolean hasRealGround) {
        return hasRealGround
            && Double.isFinite(distanceToGround)
            && distanceToGround >= -1.0
            && distanceToGround <= BRAKE_DISTANCE_BLOCKS
            && Double.isFinite(verticalVelocity)
            && verticalVelocity < -0.05;
    }
}
