package dev.sleep.shaders;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The shadow pass, before the sky: from the sun (or moon) with the pack's shadow programs into its shadow map - the
 * built sections in the shadow box (opaque layers, then shadowtex1 copied, then the translucent layer), the entities
 * within entityShadowDistanceMul x shadowDistance and the sections' block entities (through the same dispatchers, so
 * the render cache replays them). The section list is a reused array.
 */
final class ShadowPass {
    private final ShaderPipeline pl;
    final ShadowTargets targets;
    private SectionRenderDispatcher.RenderSection[] list = new SectionRenderDispatcher.RenderSection[1024];
    private int count;
    private final PoseStack pose = new PoseStack();
    private final Matrix4f mvp = new Matrix4f();
    private final Vector4f tmp = new Vector4f();
    long sections, draws, entities, blockEntities, nanos, multiLayers;

    /** the collected sections as an Iterable (one object, reused) */
    private final Iterable<SectionRenderDispatcher.RenderSection> view = () -> new java.util.Iterator<>() {
        int i;

        @Override
        public boolean hasNext() {
            return i < count;
        }

        @Override
        public SectionRenderDispatcher.RenderSection next() {
            return list[i++];
        }
    };

    ShadowPass(ShaderPipeline pl, ShadowTargets targets) {
        this.pl = pl;
        this.targets = targets;
    }

