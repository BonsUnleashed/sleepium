package dev.probe.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Measurement mixins are always on, except the expensive read probes (ReadProbe*), which need -Dprobe.beReads=true. */
public final class ProbeMixinPlugin implements IMixinConfigPlugin {
    static {
        dev.probe.optsupport.GameDefaults.ensure();   // a built jar's flags before any is read
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        boolean sleep = Boolean.getBoolean("lab.opt.SleepMixin") || Boolean.getBoolean("lab.opt.EntitySleep");
        if (simple.startsWith("SleepProbeUnobserved")) return sleep && !Boolean.getBoolean("lab.sleep.noUnobserved");   // off only for measurements
        if (simple.startsWith("SleepProbe")) return sleep;
        if (simple.startsWith("FrameHash")) return Boolean.getBoolean("probe.clientAuto.framehash");
        if (simple.startsWith("DrawStats")) return Boolean.getBoolean("probe.drawStats");
        if (simple.startsWith("NetStats")) return Boolean.getBoolean("probe.net.stats");
        if (simple.startsWith("GenTrace") || simple.equals("DataLayerMapAccessor")) return Boolean.getBoolean("lab.pgen.trace");
        if (simple.startsWith("IoScout")) return Boolean.getBoolean("probe.ioScout");
        if (simple.equals("ClientAutoMouseMixin")) return System.getProperty("probe.clientAuto") != null;
        if (simple.startsWith("EntSleep")) return Boolean.getBoolean("lab.opt.EntitySleep") || System.getProperty("lab.entsleep.diag") != null;
        return !simple.startsWith("ReadProbe") || Boolean.getBoolean("probe.beReads") || sleep;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
