package dev.sleep.opt;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PartBatch: which shader a render type draws with. */
@Mixin(RenderStateShard.ShaderStateShard.class)
public interface ShaderStateShardAccessor {
    @Accessor("shader")
    Optional<Supplier<ShaderInstance>> sleep$shader();
}
