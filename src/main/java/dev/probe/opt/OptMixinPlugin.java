package dev.probe.opt;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gates every experimental optimisation mixin behind a JVM flag, so one jar can be A/B tested by
 * interleaving runs with and without the flag:
 * <pre>  -Dlab.opt.GoalSelectorMixin=true   or   -Dlab.opt.all=true</pre>
 * Everything is OFF by default: the probe must not change the game unless asked to.
 */
public final class OptMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        boolean on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt." + simple);
        // helpers that belong to a feature flag
        if (simple.equals("EntitySectionIterMixin") || simple.equals("ClassInstanceMultiMapAccessor"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.EntitySectionScanMixin")
                    || Boolean.getBoolean("lab.opt.EntitySectionIndexMixin");
        if (simple.equals("EntitySectionKeyMixin") || simple.equals("EntityCallbackAccessor") || simple.equals("EntityBoxIndexMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.EntitySectionIndexMixin");
        if (simple.equals("NeighborReactStateMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NeighborReactMixin");
        if (simple.equals("ChunkCacheClearMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.ChunkCacheMixin");
        if (simple.equals("LevelNeighborUpdaterAccessor"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NeighborNotifySkipMixin");
        // both replace Util's pools: LabPools wins when both are requested
        if (on && simple.equals("PersistentPoolsMixin")
                && (Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.LabPoolsMixin"))) on = false;
        if (on) {
            System.out.println("[probe/opt] applying " + simple + " -> " + targetClassName);
        }
        return on;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
