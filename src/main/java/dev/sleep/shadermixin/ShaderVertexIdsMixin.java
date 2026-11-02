package dev.sleep.shadermixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.sleep.shaders.ShaderIds;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * While a shaderpack renders the level: entity vertices (NEW_ENTITY) carry the ids in the high bytes of their overlay
 * and light shorts (ShaderIds). One static check per write otherwise.
 */
@Mixin(BufferBuilder.class)
public class ShaderVertexIdsMixin {
    @Shadow
    @Final
    private VertexFormat format;

    @ModifyVariable(method = "setUv1", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int sleep$overlayU(int u) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? u & 0xFF | (ShaderIds.slot1 & 0xFF) << 8 : u;
    }

    @ModifyVariable(method = "setUv1", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int sleep$overlayV(int v) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? v & 0xFF | (ShaderIds.slot1 >> 8 & 0xFF) << 8 : v;
    }

    @ModifyVariable(method = "setUv2", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int sleep$lightU(int u) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? u & 0xFF | (ShaderIds.item1 & 0xFF) << 8 : u;
    }

    @ModifyVariable(method = "setUv2", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int sleep$lightV(int v) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? v & 0xFF | (ShaderIds.item1 >> 8 & 0xFF) << 8 : v;
    }

    @ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int sleep$packedOverlay(int packedOverlay) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? ShaderIds.overlay(packedOverlay) : packedOverlay;
    }

    @ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int sleep$packedLight(int packedLight) {
        return ShaderIds.on && format == DefaultVertexFormat.NEW_ENTITY ? ShaderIds.light(packedLight) : packedLight;
    }
}
