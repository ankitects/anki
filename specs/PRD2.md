# Rust Change Spec — Mastery-by-Tag Query

Expands PRD §5.2. This is the one required engine change for Wednesday. It is **read-only**, **generic**, and touches as few upstream files as possible.

> **Revised against the checked-out `main` source.** All file paths, function names, and service conventions below were verified against the codebase (citations are `file:line`). Where the original draft guessed, the guess has been corrected. The headline: the change is _lower-risk than first written_ — Anki already computes FSRS retrievability and already has the tag/card-note plumbing — but several first-draft assumptions were wrong (FSRS is **not** on by default; the trait impl lives in `service.rs` not `mod.rs`; build with `just check`, never `./check`/`./ninja`; there is **no** iOS FFI target in this repo).

---

## 1. Goal

Add a backend call that returns, **per tag group**, the number of mastered cards, the total cards, and the average recall (FSRS retrievability), fast enough to power a dashboard on a 50,000-card collection.

It does **not** know what the MCAT is. It groups by Anki tags. The app layer maps MileDown's `::`-hierarchical tags onto AAMC sections for display. This keeps the engine change generic and minimal.

## 2. Scope & non-goals

**In scope (the whole change):**

- One new RPC + its request/response messages on the **existing `StatsService`**.
- One Rust implementation module (a single read-only pass over searched cards).
- 3 Rust unit tests + 1 Python test.
- Proof that undo and collection integrity are untouched.

**Explicit non-goals (do not touch these):**

- The scheduler. No changes to FSRS scheduling, intervals, or queue order.
- Any write path. The query creates no `OpChanges` **and must not write to the DB at all** (see §8 — "no `OpChanges`" and "no write" are _independent_ properties in this codebase).
- The AAMC taxonomy. That lives in the app layer, not the engine.
- A new proto **service**. Extend `StatsService`; leave the empty `BackendStatsService {}` marker untouched (§4).
- TypeScript hand-coding. Codegen creates the `@generated/backend` client stub automatically.

## 3. Why this belongs in Rust (for your one-page write-up)

It is a hot aggregation over up to 50k cards that reads each card's FSRS memory state and its note's tags. In Python this means pulling every card+note across the bridge and looping in interpreted code — slow, and it crosses the Python/Rust boundary per card. In Rust it is a single in-process pass over the SQLite-backed data returning one compact aggregated message. It also reuses the **already-registered SQLite scalar `extract_fsrs_retrievability(...)`** (`rslib/src/storage/sqlite.rs:314`), so the recall math is the engine's own, computed in SQL.

> **iOS note (corrected).** The first draft said this "ships to iOS via FFI for free." That is **not** true today: there is no iOS build target, xcframework, C ABI, or Swift in this repo — the only cross-language binding is the PyO3 `cdylib` for Python (`pylib/rsbridge/Cargo.toml`). The honest framing: the logic is target-agnostic generic Rust in `rslib`, so _once an iOS FFI bridge exists_ (see PRD1 §5.7 — net-new work) this RPC needs no extra FFI code, because it is just another `(service, method)` index over the existing protobuf-bytes dispatch (`Backend::run_service_method`, generated at `rslib/rust_interface.rs:141`). Do **not** add an "build for the iOS FFI target" verification step (§10) — there is nothing to build against yet.

## 4. Interface

Add the RPC to the **existing `StatsService`** in `proto/anki/stats.proto` (the service block at lines 13–19). Anki uses a **dual-service convention**: `StatsService` lists the full RPC set, and the empty marker `service BackendStatsService {}` (line 23, with the comment "Implicitly includes any of the above methods that are not listed in the backend service") auto-delegates every `StatsService` method to the `Backend` — which is what exposes it to Python via `col._backend`.

> ⚠️ **The emptiness of `BackendStatsService {}` is load-bearing.** Add `TagMastery` only to `StatsService`. Do **not** list it under `BackendStatsService`, and do **not** delete that empty marker — doing either would change how the method is delegated (verified in `rslib/proto_gen/src/lib.rs:46-80`, `rslib/proto/python.rs:18-35`).

