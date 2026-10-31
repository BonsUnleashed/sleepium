package dev.sleep.opt;

import java.util.List;
import java.util.Map;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw lists behind ClassInstanceMultiMap, for allocation-free iteration (EntitySectionIterMixin). */
@Mixin(ClassInstanceMultiMap.class)
public interface ClassInstanceMultiMapAccessor<T> {
    @Accessor("allInstances")
    List<T> sleep$all();

    @Accessor("byClass")
    Map<Class<?>, List<T>> sleep$byClass();
}
