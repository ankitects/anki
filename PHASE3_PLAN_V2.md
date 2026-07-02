# Phase 3 Plan v2 — Readiness (Banded + Calibrated), Unification & the Rigorous Ablation

> ⚠️ **GRILLING ERRATA — see [`GRILLING_NOTES.md`](./GRILLING_NOTES.md), which SUPERSEDES conflicting
> text below.** Corrected here: **C4** (drop the "distance-to-1600 scaled-score band" — it invents a
> number the rubric forbids; keep P(pass) on [0,1] with abstention); **C5** (Beta-Binomial/calibration
> need real outcomes the demo deck lacks → they come from the manual **30×2 paraphrase set**, else
> abstain); **C6** (IRT/PFA/LKT + Rudner + Venn-Abers + conformal are **descoped to future work** —
> unidentifiable for n=1, no stats runtime in `rslib`; ship shrunk-logistic/Beta-Binomial + fixed
> blueprint priors + honest abstention); **C8** (held-out testing must NOT depend on the deferred AI
> pipeline — use the 30×2 set, simulate the ≥7-day delay from the revlog). The mobile rebase is a
> cherry-pick **spike-first** decision (see `GRILLING_NOTES.md` §4).

> **BANNER — evidence-refined revision of `PHASE3_PLAN.md` (v1).** This supersedes v1 by folding
> in `RESEARCH_ADDENDUM.md` (9 threads, 29 concrete plan changes). Same skeleton as v1
> (scope / milestones M0–M6 / deliverables / touch-points / risks), but every readiness claim is
> now grounded in a cited method with a concrete parameter. **Core stance for the 20%-honesty and
> 12%-held-out-tests weights: never emit a point pass-probability — emit a calibrated BAND, add a
> SECOND honest number (classification confidence), ABSTAIN loudly when thin, and prove
> memory→performance on DELAYED, held-out, re-runnable application MCQs.**
>
> Companion to `brainlift.md`, `PHASE1_PLAN.md`, `PHASE2_PLAN.md`, `PRD.md`, `RESEARCH_ADDENDUM.md`.

---

## What changed vs PHASE3_PLAN.md (v1) — delta section

The one thing already built ahead of plan is the Readiness **display** gauge in
`ts/routes/dashboard/metrics.ts`: it computes a logistic **point** `pPass`, an MPS band
`[MPS_LOW=0.6, MPS_HIGH=0.7]`, a naive Wald `band()`, and a **far-too-lenient** give-up rule
(`MIN_GRADED_REVIEWS = 15`, `MIN_COVERAGE = 0.01`). Everything else in Phase 3 is NOT started.
v2 rewrites the _math and contract_ of that gauge and specifies the backend it needs.

| #  | v1 said                                           | v2 changes it to                                                                                                                                                    | Cite  |
| -- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| 1  | `pPass.point = logistic(...)` (a point number)    | **Never emit a point.** Point-of-record = Beta-Binomial posterior mean, Jeffreys prior; ship a Wilson/Jeffreys 90% BAND, output as **distance-to-1600 scaled band** | [R2]  |
| 2  | Wald `band()` (`mean±Z·√(p(1−p)/n)`)              | Wilson/Jeffreys interval (Wald under-covers at small n)                                                                                                             | [R2]  |
| 3  | (no calibration; deferred)                        | **Platt/temperature** default; Venn-Abers/Beta for a distribution-free guarantee; **never isotonic** under ~1000 outcomes                                           | [R3]  |
| 4  | logistic map on hand-guessed `TRANSFER[]` factors | Readiness backbone = **IRT/PFA/LKT** with EPP-capped predictors, blueprint weights as fixed priors, shrinkage ≥0.9 — NOT deep knowledge tracing                     | [R4]  |
| 5  | one number (`pPass`)                              | **two** numbers: P(pass) AND IRT classification accuracy/consistency at the MPS ("confidence of the call")                                                          | [R5]  |
| 6  | (none)                                            | conformal backstop w/ Small-Sample Beta Correction once a held-out bank exists (expect frequent "abstain")                                                          | [R6]  |
| 7  | held-out "measurement" deferred, undated          | held-out probes must be **DELAYED ≥1 week** application MCQs, not immediate accuracy                                                                                | [R7]  |
| 8  | cluster credit unqualified                        | **within-topic near-transfer only**; cross-topic leakage is the ablation; ZERO transfer credit w/o success+congruency+feedback                                      | [R8]  |
| 9  | `MIN_GRADED_REVIEWS=15`, `MIN_COVERAGE=0.01`      | abstain unless `graded_reviews≥300` AND `coverage≥70%` AND `≥50 delayed probe items` AND `interval_half_width≤W_max`                                                | [R1]  |
| 10 | hardcoded `MPS_CENTER=0.65` / logistic to 1.0     | cap max certainty at `prior_mock_exam_corr≈0.7`; gate = configurable band **~68–75%**; enforce a **minimum interval width**                                         | [R25] |
| 11 | (mobile: doc-only)                                | **PREREQUISITE:** rebase onto anki **25.09.2** tag (rsdroid 0.1.64) so one commit builds desktop + phone                                                            | [R27] |

