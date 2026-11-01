package dev.sleep.catmixin;

import dev.sleep.optsupport.KineticResident;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.server.level.BlockDestructionProgress;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KineticResident: the frame's camera before the level is drawn, and the section renderLevel's block entity loop is in. */
@Mixin(LevelRenderer.class)
public abstract class KineticLevelMixin {
    @Shadow @Final private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;
    @Shadow private net.minecraft.client.renderer.ViewArea viewArea;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sleep$frame(DeltaTracker delta, boolean outline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f frustum, Matrix4f projection, CallbackInfo ci) {
        KineticResident.Crumbling.progress = destructionProgress;
        KineticResident.beginFrame(camera, viewArea);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getCompiled()Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;"))
    private SectionRenderDispatcher.CompiledSection sleep$section(SectionRenderDispatcher.RenderSection s) {
        KineticResident.section(s);
        return s.getCompiled();
    }
}
