package dev.probe.opt;

import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * AllocTrim (lab.opt.AllocTrim): WeightedRandom's loops walk a random-access list by index instead of an iterator -
 * the iterator was 5.7 % of all allocation during teleports (feature and spawn picks, 10-08). The same elements in the
 * same order; other lists keep vanilla's iterator.
 */
@Mixin(WeightedRandom.class)
public abstract class AllocTrimWeightedMixin {
    /**
     * @author probe (AllocTrim)
     * @reason no iterator for random-access lists
     */
    @Overwrite
    public static int getTotalWeight(List<? extends WeightedEntry> entries) {
        long i = 0L;
        if (entries instanceof RandomAccess) {
            for (int k = 0, n = entries.size(); k < n; k++) i += (long) entries.get(k).getWeight().asInt();
        } else {
            for (WeightedEntry e : entries) i += (long) e.getWeight().asInt();
        }
        if (i > 2147483647L) throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        return (int) i;
    }

    /**
     * @author probe (AllocTrim)
     * @reason no iterator for random-access lists
     */
    @Overwrite
    public static <T extends WeightedEntry> Optional<T> getWeightedItem(List<T> entries, int weightedIndex) {
        if (entries instanceof RandomAccess) {
            for (int k = 0, n = entries.size(); k < n; k++) {
                T t = entries.get(k);
                weightedIndex -= t.getWeight().asInt();
                if (weightedIndex < 0) return Optional.of(t);
            }
            return Optional.empty();
        }
        for (T t : entries) {
            weightedIndex -= t.getWeight().asInt();
            if (weightedIndex < 0) return Optional.of(t);
        }
        return Optional.empty();
    }
}
