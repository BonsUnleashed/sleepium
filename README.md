# sleep

A NeoForge mod for Minecraft 1.21.1 that makes servers and clients cheaper to run **without changing what the game
computes**. A hopper with nothing to do, a resting item stack, a furnace waiting for fuel: in vanilla each of them runs
its full tick 20 times a second only to find out that nothing happened. The engine proves from the class's own bytecode
and from what it observes that a tick only moves clocks, takes the object off the tick ("puts it to sleep") and wakes it
at exactly the tick vanilla would have acted. Around that idea sit an event engine, exact memos and indexes, parallel
world generation, batched chunk I/O, and a renderer that keeps the frames pixel-identical.

Every optimisation is a switch, and each one is checked against vanilla. A scene runs twice from the same seeds, once
with the optimisations off and once with them on. After the same number of ticks the blocks, the block entities and
every field of every entity are hashed. On the client, rendered frames are hashed.

- Minecraft 1.21.1, NeoForge 21.1.251, Java 21.
- License: MIT.
- Status: 0.1.0, tested as described below. Compatibility with other optimisation mods has **not** been tested yet
  (see [Known limits](#known-limits)).

## Results at a glance

Intel N100 (4 cores, 6 W, Intel UHD Graphics, 16 GB): server milliseconds per tick (MSPT) as reported by vanilla's
`/tick query`, lower is better. "vanilla" is the same jar with every optimisation off (`-Dlab.opt.defaults=false`).

| scene | what it stresses | vanilla | sleep | |
|---|---|---:|---:|---:|
| items | 4 000 item stacks that cannot merge | 109.60 | 8.00 | ×13.7 |
| hoppers | 1 024 idle hoppers | 2.78 | 0.73 | ×3.8 |
| carts | items among sliding minecarts on ice | 36.97 | 10.82 | ×3.4 |
| furnaces | 1 024 furnaces | 1.25 | 0.55 | ×2.3 |
| fluids | water and lava flowing on a grid | 10.43 | 5.15 | ×2.0 |
| cramming | 200 cows in one 1×1 column | 13.22 | 6.75 | ×2.0 |
| pistons | 200 observer clocks with sticky pistons | 8.00 | 4.20 | ×1.9 |
| mobs | 300 farm animals + 50 villagers | 18.48 | 10.00 | ×1.8 |
| villagers | 120 villagers, 64 workstations | 16.57 | 10.57 | ×1.6 |
| smelters | 1 024 chest→hopper→furnace→hopper→chest columns | 3.77 | 2.38 | ×1.6 |
| crops | 64×64 farmland, random ticks | 0.75 | 0.53 | ×1.4 |
| redstone | 32 observer clocks → dust → lamps | 12.12 | 9.78 | ×1.2 |

All numbers are from one evening (the release jar on 2026-11-01, vanilla from the same session). Client on the same
machine: an entity / block-entity scene **39 → 117 fps** (frame p50 25.6 → 7.7 ms, garbage
3 952 → 113 KiB per frame). On a desktop (Ryzen 7 5800X, RTX 4070 SUPER): the same scene **67 → 1 514–1 540 fps**,
300 animated mobs 175 → 1 782 fps, items on the server 48.9 → 3.1 ms per tick. Against Horizon, a Rust rewrite of Minecraft, the client draws 13–32 %
more frames on the N100, and the server is ahead on items, hoppers, furnaces and pistons
([Against other projects](#against-other-projects)). All tables are in [Results](#results).

The world after N ticks is the same as vanilla's in every scene where vanilla itself is reproducible (see
[Exactness](#exactness)).

## Install

Put `sleep-<version>.jar` into the `mods` folder of the server, the client, or both. In a built jar the tested set of
optimisations ([`defaults.txt`](defaults.txt)) is on by itself; nothing needs to be configured.

- `-Dlab.opt.<Name>=false` turns one optimisation off, for example `-Dlab.opt.ParallelEntitiesMixin=false`.
- `-Dlab.opt.defaults=false` turns all of them off. The mod then behaves like vanilla, which is how the "vanilla"
  columns in this README were measured.
- Opt-in features are listed under [Opt-in](#opt-in-features).

[GeckoLib](https://github.com/bernie-g/geckolib) is optional. When it is installed, its models are drawn by the engine's
parallel workers and from video memory, and its Molang expressions are compiled.

## Build

```
./gradlew build          # build/libs/sleep-0.1.0.jar
./gradlew runClient      # a dev client with the test scenes
./gradlew runServer      # a dev server; reads commands from standard input
```

The build needs only this folder: GeckoLib comes from its maven as a compile-only dependency. The test scenes live in
their own source set (`src/scenes`). Dev runs load them, the jar does not ship them.

Other mods can include the engine from source, `includeBuild('../sleep')` in `settings.gradle` and then
`compileOnly 'dev.sleep:sleep:0.1.0'`. Only `dev.sleep.api` is meant for them (see [API](#api-for-other-mods)). The
build fails if the engine imports another mod's package (`checkEngineIndependent`).

## How it was tested

### Exactness

`tools/exactness.py <scene>` runs a scene twice on a dedicated server of this build:

1. a fresh flat world (fixed seed), ticks frozen with `/tick freeze`;
2. the scene's chunks force-loaded, the clock stepped to game time 300;
3. vanilla's randoms that are seeded from the clock made reproducible (`-Dsleep.seedEntities`, `/sleep reseed`), so two
   runs draw the same numbers. These are every entity's random generator and UUID, each level's randoms, the
   random-tick generator, the static randoms of `Sensor` and `Mth`, and the randoms of every `ShufflingList` (a brain's
   behaviour order) and `Explosion`;
4. the scene built, then exactly N ticks stepped (`/tick step`, 2 400 by default = two minutes of game time);
5. `/sleep hash` over the scene's area: every block state, every block entity's saved data and every entity: position,
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
- **In-world clone test** (`/sleep equiv`). The same scene is built twice, 2 000 blocks apart, and only one copy may
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

Rules the numbers follow (each learned the hard way, see [Lessons](#lessons)):

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

## Results

### Server, N100 (2026-11-01)

`h2h.py` on a NeoForge 1.21.1 server with the built jar: a fresh flat world per server, 40 s warm-up per scene, then
four 100-tick windows of `/tick query` (20 s); `-Xmx2G`. Values are ms per tick (mean of the windows).

The [table above](#results-at-a-glance) gives vanilla against this release. To check for regressions, the release
was also measured against the engine's last build before it became a standalone mod. Scene groups alternated between
the two jars:

| scene | previous build | this release | memory (RSS), previous → release |
|---|---:|---:|---|
| items | 8.55 | 8.00 | 885 → 755 MB |
| mobs | 10.25 | 10.00 | 919 → 814 MB |
| villagers | 10.10 | 10.57 | 922 → 816 MB |
| hoppers | 0.73 | 0.73 | |
| smelters | 2.67 | 2.38 | |
| furnaces | 0.80 | 0.55 | |
| cramming | 6.85 | 6.75 | |
| redstone | 9.75 | 9.78 | |
| pistons | 4.57 | 4.20 | |
| crops | 0.50 | 0.53 | |
| fluids | 5.20 | 5.15 | |
| carts | 10.90 | 10.82 | |

In every earlier pass of the night villagers came out 3–5 % slower than the previous build, always with the release
run first. Three more pairs with the order reversed came back level: previous build 10.20 / 9.85 / 9.75 (mean 9.93),
release 10.62 / 9.65 / 9.65 (mean 9.97), p50 9.1–9.7 in both. The release uses about 100 MB less memory, because the
animation framework built on the engine is no longer in the same jar.

### Server, N100, other JVM flags (2026-11-01)

Does the result depend on how the JVM is started? The same comparison was run twice more, with scene groups
alternating which side goes first:

- with the whole heap committed and touched up front: `-Xms2G -Xmx2G -XX:+AlwaysPreTouch`;
- with the G1 settings most server hosts use ("Aikar's flags": `-Xms2G -Xmx2G -XX:+AlwaysPreTouch -XX:+UseG1GC
  -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20
  -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1`).

| scene | pre-touched: vanilla | sleep | | Aikar's flags: vanilla | sleep | |
|---|---:|---:|---:|---:|---:|---:|
| items | 143.93 | 8.97 | ×16.0 | 115.12 | 8.95 | ×12.9 |
| hoppers | 3.20 | 0.77 | ×4.2 | 3.00 | 0.70 | ×4.3 |
| carts | 35.67 | 10.33 | ×3.5 | 36.53 | 10.90 | ×3.4 |
| furnaces | 1.77 | 0.70 | ×2.5 | 1.55 | 0.90 | ×1.7 |
| fluids | 9.70 | 5.18 | ×1.9 | 10.27 | 5.38 | ×1.9 |
| cramming | 13.43 | 6.58 | ×2.0 | 13.23 | 6.53 | ×2.0 |
| pistons | 7.47 | 4.30 | ×1.7 | 7.70 | 4.45 | ×1.7 |
| mobs | 18.80 | 10.38 | ×1.8 | 19.18 | 10.05 | ×1.9 |
| villagers | 18.20 | 10.32 | ×1.8 | 17.08 | 10.40 | ×1.6 |
| smelters | 4.40 | 2.93 | ×1.5 | 3.77 | 2.67 | ×1.4 |
| crops | 0.90 | 0.55 | ×1.6 | 0.80 | 0.53 | ×1.5 |
| redstone | 12.02 | 10.00 | ×1.2 | 12.30 | 9.93 | ×1.2 |

The gains hold under both. With the defaults (`-Xmx2G` alone) G1 starts with a small heap and young generation, so a
fresh JVM reuses its touched pages early; the first-touch effect seen on the host with a fixed 4 GB heap does not
apply there.

### Server, host (2026-11-01)

`tools/bench.py`, heap 4 GB pre-touched, 30 s warm-up, then 30 s of `/tick query` windows per scene. Values are ms per
tick, lower is better.

| scene | vanilla (mean / p50 / p99) | sleep (mean / p50 / p99) | |
|---|---:|---:|---:|
| items | 48.93 / 49.00 / 59.10 | 3.08 / 3.00 / 5.50 | ×15.9 |
| carts | 11.35 / 11.30 / 28.50 | 3.12 / 2.90 / 7.00 | ×3.6 |
| hoppers | 0.40 / 0.40 / 0.70 | 0.20 / 0.20 / 0.50 | ×2.0 |
| furnaces | 0.40 / 0.40 / 0.70 | 0.20 / 0.20 / 0.50 | ×2.0 |
| mobs | 4.43 / 4.50 / 16.20 | 2.48 / 2.50 / 11.50 | ×1.8 |
| fluids | 3.00 / 0.80 / 27.20 | 1.75 / 0.50 / 15.80 | ×1.7 |
| cramming | 3.52 / 3.50 / 5.60 | 2.10 / 2.10 / 2.70 | ×1.7 |
| pistons | 4.45 / 4.30 / 12.60 | 2.70 / 2.60 / 9.70 | ×1.6 |
| crops | 0.65 / 0.60 / 2.80 | 0.40 / 0.40 / 1.00 | ×1.6 |
| villagers | 4.72 / 4.40 / 28.40 | 3.38 / 3.10 / 24.40 | ×1.4 |
| smelters | 0.95 / 0.60 / 20.30 | 0.82 / 0.50 / 23.20 | ×1.2 |
| redstone | 3.80 / 0.30 / 26.50 | 3.48 / 0.30 / 16.20 | ×1.1 |

`/tick query` reports in steps of 0.1 ms. Smaller scenes were also repeated in earlier passes the same night. On those
passes, scenes that came out close (furnaces, crops) were re-run twice, interleaved, and the engine was level or
ahead every time (furnaces 0.52–0.55 → 0.30, crops 0.20 → 0.20). The host is a desktop with other load, so single
p99 spikes are not a pattern.

### Client, N100 (2026-11-01)

Self-driving client (`-Dsleep.clientAuto`): fixed seed, vsync off, unlimited fps. The client waits 150 s for the
world to load, builds the scene, then measures 30 s at a fixed position. Window 1280×720.

| scene | | vanilla | previous build | sleep |
|---|---|---:|---:|---:|
| entities: 120 armour stands, 235 item frames, 69 chests, 69 signs, 46 skulls | fps | 39 | 118 / 115 | 117 |
| | frame p50 / p99, ms | 25.6 / 38.1 | 7.8 / 14.8 | 7.7 / 18.3 |
| | garbage per frame | 3 952 KiB | 115–149 KiB | 113 KiB |
| mobs: 300 animated mobs | fps | 76 | 138 | 138 |
| | frame p50 / p99, ms | 11.7 / 21.8 | 6.6 / 16.0 | 5.9 / 17.3 |
| | garbage per frame | 832 KiB | 83 KiB | 82 KiB |
| light: 196 light sources switched every 2 ticks | fps | 144 | 157–161 | 158–160 (release: 159) |
| | frame p50 / p99, ms | 5.8 / 18.1 | 5.9–6.2 / 12.1–13.8 | 5.9–6.1 / 12.1–16.0 |

"Previous build" is the engine's last build before it became a standalone mod. The "sleep" column is the release
jar; ranges come from repeated runs of the same build. Frame rates are level.

One open point is CPU use in the light scene. The previous build used 0.60–0.78 cores (mean 0.69, 5 runs) and this
engine 0.70–0.88 (mean 0.78, 8 runs). In the two pairs run back to back with identical flags the two were level
(0.77 / 0.71 against 0.72 / 0.78). The extra time is in the shared worker pool, which does light and section
meshing. Vanilla uses 0.44 cores here: the engine runs that work on workers in parallel with the frame.

### Client, host (2026-11-01)

`tools/client_bench.py`: the same self-driving client from `./gradlew runClient` of this build, window 1280×720,
vsync off, unlimited fps, heap 3 GB pre-touched, 40 s for the terrain, 30 s measured. NVIDIA RTX 4070 SUPER.

| scene | | vanilla | sleep | |
|---|---|---:|---:|---:|
| entities | fps | 67 | 1 514 / 1 540 | ×23 |
| | frame p50 / p99, ms | 14.7 / 18.3 | 0.58 / 1.54 | |
| | garbage per frame | 3 953 KiB | 68–88 KiB | |
| mobs | fps | 175 | 1 782 | ×10 |
| | frame p50 / p99, ms | 5.5 / 7.8 | 0.50 / 2.76 | |
| | garbage per frame | 841 KiB | 38 KiB | |
| light | fps | 511 | 2 813 | ×5.5 |
| | frame p50 / p99, ms | 1.8 / 8.5 | 0.33 / 0.80 | |
| | garbage per frame | 187 KiB | 24 KiB | |

Without a frame cap the engine uses more cores (mobs 1.9 → 3.8) because it draws ten times as many frames. Per frame
it costs a fraction of vanilla's CPU.

Measured earlier on the host, uncapped (RTX 4070 SUPER), vanilla → sleep: terrain 242–283 → ≈1 840 fps; 225 mobs
167 → ≈1 775 fps; the entity / block-entity scene 64 → ≈690 fps (later 994). At a fixed 60 fps the whole client process
used 0.74 → 0.28 cores.

### World generation

On the host, 43–56 chunks/s in vanilla → 158–160 chunks/s (a second batch, everything warm). Single-threaded work per
chunk drops from 71.9 ms and 19.1 MB of garbage to 35.5 ms. On the N100, 12 → 25.6 chunks/s once warm.

### Against other projects

- **Sodium 0.8.13** (host, same day, render distance 12). Terrain is on par: Sodium 1 697–1 827 fps against
  1 762–1 912. The entity scene is about 10× faster: Sodium 146 fps against 1 500–1 680. On terrain, sleep's process
  used ~0.9 core more, all of it in the NVIDIA driver's own thread; that is not explained yet.
- **Horizon** (a Rust rewrite of Minecraft 26.3, release 0.2.97), on the N100 on 2026-10-30. The versions differ, so
  each is shown next to its own vanilla. Values are ms per tick, two interleaved passes.

  | scene | vanilla 1.21.1 | sleep | vanilla 26.3 | Horizon |
  |---|---|---|---|---|
  | items | 127 / 130 | **8.5 / 8.5** | 97 / 83 | 14.7 / 13.7 |
  | hoppers | 3.2 / 2.5 | **0.73 / 0.70** | 1.2 / 1.4 | 2.7 / 2.7 |
  | smelters | 3.7 / 3.9 | 2.9 / 2.4 | **2.3 / 2.7** | 11.4 / 11.5 |
  | furnaces | 1.4 / 1.4 | **0.78 / 0.75** | 0.85 / 1.5 | 0.97 / 1.0 |
  | pistons | 7.4 / 7.3 | **4.1 / 4.5** | 6.1 / 6.0 | 6.2 / 6.1 |
  | mobs | 18.9 / 17.9 | 10.1 / 10.0 | 15.0 / 14.3 | **8.2 / 8.1** |
  | villagers | 17.8 / 17.0 | 12.2 / 12.0 | 12.7 / 12.7 | **5.6 / 5.1** |
  | cramming | 14.0 / 12.9 | 6.5 / 6.5 | 9.3 / 9.8 | **4.7 / 4.7** |
  | redstone | 12.2 / 12.0 | 10.1 / 10.0 | 10.8 / 10.7 | **8.4 / 8.4** |
  | carts | 37.6 / 33.0 | 10.9 / 11.0 | 31.0 / 31.0 | **8.1 / 8.0** |
  | fluids | 9.9 / 10.2 | 5.0 / 5.0 | 6.1 / 6.2 | **0.47 / 0.47** |
  | crops | 0.8 / 0.8 | 0.5 / 0.5 | 0.6 / 0.6 | 0.5 / 0.5 |
  | memory (RSS) | 0.7–0.9 GB | 0.7–0.9 GB | 0.6–0.8 GB | **0.37–0.45 GB** |

  **Client: sleep draws more frames than Horizon in every scene**, on the same N100, the same view and 1280×720 (the
  same session, 2026-10-30):

  | client scene | vanilla 1.21.1 | sleep | Horizon | sleep against Horizon |
  |---|---:|---:|---:|---:|
  | entities | 37 fps | **115 fps** | 87 fps | +32 % |
  | mobs | 70 fps | **136 fps** | 112 fps | +21 % |
  | light | 132 fps | **156 fps** | 138 fps | +13 % |

  Horizon draws with Vulkan (wgpu) and keeps frame-time p99 lower (10–14 ms against 17–33 ms); sleep has the lower
  p50. Horizon is ahead on mobs and
  villagers partly because of its data layout and partly because some of its shortcuts are not vanilla-exact. sleep
  keeps the vanilla game and its mods.
- **Lithium, C2ME, Moonrise, ModernFix, FerriteCore**: no A/B has been run yet.

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
are refused, each for a reason that `/sleep sleep` lists.

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
vanilla-exact. sleep keeps vanilla's algorithm and removes its garbage. The neighbour-update queue becomes
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
| per-vertex light "recipes" and per-section light volumes: a light-only change refreshes the volume instead of re-meshing | light switching re-meshed whole sections | compile work −42 % |
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

GeckoLib shares one bone set per model. sleep gives each render worker its own copy, so GeckoLib entities render in
parallel. Bones are drawn through the GPU-posed part path, and Molang expressions are compiled to bytecode with
GeckoLib's exact operators: 0 mismatches over 59 895 test cases. Synthetic scene with 140 animated mobs and 48
animated block entities: 131–148 → 1 363–1 475 fps.

### Measurement tools

- `/sleep run <seconds> <label>`: MSPT mean, p50 and p99; allocation per tick; CPU by thread group; GC and JIT time.
- `/sleep events`: a per-tick census of bus posts, neighbour updates, scheduled ticks and block-entity / entity ticks.
- `/sleep sleep`, `/sleep sleepents`: which classes sleep and why the others do not.
- `/sleep hash`, `/sleep equiv`: region and entity hashes, the in-world clone comparison.
- `/sleep genbench`: chunks/s, time and allocation per generation step, the CARVERS hash.
- `/sleep bench`, `/sleep busbench`, `/sleep threads`, `/sleep net`, `/sleepclient` (client frame statistics).
- Test scenes: `/function sleep:lag_<scene>` (server) and `sleep:client_<scene>` (client), in `src/scenes`.
- `tools/exactness.py`, `tools/exactness_all.sh`, `tools/bench.py`, `tools/server.py`: the procedures above.

## Opt-in features

| flag | what | why it is not on by default |
|---|---|---|
| `-Dlab.sleep.components=true -Dlab.sleep.mobRuntime=true -Dlab.opt.WriteBarrier=true` | mob sleep | modest gain, villagers slower, ~300 MB more memory after class analysis |
| `-Dlab.opt.JobSiteIndex=true` | villagers skip the job-site competitor scan when nobody else holds the site | small (−4 to −10 % on villagers), tested on few scenes |
| `-Dlab.opt.MoveMemo=true` | an unchanged `Entity.move` is skipped | small (−6 % on mobs) |
| `-Dlab.opt.WorldgenReactor=true` | finished chunks admitted to the main thread against the tick deadline | needed three fixes in singleplayer and was tested on few scenes |
| `-Dlab.opt.HeapReturn=true` | returns unused heap to the OS at run time | was on by default for one day: on a 1.2 GB live heap G1 thrashed (315 full collections in 2.5 min) |

## Settings screen

Options → **sleep...** (or the mod list's Config button) opens the engine's settings: features (groups of tested
flags, switched together), shaderpacks, and every flag one by one with search. Choices are saved to
`config/sleep.properties`; a `-D` flag on the launch line still wins. Most flags apply after a restart; the screen says
which.

## Shaderpacks (beta)

sleep runs OptiFine / Iris-format shaderpacks itself, inside its own renderer (no Iris or Sodium needed): put a pack
(.zip or folder) into `shaderpacks/` and pick it on the settings screen's Shaders tab; it switches without a restart.
Tested with Complementary Reimagined r5.9.3 (shadows, deferred lighting, composites). This is a **beta**: packs that
need features not implemented yet (compute passes, custom images, some uniforms) may render wrong. With Iris, Oculus or
OptiFine installed, sleep leaves shaders to them.

## Known limits

- **Other optimisation mods.** Compatibility with Sodium/Embeddium/Iris, Lithium, C2ME, Moonrise, ModernFix and
  FerriteCore is untested. Many mixins overlap with them. Mixins are declared `require = 1`, so a conflict fails at
  load instead of silently. The sleep engines refuse classes that another mod's mixin has changed; a stand-in foreign
  mixin ships as a test (`-Dsleep.foreignTest`).
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

## API for other mods

`dev.sleep.api`:

- `SleepApi`: is a feature on; wake the server thread.
- `Pools`: the shared background and IO pools.
- `CompletionRing`: hands results from any thread to an owner, in order, without blocking.
- `SideEffectJournal`: runs work off the owner thread and replays its shared-state calls in order.
- `AdmissionWindow`: budgets heavy work against the tick deadline.
- `NetContent`: bulk content to clients.
- `Travel`: teleports once the destination's chunks are loaded.
- `Compat`: per-type opt-outs from parallel rendering, the render cache, block-entity sleep and entity sleep. Players
  can set the same opt-outs without code: `-Dsleep.compat.noParallelRender=`, `noRenderCache`, `noSleep` and
  `noEntitySleep` take comma-separated ids, for example `-Dsleep.compat.noRenderCache=mymod:clock`.

Every API call is safe with any combination of flags.

## License

MIT, see [LICENSE](LICENSE).