```proto
// proto/anki/stats.proto — inside the existing `service StatsService { ... }` block:
rpc TagMastery(TagMasteryRequest) returns (TagMasteryResponse);

message TagMasteryRequest {
  // Group by the first N components of each `::` tag hierarchy.
  // 1 = top-level tag (good default for AAMC sections). 0 = whole tag.
  uint32 group_depth = 1;
  // Retrievability at/above which a card counts as "mastered".
  // 0 means "use the server default" (see §5).
  double mastered_threshold = 2;
  // Optional Anki search to scope the query (e.g. "deck:MileDown").
  // Empty = whole collection. Reuses the Graphs idiom (§5).
  string search = 3;
}

message TagMasteryResponse {
  // Echo the effective threshold actually used (honesty per §11.1) AND ensure
  // the response has >1 field — see the codegen caveat below.
  double threshold_used = 1;
  // Collection-wide give-up-rule inputs (PRD1 §5.4), computed in the same pass.
  uint32 total_graded_reviews = 2;
  uint32 topics_with_reviews = 3;
  // Overall memory-readiness band (PRD1 §5.4 — LOCKED as a 90% CI on mean recall).
  double overall_mean_recall = 5; // mean current retrievability over scored cards
  double overall_ci_low = 6;      // 90% CI lower bound (mean - 1.645*SD/sqrt(n))
  double overall_ci_high = 7;     // 90% CI upper bound (clamped to [0,1])
  uint32 overall_n = 8;           // cards_with_state used for the band (the "how-sure" base)
  message Group {
    string tag = 1;              // the tag (or tag prefix) this row aggregates
    uint32 total_cards = 2;      // cards mapped into this group
    uint32 cards_with_state = 3; // cards that had FSRS memory state (honest denominator)
    uint32 mastered_cards = 4;   // cards with current retrievability >= threshold
    double average_recall = 5;   // mean current retrievability over cards_with_state
    uint32 graded_reviews = 6;   // graded reviews on this group's cards (give-up rule)
  }
  repeated Group groups = 4;
}
```

> 🧩 **Single-field-output codegen caveat (was missing from the draft).** Anki's Python codegen collapses a response that has exactly **one non-enum field**: `col._backend.<rpc>(req)` then returns _that field_, not the message (`rslib/proto/python.rs:150-162`). The first draft's response had only `repeated Group groups`, so `tag_mastery()` would have returned a `Sequence[Group]`, not a `TagMasteryResponse` — silently breaking the §4 usage and §7 test. The schema above adds `threshold_used`/`total_graded_reviews`/`topics_with_reviews` (all independently useful for honesty + the give-up gate), so the full message is returned.

Python usage after build (no hand-written binding needed). ⚠️ **Input is destructured into kwargs, not passed as a message** — `python.rs:maybe_destructured_input` destructures any `*Request` input (confirmed: the generated `graphs(self, *, search, days)`). The _response_ (8 fields) is returned whole:

```python
# generated signature: tag_mastery(self, *, group_depth, mastered_threshold, search)
resp = col._backend.tag_mastery(group_depth=1, mastered_threshold=0.0, search="")
for g in resp.groups:
    ...  # resp is the full TagMasteryResponse because it has >1 field
```

(Optionally add a thin `col.tag_mastery(...)` wrapper in `pylib/anki/collection.py` for ergonomics, mirroring the existing `card_stats_data` wrapper. Not required for the change to count.)

## 5. Algorithm (single read-only pass)

Model this on the **existing retrievability graph**, `GraphsContext::retrievability()` in `rslib/src/stats/graphs/retrievability.rs:13-60`, and the search-scoped data flow `graph_data_for_search` in `rslib/src/stats/graphs/mod.rs:31-39`. Those already do 90% of this — iterate searched cards, read each card's `memory_state`, compute R via the FSRS helper, and aggregate into a `HashMap`.

