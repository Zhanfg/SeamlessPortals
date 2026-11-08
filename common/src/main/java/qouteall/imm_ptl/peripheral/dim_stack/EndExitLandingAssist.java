package qouteall.imm_ptl.peripheral.dim_stack;

import com.warwa.seamlessportals.platform.Platform;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;

/**
 * Server-authoritative late landing brake for an End-floor one-way crossing.
 *
 * The player keeps their transformed velocity, camera angle and original X/Z.
 * No /tp, snap-to-ground, auto-water placement or remote-world forced
 * simulation. Only players who actually use the configured End escape are
 * tracked, and only for a bounded interval.
 */
public final class EndExitLandingAssist {
    private static final int MAX_TRACKED = 16;
    private static final int MAX_TRACKING_TICKS = 1200;
    private static final int SLOW_FALLING_TICKS = 45;
    private static final Map<UUID, Tracking> TRACKED = new HashMap<>();

    private record Tracking(net.minecraft.resources.ResourceKey<Level> destination, int ticksLeft) {}

    private EndExitLandingAssist() {}

    public static void init() {
        Platform.get().onServerTickEnd(EndExitLandingAssist::tick);
    }

    public static void onCrossing(VerticalConnectingPortal portal, ServerPlayer player) {
        if (portal.level().dimension() != Level.END
            || player.level().dimension() != portal.getDestDim()) {
            return;
        }
        DimStackInfo preset = DimStackManagement.getDimStackPreset();
        if (preset == null || !EndBottomExitPolicy.isSupportedTarget(preset.endBottomExitTarget)) {
            return;
        }
        if (!portal.getDestDim().identifier().toString().equals(preset.endBottomExitTarget)) {
            return;
        }
        if (TRACKED.size() >= MAX_TRACKED && !TRACKED.containsKey(player.getUUID())) {
            return;
        }
        TRACKED.put(player.getUUID(),
            new Tracking(player.level().dimension(), MAX_TRACKING_TICKS));
    }

    private static void tick(MinecraftServer server) {
        if (TRACKED.isEmpty()) return;
        Iterator<Map.Entry<UUID, Tracking>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Tracking> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Tracking state = e.getValue();
            if (player == null || player.isRemoved()
                || player.level().dimension() != state.destination
                || player.onGround() || state.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            e.setValue(new Tracking(state.destination, state.ticksLeft - 1));
            if (!(player.level() instanceof ServerLevel level)
                || player.getDeltaMovement().y >= -0.05) {
                continue;
            }

            // At most one 49-block downward ray every two ticks for this
            // tracked player. Unlike a surface heightmap, this finds cave
            // floors and floating islands BELOW the player rather than the
            // highest distant land in the same X/Z column.
            if ((state.ticksLeft & 1) != 0) continue;
            Vec3 feet = player.position();
            BlockHitResult ground = level.clip(new ClipContext(
                feet.add(0, 0.15, 0),
                feet.add(0, -49.0, 0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.WATER,
                player
            ));
            boolean groundExists = ground.getType() == HitResult.Type.BLOCK;
            if (!EndExitLandingPolicy.shouldBrake(
                groundExists ? player.getY() - ground.getLocation().y : Double.POSITIVE_INFINITY,
                player.getDeltaMovement().y,
                groundExists
            )) continue;

            MobEffectInstance existing = player.getEffect(MobEffects.SLOW_FALLING);
            if (existing == null || existing.getDuration() < 12) {
                // Ambient=false, particles=false, icon=false. No vignette,
                // potion HUD or artificial slowdown high above the world.
                player.addEffect(new MobEffectInstance(
                    MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0,
                    false, false, false
                ));
            }
        }
    }
}
