package dev.probe.opt;

import dev.probe.optsupport.BlockCollisionMemo;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Goes with BlockCollisionMemoMixin: the per-entity memo slot. */
@Mixin(Entity.class)
public abstract class EntityCollisionMemoMixin implements BlockCollisionMemo.Holder {
    @Unique private BlockCollisionMemo probe$collisionMemo;

    @Override
    public BlockCollisionMemo probe$collisionMemo() {
        return probe$collisionMemo;
    }

    @Override
    public void probe$setCollisionMemo(BlockCollisionMemo m) {
        probe$collisionMemo = m;
    }
}
