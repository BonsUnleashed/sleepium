package dev.sleep.opt;

import dev.sleep.optsupport.BlockCollisionMemo;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Goes with BlockCollisionMemoMixin: the per-entity memo slot. */
@Mixin(Entity.class)
public abstract class EntityCollisionMemoMixin implements BlockCollisionMemo.Holder {
    @Unique private BlockCollisionMemo probe$collisionMemo, probe$collideMemo, probe$supportMemo;

    @Override public BlockCollisionMemo probe$collideMemo() { return probe$collideMemo; }
    @Override public void probe$setCollideMemo(BlockCollisionMemo m) { probe$collideMemo = m; }
    @Override public BlockCollisionMemo probe$supportMemo() { return probe$supportMemo; }
    @Override public void probe$setSupportMemo(BlockCollisionMemo m) { probe$supportMemo = m; }

    @Override
    public BlockCollisionMemo probe$collisionMemo() {
        return probe$collisionMemo;
    }

    @Override
    public void probe$setCollisionMemo(BlockCollisionMemo m) {
        probe$collisionMemo = m;
    }
}
