package dev.probe.optsupport;

import dev.probe.opt.CompositeRenderTypeAccessor;
import dev.probe.opt.CompositeStateAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** EntityBatchMixin's rule and counters (render thread). */
public final class EntityBatch {
    /** render types given their own buffer, at most (each keeps a small off-heap buffer) */
    public static final int MAX_TYPES = 512;
    public static long batches, draws;
    private static final Map<RenderType, Boolean> COMMUTES = new IdentityHashMap<>();

    private EntityBatch() {}

    /**
     * Draws of this type give the same pixels in any order among themselves: opaque (no blending, no colour logic),
     * depth-tested {@code <=} with depth and colour writes (the nearest fragment wins whatever the order), into the
     * main target, not sorted on upload, independent primitives. Read from the type's state, so it holds for mod
     * render types too.
     */
    public static boolean commutes(RenderType t) {
        Boolean c = COMMUTES.get(t);
        if (c == null) {
            c = false;
            if (t instanceof CompositeRenderTypeAccessor a && !t.sortOnUpload() && t.canConsolidateConsecutiveGeometry() && !t.isOutline()) {
                CompositeStateAccessor s = (CompositeStateAccessor) (Object) a.probe$state();
                c = s.probe$transparency() == RenderStateShard.NO_TRANSPARENCY
                        && s.probe$depthTest() == RenderStateShard.LEQUAL_DEPTH_TEST
                        && s.probe$writeMask() == RenderStateShard.COLOR_DEPTH_WRITE
                        && s.probe$output() == RenderStateShard.MAIN_TARGET
                        && s.probe$colorLogic() == RenderStateShard.NO_COLOR_LOGIC;
            }
            COMMUTES.put(t, c);
        }
        return c;
    }

    public static String report() {
        long n = COMMUTES.values().stream().filter(b -> b).count();
        return "entity batch: types batched " + n + " of " + COMMUTES.size() + ", batches opened " + batches + ", batch draws " + draws;
    }
}
