package dev.sleep.opt;

import dev.sleep.optsupport.LightSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** LightSnapshot: the block light map's snapshot is a base + delta instead of a full copy of the section map. */
@Mixin(targets = "net.minecraft.world.level.lighting.BlockLightSectionStorage$BlockDataLayerStorageMap")
public abstract class LightSnapshotBlockMixin {
    @Redirect(method = "copy()Lnet/minecraft/world/level/lighting/BlockLightSectionStorage$BlockDataLayerStorageMap;", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;clone()Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;"))
    private Long2ObjectOpenHashMap<DataLayer> probe$snapshot(Long2ObjectOpenHashMap<DataLayer> map) {
        return ((LightSnapshot.Tracked) (Object) this).probe$snapshot(map);
    }
}
