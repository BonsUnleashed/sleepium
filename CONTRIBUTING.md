# Contributing

Thanks for looking. The short version: **send a pull request to the branch of the version you use**
(`1.21.1-neoforge`, `1.20.1-forge`, ...). You do not need to touch the other versions - the maintainers bring the
change to them.

## Where things are

| You want to... | Look in |
|---|---|
| fix or improve something for one Minecraft version | that version's branch, e.g. `1.20.1-forge` |
| read how a feature works | [`docs/how-it-works.md`](docs/how-it-works.md), then the code in your version's branch |
| find an old version | the [versions table](README.md#versions) - unsupported versions are `eol/...` branches |
| report a bug | an issue: Minecraft version, loader, Sleepium version, the other mods, and the log |

Each version's branch is a complete Gradle project. Build and run it like any mod:

```
git checkout 1.20.1-forge
./gradlew build          # the jar in build/libs
./gradlew runClient      # a development client
```

Working on several versions at once? `git worktree add ../1.21.1-neoforge 1.21.1-neoforge` gives each branch its own
folder, side by side.

## The one rule: change nothing the game computes

Every change must leave the game's results as vanilla's: the same blocks, entities and items after the same ticks,
the same frame. A faster version that is "almost the same" is a bug. So a pull request that changes behaviour comes
with its check:

- **Server side:** the branch's exactness tool on the scenes your change touches - it must print `EXACT`:
  ```
  python tools/exactness.py hoppers
  ```
- **Rendering:** the frame hash of the affected scene, with the optimisation off and on (`-Dsleep.clientAuto.framehash=true`).
- **Speed:** if the change is about speed, the measurement and the machine you used.

[docs/testing.md](docs/testing.md) explains the tools and the scenes.

## How versions are kept in step

- Supported versions: one **main line** (new features land there first) and **long-term** ones (fixes always, features
  when the port is cheap). At most two or three at a time.
- Branches are full copies, not a shared core. The same change goes to another branch with `git cherry-pick -x` (the
  message then says where it came from); a change written differently for a version says `Port-of: <commit>`; a change
  meant for one version only says `Only: <branch>`. If two versions differ somewhere, a comment there says why.
- A version that is no longer supported becomes an `eol/<minecraft>-<loader>` branch with a tag of its last release.

## Java

Each version needs at least the Java its Minecraft needs (17 for 1.20.1, 21 for 1.21.1) and runs on any newer Java,
from any vendor. Please do not use internal JDK APIs without a fallback that works when they are missing.

## Style

Match the code around your change: its naming, its comments, its idioms. Comments explain *why* - what was measured,
what breaks without it - more than *what*.

## License

By contributing you agree that your contribution is licensed under LGPL-3.0-only, as the rest of the project.
