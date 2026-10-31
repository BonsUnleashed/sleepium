package dev.sleep.opt;

import dev.sleep.optsupport.BlockCollisionMemo;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Goes with BlockCollisionMemoMixin: the per-entity memo slot. */
@Mixin(Entity.class)
public abstract class EntityCollisionMemoMixin implements BlockCollisionMemo.Holder {
    @Unique private BlockCollisionMemo sleep$collisionMemo, sleep$collideMemo, sleep$supportMemo;

    @Override public BlockCollisionMemo sleep$collideMemo() { return sleep$collideMemo; }
    @Override public void sleep$setCollideMemo(BlockCollisionMemo m) { sleep$collideMemo = m; }
    @Override public BlockCollisionMemo sleep$supportMemo() { return sleep$supportMemo; }
    @Override public void sleep$setSupportMemo(BlockCollisionMemo m) { sleep$supportMemo = m; }

    @Override
    public BlockCollisionMemo sleep$collisionMemo() {
        return sleep$collisionMemo;
    }

    @Override
    public void sleep$setCollisionMemo(BlockCollisionMemo m) {
        sleep$collisionMemo = m;
    }
}