New backend needed: `rslib/src/readiness/` (Beta-Binomial + Wilson/Jeffreys, Platt/Venn-Abers
calibrator, IRT/PFA scorer + Rudner CA/CC, conformal backstop, abstention gate) exposed via a
`GetReadiness` RPC; `metrics.ts` becomes a thin display layer over it. Deferred-in-v1 M3/M4 are
**partially promoted** here because the honesty (20%) and held-out (12%) weights depend on them.

---

## Scope

**In scope**

- **Unification** — one graph-aware `build_queues` pass over the **tag taxonomy** (cluster
  interference edges on `cluster::*` + rung dependencies on `rung::*`), all three SPOVs at once.
  No synced edge table (tags already sync; a local-only `card_relationships` cache only if needed).
- **Readiness gauge, banded + calibratable** — a documented, cited method that **emits a band, not
  a point**, adds a **second** honest number (classification confidence), **abstains** on thin
  data, and displays the full honesty contract (evidence + missing data + calibration history +
  range + best-next-topic). Plus the readiness-optimization allocation selector (demoted SPOV4).
- **Held-out mock harness (promoted from deferred)** — a disjoint, **delayed** application-MCQ
  probe bank that is both the memory→performance bridge proof (challenge 7d) and the calibration
  ground truth; re-runnable (challenge weight 12%).
- **Generalization** — validated edge sourcing for BYO / untagged decks.

**Builds on Phases 1–2:** Phase 1 contrast (interference edges) + Phase 2 fade (dependency edges +
Performance gauge + content pipeline). Depends on Phase 1 [R1] abstention thresholds.

**Out of scope:** general productization; full statistically-powered multi-subject RCT (single-user

- small-cohort ablation only — power is honestly disclosed as a limitation).

---

## Milestones

### M0 — Unify the graph (over tags) — **[REVISE]**

Run both edge types through one `build_queues` pass: contrast on `cluster::*` (interference) +
gating/fading on `rung::*` (dependency). Precedence when a card is in both: **gate first, then
order survivors by cluster.** No new synced data. **[R8] constraint added:** cluster ordering
credit is applied **within-topic only** — never reorder across topic boundaries, because
general/far transfer is ~zero (St. Hilaire & Carpenter 2023, general g=0.04). Cross-topic
reordering is reserved as an explicit ablation arm (M4), not a default.

### M1 — Readiness gauge: banded, two-number, abstaining — **[PARTIAL-DONE → REVISE heavily]**

Display exists in `metrics.ts`; its math and contract are replaced. New backend does the stats.

- **[R2] Emit a BAND, never a point.** Replace `pPass.point = logistic(...)`.
  - Point-of-record = Beta-Binomial posterior mean with **Jeffreys prior Beta(0.5,0.5)**:
    `p̂ = (x + 0.5) / (n + 1)` over graded held-out outcomes (x correct of n).
  - Band = **Wilson / Jeffreys 90%** interval (replace the current Wald `band()` — Wald
    under-covers at small n). Widen the band as it propagates through the MPS map.
  - **Output as a distance-to-1600 scaled-score band** (CFA moved to a 1600-indexed scaled score
    Feb 2025), e.g. "≈1310 ± 70, below the ~1330 pass region." Evidence: Brown, Cai & DasGupta
    (2001), recommended for n≤40.
