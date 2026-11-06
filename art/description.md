# Sleepium

**A performance engine for Minecraft 1.21.1 (NeoForge) and 1.20.1 (Forge) that makes servers and clients cheaper to run without changing what the game computes.**

A hopper with nothing to do, a resting item stack, a furnace waiting for fuel: in vanilla each of them runs its full
tick 20 times a second only to find out that nothing happened. Sleepium proves from the class's own bytecode and from
what it observes that a tick only moves clocks, takes the object off the tick ("puts it to sleep") and wakes it at
exactly the tick vanilla would have acted. Around that idea sit an event engine, exact memos and indexes, parallel
world generation, batched chunk I/O, and a renderer that keeps frames pixel-identical.

Every optimisation is checked against vanilla: a scene runs twice from the same seeds, once with the optimisations off
and once on, and after the same number of ticks every block, block entity and every field of every entity is hashed.
On the client, rendered frames are hashed.

## Numbers

Intel N100 (4 cores, 6 W, Intel UHD Graphics), server milliseconds per tick, lower is better:

| scene | vanilla | Sleepium | |
|---|---:|---:|---:|
| 4 000 item stacks | 109.6 | 8.0 | ×13.7 |
| 1 024 idle hoppers | 2.78 | 0.73 | ×3.8 |
| items among sliding minecarts | 36.97 | 10.82 | ×3.4 |
| 1 024 furnaces | 1.25 | 0.55 | ×2.3 |
| water and lava flowing | 10.43 | 5.15 | ×2.0 |
| 300 farm animals + 50 villagers | 18.48 | 10.00 | ×1.8 |

Client, entity / block-entity scene (armour stands, item frames, chests, signs, skulls):

- Intel N100: **39 → 117 fps**, garbage 3 952 → 113 KiB per frame
- Ryzen 7 5800X + RTX 4070 SUPER: **67 → 1 514 fps**; 300 animated mobs 175 → 1 782 fps

## New in 1.1.0: Minecraft 1.20.1 / Forge

The whole engine on Forge 47 (Java 17 or newer): sleeping block entities and entities, the event engine, exact memos,
parallel world generation, the renderer, GeckoLib and Create support, the settings screen, shaderpacks.

Intel N100, production server, milliseconds per tick:

| scene | vanilla | Sleepium |
|---|---:|---:|
| 4 000 item stacks | 114.9 | 10.7 |
| 300 animals + 50 villagers | 22.2 | 13.25 |
| 120 villagers | 19.3 | 14.0 |
| 1 024 idle hoppers | 6.68 | 2.4 |

The fights from a player's Cursed Fate videos (840 entities, zombies burning at noon, black holes and supernovas):
**87 -> 600-646 fps**; with Oculus + Embeddium installed 289 -> 299 fps (next to Oculus the entities stay on the render thread).

## New in 1.1.0: the gains kept next to other optimisation mods

