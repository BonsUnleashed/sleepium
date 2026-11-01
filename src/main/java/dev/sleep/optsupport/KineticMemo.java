package dev.sleep.optsupport;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.KineticDebugger;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Create's kinetic block entities drawn without calling their renderer (opt-in {@code -Dlab.opt.KineticMemoMixin}, with
 * SbbPartsMixin). The first draw is recorded: every SuperByteBuffer part it makes (SbbPartsMixin -> PartBatch) is kept
 * with its pose relative to the dispatcher's, its template, colour, light, and - when Create's kineticRotationTransform
 * turned it (KineticRotationMixin) - the axis, the speed and the angle's offset, so the angle can be computed for any
 * time as Create does: ((time x speed x 0.3 + offset) % 360) degrees about the block's centre. While the block state,
 * the speed, the light, the overlay and the overstress effect stay the same, later frames draw those parts again from
 * the memo. A draw that made anything else (vertices on the CPU path, items, text) is not kept: the renderer then runs
 * every frame as before, the recording retried after a while.
 */
public final class KineticMemo implements BeMemo, PartBatch.SbbRecorder {
    public static final KineticMemo INSTANCE = new KineticMemo();
    public static long replayed, recorded, refused;
    /** not kept by cause: other output, no part, CPU-path part, turned after the turn, shift not of the family; by renderer */
    public static final long[] WHY = new long[5];
    private static final java.util.Map<String, long[]> BY_RENDERER = new java.util.HashMap<>();
    private int why = -1;

    private KineticMemo() {}

    public static void install() {
        RenderCache.BE_MEMO = INSTANCE;
        PartBatch.sbbRecorder = INSTANCE;
        if (KineticResident.ON) KineticResident.install();
        System.out.println("[sleep/opt] kinetic memo installed (Create's rotating parts from VRAM without their renderer)");
    }

    /** The overstress tint's state of a kinetic block entity (KineticBeMixin implements it). */
    public interface Effects {
        float sleep$overStressed();

        /** KineticResident: the block entity's parts in VRAM, or null */
        Object sleep$resident();

        void sleep$resident(Object r);

        /** bumped by every read of the block entity's data (client syncs): what is drawn may depend on it */
        int sleep$version();
    }

    static final class Part {
        RenderType type;
        int[] geo;
        final Matrix4f rel = new Matrix4f(), local = new Matrix4f();
        final Matrix3f nRel = new Matrix3f(), nLocal = new Matrix3f();
        Direction axis;   // null: not turned
        float speed, offsetDeg;
        float r, g, b, a;
        boolean diffuse;
        float fakeY;
        int light, overlay;
        /** a scrolling sprite shift (resident draws only): original u0, target u0, original v0, target v0 */
        float[] sprites;
        float scrollU, scrollSpeed, scrollPhase, scrollScale;
        /** chain conveyors' level of detail: 0 always drawn, 1 near only, 2 far only (by the distance to mx, my, mz) */
        byte lod;
        double mx, my, mz;
        /** another position whose light is in the geometry (a chain's far end): its section is watched */
        net.minecraft.core.BlockPos lightAt;
    }

    static final class Memo {
        BlockState state;
        float speed, effect;
        int light, overlay, version;
        /** parts with a scrolling shift: drawn on the CPU path unless resident; their vertices while recording */
        boolean scrolls;
        long shiftedVertices;
        final List<Part> parts = new ArrayList<>();
        boolean ok = true;
        long retryAt;
    }

    private final Map<Object, Memo> memos = new IdentityHashMap<>();
    private Memo rec;
    private KineticBlockEntity recBe;
    private final Matrix4f dispInv = new Matrix4f();
    private final Matrix3f dispNInv = new Matrix3f();
    private int sweep;

    private static float effect(KineticBlockEntity be) {
        return ((Effects) be).sleep$overStressed();
    }