1. `threshold = req.mastered_threshold if > 0 else DEFAULT_MASTERED`. **"Mastered" = current/decayed retrievability ≥ threshold** (LOCKED). The threshold _value_ is **TBD** (placeholder `0.9`) — keep it tunable and always echo it back as `threshold_used`. Note this is the engine's _current_ retrievability (recall right now), not relative/desired-retention.
2. Fetch timing **once**: `let timing = self.timing_today()?` (`rslib/src/scheduler/mod.rs:50` → `SchedTimingToday { days_elapsed, next_day_at, now }`, `rslib/src/scheduler/timing.rs:13-19`). These three are **collection-wide scalars, not per-card columns** — the first draft implied they were per-card.
3. Get the card set (read-only). Reuse the Graphs idiom: `self.search_cards_into_table(&req.search, SortMode::NoOrder)?` then `self.storage.all_searched_cards()?` (`rslib/src/search/mod.rs:226`, `rslib/src/storage/card/mod.rs:594`); empty `search` = whole collection. The temp table is session-local and is **not** a collection write. (Alternative minimal path: one direct `SELECT … FROM cards c JOIN notes n ON c.nid = n.id` — see §6. Either way, **tags are on the note, not the card** — `all_searched_cards()`/`get_card.sql` load no tags, so you must also fetch `notes.tags`.)
4. Compute recall per card. Two equivalent in-engine options — pick one and be consistent with the test:
   - **Rust (mirrors the graph, recommended):** `FSRS::new(None).unwrap().current_retrievability_seconds(state.into(), card.seconds_since_last_review(&timing).unwrap_or_default(), card.decay.unwrap_or(fsrs::FSRS5_DEFAULT_DECAY))` — exactly `retrievability.rs:34-38`. `card.memory_state: Option<FsrsMemoryState>` is already loaded by the card loader.
   - **SQL:** call the registered scalar `extract_fsrs_retrievability(c.data, case when c.odue!=0 then c.odue else c.due end, c.ivl, {days_elapsed}, {next_day_at}, {now})` inline, formatting the three timing scalars as literals (pattern: `rslib/src/search/sqlwriter.rs:441`). Pass `next_day_at` positionally even though this scalar ignores it (6-arg arity assert, `sqlite.rs:317`).
   - **Do not re-derive the forgetting curve by hand** — both helpers already handle the three elapsed-time regimes (`sqlite.rs:334-359`).
5. For each card:acti
   - Split the note's tags with `crate::tags::split_tags(tags)` (`rslib/src/tags/mod.rs:42`) — **not** a hand-rolled `split(' ')`. The `notes.tags` column is stored in `join_tags` format (`" tag1 tag2 "`, leading/trailing spaces) and the separator set includes U+3000 (ideographic space); `split_tags` handles this. (`split_tags` is `pub(crate)` in the `tags` module — re-export it or call `crate::tags::split_tags`.)
   - For each tag, take the first `group_depth` `::` components for the group key: `tag.split("::").take(group_depth as usize).collect::<Vec<_>>().join("::")` (whole tag if `group_depth == 0`). **No first-N helper exists** — do **not** use `immediate_parent_name_*` (`mod.rs:58,62`); those `rsplit` and drop the _last_ component. Cards with no tags → group `"(untagged)"` (a sentinel you define; safe because real tags can't be empty or contain spaces).
   - A card with multiple tag groups is counted under **each** group it belongs to. Document this: per-group totals can sum to more than the card count, and `average_recall` is therefore the mean over **(card, group) memberships**, not unique cards — correct for "mastery per topic."
   - `total_cards += 1` for each group.
   - **Membership in recall/mastery is gated solely on `R` being present (`memory_state.is_some()` / non-NULL).** Drop the first draft's separate "is it a review card?" queue/type check — it is redundant and risks excluding valid relearning cards. `memory_state()` is `Some` only when both FSRS stability+difficulty are present (`rslib/src/storage/card/data.rs:81-91`), which is exactly "has FSRS state." When present: `cards_with_state += 1`; `sum_R += R`; if `R >= threshold`, `mastered_cards += 1`.
6. `average_recall = sum_R / cards_with_state` per group (guard divide-by-zero → 0, matches `retrievability.rs:51`).
7. **Overall memory-readiness band (PRD1 §5.4 — LOCKED as a 90% CI).** Treat each scored card's current retrievability as a sample of a recall probability. Accumulate, over **unique** cards with state (collection-wide, not per-group, so multi-tag cards aren't double-counted here): `n`, `sum_R`, `sum_R2` (sum of squares). Then `mean = sum_R/n`; `var = max(0, sum_R2/n − mean²)`; `sd = sqrt(var)`; **90% CI** = `mean ± 1.645·sd/sqrt(n)`, clamped to `[0,1]`. Return `overall_mean_recall`, `overall_ci_low`, `overall_ci_high`, `overall_n`. (`n` is the "how-sure" base; a wide interval / small `n` = low confidence.) Guard `n==0` → all zeros (the honest abstain state). Normal approximation is fine for the MVP; note the small-`n` caveat (a Wilson/bootstrap interval is a later upgrade).
8. Give-up-rule counters (PRD1 §5.4), same pass: count graded reviews from the `revlog` table — rows with `button_chosen > 0`, excluding manual/cram/reschedule per `has_rating_and_affects_scheduling()` (`rslib/src/revlog/mod.rs:126`). Surface `total_graded_reviews` and per-group `graded_reviews`, and `topics_with_reviews` (distinct groups with ≥1 graded review). This lets the dashboard _and_ the abstain gate read from one call.
9. Sort groups by tag for deterministic output.

