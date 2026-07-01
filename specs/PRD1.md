# PRD — Speedrun MCAT (Wednesday MVP)

**Working title:** Speedrun MCAT (rename freely)
**Exam:** MCAT — scored 472–528, four sections each 118–132. Large fact base + reading passages; the hard part is _covering all of it_.
**License:** AGPL-3.0-or-later, fork of Anki, credit to Anki (some Anki components are BSD-3-Clause).
**Scope of this document:** The **Wednesday submission (the MVP) only.** Friday, Sunday, and stretch work are summarized in §10 so the through-line is visible, but they are **not** Wednesday deliverables. Do not build them this round.

---

## 0. Key decisions (recommended defaults — confirm or flip before coding)

| Decision           | Choice for MVP                                                                        | Why                                                                                                                                                                                                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Rust engine change | **Mastery query** (per-topic mastery + avg recall)                                    | Read-only, doesn't touch the scheduler → lowest risk to undo/collection integrity, which must be _proven_. Directly powers the dashboard and seeds the later coverage map.                                                                                                       |
| Mobile platform    | **Deferred to post-Wednesday** (was iOS)                                              | **Decision: desktop-first.** iOS is 100% greenfield here (no target/C-ABI/Swift — §5.7) and would dwarf the rest. We knowingly **accept the rubric's 70% cap for Wednesday** and build iOS later (§10). The mastery query stays generic Rust so it ports cleanly when iOS lands. |
| Memory model       | **Always FSRS, surfaced honestly** (point + 90% CI + give-up rule + topic breakdown)  | **Decision: the fork always enables FSRS.** FSRS already estimates memory well; Wednesday is honest _display_, not a new model.                                                                                                                                                  |
| MCAT deck          | **MileDown** (~2,900 cards, sub-topic hierarchical tags, AAMC-aligned, plain `.apkg`) | Hierarchical `::` tags map onto the AAMC outline (feeds the mastery query for free); concise enough to build/iterate/demo fast; no platform dependency.                                                                                                                          |

These are reversible, but the deck and mobile path are now locked unless you say otherwise.

**Decisions locked this round:**

- **Desktop-first; iOS deferred to post-Wednesday** (accepts the 70% cap for now).
- **Always enable FSRS** on the fork (collection setup forces `BoolKey::Fsrs = true` + computes memory state).
- **"Mastered" = current/decayed retrievability ≥ threshold** (recall right now); exact threshold value TBD.
- **Memory-readiness range = a 90% confidence interval** computed statistically over the per-card recall probabilities; "how-sure" derives from that interval's width / sample size.

---

## 0.5 Codebase-grounded corrections (read before building)

This PRD was first written without reading the Anki source. After verifying every concrete claim against the checked-out `main` (citations are `file:line`), these need fixing — the product vision holds, but some assumptions don't:

