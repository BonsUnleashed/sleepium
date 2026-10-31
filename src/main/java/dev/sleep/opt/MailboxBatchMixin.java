package dev.sleep.opt;

import it.unimi.dsi.fastutil.ints.Int2BooleanFunction;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * exp/mailbox-batch: let one executor dispatch of a mailbox drain up to N tasks instead of exactly one.
 *
 * <p>Vanilla {@code run()} is {@code pollUntil(i -> i == 0)}: run one task, go idle, re-submit itself to the
 * ForkJoinPool. That costs a CAS pair, an FJP push and possibly a worker wake-up per task (measured live: 1.00
 * tasks per dispatch for light/sorter/worldgen/IO). Batching keeps the mailbox on its worker while it has work.
 * Tasks still run strictly in mailbox order (priority order for the sorter); only the hand-back point moves.
 *
 * <p>Batch size: {@code -Dlab.opt.mailboxBatch=64} (default 64).
 */
@Mixin(ProcessorMailbox.class)
public abstract class MailboxBatchMixin {
    @Unique
    private static final int sleep$BATCH = Integer.getInteger("lab.opt.mailboxBatch", 64);
    @Unique
    private static final Int2BooleanFunction sleep$UP_TO_BATCH = i -> i < sleep$BATCH;

    @ModifyArg(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/thread/ProcessorMailbox;pollUntil(Lit/unimi/dsi/fastutil/ints/Int2BooleanFunction;)I"))
    private Int2BooleanFunction sleep$batch(Int2BooleanFunction vanillaOneTask) {
        return sleep$UP_TO_BATCH;
    }
}