**Memory-model setup (LOCKED — always enable FSRS):** FSRS is **not** on by default in the engine — `BoolKey::Fsrs` defaults to `false` (`rslib/src/config/bool.rs:74`; the default-true list at lines 61-71 does _not_ include it). **Decision: the fork always enables FSRS.** Collection setup must `col.set_config_bool(BoolKey::Fsrs, true, …)` **and** compute memory state (review the cards or run FSRS optimize → `update_memory_state`, `rslib/src/scheduler/fsrs/memory_state.rs:81`) — enabling the flag alone does not backfill memory state. Brand-new never-reviewed cards still legitimately have no state (`R` is NULL → excluded from `cards_with_state`/recall), which is the **correct, honest "not enough data yet"** surface (PRD1 §5.4), not a bug. Without the enable+compute step the dashboard shows all-zeros on the grader's clean machine — so make it part of the demo/setup script (PRD1 §0.5).

## 6. Files touched (the entire footprint)

1. `proto/anki/stats.proto` — add the RPC inside the existing `service StatsService { … }` block (lines 13-19), plus the two messages near the existing `GraphsResponse` messages. **Leave `service BackendStatsService {}` (line 23) empty and untouched.** _(small, required)_
2. `rslib/src/stats/mod.rs` — add `mod tag_mastery;` to the existing module-declaration block (`mod card; mod graphs; mod service; mod today;`, lines 4-7). _(one line)_
3. `rslib/src/stats/service.rs` — add the trait method to the existing `impl crate::services::StatsService for Collection` block (lines 7-39), delegating to the new module. Signature must match the generated trait exactly:
   ```rust
   fn tag_mastery(
       &mut self,
       input: anki_proto::stats::TagMasteryRequest,
   ) -> error::Result<anki_proto::stats::TagMasteryResponse> {
       self.tag_mastery(input)
   }
   ```
   _(a few lines — this is the file the compiler flags with E0046 until implemented, **not** `mod.rs` as the draft said)_
4. `rslib/src/stats/tag_mastery.rs` — **new file**: the `Collection::tag_mastery(...)` impl, the query + aggregation, and the 3 Rust unit tests. _(isolated bulk of the change)_
5. `pylib/tests/test_tag_mastery.py` — **new file**: the 1 Python test (use `getEmptyCol()` from `tests.shared`, mirror `pylib/tests/test_stats.py`).

> The generated `StatsService` trait lives at `$OUT_DIR/backend.rs` (included via `rslib/src/services.rs:9`). It is regenerated from the proto on build — never hand-edit it. The only shared lines you edit are the proto service block and one `mod`/trait method — which is what keeps a future upstream merge low-risk.

## 7. Tests

**Rust (≥3, in `tag_mastery.rs`).** Build fixtures with the real helpers (`rslib/src/tests.rs`): `Collection::new()` (in-memory), `NoteAdder::basic(&mut col).fields(&["q","a"]).add(&mut col)`, and set `note.tags` _before_ `.add()` for tagged notes. There is **no** `open_test_collection`/`new_v1_test` and **no** high-level "set memory state" API — set it directly on the card (the pattern at `memory_state.rs:699-706`): `card.ctype = CardType::Review; card.interval = 1; card.memory_state = Some(FsrsMemoryState{stability, difficulty}); card.decay = Some(...);` then `col.storage.update_card(&card)?`. (Memory state lives in the card `data` blob, so a card-only fixture with no revlog suffices for recall tests.)

1. **Empty / no-state:** new (no-state) cards → every group reports `mastered_cards == 0`, `average_recall == 0`, `cards_with_state == 0`, while `total_cards` is correct. (This is also the real "FSRS off / fresh import" case.)
2. **Aggregation correctness:** known cards across two tags with known memory states → assert exact `total_cards`, `cards_with_state`, `mastered_cards`, and `average_recall` per group. Derive the expected `R` from the **same** helper you implement with (the `extract_fsrs_retrievability` scalar or `current_retrievability_seconds`) — do not hand-write a formula. Pin `now`/`days_elapsed` deterministically. If the fixture has multi-tag cards, account for the shared card contributing to each group's `sum_R`/`cards_with_state`.
3. **Tag handling:** a two-tag card is counted in both groups; an untagged card lands in `"(untagged)"`; `group_depth` collapses `a::b::c` to the right prefix.
4. _(optional 4th)_ **Give-up counters / determinism:** assert `total_graded_reviews`/`topics_with_reviews` against a fixture with a known revlog, and that output order is stable.