1. **FSRS is NOT on by default — so the fork forces it on.** `BoolKey::Fsrs` defaults to `false` (`rslib/src/config/bool.rs:74`); a fresh collection / freshly imported `.apkg` carries no FSRS memory state until enabled. **Decision: always enable FSRS.** Collection setup must `set_config_bool(BoolKey::Fsrs, true, …)` **and** compute memory state for reviewed cards (FSRS optimize / `update_memory_state`) so retrievability is populated. Brand-new never-reviewed cards still legitimately have no state (they aren't learned yet) — that's the honest "not enough data" path, not a bug. Without this the demo shows all-zeros on the grader's clean machine, risking the "fails on a clean device → 50%" cap. ✅ **Implemented:** `AnkiQt._ensure_fsrs_enabled()` in `qt/aqt/main.py`, called on every `loadCollection`, sets config key `fsrs=true` if unset (memory state then fills in as cards are reviewed / on FSRS optimize).
2. **iOS is deferred (per the desktop-first decision).** It is entirely net-new here — no `aarch64-apple-ios` target, no xcframework/uniffi/cbindgen, no Swift app, no C ABI (only a PyO3 `cdylib` for Python); AnkiMobile is closed-source and not in this repo. The full technical plan is kept in §5.7 but is **post-Wednesday roadmap** (§10), not an MVP gate. We accept the 70% rubric cap for this round.
3. **"Likely range" and "how-sure" have no engine source — so we define them statistically.** FSRS gives a single point retrievability per card (no confidence interval). **Decision (§5.4): readiness range = a 90% confidence interval** on the mean recall probability across cards, computed in the new Rust query; "how-sure" is derived from that interval's width / sample size. This is grounded in the data we already have, not fabricated.
4. **Build with `just`, never `./check`/`./ninja`** (project `CLAUDE.md`). There is no local `just installer` recipe today (§5.6).
5. **Two silent wiring steps** for any new dashboard page: register it in `qt/aqt/mediasrv.py` `is_sveltekit_page()` (`:413`) or it 404s, and add the RPC's snake_case name to `exposed_backend_list` (`:726`) or the POST is rejected. Both fail at runtime, not build time.

What checks out cleanly: the Rust mastery query (read-only, reuses existing retrievability + tag plumbing), `.apkg` import is a stock RPC, hierarchical `::` tags are native (no add-on), the reviewer is stock, and the AGPL-3.0/BSD-3 license framing is correct (`LICENSE`).

---

## 1. Problem & product thesis

A flashcard app answers one question: _can the student recall this fact right now?_ An exam asks for more — apply facts to unseen questions, work fast enough to finish, and know whether you're actually ready. The core trap: recalling "mitochondria is the powerhouse of the cell" is not the same as answering an MCAT passage on cellular respiration.

The product's job is to **measure the gap between memory and readiness and never hide it.** Anki/FSRS handles memory. The two harder bridges (memory → answering new questions, and questions → a real score) come later. The MVP builds the foundation honestly so those bridges have something to stand on.

**The honesty rule (binding from day one):** the app may not display a readiness/memory score unless it can also show what evidence produced it, what data is still missing, the likely _range_ (not a single number), a "how sure" indicator, when it was last updated, and the single best next thing to study. A confident number with none of that is a guess in a nice font, and fabricating one is an automatic fail.

---

## 2. Target user

**Primary persona — "Maya," self-studying pre-med.**

- 21, junior, studying for the MCAT over ~4 months, targeting ~510+.
- Uses a large premade deck (~3–10k cards). Studies at a desk in the evenings and on her phone between classes.
- **Pain points (evidence-grounded):**
  - Doesn't know whether memorizing equals being ready. (memory ≠ performance ≠ readiness)
  - Over-trusts high Anki accuracy — near-perfect accuracy can mean near-zero new learning.
  - Drops/marks cards inaccurately due to weak metacognition; students don't reliably know what they know.
  - Treats Anki as the whole plan, when the MCAT is an applied-reasoning exam wrapped around content.
- **Goals:** trust a readiness signal, know what to study next, stop wasting hours on cards she already knows, study seamlessly across desk and phone.

One persona is enough to scope the MVP. Secondary users (advisors, post-bacc students) are out of scope.

---

## 3. What the MVP is (and isn't)

The MVP is the **proof that the hard infrastructure works**, presented honestly. The student can: review their MCAT deck on the desktop, see a memory-readiness band (or an honest "not enough data"), and see per-topic mastery on a simple dashboard. **Desktop only this round — iOS is deferred (§5.7, §10).** That's the entire Wednesday surface area.

### In scope (Wednesday)

1. Anki forked and building from source.
2. One real Rust change (mastery query) end-to-end, with tests, undo intact, no corruption.
3. A working review loop on the MileDown deck (desktop), with **FSRS always enabled**.
4. An honest memory-readiness display: point estimate + **90% confidence interval** + coverage + how-sure + last-updated + reasons + give-up rule.
5. A minimal topic-mastery dashboard reading from the Rust mastery query.
6. A desktop installer that runs on a clean machine.

### Explicitly NOT in scope (Wednesday — see §10 for where they go)

- **iOS / phone companion.** Deferred to post-Wednesday (desktop-first decision, §0). We knowingly accept the rubric's 70% cap for this round. Technical plan retained in §5.7.
- **No AI of any kind** (no model calls, no generated cards, no chatbot). Hard rule for Wednesday.
- Two-way sync.
- Performance model / paraphrase test / score mapping.
- The 5-tab experience, diagnostic trainer, "I never learned this" workflow, answer timer, textbook integration, onboarding tutorial, behavioral nudges ("you marked a lot Easy — sure?").
- Calibration charts, Brier/log-loss reporting (that's Sunday).

---

## 4. User stories (Wednesday)

- As a student, I can open the desktop app and review my MCAT deck so my recall stays fresh.
- As a student, I see a memory-readiness band with a likely range (a 90% confidence interval) — or an honest "not enough data yet" with what's missing — so I'm never misled by a single fake number.
- As a student, I can see per-topic mastery (cards mastered, average recall) on a simple dashboard so I know where I stand.
- As a grader/reviewer, I can install the desktop app on a clean device and watch a review session, the Rust change in action, and the honest score — all with AI off (it's off by definition Wednesday).
- _(Deferred, post-Wednesday)_ As a student, I can review the same deck on my iPhone so I can study between classes.

---

## 5. Functional requirements

### 5.1 Fork & build

- Public AGPL-3.0-or-later fork of Anki with credit to Anki. (License confirmed: `LICENSE` is AGPL-3.0-or-later with per-file BSD-3/MIT/Apache exceptions — see `CONTRIBUTORS`.)
- Builds from source on a clean machine; build steps documented in README. **Build via `just` recipes** (`just run` to build+launch, `just check` for format+build+lint+test, `just wheels`) — **never `./ninja`/`./check`/`./run` directly** (project `CLAUDE.md`).
- README states the exam (MCAT) up front, lists touched upstream files, and includes an architecture overview.
- **Touched upstream files (so far):** `proto/anki/stats.proto`, `rslib/src/stats/{mod.rs,service.rs,tag_mastery.rs}`, plus the new SvelteKit dashboard route and the two `qt/aqt/mediasrv.py` allowlist entries (§5.5). Keep this list current for the README's "upstream files touched / merge difficulty" note.

### 5.2 Rust change — Mastery query

A new backend call that returns, **per topic**, the number of mastered cards and the average recall, fast enough to power the dashboard on a 50,000-card collection. **Full grounded spec: see `specs/PRD2.md`** (revised against source — it reuses the already-registered `extract_fsrs_retrievability` SQL scalar and the existing retrievability-graph pattern, so this is _lower-risk_ than first written).

- "Topic" derives from MileDown's hierarchical (`::`-delimited) tags mapped to AAMC sections/subsections (define the mapping; a simple tag-prefix grouping is acceptable for MVP).
- "Mastered" needs a stated definition (e.g., FSRS retrievability above a threshold and ≥N successful reviews) — write it down; it's tunable.
- New protobuf message for the call; invoked from Python.
- **Tests:** ≥3 Rust unit tests + 1 test that calls it from Python.
- **Integrity:** prove undo still works and the collection does not corrupt (the query is read-only, which makes this straightforward — keep it that way).
- **Docs:** one-page note on why this belongs in Rust not Python; list of upstream files touched and expected future-merge difficulty.
- **Shared-engine note (corrected):** the change is generic Rust in `rslib`, so it is _portable_ to iOS — but it does **not** "ship to iOS for free": there is no iOS FFI bridge in this repo yet (§5.7). Wednesday only _requires_ the phone to run a review session on the shared engine; exercising the custom query through Swift can wait. The RPC reuses the existing `(service, method)` bytes dispatch, so once the FFI bridge exists it needs no extra per-call FFI code — don't architect anything that blocks that.

### 5.3 Review loop (desktop)

- Real review session on the MileDown deck using FSRS scheduling (Again/Hard/Good/Easy).
- No custom scheduling behavior this round — stock FSRS is correct for the MVP.
- **No frontend work required.** The reviewer is the existing `stdHtml`-based card view (`qt/aqt/reviewer.py:345`), separate from the SvelteKit stats/dashboard pages — it's untouched stock Anki. **Prerequisite:** FSRS must be enabled on the collection first (it's off by default — §0.5), otherwise reviews accrue SM-2 state and the mastery/readiness surfaces stay empty.

### 5.4 Memory-readiness display (the honest score)

- Built on FSRS **current/decayed retrievability** ("recall right now") — **do not invent a new memory model.** Each card's retrievability is a recall _probability_ in [0,1]; the existing per-card computation already feeds `GraphsResponse.Retrievability` (`proto/anki/stats.proto:89`, `rslib/src/stats/graphs/retrievability.rs`). With FSRS always on (§0.5), this is populated for reviewed cards.
- Must show: point estimate, likely range, % of exam topics covered so far, a how-sure indicator, last-updated time, the main reasons behind it, and the give-up rule.
- **Range & how-sure (LOCKED — statistical, not fabricated):** treat the per-card current retrievabilities as samples of a recall probability. The **point estimate** = their mean. The **likely range = a 90% confidence interval** on that mean — normal approximation `mean ± 1.645 · SD/√n` over `n = cards_with_state` (note the small-`n` caveat; a Wilson/bootstrap interval is an acceptable upgrade later). The **"how-sure" indicator** is driven by that interval: a wide CI / small `n` reads as low confidence, a tight CI / large `n` as high. Compute all of this in the §5.2 Rust query and return explicit `mean`, `ci_low`, `ci_high`, `n` fields so the Svelte layer just renders them. (The exact "mastered" _threshold_ on top of retrievability is still TBD — see PRD2 §11.1 — but the readiness band itself does not need it.)
- **"Last-updated"** should be a concrete timestamp returned by the query — decide between the collection's day-rollover (`timing_today()`) and the most-recent graded `revlog` entry (they differ; pick one).
- **Give-up rule (write yours; this is a starting line):** _Show no memory-readiness number until the collection has ≥150 graded reviews spanning ≥5 distinct topics. Below that, show "Not enough data yet" and exactly what is missing._ **Compute this server-side in the §5.2 query** (return `total_graded_reviews` + `topics_with_reviews`, or an `enough_data` bool) so the abstain decision is authoritative and unit-testable — graded reviews = `revlog` rows with `button_chosen>0` excluding manual/cram/reschedule (`rslib/src/revlog/mod.rs:126`). One Rust unit test should cover the abstention boundary.
- The display must abstain rather than show a number when the rule isn't met. Reuse the existing empty-state component `ts/routes/graphs/NoDataOverlay.svelte` ("Not enough data yet") and `RangeBox.svelte` for the range control — no new UI primitives needed.

### 5.5 Topic-mastery dashboard (minimal)

> ✅ **Implemented.** SvelteKit route `ts/routes/mastery/+page.svelte` (readiness band with 90% CI + abstain state, per-topic table); strings in `ftl/core/statistics.ftl`; both `qt/aqt/mediasrv.py` allowlists updated (`mastery` page + `tag_mastery` RPC); opened via **Tools ▸ Topic mastery** (`qt/aqt/mastery.py` `MasteryStats`, registered in the dialog manager, menu action in `qt/aqt/main.py`). Verified: svelte/typescript/eslint + mypy/ruff pass.

- Reads from the §5.2 Rust query.
- Shows per-topic mastered-count and average recall, plus overall % topics covered.
- Should load and refresh without freezing the UI on the reference deck. **This is automatic:** the frontend is SvelteKit + Vite; the generated TS client (`tagMastery()` from `@generated/backend`) calls the backend over an async `fetch` POST to the in-process mediasrv (`out/ts/lib/generated/post.ts`), so the UI thread never blocks while Rust runs — no extra threading work.
- **Concrete build path** (model on the existing graphs page):
  - New route `ts/routes/<dashboard-name>/+page.svelte` (copy `ts/routes/graphs/+page.svelte` + `GraphsPage.svelte`); data-loading wrapper modeled on `ts/routes/graphs/WithGraphData.svelte` calling `await tagMastery({...})`. SvelteKit auto-discovers the route on build.
  - **Two manual registration steps that fail silently if missed** (see §0.5): add the page name to `is_sveltekit_page()` (`qt/aqt/mediasrv.py:413`) or it 404s; add the RPC's snake_case name `tag_mastery` to `exposed_backend_list` (`qt/aqt/mediasrv.py:726`) or the POST is rejected as NotFound.
  - New strings go in `ftl/core/statistics.ftl` (kebab-case keys); use via `tr.statisticsTagMastery()` after codegen. Open the page from Qt with `webview.load_sveltekit_page('<dashboard-name>')` (pattern: `qt/aqt/stats.py:141`).
  - Reusable components: `Graph.svelte`/`TitledContainer`, `TableData.svelte` (label/value rows), `CardCounts.svelte`, `RangeBox.svelte`, `NoDataOverlay.svelte`.
- **Open question:** standalone new page (cleaner product, needs the two `mediasrv.py` edits) vs. a new card appended to the existing graphs list (cheaper, couples to the stats screen). Decide before building.

### 5.6 Desktop installer

- Packaged installer that runs on a clean machine.
- **Tooling reality:** Anki uses a **Briefcase**-based installer (`qt/installer/{mac,linux,windows}-template/`, logic in `build/configure/src/installer.rs`). ⚠️ There is **no `just installer` recipe** today, and `CLAUDE.md` forbids calling the underlying `./tools/build-installer` / `./ninja installer` directly. **Decision needed (owner before Wednesday):** either (a) add a thin `just installer` recipe wrapping `./tools/build-installer` (cleanest, keeps the convention — a devops change), or (b) produce the installer via CI (`just release build --ref <branch>` dispatches `release.yml`) and download the `installer-macos`/`installer-windows` artifact for the clean-machine recording. Budget time for first-run Briefcase setup on macOS.

### 5.7 Mobile (iOS / Swift) — DEFERRED (post-Wednesday)

> 🗓️ **Not a Wednesday deliverable.** Per the desktop-first decision (§0), iOS moves to the roadmap (§10). We knowingly accept the rubric's **70% cap** for this round. The plan below is retained so the later build is ready to start and so nothing on the desktop path blocks it.

> ⚠️ **Why it was descoped — 100% greenfield.** Verified against source: this repo has **no** iOS rust target, **no** xcframework/uniffi/cbindgen, **no** Swift app, and **no** C ABI. The only cross-language binding is the PyO3 `cdylib` for Python (`pylib/rsbridge/Cargo.toml`); the `build/ninja_gen` `StaticLib` variant is defined but **unused**. The `qt/mac/*.swift` files are a macOS-only AVKit/ctypes helper, not iOS. AnkiMobile (the official iOS app) is closed-source and absent — there is no copyable precedent here. This is plausibly larger than every other deliverable combined.

- **Requirement (when built):** Swift/SwiftUI app over the Anki **Rust backend** compiled for iOS as a static lib / xcframework, driven over FFI. **The scheduler stays in Rust** — a Swift reimplementation does not satisfy the shared-engine requirement.
- Builds and runs on the iOS Simulator (real device + Apple signing can wait).
- Loads the MileDown deck and runs a real review session on the shared Rust engine.
- Two-way sync NOT required Wednesday.

**What "wire up the FFI" actually means here (all net-new):**

1. Add iOS rust targets: `rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios`.
2. Build `rslib` as `crate-type=["staticlib"]` per arch → `lipo`/`xcodebuild -create-xcframework` into `Anki.xcframework`. (No `just` recipe exists — this is net-new devops work.)
3. **Build a C ABI** — the natural seam is the existing protobuf-bytes dispatch `Backend::run_service_method(service:u32, method:u32, input:&[u8]) -> Result<Vec<u8>,Vec<u8>>` (`rslib/rust_interface.rs:141`), but it is **not C-callable today** (only PyO3 wraps it). Decide the mechanism up front: (a) a hand-rolled `#[no_mangle] extern "C"` shim (~4-5 fns: `anki_open`, `anki_command`, `anki_free`, …) with a documented byte-buffer ownership contract, or (b) uniffi/cbindgen-generated bindings. Recommend (a) for a fast MVP.
4. SwiftUI app linking the xcframework; marshal protobuf bytes ↔ Swift `Data` (adopt `swift-protobuf` against `proto/anki/*.proto`, or hand-build the 2-3 request messages the MVP needs). Drive Again/Hard/Good/Easy through the scheduler RPCs (`proto/anki/scheduler.proto`).
5. **iOS storage glue:** copy the bundled `.apkg` into the app sandbox `Documents` dir at first launch and set SQLite temp paths for the sandbox. `rslib` has `cfg(target_os="android")` workarounds (`sqlite.rs:71`, `media/files.rs:366`) but **no iOS equivalents** — expect to add `cfg(target_os="ios")` guards where `/tmp`/`utimes` assumptions break.

- **Build-order + bail-out (this replaces the vague "do it first"):** run a **day-one time-boxed spike** with hard checkpoints, and a written fallback so iOS can't sink the submission:
  - **C1:** `rslib` builds for `aarch64-apple-ios-sim` as a staticlib.
  - **C2:** `extern "C"` shim + xcframework links into a Swift hello-world that round-trips bytes through `anki_command`.
  - **C3:** a collection opens / `.apkg` imports inside the iOS sandbox.
  - **C4:** one grade round-trips through the scheduler RPC.
  - **Fallback:** if checkpoint _N_ slips past time _T_, accept the 70% cap and harden desktop — protect the proven-undo/integrity Rust deliverable and the clean-device installs (those avoid the harsher 50% caps).
- **Prerequisites the repo can't give you:** macOS + Xcode + simulator toolchain are external; confirm they're ready before the spike.

---

## 6. Design principles (evidence-based — guide even the MVP)

- **Anki is a "do-not-forget-it" tool, not a "learn-it" tool.** The UI must never imply that high recall = exam readiness. This is the product's whole reason to exist.
- **Honesty over flattery.** Ranges and abstention beat a single confident number. Build the abstention path first.
- **Consistency is the dominant factor in spaced repetition.** Even the MVP should surface honest daily/streak activity rather than vanity metrics. (Display only; no nudges yet.)
- **Near-perfect accuracy can mean near-zero learning.** Keep this in mind for later features (Easy-button checks), but the MVP just measures honestly.

---

## 7. Data & taxonomy needs (MVP)

- **Deck:** MileDown MCAT deck (~2,900 cards), freely available as a `.apkg` (AnkiWeb / r/MCAT). Sub-topic hierarchical tags; AAMC-aligned; Khan Academy video links per card. Import is a stock flow (`ImportAnkiPackage` RPC, `rslib/src/import_export/package/apkg/import/mod.rs:52`).
  - ⚠️ **Import gotcha:** a community `.apkg` generally carries SM-2 (or no) FSRS state, and `with_scheduling=false` resets cards to new. So **right after import, every card's retrievability is null** until FSRS is enabled (§0.5) and the user reviews or runs FSRS _optimize_ (`update_memory_state`). The demo must include that seed step.
  - ⚠️ **Verify the real deck before locking `group_depth=1`:** confirm MileDown is single-rooted under one AAMC-aligned tag prefix and case-consistent. Anki canonifies tag case to first-seen, so the static AAMC map must be case-insensitive and must _surface_ unmapped/`(untagged)` groups (honesty rule), not silently drop them.
- **Topic map:** MileDown tag-prefix → AAMC section/subsection mapping (a static config file is fine for MVP). Native Anki hierarchical-tag support handles `::` tags — no legacy add-on required _(confirmed: `rslib/src/tags/tree.rs`, `matcher.rs`)_.
- **50k stress deck (later, not Wednesday):** combine MileDown with JackSparrow2048 / Abdullah, or programmatically multiply cards. The coverage map should be built against the AAMC outline regardless of deck size.
- **Held-out data:** not required to be _used_ Wednesday, but set aside a held-out slice of reviews now so Sunday's calibration work isn't scrambling. Keep training/test separation clean from the start.

---

## 8. Proof to capture for Wednesday

- Commit hash + clean-build screen recording (desktop).
- Test results (3 Rust unit tests + 1 Python-calling test).
- Clean-machine install recording (desktop).
- A short clip of the honest memory score showing both the number + 90% CI state and the "not enough data" abstention state.
- _(Deferred, not Wednesday)_ iOS review-session recording — see §5.7/§10.

---

## 9. Acceptance criteria (Wednesday)

The MVP passes when all of the following are true:

- Fork builds from source on a clean machine (`just` recipes).
- The mastery-query Rust change works end-to-end, has ≥3 Rust + 1 Python test passing, and undo + collection integrity are demonstrably intact.
- A review loop runs on the MileDown deck (desktop) with FSRS enabled.
- The memory score displays with point estimate + 90% CI + coverage + how-sure + last-updated + reasons, and correctly **abstains** under the give-up rule.
- The dashboard renders per-topic mastery from the Rust query.
- A desktop installer runs on a clean machine.
- _(Deferred)_ iOS build on the shared engine — explicitly out of scope this round.

**Hard caps (from the grading rubric):** no real Rust change → 50% max; **no phone companion on the shared engine → 70% max — knowingly accepted this round** (desktop-first, §0); either app fails on a clean device → 50% max; a made-up/misleading score → automatic fail. **Net:** with iOS deferred, the realistic ceiling this round is ~70%; the plan therefore maximizes the desktop deliverables (real Rust change + clean-device install + honest score) that avoid the _harsher_ 50%/auto-fail traps.

---

## 10. Post-MVP roadmap (where your feature ideas live)

Nothing here is Wednesday work. This sequences your ideas onto the project's later deadlines so the vision stays intact.

### Friday (iOS foundation + AI added + phone sync)

- **iOS shared-engine foundation (moved here from Wednesday):** the §5.7 spike — iOS rust targets → staticlib/xcframework → `extern "C"` shim over `run_service_method` → SwiftUI app that opens the deck and runs a real review session on the shared Rust engine (simulator OK). This lifts the 70% rubric cap. Do it before the iOS UI polish.
- **AI card generation** from a named source, with a checker against a gold set, beating a keyword/vector baseline; app still scores with AI off.
- **Performance model** + the **paraphrase test** (the memory→question bridge).
- **Two-way sync** (iOS↔desktop), offline review, conflict rule.
- iOS app shows the three scores with ranges + give-up rule.

### Sunday (prove it + ship both)

- **Memory calibration** (calibration chart + Brier/log loss on held-out reviews).
- **Score mapping** (questions → projected MCAT with a range).
- **Study-feature experiment**: pick one learning-science feature (e.g., interleaving), state the hypothesis, and run three builds — full app / feature-off ablation / plain Anki — at equal study time.
- Packaged signed builds (desktop installer + iOS via TestFlight or sideload), documented sync conflict handling, honest reporting (including null results).

### Your feature ideas, mapped

- **5 tabs** (memorization / problem-solving-procedure memorization / "actual learning" / passage-based learning / practice problems) → product architecture for Friday+; passage-based + procedures align with the performance/score bridges.
- **"I never learned this" button → separate "first-time learning" tab** → Friday+ (card-state/tag workflow; supports the "learn before flashcards" principle).
- **Diagnostic that teaches correct Again/Hard/Good/Easy grading** → Friday+ (directly targets the metacognition problem from the research).
- **Answer timer + numeric metrics** → timer ties to the performance/speed bridge (Friday+); metrics dashboard is an extension of the MVP dashboard.
- **Curriculum for MCAT problem-solving procedures** → Sunday+ / stretch.
- **"Actual learning connects to a textbook"** → Friday+ (content/AI integration).
- **Onboarding tutorial** → Friday+ (low risk, do once core is solid).
- **Behavioral nudges** ("you marked many Easy — sure?"; "go learn this topic before flashcards") → Sunday+ (built on the Easy-pattern and coverage data).

---

## 11. Open questions to confirm

**Resolved this round:** iOS → **deferred** (desktop-first, accept 70% cap). FSRS → **always on**. "Mastered" basis → **current/decayed retrievability** (threshold value still TBD). Readiness range → **90% confidence interval**, computed statistically.

Still open:

1. **"Mastered" threshold value** — the cutoff on current retrievability (was `0.9`; you said "define later"). Doesn't block the readiness band, only the per-topic "mastered" count.
2. **Give-up thresholds** (≥150 graded reviews / ≥5 topics): collection-wide or per-displayed-deck? Include learning-step grades? Computed server-side in the §5.2 query (recommended).
3. **Dashboard shape:** standalone SvelteKit page (needs the two `mediasrv.py` edits) vs. a card on the existing graphs screen (§5.5).
4. **Per-card vs per-note** mastery unit (MileDown multi-template notes inflate per-card counts; PRD2 §11.4).
5. **Installer:** add a `just installer` recipe or rely on CI artifacts (§5.6)?
6. **Has the real MileDown `.apkg` been inspected** for tag rooting + FSRS state? (Blocks `group_depth=1` and the demo seed step.)

---

## 12. Tech stack & implementation map (grounded in source)

| Layer              | Tech / location                                                                                                   | Notes                                                                                                             |
| ------------------ | ----------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Core engine        | Rust (`rslib/`), SQLite                                                                                           | mastery query: `rslib/src/stats/{service.rs,tag_mastery.rs}`                                                      |
| IPC / API          | protobuf over POST; `proto/anki/*.proto`                                                                          | add RPC to `service StatsService` (`stats.proto:13`); leave `BackendStatsService {}` empty                        |
| Python bridge      | PyO3 `cdylib` (`pylib/rsbridge`), `_backend.py`                                                                   | `col._backend.tag_mastery(...)` auto-generated                                                                    |
| Web frontend       | **SvelteKit + Vite**, `ts/routes/`                                                                                | dashboard = new `+page.svelte` route; calls `tagMastery()` from `@generated/backend` (async fetch → no UI freeze) |
| Page serving       | mediasrv at `localhost:40000/_anki/pages/`                                                                        | register page in `mediasrv.py:413` + RPC in `:726`                                                                |
| Reviewer           | stock `stdHtml` (`qt/aqt/reviewer.py`)                                                                            | no changes needed (§5.3)                                                                                          |
| Desktop GUI        | PyQt (`qt/aqt/`), embeds web views                                                                                | open page via `webview.load_sveltekit_page(...)`                                                                  |
| i18n               | Fluent (`ftl/core/statistics.ftl`)                                                                                | new keys → `tr.statistics*()`                                                                                     |
| FSRS recall        | `fsrs` crate; `extract_fsrs_retrievability` SQL scalar (`sqlite.rs:314`)                                          | reuse — don't re-derive                                                                                           |
| Memory state       | `rslib/src/scheduler/fsrs/memory_state.rs`; toggle `BoolKey::Fsrs` (**default false**)                            | enable + seed before demo                                                                                         |
| Deck import        | `ImportAnkiPackage` RPC (`rslib/src/import_export/...`)                                                           | stock `.apkg` flow                                                                                                |
| Installer          | Briefcase (`qt/installer/`)                                                                                       | no `just installer` recipe yet (§5.6)                                                                             |
| iOS — **deferred** | rust iOS targets → staticlib → xcframework; `extern "C"` shim over `run_service_method`; SwiftUI + swift-protobuf | nothing exists yet; post-Wednesday (§5.7, §10)                                                                    |
| Readiness stats    | mean + 90% CI over per-card recall (`mean ± 1.645·SD/√n`)                                                         | computed in the mastery query, returned to Svelte                                                                 |
| Build              | `just check` / `just run` / `just wheels` / `just test-{rust,py,ts}`                                              | never `./check`/`./ninja`                                                                                         |
