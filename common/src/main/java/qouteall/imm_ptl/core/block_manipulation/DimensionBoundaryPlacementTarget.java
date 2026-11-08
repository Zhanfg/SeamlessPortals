package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;

/**
 * Generate a valid vanilla BlockHitResult on the *destination* side of an
 * existing height seam if a remote ray found no block at all.
 *
 * Empty space beyond Overworld Y=320 cannot carry a vanilla BlockPos.
 * A valid Skyland floor position can. Do not fake blocks in a dimension
 * beyond its bounds or mutate either dimension's min/max height.
 *
 * Only called for the currently aimed portal. Never scans the enormous
 * global portal face, stores chunk coordinates, or loads missing chunks.
 */
public final class DimensionBoundaryPlacementTarget {
    private DimensionBoundaryPlacementTarget() {}

    @Nullable
    public static BlockHitResult forEmptySeamFace(
        Portal portal, Level source, Level destination, Vec3 destRayOrigin
    ) {
        if (!(portal instanceof VerticalConnectingPortal)
            || !source.dimension().equals(portal.level().dimension())
            || !destination.dimension().equals(portal.getDestDim())
            || !DimensionBoundaryPlacementPolicy.isSourcePlaneAtBoundary(
                portal.getOriginPos().y, source.getMinY(), source.getMaxY()
            )) {
            return null;
        }

        double dy = portal.getContentDirection().y;
        int y = DimensionBoundaryPlacementPolicy.firstCell(
            portal.getDestPos().y, dy,
            destination.getMinY(), destination.getMaxY(),
            portal.getScaling()
        );
        if (y == DimensionBoundaryPlacementPolicy.NO_CELL
            || !Double.isFinite(destRayOrigin.x)
            || !Double.isFinite(destRayOrigin.z)) {
            return null;
        }

        BlockPos cell = BlockPos.containing(destRayOrigin.x, y, destRayOrigin.z);
        // Missing chunks must be fetched by the existing authoritative portal
        // loader; a fabricated "air" placement is not a substitute for data.
        if (!destination.hasChunkAt(cell)
            || !destination.getWorldBorder().isWithinBounds(cell)
            || !destination.getBlockState(cell).isAir()) {
            return null;
        }

        Direction face = dy > 0 ? Direction.UP : Direction.DOWN;
        double hitY = DimensionBoundaryPlacementPolicy.interiorHitY(y, dy);
        return new BlockHitResult(
            new Vec3(destRayOrigin.x, hitY, destRayOrigin.z),
            face,
            cell,
            false
        );
    }
}
