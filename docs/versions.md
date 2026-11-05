# Versions

## Supported versions

| Minecraft | Loader | Role | Branch | Java |
|---|---|---|---|---|
| 1.21.1 | NeoForge 21.1 | main line: new work lands here first | `1.21.1-neoforge` | 21 or newer |
| 1.20.1 | Forge 47 | long-term: every fix, features when the port is cheap | `1.20.1-forge` | 17 or newer |

At most two or three versions are supported at a time. A version enters when big packs move to it, and leaves (an
`eol/<minecraft>-<loader>` branch with a tag of its last release) when they have moved on.

## How the repository keeps them

- A **branch per Minecraft version and loader**, each a complete mod: its own build, Java level and tests. There is no
  shared core and no preprocessor - what a branch holds is exactly what runs.
- The same change goes to another version with `git cherry-pick -x`; a change written differently for a version says
  `Port-of: <commit>`; one meant for one version only says `Only: <branch>`. `tools/notported.py` (on `main`) lists what
  one supported version has and another does not yet.
- Branch states by name: `<minecraft>-<loader>` supported, `paused/...` started but not kept up now, `eol/...` frozen.
  The versions board on the front page is generated from the branches (`tools/board.py`).

## Fixed names

- Jars: `sleepium-<version>+mc<minecraft>-<loader>.jar` (from 1.1.0; earlier `sleepium-<version>+mc1.21.1.jar`).
- Tags: one `v<version>` per release, with every supported version's jar.
- `update.json` on `main` (Forge and NeoForge read it for the mod list's update notice): promotions per Minecraft
  version.

## What differs on 1.20.1 / Forge

- **Member names in production are SRG** (`m_12345_`, `f_12345_`); class names are Mojang's. The bytecode analyses read
  classes with Mojang's member names (`ClassBytes` maps them back), and every name looked up at run time goes through
  `Names`. A production server and client are part of every check: dev runs cannot see this.
- **Entities are Forge capability providers.** Entity sleep assumes no capability is attached to the entity (checked at
  run time: an entity a mod attached one to does not sleep) and handles the provider's own bookkeeping.
- **Synched entity data takes a read lock** on every read in 1.20.1 and keeps its items in a hash map; Sleepium reads
  without the lock (the map only changes while the entity is built) and from an array by id.
- **The event bus is Forge's eventbus 6**, the renderer is the old buffer API (`BufferBuilder.begin`), networking has no
  payload codecs: each has its own form in the branch.
- **Spawn chunks:** 23 x 23 always loaded and walked (vanilla 1.20.1).
- Not on 1.20.1: the adapters for Veil / Sable / Create Aeronautics (those mods are 1.21.1 only).
