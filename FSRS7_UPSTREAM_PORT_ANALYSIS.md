# FSRS7 upstream-port analysis

Date: 2026-09-24

## Audit basis

- Implementation baseline (2026-09-11): `upstream/main` at
  `7941f546fcafeb54e63f2fcf17c1193e6df65716`.
- Upstream was refreshed again on 2026-09-24 to `bd69a4530`. The intervening
  collection-opening, schema-upgrade, and FSRS source changes were inspected;
  the worktree remains based on `7941f546f` and has not been rebased.
- Fork remote baseline at the original audit: `origin/main` at
  `f34323ce5d1e45cb4091ace738543560bffc8795`; the fork remediation commit
  `43c29bad1` is an ancestor of this ref.
- Fork remediation: originally developed as `codex/fix-fsrs7-exact-math` at
  `bdd87ca`, then cherry-picked onto `/Users/jschoreels/workspace/anki` `main` as
  `43c29bad1`, directly above that `origin/main` baseline.
- Refreshed fork/upstream merge base:
  `9753998c5b40107b7bde9de062b683e5dde5b247`.
- The initial analysis was performed in a separate detached worktree. It was refreshed
  against the latest refs and moved to `codex/fsrs7-upstream`, based on
  `upstream/main`, for the upstream implementation work.
- Since the earlier `9f71d0e83` audit baseline, the subsequently refreshed upstream
  changes do not alter this report's FSRS7 design conclusions.
- Anki upstream still depends on `fsrs` 6.6.2. This branch now pins refreshed
  fsrs-rs upstream `c137ee6e096f9217632397a8fb2bdb6f6e1b92ae`, including empty
  parameters selecting FSRS7, model-version introspection, fast-state validation,
  corrected SM-2 conversion, and removal of Burn.

This document now records both the porting analysis and the implementation on
`codex/fsrs7-upstream`. The fork should still not be cherry-picked wholesale: it
combines FSRS7 with version
selectors, Dynamic DR, RWKV scheduling, custom fuzz/minimum-interval controls, add-on
preset overlays, release infrastructure, and other unrelated behavior.

## Executive conclusion

A minimal _complete_ upstream PR is feasible, but it is not just a dependency bump and
a 34-value parameter slot. It needs one coherent FSRS7 state-and-metrics conversion:

1. accept final 34-parameter FSRS7 models without adding a user-visible version
   selector;
2. persist the complete dual-trace memory state and preserve user-visible S90;
3. rebuild that state from revlogs whenever a preset begins using FSRS7 parameters;
4. centralize preset-aware FSRS math and route every current-R consumer through it;
5. move R-dependent queue/search ordering out of the legacy scalar-decay SQL path;
6. train/evaluate FSRS7 on fractional same-day targets with scheduling penalties always
   enabled;
7. port the concurrent Optimize All Presets execution and its multi-progress UI;
8. add the requested **New card intervals at graduation** comparison table by simulating
   the real scheduler state machine;
9. retain model-generated intraday intervals after configured steps, without changing
   upstream daily-limit accounting; and
10. expose read-only batch card-details/current-metrics APIs so add-ons can obtain
    correct FSRS7 R without reimplementing its model boundary.

This implementation pins an audited git revision rather than a published crate release.
The final upstream PR should preferably use an agreed tagged/published fsrs-rs
release containing the final 34-parameter API, or obtain approval for the exact git
pin. The updated revision removes Burn and
its transitive dependencies, so the earlier `bincode` and `paste` advisory exceptions
have been removed. The dependency audit also required the minimal rustls 0.23.45
security update (RUSTSEC-2026-0285) and rustls-webpki 0.103.15.

## Implementation status on the upstream branch

The requested production scope is implemented on `codex/fsrs7-upstream`, on
the implementation baseline above. The implementation was reconstructed as an upstream
change instead of transplanting the fork's bundled feature commits.

| Area                             | Status on this branch                                                                                                                            |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| FSRS7 dependency/model           | Pins the audited 34-parameter fsrs-rs revision; implicit model selection and updated license/advisory metadata.                                  |
| State persistence                | Stores S90 plus slow internal stability, optional fast stability, and difficulty; old state remains readable.                                    |
| Scheduling/training              | Uses actual fractional elapsed time for every FSRS7 review, including multi-day reviews, and always enables the scheduling-penalty model.        |
| Exact R and relative overdueness | Uses one preset-aware backend metric context with cached models and full memory state.                                                           |
| R consumers                      | Browser cells/order, `prop:r`, filtered decks, main queue ordering, Card Info, and graphs use exact FSRS7 math.                                  |
| SQL ordering                     | FSRS7 R-dependent ordering is computed before limits in Rust; `prop:r` uses an operation-scoped temporary table.                                 |
| Queue performance                | Bulk-reads due candidates and state once, reuses preset models, and retains signed FNV ties and post-sort limits; opt-in synthetic benchmark.    |
| Intraday intervals               | Configured steps take precedence; model intervals below twelve hours use learning/relearning queues, including after the last step.              |
| Foreign/legacy state repair      | Repairs incomplete FSRS7 traces on open/sync/Check Database; replays imports under destination presets; calibrates legacy API S90/D writes.      |
| Batch read APIs                  | Read-only card details and current memory metrics, with optional exact R and selected note fields; Python wrappers and contract tests.           |
| Optimize All Presets             | Prepares data serially, balances concurrent worker lanes, preserves best-effort updates, and reports aggregate/per-preset progress and failures. |
| New-card interval table          | Simulates the actual learning state machine for the eight requested answer paths and compares current/selected retention.                        |
| Documentation                    | Deck options, search, filtered-deck, and statistics manuals describe the new behavior.                                                           |

