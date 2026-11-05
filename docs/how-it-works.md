# How it works

What the engine does and why each part is exact. The class names are the same in every version's branch; where a version differs, [Versions](versions.md) says how.

A NeoForge mod for Minecraft 1.21.1 that makes servers and clients cheaper to run **without changing what the game
computes**. A hopper with nothing to do, a resting item stack, a furnace waiting for fuel: in vanilla each of them runs
its full tick 20 times a second only to find out that nothing happened. The engine proves from the class's own bytecode
and from what it observes that a tick only moves clocks, takes the object off the tick ("puts it to sleep") and wakes it
at exactly the tick vanilla would have acted. Around that idea sit an event engine, exact memos and indexes, parallel
world generation, batched chunk I/O, and a renderer that keeps the frames pixel-identical.

Every optimisation is a switch, and each one is checked against vanilla. A scene runs twice from the same seeds, once
with the optimisations off and once with them on. After the same number of ticks the blocks, the block entities and
every field of every entity are hashed. On the client, rendered frames are hashed.

## What is inside, and why

The sections follow the order in which the work was done. Each starts from a measured cost.

### Block-entity sleep (`SleepMixin`)

**Why.** A field diff over every ticking block entity showed that **93–100 % of block-entity ticks are pure time
passing**: only clock fields change. This holds for any type, modded ones included. 1 024 idle hoppers cost ~0.55 ms and
7.5 MB/s of garbage only to confirm that nothing happened. Existing mods put a fixed list of vanilla types to sleep;
nothing did it for modded block entities.

**What.**
- For each class, a bytecode analysis finds the clock fields and the conditions that compare them (deadlines). No
  per-type rules are written by hand.
- At run time an observed tick records which fields change, which parts of the world the tick reads (blocks, entity
  boxes, other block entities) and the clocks' rates.
