package dev.sleep.opt;

import dev.sleep.optsupport.BlockCollisionMemo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Goes with BlockCollisionMemoMixin: a modification counter per chunk section, bumped at every write of its block
 * states (setBlockState, both paths, and the client packet read); these are the only writers of the container.
 */
@Mixin(LevelChunkSection.class)
public abstract class SectionModCountMixin implements BlockCollisionMemo.Counted {
    @Unique private int probe$mod;

    @Override
    public int probe$mod() {
        return probe$mod;
    }

    @Redirect(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;"))
    private Object probe$set(PalettedContainer<Object> states, int x, int y, int z, Object state) {
        probe$mod++;
        return states.getAndSet(x, y, z, state);
    }

    @Redirect(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;getAndSetUnchecked(IIILjava/lang/Object;)Ljava/lang/Object;"))
    private Object probe$setUnchecked(PalettedContainer<Object> states, int x, int y, int z, Object state) {
        probe$mod++;
        return states.getAndSetUnchecked(x, y, z, state);
    }

    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;read(Lnet/minecraft/network/FriendlyByteBuf;)V"))
    private void probe$read(PalettedContainer<?> container, FriendlyByteBuf buf) {
        probe$mod++;
        container.read(buf);
    }
}
