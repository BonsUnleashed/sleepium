package dev.sleep.opt;

import com.mojang.blaze3d.shaders.Uniform;
import dev.sleep.optsupport.ClientCounters;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/uniform-dedupe (client): Uniform.upload sends the value only when it differs from the one this uniform last sent
 * to its location. Vanilla's dirty test is an empty if, and setDefaultUniforms sets the same matrices and fog values
 * on every apply, so every uniform of every shader went to the driver on every draw. A GL program keeps its uniform
 * values, and each Uniform belongs to one program: skipping an equal value (compared bit for bit) changes nothing.
 * Location -1 (not in the program) is a no-op for GL and is skipped too.
 */
@Mixin(Uniform.class)
public abstract class UniformDedupeMixin {
    @Shadow private int location;
    @Shadow @Final private int count;
    @Shadow @Final private int type;
    @Shadow @Final private IntBuffer intValues;
    @Shadow @Final private FloatBuffer floatValues;
    @Shadow private boolean dirty;

    @Unique private int[] probe$last;
    @Unique private int probe$lastLocation = Integer.MIN_VALUE;

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void probe$dedupe(CallbackInfo ci) {
        if (this.location == -1) {
            this.dirty = false;
            ClientCounters.uniformsSkipped++;
            ci.cancel();
            return;
        }
        if (this.type > 10) return;
        int n = this.count;
        int[] last = probe$last;
        boolean ints = this.type <= 3;
        if (last != null && last.length == n && probe$lastLocation == this.location) {
            boolean same = true;
            for (int i = 0; i < n; i++) {
                int v = ints ? this.intValues.get(i) : Float.floatToRawIntBits(this.floatValues.get(i));
                if (v != last[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                this.dirty = false;
                ClientCounters.uniformsSkipped++;
                ci.cancel();
                return;
            }
        }
        if (last == null || last.length != n) probe$last = last = new int[n];
        for (int i = 0; i < n; i++) last[i] = ints ? this.intValues.get(i) : Float.floatToRawIntBits(this.floatValues.get(i));
        probe$lastLocation = this.location;
        ClientCounters.uniformsSent++;
    }
}