The implementation deliberately does **not** add FSRS version selectors, a same-day
optimization toggle, a penalty toggle, RWKV, Dynamic Desired Retention, review-fuzz or
minimum-interval controls, or the fork's release/add-on infrastructure. The only
`include_same_day` switch in the code is a private replay compatibility argument used
to distinguish legacy parameter families; it is not configuration or UI.

The source tree does not contain an upstream `RELEASE.md`; the manual updates describe
the user-visible behavior.

Verification results are recorded near the acceptance matrix below. The remaining
external dependency-readiness item is agreement on a release/tag or acceptance of the
exact fsrs-rs git revision used here.

## Fork fixes completed after the audit

The fork remediation branch corrects the concrete defects discovered while preparing
this upstream plan:

- added a complete-state retrievability helper that passes slow internal stability,
  fast stability, and difficulty to the card's selected FSRS parameters;
- routed Browser cells/sorts, `prop:r`, filtered-deck ordering, review-queue ordering,
  and Card Info stats through that helper;
- replaced FSRS Relative Overdueness SQL keys in normal and filtered-deck built-in paths
  with `-elapsed / interval_at_retrievability(full_state, desired_retention)`;
- made both Ascending R and Descending R share the global due-card gather path, apply
  limits after sorting, and retain the signed `fnvhash(id, mod)` tiebreak;
- updated the TypeScript Card Info curve to carry each historical state's slow trace,
  fast trace, and difficulty;
- kept scalar add-on math APIs source-compatible, but documented their FSRS7 assumptions
  instead of describing them as exact; and
- restored Optimize All Presets aggregate progress, review-weighted ETA, active-job
  bars, and completion/skip details. Its balanced concurrent backend and the **New card
  intervals at graduation** table were already present and remain included.

Regression coverage now includes equal-slow-stability states with distinct fast traces
and difficulty, global descending queue interleaving, exact Relative Overdueness target
intervals, search/filter/display paths, and a TypeScript full-state curve fixture.
Verification completed on the remediation branch with `just test-rust`, `just test-ts`,
`just test-py`, and `just lint`; the project-required `just check` also passed. After
the fix was transplanted onto the refreshed fork `main`, `just check` passed again at
`43c29bad1` from a clean worktree.

These fork repairs do not change the recommended upstream product scope. The fork still
retains its selectors, same-day and penalty toggles, RWKV/Dynamic DR behavior, and other
non-production features. The upstream PR should omit those as described below.

## The non-negotiable FSRS7 math contract

### R is not a function of `card.data.s` and elapsed time

For FSRS6 and earlier, Anki's scalar power curve could derive current retrievability
from one stability, elapsed time, and one decay. FSRS7's active curve is a weighted
mixture. Its exact input is:

```text
R = model(params[0..34]).current_retrievability(
        slow_internal_stability,
        fast_stability,
        difficulty,
        elapsed_days,
    )
```

The first component uses `s_fast` and a decay that itself varies with `s_fast`; the
second uses slow `s` and difficulty; the mixing weights also depend on both stabilities
and difficulty. The full preset parameter vector is part of the calculation.

Consequences:

- `card.data.s`, `card.data.s_int`, `card.data.s_fast`, or `card.data.d` alone is never
  enough to derive exact R.
- A single stored `decay` cannot represent the FSRS7 mixture.
- R must not be persisted: it changes with time and becomes stale when parameters,
  state, deck routing, or elapsed time changes.
- A helper whose signature is `(params, stability, elapsed)` is not an exact FSRS7
  helper, even if it calls `FSRS::current_retrievability` internally.

Using the audited dependency's default parameters illustrates the lost information. At
20 elapsed days, three states with the same slow stability of 10 days produce different
R values:

| Slow S | Fast S | Difficulty |       R |
| -----: | -----: | ---------: | ------: |
|     10 |      5 |          5 | 0.87905 |
|     10 |     20 |          5 | 0.88150 |
|     10 |      5 |          8 | 0.82883 |

A scalar helper would collapse all three states to one value and can therefore reverse
or flatten real ordering relationships.

### Fork defect found during the audit, now corrected

At the audited `origin/main`, the fork defined
`fsrs_current_retrievability_for_params(params, stability, elapsed)` and reconstructed
an fsrs-rs state with `difficulty = 5` and `stability_fast = stability`. Browser,
search, filtered-deck, queue, and Card Info paths then passed only
`stability_internal`. Those results were not exact for general FSRS7 states, and the
tests mostly used difficulty 5 with no distinct fast trace.

The shared API must instead accept the complete state:

```rust
fn current_retrievability(
    fsrs: &FSRS,
    state: FsrsMemoryState,
    elapsed_days: f32,
) -> Result<f32>
```

