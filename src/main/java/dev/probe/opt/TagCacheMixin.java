package dev.probe.opt;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/tag-cache: {@code Holder.Reference.is(TagKey)} is a Set.contains with record hashing and equals on every call.
 * JFR cramming scene: ~30 % of the server thread (pushableBy -> isPushable -> onClimbable -> isLadder ->
 * state.is(CLIMBABLE), for every pair of crammed mobs). Here: a 2-entry memo per holder keyed by the identity of
 * the TagKey AND of the holder's current tag set (rebinding tags on /reload replaces the set, which invalidates the
 * memo by itself). Server thread only (single writer and reader, no synchronisation); other threads use vanilla.
 * Exact: same key object + same set object = same answer.
 */
@Mixin(Holder.Reference.class)
public abstract class TagCacheMixin<T> {
    @Shadow private Set<TagKey<T>> tags;

    @Unique private Object probe$set, probe$k1, probe$k2;
    @Unique private boolean probe$v1, probe$v2;
    @Unique private static Thread probe$server;

    /**
     * @author probe (lab.opt.TagCacheMixin)
     * @reason memoize tag membership on the server thread; identical results
     */
    @Overwrite
    public boolean is(TagKey<T> tagKey) {
        Set<TagKey<T>> t = this.tags;
        Thread server = probe$server;
        if (server == null) {
            var s = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (s == null) return t.contains(tagKey);
            probe$server = server = s.getRunningThread();
        }
        if (Thread.currentThread() != server) return t.contains(tagKey);
        if (probe$set == t) {
            if (probe$k1 == tagKey) return probe$v1;
            if (probe$k2 == tagKey) return probe$v2;
        } else {
            probe$set = t;
            probe$k1 = probe$k2 = null;
        }
        boolean r = t.contains(tagKey);
        probe$k2 = probe$k1;
        probe$v2 = probe$v1;
        probe$k1 = tagKey;
        probe$v1 = r;
        return r;
    }
}
