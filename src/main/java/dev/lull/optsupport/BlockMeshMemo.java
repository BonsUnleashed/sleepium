package dev.lull.optsupport;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.lull.opt.BufferBuilderAccessor;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * exp/block-mesh-memo (client, part of the render cache engine): a block model tesselated against the live world
 * outside the chunk meshes — moving piston blocks, falling blocks, any renderer that calls
 * ModelBlockRenderer.tesselateBlock with the client level — recorded once in model space and replayed with the frame's
 * pose until the world around it changes.
 *
 * <p>The output of tesselateWithAO / tesselateWithoutAO in model space depends on the model, the block state, the seed,
 * checkSides, the overlay, the model data, the render type — and on the world within one block of pos (shouldRenderFace,
 * AO, light, tint): pos itself only through those reads. Validity uses the rule vanilla uses for chunk meshes: a block
 * change at B marks the sections of B±1 dirty (LevelRenderer.setBlocksDirty), a light change marks its section and its
 * neighbours (ClientChunkCache.onLightUpdate), so any change within one block of pos marks pos's own section. A record
 * is replayed while that section has not been marked since it was recorded. What no mark covers: a block of dynamic
 * shape (hasDynamicShape: its shape comes from its block entity, e.g. a moving piston, whose collision shape — and so
 * the AO shade of its neighbours — follows its progress every tick) within one block: such a record is valid for its
 * tick only (found by a scan of the 27 blocks when recording). And every record is renewed after REFRESH ticks, the
 * bound for anything else (a mod's shade brightness from block entity data). Replay: one copy plus the pose's
 * transform per vertex (float rounding may differ from vanilla's, as in the rest of the render cache engine).
 *
 * <p>Per thread (the render thread and ParallelRender's workers); the section marks are written on the render thread
 * only, never while entity jobs run. {@code -Dlab.bmesh.verify=true}: every hit also tesselated fresh and compared.
 */
public final class BlockMeshMemo {
    private BlockMeshMemo() {}

    public static final boolean VERIFY = Boolean.getBoolean("lab.bmesh.verify");
    private static final int SWEEP_FRAMES = 128, KEEP_FRAMES = 64, MAX_MARKS = 1 << 16;
    private static final int REFRESH = Integer.getInteger("lab.bmesh.refresh", 20);
    /**
     * ticks a record next to a block of dynamic shape stays valid: 1 = exact (its tick only); 0 = such neighbours
     * ignored (faster: their AO shade may lag behind a moving piston until a mark or REFRESH)
     */
    private static final int DYNAMIC_TICKS = Integer.getInteger("lab.bmesh.dynamicTicks", 1);

    // ------------------------------------------------------------------ section marks (render thread)

    private static final Long2LongOpenHashMap MARKS = new Long2LongOpenHashMap();
    /** every mark gets the next epoch; records older than floor are stale (after a clear of MARKS) */
    private static volatile long epoch = 1, floor;
    public static volatile long frame;
    public static long hits, records, stale, bypass, verified, mismatches;

    public static void marked(int sx, int sy, int sz) {
        if (MARKS.size() >= MAX_MARKS) clearAll();
        MARKS.put(SectionPos.asLong(sx, sy, sz), ++epoch);
    }

    /** a new level, a renderer reset (allChanged): everything recorded is stale */
    public static void clearAll() {
        MARKS.clear();
        floor = ++epoch;
    }

    private static boolean valid(long sectionKey, long recorded) {
        return recorded > floor && MARKS.get(sectionKey) < recorded && !(LIGHT_PENDING && ClientLightAsync.lightPending(sectionKey));
    }

    /** -Dlab.bmesh.lightPending=false: records of sections whose new light is not marked yet served anyway (1.1.9's way, for A/B) */
    private static final boolean LIGHT_PENDING = !"false".equals(System.getProperty("lab.bmesh.lightPending"));

    // ------------------------------------------------------------------ records (per thread)

    static final class Entry {
        Entry next;
        long hash, pos, seed, epochAt, lastUsed, tickAt;
        boolean dynamic;
        BlockState state;
        BakedModel model;
        Object modelData;
        RenderType renderType;
        VertexFormat format;
        int overlay, flags;
        long mem;
        int n, cap;
    }

    static final class Memo {
        final Long2ObjectOpenHashMap<Entry> map = new Long2ObjectOpenHashMap<>();
        final PoseStack local = new PoseStack();
        final Matrix4f m = new Matrix4f();
        final Matrix3f nrm = new Matrix3f();
        final Matrix3f identity3 = new Matrix3f();
        final java.util.Map<VertexFormat, RecordingBuilder> recorders = new java.util.IdentityHashMap<>();
        long lastSweep;
        /** between begin (RECORD) and end: the entry being recorded, its epoch, whether it is a verification */
        Entry pending;
        long pendingAt;
        boolean verifying;
        long pendingTick;
        RecordingBuilder rec;
        final BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();

        RecordingBuilder recorder(VertexFormat f) {
            RecordingBuilder r = recorders.get(f);
            if (r == null) recorders.put(f, r = new RecordingBuilder(f));
            r.reset();
            return r;
        }

        void sweep(long now) {
            lastSweep = now;
            ObjectIterator<Long2ObjectOpenHashMap.Entry<Entry>> it = map.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var me = it.next();
                Entry head = me.getValue(), keep = null, tail = null;
                for (Entry e = head; e != null; ) {
                    Entry nx = e.next;
                    if (now - e.lastUsed > KEEP_FRAMES) {
                        if (e.mem != 0) MemoryUtil.nmemFree(e.mem);
                        e.mem = 0;
                    } else {
                        e.next = null;
                        if (keep == null) keep = e;
                        else tail.next = e;
                        tail = e;
                    }
                    e = nx;
                }
                if (keep == null) it.remove();
                else if (keep != head) me.setValue(keep);
            }
        }
    }

    private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);

    /** begin's answers: the call was served; run it the vanilla way; run it into recordPose() / recorder(), then end() */
    public static final int SERVED = 0, VANILLA = 1, RECORD = 2;

    private static boolean supported(VertexFormat f) {
        for (VertexFormatElement e : f.getElements())
            if (e != VertexFormatElement.POSITION && e != VertexFormatElement.COLOR && e != VertexFormatElement.UV0 && e != VertexFormatElement.UV1
                    && e != VertexFormatElement.UV2 && e != VertexFormatElement.NORMAL) return false;
        return true;
    }

    private static VertexFormat formatOf(VertexConsumer vc) {
        if (vc instanceof BufferBuilder bb) {
            BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
            return a.sleep$mode() == VertexFormat.Mode.QUADS ? a.sleep$format() : null;
        }
        if (vc instanceof RecordingBuilder rb) return rb.format;
        return null;
    }

    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }

    /**
     * Before a tesselateWithAO / tesselateWithoutAO call: SERVED (replayed into consumer with pose), VANILLA, or RECORD
     * (the caller runs the call with recordPose() into recorder(), then calls end(pose, consumer)).
     */
    public static int begin(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack pose, VertexConsumer consumer,
                            boolean checkSides, long seed, int overlay, Object modelData, RenderType renderType, boolean ao) {
        if (level != Minecraft.getInstance().level || level == null) return VANILLA;
        VertexFormat f = formatOf(consumer);
        if (f == null || !supported(f)) {
            bypass++;
            return VANILLA;
        }
        Memo memo = MEMO.get();
        if (memo.pending != null) return VANILLA;   // a tesselation inside a recorded one: left alone
        long now = frame;
        if (now - memo.lastSweep > SWEEP_FRAMES) memo.sweep(now);
        long p = pos.asLong();
        int flags = (checkSides ? 1 : 0) | (ao ? 2 : 0);
        long h = mix(mix(mix(mix(mix(mix(mix(p, System.identityHashCode(state)), System.identityHashCode(model)), seed), flags * 31L + overlay),
                System.identityHashCode(renderType)), System.identityHashCode(f)), System.identityHashCode(modelData));
        Entry head = memo.map.get(h), e = head;
        while (e != null && !(e.hash == h && e.pos == p && e.state == state && e.model == model && e.seed == seed && e.flags == flags && e.overlay == overlay
                && e.renderType == renderType && e.format == f && e.modelData == modelData)) e = e.next;
        long section = SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
        long tick = ((net.minecraft.world.level.Level) level).getGameTime();
        boolean hit = e != null && valid(section, e.epochAt) && (e.dynamic && DYNAMIC_TICKS > 0 ? tick - e.tickAt < DYNAMIC_TICKS : tick - e.tickAt < REFRESH);
        if (hit) {
            e.lastUsed = now;
            if (!VERIFY) {
                replay(memo, e, pose, consumer);
                hits++;
                return SERVED;
            }
        } else if (e == null) {
            e = new Entry();
            e.hash = h;
            e.pos = p;
            e.state = state;
            e.model = model;
            e.seed = seed;
            e.flags = flags;
            e.overlay = overlay;
            e.renderType = renderType;
            e.format = f;
            e.modelData = modelData;
            e.next = head;
            memo.map.put(h, e);
        } else {
            stale++;
        }
        memo.pending = e;
        memo.pendingAt = epoch;   // marks from here on make the record stale
        memo.verifying = hit;
        memo.pendingTick = tick;
        if (!hit) e.dynamic = DYNAMIC_TICKS > 0 && dynamicAround(memo.scan, level, pos);
        memo.rec = memo.recorder(f);
        memo.local.setIdentity();
        return RECORD;
    }

    /** a block within one block of pos whose shape is not a function of its state */
    private static boolean dynamicAround(BlockPos.MutableBlockPos m, BlockAndTintGetter level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (level.getBlockState(m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)).getBlock().hasDynamicShape()) return true;
        return false;
    }

    public static PoseStack recordPose() {
        return MEMO.get().local;
    }

    public static VertexConsumer recorder() {
        return MEMO.get().rec;
    }

    /** After the RECORD run: store it (or compare it, when verifying) and replay into the real consumer. */
    public static void end(PoseStack pose, VertexConsumer consumer) {
        Memo memo = MEMO.get();
        Entry e = memo.pending;
        memo.pending = null;
        RecordingBuilder r = memo.rec;
        VertexFormat f = e.format;
        if (memo.verifying) {
            verified++;
            boolean same = r.n == e.n;
            String what = "count";
            if (same) {
                boolean[] used = RenderCache.usedBytes(f);
                int vs = f.getVertexSize();
                for (long i = 0; i < (long) e.n * vs && same; i++)
                    if (used[(int) (i % vs)] && MemoryUtil.memGetByte(r.mem + i) != MemoryUtil.memGetByte(e.mem + i)) {
                        same = false;
                        int off = (int) (i % vs);
                        what = "?";
                        for (VertexFormatElement el : f.getElements())
                            if (off >= f.getOffset(el) && off < f.getOffset(el) + el.byteSize()) what = f.getElementName(el);
                    }
            }
            if (!same) {
                mismatches++;
                String k = e.state.getBlock().getDescriptionId().replace("block.minecraft.", "") + "/" + what + (lightPending() ? "/lightPending" : "");
                synchronized (WHY) {
                    WHY.merge(k, 1L, Long::sum);
                }
            }
            hits++;
        } else {
            int bytes = r.n * f.getVertexSize();
            if (bytes > e.cap) {
                e.mem = e.mem == 0 ? MemoryUtil.nmemAlloc(bytes) : MemoryUtil.nmemRealloc(e.mem, bytes);
                e.cap = bytes;
            }
            if (bytes > 0) MemoryUtil.memCopy(r.mem, e.mem, bytes);
            e.n = r.n;
            e.epochAt = memo.pendingAt;
            e.lastUsed = frame;
            e.tickAt = memo.pendingTick;
            records++;
        }
        replay(memo, e, pose, consumer);
    }

    /** The RECORD run threw: nothing stored. */
    public static void abort() {
        MEMO.get().pending = null;
    }

    private static void replay(Memo memo, Entry e, PoseStack pose, VertexConsumer consumer) {
        int n = e.n;
        if (n == 0) return;
        VertexFormat f = e.format;
        int vs = f.getVertexSize();
        Matrix4f m = memo.m.set(pose.last().pose());
        Matrix3f nm = memo.nrm.set(pose.last().normal());
        boolean normalIdentity = nm.equals(memo.identity3);
        long dst;
        if (consumer instanceof BufferBuilder bb && ((BufferBuilderAccessor) (Object) bb).sleep$elementsToFill() == 0) {
            BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
            dst = a.sleep$buffer().reserve(n * vs);
            MemoryUtil.memCopy(e.mem, dst, (long) n * vs);
            transform(dst, n, f, m, nm, normalIdentity);
            a.sleep$setVertices(a.sleep$vertices() + n);
            a.sleep$setVertexPointer(dst + (long) (n - 1) * vs);
        } else if (consumer instanceof RecordingBuilder rb) {
            dst = rb.appendRaw(n);
            MemoryUtil.memCopy(e.mem, dst, (long) n * vs);
            transform(dst, n, f, m, nm, normalIdentity);
        } else {
            // a BufferBuilder with a vertex still open: through its calls
            long tmp = MemoryUtil.nmemAlloc((long) n * vs);
            MemoryUtil.memCopy(e.mem, tmp, (long) n * vs);
            transform(tmp, n, f, m, nm, normalIdentity);
            generic(consumer, tmp, n, f);
            MemoryUtil.nmemFree(tmp);
        }
    }

    private static void transform(long base, int n, VertexFormat f, Matrix4f m, Matrix3f nm, boolean normalIdentity) {
        int vs = f.getVertexSize(), po = f.getOffset(VertexFormatElement.POSITION), no = f.contains(VertexFormatElement.NORMAL) ? f.getOffset(VertexFormatElement.NORMAL) : -1;
        for (int i = 0; i < n; i++) {
            long v = base + (long) i * vs;
            float x = MemoryUtil.memGetFloat(v + po), y = MemoryUtil.memGetFloat(v + po + 4), z = MemoryUtil.memGetFloat(v + po + 8);
            MemoryUtil.memPutFloat(v + po, m.m00() * x + m.m10() * y + m.m20() * z + m.m30());
            MemoryUtil.memPutFloat(v + po + 4, m.m01() * x + m.m11() * y + m.m21() * z + m.m31());
            MemoryUtil.memPutFloat(v + po + 8, m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
            if (no >= 0 && !normalIdentity) {
                float nx = MemoryUtil.memGetByte(v + no) / 127f, ny = MemoryUtil.memGetByte(v + no + 1) / 127f, nz = MemoryUtil.memGetByte(v + no + 2) / 127f;
                float tx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz, ty = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz, tz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
                float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (len > 0) {
                    tx /= len;
                    ty /= len;
                    tz /= len;
                }
                MemoryUtil.memPutByte(v + no, (byte) ((int) (net.minecraft.util.Mth.clamp(tx, -1f, 1f) * 127f) & 0xFF));
                MemoryUtil.memPutByte(v + no + 1, (byte) ((int) (net.minecraft.util.Mth.clamp(ty, -1f, 1f) * 127f) & 0xFF));
                MemoryUtil.memPutByte(v + no + 2, (byte) ((int) (net.minecraft.util.Mth.clamp(tz, -1f, 1f) * 127f) & 0xFF));
            }
        }
    }

    /** already transformed vertices through the consumer's calls */
    private static void generic(VertexConsumer vc, long src, int n, VertexFormat f) {
        int vs = f.getVertexSize();
        for (int i = 0; i < n; i++) {
            long v = src + (long) i * vs;
            int po = f.getOffset(VertexFormatElement.POSITION);
            vc.addVertex(MemoryUtil.memGetFloat(v + po), MemoryUtil.memGetFloat(v + po + 4), MemoryUtil.memGetFloat(v + po + 8));
            if (f.contains(VertexFormatElement.COLOR)) {
                long c = v + f.getOffset(VertexFormatElement.COLOR);
                vc.setColor(MemoryUtil.memGetByte(c) & 0xFF, MemoryUtil.memGetByte(c + 1) & 0xFF, MemoryUtil.memGetByte(c + 2) & 0xFF, MemoryUtil.memGetByte(c + 3) & 0xFF);
            }
            if (f.contains(VertexFormatElement.UV0)) {
                long u = v + f.getOffset(VertexFormatElement.UV0);
                vc.setUv(MemoryUtil.memGetFloat(u), MemoryUtil.memGetFloat(u + 4));
            }
            if (f.contains(VertexFormatElement.UV1)) {
                long u = v + f.getOffset(VertexFormatElement.UV1);
                vc.setUv1(MemoryUtil.memGetShort(u), MemoryUtil.memGetShort(u + 2));
            }
            if (f.contains(VertexFormatElement.UV2)) {
                long u = v + f.getOffset(VertexFormatElement.UV2);
                vc.setUv2(MemoryUtil.memGetShort(u), MemoryUtil.memGetShort(u + 2));
            }
            if (f.contains(VertexFormatElement.NORMAL)) {
                long u = v + f.getOffset(VertexFormatElement.NORMAL);
                vc.setNormal(MemoryUtil.memGetByte(u) / 127f, MemoryUtil.memGetByte(u + 1) / 127f, MemoryUtil.memGetByte(u + 2) / 127f);
            }
        }
    }

    private static final java.util.Map<String, Long> WHY = new java.util.TreeMap<>();

    private static boolean lightPending() {
        return ClientLightAsync.running();
    }

    public static String report() {
        return "block mesh memo: hits " + hits + ", records " + records + " (stale " + stale + "), bypassed " + bypass
                + (VERIFY ? ", verified " + verified + ", mismatches " + mismatches + " " + WHY : "");
    }
}
