# Sleepium for Minecraft 1.21.1 / NeoForge

This branch is the **main line**: new work lands here first.

- Minecraft 1.21.1, NeoForge 21.1.251 or newer, Java 21 or newer.
- GeckoLib is optional (compile-only from its maven); the adapters for Create, Flywheel, Veil, Sable and Create
  Aeronautics apply only when those mods are installed.

Sleepium is a performance engine for modded Minecraft that changes nothing the game computes; the documentation for
all versions is on the front page: [https://github.com/ha7gawa/sleepium](https://github.com/ha7gawa/sleepium) ([how it works](https://github.com/ha7gawa/sleepium/blob/main/docs/how-it-works.md),
[testing](https://github.com/ha7gawa/sleepium/blob/main/docs/testing.md), [compatibility](https://github.com/ha7gawa/sleepium/blob/main/docs/compatibility.md),
[versions](https://github.com/ha7gawa/sleepium/blob/main/docs/versions.md)).

## Build

```
./gradlew build          # the jar in build/libs
./gradlew runClient      # a dev client with the test scenes
./gradlew runServer      # a dev server; reads commands from standard input
python tools/exactness.py hoppers    # a scene with the optimisations off and on: EXACT when the world is the same
python tools/suite.py                # every scene, three servers at once
```

The test scenes live in their own source set (`src/scenes`): dev runs load them, the jar does not ship them.

## License

LGPL-3.0-only ([LICENSE](LICENSE), [COPYING](COPYING)).
