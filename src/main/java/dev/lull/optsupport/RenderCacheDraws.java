package dev.lull.optsupport;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * RenderCacheMixin / RenderCacheBlockEntityMixin: reusable call objects for RenderCache.render instead of a capturing
 * lambda per object per frame; one per nesting depth (a renderer may render another object inside), render thread only.
 */
public final class RenderCacheDraws {
    private RenderCacheDraws() {}

    private static int entityDepth, beDepth;
    private static final EntityDraw[] ENTITY = {new EntityDraw(), new EntityDraw(), new EntityDraw(), new EntityDraw()};
    private static final BeDraw[] BE = {new BeDraw(), new BeDraw(), new BeDraw(), new BeDraw()};

    public static final class EntityDraw implements RenderCache.Draw {
        @SuppressWarnings("rawtypes") EntityRenderer r;
        Entity e;
        float yaw;
        int light;
        /** the wrap chain of the call (RenderCacheMixin's @WrapOperation): other mods' wraps of it run too */
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> op;

        @Override
        @SuppressWarnings("unchecked")
        public void draw(PoseStack p, MultiBufferSource b, float t) {
            if (op != null) op.call(r, e, yaw, t, p, b, light);
            else r.render(e, yaw, t, p, b, light);
        }
    }

    public static final class BeDraw implements RenderCache.Draw {
        @SuppressWarnings("rawtypes") BlockEntityRenderer r;
        BlockEntity be;
        int light, overlay;
        /** the wrap chain of the call (RenderCacheBlockEntityMixin's @WrapOperation) */
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> op;

        @Override
        @SuppressWarnings("unchecked")
        public void draw(PoseStack p, MultiBufferSource b, float t) {
            if (op != null) op.call(r, be, t, p, b, light, overlay);
            else r.render(be, t, p, b, light, overlay);
        }
    }

    @SuppressWarnings("rawtypes")
    public static EntityDraw enterEntity(EntityRenderer r, Entity e, float yaw, int light) {
        return enterEntity(r, e, yaw, light, null);
    }

    @SuppressWarnings("rawtypes")
    public static EntityDraw enterEntity(EntityRenderer r, Entity e, float yaw, int light, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> op) {
        int d = entityDepth++;
        EntityDraw x = d < ENTITY.length ? ENTITY[d] : new EntityDraw();
        x.op = op;
        x.r = r;
        x.e = e;
        x.yaw = yaw;
        x.light = light;
        return x;
    }

    public static void exitEntity(EntityDraw x) {
        entityDepth--;
        x.r = null;
        x.e = null;
        x.op = null;
    }

    @SuppressWarnings("rawtypes")
    public static BeDraw enterBe(BlockEntityRenderer r, BlockEntity be, int light, int overlay) {
        return enterBe(r, be, light, overlay, null);
    }

    @SuppressWarnings("rawtypes")
    public static BeDraw enterBe(BlockEntityRenderer r, BlockEntity be, int light, int overlay, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> op) {
        int d = beDepth++;
        BeDraw x = d < BE.length ? BE[d] : new BeDraw();
        x.op = op;
        x.r = r;
        x.be = be;
        x.light = light;
        x.overlay = overlay;
        return x;
    }

    public static void exitBe(BeDraw x) {
        beDepth--;
        x.r = null;
        x.be = null;
        x.op = null;
    }
}
