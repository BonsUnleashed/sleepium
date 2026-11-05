# Changelog

Versions follow [Semantic Versioning](https://semver.org): a patch release fixes, a minor release adds, a major
release changes the API (`dev.lull.api`) or the settings in a way that needs attention. Jars are named
`sleepium-<version>+mc<minecraft version>-<loader>.jar` (before 1.1.0 without the loader).

## 1.1.0 - 2026-11-06

**Minecraft 1.20.1 / Forge is supported** (`sleepium-1.1.0+mc1.20.1-forge.jar`, Forge 47.4.9 or newer, Java 17 or
newer), next to 1.21.1 / NeoForge (`sleepium-1.1.0+mc1.21.1-neoforge.jar`). Jars now name the loader too.

Both versions:

- **Next to other optimisation mods, the gains are no longer lost.** Tested one mod at a time and as a pack:
  - Entity sleep works next to mods that change `Entity` (Lithium, Radium, Canary - most packs): before, no item ever
    slept with them. The analyses now read what other mods' mixins change as it runs. Items with Lithium 4.32 -> 3.37
    ms per tick; with Radium on 1.20.1 3.92 ms against Radium's own 5.58. Exact (world hashes).
  - ModernFix no longer turns off the collision shape cache and with it the collision memo (mobs with ModernFix 4.13 ->
    3.17 ms per tick on 1.20.1).
  - Iris / Oculus (without a shaderpack), Embeddium and ImmediatelyFast no longer keep the render cache on its slow
    path: client_entities with Embeddium 137 -> 845 fps, Oculus 238 -> 440-464, ImmediatelyFast 371 -> 681 (1.20.1);
    Iris + Sodium 377 -> 570, ImmediatelyFast 809 -> 1 149 (1.21.1). Frames as before, bit for bit.
  - Iris / Oculus no longer keep every entity on the render thread: entities are drawn on workers and merged inside the
    dispatcher, where those mods' state is set (a horde of burning zombies with Oculus + Embeddium 559 -> 718 fps,
    with Iris + Sodium 636 -> 805).
- Burning entities are drawn on the parallel render workers (the flame where vanilla draws it).
- Interpolated animated textures (`interpolate` in .mcmeta) are blended over the images' memory - pixel for pixel as
  vanilla, several times faster (a mod's 512x512 effect texture: 12 % -> 3.8 % of the render thread).
- Translucent blocks are drawn from a kept list of sections (no pass over every visible section).

1.20.1 only - what the port needed beyond the 1.21.1 engine:

- Entity sleep on Forge (entities are capability providers), the collision memo with production (SRG) names, entity
  data read without 1.20.1's lock and from an array.
- On an Intel N100 production server: items 114.9 -> 10.7 ms per tick, mobs 22.2 -> 13.25. A Cursed Fate fight on the
  client: 87 -> 600-646 fps; with Oculus + Embeddium 289 -> 408-423.
- Not on 1.20.1: the adapters for Veil / Sable / Create Aeronautics (those mods are 1.21.1 only).

The repository now keeps a branch per Minecraft version and loader; `main` is the front page with the documentation
for all versions (`docs/`).

## 1.0.3+mc1.21.1 - 2026-11-03

- Compatibility with other optimisation mods. Tested together with 22 of them and a 13-mod pack (Sodium, Iris,
  Embeddium, Lithium, Moonrise, C2ME, ModernFix, FerriteCore, ImmediatelyFast, EntityCulling, ScalableLux, Radium and
  more). Five crashed or broke with 1.0.2 and run now: Embeddium (its mixins come from a plugin, not its config list),
  ModernFix, Lithium, Moonrise and C2ME (a mixin of ours that gave way left out an interface other mixins of ours
  used; a method another mod adds too; C2ME's random). Where another mod changes the same code, Sleepium steps aside
  there and says so in the log. With Lithium or Moonrise installed, Sleepium's changes are still exact (world hashes).
- Water, glass and ice are drawn in one multi-draw, in vanilla's order: the terrain view was bound by the graphics
  driver's work for vanilla's per-section translucent draws. A view over a river: 1227-1383 -> 2635-2833 fps (RTX 4070
  SUPER); on an Intel N100 the client's CPU use dropped from 1.67 to 0.52 cores. Frames unchanged (frame hashes).
  Shaderpacks are unchanged (Complementary Reimagined: 604-638 fps on 1.0.2, 607-640 on 1.0.3).
- Water takes light from the light volumes like the other blocks: a light change no longer rebuilds every section
  with water (CPU 3.6 -> 2.6 cores on a scene of lights switching over water).
- Item frames, chests and signs drawn from video memory (no per-frame copy); layers drawn again from kept lists keep
  their draw data in place.
- Players without Sleepium can join a server with it (tested).

## 1.0.2+mc1.21.1 - 2026-11-02

- A new settings screen: a sidebar of pages (Overview, the feature groups, Shaders, All flags) and a panel describing
  what the pointer is on. The Overview shows the engine at work: FPS and frame times, the singleplayer server's
  MSPT / TPS, block entities asleep and ticks saved per second, the shaderpack's state; presets (Maximum, Default,
  Compatible, Off), the feature groups' switches, live stats in the game corner, and a report to copy for bug
  reports. Also opened from the pause menu.
- Shaderpack options: each pack's own settings screen (its screens, sliders, profiles and labels, as Iris shows
  them), saved to shaderpacks/<pack>.txt. Applying prepares the new settings beside the pack in use and swaps them in
  one frame: no vanilla frames meanwhile, and the world's meshes are kept when the pack's block ids stay the same.
- Diagnostics for the render thread (-Dlab.rcache.time, -Dlab.prender.why), off by default.

## 1.0.1+mc1.21.1 - 2026-11-02

- Shaderpacks: a pack is now prepared over frames and switched on when ready, with the progress in the corner; the game
  draws vanilla meanwhile instead of stopping. A driver's first compile of a pack is long (Intel UHD, Complementary:
  57 s frozen before); a pack chosen beforehand is prepared on the title screen.
- Settings screen: flags a plugin switches by another flag are shown as "part of" it (their own buttons did nothing);
  a flag switched off stays off even when the universal set is on; every flag has a description; the network lag
  test tool is no longer listed.
- The mod list shows Sleepium's logo, its page and issue tracker, and tells of new versions (Modrinth).

## 1.0.0+mc1.21.1 - 2026-11-02

The first public release.

- Server: sleeping block entities and entities (idle ticks skipped, woken at the exact tick vanilla acts), the event
  bus with compiled listener lists and allocation-free empty posts, exact memos and indexes (collisions, shapes,
  entity sections, neighbour updates, scheduled ticks), parallel world generation, chunk saving and loading off the
  main thread, batched network sends.
- Client: terrain in indirect multi-draws with packed vertices, entities batched and posed on the GPU, a render cache
  for unchanged objects, client light off the render thread, GeckoLib models with parallel bones and compiled Molang.
- Shaderpacks (beta): OptiFine / Iris-format packs run by Sleepium's own renderer, switched without a restart.
- A settings screen (Options → Sleepium...): features by group, the shaderpack, every flag with search; saved to
  `config/sleepium.properties`.
- Every default optimisation checked against vanilla by world and frame hashes (see README, Exactness).
- License: LGPL-3.0-only.
