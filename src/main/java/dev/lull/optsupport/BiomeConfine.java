package dev.lull.optsupport;

/** WorldgenShare (biomes): a BiomeManager that only one thread uses may keep its per-cell fiddle memo. */
public interface BiomeConfine {
    void sleep$confine();
}
