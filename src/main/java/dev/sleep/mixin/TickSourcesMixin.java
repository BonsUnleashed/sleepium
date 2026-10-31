package dev.sleep.mixin;

import dev.sleep.EventSources;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block-entity polling volume: how many tickers the server level walks per tick (see {@link EventSources}). */
@Mixin(Level.class)
public abstract class TickSourcesMixin {
    @Shadow @Final protected List<TickingBlockEntity> blockEntityTickers;

    @Shadow public abstract boolean isClientSide();

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void probe$blockEntityTicks(CallbackInfo ci) {
        if (!isClientSide()) EventSources.blockEntityTicks += blockEntityTickers.size();
    }
}
