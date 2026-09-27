package dev.probe.optsupport;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lab.runtime.LabRuntime;
import dev.lab.runtime.OsThreads;
import dev.probe.opt.EntityRenderDispatcherInvoker;
import dev.probe.opt.LevelRendererInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * exp/parallel-render (client): entity vertices generated on worker threads, bit-exact.
 *
 * <p>LevelRenderer's entity loop no longer draws: each entity becomes a job in an ordered frame queue. An eligible one is
 * rendered at once by a worker — the same computation as renderEntity + EntityRenderDispatcher.render (same
 * camera-relative pose, light, offset, shadow) into the worker's own recording source — with its own full set of
 * entity renderers, item renderer and hand renderer (vanilla shares one model per entity type and setupAnim writes
 * into it). After the loop, the render thread walks the queue in order: a worker job's bytes are appended per render
 * type in the order they were asked for, the others are rendered there the vanilla way. Same bytes in the same order
 * as vanilla, so the frame hash holds; {@code -Dlab.prender.verify=true} also renders every worker job on the render
 * thread and compares the bytes.
 *
 * <p>No game logic runs meanwhile: the render thread waits for the workers, and the client tick runs on it. Kept on the
 * render thread: players, named entities (text may upload glyphs), burning ones (flame uses the camera), outline
 * rendering, hitboxes, render events with listeners (a mod's listener must not run on a worker), and — with the render
 * cache on — every entity the cache handles.
 */
public final class ParallelRender {
    public static final boolean VERIFY = Boolean.getBoolean("lab.prender.verify");
    private static final int THREADS = Integer.getInteger("lab.prender.threads", Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)));
    public static long jobs, parallel, sequential, failed, verified, mismatches, helped;
    private static volatile int epoch;

    private ParallelRender() {}

    private static LabRuntime pool;

    private static LabRuntime pool() {
        if (pool == null) {
            pool = new LabRuntime(LabRuntime.Config.of("Render-Worker", THREADS).withPriority(OsThreads.NORMAL).withDaemon(true));
            System.out.println("[probe/opt] parallel render: " + THREADS + " workers");
        }
        return pool;
    }

    /** From EntityRenderDispatcher.onResourceManagerReload: the worker renderer sets are built again. */
    public static void reloaded() {
        epoch++;
    }

    // ------------------------------------------------------------------ per-worker renderer sets

    static final class Set {
        final int epoch;
        final PoseStack pose = new PoseStack();
        final Map<EntityType<?>, EntityRenderer<?>> renderers;
        final Map<PlayerSkin.Model, EntityRenderer<? extends net.minecraft.world.entity.player.Player>> players;

        Set(int epoch) {
            this.epoch = epoch;
            Minecraft mc = Minecraft.getInstance();
            EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
            var rm = mc.getResourceManager();
            BlockEntityWithoutLevelRenderer bewlr = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
            bewlr.onResourceManagerReload(rm);
            ItemRenderer ir = new ItemRenderer(mc, mc.getTextureManager(), mc.getModelManager(), mc.getItemColors(), bewlr);
            ir.onResourceManagerReload(rm);
            ItemInHandRenderer hand = new ItemInHandRenderer(mc, d, ir);
            EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(d, ir, mc.getBlockRenderer(), hand, rm, mc.getEntityModels(), mc.font);
            renderers = EntityRenderers.createEntityRenderers(ctx);
            players = EntityRenderers.createPlayerRenderers(ctx);
            net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers(renderers, players, ctx));
        }

        @SuppressWarnings("unchecked")
        <T extends Entity> EntityRenderer<? super T> renderer(T e) {
            return (EntityRenderer<? super T>) renderers.get(e.getType());
        }
    }

    private static final ConcurrentLinkedQueue<Set> SETS = new ConcurrentLinkedQueue<>();

    /** THREADS sets, built on the render thread (a mod renderer's constructor may touch GL). */
    private static void ensureSets() {
        int e = epoch;
        Set any = SETS.peek();
        if (any != null && any.epoch == e && SETS.size() == THREADS + 1) return;
        SETS.clear();
        long t0 = System.nanoTime();
        for (int i = 0; i < THREADS + 1; i++) SETS.add(new Set(e));   // one more: the render thread helps
        System.out.printf("[probe/opt] parallel render: %d renderer sets built in %.1f ms%n", THREADS, (System.nanoTime() - t0) / 1e6);
    }

    // ------------------------------------------------------------------ the frame queue

    static final class Job implements Runnable {
        Entity entity;
        double camX, camY, camZ;
        float pt;
        PoseStack pose;
        MultiBufferSource buffers;
        boolean parallel;
        volatile boolean done, ok;
        final RenderCache.Recorder recorder = new RenderCache.Recorder();
        final RenderCache.Rec out = new RenderCache.Rec();

        /** worker or render thread: whoever claims the job first renders it */
        final java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();

        @Override
        public void run() {
            if (!claimed.compareAndSet(false, true)) return;   // the render thread took it while waiting
            Set s = SETS.poll();
            try {
                ok = s != null && render(s, this);
            } catch (Throwable t) {
                ok = false;
                if (failed++ < 5) System.out.println("[probe/opt] parallel render: " + entity.getType() + " failed on a worker: " + t);
            } finally {
                if (s != null) SETS.add(s);
                done = true;
                synchronized (this) {
                    notifyAll();
                }
            }
        }

        void await() {
            if (done) return;
            synchronized (this) {
                while (!done) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private static final List<Job> QUEUE = new ArrayList<>();
    private static final List<Job> FREE = new ArrayList<>();
    private static int size;
    private static boolean listened;

    /** LevelRenderer's entity loop, per entity (in place of renderEntity). */
    public static void enqueue(LevelRenderer lr, Entity e, double camX, double camY, double camZ, float pt, PoseStack pose, MultiBufferSource buffers) {
        if (size == 0) {
            ensureSets();
            listened = EventSites.renderListened();   // once per frame
        }
        Job j = size < QUEUE.size() ? QUEUE.get(size) : null;
        if (j == null) {
            j = new Job();
            QUEUE.add(j);
        }
        size++;
        j.entity = e;
        j.camX = camX;
        j.camY = camY;
        j.camZ = camZ;
        j.pt = pt;
        j.pose = pose;
        j.buffers = buffers;
        j.done = false;
        j.ok = false;
        j.claimed.set(false);
        j.parallel = eligible(e, buffers);
        jobs++;
        if (j.parallel) pool().execute(j);
    }

    private static boolean eligible(Entity e, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        if (buffers != mc.renderBuffers().bufferSource() || e instanceof AbstractClientPlayer || e.hasCustomName() || e.displayFireAnimation()
                || d.shouldRenderHitBoxes() || listened) return false;
        return !Boolean.getBoolean("lab.opt.RenderCacheMixin") || RenderCache.animated(e);
    }

    /** After the loop: every job in order. */
    public static void drain(LevelRenderer lr) {
        LevelRendererInvoker inv = (LevelRendererInvoker) lr;
        for (int i = 0; i < size; i++) {
            Job j = QUEUE.get(i);
            if (j.parallel) {
                // helping instead of waiting: the queued jobs from here on that no worker has taken yet
                for (int k = i; !j.done && k < size; k++) {
                    Job h = QUEUE.get(k);
                    if (h.parallel && !h.done) {
                        h.run();
                        if (h.done) helped++;
                    }
                }
                j.await();
                if (j.ok) {
                    if (VERIFY) verify(inv, j);
                    RenderCache.append(j.out, j.buffers);
                    parallel++;
                    j.entity = null;
                    continue;
                }
            }
            inv.probe$renderEntity(j.entity, j.camX, j.camY, j.camZ, j.pt, j.pose, j.buffers);
            sequential++;
            j.entity = null;
        }
        size = 0;
    }

    private static final RenderCache.Recorder VERIFY_REC = new RenderCache.Recorder();
    private static final RenderCache.Rec VERIFY_OUT = new RenderCache.Rec();

    /** The same entity rendered on the render thread with the vanilla renderers, compared byte for byte. */
    private static void verify(LevelRendererInvoker inv, Job j) {
        VERIFY_REC.reset();
        inv.probe$renderEntity(j.entity, j.camX, j.camY, j.camZ, j.pt, new PoseStack(), VERIFY_REC);
        VERIFY_REC.finish(VERIFY_OUT);
        verified++;
        if (!VERIFY_OUT.sameAs(j.out)) {
            if (mismatches++ < 5) System.out.println("[probe/opt] parallel render: mismatch for " + j.entity.getType() + ": " + describe(VERIFY_OUT, j.out));
        }
    }

    private static String describe(RenderCache.Rec a, RenderCache.Rec b) {
        StringBuilder sb = new StringBuilder("render thread " + a.types.size() + " types / " + a.size + " bytes, worker " + b.types.size() + " types / " + b.size + " bytes");
        for (int i = 0; i < Math.max(a.types.size(), b.types.size()); i++)
            sb.append("\n    ").append(i < a.types.size() ? a.types.get(i).toString().substring(0, Math.min(60, a.types.get(i).toString().length())) + " x" + a.counts[i] : "-")
                    .append(" | ").append(i < b.types.size() ? b.types.get(i).toString().substring(0, Math.min(60, b.types.get(i).toString().length())) + " x" + b.counts[i] : "-");
        int n = Math.min(a.size, b.size);
        for (int i = 0; i < n; i++) {
            byte x = org.lwjgl.system.MemoryUtil.memGetByte(a.mem + i), y = org.lwjgl.system.MemoryUtil.memGetByte(b.mem + i);
            if (x != y) {
                sb.append("\n    first difference at byte ").append(i).append(": ").append(x).append(" vs ").append(y);
                break;
            }
        }
        return sb.toString();
    }

    /** renderEntity + EntityRenderDispatcher.render (without hitboxes), with a worker's renderers. */
    private static boolean render(Set s, Job j) {
        Entity e = j.entity;
        float pt = j.pt;
        EntityRenderer<? super Entity> r = s.renderer(e);
        if (r == null) return false;
        double x = Mth.lerp((double) pt, e.xOld, e.getX()) - j.camX;
        double y = Mth.lerp((double) pt, e.yOld, e.getY()) - j.camY;
        double z = Mth.lerp((double) pt, e.zOld, e.getZ()) - j.camZ;
        float yaw = Mth.lerp(pt, e.yRotO, e.getYRot());
        int light = r.getPackedLightCoords(e, pt);
        RenderCache.Recorder rec = j.recorder;
        rec.reset();
        PoseStack pose = s.pose;   // one per renderer set (a set serves one job at a time): its pose pool persists
        pose.setIdentity();
        Vec3 off = r.getRenderOffset(e, pt);
        pose.pushPose();
        pose.translate(x + off.x(), y + off.y(), z + off.z());
        r.render(e, yaw, pt, pose, rec, light);
        pose.translate(-off.x(), -off.y(), -off.z());
        EntityRenderDispatcher d = Minecraft.getInstance().getEntityRenderDispatcher();
        if (d.options.entityShadows().get() && ((EntityRenderDispatcherInvoker) d).probe$shouldRenderShadow() && !e.isInvisible()) {
            float f = ((dev.probe.opt.EntityRendererAccessor) r).probe$shadowRadius(e);
            if (f > 0.0F) {
                double d1 = d.distanceToSqr(e.getX(), e.getY(), e.getZ());
                float f1 = (float) ((1.0 - d1 / 256.0) * (double) ((dev.probe.opt.EntityRendererAccessor) r).probe$shadowStrength());
                if (f1 > 0.0F) EntityRenderDispatcherInvoker.probe$renderShadow(pose, rec, e, f1, pt, e.level(), Math.min(f, 32.0F));
            }
        }
        pose.popPose();
        if (rec.unsupported) return false;
        rec.finish(j.out);
        return true;
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "parallel render: %d workers, jobs %d, on workers %d (render thread helped %d), on the render thread %d, failed %d%s", THREADS, jobs, parallel,
                helped, sequential, failed, VERIFY ? ", verified " + verified + ", mismatches " + mismatches : "");
    }
}
