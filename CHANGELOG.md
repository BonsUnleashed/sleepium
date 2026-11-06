# Changelog

Versions follow [Semantic Versioning](https://semver.org): a patch release fixes, a minor release adds, a major
release changes the API (`dev.lull.api`) or the settings in a way that needs attention. Jars are named
`sleepium-<version>+mc<minecraft version>-<loader>.jar` (before 1.1.0 without the loader).

## 1.1.1 - 2026-11-06

- **A NeoForge server did not start next to some mods** (reported on CurseForge): `VerifyError: Operand stack
  underflow` in `CommonHooks.onEntityIncomingDamage`. Another mod wraps that method's event post (MixinExtras), and
  Sleepium's rewrite of NeoForge's event posts replaced the very instruction the wrap is built around. Sleepium now
  leaves every such method alone: a method another mod's mixin changes keeps NeoForge's own post. Reproduced with a
  stand-in mod (the same crash before, a started server after); without such mods nothing changes. Workaround for
  1.1.0: `-Dlab.opt.EventSiteMixin=false` in the server's `user_jvm_args.txt`.
- Entity Model Features: the hook of the animation cut is optional again - an EMF version without that spot runs
  without the cut instead of not starting.

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
- **Entity Model Features / Fresh Animations.** EMF before 3.3 compiles each model's animation into one method too
  large for the JIT, so it ran in the interpreter (35 % of a frame): Sleepium cuts it into parts the JIT compiles - the
  same instructions, the same results bit for bit (checked on 26 496 runs). EMF's model parts are drawn from video
  memory like vanilla's. 144 Fresh Animations mobs among a test world's 450 other entities, Sleepium before this ->
  with it: EMF 3.2 186 -> 391 fps, EMF 3.3 ~320 -> ~440 (1.20.1); EMF 3.3 ~370 -> ~500 (1.21.1); without Sleepium
  32-46. Entities whose rendering EMF, ETF or Iris / Oculus take part in stay on the render thread: those mods keep the
  entity being drawn in globals.
- The parallel renderer's per-thread renderer sets are built only when an entity goes to the workers: next to mods
  that keep every entity on the render thread (Oculus, EMF) the world no longer pauses when it opens (16 s in the
  Biohazard pack).
- **Crash at start with TerraBlender fixed** (1.0.x, both versions): TerraBlender makes methods Sleepium replaces public,
  and Mixin refused Sleepium's narrower ones. TerraBlender is in many packs (Biomes O' Plenty, Regions Unexplored, ...).
  The whole Biohazard pack (473 mods, Fresh Animations included), a new world: 351-379 -> 571-578 fps (1.20.1).
- A mod whose mixin config lists an empty (`null`) entry no longer hides the mods after it from Sleepium's check of
  other mods' changes (Sleep Tight's does): in a large pack Sleepium then did not step aside for Radium and the game
  did not start. Tested with the whole Biohazard pack (473 mods).
- Burning entities are drawn on the parallel render workers (the flame where vanilla draws it).
- Interpolated animated textures (`interpolate` in .mcmeta) are blended over the images' memory - pixel for pixel as
  vanilla, several times faster (a mod's 512x512 effect texture: 12 % -> 3.8 % of the render thread).
- Translucent blocks are drawn from a kept list of sections (no pass over every visible section).

1.20.1 only - what the port needed beyond the 1.21.1 engine:

- Entity sleep on Forge (entities are capability providers), the collision memo with production (SRG) names, entity
  data read without 1.20.1's lock and from an array.
- On an Intel N100 production server: items 114.9 -> 10.7 ms per tick, mobs 22.2 -> 13.25. A Cursed Fate fight on the
  client: 87 -> 600-646 fps (with Oculus + Embeddium 289 -> 299: the entities stay on the render thread).
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
