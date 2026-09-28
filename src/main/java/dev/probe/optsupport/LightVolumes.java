package dev.probe.optsupport;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.probe.opt.BufferBuilderAccessor;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

/**
 * exp/shader-light (client), step 2: the recipes travel with the mesh. A section whose light has changed ("dynamic")
 * is compiled with recipes: every vertex of its BLOCK-format layers carries, in the normal + padding bytes (offset 28,
 * unread by the chunk shaders), either its light recipe (LightRecipe) or the sentinel (baked: the shader keeps UV2).
 * UV2 itself stays baked as vanilla's, so a section without a light volume renders exactly as before.
 *
 * <p>A recipe is written only when it reproduces the UV2 actually written for that vertex (so a mod's own light,
 * NeoForge's per-quad light data, full-bright quads... stay baked). Vertices that did not come through an AO or flat
 * quad of ModelBlockRenderer get the sentinel in a pass over each layer's buffer before it is built. Meshes with
 * recipes are marked (not packed into the 16-byte format, which has no room for them).
 */
public final class LightVolumes {
    private LightVolumes() {}

    /** -Dlab.shaderlight.all=true: every section compiled with recipes (tests) */
    public static final boolean ALL = Boolean.getBoolean("lab.shaderlight.all");
    public static final int SENTINEL = 0xFFFFFFFF;   // sample code 31: never a recipe
    private static final int STRIDE = 32, UV2 = 24, NORMAL = 28;

    /** sections (SectionPos longs) whose light changed since they were first shown: compiled with recipes */
    public static final Set<Long> DYNAMIC = ConcurrentHashMap.newKeySet();
    public static final AtomicLong recipeSections = new AtomicLong(), recipeVertices = new AtomicLong(), bakedVertices = new AtomicLong(),
            rejected = new AtomicLong();

    private static final class Compile {
        boolean on;
        final IdentityHashMap<BufferBuilder, BitSet> written = new IdentityHashMap<>();
        final int[] rec = new int[4];
    }

    private static final ThreadLocal<Compile> STATE = ThreadLocal.withInitial(Compile::new);

    public static void beginCompile(SectionPos section) {
        Compile c = STATE.get();
        c.on = ALL || DYNAMIC.contains(section.asLong());
        c.written.clear();
        if (c.on) recipeSections.incrementAndGet();
    }

    public static void endCompile() {
        Compile c = STATE.get();
        c.on = false;
        c.written.clear();
    }

    private static boolean blockBuilder(VertexConsumer vc) {
        return vc instanceof BufferBuilder bb && ((BufferBuilderAccessor) bb).probe$format() == DefaultVertexFormat.BLOCK;
    }

    /** After ModelBlockRenderer.putQuadData: the quad's four vertices are the builder's last four. */
    public static void quadWritten(BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, java.util.BitSet shapeFlags, VertexConsumer consumer, boolean ao) {
        Compile c = STATE.get();
        if (!c.on || !blockBuilder(consumer)) return;
        BufferBuilder bb = (BufferBuilder) consumer;
        BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
        int n = a.probe$vertices();
        if (n < 4) return;
        long last = a.probe$vertexPointer();
        boolean have;
        if (ao) {
            have = LightRecipe.integerPath(quad.getDirection(), shapeFlags);
            if (have) LightRecipe.recipes(level, pos, quad.getDirection(), shapeFlags, c.rec);
        } else {
            // NeoForge's calculateFaceWithoutAO (quads without AO) and flat lighting: one sample, four times
            BlockPos lp = shapeFlags.get(0) ? pos.relative(quad.getDirection()) : pos;
            int one = LightRecipe.flat(pos, lp);
            c.rec[0] = c.rec[1] = c.rec[2] = c.rec[3] = one;
            have = true;
        }
        BitSet w = c.written.computeIfAbsent(bb, k -> new BitSet());
        for (int i = 0; i < 4; i++) {
            long v = last - (long) (3 - i) * STRIDE;
            int value = SENTINEL;
            if (have) {
                int uv2 = MemoryUtil.memGetInt(v + UV2);
                if (LightRecipe.emulate(level, pos, c.rec[i]) == uv2) value = c.rec[i];
                else rejected.incrementAndGet();
            }
            MemoryUtil.memPutInt(v + NORMAL, value);
            if (value == SENTINEL) bakedVertices.incrementAndGet();
            else recipeVertices.incrementAndGet();
            w.set(n - 4 + i);
        }
    }

    /** SectionCompiler, before a layer's builder is built: the vertices no quad hook wrote get the sentinel. */
    public static boolean beforeBuild(BufferBuilder bb) {
        Compile c = STATE.get();
        if (!c.on || !blockBuilder(bb)) return false;
        BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
        int n = a.probe$vertices();
        if (n == 0) return false;
        long first = a.probe$vertexPointer() - (long) (n - 1) * STRIDE;
        BitSet w = c.written.get(bb);
        for (int i = 0; i < n; i++) {
            if (w != null && w.get(i)) continue;
            MemoryUtil.memPutInt(first + (long) i * STRIDE + NORMAL, SENTINEL);
            bakedVertices.incrementAndGet();
        }
        return true;
    }

    /** Implemented on MeshData: built with recipes. */
    public interface RecipeMesh {
        boolean probe$recipes();

        void probe$setRecipes(boolean v);
    }

    public static boolean hasRecipes(MeshData mesh) {
        return mesh instanceof RecipeMesh r && r.probe$recipes();
    }

    public static String report() {
        long t = Math.max(1, recipeVertices.get() + bakedVertices.get());
        return String.format(java.util.Locale.ROOT, "shader light: %d sections compiled with recipes, vertices: recipe %.2f %%, baked %.2f %% (recipe rejected by the UV2 check %d)",
                recipeSections.get(), 100.0 * recipeVertices.get() / t, 100.0 * bakedVertices.get() / t, rejected.get());
    }

    /** the light a flat sample uses (getLightColor of the state there), for completeness of the recipe check */
    static int lightAt(BlockAndTintGetter level, BlockPos p) {
        return LevelRenderer.getLightColor(level, level.getBlockState(p), p);
    }
}
