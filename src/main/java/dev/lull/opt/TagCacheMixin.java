package dev.lull.opt;

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

    @Unique private Object sleep$set, sleep$k1, sleep$k2;
    @Unique private boolean sleep$v1, sleep$v2;

    /**
     * @author probe (lab.opt.TagCacheMixin)
     * @reason memoize tag membership on the server thread; identical results
     */
    @Overwrite
    public boolean is(TagKey<T> tagKey) {
        Set<TagKey<T>> t = this.tags;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();   // current one: a client starts a new server per world
        if (server == null || Thread.currentThread() != server.getRunningThread()) return t.contains(tagKey);
        if (sleep$set == t) {
            if (sleep$k1 == tagKey) return sleep$v1;
            if (sleep$k2 == tagKey) return sleep$v2;
        } else {
            sleep$set = t;
            sleep$k1 = sleep$k2 = null;
        }
        boolean r = t.contains(tagKey);
        sleep$k2 = sleep$k1;
        sleep$v2 = sleep$v1;
        sleep$k1 = tagKey;
        sleep$v1 = r;
        return r;
    }
}
