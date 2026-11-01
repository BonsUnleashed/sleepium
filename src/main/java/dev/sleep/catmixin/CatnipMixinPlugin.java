package dev.sleep.catmixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** sleep-catnip.mixins.json, applied only when Ponder (catnip: Create's render library) is loaded: SbbPartsMixin. */
public final class CatnipMixinPlugin implements IMixinConfigPlugin {
    static {
        dev.sleep.optsupport.GameDefaults.ensure();   // a built jar's flags before they are read
    }

    /** SbbPartsMixin: SuperByteBuffer templates drawn from VRAM, {@code -Dlab.opt.SbbPartsMixin=true} with PartBatchMixin */
    private static final boolean PARTS = (Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.SbbPartsMixin"))
            && (Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.PartBatchMixin"));

    static boolean ponder() {
        var mods = net.neoforged.fml.loading.LoadingModList.get();
        return mods != null && mods.getModFileById("ponder") != null;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean on = PARTS && ponder();
        if (on) System.out.println("[sleep/opt] applying " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " -> " + targetClassName);
        return on;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
