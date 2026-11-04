package dev.lull.shaders;

/**
 * Oculus / Iris / OptiFine loaded: they run packs; sleepium-shaders.mixins.json is not applied (ShaderMixinPlugin) and
 * ShaderPipeline loads no pack. A class of its own, without Minecraft types: the mixin plugin asks it while mixin
 * configs load (no game class may load then), and game code may not load classes of a mixin package.
 */
public final class OtherShaderMods {
    private OtherShaderMods() {}

    public static boolean present() {
        var mods = net.minecraftforge.fml.loading.LoadingModList.get();
        return mods != null && (mods.getModFileById("iris") != null || mods.getModFileById("oculus") != null || mods.getModFileById("optifine") != null);
    }
}
