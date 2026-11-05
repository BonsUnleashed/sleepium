# API for other mods

Only `dev.lull.api` is meant for other mods.

Other mods can include the engine from source, `includeBuild('../sleepium/1.21.1-neoforge') (a checkout of a version's branch)` in `settings.gradle` and then
`compileOnly 'dev.lull:sleepium:1.0.0'`. Only `dev.lull.api` is meant for them. The
build fails if the engine imports another mod's package (`checkEngineIndependent`).

`dev.lull.api`:

- `SleepApi`: is a feature on; wake the server thread.
- `Pools`: the shared background and IO pools.
- `CompletionRing`: hands results from any thread to an owner, in order, without blocking.
- `SideEffectJournal`: runs work off the owner thread and replays its shared-state calls in order.
- `AdmissionWindow`: budgets heavy work against the tick deadline.
- `NetContent`: bulk content to clients.
- `Travel`: teleports once the destination's chunks are loaded.
- `Compat`: per-type opt-outs from parallel rendering, the render cache, block-entity sleep and entity sleep. Players
  can set the same opt-outs without code: `-Dsleep.compat.noParallelRender=`, `noRenderCache`, `noSleep` and
  `noEntitySleep` take comma-separated ids, for example `-Dsleep.compat.noRenderCache=mymod:clock`.

Every API call is safe with any combination of flags.
