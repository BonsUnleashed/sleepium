package dev.lull.optsupport;

import java.util.Iterator;
import net.minecraft.world.phys.Vec3;

/**
 * Small helper types the client mixins use. They live here, not as nested classes of the mixins: Mixin refuses to
 * load classes of a mixin package (IllegalClassLoadError), depending on how they are first reached.
 */
public final class MixinHelpers {
    private MixinHelpers() {}

    /** Vec3FromRgbMixin's cache entry. */
    public record RgbEntry(int rgb, Vec3 v) {}

    /** OverworldFogMixin's memo entry. */
    public record FogEntry(Vec3 in, int brightness, Vec3 out) {}

    /** ModelPartIterMixin's reusable array iterator. */
    public static final class ArrayIter implements Iterator<Object> {
        private Object[] a;
        private int i;

        public ArrayIter reset(Object[] a) {
            this.a = a;
            this.i = 0;
            return this;
        }

        @Override
        public boolean hasNext() {
            return i < a.length;
        }

        @Override
        public Object next() {
            return a[i++];
        }
    }
}