    void run(Camera camera, float pt) {
        long t0 = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        ViewArea va = ((dev.sleep.shadermixin.ShaderViewAreaAccessor) mc.levelRenderer).sleep$viewArea();
        if (va == null || mc.level == null) return;
        PackUniforms u = pl.uniforms;
        Vec3 cam = camera.getPosition();
        targets.clear();
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        var mvs = RenderSystem.getModelViewStack();
        mvs.pushMatrix();
        mvs.set(u.shadowModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(u.shadowProjection), VertexSorting.ORTHOGRAPHIC_Z);
        GL11.glViewport(0, 0, targets.size, targets.size);
        pl.shadowPhase = true;
        try {
            collect(va, cam);
            boolean terrain = pl.props.bool("shadowTerrain", true);
            if (terrain) {
                layer(RenderType.solid(), cam);
                layer(RenderType.cutoutMipped(), cam);
                layer(RenderType.cutout(), cam);
            }
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            if (pl.props.bool("shadowEntities", true)) entities(mc, cam, pt, buffers);
            if (pl.props.bool("shadowBlockEntities", true)) blockEntities(mc, cam, pt, buffers);
            targets.copyOpaque();
            if (terrain && pl.props.bool("shadowTranslucent", true)) layer(RenderType.translucent(), cam);
        } finally {
            pl.shadowPhase = false;
            pl.shadowBlockEntitiesPhase = false;
            mvs.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProj, savedSorting);
        }
        nanos += System.nanoTime() - t0;
    }

    /** the built sections whose box the shadow volume holds */
    private void collect(ViewArea va, Vec3 cam) {
        count = 0;
        mvp.set(pl.uniforms.shadowProjection).mul(pl.uniforms.shadowModelView);
        float dist = pl.uniforms.shadowDistance;
        float r = 14f / dist;   // a section's half diagonal in the shadow box's units
        for (SectionRenderDispatcher.RenderSection s : va.sections) {
            if (s == null || s.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) continue;
            BlockPos o = s.getOrigin();
            float cx = (float) (o.getX() + 8 - cam.x), cy = (float) (o.getY() + 8 - cam.y), cz = (float) (o.getZ() + 8 - cam.z);
            if (cx * cx + cz * cz > (dist + 16) * (dist + 16)) continue;
            mvp.transform(tmp.set(cx, cy, cz, 1));
            if (Math.abs(tmp.x) > 1 + r || Math.abs(tmp.y) > 1 + r || Math.abs(tmp.z) > 1 + r) continue;
            if (count == list.length) list = java.util.Arrays.copyOf(list, count * 2);
            list[count++] = s;
        }
        sections += count;
    }

    /** one terrain layer, the way LevelRenderer.renderSectionLayer draws it */
    private void layer(RenderType type, Vec3 cam) {
        type.setupRenderState();
        ShaderInstance sh = RenderSystem.getShader();
        if (sh == null) {
            type.clearRenderState();
            return;
        }
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, pl.uniforms.shadowModelView, pl.uniforms.shadowProjection, Minecraft.getInstance().getWindow());
        sh.apply();
        Uniform off = sh.CHUNK_OFFSET;
        // the opaque layers: one multi-draw per arena (every face bucket: the GPU culls by the light's view)
        boolean multi = false;
        if (dev.sleep.optsupport.ChunkMultiDraw.ACTIVE && dev.sleep.optsupport.ChunkMultiDraw.layerSupported(type)
                && dev.sleep.optsupport.ChunkMultiDraw.replacesVanilla(type)) {
            dev.sleep.optsupport.ChunkMultiDraw.allFaces = true;
            org.joml.Vector3f toLight = new Matrix4f(pl.uniforms.shadowModelView).invert().transformDirection(new org.joml.Vector3f(0, 0, 1));
            dev.sleep.optsupport.ChunkMultiDraw.lightDirection(toLight.x, toLight.y, toLight.z);
            try {
                multi = dev.sleep.optsupport.ChunkMultiDraw.draw(type, view, cam.x, cam.y, cam.z, sh);
            } finally {
                dev.sleep.optsupport.ChunkMultiDraw.allFaces = false;
            }
            if (multi) multiLayers++;
        }
        for (int i = 0; i < count && !multi; i++) {
            SectionRenderDispatcher.RenderSection s = list[i];
            if (s.getCompiled().isEmpty(type)) continue;
            VertexBuffer vb = s.getBuffer(type);
            BlockPos o = s.getOrigin();
            if (off != null) {
                off.set((float) (o.getX() - cam.x), (float) (o.getY() - cam.y), (float) (o.getZ() - cam.z));
                off.upload();
            }
            vb.bind();
            vb.draw();
            draws++;
        }
        if (off != null) off.set(0f, 0f, 0f);
        sh.clear();
        VertexBuffer.unbind();
        type.clearRenderState();
    }

    private void entities(Minecraft mc, Vec3 cam, float pt, MultiBufferSource.BufferSource buffers) {
        var d = mc.getEntityRenderDispatcher();
        float mul = Float.parseFloat(pl.set.consts.getOrDefault("entityShadowDistanceMul", "1").replaceAll("[fF]$", ""));
        double max = pl.uniforms.shadowDistance * mul, max2 = max * max;
        boolean playerShadow = pl.props.bool("shadowPlayer", true);
        d.setRenderShadow(false);
        try {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.distanceToSqr(cam) > max2 || e.isInvisible() && !e.isCurrentlyGlowing()) continue;
                if (!playerShadow && e == mc.player) continue;
                double x = Mth.lerp(pt, e.xOld, e.getX()) - cam.x, y = Mth.lerp(pt, e.yOld, e.getY()) - cam.y, z = Mth.lerp(pt, e.zOld, e.getZ()) - cam.z;
                float yaw = Mth.lerp(pt, e.yRotO, e.getYRot());
                pose.setIdentity();
                d.render(e, x, y, z, yaw, pt, pose, buffers, d.getPackedLightCoords(e, pt));
                entities++;
            }
            buffers.endBatch();
        } finally {
            d.setRenderShadow(true);
        }
    }

    private void blockEntities(Minecraft mc, Vec3 cam, float pt, MultiBufferSource.BufferSource buffers) {
        var d = mc.getBlockEntityRenderDispatcher();
        double max2 = (double) pl.uniforms.shadowDistance * pl.uniforms.shadowDistance;
        pl.shadowBlockEntitiesPhase = true;
        for (int i = 0; i < count; i++) {
            for (BlockEntity be : list[i].getCompiled().getRenderableBlockEntities()) {
                BlockPos p = be.getBlockPos();
                double x = p.getX() - cam.x, y = p.getY() - cam.y, z = p.getZ() - cam.z;
                if (x * x + z * z > max2) continue;
                pose.setIdentity();
                pose.translate(x, y, z);
                d.render(be, pt, pose, buffers);
                blockEntities++;
            }
        }
        buffers.endBatch();
        pl.shadowBlockEntitiesPhase = false;
    }

    /** the shadow framebuffer for a shadow program's draw buffers */
    int framebuffer(int[] drawBuffers) {
        int fb = targets.framebuffer(drawBuffers);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fb);
        GL11.glViewport(0, 0, targets.size, targets.size);
        return fb;
    }
}
