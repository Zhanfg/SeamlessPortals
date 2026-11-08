package qouteall.imm_ptl.core.render.context_management;

/** Allocation-free, time-based cross-dimension atmosphere transition math.
 * The curve is applied ONLY to the main camera output, never a remote portal
 * destination pass: each remote world retains its own real sky and fog.
 */
public final class SeamAtmosphereCurve {
    private SeamAtmosphereCurve() {}

    public static float smoothProgress(long elapsedMillis, int durationMillis) {
        if (durationMillis <= 0) return 1f;
        float t = Math.max(0f, Math.min(1f,
            (float) Math.max(0L, elapsedMillis) / durationMillis));
        return t * t * (3f - 2f * t);
    }

    public static float blend(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
