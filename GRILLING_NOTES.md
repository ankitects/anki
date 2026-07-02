# Grilling Notes — Stress-Test of the v2 Plan (ERRATA + Revised Scope)

**Date:** 2026-07-01 · **Method:** two adversarial grillers run in parallel against
`PHASE1/2/3_PLAN_V2.md` + the actual source — (A) a **grade/scope optimizer** and (B) an
**engine-correctness red-team**. This file **supersedes** any conflicting spec in the v2 plans
(each v2 plan carries a banner pointing here). It is the "further adjustments" output of the
grilling step. **No code was implemented** (per instruction).

---

## 0. Headline

- The v2 plans are **academically over-built and grade-under-optimized for a speedrun.** Fully
  executing them is a multi-week program (IRT + conformal + Venn-Abers + full RRF/cross-encoder IR
  - delayed held-out banks + ablation runs). Several items are also **flatly wrong or infeasible**
    in the real engine (see §1).
- **One live AUTO-FAIL is shipped today:** `ts/routes/dashboard/metrics.ts` emits a **point**
  pass-probability (`pPass = logistic(k·(perf − MPS))`, perf = FSRS-recall × a hardcoded `TRANSFER[]`
  guess) behind a trivially-cleared give-up rule (`≥15 reviews & ≥1% coverage`). The rubric
  auto-fails "made-up / misleading readiness numbers." **Fix this first — it is unbounded downside.**
- **The unlock for 32% of the rubric weight (honesty 20% + held-out 12%) is cheap and non-AI:** the
  **challenge-7d paraphrase set** (30 cards × 2 reworded application MCQs, delay simulated from the
  revlog). It produces the real `(x correct, n)` outcomes that Beta-Binomial calibration, the
  memory→performance bridge proof, and the held-out harness all need — **decoupled from the deferred
  AI pipeline** (which the plans circularly depended on).
- Correction to earlier recon: **`25.09.2` IS already an ancestor of HEAD** (`git describe` =
  `25.09.2-268-g…`). The branch = 25.09.2 + ~265 upstream (26.05 dev-line) commits + 3 CFA commits.
  The mobile fix is a **rebase-down / cherry-pick spike**, not a forward-port — but it drops ~265
  upstream commits the Phase-1 code may depend on (`note_tags_by_id`, `searched_cards_*`,
  `extract_fsrs_retrievability`, `CardData.decay` are 26.05-era). **Spike on a throwaway branch +
  `just check` before committing.**

---

## 1. CORRECTIONS that SUPERSEDE the v2 plan text (wrong / infeasible as written)