    @Override
    public boolean render(Object obj, Class<?> rendererClass, RenderCache.Draw draw, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(obj instanceof KineticBlockEntity be) || KineticDebugger.isActive() || be.getLevel() == null) return false;
        if (++sweep >= 1 << 16) {   // forget removed block entities now and then
            sweep = 0;
            memos.keySet().removeIf(k -> ((KineticBlockEntity) k).isRemoved());
        }
        Memo m = memos.get(be);
        BlockState state = be.getBlockState();
        float speed = be.getSpeed(), effect = effect(be);
        int version = ((Effects) be).sleep$version();
        long now = be.getLevel().getGameTime();
        if (m != null && m.state == state && m.speed == speed && m.light == light && m.overlay == overlay && m.effect == effect && m.version == version) {
            if (m.ok) {
                if (KineticResident.ON && KineticResident.offer(m, be, pose.last(), rendererClass)) return true;   // drawn from VRAM from now on
                if (m.scrolls) return false;   // its scrolling parts only resident: the renderer draws it
                replay(m, be, pose, buffers);
                return true;
            }
            if (now < m.retryAt) return false;
        }
        // record: the renderer runs as usual; its parts are kept
        m = new Memo();
        m.state = state;
        m.speed = speed;
        m.effect = effect;
        m.light = light;
        m.overlay = overlay;
        m.version = version;
        dispInv.set(pose.last().pose()).invert();
        dispNInv.set(pose.last().normal()).invert();
        MultiBufferSource.BufferSource src = (MultiBufferSource.BufferSource) buffers;
        long before = RenderCache.vertices(src), empties = PartBatch.emptyQuadVertices;
        rec = m;
        recBe = be;
        why = -1;
        try {
            draw.draw(pose, buffers, pt);
        } finally {
            rec = null;
            recBe = null;
        }
        long drawn = RenderCache.vertices(src) - before - (PartBatch.emptyQuadVertices - empties) - m.shiftedVertices;
        if (m.ok && (drawn != 0 || m.parts.isEmpty())) {   // something besides parts (or nothing at all): not kept
            m.ok = false;
            why = drawn != 0 ? 0 : 1;
        }
        if (m.ok) recorded++;
        else {
            refused++;
            if (why >= 0) {
                WHY[why]++;
                BY_RENDERER.computeIfAbsent(rendererClass.getSimpleName(), x -> new long[5])[why]++;
            }
            m.parts.clear();
            m.retryAt = now + 100;
        }
        memos.put(be, m);
        return true;
    }

    // ------------------------------------------------------------------ recording (SbbPartsMixin -> PartBatch -> here)

    private Object pendSbb;
    private Direction pendAxis;
    private float pendAngle, pendSpeed, pendTime;
    private final Matrix4f pendBefore = new Matrix4f();
    private final Matrix3f pendBeforeN = new Matrix3f();

    /** KineticRotationMixin: kineticRotationTransform is about to turn this SuperByteBuffer (radians). */
    public static void rotating(Object sbb, KineticBlockEntity be, Direction.Axis axis, float angle) {
        KineticMemo k = INSTANCE;
        if (k.rec == null || !(sbb instanceof SbbLocal l)) return;
        k.pendSbb = sbb;
        k.pendAxis = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        k.pendAngle = angle;
        k.pendSpeed = be.getSpeed();
        k.pendTime = AnimationTickHolder.getRenderTime(be.getLevel());
        k.pendBefore.set(l.sleep$local().pose());
        k.pendBeforeN.set(l.sleep$local().normal());
    }

    @Override
    public boolean recording() {
        return rec != null;
    }

    @Override
    public void cpu() {
        if (rec != null) {
            rec.ok = false;
            if (why < 0) why = 2;
        }
    }

    private static final Matrix4f TMP = new Matrix4f();
    private static final Matrix3f TMPN = new Matrix3f();
    private static final Quaternionf Q = new Quaternionf();

    /** The turn rotateCentered(angle, axis) applies, about the block's centre (Flywheel's Affine.rotateCentered). */
    private static Matrix4f turn(Matrix4f into, Direction d, float angle) {
        Q.setAngleAxis(angle, d.getStepX(), d.getStepY(), d.getStepZ());
        return into.translation(0.5f, 0.5f, 0.5f).rotate(Q).translate(-0.5f, -0.5f, -0.5f);
    }

    @Override
    public void captured(Object sbb, PoseStack.Pose input, PoseStack.Pose local, float r, float g, float b, float a, boolean diffuse, float fakeY,
            int light, int overlay, RenderType type, int[] geo) {
        Memo m = rec;
        if (m == null || !m.ok) return;
        Part p = new Part();
        p.type = type;
        p.geo = geo;
        p.rel.set(dispInv).mul(input.pose());
        p.nRel.set(dispNInv).mul(input.normal());
        if (sbb == pendSbb) {   // turned by kineticRotationTransform: its local = before x turn(angle), checked
            TMP.set(pendBefore).mul(turn(new Matrix4f(), pendAxis, pendAngle));
            if (!TMP.equals(local.pose(), 1e-4f)) {
                m.ok = false;
                if (why < 0) why = 3;   // something else changed the transform after the turn
                return;
            }
            p.local.set(pendBefore);
            p.nLocal.set(pendBeforeN);
            p.axis = pendAxis;
            p.speed = pendSpeed;
            p.offsetDeg = pendAngle * 180f / (float) Math.PI - pendTime * pendSpeed * 3f / 10;
        } else {
            p.local.set(local.pose());
            p.nLocal.set(local.normal());
        }
        pendSbb = null;
        p.r = r;
        p.g = g;
        p.b = b;
        p.a = a;
        p.diffuse = diffuse;
        p.fakeY = fakeY;
        p.light = light;
        p.overlay = overlay;
        m.parts.add(p);
    }

    /**
     * A part with a scrolling sprite shift (Create's belts): the scroll is kept as the function of the time Create computes
     * it with - frac(s x speed x time / 504 + phase) x spriteSize x mult, s = +-1, phase 0 or 1/2, mult 1/2 or 3/8 - when
     * exactly one such function gives the observed value at this time (in Java's float / double steps); else not kept.
     */
    @Override
    public void capturedShifted(Object sbb, PoseStack.Pose input, PoseStack.Pose local, float r, float g, float b, float a, boolean diffuse,
            float fakeY, int light, int overlay, RenderType type, int[] geo, Object spriteShift, float scrollU, float scrollV, int vertexCount) {
        Memo m = rec;
        if (m == null || !m.ok) return;
        if (!(spriteShift instanceof net.createmod.catnip.render.SpriteShiftEntry e) || scrollU != 0f || recBe == null) {
            m.ok = false;
            if (why < 0) why = 4;
            return;
        }
        float time = AnimationTickHolder.getRenderTime(recBe.getLevel()), speed = recBe.getSpeed();
        float size = e.getTarget().getV1() - e.getTarget().getV0();
        float found = Float.NaN, phase = 0, mult = 0;
        int matches = 0;
        for (float sgn : new float[] {1f, -1f})
            for (float ph : new float[] {0f, 0.5f})
                for (float mu : new float[] {0.5f, 3f / 8f}) {
                    float sp = sgn * speed;
                    if (scrollOf(sp, time, ph, size, mu) != scrollV) continue;
                    if (matches > 0 && sp == found && ph == phase && mu == mult) continue;   // speed 0: the sign does not matter
                    matches++;
                    found = sp;
                    phase = ph;
                    mult = mu;
                }
        if (matches != 1) {
            m.ok = false;
            if (why < 0) why = 4;
            return;
        }
        int before = m.parts.size();
        captured(sbb, input, local, r, g, b, a, diffuse, fakeY, light, overlay, type, geo);
        if (!m.ok || m.parts.size() != before + 1) return;
        Part p = m.parts.get(before);
        p.sprites = new float[] {e.getOriginal().getU0(), e.getTarget().getU0(), e.getOriginal().getV0(), e.getTarget().getV0()};
        p.scrollU = scrollU;
        p.scrollSpeed = found;
        p.scrollPhase = phase;
        p.scrollScale = size * mult;
        m.scrolls = true;
        m.shiftedVertices += vertexCount;
    }

    /** BeltRenderer's scroll for a signed speed, phase and multiplier, in its float / double steps. */
    static float scrollOf(float speed, float time, float phase, float size, float mult) {
        double scroll = speed * time / (31.5 * 16) + phase;
        scroll = scroll - Math.floor(scroll);
        scroll = scroll * size * mult;
        return (float) scroll;
    }

    // ------------------------------------------------------------------ chain conveyors (ChainConveyorMemoMixin)

    /** a chain mesh: its length, the lights of its ends, near or far (ChainConveyorRenderer.renderChain's arguments) */
    private record ChainKey(float length, int light1, int light2, boolean far) {}

    private final java.util.HashMap<ChainKey, ChainKey> chainKeys = new java.util.HashMap<>();

    /** Our own chain mesh: Create's renderChain with no animation (minV 0), into PartBatch's geometry recorder. */
    private static final PartBatch.GeometrySource CHAIN_MESH = (a, unused, consumer) -> {
        ChainKey k = (ChainKey) a;
        com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer.renderChain(new PoseStack(), t -> consumer, 0f, k.length(), k.light1(), k.light2(), k.far());
        return true;
    };

    private int[] chainGeometry(float length, int l1, int l2, boolean far) {
        ChainKey k = chainKeys.computeIfAbsent(new ChainKey(length, l1, l2, far), x -> x);
        return PartBatch.geometryOf(k, CHAIN_MESH, k, null);
    }

    /**
     * A chain drawn while a chain conveyor is recorded (drawn the usual way this frame: its vertices expected): kept as
     * two parts, the near mesh scrolled on the GPU as Create animates it - frac(time x |speed| / 360) - 0.5, checked
     * against the animation it was drawn with - and the far one, chosen per frame by the distance to the chain's middle.
     */
    public void chain(PoseStack.Pose pose, float animation, float length, int light1, int light2,
            com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity be, net.minecraft.core.BlockPos offset, long vertices) {
        Memo m = rec;
        if (m == null || !m.ok) return;
        float time = AnimationTickHolder.getRenderTime(be.getLevel()), speed = Math.abs(be.getSpeed());
        float t = time / (360f / speed);
        t %= 1;
        if (t < 0) t += 1;
        double x = (double) speed * (504f / 360f) * time / 504.0;
        if (Math.abs((t - 0.5f) - animation) > 1e-4f || Math.abs((x - Math.floor(x)) - t) > 1e-3) {   // not the animation we can reproduce
            m.ok = false;
            if (why < 0) why = 4;
            return;
        }
        int[] near = chainGeometry(length, light1, light2, false), far = chainGeometry(length, light1, light2, true);
        net.minecraft.client.renderer.RenderType type = com.simibubi.create.foundation.render.RenderTypes.chain(
                com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer.CHAIN_LOCATION);
        if (near == null || far == null) {
            m.ok = false;
            return;
        }
        net.minecraft.core.BlockPos tile = be.getBlockPos();
        double mx = tile.getX() + 0.5 + offset.getX() / 2f, my = tile.getY() + 0.5 + offset.getY() / 2f, mz = tile.getZ() + 0.5 + offset.getZ() / 2f;
        for (int v = 0; v < 2; v++) {
            Part p = new Part();
            p.type = type;
            p.geo = v == 0 ? near : far;
            p.rel.set(dispInv).mul(pose.pose());
            p.nRel.set(dispNInv).mul(pose.normal());
            p.r = p.g = p.b = p.a = 1f;
            p.diffuse = false;
            p.fakeY = 1f;
            p.overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            p.lod = (byte) (v + 1);
            p.mx = mx;
            p.my = my;
            p.mz = mz;
            p.lightAt = tile.offset(offset);
            if (v == 0) {   // v' = v - 0.5 + frac(|speed| x 1.4 x time / 504)
                p.sprites = new float[] {0f, 0f, 0.5f, 0f};
                p.scrollSpeed = speed * (504f / 360f);
                p.scrollScale = 1f;
            }
            m.parts.add(p);
        }
        m.scrolls = true;   // resident only
        m.shiftedVertices += vertices;
    }

    // ------------------------------------------------------------------ replay

    private final Matrix4f m4 = new Matrix4f(), t4 = new Matrix4f();
    private final Matrix3f n3 = new Matrix3f(), t3 = new Matrix3f();

    private void replay(Memo m, KineticBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        PoseStack.Pose disp = pose.last();
        for (int i = 0, n = m.parts.size(); i < n; i++) {
            Part p = m.parts.get(i);
            BufferBuilder bb = PartBatch.sbbBuilder(buffers, p.type);
            if (bb == null) continue;
            m4.set(disp.pose()).mul(p.rel).mul(p.local);
            n3.set(disp.normal()).mul(p.nRel).mul(p.nLocal);
            if (p.axis != null) {   // Create's angle for this time (getAngleForBe's expression)
                float angle = ((time * p.speed * 3f / 10 + p.offsetDeg) % 360) / 180 * (float) Math.PI;
                turn(t4, p.axis, angle);
                m4.mul(t4);
                n3.mul(t4.get3x3(t3));
            }
            PartBatch.queueSbb(bb, p.type, p.geo, m4, n3, p.r, p.g, p.b, p.a, p.diffuse, p.fakeY, p.light, p.overlay);
        }
        replayed++;
    }

    @Override
    public String report() {
        StringBuilder by = new StringBuilder();
        BY_RENDERER.entrySet().stream().sorted((x, y) -> Long.compare(java.util.Arrays.stream(y.getValue()).sum(), java.util.Arrays.stream(x.getValue()).sum())).limit(8)
                .forEach(e -> by.append(' ').append(e.getKey()).append(java.util.Arrays.toString(e.getValue())));
        return String.format(java.util.Locale.ROOT, "kinetic memo: replayed %d, recorded %d, not kept %d %s%s", replayed, recorded, refused, java.util.Arrays.toString(WHY), by)
                + (KineticResident.ON ? "; " + KineticResident.report() : "");
    }
}
