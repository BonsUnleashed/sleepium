package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.lull.GenProfile;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (genbench): the server's own waitForTasks, outside and inside the idle phase. */
@Mixin(MinecraftServer.class)
public abstract class GenProfileServerWaitMixin {
    @WrapMethod(method = "waitForTasks")
    private void sleep$wait(Operation<Void> original) {
        if (!GenProfile.active()) { original.call(); return; }
        long t = System.nanoTime();
        original.call();
        GenProfile.add("main: wait, " + (dev.lull.optsupport.MainReactor.idle ? "idle phase" : "blocking") + ", server", System.nanoTime() - t, 0);
    }
}
