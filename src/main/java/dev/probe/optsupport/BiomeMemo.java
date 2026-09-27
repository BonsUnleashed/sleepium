package dev.probe.optsupport;

/** ClientBiomeMemoMixin: the client level's noise-biome memo. */
public interface BiomeMemo {
    void probe$biomesChanged();

    String probe$biomeReport();
}
