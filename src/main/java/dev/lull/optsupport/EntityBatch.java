package dev.lull.optsupport;

import dev.lull.opt.CompositeRenderTypeAccessor;
import dev.lull.opt.CompositeStateAccessor;
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
            // (with the residents' experiment: a type sorted on upload counts when it is opaque - Create's chains - the
            // nearest fragment wins whatever the order; only coplanar fragments of equal depth could differ)
            if (t instanceof CompositeRenderTypeAccessor a && (!t.sortOnUpload() || PartBatch.PERSIST_ON) && t.canConsolidateConsecutiveGeometry() && !t.isOutline()) {
                CompositeStateAccessor s = (CompositeStateAccessor) (Object) a.sleep$state();
                c = s.sleep$transparency() == RenderStateShard.NO_TRANSPARENCY
                        && s.sleep$depthTest() == RenderStateShard.LEQUAL_DEPTH_TEST
                        && s.sleep$writeMask() == RenderStateShard.COLOR_DEPTH_WRITE
                        && s.sleep$output() == RenderStateShard.MAIN_TARGET
                        && s.sleep$colorLogic() == RenderStateShard.NO_COLOR_LOGIC;
            }
            COMMUTES.put(t, c);
        }
        return c;
    }

    private static final Map<com.mojang.blaze3d.vertex.BufferBuilder, RenderType> TYPE_OF = new IdentityHashMap<>();

    /** EntityBatchMixin: a batched builder of this type was started / drawn. */
    public static void started(com.mojang.blaze3d.vertex.BufferBuilder b, RenderType t) {
        TYPE_OF.put(b, t);
    }

    public static void ended(com.mojang.blaze3d.vertex.BufferBuilder b) {
        if (b != null) TYPE_OF.remove(b);
    }

    /** PartBatch: the render type of a builder this batching owns (null for any other consumer). */
    public static RenderType typeOf(com.mojang.blaze3d.vertex.BufferBuilder b) {
        return TYPE_OF.get(b);
    }

    /** The shader a composite render type sets up (its shader shard's supplier), or null. */
    public static net.minecraft.client.renderer.ShaderInstance shaderOf(RenderType t) {
        if (!(t instanceof CompositeRenderTypeAccessor a)) return null;
        var shard = ((CompositeStateAccessor) (Object) a.sleep$state()).sleep$shader();
        if (shard == null) return null;
        var sup = ((dev.lull.opt.ShaderStateShardAccessor) shard).sleep$shader();
        return sup.isPresent() ? sup.get().get() : null;
    }

    public static String report() {
        long n = COMMUTES.values().stream().filter(b -> b).count();
        return "entity batch: types batched " + n + " of " + COMMUTES.size() + ", batches opened " + batches + ", batch draws " + draws;
    }
}
