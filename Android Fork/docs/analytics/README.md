# Analytics

AnkiDroid sends opt-in anonymous usage data to Google Analytics 4 (GA4). The
transport is [google-analytics-kt][lib] — see its README for the library's
setup, builder API, and the full list of hit types. This document covers
only the AnkiDroid-side wiring and the questions reviewers tend to have.

[lib]: https://github.com/criticalAY/google-analytics-kt

---

## What AnkiDroid actually sends

Of the six hit types the library supports, AnkiDroid uses three:

| Used by AnkiDroid               | Sent from                                                                      |
|---------------------------------|--------------------------------------------------------------------------------|
| `screen_view`                   | `AnkiDroidUsageAnalytics.sendAnalyticsScreenView(...)`                         |
| `event`                         | `AnkiDroidUsageAnalytics.send(AnalyticsEvent)`                                 |
| `event` (`PotentiallyDeadCode`) | `reportPotentiallyDeadCode("SomeClass.fallback")` - one event per invocation |
| `exception`                     | `AnkiDroidUsageAnalytics.sendAnalyticsException(...)` (truncated to 150 chars) |

The complete list of events, with their categories and actions, lives in
[`AnalyticsEvent.kt`](../../common/src/main/java/com/ichi2/anki/common/analytics/AnalyticsEvent.kt).
We never include card content, deck names, note fields, sync credentials, or
file paths.

Device details are no longer collected. The Universal Analytics implementation
tagged every hit with OS version, brand, model, board and screen size; none of
that is sent now. Crash reports still carry it via ACRA, which is where it is
useful, and GA4 has no equivalent of UA's custom dimensions, so restoring it
would mean modelling it as user properties rather than porting it across.

`client_id` is a UUID generated and persisted once per install in a dedicated
prefs file (`analyticsPrefs`); it is not tied to any Anki account and survives
profile switches.

---

## Consent when does a hit actually leave the device?

Default state: **opted out**. A hit leaves the device only if all of the
following are true:

1. The user has ticked the opt-in checkbox in the analytics dialog or
   settings. Pref key: `analytics_opt_in_v2`, default `false`.
2. `AnkiDroidUsageAnalytics.optIn` is true every `send…` function starts
   with `if (!optIn) return`.
3. The library's own `config.enabled` is true `GaImpl.send()` re-checks
   this. We pass `enabled = optIn` and call `reinitialize()` when opt-in
   flips, so this is belt-and-suspenders.
4. The process won the sampling roll (see below).

If any check fails the hit is dropped before any network I/O.

---

## Performance

Calls are fire-and-forget on `Dispatchers.IO` `sendAsync` returns
immediately. There is no on-disk queue.

AnkiDroid-specific knobs:

- **Sampling**: configured via `R.integer.ga_sampleFrequency` (production: 10
  → 10% of installs are in-sample for their process lifetime). `setDevMode()`
  forces 100%.
- **Batching**: left off. Each hit is its own POST. See the library's
  [Limitations][lib-limits] for what batching would buy us.

[lib-limits]: https://github.com/criticalAY/google-analytics-kt#limitations

---

## Network failures

The library wraps every HTTP call in `try/catch` on any exception (server
down, no internet, captive portal, timeout) it returns
`GaResponse(statusCode = -1)`, logs at ERROR, and the hit is gone. No retry,
no backoff, no offline replay see the library [Limitations][lib-limits].
Default timeouts are 10s connect / 30s read, blocking only the IO coroutine.

---

## Can it crash AnkiDroid?

**Send path: no.** Both `sendAsync`'s outer launch and the inner OkHttp call
have `try/catch (Exception)`.

**Init path: the only realistic risk.** `AnkiDroidUsageAnalytics.initialize`
is called from `AnkiDroidApp.onCreate()` and runs
`GoogleAnalytics.builder { ... }`, which constructs an `OkHttpClient`. The
builder does not validate `measurementId`/`apiSecret` today empty strings
just produce bad URL params, not exceptions. But to stay safe against future
library changes, we should wrap `initialize` in
`runCatching { ... }.onFailure { Timber.e(it) }` so a bad config can never
prevent app start.

---

## Debug logging seeing what would be sent

The library uses [`io.github.oshai:kotlin-logging`][klog] (an slf4j facade)
and already logs the full request body, response, sampling decision, and
drop reasons. AnkiDroid routes SLF4J to Timber through
[`TimberServiceProvider`](../../AnkiDroid/src/main/java/com/ichi2/anki/logging/TimberServiceProvider.kt),
so debug builds show these in logcat without any setup
(`adb logcat -s GaImpl OkHttpClientImpl`). Release builds only keep INFO and
above, so there you'll see the sampling decision and failed sends.

[klog]: https://github.com/oshai/kotlin-logging

---

## Setting up your own GA4 property

For getting a measurement ID + API secret from the GA4 admin panel, follow
the library's [Prerequisites section][lib-prereqs]. Once you have them, plug
them into AnkiDroid like so:

| Value | Where it goes |
|---|---|
| Measurement ID (`G-XXXXXXXX`) | `AnkiDroid/src/main/res/values/analytic_constants.xml` → `ga_trackingId` |
| API secret | `local.properties` → `ANALYTICS_API_KEY=...` (read at compile time into `BuildConfig.ANALYTICS_API_KEY`, see [`AnkiDroid/build.gradle.kts`](../../AnkiDroid/build.gradle.kts)) |

Builds without an `ANALYTICS_API_KEY` fall back to `DUMMY_API_XXX`, which GA
rejects at ingest contributor builds can't accidentally write to our
production property.

[lib-prereqs]: https://github.com/criticalAY/google-analytics-kt#prerequisites

### Keeping dev traffic out of prod

A signed dev build with the real `ANALYTICS_API_KEY` would otherwise hit our
production GA4 property.

Debug builds pass `debug = BuildConfig.DEBUG` to the library, which sends hits
to GA's validation endpoint (`/debug/mp/collect`) instead. That endpoint checks
the payload and returns any problems with it, but records nothing, so
development traffic can't reach the property.

A separate GA4 property for debug builds would be cleaner still, since it would
let us see the events rather than only validate them. It needs a second
Measurement Protocol secret, so it's left for later.
