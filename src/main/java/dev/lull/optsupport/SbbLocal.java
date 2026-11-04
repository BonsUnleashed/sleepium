package dev.lull.optsupport;

import com.mojang.blaze3d.vertex.PoseStack;

/** A SuperByteBuffer's own transform stack (SbbPartsMixin implements it): KineticMemo reads it around a rotation. */
public interface SbbLocal {
    PoseStack.Pose sleep$local();
}
