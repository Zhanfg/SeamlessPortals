package qouteall.imm_ptl.core.render.context_management;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;

/**
 * Camera-local atmosphere memory, not a screen fade or an artificial skybox.
 *
 * On vertical dimension seams, the existing portal renderer already displays
 * the real destination world BEFORE crossing. We keep the proven attribute
 * probe reset (prevents source-world sky flashes) and softly blend only the
 * MAIN world's sky-disc RGB + fog RGB for a short time AFTER crossing.
 *
 * No extra worlds, sky textures, shaders, FBOs, server packets or per-tick heap
 * allocations. Portal destination passes are never tinted.
 */
@Environment(EnvType.CLIENT)
public final class SeamAtmosphereBlend {
    private static ResourceKey<Level> lastSampledWorld;
    private static ResourceKey<Level> transitionWorld;

    private static float lastSkyR, lastSkyG, lastSkyB;
    private static float lastFogR, lastFogG, lastFogB;
    private static boolean hasSkySample, hasFogSample;

    private static float sourceSkyR, sourceSkyG, sourceSkyB;
    private static float sourceFogR, sourceFogG, sourceFogB;
    private static boolean blendSky, blendFog;
    private static long beganAtMillis;
    private static boolean active;

    private SeamAtmosphereBlend() {}

    public static void reset() {
        active = false;
        blendSky = false;
        blendFog = false;
        lastSampledWorld = null;
        transitionWorld = null;
        hasSkySample = false;
        hasFogSample = false;
    }

    /** Called immediately BEFORE changing the client's dimension. */
    public static void begin(Portal portal, ResourceKey<Level> from, ResourceKey<Level> to) {
        if (!IPGlobal.smoothDimensionSeamAtmosphere
            || IPGlobal.dimensionSeamBlendMillis <= 0
            || from.equals(to)
            || !(portal instanceof VerticalConnectingPortal)) {
            return;
        }
        // Skip rather than invent a black first frame if the main world did
        // not render a recent sky/fog sample (e.g. a chained cross in one frame).
        blendSky = hasSkySample && from.equals(lastSampledWorld);
        blendFog = hasFogSample && from.equals(lastSampledWorld);
        if (!blendSky && !blendFog) {
            active = false;
            return;
        }
        sourceSkyR = lastSkyR;
        sourceSkyG = lastSkyG;
        sourceSkyB = lastSkyB;
        sourceFogR = lastFogR;
        sourceFogG = lastFogG;
        sourceFogB = lastFogB;
        transitionWorld = to;
        beganAtMillis = Util.getMillis();
        active = true;
    }

    private static float factor(ResourceKey<Level> world) {
        if (!active || !IPGlobal.smoothDimensionSeamAtmosphere
            || transitionWorld != world) return 1f;
        long elapsed = Math.max(0, Util.getMillis() - beganAtMillis);
        float factor = SeamAtmosphereCurve.smoothProgress(
            elapsed, IPGlobal.dimensionSeamBlendMillis
        );
        if (factor >= 1f) {
            active = false;
            return 1f;
        }
        return factor;
    }

    /** Hooked at SkyRenderer.render HEAD. Never runs during portal-world draw. */
    public static void onSky(SkyRenderState sky) {
        if (WorldRenderInfo.isRendering()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || sky.skyColor == null) return;
        ResourceKey<Level> world = mc.level.dimension();
        Vector3fc raw = sky.skyColor;
        // Cache an actual vanilla palette; never recursively blend an already
        // blended value back into the source sample.
        if (lastSampledWorld != world) {
            hasFogSample = false;
            hasSkySample = false;
        }
        lastSampledWorld = world;
        lastSkyR = raw.x(); lastSkyG = raw.y(); lastSkyB = raw.z();
        hasSkySample = true;

        float progress = factor(world);
        if (progress >= 1f || !blendSky) return;
        sky.skyColor = new Vector3f(
            SeamAtmosphereCurve.blend(sourceSkyR, raw.x(), progress),
            SeamAtmosphereCurve.blend(sourceSkyG, raw.y(), progress),
            SeamAtmosphereCurve.blend(sourceSkyB, raw.z(), progress)
        );
    }

    /** Hooked at FogRenderer.updateBuffer HEAD, never remote portal UBOs. */
    public static void onFog(FogData fog) {
        if (WorldRenderInfo.isRendering()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || fog.color == null) return;
        ResourceKey<Level> world = mc.level.dimension();
        if (lastSampledWorld != world) {
            hasFogSample = false;
            hasSkySample = false;
        }
        lastSampledWorld = world;
        float r = fog.color.x, g = fog.color.y, b = fog.color.z;
        lastFogR = r; lastFogG = g; lastFogB = b;
        hasFogSample = true;
        float progress = factor(world);
        if (progress >= 1f || !blendFog) return;
        fog.color.set(
            SeamAtmosphereCurve.blend(sourceFogR, r, progress),
            SeamAtmosphereCurve.blend(sourceFogG, g, progress),
            SeamAtmosphereCurve.blend(sourceFogB, b, progress),
            fog.color.w
        );
    }
}
