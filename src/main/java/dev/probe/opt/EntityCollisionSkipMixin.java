package dev.probe.opt;

import com.google.common.collect.ImmutableList;
import dev.probe.optsupport.CollidableCount;
import dev.probe.optsupport.EntityCollide;
import dev.probe.optsupport.OptCounters;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/collide-skip: every entity move and every item tick (noCollision) asks getEntityCollisions: vanilla walks every
 * nearby entity and tests !isSpectator && mover.canCollideWith(e), i.e. e.canBeCollidedWith(), which is Entity's
 * constant false for all but boats, minecarts and shulkers (JFR, 4000 items: 13 % of the server thread in that test).
 * When the mover uses Entity's canCollideWith (or is null) and no section vanilla would visit holds an entity whose
 * class overrides canBeCollidedWith (per-section counters, EntitySectionCollidableMixin), and no part entity's class
 * does, the answer is provably the empty list: returned directly. Otherwise vanilla, verbatim.
 * {@code -Dlab.opt.collideCheck=true} runs vanilla on every skip and counts non-empty answers.
 */
@Mixin(Level.class)
public abstract class EntityCollisionSkipMixin {
    @Shadow protected abstract LevelEntityGetter<Entity> getEntities();
    @Shadow public abstract List<Entity> getEntities(@Nullable Entity entity, AABB area, Predicate<? super Entity> predicate);
    @Shadow public abstract ProfilerFiller getProfiler();

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.collideCheck");

    /** Overrides the EntityGetter/CommonLevelAccessor default for every Level. */
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        if (dev.probe.optsupport.sleep.SleepEngine.HOOKS) dev.probe.optsupport.sleep.SleepEngine.readBox('C', collisionBox);
        if (collisionBox.getSize() < 1.0E-7) return List.of();
        AABB area = collisionBox.inflate(1.0E-7);
        if (EntityCollide.ENTITY_UNTOUCHED && (entity == null || EntityCollide.DEFAULT_COLLIDE.get(entity.getClass())) && probe$none(area)) {
            if (CHECK) probe$check(entity, area);
            this.getProfiler().incrementCounter("getEntities");   // what vanilla's getEntities would have counted
            return List.of();
        }
        Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        List<Entity> list = this.getEntities(entity, area, predicate);
        if (list.isEmpty()) return List.of();
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(list.size());
        for (Entity e : list) builder.add(Shapes.create(e.getBoundingBox()));
        return builder.build();
    }

    @Unique
    private boolean probe$none(AABB area) {
        LevelEntityGetter<Entity> getter = this.getEntities();
        if (!(getter instanceof LevelEntityGetterAdapter<?>)) return false;
        for (var part : ((net.neoforged.neoforge.common.extensions.ILevelExtension) (Object) this).getPartEntities())
            if (EntityCollide.CAPABLE.get(part.getClass())) return false;
        return !((CollidableCount.Storage) ((LevelEntityGetterAdapterAccessor) getter).probe$sectionStorage()).probe$anyCollidable(area);
    }

    @Unique
    private void probe$check(@Nullable Entity entity, AABB area) {
        Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        OptCounters.COLLIDE_CHECKED.incrementAndGet();
        if (!this.getEntities(entity, area, predicate).isEmpty() && OptCounters.COLLIDE_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[probe/opt] entity collision skip MISMATCH for " + entity + " at " + area);
    }
}
