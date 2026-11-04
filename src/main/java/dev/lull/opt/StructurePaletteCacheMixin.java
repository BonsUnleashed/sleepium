package dev.lull.opt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/parallel-worldgen (1.20.1 only): a StructureTemplate is shared by every structure built from it, and its
 * Palette keeps a per-block filter cache in a HashMap (computeIfAbsent) - read by structure starts (jigsaw:
 * getShuffledJigsawBlocks) and by FEATURES (data markers of placed pieces). Vanilla runs both in the one worldgen actor;
 * with FEATURES on the pool (and structure starts too with WorldgenReactor) two threads fill one palette's cache at once:
 * a ConcurrentModificationException out of computeIfAbsent crashed a nether run (bastions). NeoForge 1.21.1 made this
 * map concurrent itself (its fix of MC-271899); here the same map type. The cached lists are the same either way (a
 * filter of the palette's fixed block list), so generation is unchanged.
 */
@Mixin(StructureTemplate.Palette.class)
public abstract class StructurePaletteCacheMixin {
    @Shadow @Final @Mutable private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sleep$concurrent(List<StructureTemplate.StructureBlockInfo> blocks, CallbackInfo ci) {
        this.cache = new ConcurrentHashMap<>();
    }
}