Its conversion to `fsrs::MemoryState` must preserve slow stability, fast stability, and
difficulty. Code that only has a scalar stability must either obtain/reconstruct the
full state or be explicitly named and documented as an approximation. The remediation
branch now follows this rule: built-in consumers use a full-state helper, while the
existing scalar add-on APIs remain explicitly documented compatibility approximations.

### Stability storage contract

The cleanest compatibility contract is the one the fork was aiming for:

| Value                          | Storage/API        | Meaning                                            |
| ------------------------------ | ------------------ | -------------------------------------------------- |
| `s` / `memory_state.stability` | existing field     | S90: interval where the active model reaches R=90% |
| `s_int` / `stability_internal` | new optional field | FSRS internal slow stability                       |
| `s_fast` / `stability_fast`    | new optional field | FSRS7 fast stability                               |
| `d` / `difficulty`             | existing field     | full difficulty                                    |

This preserves the meaning of the Stability column and `prop:s` across model versions.
On every FSRS7 state write, compute S90 with `FSRS::s90(full_state)` (or
`interval_at_retrievability(full_state, 0.9)`) and store it in `s`; never assume the slow
trace is S90.

Add optional protobuf fields 3 and 4 to `cards.FsrsMemoryState` for internal and fast
stability. Add the JSON keys to `CardData`; no SQL schema bump is required. Round all
stabilities consistently, and ensure a positive value below four-decimal precision is
persisted as at least `0.0001`, including during Check Database repair.

The existing `decay` card-data field can remain for older parameter families and old
clients, but it must not be used as an FSRS7 source of truth. In particular, storing
`params[23]` as a “compatibility decay” does not make legacy SQL or JavaScript formulas
correct.

### State migration and incomplete state

Exact dual-trace state cannot be reconstructed from the old scalar card state. When a
preset first switches from 17/19/21 parameters to 34 parameters, replay each affected
card's review history with the new model and persist the resulting full state and S90.
The existing deck-options parameter-change/update-memory-state flow is the natural place
to do this.

Incomplete internal or fast traces under an explicit FSRS7 preset trigger eager
repair on opening, normal sync, and Check Database. Usable revlogs are replayed first;
without them, model-aware SM2 conversion supplies an approximate trace shape, rescaled
at the stored difficulty to preserve public S90. This cannot recover the original
traces. Sorts then evaluate the repaired state with the real destination model, not a
scalar-decay substitute. Repair is transactional and retains due dates/intervals.

For truncated FSRS history, restoring difficulty from the first revlog's FSRS factor
changes the FSRS7 curve. Both traces must be rescaled again to retain the first known
interval at historical retention. The library's corrected SM2 conversion alone does
not cover this Anki-side difficulty override.

Legacy API callers that intentionally write only S90/D receive the calibrated
approximation; replaying history would overwrite their explicit edit. Scheduled APKG
imports retain source-preset provenance before deck remapping, detect incomplete
JSON traces before compatibility defaults fill them, and rebuild after importing
revlogs if the destination preset differs or source presets were not retained.

Cards moved to another normal deck also need their state rebuilt with the destination
preset. Otherwise the new preset's mixture is applied to state produced by the old
model. Filtered cards must resolve their original/home deck preset.

## Recommended compatibility model: no selector

Do not port the fork's `FsrsVersion` protobuf field, deck-options tabs/selectors, or
per-version parameter inputs. Continue selecting the model internally by validated
parameter length:

- existing non-empty 17/19/21-value presets remain on their existing model until the
  user optimizes or resets parameters;
- a legacy preset with all parameter slots empty automatically adopts explicit FSRS7
  defaults and rebuilds its cards before the collection is available for use;
- new collections/presets and Reset use explicit final 34-value FSRS7 defaults;
- Optimize and Optimize All always produce 34-value FSRS7 parameters;
- an empty newest slot falls back to the existing legacy slot for old collections;
- invalid or non-finite arrays fail validation instead of changing model implicitly.

Add `fsrs_params_7` as field **7** in `DeckConfig.Config`, reducing the existing
`reserved 7 to 8` declaration to reserve field 8. Upstream deliberately kept those
numbers for future FSRS parameter changes; do not copy the fork's temporary field 52 or
its `other["jschoreels.fsrs"]` storage.

Update defaulting, schema11 conversion, import/export, deck-config update detection,
rescheduling, and parameter-input selection to prefer `fsrs_params_7`, then 6/5/4.
Keep old fields readable and syncable for compatibility. Empty presets upgrade when a
client opens a collection and after receiving them through normal sync. The preset
parameters and rebuilt card states are written in one transaction, so an error or
cancellation rolls back both and the next attempt can retry. Complete histories are
replayed with fractional same-day deltas; truncated/absent histories use the model's
SM-2 conversion. Existing due dates, intervals, queues, and filtered-deck membership
are preserved. New cards have no state to rebuild. With FSRS disabled, cards without
FSRS state remain untouched.

Persisting the 34 defaults makes the upgrade idempotent and syncable without a new
schema version or collection-wide migration flag. Server collections and temporary
sync-payload validation skip the client upgrade; full downloads are upgraded on
reopening, after the downloaded sync timestamp is established. Normal-sync upgrades
run after its transaction commits, retaining pending USNs for a subsequent upload.
The migration transaction also keeps its modification timestamp strictly newer than
the last-sync timestamp, including when the server clock is ahead; normal edits and
full-upload bookkeeping retain their existing timestamp behavior.
APKG exports without scheduling still contain no saved FSRS parameters. Opening that
package for import resolves the empty preset to current FSRS7 defaults.
Backend memory-rebuild progress and cancellation use the existing progress channel;
Qt's synchronous collection-opening path does not yet provide a dedicated migration
progress dialog.

