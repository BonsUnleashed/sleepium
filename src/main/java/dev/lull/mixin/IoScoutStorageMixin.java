package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.lull.IoScout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Measurement only (-Dsleep.ioScout=true): reading a chunk from its region file (disk + inflate + NBT parse). */
@Mixin(RegionFileStorage.class)
public abstract class IoScoutStorageMixin {
    @Shadow @org.spongepowered.asm.mixin.Final private java.nio.file.Path folder;

    /** 1.20.1 has no RegionStorageInfo: the kind from the folder (region = chunk, poi, entities) */
    @org.spongepowered.asm.mixin.Unique
    private String sleep$type() {
        String n = String.valueOf(this.folder.getFileName());
        return n.equals("region") ? "chunk" : n;
    }

    @WrapMethod(method = "read")
    private CompoundTag sleep$read(ChunkPos pos, Operation<CompoundTag> original) {
        long t = System.nanoTime();
        try {
            return original.call(pos);
        } finally {
            IoScout.read(sleep$type(), System.nanoTime() - t);
        }
    }
}
