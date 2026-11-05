# Testing

How every optimisation is checked against vanilla, how speed is measured, and on which machines. The tools live in each version's branch (`tools/`); they work the same way on every version.

## How it was tested

### Exactness

`tools/exactness.py <scene>` runs a scene twice on a dedicated server of this build:

1. a fresh flat world (fixed seed), ticks frozen with `/tick freeze`;
2. the scene's chunks force-loaded, the clock stepped to game time 300;
3. vanilla's randoms that are seeded from the clock made reproducible (`-Dsleep.seedEntities`, `/sleepium reseed`), so two
   runs draw the same numbers. These are every entity's random generator and UUID, each level's randoms, the
   random-tick generator, the static randoms of `Sensor` and `Mth`, and the randoms of every `ShufflingList` (a brain's
   behaviour order) and `Explosion`;
4. the scene built, then exactly N ticks stepped (`/tick step`, 2 400 by default = two minutes of game time);
5. `/sleepium hash` over the scene's area: every block state, every block entity's saved data and every entity: position,
   motion, saved data and all its primitive fields (`-Dsleep.hashEntitiesDeep`). One row per entity is also written to
   a file.

The first run has the optimisations off, the second has the defaults on. Equal region hashes and equal entity rows
mean the optimisations changed nothing the game computes. `tools/exactness_all.sh` runs every scene this way. When a
scene differs, it runs the scene again with **both** runs off: if vanilla differs from itself, the scene is not
reproducible and cannot judge the engine.

Results on 2026-11-01 (host, 2 400 ticks):

| scene | what moves | block entities | entities | region hash (off = on) | result |
|---|---|---:|---:|---|---|
| restcarts | 192 minecarts at rest | 0 | 192 | `7f7b0f9a10b09dfe` | exact |
| items | 4 000 item stacks | 0 | 4 000 | `081e79e6cf3eeec2` | exact |
| itemwake | items woken by lava and hoppers | 112 | 48 | `b80987a45e5b17eb` | exact |
| carts | items among sliding minecarts | 0 | 1 580 | `81a27c4293fbb277` | exact |
| vehicles | minecarts and boats with passengers, items | 0 | 1 610 | `279f4407b5a0fd1c` | exact |
| cramming | 200 cows in one column | 0 | 200 | `04e3a71723319a19` | exact |
| physics | pistons, falling sand, armour stands on slime | 152 | 123 | `8445b2666d34ebfe` | exact |
| hoppers | 1 024 hoppers | 1 024 | 0 | `8bf3dd5d96cfd0d3` | exact |
| furnaces | 1 024 furnaces | 1 024 | 0 | `85621eb58c3490d3` | exact |
| smelters | 1 024 smelting columns | 5 120 | 0 | `ab3293b779b6efe5` | exact |
| beactive | campfires, brewing stands, furnaces at work | 128 | 48 | `eeea1d6b97420518` | exact |
| bezoo | 16 of every ticking block-entity type | 384 | 0 | `108c14b7ac8e6b54` | exact |
| pistons | 200 observer clocks with pistons | 400 | 0 | `2236ee9b507b6113` | exact |
| redstone | observer → dust → lamp clocks | 0 | 0 | `cbf504a1a65d1d13` | exact |
| crops | random ticks on farmland | 0 | 0 | `01cb368c4a17867f` | exact |
| fluids | flowing water and lava | 0 | 0 | `807715f86a759f73` | exact |
| mobs | 300 farm animals + 50 villagers | 0 | 350 | `7125cd625e4919cb` | exact |
| mobs + mob sleep | 300 farm animals + 50 villagers | 0 | 350 | `7125cd625e4919cb` | exact |
| villagers | 120 villagers with workstations | 32 | 81 | `38c75b5189f7ff7a` | exact |
| jobclash | farmers competing for job sites | 0 | 32 | `3b46cb39b445c5fd` | exact |
| calmzoo | chickens laying eggs, mooshrooms | 0 | 120 | `e4a88d065f0e1b4a` | exact |
| calmzoo + mob sleep | chickens laying eggs, mooshrooms | 0 | 120 | `e4a88d065f0e1b4a` | exact |
| zoo | 13 passive kinds, predators, monsters | 0 | 198 | `3fe343b93a9215e3` | exact |


Every scene: the same region hash with the optimisations off and on, and every entity row equal. Vanilla itself was
reproducible in every scene (off against off) once the harness fixes below were in.

