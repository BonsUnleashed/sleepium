package dev.sleep.opt;

import java.util.Deque;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ClientLightAsyncMixin: are there queued light tasks (from packets)? */
@Mixin(ClientLevel.class)
public interface ClientLevelLightQueueAccessor {
    @Accessor("lightUpdateQueue")
    Deque<Runnable> probe$lightQueue();
}