## One canonical preset-aware metric layer

Introduce one small backend component used by scheduler, search, browser, and stats.
For a batch of cards it should:

1. resolve each card's effective home deck config;
2. validate/select that config's parameter array;
3. cache one `FSRS` instance per config id plus parameter fingerprint;
4. obtain the complete memory state;
5. calculate elapsed seconds in one place, preferring `last_review_time` and retaining
   the existing due/interval fallback for legacy cards; and
6. return exact R, S90, and (when requested) relative-overdueness keys.

This avoids repeated model construction and prevents slightly different elapsed-time
rules from appearing in each consumer.

For an FSRS7 card:

```text
R = fsrs.current_retrievability(full_state, elapsed_days)
target_interval = fsrs.interval_at_retrievability(full_state, desired_retention)
relative_overdueness_sort_key = -elapsed_days / max(target_interval, epsilon)
```

The last formula is the model-independent equivalent of upstream's current scalar
inverse-curve expression. Sorting the negative ratio ascending keeps the most relatively
overdue cards first. It avoids inventing an FSRS7 “decay”. Preserve the existing SM2
fallback for cards without usable FSRS state.

## R ordering and SQL audit

Upstream currently embeds `extract_fsrs_retrievability(...)` or its relative form in
SQL for review queue order, filtered decks, Browser ordering, and `prop:r`. Those UDFs
only receive `card.data`, due/interval values, and timing; they do not receive the deck
preset parameters and implement a scalar curve. They cannot be extended to exact FSRS7
by merely reading `s_int` and `s_fast`.

The legacy SQL UDFs may remain for parameter families up to FSRS6, but every active
34-parameter path must bypass them.

### Review queue

Use one shared in-memory gather/sort path for **both** Retrievability Ascending and
Retrievability Descending:

- gather due reviews, due interday learning/relearning, and due-now intraday
  learning/relearning before applying review limits;
- exclude new cards because they have no R;
- keep future intraday cards hidden until due and preserve the queue-rebuild behavior
  that inserts them at the right time;
- compute exact full-state R with the card's home preset;
- ascending means lowest R first; descending means highest R first;
- apply root, parent, and per-deck admission limits only after the global R sort;
- preserve sibling burying, the pinned current card, original-deck accounting for
  filtered cards, and new/review mixing behavior; and
- preserve upstream's deterministic FNV tie-breaker (`fnvhash(id, mod)`) instead of
  silently changing equal-R cards to card-id order.

The remediation branch completes this shape for Ascending R, Descending R, and Relative
Overdueness. It globally interleaves due review/interday/due-now intraday cards,
computes full-state keys before applying review limits, and retains the SQL-compatible
signed FNV id/mtime tiebreak. Upstream should port the corrected architecture, not the
older audited implementation.

The follow-up port removes its per-candidate `get_card()` lookup: a narrow storage
projection reads scheduling fields and shared-decoder memory state in one union of
due day queues and due-now second queues. No SQL limit is applied before exact
scoring. Models remain request-local, so there is no persistent cache invalidation
contract. `just fsrs-queue-bench` measures synthetic 1k/10k/50k/100k due-card backlogs
under FSRS6/7 and Day/Random/Ascending R/Descending R, including answer-next latency.
It opens no user collection and has no wall-clock pass/fail threshold.

Future intraday cards are not exposed through the ordinary learn-ahead iterator in
exact-R mode. When one becomes due, the queue rebuilds at the next answer boundary
so it enters at its R rank rather than jumping ahead of reviews. The displayed
question remains pinned until answered. Learning counts exclude future entries;
both directions, retained current card, intraday-only reads, and undo are covered.
Automatic rebuilds advance the queue's timestamp token even within one millisecond,
so undo cannot apply an earlier count snapshot to a newly sorted queue. The regression
forces this clock/token collision instead of relying on test execution speed.

Local arm64 macOS release-mode results on 2026-09-24 (FSRS7, Ascending R,
review limit 200; synthetic in-memory collection):

| Due cards | First rebuild (ms) | Median of 5 warm rebuilds (ms) | Median answer + next, 25 answers (ms) |
| --------: | -----------------: | -----------------------------: | ------------------------------------: |
|     1,000 |               1.07 |                           1.06 |                                 0.046 |
|    10,000 |               9.10 |                           8.99 |                                 0.042 |
|    50,000 |              46.61 |                          45.55 |                                 0.043 |
|   100,000 |              90.79 |                          93.18 |                                 0.045 |

At 100k, Descending R's warm median was 96.65 ms. These are observed local
measurements, not a cross-machine promise or a before/after speedup claim. They do
not measure real-profile disk I/O, the `prop:r` temporary table, or a queue rebuild
on every answer; the benchmark separately times rebuilds and ordinary answer-next.

### Filtered decks

For an R-ordered filtered search, obtain all cards matching the term without applying
the term limit, compute exact keys, sort in the requested direction, then take the
limit. Applying SQL `LIMIT` before exact sorting selects the wrong cards. Use the same
FNV tie behavior as the existing filtered-deck SQL order.