**What the release check found.** In the first full run, five scenes (mobs, villagers, job clash, zoo, vehicles) were
"not reproducible": vanilla differed from itself. None of that was vanilla's fault. It was the harness:

- `Sensor`, `ShufflingList`, `Mth`, `Explosion` and the level's random-tick generator were still seeded from the clock;
- passengers' UUIDs (in a vehicle's saved data) were drawn before the entity's seed was set;
- the deep hash used `toString()` for villager data and trade offers. Those classes don't override it, so the hash
  contained the object's identity, which is different in every run.

With these fixed, all five scenes became reproducible. One of them then showed a **real bug in the engine**. Vanilla
sets an entity's new bounding box only after the position, and the "entity moved" event fired in between, so sleepers
were tested against the entity's *old* box. A boat that came to rest inside a sleeping item's collision box never woke
the item, and 20 items ended in different places. The event now fires once per box change and covers the old and new
boxes together, and the scene is exact. A first fix used two events, one per box. On the N100 it looked 3–4 % slower
on villagers, so it was replaced with the single event, which does the work of the old one plus six min/max
operations.

Other checks used while building each optimisation (all still in the code, switched on by a property):

- **Shadow checks.** The engine runs vanilla's code next to its own and counts mismatches. Examples: entity-section
  scans (2.7 M queries), the spatial index (4.2 M), collision skips (1.9 M), the signal path (1.77 M), light recipes
  (43.6 M vertices), translucent sorting (63.5 M quads), Molang (59 895 test cases). All had 0 mismatches.
- **In-world clone test** (`/sleepium equiv`). The same scene is built twice, 2 000 blocks apart, and only one copy may
  sleep; every block and every block entity's saved data is then compared.
- **Frame hashes.** On the client the GUI is hidden, animated textures and light flicker are frozen, and the colour
  buffer is hashed. Rendering optimisations must give the same hash as vanilla.
- **World generation.** A chunk after the CARVERS step is a pure function of seed and position: 576 of 576 chunks
  hashed equal to vanilla in every batch (also nether, end, amplified, large biomes). After FEATURES, vanilla is not
  reproducible even against itself (feature order between neighbouring chunks depends on thread timing). The parallel
  generator produces one valid vanilla ordering.
- **Chunk I/O.** 508 176 encoded palettes were compared with vanilla's bytes. A crash test killed the process during an
  unload and then read back every region file: 0 bad chunks.

### Speed

`tools/bench.py on|off <scenes...>` times scenes on a dedicated server of this build. Each call uses one server and a
fresh flat world. For each scene it clears the area, builds the scene, waits 40 s for the scene and the JIT to settle,
then queries vanilla's `/tick query` every 5 s for 60 s. The reported number is the mean of the 100-tick windows'
averages. The host runs below used 30 s of warm-up and 30 s of windows. The N100 server numbers come from the same
procedure against a NeoForge server with the built jar, using 40 s of warm-up and 20 s of windows.

Rules the numbers follow (each learned the hard way, see [Lessons](how-it-works.md#lessons)):

- Compare only runs from the same day on the same machine. The same configuration on the same machine moved by
  20–30 % between days.
- Interleave and repeat A/B runs, alternate which build runs first, and warm up for at least 40 s. On the N100 the JIT and the first class analysis
  otherwise land inside the measuring window.
- Wait until the world around the scene is loaded before measuring the client. On the N100 that takes 150 s.
- Never judge spikes from a profiled run: the recording itself raises p99.
- Touch the heap up front (`-XX:+AlwaysPreTouch`, which `bench.py` sets). Otherwise a fresh JVM takes a page fault on
  every new eden page until its first collections. That charges a run that allocates little (the engine) more than
  one that cycles through eden quickly (vanilla). On the host, this made 1 024 hoppers look slower with the engine
  (0.98 against 0.58 ms) until the heap was pre-touched. A long-running server reuses pages it has already touched.

### Hardware

- **N100**: Intel N100 (4 cores / 4 threads, 6 W), 16 GB, Intel UHD Graphics, Windows 11, JDK 21. Server `-Xmx2G`,
  client `-Xmx4G`, window 1280×720.
- **host**: AMD Ryzen 7 5800X (8 cores / 16 threads), 32 GB, NVIDIA RTX 4070 SUPER, Windows 11, Temurin 21.0.12 (Gradle
  toolchain), server `-Xms4G -Xmx4G -XX:+AlwaysPreTouch`. This is a shared desktop and noisier than the N100.