Tested one mod at a time and as a pack (Biohazard: Project Genesis' optimisation set):

| next to | before 1.1.0 | 1.1.0 |
|---|---:|---:|
| Embeddium (1.20.1), client fps | 137 | 845 |
| Oculus + Embeddium (1.20.1), client fps | 238 | 440-464 |
| ImmediatelyFast (1.20.1), client fps | 371 | 681 |
| Iris + Sodium, no pack (1.21.1), client fps | 377 | 570 |
| ImmediatelyFast (1.21.1), client fps | 809 | 1 149 |
| Lithium (1.21.1), 4 000 items, ms per tick | 4.32 | 3.37 |

In packs, without Sleepium and with 1.1.0 (production clients):

| | without | 1.1.0 |
|---|---:|---:|
| Biohazard, 473 mods (1.20.1), a new world, fps | 351-379 | 571-578 |
| 144 Fresh Animations mobs + 450 other entities, EMF 3.2 (1.20.1), fps | 32-33 | 391-392 |
| 144 Fresh Animations mobs + 450 other entities, EMF 3.3 (1.21.1), fps | 42-46 | 490-506 |

Entity Model Features: its animations now run compiled (EMF before 3.3 left them to the interpreter) and its model
parts are drawn from video memory - on the scene above with EMF 3.2, Sleepium 1.0 186 fps, 1.1.0 391.

Entity sleep now works next to mods that change entities (Lithium, Radium, Canary); ModernFix no longer turns off the
collision memo. Fixed: a crash at start with TerraBlender (Biomes O' Plenty, Regions Unexplored and many packs) in
1.0.x. Exact: world and frame hashes as before.

## Water and glass in one draw (1.0.3)

A view over a river on the client (RTX 4070 SUPER): **1 227-1 383 fps on 1.0.2, 2 635-2 833 on 1.0.3** (Sleepium 1.0.2 with Sodium: 1 718). Water, glass and ice are drawn in one multi-draw in vanilla's order; frames unchanged. On an Intel N100 the client's CPU use dropped from 1.67 to 0.52 cores.

## With Create

Client on real Create worlds (RTX 4070 SUPER, 1280×720, Flywheel on, default settings; vanilla = the same mods without Sleepium):

| world | vanilla | Sleepium |
|---|---:|---:|
| Alpha Industries (12 880 Create block entities) | 48 fps | 176–207 fps |
| Create Aeronautics: SS.Venator placed | 108 fps | 283–297 fps |
| Create Aeronautics: SS.Venator assembled (Sable) | 101 fps | 254 fps |

Experimental, off by default: Create's kinetic parts drawn by Sleepium instead of Flywheel - 2 181 fps against Flywheel's 1 523 on a scene of driven lines and belts.

## Against other optimisation mods

Server, Intel N100, each mod alone, speed-up over vanilla (vanilla MSPT / the mod's MSPT):

| scene | Lithium 0.15.4 | Moonrise beta.15 | Sleepium |
|---|---:|---:|---:|
| 4 000 item stacks | ×6.8 | ×10.9 | **×13.6** |
| 1 024 idle hoppers | ×3.2 | ×1.0 | **×3.7** |
| 200 cows crammed | ×1.3 | ×1.5 | **×2.1** |
| 300 animals + 50 villagers | ×1.4 | ×1.3 | **×1.9** |
| 120 villagers | ×1.4 | ×0.9 | **×1.5** |
| minecarts | ×3.1 | **×4.2** | ×3.6 |
| redstone clocks | **×1.5** | ×1.4 | ×1.2 |

Sleepium leads in six of twelve scenes and is level in one; Moonrise is ahead on carts, pistons and crops, Lithium on fluids and redstone. 

Client against Sodium (RTX 4070 SUPER, 1280×720):

| scene | Sodium | Sleepium |
|---|---:|---:|
| terrain, no shaders | 1 697–1 827 fps | 1 762–1 912 fps |
| entity scene, no shaders | 146 fps | 1 500–1 680 fps |
| 538 entities, Complementary + shadows (Iris + Sodium) | 68–73 fps | 484 fps |
| terrain view, Complementary (Iris + Sodium) | 477–515 fps | 452–475 fps |

Running Sleepium together with them works since 1.0.3 (tested with 22 optimisation mods and a 13-mod pack); see below.

Against **Horizon**, a Rust rewrite of Minecraft (0.2.97, on 26.3 - the versions differ), on the same Intel N100: the client draws more frames in every scene (entities 115 vs 87 fps, mobs 136 vs 112, light 156 vs 138: +13-32 %); on the server Sleepium is ahead on items, hoppers, smelters, furnaces and pistons, Horizon on mobs, villagers, cramming, redstone, carts and fluids, partly through shortcuts that are not vanilla-exact. Sleepium keeps the vanilla game and its mods.

## What it does

- **Sleeping block entities and entities**: idle ticks skipped and woken at the exact tick vanilla would act.
- **Event bus**: each listener list compiled into its own class, and events nobody listens to never allocated (50 listeners: 542 → 30–38 ns per post).
- **Exact memos and indexes**: collisions, shapes, entity sections, neighbour updates, scheduled ticks.
- **World generation and chunk I/O**: parallel generation steps, shared noise, chunk saving and loading off the main thread.
- **Renderer**: terrain in a few indirect multi-draws with packed vertices, entities batched and posed on the GPU, a
  render cache for unchanged objects; frames hash-identical to vanilla.
- **GeckoLib models**: parallel bone work and compiled Molang.

## Shaderpacks (beta)

Sleepium runs OptiFine / Iris-format shaderpacks itself, inside its own renderer, with no Iris or Sodium needed. Put a
pack (.zip or folder) into `shaderpacks/` and pick it on the settings screen's Shaders tab; it switches without a
restart. Tested with Complementary Reimagined r5.9.3 (shadows, deferred lighting, composites). Packs that need
features not implemented yet may render wrong. With Iris, Oculus or OptiFine installed, Sleepium leaves shaders to them.

## Settings

Options → **Sleepium...** (or the mod list's Config button): features switched by group, the shaderpack, and every
engine flag one by one with search. Saved to `config/sleepium.properties`.

## Installation and compatibility

- Minecraft 1.21.1 with NeoForge 21.1.251 or later (Java 21 or newer), or Minecraft 1.20.1 with Forge 47.4.9 or later
  (Java 17 or newer). The renderer needs OpenGL 4.3 (4.6 for multi-draw).
- Works on the server, on the client, or both: a server with Sleepium accepts clients without it, and the other way round.
- Tested with Create 6.0.10, Create Aeronautics (Sable, Veil) and GeckoLib 4.9.3.
- Tested together with Sodium, Iris (also with shaderpacks), Embeddium, Lithium, Moonrise, C2ME, ModernFix, FerriteCore, ImmediatelyFast, EntityCulling, ScalableLux, Radium, BadOptimizations, Dynamic FPS, Chloride, Noisium, spark, FastSuite, Ksyxis, ServerCore, Clumps and Create, and 13 of them at once. With Lithium or Moonrise installed Sleepium's changes stay exact.
- On 1.20.1 tested with the optimisation set of Biohazard: Project Genesis (Embeddium, Oculus, ImmediatelyFast, EntityCulling, ModernFix, FerriteCore, Radium, Canary, dynamic lights, Create), each alone and all together, and with the Cursed Fate set (GeckoLib, Photon).
- Where another mod overwrites or redirects the same method, Sleepium's change to it steps aside and vanilla's code runs with the other mod's change.

## Source and license

Source, measurements and the full write-up: https://github.com/ha7gawa/sleepium

LGPL-3.0-only. Modpacks may include it; other mods, open or not, may depend on it.