**Python (1, end-to-end):** open a fresh test collection, add notes with known tags, set memory state if asserting recall, call `col._backend.tag_mastery(TagMasteryRequest(...))`, and assert the returned `TagMasteryResponse` structure. Proves the proto → Rust → Python path works.

## 8. Integrity proofs (required by the brief)

Read-only here means **two independent properties** — the engine enforces neither automatically, so prove both:

- **No `OpChanges` / no undo entry:** `OpChanges` and undo steps are produced _only_ inside `Collection::transact` (`rslib/src/collection/transact.rs:8-55`, `rslib/src/undo/mod.rs:72-102`). A method that never calls `transact()` produces neither. **Test (strengthened):** first perform a _real_ undoable op (e.g. `NoteAdder…add` or a `transact(Op::UpdateCard, …)`) so `col.can_undo()` is `Some` and `col.undo_status().last_step > 0`; then run `tag_mastery`; then assert `can_undo()`, `can_redo()`, and `undo_status().last_step` are unchanged. (Asserting an _empty_ stack stays empty is a weak test. "Byte-for-byte" isn't literally testable — `UndoStatus` isn't `Eq`.)
- **No DB write at all:** ⚠️ skipping `transact()` does **not** make a method read-only — `card_stats` (the RPC the draft proposed copying) actually **writes** via `self.storage.update_card(...)` outside any transaction (`rslib/src/stats/card.rs:45`), persisting silently via autocommit with no `OpChanges` and no mtime bump. So **do not copy that backfill pattern**; use only read/query accessors, never `storage.update_*/add_*/remove_*` or `set_modified`. **Test:** snapshot `col.storage.get_collection_timestamps()?` (mod/scm, `rslib/src/storage/collection_timestamps.rs:11`) **before** the call and assert unchanged **immediately after** — _before_ any integrity check.
- **No corruption:** run `check_database` **last** and assert clean (Rust: `assert_eq!(col.check_database()?, CheckDatabaseOutput::default())`, pattern at `rslib/src/dbcheck.rs:506`; Python: `col.fix_integrity()[1] is True`, `pylib/anki/collection.py:1093`). ⚠️ **Order matters:** `check_database` is itself a _mutating_ `transact_no_undo` op (re-derives tags, can bump `scm`, `rslib/src/dbcheck.rs:126,156,245`). Asserting mod/scm unchanged _after_ it would spuriously fail. Sequence: snapshot timestamps → `tag_mastery` → assert timestamps unchanged → **then** `check_database`.

These are easy to satisfy _because_ the change is read-only. If you ever feel tempted to write during this query, stop — that's a different (riskier) feature.

## 9. Performance

- One pass, O(cards). One DB read to gather rows, aggregate in memory, return one message — same shape as the existing retrievability graph (`retrievability.rs:31-52`).
- No per-card bridge crossings (the whole point of doing it in Rust).
- Validate informally on a 50k-card collection (combine MileDown with another deck, or multiply cards) so the dashboard load is comfortably interactive. Formal benchmarking is a later deadline, not Wednesday.

## 10. Build / verify checklist

- **Use `just` recipes — never `./check` or `./ninja` directly** (project `CLAUDE.md` forbids it). The first draft's `./check (or ./ninja)` is wrong for this repo.
- Editing a `.proto` requires a full build: run **`just check`** (`justfile:34` → format + build + lint + test) so codegen regenerates the Rust trait **and** the Python (`out/pylib/anki/_backend_generated.py`) and TS (`out/ts/lib/generated/backend.ts`) stubs _and_ restages them into the runnable package. Nuance: bare `cargo check` regenerates the Rust trait and the stub _files_ (enough for the Rust contract to compile), but the **Python end-to-end** path (`col._backend.tag_mastery`) needs the ninja-driven build — `just run` / `just wheels` / `just check` — to restage `_backend_generated.py` into the `anki` package.
- After implementing, `rslib/src/stats/service.rs` won't compile until the new trait method is added — that's codegen enforcing the contract (E0046). Good signal.
- Run `just check` as the final step.
- For fast iteration: `cargo check` (Rust), `just test-rust` / `just test-py`, `just lint`.
- **Removed:** the "build for the iOS FFI target" step — there is no iOS target in this repo (see §3).

