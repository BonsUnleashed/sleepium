package dev.lull;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Thread-safe entry points for mixins (called from worldgen worker threads).
 *
 * <p>Per-step hashes (ChunkStepHashMixin, -Dsleep.genbench.hash containing "steps"): on 1.20.1 the final blocks of a
 * region depend on the order neighbouring chunks run FEATURES (each writes into its 3x3), so even two vanilla runs give
 * different region hashes. A step is a function of what it reads, so each (chunk, step) records the hash of its window
 * right before the step body and right after it: the chunk itself for the radius-0 steps, the 3x3 it writes for
 * FEATURES. Two runs are then compared per step - where the input hashes agree the output hashes must agree; a step
 * whose output differs from the same input is where an optimisation changed generation. Steps whose input already
 * differs (another order of the neighbours' features) are not judged. The window covers what the step can write; what
 * it reads further out was written by earlier steps, which are judged on their own.
 *
 * <p>The hash covers block states, biomes, post-processing marks, heightmaps, carving masks, structure starts and
 * references, and block entities; entities (whose ids and random spawn bonuses are per run) only with
 * -Dsleep.genbench.entities=true. Light steps are not hashed (they write no blocks; their data is not hashed).
 */
public final class GenBenchHooks {
    static volatile boolean active;
    static volatile int x0, z0, side;
    /** chunk -> hash after CARVERS */
    static final ConcurrentHashMap<Long, Long> CARVERS = new ConcurrentHashMap<>();
    /** (status index, chunk) -> {input hash, output hash} */
    static final ConcurrentHashMap<Long, long[]> STEPS = new ConcurrentHashMap<>();
    private static final boolean ENTITIES = Boolean.getBoolean("sleep.genbench.entities");

    private GenBenchHooks() {}

    public static boolean active() {
        return active;
    }

    static boolean inArea(ChunkPos p) {
        return p.x >= x0 && p.x < x0 + side && p.z >= z0 && p.z < z0 + side;
    }

    static long stepKey(int statusIndex, ChunkPos p) {
        return ((long) statusIndex << 58) ^ p.toLong();
    }

    /** the radius a step writes: FEATURES its 3x3 (WorldGenRegion's write radius 1), every other step its own chunk */
    private static int window(ChunkStatus status) {
        return status == ChunkStatus.FEATURES ? 1 : 0;
    }

    /** whether ChunkStepHashMixin records this step of this generate call (its centre in the bench area) */
    public static boolean recordsStep(ChunkStatus status, List<ChunkAccess> cache) {
        if (!active || !GenBench.HASH_STEPS || status == ChunkStatus.INITIALIZE_LIGHT || status == ChunkStatus.LIGHT) return false;
        return inArea(cache.get(cache.size() / 2).getPos());
    }

    /** the hash of the step's window (the generate call's cache: (2r+1)^2 chunks, x fastest, the centre in the middle) */
    public static long windowHash(ChunkStatus status, List<ChunkAccess> cache, ChunkAccess center) {
        int r = window(status);
        if (r == 0) return chunkHash(center);
        int w = (int) Math.round(Math.sqrt(cache.size())), c = (w - 1) / 2;
        long h = 1469598103934665603L;
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++) {
                ChunkAccess chunk = dx == 0 && dz == 0 ? center : cache.get((c + dx) + (c + dz) * w);
                h = (h ^ chunkHash(chunk)) * 1099511628211L;
            }
        return h;
    }

    public static void onStep(ChunkStatus status, ChunkPos pos, long in, long out) {
        STEPS.put(stepKey(status.getIndex(), pos), new long[] {in, out});
    }

    public static void onStepDone(ChunkStatus status, ChunkAccess chunk) {
        if (status != ChunkStatus.CARVERS || !GenBench.HASH_CARVERS) return;
        ChunkPos p = chunk.getPos();
        if (!inArea(p)) return;
        long h = GenBench.hashChunk(chunk);
        // plus the post-processing marks (fluid updates the aquifer asked for): they are not block states
        it.unimi.dsi.fastutil.shorts.ShortList[] pp = chunk.getPostProcessing();
        for (int i = 0; i < pp.length; i++) {
            h = (h ^ (i + 1)) * 1099511628211L;
            if (pp[i] != null) for (int n = 0; n < pp[i].size(); n++) h = (h ^ pp[i].getShort(n)) * 1099511628211L;
        }
        CARVERS.put(p.toLong(), h);
    }

    private static long mix(long h, long v) {
        return (h ^ v) * 1099511628211L;
    }

    /** an order-independent term for set / map members (their iteration order is not part of the state) */
    private static long spread(long v) {
        v = (v ^ (v >>> 33)) * 0xff51afd7ed558ccdL;
        v = (v ^ (v >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return v ^ (v >>> 33);
    }

    private static long str(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h;
    }

    /** everything generation writes into one chunk (see class doc), in a fixed order */
    static long chunkHash(ChunkAccess chunk) {
        long h = GenBench.hashChunk(chunk);
        for (LevelChunkSection section : chunk.getSections()) {
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < 4; z++)
                    for (int x = 0; x < 4; x++) {
                        Holder<Biome> b = section.getNoiseBiome(x, y, z);
                        h = mix(h, b.unwrapKey().map(k -> str(k.location().toString())).orElse(-1L));
                    }
        }
        it.unimi.dsi.fastutil.shorts.ShortList[] pp = chunk.getPostProcessing();
        for (int i = 0; i < pp.length; i++) {
            h = mix(h, i + 1);
            if (pp[i] != null) for (int n = 0; n < pp[i].size(); n++) h = mix(h, pp[i].getShort(n));
        }
        for (Heightmap.Types t : Heightmap.Types.values()) {
            if (!chunk.hasPrimedHeightmap(t)) continue;
            h = mix(h, t.ordinal() + 100);
            for (long v : chunk.getOrCreateHeightmapUnprimed(t).getRawData()) h = mix(h, v);
        }
        if (chunk instanceof ProtoChunk proto) {
            for (GenerationStep.Carving c : GenerationStep.Carving.values()) {
                var mask = proto.getCarvingMask(c);
                if (mask != null) for (long v : mask.toArray()) h = mix(h, v);
                h = mix(h, c.ordinal() + 200);
            }
        }
        long starts = 0;
        for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
            StructureStart s = e.getValue();
            long t = str(structureId(e.getKey()));
            t = mix(t, s.getChunkPos().toLong());
            t = mix(t, s.getPieces().size());
            if (s.isValid()) {
                var bb = s.getBoundingBox();
                t = mix(mix(mix(mix(mix(mix(t, bb.minX()), bb.minY()), bb.minZ()), bb.maxX()), bb.maxY()), bb.maxZ());
            }
            starts += spread(t);
        }
        h = mix(h, starts);
        long refs = 0;
        for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
            long id = str(structureId(e.getKey()));
            for (long v : e.getValue()) refs += spread(mix(id, v));
        }
        h = mix(h, refs);
        long bes = 0;
        for (BlockPos p : chunk.getBlockEntitiesPos()) {
            CompoundTag nbt = chunk.getBlockEntityNbtForSaving(p);
            bes += spread(mix(p.asLong(), nbt == null ? 0 : str(SleepCommand.canonNbt(nbt))));
        }
        h = mix(h, bes);
        if (ENTITIES && chunk instanceof ProtoChunk proto) {
            long ents = 0;
            for (CompoundTag e : proto.getEntities()) {
                CompoundTag t = e.copy();
                t.remove("UUID");
                for (net.minecraft.nbt.Tag at : t.getList("Attributes", net.minecraft.nbt.Tag.TAG_COMPOUND))
                    for (net.minecraft.nbt.Tag m : ((CompoundTag) at).getList("Modifiers", net.minecraft.nbt.Tag.TAG_COMPOUND))
                        ((CompoundTag) m).remove("UUID");
                ents += spread(str(SleepCommand.canonNbt(t)));
            }
            h = mix(h, ents);
        }
        return h;
    }

    private static String structureId(Structure s) {
        // the structure registry of the running server: the same ids in every run
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return s.getClass().getName();
        var key = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(s);
        return key == null ? s.getClass().getName() : key.toString();
    }
}
