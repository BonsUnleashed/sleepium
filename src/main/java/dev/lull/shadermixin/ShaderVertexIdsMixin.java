package dev.lull.shadermixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lull.shaders.ShaderIds;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * While a shaderpack renders the level: entity vertices (NEW_ENTITY) carry the ids in the high bytes of their overlay
 * and light shorts (ShaderIds). One static check per write otherwise.
 *
 * <p>1.20.1: BufferBuilder takes the overlay and light through BufferVertexConsumer's default overlayCoords / uv2
 * (1.21.1: its own setUv1 / setUv2), so they are given here as BufferBuilder's own methods, with the default's body
 * (uvShort) after the ids; the fast path vertex(...) (1.21.1: addVertex) has its packed overlay and light changed at
 * its head. A vertex the fast path cannot take goes through the defaults, which come back here: the same bits again.
 * The two methods are soft implementations (@Implements, prefix sleep$ids$): the annotation processor maps them to
 * the interface methods' SRG names (a plain method named overlayCoords would not override m_7122_ in production).
 */
@Mixin(BufferBuilder.class)
@Implements(@Interface(iface = VertexConsumer.class, prefix = "sleep$ids$"))
public abstract class ShaderVertexIdsMixin {
    @Shadow
    private VertexFormat format;

    public VertexConsumer sleep$ids$overlayCoords(int u, int v) {
        if (ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY) {
            u = u & 0xFF | (ShaderIds.slot1 & 0xFF) << 8;
            v = v & 0xFF | (ShaderIds.slot1 >> 8 & 0xFF) << 8;
        }
        return ((BufferVertexConsumer) (Object) this).uvShort((short) u, (short) v, 1);
    }

    public VertexConsumer sleep$ids$uv2(int u, int v) {
        if (ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY) {
            u = u & 0xFF | (ShaderIds.item1 & 0xFF) << 8;
            v = v & 0xFF | (ShaderIds.item1 >> 8 & 0xFF) << 8;
        }
        return ((BufferVertexConsumer) (Object) this).uvShort((short) u, (short) v, 2);
    }

    @ModifyVariable(method = "vertex(FFFFFFFFFIIFFF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int sleep$packedOverlay(int packedOverlay) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? ShaderIds.overlay(packedOverlay) : packedOverlay;
    }

    @ModifyVariable(method = "vertex(FFFFFFFFFIIFFF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int sleep$packedLight(int packedLight) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? ShaderIds.light(packedLight) : packedLight;
    }
}
