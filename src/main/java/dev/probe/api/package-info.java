/**
 * The public API of probe: what other mods may call. Everything outside this package ({@code dev.probe.opt},
 * {@code dev.probe.optsupport}, {@code dev.probe.mixin}) is internal and changes without notice.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>{@link dev.probe.api.ProbeApi}: version, which features are active, waking the server thread.</li>
 *   <li>{@link dev.probe.api.Pools}: the shared worker pools and new {@link dev.lab.runtime.LabRuntime} pools.</li>
 *   <li>{@link dev.probe.api.CompletionRing}: hand results from any thread to an owner thread (server or render
 *       thread), one drain task per batch, no blocking.</li>
 *   <li>{@link dev.probe.api.SideEffectJournal}: run code that has main-thread side effects on another thread; the
 *       side effects are recorded and replayed later on the owner thread, in order.</li>
 *   <li>{@link dev.probe.api.AdmissionWindow}: feedback for work that may stretch a tick (average cost against a
 *       deadline, an AIMD window over tick length).</li>
 *   <li>{@link dev.probe.api.Compat}: opt a mod's entity or block entity types out of parallel rendering, the render
 *       cache and the sleep engines.</li>
 * </ul>
 *
 * <h2>Using it from another mod</h2>
 * Declare probe as an optional dependency and touch these classes only when it is loaded
 * ({@code ModList.get().isLoaded("probe")}), so the mod still runs without it. Build against the jar published by
 * {@code ./gradlew publish} ({@code dev.probe:probe-mod:<version>} in {@code probe-mod/repo}).
 *
 * <h2>Stability</h2>
 * {@link dev.probe.api.ProbeApi#VERSION} grows when something is added; nothing in this package is removed or changes
 * meaning without a new major version. Every call is safe whatever flags are set: a feature that is off makes the
 * call a plain vanilla-equivalent path (a ring still delivers, a journal still runs its calls, an exclusion is simply
 * kept for when the feature is on).
 */
package dev.probe.api;
