# Settings

Everything is on by itself in a built jar; these are the switches for when you want otherwise.

Put `sleepium-<version>.jar` into the `mods` folder of the server, the client, or both. In a built jar the tested set of
optimisations ([`defaults.txt`](https://github.com/ha7gawa/sleepium/blob/1.21.1-neoforge/defaults.txt)) is on by itself; nothing needs to be configured.

- `-Dlab.opt.<Name>=false` turns one optimisation off, for example `-Dlab.opt.ParallelEntitiesMixin=false`.
- `-Dlab.opt.defaults=false` turns all of them off. The mod then behaves like vanilla, which is how the "vanilla"
  columns in these pages were measured.
- Opt-in features are listed under [Opt-in](#opt-in-features).

[GeckoLib](https://github.com/bernie-g/geckolib) is optional. When it is installed, its models are drawn by the engine's
parallel workers and from video memory, and its Molang expressions are compiled.

## Opt-in features

| flag | what | why it is not on by default |
|---|---|---|
| `-Dlab.sleep.components=true -Dlab.sleep.mobRuntime=true -Dlab.opt.WriteBarrier=true` | mob sleep | modest gain, villagers slower, ~300 MB more memory after class analysis |
| `-Dlab.opt.JobSiteIndex=true` | villagers skip the job-site competitor scan when nobody else holds the site | small (−4 to −10 % on villagers), tested on few scenes |
| `-Dlab.opt.MoveMemo=true` | an unchanged `Entity.move` is skipped | small (−6 % on mobs) |
| `-Dlab.opt.WorldgenReactor=true` | finished chunks admitted to the main thread against the tick deadline | needed three fixes in singleplayer and was tested on few scenes |
| `-Dlab.opt.HeapReturn=true` | returns unused heap to the OS at run time | was on by default for one day: on a 1.2 GB live heap G1 thrashed (315 full collections in 2.5 min) |

## Settings screen

Options → **Sleepium...** (or the mod list's Config button) opens the engine's settings: features (groups of tested
flags, switched together), shaderpacks, and every flag one by one with search. Choices are saved to
`config/sleepium.properties`; a `-D` flag on the launch line still wins. Most flags apply after a restart; the screen says
which.

## Shaderpacks (beta)

Sleepium runs OptiFine / Iris-format shaderpacks itself, inside its own renderer (no Iris or Sodium needed): put a pack
(.zip or folder) into `shaderpacks/` and pick it on the settings screen's Shaders tab; it switches without a restart.
Tested with Complementary Reimagined r5.9.3 (shadows, deferred lighting, composites). This is a **beta**: packs that
need features not implemented yet (compute passes, custom images, some uniforms) may render wrong. With Iris, Oculus or
OptiFine installed, Sleepium leaves shaders to them.
