package dev.lull.optsupport;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.system.MemoryUtil;

/**
 * RenderCache / ParallelRender: a reusable vertex recorder writing the same bytes a BufferBuilder of the format writes,
 * into one native buffer kept across recordings — no BufferBuilder or RenderedBuffer per recording.
 *
 * <p>1.20.1's BufferBuilder fills a vertex element by element in the format's order: each setter writes only when the
 * current element is its own (else it does nothing), nextElement steps over padding and puts the default colour when
 * one is set, endVertex wants every element filled, lines modes write each vertex twice. This class is the same state
 * machine: it is a BufferVertexConsumer, so the element writers are vanilla's own default methods, and nextElement /
 * endVertex / color / the 14-argument fast path (NEW_ENTITY and BLOCK) are BufferBuilder's bodies. Padding bytes are
 * never written (as in BufferBuilder): RenderCache compares and copies them as they are.
 */
public final class RecordingBuilder extends DefaultedVertexConsumer implements BufferVertexConsumer {
    final VertexFormat format;
    final VertexFormat.Mode mode;
    /** the render type this builder records (set by RenderCache.Recorder) */
    public net.minecraft.client.renderer.RenderType type;
    final int vertexSize;
    private final ImmutableList<VertexFormatElement> elements;
    private final boolean fastFormat, fullFormat, lines;
    long mem;
    private long cap;
    int n;
    /** BufferBuilder's nextElementByte: where the current element goes */
    private int next;
    private VertexFormatElement current;
    private int elementIndex;
    /** VertexTags: runs of vertices written under one state - each run's first vertex and its three ids */
    int runs;
    int[] runAt = new int[2], runTag = new int[6];
    private final int[] now = new int[3];

    public RecordingBuilder(VertexFormat format, VertexFormat.Mode mode) {
        this.format = format;
        this.mode = mode;
        this.vertexSize = format.getVertexSize();
        this.elements = format.getElements();
        this.fastFormat = format == DefaultVertexFormat.NEW_ENTITY || format == DefaultVertexFormat.BLOCK;
        this.fullFormat = format == DefaultVertexFormat.NEW_ENTITY;
        this.lines = mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP;
        reset();
    }

    /** The element's byte offset in the format, or -1 (1.20.1's VertexFormat has offsets by index only). */
    static int offset(VertexFormat f, VertexFormatElement e) {
        ImmutableList<VertexFormatElement> es = f.getElements();
        for (int i = 0; i < es.size(); i++) if (es.get(i).equals(e)) return f.getOffset(i);
        return -1;
    }

    /** a new recording (BufferBuilder.begin: the first element, nothing written) */
    public void reset() {
        n = 0;
        next = 0;
        elementIndex = 0;
        current = elements.get(0);
        runs = 0;
        ensure(vertexSize);
    }

    private void ensure(long bytes) {
        if (bytes <= cap) return;
        long c = Math.max(bytes, Math.max(4096, cap * 2));
        mem = mem == 0 ? MemoryUtil.nmemAlloc(c) : MemoryUtil.nmemRealloc(mem, c);
        cap = c;
    }

    /** a new run when the state differs from the last run's */
    private void tag(int vertex) {
        VertexTags.read(now, 0);
        if (runs > 0) {
            int k = (runs - 1) * 3;
            if (runTag[k] == now[0] && runTag[k + 1] == now[1] && runTag[k + 2] == now[2]) return;
        }
        if (runs == runAt.length) {
            runAt = java.util.Arrays.copyOf(runAt, runs * 2);
            runTag = java.util.Arrays.copyOf(runTag, runs * 6);
        }
        runAt[runs] = vertex;
        System.arraycopy(now, 0, runTag, runs * 3, 3);
        runs++;
    }

    /** count whole vertices appended by the caller (BlockMeshMemo's replay): their first byte */
    long appendRaw(int count) {
        if (elementIndex != 0) throw new IllegalStateException("Not filled all elements of the vertex");
        ensure((long) (n + count + 1) * vertexSize);
        long dst = mem + next;
        if (VertexTags.ON) tag(n);
        n += count;
        next += count * vertexSize;
        return dst;
    }

    // ------------------------------------------------------------------ BufferVertexConsumer: the element cursor

