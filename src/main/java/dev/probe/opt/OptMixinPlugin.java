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
        if (simple.equals("SectionStorageIndexMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.PoiColumnMixin");
        if (simple.equals("BlockBehaviourInvoker") || simple.equals("ShapeCacheMixin"))   // the memo needs the static-shape flags
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.ShapeCacheMixin") || Boolean.getBoolean("lab.opt.BlockCollisionMemoMixin");
        if (simple.equals("SectionModCountMixin") || simple.equals("EntityCollisionMemoMixin") || simple.equals("EntityCollideMemoMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.BlockCollisionMemoMixin");
        if (simple.equals("EntitySectionCollidableMixin") || simple.equals("EntityStorageCollidableMixin") || simple.equals("LevelEntityGetterAdapterAccessor"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.EntityCollisionSkipMixin");
        if (simple.equals("VoxelShapeAccessor"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.CubeShapeMixin");
        if (simple.equals("SignalProofStateMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.SignalPathMixin");
        if (simple.equals("PoseAccessor"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.PoseStackPoolMixin");
        if (simple.equals("AmbientChunksMixin"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.AmbientParticleSkipMixin");
        if (simple.equals("LevelNeighborUpdaterAccessor"))
            on = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NeighborNotifySkipMixin");
        // both replace Util's pools: LabPools wins when both are requested
        if (on && simple.equals("PersistentPoolsMixin")
                && (Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.LabPoolsMixin"))) on = false;
        // A/B of the client-side ones with the server-side ones on: -Dlab.opt.noClient=true
        if (on && Boolean.getBoolean("lab.opt.noClient") && (simple.startsWith("Section") && simple.contains("Layer") || simple.startsWith("Ambient") || simple.startsWith("Shader") || simple.startsWith("ClientEventSite") || simple.startsWith("Pose") || simple.startsWith("BulkData"))) on = false;
        if (on) {
            System.out.println("[probe/opt] applying " + simple + " -> " + targetClassName);
        }
        return on;
    }

    /**
     * {@code -Dlab.opt.universal=true}: every universal, exactness-checked optimisation at once (not the sleep engine,
     * not the experimental worldgen/pool ones). Expanded into the individual flags before any of them is read.
     */
    static final String[] UNIVERSAL = {"TickMemoMixin", "ChunkCacheMixin", "EntitySectionScanMixin", "EntitySectionIndexMixin",
            "NeighborNotifySkipMixin", "NeoForgeBusMixin", "NeighborReactMixin", "TagCacheMixin", "BrainFlatMixin", "PoiColumnMixin", "EntityCollisionSkipMixin", "BlockCollisionMemoMixin", "EventSiteMixin", "NeighborQueueMixin", "SignalPathMixin", "CubeShapeMixin", "FluidSpreadMixin", "TickSchedulerMixin",
            // client side (applied on the physical client only): event sites, measured -22 % client tick time with the
            // client-thread collision memo (OwnerThread)
            "ClientEventSiteMixin",
            // sampler locations from updateLocations + sampler names made once: entity scene 64 -> 127 fps
            "ShaderSamplerCacheMixin"};
    // client side, exact but no measurable fps effect (3 steady A/B pairs): opt-in only
    //   -Dlab.opt.SectionLayerMaskMixin / AmbientParticleSkipMixin

    @Override
    public void onLoad(String mixinPackage) {
        if (Boolean.getBoolean("lab.opt.universal")) {
            for (String f : UNIVERSAL) System.setProperty("lab.opt." + f, "true");
            System.out.println("[probe/opt] lab.opt.universal: " + String.join(", ", UNIVERSAL));
        }
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".EventSiteMixin") || mixinClassName.endsWith(".ClientEventSiteMixin"))
            dev.probe.optsupport.EventSiteTransformer.transform(targetClass);
    }
}
