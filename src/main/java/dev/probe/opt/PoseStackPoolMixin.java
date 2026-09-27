package dev.probe.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/pose-pool (client): every pushPose allocated a Pose with a new Matrix4f and Matrix3f (18 % of the render thread's
 * allocation in an entity-heavy scene). Popped poses go to a free list of this stack and are reused by the next push,
 * which copies the top pose into them exactly as the copy constructor would. Exact as long as nobody keeps a Pose after
 * popping it (vanilla does not; the frame hash checks it); opt-in for that reason.
 */
@Mixin(PoseStack.class)
public abstract class PoseStackPoolMixin {
    @Shadow @Final private Deque<PoseStack.Pose> poseStack;
    @Unique private final ArrayDeque<PoseStack.Pose> probe$free = new ArrayDeque<>();

    /**
     * @author probe (lab.opt.PoseStackPoolMixin)
     * @reason reuse popped poses instead of allocating two matrices per push
     */
    @Overwrite
    public void pushPose() {
        PoseStack.Pose last = this.poseStack.getLast();
        PoseStack.Pose p = probe$free.pollLast();
        if (p == null) {
            p = PoseAccessor.probe$copyOf(last);
        } else {
            PoseAccessor a = (PoseAccessor) (Object) p, l = (PoseAccessor) (Object) last;
            a.probe$pose().set(l.probe$pose());
            a.probe$normal().set(l.probe$normal());
            a.probe$setTrusted(l.probe$trusted());
        }
        this.poseStack.addLast(p);
    }

    /**
     * @author probe (lab.opt.PoseStackPoolMixin)
     * @reason keep the popped pose for the next push
     */
    @Overwrite
    public void popPose() {
        probe$free.addLast(this.poseStack.removeLast());
    }
}