- Asleep, the block entity leaves the ticking list. Its reads become wake subscriptions (a block change, an entity
  entering a box, a neighbour's `setChanged`) and its deadline goes into a timing wheel. On wake its clocks are
  fast-forwarded by rate × elapsed ticks.
- Before every skip it re-checks its own fields, so an outside write (a bell being hit) wakes it.
- Classes changed by another mod's mixin are refused. Sleeping block entities are written back before every save and
  stay awake while a menu is open.

Most ticking vanilla types can sleep. Sculk sensors, shriekers, trial spawners, vaults, spawners, beacons and conduits
are refused, each for a reason that `/sleepium sleep` lists.

**Effect.** Host: 1 024 hoppers 0.597 → 0.250 ms p50 (−58 %), allocation −94 %; smelters −43 %. In tick-sprint mode
hoppers ran 4 125 → 20 366 TPS.

### Entity sleep (`EntitySleep`)

The same analysis applied to an entity's `tick()`. After enough quiet ticks with a periodic state, the tick is replayed
from recorded values instead of executed. The entity wakes on block changes in what it reads, entity events in its
boxes, synched-data changes and deadlines. Items, XP orbs, arrows, falling blocks, frames, paintings, displays, TNT and
minecarts are eligible: 42 vanilla classes and more. With 4 000 resting items, allocation drops by 85 %.

### Mob sleep: opt-in

**Why.** After the rest, mobs were the largest per-entity cost left (~30 µs per mob on the N100). 79 % of a farm
animal's ticks are quiet, in streaks of 32–39 ticks. But every quiet tick draws from the mob's random generator, so
replaying a period can never put a mob to sleep.

**What.**
- The analysis follows a mob's own AI objects: navigation, controls, goal selectors and each goal.
- It treats draws from the mob's own random generator as conditions. A replayed tick advances the seed exactly. The
  deadline is the first tick at which a simulated copy of the generator would take another branch.
- A **write barrier** is inserted before every field write into a mob's parts: one load and one branch when nobody
  watches. It marks the changed slots, so a replayed tick verifies ~9 values instead of ~330.

**Effect.** N100, 300 animals + 50 villagers: −8 to −11 %. All 300 animals stay identical to vanilla over 2 400
ticks. It stays opt-in for three reasons: the gain is modest; villagers are not eligible yet and pay ~5 µs each for the
barrier; and the first analysis of each class allocates heavily, which costs ~300 MB more resident memory.

### Event bus and event sites (`NeoForgeBusMixin`, `EventSiteMixin`, `ClientEventSiteMixin`)

**Why.** NeoForge's bus dispatches through one megamorphic call site, so 100 listeners cost 75× as much as 100 direct
calls. A census showed that 97–99 % of posts have no listener at all and still allocate their event.

**What.**
- Each listener list gets a generated class with one monomorphic call site per listener, and cancellation is compiled
  in.
- Every `EVENT_BUS.post(new E(...))` in NeoForge's hooks (161 server and 80 client sites) becomes an `invokedynamic`
  site. While `E` has no listener, the site returns without posting, so the JIT removes the allocation. Registering a
  listener switches the sites back.

**Effect.** 50 listeners: 542 → 30–38 ns per post. 100 listeners: 1 270 → 78–97 ns. The overhead for 0–2 listeners is
3–5 ns. Gains show up in mod packs with many listeners.

### Exact memos and indexes for entities, collisions and AI

| what | why | effect (host) |
|---|---|---|
| a spatial index of bitsets inside entity sections holding ≥ 48 entities; queries visit candidates in vanilla's order | 77 % of the items scene was an O(n²) scan inside sections | items 51.4 → 9.0 ms |
| collision memos: `noCollision`, `collide` and the supporting block are reused while the box, the sections (identity + modification counter) and the static shapes are unchanged | resting items re-check the same box every tick | items 6.47 → 5.19 ms, then −25 % MSPT / −38 % allocation |
| entity collisions skipped when no nearby section holds a class that overrides `canBeCollidedWith` | | items 8.23 → 6.46 ms |
| Brain behaviours as flat arrays in vanilla's order; POI range queries visit only sections that hold POI | villagers walked TreeMap → HashMap → LinkedHashSet twice per tick | villagers −23 % |
| tag membership memo, one-chunk cache in `getBlockState`, full-block collision math without temporary objects | | cramming, general |

All of these were shadow-checked (0 mismatches over millions of queries) and give the same entity hashes as vanilla.

### Redstone, neighbour updates, fluids

Faster redstone wire algorithms (Alternate Current and similar) change the update order, so they are not
vanilla-exact. Sleepium keeps vanilla's algorithm and removes its garbage. The neighbour-update queue becomes
struct-of-arrays. The signal path uses a reused position wherever a bytecode proof shows the block never keeps it.
Updates to blocks that do not override `neighborChanged` are skipped. Fluid spreading uses generation-stamped arrays
instead of per-call hash maps.

**Effect.** Redstone allocation −46 %; fluids −29 % mean time, p99 33 → 23 ms, allocation −65 %. Redstone speed itself
moves only ~10 %: the time goes to the algorithm, which has to stay as it is.

### Threads, pools and the server reactor

- Vanilla re-creates IO and ForkJoin threads constantly. They are replaced by persistent workers that spin briefly,
  then park, at below-normal priority.
- Mailboxes drain up to 64 tasks per dispatch, and a thread is unparked only if it is actually waiting.
- On the client this alone cut frame p99 from 31–42 ms to 22–23 ms, because workers stopped pre-empting the render
  thread.
- Between ticks the server thread parks until the next deadline or a signal (a task, a finished chunk, light work)
  instead of vanilla's poll every 100 µs. Event-to-wake latency is 6 µs, and an idle server uses 0.01 cores instead of
  0.03.
- A timing wheel (1 024 one-tick slots plus a heap) serves scheduled block ticks and every sleep deadline.

### World generation (`ParallelWorldgenMixin`, `WorldgenAlloc`, `WorldgenSkip`, `WorldgenShare`)

**Why.** Vanilla runs the heavy steps (SURFACE, CARVERS, FEATURES) one chunk at a time per level. Before the fix,
the profile showed workers waiting, not working: batching the mailboxes removed 97.7 % of the wake-ups and changed
throughput by nothing. Inside a chunk, 64.6 % of all blocks take the aquifer's slow path just to answer "air" above
the surface.

**What.**
- The heavy steps run on the shared pool. A FEATURES step claims its 3×3 neighbourhood in a table, so no worker
  blocks. Pieces of one structure are placed in order.
- Interval proofs show that whole noise cells are air (58 % of cells) or solid, so they are not evaluated block by
  block.
- Surface heights and border columns are shared between neighbours.
- Lambdas and iterators are removed from the surface rules: 19.2 → 9.6 MB of garbage per chunk.

### Chunk save and load (`AsyncChunkSaveMixin`, `ChunkIo`, `LightSnapshotMixin`)

**Why.** On Windows, vanilla writes region files with DSYNC, header and data synchronously per chunk: ~1 ms each.
Saving on the main thread allocated 6–14 MB per saving tick.

**What.**
- Palettes are encoded and compressed on the pool, from copies. Saves of one position stay ordered, and a read waits
  for a pending save.
- Writes are group-committed per region file: data, `force`, header once, `force`. The file format and bytes are
  unchanged.
- Chunks without saved data are created off the main thread.
- The light engine's snapshot is published as base + delta instead of a full copy (~330 copies/s while generating).

**Effect.** Tick p99 −33 % and allocation −52 % on a real world. Exit save after two long teleports 5 751 → 119 ms.

### Rendering (client)

Every change is checked against vanilla with frame hashes.

| what | why | effect (host) |
|---|---|---|
| shader sampler locations stored instead of `glGetUniformLocation` on every apply | 78 % of native render time in an entity scene | ~2× fps in that scene |
| chunk layers in arenas drawn with one indirect multi-draw per layer; 16-byte packed vertices where bit-exact | vanilla binds a VAO and uploads an offset per section per layer | terrain 242–283 → 683–782 fps in the first step; chunk memory −47 % |
| **render cache** ("render sleep"): a renderer's output is recorded once in local space and replayed with the pose while its state hash is unchanged | entities are rebuilt and re-uploaded every frame (5.2 MiB per frame for a still scene) | entity scene 152 → 367 fps |
| entities rendered on worker threads into per-worker buffers, merged in vanilla's order | one render thread | 174 → 299 fps; 2.49 M jobs verified byte-equal |
| model parts posed on the GPU: 144 bytes per part, one indirect draw | | 225 mobs 1 255 → 1 773 fps |
| translucent sort as a stable radix sort (same permutation) | | 63.5 M quads, 0 mismatches |
| the translucent layer (water, glass, ice) in one indirect multi-draw: the sections' vertices and vanilla's sorted indices (renewed by every resort) in one arena, drawn in vanilla's order | the terrain view was bound by the driver's thread, busy with vanilla's per-section translucent draws | river view 1 227–1 383 → 2 635–2 833 fps; N100 client CPU 1.67 → 0.52 cores |
| per-vertex light "recipes" and per-section light volumes (water too since 1.0.3): a light-only change refreshes the volume instead of re-meshing | light switching re-meshed whole sections | compile work −42 % |
| client light updates on a worker thread | | light-switching p99 7.83 → 3.11 ms |
| per-frame garbage removed (pose stacks, buffers, iterators, colour vectors) | | 3 984 → 74 KiB per frame |

Requirements: multi-draw needs OpenGL 4.6 or `ARB_shader_draw_parameters`, indirect drawing OpenGL 4.3; both are
checked at run time. Intel's driver needed two fixes, found on the N100: no `readonly` SSBOs under GLSL 150, and no
indirect draws from a re-specified buffer.

### Networking (`NetReactor`, `NetCoalesce`, `NetBulk`)

- Sends are queued per connection and flushed as one netty task at the end of the tick. Packets, their order and
  their bytes are the same as vanilla's. Vanilla sends each packet as its own task and socket flush: on a 350-mob scene
  that is 35 028 tasks, against 520 here.
- For clients that have the mod, a flush is packed into one per-connection deflate stream: 45–52 % fewer bytes on the
  wire.
- Bulk content is sent content-addressed (SHA-256 chunks with a client cache, resumable), so nothing the client
  already has is sent again.

### GeckoLib (only when installed)

GeckoLib shares one bone set per model. Sleepium gives each render worker its own copy, so GeckoLib entities render in
parallel. Bones are drawn through the GPU-posed part path, and Molang expressions are compiled to bytecode with
GeckoLib's exact operators: 0 mismatches over 59 895 test cases. Synthetic scene with 140 animated mobs and 48
animated block entities: 131–148 → 1 363–1 475 fps.

### Measurement tools

- `/sleepium run <seconds> <label>`: MSPT mean, p50 and p99; allocation per tick; CPU by thread group; GC and JIT time.
- `/sleepium events`: a per-tick census of bus posts, neighbour updates, scheduled ticks and block-entity / entity ticks.
- `/sleepium sleep`, `/sleepium sleepents`: which classes sleep and why the others do not.
- `/sleepium hash`, `/sleepium equiv`: region and entity hashes, the in-world clone comparison.
- `/sleepium genbench`: chunks/s, time and allocation per generation step, the CARVERS hash.
- `/sleepium bench`, `/sleepium busbench`, `/sleepium threads`, `/sleepium net`, `/sleepiumclient` (client frame statistics).
- Test scenes: `/function sleep:lag_<scene>` (server) and `sleep:client_<scene>` (client), in `src/scenes`.
- `tools/exactness.py`, `tools/exactness_all.sh`, `tools/bench.py`, `tools/server.py`: the procedures above.

## Known limits

- **Other optimisation mods.** 22 were tested together with Sleepium (see [Compatibility](compatibility.md)); others
  are not. Where another mod changes the same code, Sleepium's change there steps aside (logged as "not applied");
  the sleep engines refuse classes that another mod's mixin has changed. Not ours, but seen in the tests:
  MoreCulling 1.0.11 fails to load on NeoForge 21.1.251 on its own, and Create and Moonrise do not start together.
- **Visible differences from vanilla.**
  - The render cache and GPU-posed parts may differ at float-rounding level: a few pixels at silhouette edges, 4 of
    269 864 on the host and 2 on the N100.
  - With client light on a worker thread, a light change re-meshes a few frames later.
  - Overlapping translucent objects can sort differently: 1 pixel on a torture scene. Set
    `-Dlab.rcache.translucentResident=false` for vanilla's order.
  - Everything else is hash-identical.
- **Pooled render objects.** A mod that keeps a popped `PoseStack` pose or a vertex array after a frame would see it
  reused.
- **Five flags in `defaults.txt` (`GeoAnim*`, `GeoMarkersMixin`, `KeyframeInternMixin`) are read by mods built on the
  engine**; on their own they do nothing.
- **Some defaults have no measured effect**, though they are exact: `ShapeCacheMixin`, `LightLayerCacheMixin`,
  `ClientBiomeMemoMixin`, `ChunkTickMemoMixin`, `UniformDedupeMixin`, `GoalSelectorMixin`.

## Lessons

These are the mistakes that cost the most time. Each produced a rule that the numbers above follow.

- **Profilers lie about periodic work.**
  - JFR showed GoalSelector's EnumMap iteration as 19.9 % of allocation; removing it saved 1–3 %.
  - A light-layer cache (JFR: 15–19 % of time) and a pathfinding hook (JFR: path creation halved) changed nothing in
    tick time.
  - Rule: time a hot spot directly before building a fix.
- **Profile what waits, not only what runs.** World generation was limited by one serial actor, not by the code the
  profiler showed.
- **Hooks are not free on hot paths.**
  - A redirect in `Level.getBlockState` stopped inlining and escape analysis: +1 MB per tick of garbage.
  - Mixin's cancellable injectors allocate a `CallbackInfo` per call.
  - C2 has no partial escape analysis: a lambda that reaches a non-inlined call on any path is allocated every time.
- **Garbage collector choice.** Generational ZGC slowed hoppers by 68 % (its barriers). On the N100 even Epsilon (no GC
  at all) was slower than G1: the cost of allocation is cache misses, not collection.
- **Test on weak hardware and in singleplayer.**
  - The N100 found two Intel-only GL bugs.
  - Singleplayer found that the client's `Util.getNanos()` is GLFW time, not `System.nanoTime`: every server deadline
    looked 45 hours late, and the world hung while preparing spawn.
- **A check that cannot fail checks nothing.** Scenes put down as "vanilla isn't reproducible" were set aside as
  untestable, and one of them hid a real wake-up bug. The cause was the harness: unseeded randoms, and a hash that included object
  identities. Make vanilla reproducible first, then compare.
- **The order of an A/B is a variable.** On the N100, the build measured first in each pair looked 3–5 % slower on
  villagers in every pass. With the order reversed, the difference disappeared.
- **Benchmark self-cost.** The engine's own event counter forced every entity tick onto a slow path (~7 %). Hashing
  chunks was a quarter of the generation benchmark.
- **Ideas dropped after measuring.**
  - Region-parallel ticking (Folia-style) is not vanilla-exact and is unsafe for mods.
  - Per-entity speculation conflicted 99.5 % of the time in dense scenes.
  - Lock-free rewrites: contended locks were near zero.
  - A second generation pool made every step 1.5–3× slower.
  - Class preloading had no effect.
  - The first move memo was exact but 22 % slower, because its key cost more than the move.
