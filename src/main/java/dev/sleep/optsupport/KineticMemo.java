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

    private KineticMemo() {}

    public static void install() {
        RenderCache.BE_MEMO = INSTANCE;
        PartBatch.sbbRecorder = INSTANCE;
        System.out.println("[sleep/opt] kinetic memo installed (Create's rotating parts from VRAM without their renderer)");
    }

    /** The overstress tint's state of a kinetic block entity (KineticBeMixin implements it). */
    public interface Effects {
        float sleep$overStressed();
    }

    private static final class Part {
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
    }

    private static final class Memo {
        BlockState state;
        float speed, effect;
        int light, overlay;
        final List<Part> parts = new ArrayList<>();
        boolean ok = true;
        long retryAt;
    }

    private final Map<Object, Memo> memos = new IdentityHashMap<>();
    private Memo rec;
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
        long now = be.getLevel().getGameTime();
        if (m != null && m.state == state && m.speed == speed && m.light == light && m.overlay == overlay && m.effect == effect) {
            if (m.ok) {
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
        dispInv.set(pose.last().pose()).invert();
        dispNInv.set(pose.last().normal()).invert();
        MultiBufferSource.BufferSource src = (MultiBufferSource.BufferSource) buffers;
        long before = RenderCache.vertices(src), empties = PartBatch.emptyQuadVertices;
        rec = m;
        try {
            draw.draw(pose, buffers, pt);
        } finally {
            rec = null;
        }
        long drawn = RenderCache.vertices(src) - before - (PartBatch.emptyQuadVertices - empties);
        if (drawn != 0 || m.parts.isEmpty()) m.ok = false;   // something besides parts (or nothing at all): not kept
        if (m.ok) recorded++;
        else {
            refused++;
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
        if (rec != null) rec.ok = false;
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
                m.ok = false;   // something else changed the transform after the turn
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
        return String.format(java.util.Locale.ROOT, "kinetic memo: replayed %d, recorded %d, not kept %d", replayed, recorded, refused);
    }
}
