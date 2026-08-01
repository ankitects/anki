# `:baselineprofile`

This module generates AnkiDroid's **baseline profile** - a list of classes
and methods ART pre-compiles at install time so cold start is faster.

It has two classes:

- **`BaselineProfileGenerator`** captures a cold-start trace and writes
  `baseline-prof.txt` into `:AnkiDroid`.
- **`StartupBenchmark`** measures cold-start time with vs without the
  profile so you can see the impact.

For results to be valid for comparisons and real-world impact, they must
be run on real hardware devices ideally the same device over time and
representative of typical user hardware. So this is currently not
intended to be used in CI unless CI is connected to real devices (e.g.
Firebase Test Lab or similar).

## Requirements

- Physical Android device, API 28+ (emulators aren't trustworthy)
- USB debugging on, device plugged in

## Regenerate the profile

Run with `-PcustomSuffix` and `-PcustomName` so the benchmarked APK installs
alongside your real AnkiDroid (`com.ichi2.anki.bp`) instead of replacing it.
Without these flags the build targets `com.ichi2.anki`, the production app
ID — installing it would clobber your real collection or fail with a
signing-key mismatch.

```sh
./gradlew :AnkiDroid:generateBaselineProfile \
  -PcustomSuffix=".bp" \
  -PcustomName="AnkiDroid Baseline"
```

Takes a few minutes. The plugin installs a non-minified build, runs the
cold-start journey (launch app -> dismiss intro screen if present -> wait
for DeckPicker), and writes the result to `baseline-prof.txt`.

## When to regenerate

When the startup path changes meaningfully — new init work in
`AnkiDroidApp.onCreate`, a refactor to `IntentHandler` or `DeckPicker`,
a new dependency wired into `Application`. Otherwise, leave it alone;
the profile is device- and OS-agnostic, one commit covers everyone.

## Config notes

See comments in [`build.gradle.kts`](build.gradle.kts) for the rationale
behind `minSdk`, `missingDimensionStrategy`, the `benchmark` buildType,
and `self-instrumenting`. The KDoc on each class explains the journey
and the measurement modes.