### Browser sort and cells

For a Browser sort, first obtain the matching card ids without R ordering, batch-load
their cards/configs, compute the metric, sort in Rust, and materialize the ordered ids
in the existing search table. The displayed R cell must use the same metric helper.
Test normal and reverse order, null/missing state, and cards from different presets.

### `prop:r` search

An operation-scoped temporary table keyed by card id is the smallest correct design for
arbitrary boolean searches. Populate it with exact FSRS7 R and let SQL predicates join
against it. Avoid the fork's N+1 `get_card()` construction: use a batch card scan and
batch config resolution, bulk insert in a transaction, and benchmark large collections.
For Browser sorting, scope computation to already-matched ids rather than filling the
whole collection.

Do not store this table's R values in synced card data. Its lifetime must be one search
operation because time and configuration changes invalidate it.

### Other consumers that must agree

Audit and route all of these through full-state, preset-aware math:

- `rslib/src/browser_table.rs`;
- `rslib/src/search/mod.rs` and `rslib/src/search/sqlwriter.rs`;
- `rslib/src/scheduler/queue/builder/*`;
- `rslib/src/scheduler/filtered/mod.rs`;
- `rslib/src/stats/card.rs`;
- `rslib/src/stats/graphs/retrievability.rs` and related aggregate knowledge values;
- simulator/optimal-retention final-state calculations; and
- Card Info's forgetting curve.

### Why the PR includes batch add-on read APIs

The APIs adapted from fork commit `127842766` are intentionally part of this PR,
even though core scheduling does not require a new public endpoint. FSRS7 makes
scalar add-on R calculations incorrect. `card_memory_metrics()` and `card_details()`
provide a supported alternative that resolves the home preset and complete state in
the backend, reuses models per request, and avoids repeated Card Info history work.
They do not initialize state, replay history, or write cards. Missing states remain
absent. Input order/duplicates are preserved and missing IDs omitted. Selected note
values are optional and raw, with case-sensitive names and note-type order. The
Python/protobuf docs and implementation comments explain this boundary. No scalar
math helper, preset overlay, or public batch optimizer is added.

The fork's stats retrievability graph remains a useful batch-oriented reference because
it caches `FSRS` by preset and passes the full state. On the remediation branch,
Browser, search, queue, filtered-deck, Card Info stats, and the Card Info curve also
preserve the complete state. Some paths still resolve cards/presets one at a time and
should be batched in the upstream implementation.

For the TypeScript Card Info curve, the smallest patch is to pass every historical
state's slow stability, fast stability, and difficulty into the FSRS7 mixture formula,
along with the 34 parameters, and add parity fixtures generated by fsrs-rs. The
remediation branch implements that small patch and adds a distinct-fast-stability,
non-default-difficulty fixture. A backend-generated curve would avoid duplicated model
math but is a larger IPC change.

## Training, evaluation, and scheduler state updates

For a 34-parameter model, production behavior should be fixed rather than configurable:

- `ComputeParametersVersion::Fsrs7`;
- include same-day targets in optimize, Optimize All, evaluate, and health-check paths;
- derive FSRS7 `delta_t` as fractional days from revlog timestamp gaps, with a minimal
  positive floor for distinct events; keep the initial review at zero;
- retain aligned card ids after sorting training targets, so the dependency's
  same-card/windowed training behavior receives correct groupings;
- set `enable_sched_penalties = true` explicitly for every FSRS7 optimizer/evaluation
  input, regardless of the dependency's default; and
- use one training-item preparation path for Optimize, Optimize All, Evaluate, and
  health checks.

Do not add `fsrs7IncludeSameDayOptimize` or `fsrs7EnableSchedulingPenalties` fields,
deck-config `other` keys, switches, comparison modals, or request overrides. Legacy
parameter families can keep their current calendar-day target behavior internally.

Remove the Anki-side “use the old params when raw training logloss is no better”
post-filter for FSRS7. The optimized objective can include regularization and schedule
penalties, so raw logloss is not the selection objective. Validate finiteness and
parameter shape, then trust the fsrs-rs training result.

Scheduler answer paths, historical-state reconstruction, rescheduling, and the simulator
must pass fractional elapsed days and the full memory state. Runtime FSRS7 also keeps
model-generated intervals below twelve hours in learning/relearning queues once no
configured step applies, as requested in the follow-up scope. No learning-queue bypass
or minimum-interval control is added. Intraday answers do not consume daily review
limits; original review answers and interday learning still do, preserving upstream
accounting. This is tested for normal and both exact-R review orders.

## Optimize All Presets improvements to port

The concurrency work from `e3cb450a9` is worth extracting and adapting to current
upstream:

1. prepare each preset's collection-bound, read-only training data sequentially;
2. skip zero-target presets before worker creation;
3. sort remaining jobs largest-first by target count;
4. greedily balance them into at most `min(worker_count, job_count)` lanes;
5. execute lanes concurrently while jobs inside a lane remain sequential;
6. give each job independent progress/cancellation state;
7. aggregate progress every roughly 100 ms; and
8. apply successful parameter results and update `LastFsrsOptimize` only after workers
   return, on the collection thread.

