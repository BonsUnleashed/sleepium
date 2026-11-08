package dev.lull.opt;

import com.mojang.blaze3d.shaders.Uniform;
import dev.lull.optsupport.ClientCounters;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/uniform-dedupe (client): Uniform.upload sends the value only when it differs from the one this uniform last sent
 * to its location. Vanilla's dirty test is an empty if, and setDefaultUniforms sets the same matrices and fog values
 * on every apply, so every uniform of every shader went to the driver on every draw. A GL program keeps its uniform
 * values, and each Uniform belongs to one program (its parent's current one: the cache is keyed by it, a program swapped
 * in - Veil's recompile - starts empty): skipping an equal value (compared bit for bit) changes nothing.
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

    @Shadow
    private void uploadAsInteger() {
        throw new AssertionError();
    }

    @Shadow
    private void uploadAsFloat() {
        throw new AssertionError();
    }

    @Shadow
    private void uploadAsMatrix() {
        throw new AssertionError();
    }
    @Shadow @Final private com.mojang.blaze3d.shaders.Shader parent;

    @Unique private int[] sleep$last;
    @Unique private int sleep$lastLocation = Integer.MIN_VALUE, sleep$lastProgram = Integer.MIN_VALUE;

    /** Uniform.upload's GL call for integer values, made only when the value changed (sleep$changed) */
    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;uploadAsInteger()V"))
    private void sleep$ints(Uniform self) {
        if (sleep$changed()) uploadAsInteger();
    }

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;uploadAsFloat()V"))
    private void sleep$floats(Uniform self) {
        if (sleep$changed()) uploadAsFloat();
    }

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;uploadAsMatrix()V"))
    private void sleep$matrix(Uniform self) {
        if (sleep$changed()) uploadAsMatrix();
    }

    /**
     * Whether the value must go to GL (vanilla has already cleared dirty): not for location -1 (a no-op for GL), not for
     * the very value this uniform last sent to this location of this program. Redirects, not a cancellable inject at the
     * head: no CallbackInfo per upload (every uniform of every draw).
     */
    @Unique
    private boolean sleep$changed() {
        if (this.location == -1) {
            ClientCounters.uniformsSkipped++;
            return false;
        }
        int n = this.count;
        int[] last = sleep$last;
        boolean ints = this.type <= 3;
        int program = this.parent.getId();   // a program swapped in under the same ShaderInstance (Veil's recompile) starts with no values
        if (last != null && last.length == n && sleep$lastLocation == this.location && sleep$lastProgram == program) {
            boolean same = true;
            for (int i = 0; i < n; i++) {
                int v = ints ? this.intValues.get(i) : Float.floatToRawIntBits(this.floatValues.get(i));
                if (v != last[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                ClientCounters.uniformsSkipped++;
                return false;
            }
        }
        if (last == null || last.length != n) sleep$last = last = new int[n];
        for (int i = 0; i < n; i++) last[i] = ints ? this.intValues.get(i) : Float.floatToRawIntBits(this.floatValues.get(i));
        sleep$lastLocation = this.location;
        sleep$lastProgram = program;
        ClientCounters.uniformsSent++;
        return true;
    }
}
