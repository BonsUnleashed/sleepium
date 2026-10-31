package dev.sleep.optsupport;

/** Added to EntitySection by EntitySectionIndexMixin: its section key and its (lazy) spatial index. */
public interface IndexedSection {
    long probe$key();

    void probe$setKey(long key);

    SectionIndex probe$index();

    void probe$setIndex(SectionIndex index);

    /** Index over the typed list of {@code baseClass} (getEntitiesOfClass queries); null if none yet. */
    SectionIndex probe$typedIndex(Class<?> baseClass);

    void probe$putTypedIndex(Class<?> baseClass, SectionIndex index);

    /** Bounding-box change of an entity of this section: update the typed indices (no lambda per call). */
    void probe$forEachTyped(Object entity, net.minecraft.world.phys.AABB bb);
}