The worker phase must not hold or mutate `Collection`, SQLite, deck configs, or the
update request. Cancellation must set `want_abort` for every active optimizer. Results
should be restored to input order before applying them.

Port the progress protobuf with per-preset name, iteration counts, total/long-term/
same-day target counts, `finished`, and `skipped`. The Qt progress dialog should show:

- aggregate preset completion;
- review-weighted progress and ETA;
- one bar per active job, largest workloads first;
- a bounded completed-preset log; and
- a summarized skipped-preset count.

At the audited `origin/main`, the backend/protobuf and generic multi-bar dialog
machinery were present, but a later merge had removed the `compute_all_params` branch
from `qt/aqt/mediasrv.py::_update_deck_configs()`. The remediation branch restores the
handler from `e3cb450a9`, adapted to the current update-deck-config success behavior.
This branch includes a Rust regression test that preserves the per-preset failure flag
through the progress protobuf. A focused Qt regression test that feeds a
`ComputeAllParamsProgress` message and verifies details/bars would still be useful; the
Python bridge currently relies on the Qt suite, typing, and integration coverage.

Avoid imposing a wider minimum size on every ordinary progress dialog; size the dialog
dynamically only when details or multiple bars are present. Benchmark for nested CPU
parallelism: fsrs-rs may already use threaded kernels, so the lane count should be
bounded if concurrent presets oversubscribe typical machines.

Upstream currently logs a per-preset failure and continues. This branch preserves that
best-effort policy, carries a failure flag through progress, visibly reports the failed
preset name, and leaves its old parameters unchanged. Whether Optimize All should
instead be atomic is listed as a product decision below.

## “New card intervals at graduation” table

Port only the table from `5bad1c567`, not the custom review-fuzz feature bundled in that
commit.

The backend should simulate the actual scheduling state machine with the current
unsaved deck config and return the eight existing rows in a documented fixed order:

1. Again
2. Hard
3. Good
4. Easy
5. Again → Good
6. Again → Again
7. Good → Again
8. Good → Good

Build the initial `LearnState`, call its normal `next_states()`, then feed the selected
Again/Good state back through the state machine for the follow-up rows. Format intervals
through `describe_next_states()` so the preview uses Anki's normal interval wording.
For follow-ups scheduled in seconds, pass `scheduled_secs / 86_400.0` to FSRS; do not
round a same-day interval up to one day.

The request should contain only the unsaved `DeckConfig.Config`. It should not contain
the fork's same-day/learning-queue toggles. Ensure the current parameter array is put in
the correct implicit slot (`fsrs_params_7` for 34 values), rather than the early fork
implementation's `fsrs_params_6` workaround.

The Svelte component should issue Current DR and Selected DR requests concurrently,
discard stale async responses with a monotonically increasing request id, display
backend errors, and react to changes in desired retention, params, learning steps, and
interval limits. Add backend tests for the eight paths and a frontend test for stale
response suppression.

The title says “at graduation”, while the direct Again/Hard rows can still represent a
learning interval rather than graduation. Keep the requested title for the PR, but the
wording/row semantics should receive product review.

## Proposed PR commit structure and primary files

A single PR can remain reviewable if split into these focused commits:

| Commit | Scope                                                           | Primary files                                                                                                                                                                             |
| ------ | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1      | published FSRS7 dependency and implicit 34-param selection      | `Cargo.toml`, `Cargo.lock`, `cargo/licenses.json`, `rslib/src/deckconfig/{mod.rs,schema11.rs,update.rs}`, `proto/anki/deck_config.proto`                                                  |
| 2      | dual-trace/S90 persistence and migration                        | `proto/anki/cards.proto`, `rslib/src/card/{mod.rs,service.rs}`, `rslib/src/storage/card/data.rs`, `rslib/src/scheduler/fsrs/memory_state.rs`, `rslib/src/dbcheck.rs`, import/export tests |
| 3      | FSRS7 training/evaluation/simulator correctness                 | `rslib/src/scheduler/fsrs/{params.rs,simulator.rs,rescheduler.rs}`, scheduler answering/states/service files, progress conversion                                                         |
| 4      | canonical exact metrics and all ordering/search/stats consumers | `rslib/src/scheduler/fsrs/memory_state.rs` or a new `metrics.rs`, queue builder, filtered scheduler, search, browser table, stats, Card Info curve                                        |
| 5      | concurrent Optimize All and multi-progress UI                   | `proto/anki/collection.proto`, `rslib/src/deckconfig/update.rs`, optionally a small `fsrs/batch.rs`, `rslib/src/progress.rs`, `qt/aqt/{mediasrv.py,progress.py}`                          |
| 6      | new-card interval table                                         | `proto/anki/scheduler.proto`, `rslib/src/scheduler/service/mod.rs`, `qt/aqt/mediasrv.py`, `ts/routes/deck-options/FsrsOptions.svelte`, `ftl/core/deck-config.ftl`                         |
| 7      | release notes/docs and final cross-surface tests                | `RELEASE.md`, relevant architecture/search/card-data documentation, Rust/Python/TypeScript tests                                                                                          |

Generated files under `out/` should be produced by the normal build but not treated as
hand-edited source.

## Acceptance test matrix