- **[R4] Backbone = IRT / PFA / LKT (logistic), not deep knowledge tracing.** DKT's AUC edge is a
  data-leakage artifact (Wilson 2016; Khajah 2016) — so the _simple_ model is the _defensible_
  one, which also satisfies "beat a simpler baseline" by design.
  - Cap free predictors by **Events-Per-Parameter**: `max_predictors ≈ floor(min(events,
    non-events)/10)` (EPP range 4.8–23, Riley et al. 2019 — the flat "10" is a myth; document the
    chosen EPP).
  - CFA-blueprint topic weights are **FIXED priors**, not fitted coefficients.
  - Apply **global shrinkage ≥ 0.9** toward the prior. Few aggregate features only.
  - This replaces the hand-guessed `TRANSFER[]` table in `metrics.ts` (currently 0.6–0.9 guesses).
- **[R5] Second honest number — IRT Classification Accuracy/Consistency at the MPS** (Rudner 2005
  method; R `cacIRT` as reference impl). Surfaced as **"confidence of this pass/fail CALL"**,
  distinct from P(pass). **Abstain** when `SEE(θ)` is too large near the cut. Item selection
  targets **test information near the MPS band** (Wyse & Babcock 2016 — do not blindly maximize
  info exactly at the cut).
- **[R25] Cap certainty; configurable gate; floor the interval width.**
  - `prior_mock_exam_corr ≈ 0.7` caps max attainable certainty (mocks predict the exam only
    moderately: Castro 2025 r≈0.71–0.76; Ronen: mocks reward recall, exam demands application).
  - Readiness gate is a **configurable mock-proxy BAND ~68–75%** (300Hours revised the L1 target
    down to 68% in Nov 2025) — replace the hardcoded `MPS_CENTER=0.65` / `[0.6,0.7]` and the
    logistic-to-1.0 tails.
  - Enforce a **minimum interval half-width `W_min`** reflecting irreducible residual error, so the
    band can never collapse to near-certainty.
- **[R1] Abstention gate (coordinated with Phase 1).** Emit any P(pass)/scaled-band **only if
  ALL** hold: `graded_reviews ≥ 300` AND `topic_coverage ≥ 70%` (≥7/10 areas) AND
  `delayed_held_out_probe_items ≥ 50` AND `interval_half_width ≤ W_max`; else render
  **"READINESS: insufficient data (abstaining)."** This retires the shipped `MIN_GRADED_REVIEWS=15`
  / `MIN_COVERAGE=0.01`, which risk the **made-up/misleading-readiness AUTO-FAIL (R10)**. Evidence:
  selective prediction / Chow rule (El-Yaniv & Wiener 2010).
- **Honesty-rule display contract (always render, even when abstaining):** (1) the **evidence** the
  call rests on (n graded, per-topic coverage, calibration sample size), (2) **what's missing**
  (which gate failed), (3) **calibration history** (last calibration date + Brier/log-loss), (4)
  the **range/band** (never a bare point), (5) the **single best next topic** (largest weighted gap;
  over-weight **Ethics** near the boundary — dual role: largest weight + tie-break, T8).

### M2 — Readiness-optimization allocation (demoted SPOV4) — **[REVISE]**

Card selection weighted by `exam-weight × marginal Δ P(pass-band-center)`, a Readiness-gradient
selector; toggle it; ablate against vanilla uniform desired-retention. **[R8]** the Δ it optimizes
uses **within-topic** performance credit only. **[R25]** exam-weight uses the **(min,max,midpoint)**
topic-weight config (versioned by exam year), budgeting by midpoint and carrying the range as
uncertainty; single weighted-overall target (no topic-level cutoffs, since CFA publishes only an
overall MPS).

### M3 — Held-out mock harness (calibration + the bridge proof) — **[PROMOTED from Deferred → NEW]**

Promoted because honesty (20%) and held-out re-runnable tests (12%) depend on it; kept scoped.