## 11. Decisions to lock before coding

**Locked this round:** "mastered" = **current/decayed retrievability ≥ threshold** (value TBD, placeholder `0.9`, echoed as `threshold_used`). FSRS **always enabled** (§5). Readiness band = **90% CI** on mean recall (§5 step 7). iOS **deferred** (PRD1 §0) — no FFI work here.

Still open:

1. **Mastery threshold value** — the cutoff on current retrievability ("define later"). Tunable; doesn't affect the readiness band, only the per-group `mastered_cards` count.
2. **Default `group_depth`** (proposed `1` = top-level tag). **Inspect the real MileDown `.apkg` first** — `group_depth=1` only maps cleanly to AAMC sections if the deck is single-rooted and case-consistent; Anki canonifies tag case to first-seen, so a static AAMC map must be case-insensitive and must surface unmapped/`(untagged)` groups rather than drop them.
3. **Multi-tag counting rule:** count a card under every matching group (proposed). Group totals can exceed the card count; per-group `average_recall` is a per-(card, group) mean. (The §5-step-7 readiness band dedups to unique cards, so the overall number is _not_ double-counted.)
4. **Per-card vs per-note unit:** MileDown notes with multiple templates produce sibling cards sharing tags; per-card counts inflate vs a note-level mental model. The existing graph reports both `sum_by_card` and `sum_by_note`. Proposed: per-card, stated explicitly.
5. **Scope field:** include the optional `search` field (proposed) so the dashboard can scope to a deck, reusing the Graphs idiom. If never needed, drop it for the minimal direct-JOIN path.
6. **Recall source of truth:** SQL scalar vs Rust helper — pick one and use it in both impl and test to avoid off-by-epsilon failures.
7. **Home service:** confirmed — `StatsService` (service id 43), with `BackendStatsService {}` left empty.

## 12. Quick implementation map (grounded in current source)

| Concern           | Where                                                                     | Note                                                     |
| ----------------- | ------------------------------------------------------------------------- | -------------------------------------------------------- |
| Add RPC           | `proto/anki/stats.proto:13` (`service StatsService`)                      | leave `BackendStatsService {}` (`:23`) empty             |
| Module decl       | `rslib/src/stats/mod.rs:4-7`                                              | add `mod tag_mastery;`                                   |
| Trait impl        | `rslib/src/stats/service.rs:7`                                            | `impl crate::services::StatsService for Collection`      |
| New impl + tests  | `rslib/src/stats/tag_mastery.rs` (new)                                    | mirror `graphs/retrievability.rs`                        |
| Pattern to mirror | `rslib/src/stats/graphs/retrievability.rs:13-60`, `graphs/mod.rs:31-39`   | iterate searched cards, aggregate                        |
| Recall (SQL)      | `extract_fsrs_retrievability` `rslib/src/storage/sqlite.rs:314`           | 6 args, returns float or null                            |
| Recall (Rust)     | `FSRS::current_retrievability_seconds` (`fsrs` crate)                     | `card.memory_state`, `card.decay`, `FSRS5_DEFAULT_DECAY` |
| Timing            | `self.timing_today()` `rslib/src/scheduler/mod.rs:50`                     | 3 collection-wide scalars                                |
| Tag split         | `crate::tags::split_tags` `rslib/src/tags/mod.rs:42`                      | handles join_tags format + U+3000                        |
| Cards↔notes       | `cards.nid = notes.id` (`ix_cards_nid`, `schema11.sql:72`)                | Card struct has no tags field                            |
| FSRS toggle       | `BoolKey::Fsrs` `rslib/src/config/bool.rs:74`                             | **defaults false** — enable + seed                       |
| Undo/integrity    | `transact.rs`, `undo/mod.rs`, `dbcheck.rs`, `collection_timestamps.rs:11` | see §8 ordering                                          |
| Test helpers      | `rslib/src/tests.rs` (`Collection::new`, `NoteAdder`)                     | set `note.tags` before `.add()`                          |
| Build             | `just check` / `just run` / `just test-rust`                              | never `./check`/`./ninja`                                |