The following matrix is the recommended upstream merge coverage; several cases
specifically prevent the audited fork's former scalar approximation from passing. The
branch includes focused Rust coverage for the critical state/math, search, queue,
training, Optimize All, persistence, and interval-preview paths, plus a TypeScript
full-state curve fixture. A few UI/integration rows are called out after the verification
results as additional pre-merge hardening rather than being overstated as isolated tests.

### State and math

- Two cards with equal slow stability and elapsed time but different fast stability
  produce different R matching direct fsrs-rs calls.
- Two cards with equal stabilities but different difficulty produce different R.
- A full state round-trips through CardData and protobuf without losing either trace.
- Stored `s` equals `fsrs.s90(full_state)`, not slow stability.
- Tiny positive slow/fast/S90 values do not round to zero; Check Database repairs old
  zero values.
- A 21→34 parameter transition replays history and writes both traces; changing one
  34-value preset to another and moving a card to another preset also rebuild state.
- Incomplete history uses the model-aware SM2 fallback and has a documented sort policy.

### Search/order/display consistency

- Ascending R puts the numerically lowest exact R first; Descending is its reverse.
- Both directions globally interleave review, interday learning/relearning, and due-now
  intraday cards.
- Parent/subdeck and filtered-deck review limits are applied after R sort.
- Future intraday cards remain absent until due, then enter at their exact R position.
- Equal-R queue cards retain the existing FNV tie order.
- Filtered-deck term limits are applied after exact sort.
- Browser cell, Browser order, `prop:r`, card info, and stats return the same R fixture.
- `prop:s` and Stability sorting use stored S90.
- Relative Overdueness matches `-elapsed / interval_at_target` for FSRS7.
- Card Info historical curve points match fsrs-rs fixtures with non-default difficulty
  and distinct fast/slow traces.

### Training and optimization

- Same-day FSRS7 targets carry fractional, nonzero deltas; legacy target behavior is
  unchanged.
- Target sorting preserves item/card-id alignment.
- every FSRS7 optimize/evaluate/health input has scheduling penalties enabled.
- invalid/non-finite/wrong-length optimizer output is rejected, but raw logloss does not
  override a valid penalty-trained result.
- Optimize All lane assignment is balanced and largest-first; output application is in
  input order.
- zero-target, failure, cancellation, and all-success cases have correct progress and do
  not mutate deck configs from worker threads.
- Qt progress conversion renders aggregate state, active bars, completion log, and skip/
  failure status.

### UI, IPC, and compatibility

- New-card interval preview returns exactly eight rows for both retention columns,
  including fractional same-day follow-ups.
- A slower obsolete preview response cannot overwrite a newer selection.
- Scheduling exports preserve `fsrs_params_7`, `s_int`, and `s_fast`; exports without
  scheduling strip FSRS state as before.
- Old 17/19/21 presets load unchanged; old card data without the new optional keys loads
  without corruption.
- Python/protobuf conversions expose optional new fields without breaking callers that
  only read `stability` and `difficulty`.

The initial port was verified on 2026-09-11 with:

- `just test-rust`;
- `just test-ts`;
- `just test-py` (111 Qt tests plus the pylib/tool suites);
- `just lint`;
- the project-required `just check`; and
- `just test-e2e` (27/27 Playwright tests on the clean rerun).

The first end-to-end run passed 26/27 and timed out in the pre-existing add-editor
context-switch test when a search input intercepted a click during headless Qt GPU
context-loss warnings. An unchanged full rerun passed 27/27. The targeted Rust coverage
is the primary verification for exact queue interleaving, limits, search sorting, full
state math, and fractional same-day behavior; the TypeScript fixture verifies the
FSRS7 Card Info mixture curve.

Additional pre-merge hardening that is not yet represented by a dedicated isolated
test on this branch: stale-response suppression in the Svelte interval table, Qt
rendering of each multi-progress status, and a large-collection benchmark for the
operation-scoped `prop:r` table. Scheduled APKG destination-preset compatibility is
now implemented and covered by a replay/undo regression with source presets omitted.
Both FSRS7 and explicitly retained FSRS6 destination presets are covered.
Cross-client preservation of the optional state fields
also requires AnkiMobile/AnkiDroid coordination outside this repository.

The 2026-09-24 follow-up adds automatic empty-preset migration and pins fsrs-rs
`c137ee6`. Its Rust regression coverage includes fractional-history replay, missing
history, suspended/filtered cards, unchanged due dates and intervals, explicit legacy
presets, idempotent reopening, rollback/retry, server/validation bypass, APKG stripping
and import defaults, full-download reopening, and normal-sync migration followed by
upload. A server-clock-ahead regression was observed failing before the migration
timestamp safeguard was added. The full `just check` passes with the new dependency
and migration, including Rust tests, Python/Qt tests, TypeScript tests, lint, formatting,
and dependency/license checks. `just test-e2e` also passed all 27 Playwright tests on
the first follow-up run (headless Qt still emits GPU context warnings).

The additional requested port work adds regression coverage for fractional review
elapsed time, configured-step precedence, all four model-generated intraday answers,
passing-review lapse counts, exhausted review limits with normal/ascending/descending
order, and newly due cards joining the R-sorted queue without replacing the displayed
question. State tests cover missing/null internal traces, no-history S90 calibration,
fractional-history replay, truncated-history difficulty calibration, legacy API
writes/undo, and imports into different FSRS7 or retained FSRS6 destination presets.
Rust and Python batch API tests cover optional metrics/fields, missing IDs, duplicate
order, raw field values, and read-only behavior. The adapted browser test covers empty
parameter evaluation, first optimization, and the interval table without selectors.