- **[R7] Probes are DELAYED (≥1 week), held-out application MCQs — not immediate accuracy.**
  Transfer/discrimination benefits are delay-sensitive (Rohrer 2015: d=0.42 immediate → 0.79 at
  30 days). Schedule each probe item ≥7 days after its last study touch; log the study→probe lag.
- **Bridge proof = challenge 7d paraphrase test:** ≥30 cards × 2 reworded application variants;
  report the **memory-vs-performance gap** (retention accuracy minus delayed-paraphrase accuracy).
- **Partition the held-out bank into disjoint pools:** a _Performance-probe_ pool and a
  _calibration-mock_ pool, so Readiness is never calibrated against its own inputs (no circularity).
- **[R3] Calibration method = Platt / temperature scaling** (fit one/two scalars) as the default;
  prefer **Venn-Abers or Beta calibration** for a distribution-free guarantee under exchangeability.
  **Never isotonic** below ~1000 pass/fail outcomes (Niculescu-Mizil & Caruana 2005: "<1000 cases";
  a single learner will never reach this — soften the cutoff to a 200–1000 learner-dependent range).
- **[R6] Conformal backstop** with **Small-Sample Beta Correction** (inflate α via exact
  Beta(n+1−l, l)) once an outcome bank exists; at cold start it will frequently return "abstain
  (interval too wide)" — that is the honest, correct behavior (Angelopoulos & Bates 2023; Zwart
  2025, arXiv:2509.15349).
- **Re-runnable:** the whole harness runs from one command (ties to challenge 7h bench discipline);
  leakage scan (7e) walls the probe bank off from any generator prompt / calibration input.

### M4 — Rigorous ablation — **[REVISE — arms sharpened]**

Arms on **equal total study time** and the **same content**, scored on Memory / delayed-Performance
/ Readiness, reporting per-SPOV contribution + combined effect. Beyond v1's feature-ON / OFF /
vanilla-Anki, add the evidence-motivated arms:

- **[R8] SPOV1 cross-topic-leakage arm:** default within-topic cluster credit vs a cross-topic-credit
  variant — expected to NOT help and possibly hurt calibration (St. Hilaire g=0.04; Pan & Rickard
  PEESE intercept ≈0). A free, evidence-backed ablation.
- **Give-up / abstention arm:** shipped-lenient thresholds vs the [R1] gate — quantifies the
  honesty cost of over-claiming.
  Power is limited (single user / small cohort) and is disclosed as the primary limitation, not hidden.

### M5 — Generalization (BYO / untagged decks) — **[REVISE]**

AI edge sourcing for untagged decks: LLM cluster/rung proposals + behavioral confusion mining from
`revlog` + similarity **only with a confusability signal** (never raw embedding similarity). Validate
proposed edges (human + behavioral) before use. **[R8]** generated/untagged edges get memory-retention
credit but ZERO performance-transfer credit until validated on delayed held-out probes. Maintain
held-out hygiene at scale.

### M6 — Analyze & write up — **[REVISE]**

Does graph scheduling beat vanilla Anki on **delayed** Performance/Readiness at equal study time?
Per-SPOV contribution? How well is Readiness calibrated (Brier/log-loss on the calibration-mock
pool, coverage of the conformal bands)? Report the memory→performance gap (7d). Disclose the
mock↔exam ceiling (r≈0.7), the delay sensitivity, and the power limitation.

---

## Deliverables

1. **Unified graph scheduler** over the tag taxonomy (cluster + rung in one `build_queues` pass),
   with within-topic-only cluster credit — no synced edge table. **[REVISE]**
2. **Banded, two-number, abstaining Readiness gauge**: Beta-Binomial/Wilson-Jeffreys P(pass) band
   as a distance-to-1600 scaled score + Rudner classification confidence + full honesty display
   contract, backed by a new `rslib/src/readiness/` module + `GetReadiness` RPC; `metrics.ts` is a
   thin display layer. **[REVISE / NEW backend]**
3. **Readiness-optimization allocation** toggle (within-topic Δ pass-prob × exam-weight). **[REVISE]**
4. **Held-out delayed-probe harness** (bridge proof 7d + Platt/Venn-Abers calibration + conformal
   backstop), disjoint pools, one-command re-runnable. **[PROMOTED / NEW]**
