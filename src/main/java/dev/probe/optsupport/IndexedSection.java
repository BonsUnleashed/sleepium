package dev.probe.optsupport;

/** Added to EntitySection by EntitySectionIndexMixin: its section key and its (lazy) spatial index. */
public interface IndexedSection {
    long probe$key();

    void probe$setKey(long key);

    SectionIndex probe$index();

    void probe$setIndex(SectionIndex index);
}
