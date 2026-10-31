package dev.sleep.opt;

import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/frame-garbage (client): ItemFrameRenderer.getRenderOffset depends on the frame's direction only; the six values,
 * each made once by the same expression, instead of a new Vec3 per frame per item frame (Vec3 is immutable).
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameOffsetMixin<T extends ItemFrame> {
    @Unique private static final Vec3[] sleep$offsets = new Vec3[Direction.values().length];

    /**
     * @author probe
     * @reason the same value per direction, made once (see the class comment)
     */
    @Overwrite
    public Vec3 getRenderOffset(T entity, float partialTicks) {
        Direction d = entity.getDirection();
        Vec3 v = sleep$offsets[d.ordinal()];
        if (v == null) {
            v = new Vec3((double) ((float) d.getStepX() * 0.3F), -0.25, (double) ((float) d.getStepZ() * 0.3F));
            sleep$offsets[d.ordinal()] = v;
        }
        return v;
    }
}
