# Sleepium for Minecraft 1.20.1 / Forge

This branch is a **long-term** line: every fix, features when the port is cheap. It grew from the 1.21.1 engine at
v1.0.3 (the branch's first commits are the port).

What is special here (more in [versions](https://github.com/ha7gawa/sleepium/blob/main/docs/versions.md#what-differs-on-1201--forge)):

- Minecraft 1.20.1, Forge 47.4.9, Java 17 or newer; built with ModDevGradle Legacy.
- In production Forge names members the SRG way (`m_12345_`): the bytecode analyses read Mojang's names
  (`ClassBytes`), run-time lookups go through `Names`, access transformers are in SRG names. Check every change on a
  production server and client too - dev runs cannot see this.
- `port-pending.txt` lists the 1.21.1 files left out of this build (parts not ported, 1.21-only systems).

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
