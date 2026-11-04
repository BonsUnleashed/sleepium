package dev.lull.shaders;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.lull.shadermixin.ShaderBuilderAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

/**
 * The ids a pack's programs read per vertex (mc_Entity, entityId / blockEntityId, currentRenderedItemId), carried in
 * vanilla's vertex bytes - no format change, so the block-mesh memo, the render cache and the batches copy them along:
 * <ul>
 * <li>terrain (BLOCK): the light shorts' high bytes hold the block id + 1 (16 bits; light is 0..240), the normal's
 * padding byte the block's light emission (bits 0-3) and a fluid flag (bit 4); written per block as the section is
 * compiled (each block's vertices patched before the next block's are written);</li>
 * <li>entities (NEW_ENTITY), only while a pack renders the level: the overlay shorts' high bytes hold the entity id (or
 * the block entity's id inside a block entity render), the light shorts' high bytes the rendered item's id + 1.</li>
 * </ul>
 * The pack programs' vertex prologue takes them back out and masks the shorts (PackShader).
 */
public final class ShaderIds {
    private ShaderIds() {}

    /** a pack renders the level now (entity vertices get ids) */
    public static boolean on;
    public static IdMaps maps;
    /** the overlay slot's id (entity or block entity) and the item id + 1, while their renders run */
    public static int slot1, item1;
    public static volatile long blocks, patched, patchedWithId;

    public static int overlay(int packedOverlay) {
        int id = slot1;
        return packedOverlay & 0x00FF00FF | (id & 0xFF) << 8 | (id >> 8 & 0xFF) << 24;
    }

    public static int light(int packedLight) {
        int id = item1;
        return packedLight & 0x00FF00FF | (id & 0xFF) << 8 | (id >> 8 & 0xFF) << 24;
    }

    // ------------------------------------------------------------------ terrain: per block, during section compiles

    /** per compile thread: the last block's builder and first vertex, patched when the next block starts */
    static final class Pending {
        BufferBuilder builder;
        int from;
        int value;   // id + 1 | emission << 16 | fluid << 20
    }

    private static final ThreadLocal<Pending> PENDING = ThreadLocal.withInitial(Pending::new);

    /** before a block (or fluid) is written into builder: the previous one is patched, this one remembered */
    public static void beginBlock(BufferBuilder builder, BlockState state, boolean fluid) {
        Pending p = PENDING.get();
        flush(p);
        IdMaps m = maps;
        if (m == null) return;
        blocks++;
        int id = m.block(state) + 1;
        p.builder = builder;
        p.from = ((ShaderBuilderAccessor) builder).sleep$shVertices();
        p.value = (id & 0xFFFF) | (state.getLightEmission() & 15) << 16 | (fluid ? 1 : 0) << 20;
    }

    /** after the last block of a section */
    public static void endSection() {
        flush(PENDING.get());
    }

    private static void flush(Pending p) {
        BufferBuilder b = p.builder;
        if (b == null) return;
        p.builder = null;
        ShaderBuilderAccessor a = (ShaderBuilderAccessor) b;
        if (a.sleep$shFormat() != DefaultVertexFormat.BLOCK || !a.sleep$shBuilding()) return;
        int n = a.sleep$shVertices() - p.from;
        if (n <= 0) return;
        // the last vertex's address (1.21.1: BufferBuilder's vertexPointer; 1.20.1: the write offset after it)
        long last = MemoryUtil.memAddress0(a.sleep$shBuffer()) + a.sleep$shNext() - 32;
        patched += n;
        if (p.value != 0 && (p.value & 0xFFFF) != 0) patchedWithId += n;
        int id = p.value & 0xFFFF;
        byte pad = (byte) (p.value >> 16 & 31);
        for (int i = 0; i < n; i++) {
            long v = last - (long) i * 32;
            MemoryUtil.memPutByte(v + 25, (byte) id);
            MemoryUtil.memPutByte(v + 27, (byte) (id >> 8));
            MemoryUtil.memPutByte(v + 31, pad);
        }
    }
}
