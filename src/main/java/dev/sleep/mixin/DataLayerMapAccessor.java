package dev.sleep.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Measurement only: a light storage map's section table. */
@Mixin(DataLayerStorageMap.class)
public interface DataLayerMapAccessor {
    @Accessor("map")
    Long2ObjectOpenHashMap<DataLayer> probe$map();
}
