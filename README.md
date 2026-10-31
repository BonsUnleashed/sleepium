# sleep

A NeoForge mod for Minecraft 1.21.1 (NeoForge 21.1.251, Java 21) that makes servers and clients cheaper to run
without changing what the game computes. Optimisations are checked against vanilla by hashing the world and its
entities after the same number of ticks.

## What it does

- **Sleep engines.**
  - Idle block entities (furnaces, hoppers) skip their ticks until an event wakes them.
  - Idle mobs (opt-in) replay their ticks exactly, on a deterministic random generator. A write barrier tells which
    fields changed.
- **Event bus.** A composite dispatcher and per-site event posting, so an event nobody listens to costs almost nothing.
- **World generation and chunks.** Parallel world generation, chunk I/O batched and encoded off the main thread,
  shared and skipped generator work.
- **Rendering (client).**
  - multi-draw chunks, parallel entity rendering, render caches, shader lighting;
  - for GeckoLib content (only when GeckoLib is installed): faster Molang and bones drawn from VRAM.
- **Networking.** Coalesced packets and bulk transfers.
- **Measurement.** `/probe` commands (tick timing, event counts, entity hashes) and lag scenes (`/function sleep:lag_*`).

Every optimisation is a flag (`-Dlab.opt.<Name>=true`). The set the engine is tested with is in `defaults.txt`; a
built jar turns it on unless `-Dlab.opt.defaults=false` is given.

## Build

```
./gradlew build          # build/libs/sleep-<version>.jar
```

GeckoLib (`geckolib-neoforge-1.21.1-4.9.3.jar`) is expected next to this folder for the GeckoLib mixins (compile only).

## Using it from another mod

Include the build from source: `includeBuild('../sleep')` in `settings.gradle`, then `compileOnly 'dev.sleep:sleep'`.
Only `dev.sleep.api` is meant for other mods; everything else is internal.