The browser suite passed **28/28** on the clean follow-up rerun. Its first run passed
27/28 and hit the previously observed unrelated editor context-switch click timeout;
the new FSRS test passed on both runs. The release benchmark passed through 100k cards;
measurements and their limits are recorded in the queue section above.
The final `just check` passed after the destination-model and deterministic undo
fixes, including Rust/Python/Qt/TypeScript checks, lint, formatting, and dependency
checks. No user collection was used for verification. Changes remain uncommitted in
the upstream-port worktree; this follow-up did not modify the fork's source checkout.

## Features intentionally excluded from the minimal production PR

- user-visible FSRS4/5/6/7 selector and all per-version parameter tabs;
- “include same-day reviews” switch or comparison workflow;
- scheduling-penalty switch (FSRS7 always uses the penalty model);
- custom single-decay table for FSRS7;
- Dynamic Desired Retention and calibration UI;
- RWKV models, queues, searches, stats, caches, and reviewer hooks;
- add-on FSRS preset overlays and batch optimization public APIs;
- custom minimum FSRS interval, learning-queue bypass, review-fuzz controls, and load
  balancer changes that are not already upstream;
- fork update/release/portable-build infrastructure; and
- compatibility with the fork's temporary numbered protobuf fields or
  `jschoreels.fsrs` storage, unless upstream explicitly decides to support migration
  from fork-created collections.

## Product decisions / uncertain features

These are the items where the fork contains behavior but the requested production
scope does not fully determine the long-term product answer. The implementation choice
made on this branch is stated explicitly so none are accidentally implied to be part of
the PR:

1. **Runtime FSRS-generated same-day intervals after configured steps.** **Resolved:
   included.** Configured steps take precedence; afterward FSRS7 may emit second-based
   learning/relearning intervals. Fractional elapsed input applies to all FSRS7
   reviews, not only learning. The fork's queue-bypass/minimum controls stay excluded.
   No special daily-limit bypass is needed with upstream's learning queues.
2. **Automatic upgrade of existing presets.** **Resolved by the requested change:**
   empty presets upgrade to FSRS7 atomically with their card states. Explicit
   17/19/21-value presets remain on their selected model until Reset/Optimize.
   Due dates are preserved. A dedicated Qt startup progress dialog remains UI work;
   backend progress, transactional retry, and sync handling are included.
3. **Cross-client rollout.** Older clients may preserve, ignore, or overwrite the new
   JSON/protobuf state fields. Exact behavior after an older client edits a card needs
   verification with AnkiMobile/AnkiDroid and may require coordinated releases or lazy
   reconstruction. **Branch choice: optional fields and legacy fallbacks are preserved;
   coordinated mobile validation remains outside this repository change.**
4. **Optimize All failure atomicity.** Upstream currently continues after a preset
   failure. Recommendation: retain best-effort behavior for minimality, keep old params
   for failed presets, and show the failures. An all-or-nothing policy would be a
   separate behavior change. **Branch choice: best-effort.**
5. **`prop:r` large-collection cost.** A full operation-local metric table is correct
   but O(collection). Recommendation: use batch reads/inserts in the first PR and add a
   benchmark; more sophisticated predicate prefiltering can follow if necessary.
   **Branch choice: batched operation-scoped table; a large-collection benchmark remains
   advisable before merge.**
6. **Public add-on metrics API.** **Resolved: include read-only batch card-details and
   FSRS-metrics endpoints adapted from `127842766`.** Card IDs let Anki resolve the
   complete state/current home preset; code and PR rationale explain why this avoids
   add-on scalar-math errors and repeated historical Card Info computation. No new
   public scalar API, preset overlays, or batch optimizer is added.
7. **35-value preview parameter migration.** Upstream has never stored the fork's
   preview layout. Recommendation: reject it as invalid unless official migration from
   the fork is an explicit goal. **Branch choice: no fork-format migration.**
8. **New-card table wording.** The requested title says “at graduation”, but four rows
   can show learning intervals and the arrow rows are two-answer paths. Product should
   confirm whether to retain the title, rename it, or add explanatory text. **Branch
   choice: retain the requested title.**
9. **Frontend vs backend forgetting-curve generation.** Mirroring the complete formula
   in TypeScript is the smallest patch; backend-sampled curves remove formula drift but
   enlarge the IPC. Recommendation: TypeScript plus fsrs-rs parity fixtures for this PR.
   **Branch choice: TypeScript formula with a parity fixture.**

## Overall recommendation

Treat the PR as an FSRS state-and-math boundary change, not a parameter-count update.
The smallest safe implementation keeps model selection implicit, preserves S90 for
existing user-facing semantics, reconstructs the two internal traces from history, and
introduces one full-state/preset-aware metric layer used everywhere. Build the requested
Optimize All and interval-table UX on top of that boundary. Anything that still accepts
only a scalar stability, reads a single decay for 34 parameters, or lets SQL apply a
limit before exact R calculation should block the PR from being called complete.
