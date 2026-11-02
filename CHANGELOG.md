# Changelog

Versions follow [Semantic Versioning](https://semver.org): a patch release fixes, a minor release adds, a major
release changes the API (`dev.lull.api`) or the settings in a way that needs attention. Jars are named
`sleepium-<version>+mc<minecraft version>.jar`.

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
