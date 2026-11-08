package qouteall.imm_ptl.core.block_manipulation;

/**
 * Height-lattice mapping for a dimension-stack portal placed exactly on the
 * outer block FACE of a vanilla world. Never raises Minecraft's world bounds.
 *
 * The source's Y=320 visual continuation must be stored in the destination's
 * lowest legal block, NOT as an out-of-height block in the source level.
 */
public final class DimensionBoundaryPlacementPolicy {
    public static final int NO_CELL = Integer.MIN_VALUE;
    private static final double EPSILON = 0.02;

    private DimensionBoundaryPlacementPolicy() {}

    /**
     * @param destPlaneY the portal destination plane in world Y coordinates
     * @param contentDirectionY direction into the destination world
     * @param destMinY first valid block coordinate in the destination
     * @param destMaxY LAST valid block coordinate in the destination
     * @param scaling cross-dimension geometric scale
     * @return first usable destination cell, or NO_CELL on an unsafe mapping
     */
    public static int firstCell(
        double destPlaneY, double contentDirectionY,
        int destMinY, int destMaxY, double scaling
    ) {
        if (destMaxY < destMinY || !Double.isFinite(destPlaneY)
            || !Double.isFinite(contentDirectionY) || !Double.isFinite(scaling)
            || Math.abs(scaling - 1.0) > 1.0e-8) {
            return NO_CELL;
        }
        if (contentDirectionY > 0.99 && Math.abs(destPlaneY - destMinY) < EPSILON) {
            return destMinY;
        }
        if (contentDirectionY < -0.99
            && Math.abs(destPlaneY - (destMaxY + 1.0)) < EPSILON) {
            return destMaxY;
        }
        return NO_CELL;
    }

    public static boolean isSourcePlaneAtBoundary(
        double sourcePlaneY, int sourceMinY, int sourceMaxY
    ) {
        return Double.isFinite(sourcePlaneY)
            && (Math.abs(sourcePlaneY - sourceMinY) < EPSILON
                || Math.abs(sourcePlaneY - (sourceMaxY + 1.0)) < EPSILON);
    }
}