    @Override
    public VertexFormatElement currentElement() {
        return current;
    }

    @Override
    public void nextElement() {   // BufferBuilder.nextElement
        elementIndex = (elementIndex + 1) % elements.size();
        next += current.getByteSize();
        current = elements.get(elementIndex);
        if (current.getUsage() == VertexFormatElement.Usage.PADDING) nextElement();
        if (defaultColorSet && current.getUsage() == VertexFormatElement.Usage.COLOR) BufferVertexConsumer.super.color(defaultR, defaultG, defaultB, defaultA);
    }

    @Override
    public void putByte(int index, byte value) {
        MemoryUtil.memPutByte(mem + next + index, value);
    }

    @Override
    public void putShort(int index, short value) {
        MemoryUtil.memPutShort(mem + next + index, value);
    }

    @Override
    public void putFloat(int index, float value) {
        MemoryUtil.memPutFloat(mem + next + index, value);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (VertexTags.ON && elementIndex == 0 && current.getUsage() == VertexFormatElement.Usage.POSITION) tag(n);
        return BufferVertexConsumer.super.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {   // BufferBuilder.color
        if (defaultColorSet) throw new IllegalStateException();
        return BufferVertexConsumer.super.color(red, green, blue, alpha);
    }

    @Override
    public void endVertex() {   // BufferBuilder.endVertex
        if (elementIndex != 0) throw new IllegalStateException("Not filled all elements of the vertex");
        n++;
        ensure((long) next + vertexSize);
        if (lines) {
            MemoryUtil.memCopy(mem + next - vertexSize, mem + next, vertexSize);
            next += vertexSize;
            n++;
            ensure((long) next + vertexSize);
        }
    }

    /** a shaderpack's ids, as BufferBuilder gets them (ShaderVertexIdsMixin) */
    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        if (dev.lull.shaders.ShaderIds.on && format == com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY) {
            int id = dev.lull.shaders.ShaderIds.slot1;
            u = u & 0xFF | (id & 0xFF) << 8;
            v = v & 0xFF | (id >> 8 & 0xFF) << 8;
        }
        return BufferVertexConsumer.super.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        if (dev.lull.shaders.ShaderIds.on && format == com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY) {
            int id = dev.lull.shaders.ShaderIds.item1;
            u = u & 0xFF | (id & 0xFF) << 8;
            v = v & 0xFF | (id >> 8 & 0xFF) << 8;
        }
        return BufferVertexConsumer.super.uv2(u, v);
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float texU, float texV, int overlayUV, int lightmapUV,
            float normalX, float normalY, float normalZ) {   // BufferBuilder's fast path
        if (defaultColorSet) throw new IllegalStateException();
        if (dev.lull.shaders.ShaderIds.on && format == com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY) {
            overlayUV = dev.lull.shaders.ShaderIds.overlay(overlayUV);
            lightmapUV = dev.lull.shaders.ShaderIds.light(lightmapUV);
        }
        if (!fastFormat) {
            super.vertex(x, y, z, red, green, blue, alpha, texU, texV, overlayUV, lightmapUV, normalX, normalY, normalZ);
            return;
        }
        if (VertexTags.ON) tag(n);
        putFloat(0, x);
        putFloat(4, y);
        putFloat(8, z);
        putByte(12, (byte) ((int) (red * 255.0F)));
        putByte(13, (byte) ((int) (green * 255.0F)));
        putByte(14, (byte) ((int) (blue * 255.0F)));
        putByte(15, (byte) ((int) (alpha * 255.0F)));
        putFloat(16, texU);
        putFloat(20, texV);
        int i;
        if (fullFormat) {
            putShort(24, (short) (overlayUV & 0xFFFF));
            putShort(26, (short) (overlayUV >> 16 & 0xFFFF));
            i = 28;
        } else {
            i = 24;
        }
        putShort(i, (short) (lightmapUV & 0xFFFF));
        putShort(i + 2, (short) (lightmapUV >> 16 & 0xFFFF));
        putByte(i + 4, BufferVertexConsumer.normalIntValue(normalX));
        putByte(i + 5, BufferVertexConsumer.normalIntValue(normalY));
        putByte(i + 6, BufferVertexConsumer.normalIntValue(normalZ));
        next += i + 8;
        endVertex();
    }
}