5. **BYO/untagged-deck** validated edge-sourcing path. **[REVISE]**
6. **Sharpened three-plus-arm ablation** (ON/OFF/vanilla + cross-topic-leakage + abstention). **[REVISE]**
7. **PREREQUISITE — branch rebased onto anki 25.09.2** so one commit builds desktop + AnkiDroid. **[NEW]**

---

## Engine / system touch points (reference)

| Concern                                    | File / area                                                                                                                                             | Change            |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- |
| Unified graph pass (within-topic clusters) | `Collection::build_queues` — `rslib/src/scheduler/queue/builder/`                                                                                       | [REVISE]          |
| Edges over tags                            | `cluster::*` + `rung::*` on `notes.tags`; optional local-only `card_relationships` cache                                                                | keep              |
| **Readiness backend (NEW)**                | `rslib/src/readiness/` — Beta-Binomial+Wilson/Jeffreys, Platt/Venn-Abers calibrator, IRT/PFA + Rudner CA/CC, conformal+Beta-correction, abstention gate | **[NEW]** [R2–R6] |
| Readiness RPC                              | `proto/anki/stats.proto` (`GetReadiness`); expose via Python Collection wrapper (fix web-only exposure)                                                 | [NEW]             |
| Readiness display                          | `ts/routes/dashboard/metrics.ts` → thin layer: drop local `logistic`/Wald `band()`/`TRANSFER[]`; drop `MIN_GRADED_REVIEWS=15`/`MIN_COVERAGE=0.01`       | [REVISE]          |
| FSRS inputs (fade signal)                  | `extract_fsrs_*` — `rslib/src/storage/sqlite.rs`; revlog; predicted R at exam horizon w/ fitted decay                                                   | ref               |
| Held-out probe bank + calibration          | new `readiness` submodule + one-command harness; leakage scan (7e)                                                                                      | [NEW]             |
| Toggles                                    | `proto/anki/deck_config.proto`, `rslib/src/deckconfig/mod.rs`                                                                                           | keep              |
| Behavioral mining                          | `revlog` analysis (SQL)                                                                                                                                 | keep              |
| **Mobile prerequisite**                    | rebase branch onto **25.09.2** tag (rsdroid 0.1.64)                                                                                                     | **[NEW]** [R27]   |

---

## Risks & decisions

- **[R10 AUTO-FAIL] Made-up / under-evidenced readiness** — the shipped `≥15 reviews & ≥1% coverage`
  gate is indefensible and is the single highest-leverage honesty fix. Replaced by the [R1] gate +
  band + second number + full display contract. **Never surface a bare point pass-probability.**
- **[R27 — 70% cap risk] Engine baseline mismatch (PREREQUISITE, do FIRST).** Branch merge-base is
  the 26.05 dev-line, not the **25.09.2** tag rsdroid 0.1.64 pins → breaks "same commit builds
  desktop + AnkiDroid" and risks the 70% one-engine+sync cap. Rebase before the mobile deliverable.
- **Mock↔exam ceiling** — certainty is capped at `prior_mock_exam_corr≈0.7` with a floored interval
  width; do not let the band collapse to false confidence (Castro 2025; Ronen).
- **Cold-start abstention is EXPECTED and honest** — conformal + selective prediction will often say
  "abstain (interval too wide)"; render it as a feature, not a bug ([R5]/[R6]).
- **Calibration circularity** — Performance-probe and calibration-mock pools stay disjoint (M3).
- **Delay sensitivity of the bridge** — immediate accuracy overstates transfer; probes are ≥1 week
  delayed ([R7]).
- **Cross-topic leakage** — within-topic credit by default; cross-topic is an ablation, not a
  default ([R8]).
- **AI-sourced edge quality** — validate (human + behavioral); confusability-signal only; no
  ungraded generated items feed the readiness estimate.
- **Experiment power** — single-user/small-cohort; disclosed as the primary limitation.
- **No synced edge table** — unify over tags; a first-class synced table is avoided unless truly
  needed.
