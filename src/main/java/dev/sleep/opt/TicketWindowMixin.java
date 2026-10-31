package dev.sleep.opt;

import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Experiment (lab.opt.TicketWindowMixin, -Dlab.ticketWindow=N): the player tickets in flight at once (vanilla 4). */
@Mixin(DistanceManager.class)
public abstract class TicketWindowMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkTaskPriorityQueueSorter;<init>(Ljava/util/List;Ljava/util/concurrent/Executor;I)V"), index = 2)
    private int sleep$window(int vanilla) {
        return Integer.getInteger("lab.ticketWindow", vanilla);
    }
}
