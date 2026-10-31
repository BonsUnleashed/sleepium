package dev.sleep.opt;

import java.util.Optional;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * WorldgenAlloc (jigsaw): JointType.byName streams over a fresh values() copy for every jigsaw block loaded while
 * structures are placed. The same first match by serialized name, over a cached array.
 */
@Mixin(JigsawBlockEntity.JointType.class)
public abstract class WorldgenAllocJointMixin {
    @Unique private static JigsawBlockEntity.JointType[] sleep$values;

    /**
     * @author probe
     * @reason the same first match without a stream and an array copy per call
     */
    @Overwrite
    public static Optional<JigsawBlockEntity.JointType> byName(String name) {
        JigsawBlockEntity.JointType[] v = sleep$values;
        if (v == null) sleep$values = v = JigsawBlockEntity.JointType.values();
        for (JigsawBlockEntity.JointType t : v) if (t.getSerializedName().equals(name)) return Optional.of(t);
        return Optional.empty();
    }
}
