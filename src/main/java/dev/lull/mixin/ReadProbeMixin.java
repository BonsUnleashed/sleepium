package dev.lull.mixin;

import dev.lull.BeTickStats;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World reads made during a block-entity tick: the tick's dependency set (see {@link BeTickStats}).
 * Applied only with {@code -Dsleep.beReads=true} (SleepMixinPlugin): these are the hottest methods of the game.
 */
@Mixin(Level.class)
public abstract class ReadProbeMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"))
    private void sleep$readBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BeTickStats.readPos('B', pos);
        if (dev.lull.optsupport.sleep.SleepEngine.HOOKS) dev.lull.optsupport.sleep.SleepEngine.readPos('B', pos);
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"))
    private void sleep$readBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        BeTickStats.readPos('E', pos);
        if (dev.lull.optsupport.sleep.SleepEngine.HOOKS) dev.lull.optsupport.sleep.SleepEngine.readPos('E', pos);
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("HEAD"))
    private void sleep$readEntities(Entity except, AABB box, Predicate<? super Entity> filter, CallbackInfoReturnable<List<Entity>> cir) {
        BeTickStats.readBox('A', box);
        if (dev.lull.optsupport.sleep.SleepEngine.HOOKS) dev.lull.optsupport.sleep.SleepEngine.readBox('A', box);
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V", at = @At("HEAD"))
    private void sleep$readEntitiesTyped(EntityTypeTest<?, ?> test, AABB box, Predicate<?> filter, List<?> out, int max, CallbackInfo ci) {
        BeTickStats.readBox('T', box);
        if (dev.lull.optsupport.sleep.SleepEngine.HOOKS) dev.lull.optsupport.sleep.SleepEngine.readBox('T', box);
    }
}