| #   | v2 item (superseded)                                                                                                  | Why it's wrong/infeasible (evidence)                                                                                                                                                                                                                                                   | Corrected spec                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| --- | --------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C1  | **Fade signal formula** `R(t)=(1+(19/81)·t/S)^decay` (P2 M4 / R10)                                                    | Wrong sign — engine uses `^(-decay)` with `decay` stored **positive** (`fsrs/…/inference.rs:24-25,61-62`); `19/81`=0.2346 is the `factor` only for the legacy fixed `decay=-0.5`. For FSRS-6 (per-user decay) it's stale.                                                              | **Don't hand-roll.** Reuse `extract_fsrs_retrievability` UDF (`storage/sqlite.rs:314`) or `FSRS::current_retrievability_seconds(...)` (`sqlite.rs:361-367`) with `seconds_elapsed = days_to_exam·86400`, `decay = card.decay.unwrap_or(FSRS5_DEFAULT_DECAY)`.                                                                                                                                                                                       |
| C2  | **Re-gate on answer via `clear_study_queues()`** (P2 M4 / R12)                                                        | `maybe_clear_study_queues_after_op` **explicitly excludes `Op::AnswerCard`** (`queue/mod.rs:209-213`); Anki never rebuilds on answer (perf). Naive rebuild = full gather on every button press at 50k.                                                                                 | **Gate at BUILD time only;** a newly-qualified dependent unlocks on the **next** queue build (day rollover / manual rebuild), like sibling burying. Optional: cheap in-memory promotion into `CardQueues` w/o full rebuild (document perf tradeoff).                                                                                                                                                                                                |
| C3  | **Lag-0-1 contiguity for adjacent confusables** (P1 M2 / R18)                                                         | Default `new_mix/interday = MixWithReviews` → `Intersperser` splices reviews **between** the two new confusables (`intersperser.rs`, `builder/mod.rs:212-286`); contrast reorders `new`/`review` piles **independently** (`contrast.rs:89-97`) so cross-pile confusables never adjoin. | **Move the contrast pass to the merged `main` queue** (after `merge_new`/`merge_day_learning`), or force `ReviewMix::AfterReviews`/blocked when contrast is on; cluster over the **new∪review** union; add a **review-inclusive** test (current test is all-new).                                                                                                                                                                                   |
| C4  | **"distance-to-1600 scaled-score band"** e.g. `≈1310 ± 70` (P3 M1 / R2)                                               | CFA never publishes the MPS on the 1600 scale; no public `p̂ → 1600` map. Emitting `≈1310` **invents a number the rubric forbids** (auto-fail vector).                                                                                                                                  | **Keep P(pass) on [0,1]** with the abstention contract. If a 1600 figure is shown at all, label it "illustrative rescale, MPS location unknown," never a point.                                                                                                                                                                                                                                                                                     |
| C5  | **Beta-Binomial P(pass) / IRT / calibration read now** (P3 M1 / R2,R3,R4)                                             | Needs real pass/fail **outcomes**; the demo deck has **zero** held-out MCQ outcomes; FSRS is **off by default** so even Memory has no signal. Deriving `(x,n)` from FSRS recall = fabricating outcomes.                                                                                | Outcomes come from the **manual 30×2 paraphrase set (C8)**. Until ≥ gate, **abstain**. Do IRT (if at all) **offline in Python**; ship only fixed blueprint priors + Beta-Binomial in-app.                                                                                                                                                                                                                                                           |
| C6  | **Full IRT/PFA/LKT + Rudner + Venn-Abers + conformal in `rslib`** (P3 / R4,R5,R6)                                     | Single sparse user → item params **unidentifiable**; no R/Python stats runtime in `rslib`; multi-week surface.                                                                                                                                                                         | **Descope to plan-only / "future work."** Ship **shrunk logistic or Beta-Binomial + fixed blueprint priors + honest abstention** — already "beats a simpler baseline" and is _more_ defensible for n=1.                                                                                                                                                                                                                                             |
| C7  | **Full AI IR pipeline "beats tuned BM25 AND tuned dense" via RRF+cross-encoder; gold 100-200; 2 SMEs** (P2 / R21,R23) | Multi-week; no corpus/qrels/SMEs; an **untuned** dense strawman is exactly what the research warned against (half-doing it is worse than omitting).                                                                                                                                    | **Descope to a defensible slice:** one formula cluster (duration/TVM); small hand-built qrel set (~20-30 queries) BM25 vs BM25+RRF+rerank, report precision@k **and latency** with honest small-N; gold set ~**50** (matches 7f); you are SME + adversarial model critic (disclosed). Drop "beats tuned dense" unless a dense retriever is actually tuned.                                                                                          |
| C8  | **Held-out probe bank depends on the (deferred) AI pipeline** (P3 M3 / R7) — circular                                 | 32% of weight (honesty+held-out) hung on a deferred producer.                                                                                                                                                                                                                          | **Hand-author 30×2 delayed paraphrase MCQs** (challenge 7d): no AI, no corpus. Simulate the ≥7-day delay from **revlog** (pick cards last studied ≥7d ago); disclose lag honestly; never claim a delay you didn't measure. Wire `HELD_OUT_PROBE_ITEMS` to this count.                                                                                                                                                                               |
| C9  | **`format_congruency_mult`, self-explanation credit as config fields** (P2 / R14,R16)                                 | No transfer-credit scorer exists to read them → **inert config / dead code**.                                                                                                                                                                                                          | Don't add fields nothing reads. Make `self_explain` a **real template variant toggle** (changes what the learner sees); apply congruency as an **analysis-time factor** in the write-up, not an engine field, until an outcome-based scorer exists.                                                                                                                                                                                                 |
| C10 | **"Space repeats of the SAME card" guard in `interleave_clusters`** (P1 M2 / R18)                                     | A card appears **once** per pile per build; same-card repeats live in FSRS intervals across days, not in one queue pass → **guards a non-existent case**.                                                                                                                              | Reframe as a **sibling-adjacency guard keyed on `note_id`** (avoid placing two _templates of the same note_ adjacently); `NewCard.template_index` exists.                                                                                                                                                                                                                                                                                           |
| C11 | **Memory/Perf/Readiness computed when FSRS absent** (shipped `metrics.ts:141-149` proxy)                              | FSRS **off by default**; imported deck has no `s/d` → `extract_fsrs_retrievability`=None → gauges silently use `reviewed/seen` graduation proxy (recon #3).                                                                                                                            | Make **FSRS-enabled + optimized a hard precondition**; if `!fsrs                                                                                                                                                                                                                                                                                                                                                                                    |
| C12 | **Hardcoded / stale topic weights + MPS band** (`metrics.ts:25-55`)                                                   | `WEIGHT` are single points and several are **wrong** vs 2025/26 ranges (PM 6→~10, AltInv 6→8.5, Deriv 6→6.5); `MPS_LOW/HIGH` hardcoded and the band construction looks inverted; `DEFAULT_MASTERED_THRESHOLD=0.9` mislabeled "mastered."                                               | Ship `cfa_weights_2026.json` (min,max,midpoint); weight by midpoint, carry range as uncertainty; **configurable** pass band (~68-75%); relabel "mastered" → **"high recall probability."** Fix PM/AltInv/Deriv now regardless.                                                                                                                                                                                                                      |
| C13 | **`cluster::` tags assumed present** (P1 M0/M2 / R18)                                                                 | Demo deck is **flat (53 reading tags, no `::`)**; current fallback groups by whole reading = **blocking** (the Carvalho & Goldstone d=0.76 loss the plan warns about).                                                                                                                 | **Phase 1 runs on EXISTING cluster/topic tags** (shipped first-content-tag fallback + topic alias map) — no new labeling. Keep the **no-op / don't-first-tag-block correctness guard as Phase 1** (contrast is a no-op when no usable `cluster::`/topic tags exist; never first-tag blocking). The **`confusable::high` curation + signed gate move to Phase 2 [R18 → Phase 2]** (manual per-pair SME judgment, or the computed behavioral signal). |
| C14 | **`get_dashboard`/`get_concept_graph` reachable for the harness**                                                     | They **bypass the Python `Collection` wrapper** (web-only; recon #7) → the one-command harness (7h) + calibration scripts can't call them.                                                                                                                                             | Add thin `get_dashboard`/`get_concept_graph`/(future)`get_readiness` methods to `pylib/anki/collection.py` + one Python test each (mirror `topic_mastery`).                                                                                                                                                                                                                                                                                         |

---

## 2. Revised MVP scope (grade-optimized, ordered) — for WHEN implementation resumes

**Tier 0 — remove the auto-fail (hours):** C11-corrected abstention gate + kill the point number
→ Beta-Binomial band **or abstain** (R1/R2/R25/R28); audit all three gauges for lenient fallbacks;
relabel "mastered"; confirm `just check` green.

**Tier 1 — knock out the two 60% caps + the study feature (1-2 days):** author the **30×2
paraphrase set** (7d, revlog-simulated delay) → real outcomes for the bridge proof + a tiny honest
calibration curve; wrap it + a queue-build timing loop as a **one-command bench** (7h) with a
**leakage scan** (7e); **run the contrast ON/OFF/vanilla ablation** (toggle exists), state the main
number ahead, disclose n=1 (descriptive, not inferential).

**Tier 2 — defensibility + AI survival (2-3 days):** Phase 1 keeps the **C3-corrected
merged-queue contrast** (label-free contiguity) + **C10** sibling-adjacency guard on the
**existing** cluster/topic tags (the C13 no-op guard stays Phase 1); the **signed confusability
gate + `confusable::high` curation move to Phase 2 [R18 → Phase 2]** (manual SME label / computed
behavioral signal). **Minimal traceable AI slice** (C7) to clear AI-section-0; **mobile
build+sync spike** after the rebase cherry-pick spike (C-mobile), with a fixed go/no-go checkpoint.

**PLAN-ONLY (cite as future work — do NOT build for the deadline):** IRT/PFA/LKT, Rudner CA/CC,
Venn-Abers, conformal (C6); the full fade ladder with FSRS-horizon-R + hysteresis + spaced-session
re-gating (C1/C2 — biggest engine risk, not a named challenge); the full "beats tuned dense"
RRF+cross-encoder IR project (C7).

---

## 3. Cap status (if the Tier 0-2 MVP is executed)

| Cap                               | Now                                | After MVP                                      |
| --------------------------------- | ---------------------------------- | ---------------------------------------------- |
| Made-up readiness → **AUTO-FAIL** | **LIVE (shipped)**                 | Removed (abstain + band)                       |
| Real Rust change → 50%            | Closed (contrast + mastery, tests) | Closed                                         |
| Held-out testing → 60%            | Open                               | Removed (7d set + harness)                     |
| Re-runnable tests → 60%           | Open                               | Removed (one-command bench)                    |
| Phone+sync → 70%                  | **Open (doc-only mobile)**         | Only if mobile spike lands (human decision §4) |
| AI no source → AI section 0       | **=0 (nothing built)**             | Removed by the minimal traceable slice         |
| Clean-runnable → 50%              | Verify                             | Verified via `just check`                      |
| Leaked test data → score 0        | N/A yet                            | Guarded by 7e scan built with the bank         |

---

## 4. Decisions that need the human (resolve to finalize the plan; recommendations given)

1. **Mobile — chase the 10% or accept the 70% cap?** Recommend: run the ~1hr rebase/cherry-pick
   spike onto `25.09.2` + `just check`; commit to the rsdroid `.aar` + emulator + sync demo **only
   if** the spike is clean **and** Tier 0-1 are done; else accept the cap and reinvest.
2. **AI (Phase 2) — minimal traceable survival slice or the full "beats BM25+dense" pipeline?**
   Recommend: **minimal survival slice** (clears AI-section-0) unless everything else is locked.
3. **Readiness math — TS quick-fix now, or promote to a testable Rust `GetReadiness` RPC?**
   Recommend: **TS quick-fix first** to kill the auto-fail today; promote to Rust (stronger
   Rust-change + honesty story, Python-exposed + unit-tested) only if schedule allows.
4. **Demo of permanent abstention (sparse real deck).** Recommend the **two-mode** demo: live gauge
   **abstains loudly** with the full honesty contract, plus an explicitly-labeled **calibration
   backtest** on the 30×2 outcomes (or a labeled prior-cohort/synthetic set) — never a live number.
   _Note (owner intent):_ the shipped `15 reviews / 1% coverage` gate was intentionally low to
   **test that the readiness pipeline works** during dev. Keep that testability — make the gate
   **configurable** (honest default that ships) + a **LABELED test mode** — rather than deleting it.
   A visible number proves the **plumbing**, not **accuracy**; accuracy needs the calibration curve
   on real held-out outcomes (`TRANSFER[]` is a hardcoded guess, so a live number ≠ a measurement).
