package dev.sleep.optsupport;

/** ClientBiomeMemoMixin: the client level's noise-biome memo. */
public interface BiomeMemo {
    void sleep$biomesChanged();

    String sleep$biomeReport();
}
