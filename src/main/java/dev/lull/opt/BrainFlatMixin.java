package dev.lull.opt;

import dev.lull.optsupport.OptCounters;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/brain-flat: universal for every Brain-driven mob (villagers, piglins, axolotls, frogs, allays, wardens, ... and
 * modded ones). Every tick vanilla walks TreeMap(priority) -> HashMap(activity) -> LinkedHashSet(behaviours) twice:
 * once to start stopped behaviours, once to collect the running ones into a fresh list. JFR, 120 villagers:
 * ~15 % of the server thread, mostly iterator setup and pointer chasing through map nodes.
 *
 * <p>Here the same structure is flattened into arrays in exactly the vanilla iteration order: behaviours in a row,
 * groups (priority, activity) with their end index. The activity check stays once per group, at the same moment as in
 * vanilla. The arrays are rebuilt after every mutation through Brain's own mutators (epoch counter); a mutation from
 * inside a behaviour (vanilla sees the later groups' new contents) is followed by resuming after the same behaviour
 * in the rebuilt order. The running snapshot goes into a reusable array instead of a new list.
 * {@code -Dlab.opt.brainCheck=true} compares the flat order with the vanilla walk on every tick.
 */
@Mixin(Brain.class)
public abstract class BrainFlatMixin<E extends LivingEntity> {
    @Shadow @Final private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;
    @Shadow @Final private Set<Activity> activeActivities;

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.brainCheck");

    @Unique private int sleep$epoch, sleep$builtEpoch = -1;
    @Unique private BehaviorControl<? super E>[] sleep$beh;
    @Unique private Activity[] sleep$groupAct;
    @Unique private int[] sleep$groupEnd;
    @Unique private BehaviorControl<? super E>[] sleep$runBuf;
    @Unique private boolean sleep$runBusy;

    @Inject(method = "addActivityAndRemoveMemoriesWhenStopped", at = @At("TAIL"))
    private void sleep$added(CallbackInfo ci) {
        sleep$epoch++;
    }

    @Inject(method = "removeAllBehaviors", at = @At("TAIL"))
    private void sleep$cleared(CallbackInfo ci) {
        sleep$epoch++;
    }

    // 1.20.1: Forge's Brain.copyFromBuilder (LivingMakeBrainEvent, ForgeHooks.onLivingMakeBrain) also adds behaviours,
    // straight into availableBehaviorsByPriority (1.21 NeoForge: no such mutator). Forge's own method: not remapped.
    @Inject(method = "copyFromBuilder", at = @At("TAIL"), remap = false)
    private void sleep$copied(CallbackInfo ci) {
        sleep$epoch++;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private BehaviorControl<? super E>[] sleep$flat() {
        if (sleep$builtEpoch == sleep$epoch) return sleep$beh;
        int n = 0, g = 0;
        for (Map<Activity, Set<BehaviorControl<? super E>>> map : availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> set : map.values()) {
                n += set.size();
                g++;
            }
        }
        BehaviorControl<? super E>[] beh = (BehaviorControl<? super E>[]) new BehaviorControl<?>[n];
        Activity[] act = new Activity[g];
        int[] end = new int[g];
        n = 0;
        g = 0;
        for (Map<Activity, Set<BehaviorControl<? super E>>> map : availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> e : map.entrySet()) {
                for (BehaviorControl<? super E> b : e.getValue()) beh[n++] = b;
                act[g] = e.getKey();
                end[g++] = n;
            }
        }
        sleep$beh = beh;
        sleep$groupAct = act;
        sleep$groupEnd = end;
        sleep$runBuf = (BehaviorControl<? super E>[]) new BehaviorControl<?>[n];
        sleep$builtEpoch = sleep$epoch;
        OptCounters.BRAIN_REBUILDS.incrementAndGet();
        return beh;
    }

    /**
     * @author probe (lab.opt.BrainFlatMixin)
     * @reason flat arrays in vanilla iteration order instead of nested map/set iterators
     */
    @Overwrite
    private void startEachNonRunningBehavior(ServerLevel level, E entity) {
        long time = level.getGameTime();
        sleep$flat();
        if (CHECK) sleep$check();
        sleep$start(level, entity, time, null);
    }

    /** Start pass from the beginning, or (after a mid-pass mutation) right after {@code after} in the rebuilt order. */
    @Unique
    private void sleep$start(ServerLevel level, E entity, long time, BehaviorControl<? super E> after) {
        BehaviorControl<? super E>[] beh = sleep$flat();
        Activity[] act = sleep$groupAct;
        int[] end = sleep$groupEnd;
        int epoch = sleep$epoch;
        int i = 0, g = 0;
        boolean checked = false;   // the group of i already passed vanilla's activity check
        if (after != null) {
            int k = 0;
            while (k < beh.length && beh[k] != after) k++;
            if (k == beh.length) return;   // behaviour removed: vanilla would have thrown ConcurrentModificationException
            while (end[g] <= k) g++;
            i = k + 1;
            checked = true;
        }
        for (; g < act.length; g++) {
            int e = end[g];
            if (checked || activeActivities.contains(act[g])) {
                checked = false;
                for (; i < e; i++) {
                    BehaviorControl<? super E> b = beh[i];
                    if (b.getStatus() == Behavior.Status.STOPPED) {
                        b.tryStart(level, entity, time);
                        if (sleep$epoch != epoch) {
                            OptCounters.BRAIN_RESUMES.incrementAndGet();
                            sleep$start(level, entity, time, b);
                            return;
                        }
                    }
                }
            }
            i = e;
        }
    }

    /**
     * @author probe (lab.opt.BrainFlatMixin)
     * @reason same snapshot of running behaviours as vanilla, collected into a reusable array
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    private void tickEachRunningBehavior(ServerLevel level, E entity) {
        long time = level.getGameTime();
        BehaviorControl<? super E>[] beh = sleep$flat();
        boolean own = !sleep$runBusy;
        BehaviorControl<? super E>[] run = own ? sleep$runBuf : (BehaviorControl<? super E>[]) new BehaviorControl<?>[beh.length];
        int n = 0;
        for (BehaviorControl<? super E> b : beh) {
            if (b.getStatus() == Behavior.Status.RUNNING) run[n++] = b;
        }
        if (own) sleep$runBusy = true;
        try {
            for (int k = 0; k < n; k++) run[k].tickOrStop(level, entity, time);
        } finally {
            if (own) sleep$runBusy = false;
        }
    }

    /**
     * @author probe (lab.opt.BrainFlatMixin)
     * @reason same list, same order, from the flat arrays
     */
    @Overwrite
    public List<BehaviorControl<? super E>> getRunningBehaviors() {
        List<BehaviorControl<? super E>> list = new ObjectArrayList<>();
        for (BehaviorControl<? super E> b : sleep$flat()) {
            if (b.getStatus() == Behavior.Status.RUNNING) list.add(b);
        }
        return list;
    }

    /** Shadow check: the vanilla nested walk must give the same behaviours, activities and group boundaries. */
    @Unique
    private void sleep$check() {
        List<Object> a = new ArrayList<>(), b = new ArrayList<>();
        for (Map<Activity, Set<BehaviorControl<? super E>>> map : availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> e : map.entrySet()) {
                a.add(e.getKey());
                a.addAll(e.getValue());
            }
        }
        int i = 0;
        for (int g = 0; g < sleep$groupAct.length; g++) {
            b.add(sleep$groupAct[g]);
            for (; i < sleep$groupEnd[g]; i++) b.add(sleep$beh[i]);
        }
        OptCounters.BRAIN_CHECKED.incrementAndGet();
        boolean same = a.size() == b.size();
        for (int k = 0; same && k < a.size(); k++) same = a.get(k) == b.get(k);
        if (!same && OptCounters.BRAIN_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[sleep/opt] brain flat order MISMATCH: vanilla " + a.size() + " vs flat " + b.size());
    }
}
