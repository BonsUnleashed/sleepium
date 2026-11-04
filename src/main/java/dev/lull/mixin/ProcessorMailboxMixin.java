package dev.lull.mixin;

import dev.lull.TaskStats;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts tasks told to a mailbox actor and how often the actor is dispatched to its executor.
 * Vanilla {@code run()} processes one task per dispatch, then re-submits itself.
 */
@Mixin(ProcessorMailbox.class)
public abstract class ProcessorMailboxMixin {
    @Shadow
    public abstract String name();

    @Unique
    private TaskStats.Mailbox sleep$stats;

    @Unique
    private TaskStats.Mailbox sleep$stats() {
        TaskStats.Mailbox s = sleep$stats;
        if (s == null) {
            s = sleep$stats = TaskStats.mailbox(name());
        }
        return s;
    }

    @Inject(method = "tell", at = @At("HEAD"))
    private void sleep$onTell(Object task, CallbackInfo ci) {
        sleep$stats().tasks.increment();
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void sleep$onRun(CallbackInfo ci) {
        sleep$stats().runs.increment();
    }
}
