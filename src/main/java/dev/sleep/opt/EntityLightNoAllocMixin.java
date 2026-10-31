package dev.sleep.opt;

import dev.sleep.optsupport.NoAllocChecks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/frame-garbage (client): EntityRenderer.getPackedLightCoords (final: no renderer overrides it) without its Vec3
 * and BlockPos when the probe and the light levels are the base implementations (NoAllocChecks); vanilla's body
 * otherwise.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityLightNoAllocMixin<T extends Entity> {
    @Shadow protected abstract int getBlockLightLevel(T entity, BlockPos pos);

    @Shadow protected abstract int getSkyLightLevel(T entity, BlockPos pos);

    /**
     * @author probe
     * @reason the same light without a Vec3 and a BlockPos per entity per frame (see NoAllocChecks)
     */
    @Overwrite
    public final int getPackedLightCoords(T entity, float partialTicks) {
        int fast = NoAllocChecks.packedLight((EntityRenderer<?>) (Object) this, entity, partialTicks);
        if (fast != -1) return fast;
        BlockPos blockpos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        return LightTexture.pack(this.getBlockLightLevel(entity, blockpos), this.getSkyLightLevel(entity, blockpos));
    }
}
