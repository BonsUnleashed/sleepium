package dev.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.probe.IoScout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Measurement only (-Dprobe.ioScout=true): reading a chunk from its region file (disk + inflate + NBT parse). */
@Mixin(RegionFileStorage.class)
public abstract class IoScoutStorageMixin {
    @Shadow public abstract RegionStorageInfo info();

    @WrapMethod(method = "read")
    private CompoundTag probe$read(ChunkPos pos, Operation<CompoundTag> original) {
        long t = System.nanoTime();
        try {
            return original.call(pos);
        } finally {
            IoScout.read(info().type(), System.nanoTime() - t);
        }
    }
}
