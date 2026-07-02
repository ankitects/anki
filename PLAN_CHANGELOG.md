# Plan Changelog — Evidence-Informed Revision (v1 → v2)

**Date:** 2026-07-01 · **Owner:** William
**Method:** A deep-research fan-out (9 evidence threads) executed the `/deep-research` methodology
using `paper-search` + `agent-reach` (Exa/Jina) + web search: each thread was investigated with
source-grading, then **adversarially verified** (devil's-advocate refute pass + citation checks),
then synthesized. Full cited evidence: [`RESEARCH_ADDENDUM.md`](./RESEARCH_ADDENDUM.md).

**New files produced by this revision:**

- [`RESEARCH_ADDENDUM.md`](./RESEARCH_ADDENDUM.md) — the cited research report (T1–T9 + 29 changes).
- [`PHASE1_PLAN_V2.md`](./PHASE1_PLAN_V2.md), [`PHASE2_PLAN_V2.md`](./PHASE2_PLAN_V2.md),
  [`PHASE3_PLAN_V2.md`](./PHASE3_PLAN_V2.md) — the revised phase plans.
- [`GRILLING_NOTES.md`](./GRILLING_NOTES.md) — the post-grilling **errata + revised MVP scope**
  (two adversarial grillers: grade/scope + engine-correctness). It **supersedes** conflicting spec in
  the v2 plans (each v2 plan now carries a banner pointing to it).
- This changelog. (v1 plans + `brainlift.md` + `PRD.md` are left intact as history.)

**Grilling outcome (2026-07-01):** the v2 plans are grade-optimal only if descoped — the shipped
`metrics.ts` readiness point-number is a **live auto-fail** (fix first), several v2 items were
**wrong/infeasible** in the real engine (fade formula sign, re-gate-on-answer, lag-0-1 contiguity,
distance-to-1600, inert config, IRT for n=1), and the honesty+held-out weight (32%) unlocks cheaply
via the manual **30×2 paraphrase set**, not the deferred AI pipeline. Full details + the corrected
C1–C14 spec + the ordered MVP are in `GRILLING_NOTES.md`.

---

## Headline verdict: the three SPOVs survive, but each is now _bounded_ by a measured moderator

| SPOV                             | Before (v1)                                            | After research (v2)                                                                                                                                                                                                                                                                                                                                                                             |
| -------------------------------- | ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1 — schedule the graph**       | cluster credit is the spine; transfer d≈0.40           | Transfer is **moderator-carried**: with none of {format-match, elaboration, initial success} present the corrected intercept collapses to **d≈0.015**; general/far transfer ≈0 (St. Hilaire & Carpenter 2023 g=0.04). → **cluster credit is within-topic only**; that restriction is itself a free ablation arm.                                                                                |
| **2 — fade off FSRS state**      | worked→faded→solve, fade read off FSRS, adaptive>fixed | Worked-example g=0.48 and expertise-reversal (novice +0.505 / expert −0.428) are solid, but **"adaptive > fixed" is only REFINED, not confirmed**; **feedback is a hidden invariant** (retrieval g=0.14 without / 0.50 with); the gate should key on **spaced-session count**, not a within-session criterion; fade signal should be **retrievability at the exam horizon**, not raw stability. |
| **3 — adjacency of confusables** | surface confusables back-to-back                       | **Strongest bet**, but the active ingredient is **juxtaposition/adjacency (not spacing)**, and the ordering rule **flips sign for dissimilar items** (blocking wins d=0.76) → adjacency must sit behind a **signed similarity gate** with a **contiguity constraint**; **space repeats of the same card** while juxtaposing _different_ confusables.                                            |

**Biggest single finding:** the shipped Readiness gauge is a **live R10 auto-fail risk** — it emits a
pass-probability with a give-up rule (`≥15 reviews & ≥1% coverage`) far weaker than the PRD's, and it
shows a **point** number. The grader auto-fails "made-up / misleading readiness numbers." Fixing this
(honest band + real abstention gate) is the highest-leverage change and touches the 20%-weighted
"score accuracy & honest uncertainty" area directly.

---

## The 29 concrete changes (grouped; full evidence in RESEARCH_ADDENDUM.md §4)

### A. Honesty / calibration (grader weight 20% + R10 auto-fail) — **highest priority**

- **[R1][P1·CHANGE]** Replace the shipped give-up thresholds with the abstention gate
  `graded_reviews≥300 AND coverage≥70% (≥7/10 areas) AND held_out_probe_items≥50 AND half-width≤W_max`,
  else `"READINESS: insufficient data"`. _(El-Yaniv & Wiener 2010; Castro 2025.)_
- **[R2][P3·CHANGE]** Never emit a **point** pass-probability — emit a **band**: Beta-Binomial mean w/
  Jeffreys prior `(x+0.5)/(n+1)`; Wilson/Jeffreys 90% interval; output as **distance-to-1600 scaled band**.
- **[R3][P3·CHANGE]** Calibrate with **Platt/temperature or Venn-Abers/Beta**, **not isotonic** for
  sparse data (`n<~1000→Platt`). _(Niculescu-Mizil & Caruana 2005; Vovk & Petej.)_
- **[R4][P3·CHANGE]** Readiness backbone = **IRT / PFA / LKT (logistic)**, **not deep knowledge tracing**
  (DKT's edge is a leakage artifact). Cap predictors by EPP; topic weights as fixed priors; shrinkage ≥0.9.
- **[R5][P3·ADD]** A **second** honest number: IRT **classification accuracy/consistency at the MPS**
  ("confidence of the pass/fail call"), distinct from P(pass). _(Rudner 2005.)_
- **[R6][P3·ADD]** **Conformal** backstop w/ Small-Sample Beta Correction once a held-out bank exists.
- **[R25][P3·CHANGE]** Cap certainty by `mock↔exam corr≈0.7`; readiness gate = configurable **68–75%** band
  (300Hours revised L1 target to 68%), not a hardcoded 70%.

### B. Held-out testing & the ablation (12% + 15%)

- **[R7][P3·CHANGE]** Held-out proof must use **delayed (≥1 week) application MCQs**, not immediate
  accuracy (transfer is delay-sensitive: Rohrer 2015 d 0.42→0.79 @30d). This _is_ challenge 7d.
- **[R8][P3·ADD]** SPOV1 ablation arm: **within-topic vs cross-topic** cluster credit; default ZERO
  performance-transfer credit when success+congruency+feedback absent.
- **[R19 — DROPPED per owner decision]** The study-feature ablation stays **three arms** — **full /
  feature-off / vanilla** (rubric §8). The research-suggested 4th mis-targeted-adjacency arm is not
  used; signed-gate correctness is covered by unit tests instead.

### C. Contrast scheduling (SPOV3, refines shipped `contrast.rs`)

- **[R18a][P1·CHANGE] Engine refinements stay Phase 1 (label-free):** **contiguity (C3)** — run
  the contrast pass on the _merged_ queue so the Intersperser can't split a pair (lag 0–1) — and
  the **sibling-adjacency guard (C10)** keyed on `note_id`. Phase 1 contrast runs on the deck's
  **existing** cluster/topic tags. _(Birnbaum 2013 contiguity + separable spacing.)_
- **[R18b → Phase 2] Signed confusability GATE moves to Phase 2:** the confusability gate +
  `confusable::high` marker + `contrast_confusable_tag` field move to **Phase 2** (reason: the
  marker needs **manual per-pair SME judgment**, or the **computed** behavioral revlog-confusion
  signal — `surface_similarity × discrimination_need`, within-topic, validated, must beat
  BM25/vector, R21). Phase 1 stays AI-free with no new manual-labeling burden; the LIMITATION
  (interleaving merely-similar pairs → the d=0.76 blocking-loss risk) is accepted in Phase 1 and
  resolved by the Phase-2 gate. _(Carvalho & Goldstone 2014 d=0.76.)_

### D. Fade ladder (SPOV2, Phase 2 — not yet built)

- **[R9][P2·ADD]** **Mandatory feedback after every rung** (engine invariant) — omitted in v1.
- **[R10][P2·CHANGE]** Fade signal = **predicted retrievability at the exam horizon** (fitted FSRS-6 decay),
  not raw stability nor instantaneous R.
- **[R11][P2·CHANGE]** Two-sided fade band with **hysteresis** (fade-UP threshold > fade-DOWN).
- **[R12][P2·CHANGE]** Promotion gate = **~3 spaced successful relearning passes**, not a within-session
  criterion. _(Vaughn 2016 relearning-override.)_
- **[R13][P2·ADD]** **Comprehension-first + minimum-fluency** preconditions before solve/adjacency (A/B-tested).
- **[R14][P2·CHANGE]** Format congruency = **continuous multiplier** (matched 1.0 / mismatched ≈0.75), not a gate.
- **[R15][P2·CHANGE]** Fade order = **mastery-driven**; expose backward-vs-forward as an ablation dimension.
- **[R16][P2·DROP-default]** Self-explanation prompts = **ablatable, OFF by default** (negative moderator).
- **[R17][P2·CHANGE]** Enable the ladder **only for high-element-interactivity (formula) clusters**; atomic facts stay on plain FSRS.
- **[R20][P2·ADD]** **Side-by-side compare card** for the tightest confusables (duration trio, FIFO/LIFO), scoped small.

### E. AI pipeline (15% — "beats a simpler method")

- **[R21][P2·CHANGE]** Beat **BOTH tuned BM25 AND tuned dense** at the same cutoff on held-out qrels; win via
  **BM25+dense → RRF(k=60) → cross-encoder rerank**; report precision@k **and** latency. _(BEIR 2021; Cormack 2009; Rosa 2022.)_
- **[R22][P2·ADD]** **Misconception-grounded** distractor generation seeded from the CFA confusable set; retire
  distractors chosen by <5%.
- **[R23][P2·ADD]** **Two-stage generate→validate** AIG with adversarial SME gold sign-off (≥100–200 gold items,
  pre-registered cutoff, self-consistency solve-check). Review must be adversarial (automation bias increases flaws).
- **[R24][P2/3·ADD]** Never let **ungraded** generated items feed readiness; track per-item point-biserial; auto-retire non-discriminating items.

### F. Config, engine baseline, hygiene

- **[R26][P1·CHANGE]** Topic-weight config as **(min, max, midpoint)**, versioned by exam year; single
  weighted-overall readiness target (no topic-level cutoffs); over-weight **Ethics** near the MPS boundary.
- **[R27][P3·CHANGE — mobile]** **Rebase onto the `25.09.2` tag** rsdroid 0.1.64 pins (currently on the 26.05
  dev-line), or the "same commit builds desktop + phone" story breaks (R2 70% cap risk).
- **[R28][P1·note]** Mastery gate ultimately keys on spaced-session count (Phase 2); within-topic-only cluster credit.
- **[R29][all·ADD]** Apply citation-hygiene fixes in every writeup: **Goncalves** (not Ferreira) 2025;
  **Endres & Renkl** (not Rummer & Schweppe) 2015; **Zheng 2023** (not 2024); **Pham et al., Medical Teacher
  2025** (not the Wu preprint); **Zengaffinen et al. 2026** (author, not "DeepSeek-V3.2"); **St. Hilaire &
  Carpenter 2023** (upgrade from the dissertation); the ">75% initial success" figure is a **heuristic**, not Rowland-stated.

---

## What did NOT change (validated as-is)

- The core three-gauge Memory→Performance→Readiness framing, the tag-encoded edge model (no new synced
  table), and the mastery/dashboard/concept-graph RPCs are sound and stay.
- SPOV3 remains the strongest, most-defensible study feature to headline the ablation.
- FSRS remains the memory engine (FSRS ≫ SM-2 confirmed), but **no FSRS value means "mastered"** — mastery
  is a validated product decision, not a threshold read off retrievability.
