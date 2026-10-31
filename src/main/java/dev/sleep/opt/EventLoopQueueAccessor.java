package dev.sleep.opt;

import java.util.Queue;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** MainReactor: an event loop's task queue, to re-check it before parking. */
@Mixin(BlockableEventLoop.class)
public interface EventLoopQueueAccessor {
    @Accessor("pendingRunnables")
    Queue<?> sleep$pending();
}
