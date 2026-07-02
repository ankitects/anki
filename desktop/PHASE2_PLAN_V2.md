# Phase 2 Plan v2 (evidence-refined) — The Performance Layer (SPOV 2): worked → faded → solve

> ⚠️ **GRILLING ERRATA — see [`GRILLING_NOTES.md`](./GRILLING_NOTES.md), which SUPERSEDES conflicting
> text below.** Corrected here: **C1** (the fade-signal formula `(1+(19/81)·t/S)^decay` is
> mathematically wrong — reuse the engine's `extract_fsrs_retrievability` / `current_retrievability_seconds`
> at the exam horizon); **C2** (re-gate-on-answer via `clear_study_queues()` is excluded by design and a
> perf regression — gate at BUILD time, unlock on next build); **C7** (the full AI IR pipeline that
> "beats tuned BM25 AND tuned dense" is descoped to a defensible minimal slice — one cluster, ~20-30 qrels,
> gold ~50); **C9** (`format_congruency_mult`/self-explanation credit fields are inert — no scorer reads
> them; make self-explain a real template toggle only). The whole FSRS fade ladder (hysteresis +
> spaced-session gate) is **PLAN-ONLY** for the deadline (biggest engine risk, not a named challenge).

> Companion to `brainlift.md`, `PHASE1_PLAN.md`, `PRD.md`, and — new for v2 —
> `RESEARCH_ADDENDUM.md`. Phase 2 (Friday) builds the **Memory → Performance bridge**:
> `SPOV 2` (FSRS-driven faded worked examples) on the **same engine commit** that ships to
> desktop **and** the AnkiDroid build. AI is permitted **as an authoring-time-only validated
> accelerator** — **the review loop is AI-free by construction** (generated+validated cards and
> their sources are baked into the deck; no runtime AI calls, ever).
>
> **This v2 supersedes `PHASE2_PLAN.md`.** It preserves the original structure and its good
> parts (rung tags = no new table = native sync; custom self-graded MCQ; authoring-time AI) and
> refines the specifics against the research addendum. Every research-driven change is cited
> `[R#]` with a one-line evidence note and a concrete parameter.
>
> **Deferred this iteration** (see `PRD.md` scope note): the held-out MCQ **bank**, the
> **Performance calibration**, and the full **three-arm ablation run** — Phase 2's ablation
> deliverable remains the set of on/off toggles (now several, wired as ablation dimensions).

---

## What changed vs PHASE2_PLAN.md (v1) — delta section

The engine design is preserved (rung tags, no schema bump, self-graded custom MCQ, offline AI).
The following are the substantive refinements, each grounded in `RESEARCH_ADDENDUM.md`:

- **[R9] ADD mandatory feedback after EVERY rung (engine invariant).** v1 omitted this entirely.
  Retrieval without feedback is near-worthless (Goncalves 2025 g=0.14 → g=0.50 with feedback;
  Rowland 2014 feedback ~doubles the testing effect). Also add a `feedback_present` flag to the
  readiness moderator so credit is conditioned on it.
- **[R10] Fade signal = predicted retrievability at the EXAM horizon**, computed with the
  collection's **fitted per-user decay** (FSRS-6), NOT raw stability nor instantaneous R. v1 said
  "read stability off `data`"; instantaneous R is only a due-ness signal.
- **[R11] Fade band is two-sided WITH HYSTERESIS** (fade-UP threshold > fade-DOWN). v1 had a
  one-directional "gate rung k+1 behind rung k's threshold." Expertise reversal is asymmetric.
- **[R12] Promotion gate = spaced-session count**, not a within-session criterion or a fixed
  "fade every N" counter. v1 implied a single stability crossing.
- **[R13] Comprehension-first + minimum-fluency preconditions** before solve/adjacency (new).
- **[R14] Format congruency = continuous multiplier (0.75 mismatched), NOT a hard gate.** v1's
  implicit "solve must be MCQ" congruency is relaxed for intermediate rungs; terminal stays MCQ.
- **[R15] Fade order = mastery-driven**, not hardcoded backward (v1 M3 said "backward-fading").
  Expose backward-vs-forward as an ablation dimension.
- **[R16] Self-explanation prompts = OFF-by-default ablatable flag** (v1 implied bake-in). It is a
  _negative_ moderator in the strongest CFA-adjacent study (Barbieri 2023).
- **[R17] Fade ladder enabled ONLY for high-element-interactivity (formula) clusters;** atomic
  facts stay on plain FSRS. Requires an element-interactivity tag (new, testable hypothesis).
- **[R20] ADD a side-by-side compare card** for the tightest confusables, scoped small (new).
- **[R21] AI-retrieval eval must beat BOTH tuned BM25 AND tuned dense** at the same cutoff on
  held-out qrels (dense-only is a strawman). Pipeline: BM25 top-100 + dense top-100 → RRF(k=60)
  → cross-encoder rerank. v1 said "beats BM25 + plain vector" — sharpened and made adversarial.
- **[R22] Misconception-grounded distractors seeded from the known CFA confusable set;** retire
  distractors chosen by <5% of examinees (new, ties SPOV 2 → SPOV 3).
- **[R23] Two-stage generate→validate AIG with ADVERSARIAL human gold sign-off** (drafter +
  independent critic, self-consistency solve-check, pre-registered cutoff, gold set ≥100–200).
  v1 had a single gold-set checker; automation bias means review must be adversarial.
- **[R24] Never let ungraded generated items feed the readiness estimate;** track per-item
  point-biserial from live responses and auto-retire non-discriminating items (new).

---

## Scope

**In scope**

- `SPOV 2` — the worked → faded → solve ladder, with the **fade level read off predicted
  retrievability at the exam horizon** ([R10]) and gated by **spaced-session count** ([R12]).
- **Dependency model via rung tags** — `rung::worked|faded|solve` within a cluster; gating
  computed in `build_queues` from FSRS-derived signals + tags. **No `card_relationships` table**
  → no schema change, no sync-protocol work (rides native tag sync; works on stock AnkiDroid).
- **Mandatory feedback rung** ([R9]) — every rung ends in a reveal/feedback step, enforced as an
  engine invariant, not left to the note author.
- **Solve-rung format** — a **custom A/B/C MCQ note type + self-contained HTML/JS template**
  (self-graded), transfer-appropriate to the exam, with **misconception distractors seeded from
  the CFA confusable set** ([R22]). Terminal rung stays MCQ (exam-congruent) ([R14]).
- **Side-by-side compare card** for the tightest confusables (duration trio, FIFO/LIFO), scoped
  to a small high-value set ([R20]).
- **[R18 — moved from Phase 1] Signed confusability score gating SPOV3 adjacency.** Phase 1
  ships contrast on the deck's existing cluster/topic tags **without** a confusability gate
  (it may interleave merely-similar clusters — the Carvalho & Goldstone d=0.76 blocking-loss
  risk). Phase 2 adds the **signed gate**: force adjacency **only above a confusability
  threshold**; below it fall back to default SRS spacing/blocking. The confusability signal is
  produced Phase-2-appropriately — **(a) COMPUTED** (preferred; satisfies R21 "AI beats
  BM25/vector"): `confusability(a,b) = surface_similarity × discrimination_need`, grounded in
  **behavioral confusion-mining from the revlog** (error-substitution / co-occurrence), NOT
  raw embedding similarity, **within-topic only**, and **validated against held-out
  human/behavioral labels before use**; or **(b) CURATED** (authoring-time): any manual
  `confusable::high` labeling done as a Phase-2 SME task alongside AI item authoring. Adds the
  `contrast_confusable_tag` deck-config field and the gate check in the contrast cluster
  resolution.
- **AI (validated accelerator) — authoring-time only; runtime is AI-free by construction.**
  1. **Generation** — two-stage generate→validate (drafter + independent critic) with adversarial
     SME gold sign-off and a self-consistency solve-check pre-filter ([R23]).
  2. **Retrieval-for-grounding** — finds the source passage each card is grounded on and **beats
     BOTH tuned BM25 AND tuned dense** on precision@k at held-out qrels via RRF+rerank ([R21]).
- **Toggles / ablation dimensions** — `fade_enabled`, `fade_signal`, `fade_order`,
  `self_explain_enabled`, `element_interactivity_gate` on `DeckConfigInner`.

**Builds on Phase 1**

- Reuses the `contrast.rs` post-gather hook, the deck-config toggle mechanism, and the
  **two-level tag taxonomy** — now adding the **rung** level, an **element-interactivity** tag,
  and **gating**. (Phase 1 code state confirmed: toggles `contrast_scheduling`(47) /
  `contrast_tag_prefix`(48), `contrast.rs` reorder, `TopicMastery` RPC — all present.)

**Deferred (measurement / Phase 3)**

- The disjoint **held-out MCQ bank** (must be **delayed ≥1 week** application MCQs, not immediate
  accuracy), the **Performance gauge calibration** (Platt/Venn-Abers, Beta-Binomial band), and
  the full **three-arm ablation run** (full / feature-off / vanilla). Readiness-optimization allocation, SPOV
  unification, and BYO/untagged-deck edge sourcing remain Phase 3.

---

## What's genuinely new vs Phase 1

- **Gating, not just reordering.** Phase 1 only reordered cards. Phase 2 must **hold a rung back**
  until its prerequisite has passed **~3 spaced relearning sessions** ([R12]) and re-gate on
  answer, with a **two-sided hysteresis band** on the fade signal ([R11]).
- **Mandatory feedback** ([R9]) — a hard invariant that did not exist in v1.
- **Content + a custom note type.** Phase 2 _creates_ worked / faded / solve cards (incl. the
  custom MCQ note type and the compare card) for the narrow slice.
- **Element-interactivity scoping** ([R17]) — the ladder is opt-in per cluster, not global.

---

## Milestones

### M0 — Narrow the slice **[REVISE]**

- Pick **one or two high-element-interactivity formula clusters** (Fixed-Income duration family,
  or TVM) from the Phase 1 taxonomy — **explicitly NOT atomic-fact clusters** ([R17]).
- Tag each candidate cluster with an **element-interactivity level** so only formula clusters
  enter the ladder; atomic facts stay on plain FSRS. Tag schema: `interactivity::high|low` on the
  cluster's cards. _[R17] Evidence: Sweller CLT boundary — high-element-interactivity material is
  where worked→faded pays; treat as a testable hypothesis (guarded by `element_interactivity_gate`)._
- Prove the layer on this slice before breadth.

### M1 — Content pipeline (AI-accelerated, adversarially validated) **[REVISE]**

- **Two-stage generate→validate AIG** ([R23]): a **drafter** model proposes items; an
  **independent critic** model challenges each; a **self-consistency solve-check** pre-filter runs
  the item and rejects any with >1 correct answer (the #1 defect). Then **adversarial** human SME
  sign-off — 2 SMEs, pre-registered acceptance cutoff: (a) factual accuracy, (b) single-best-answer,
  (c) ≥3 functioning distractors. Gold set **≥100–200 human-vetted items**; accept at the observed
  keep-rate (~58–70%). _Review must be adversarial, not rubber-stamp — automation bias increases
  flaws. [R23] Evidence: Riehm 2026 (GRADE VERY LOW); Frontiers 2026 automation bias._
- **Misconception-grounded distractors** ([R22]): generate distractors **solve-first**, simulating
  the specific student errors in the **known CFA confusable set** (duration trio; FIFO/LIFO/WAC +
  LIFO-reserve direction; forwards/futures/swaps vs options), NOT surface similarity. This ties the
  performance layer to SPOV 3's confusable edges. _[R22] Evidence: DiVERT (EMNLP 2024) beats GPT-4o
  on misconception distractors; retire distractors chosen by <5% of examinees (auto-flag as
  non-functioning for regeneration)._
- **Numeric validation** for parameterized generators (random inputs → computed answer →
  error-pattern distractors) — every generator numerically checked before emitting notes.
- **Provenance/leakage wall**: the grounding corpus and the gold eval set are walled off from any
  generator prompt/fine-tune; log provenance; n-gram check that stems aren't verbatim from source.
- **Retrieval-for-grounding** ([R21]): index the source(s); for each card retrieve the supporting
  passage and store it as the card's **named source**. The **fair "beats a simpler method" claim**
  = beat **BOTH a tuned BM25 AND a tuned dense retriever** at the **same cutoff** on **held-out
  qrels**, reporting **precision@k AND latency**. Pipeline: **BM25 top-100 + dense top-100 →
  RRF(k=60) → cross-encoder rerank top-N**. _Dense-only is a strawman. [R21] Evidence: BEIR (2021,
  BM25 often wins OOD); Cormack RRF (2009, p<.003); Rosa (2022, cross-encoder +>4 nDCG at ~10–100×
  latency)._
- _(Deferred: the disjoint held-out MCQ bank for Performance scoring — and it must be **delayed**
  application MCQs, not immediate accuracy.)_

### M1b — Signed confusability gate for SPOV3 adjacency **[R18 — moved from Phase 1]**

Phase 1 shipped contrast on the deck's existing cluster/topic tags with label-free
engine-correctness refinements (C3 merged-queue contiguity, C10 sibling-adjacency), but with
**no confusability signal** — so it can force adjacency on merely-similar clusters, risking the
**Carvalho & Goldstone (2014) d=0.76 blocking-loss** on dissimilar pairs. Phase 2 resolves this
by producing a confusability signal (below) and **gating** adjacency on it. This label / signal
work belongs in Phase 2 because it needs either manual per-pair SME judgment or a computed
behavioral signal — both incompatible with Phase 1's AI-free, no-new-manual-labeling constraint.

- **[R18](a) COMPUTED signal (preferred — satisfies R21 "AI beats BM25/vector").**
  `confusability(a,b) = surface_similarity × discrimination_need`, where the core term is
  **behavioral confusion-mining from the revlog** (error-substitution / co-occurrence of
  lapses across the pair), **NOT raw embedding similarity**. Scope to **within-topic** pairs
  only (`cfa::topic::*`). **Validate against held-out human/behavioral labels before use** —
  the score must beat a BM25/vector-similarity baseline on those held-out labels (ties to the
  R21 evaluation discipline) before it is allowed to drive scheduling.
- **[R18](b) CURATED signal (authoring-time fallback).** If hand-curated at all, the manual
  `confusable::high` labeling is a **Phase-2 SME task** performed alongside the AI item
  authoring (M1) — never a Phase-1 burden.
- **[R18] Deck-config field + gate check.** Add **`contrast_confusable_tag`** (string, next
  free field) to `DeckConfigInner` (`proto/anki/deck_config.proto`,
  `rslib/src/deckconfig/mod.rs`); default `confusable::high`; empty → treat all clusters as
  gated-on (legacy ungated behavior, for the ablation OFF-gate arm). In the Phase-1
  `contrast.rs` `cluster_for_tags` / cluster resolution, **only force adjacency when the pair
  is above the confusability threshold** (carries the marker / scores high); below-threshold →
  leave in default SRS spacing/blocking. Proto change needs a full `just check`.
- **[R18] Ablation (still THREE arms).** The confusability-gated contrast experiment compares
  **gated contrast vs shipped-ungated (Phase-1) contrast vs vanilla Anki** — three arms, no 4th
  mis-targeted-adjacency arm. _[R18] Evidence: Carvalho & Goldstone (2014) blocking wins d=0.76
  for low-similarity pairs; Birnbaum (2013) contiguity + separable spacing d=0.75._

### M2 — Data model: rung + interactivity tags (no new table) **[REVISE]**

- Tag each card's **rung** (`rung::worked` / `rung::faded` / `rung::solve`) alongside its Phase-1
  `cluster::*` tag, and the cluster's **`interactivity::high|low`** tag ([R17]). The "dependency"
  is **rung order within a cluster** — **no `card_relationships` table, no schema bump**. Tags sync
  inside notes (native).
- Add a per-item **provenance/grading state** marker so the readiness estimate can exclude
  ungraded generated items ([R24]) — e.g. `aig::graded` vs `aig::ungraded` (a note tag, still no
  schema change). Ungraded generated items may be _studied_ but **never feed readiness**.

### M3 — Card variants **[REVISE]**

- **worked** — a plain template showing the full solution.
- **faded** — **cloze/completion** cards; fading via cloze numbering (`notetype/cardgen.rs`,
  `cloze.rs`); native `{{type:...}}` is fine. **Fade ORDER is mastery-driven** — fade the
  highest-stability step first — **NOT hardcoded backward**; `fade_order` is an ablation dimension
  (backward vs forward/adaptive). _[R15] Evidence: fade order is contested (Renkl 2002 backward>forward
  vs Moreno 2006 forward/adaptive>backward) — sidestep with mastery-driven ordering._
- **solve** — a **custom A/B/C MCQ note type** with a **self-contained HTML/CSS/JS template**
  (renders choices; **reveals correctness + explanation on tap = the mandatory feedback step**
  [R9]), **self-graded** via the normal Again/Good. No engine change; renders on desktop **and**
  the AnkiDroid webview — **verify on the emulator early** (the one real cross-platform risk).
- **compare** ([R20]) — a **side-by-side compare card** for the tightest confusables (duration
  trio, FIFO/LIFO): simultaneously displays the confusable pair with a discriminating prompt.
  Scoped to a **small high-value set** to avoid split-attention overload. _[R20] Evidence: Kang &
  Pashler (2012) simultaneous display ≈ interleaving for tight confusables; split-attention caveat
  (Kalyuga/Sweller) → keep the set small._
- **Feedback invariant** ([R9]): every rung template — worked, faded, solve, compare — MUST end in
  a reveal/feedback step. Enforce it at card generation (a template lint in M1) so no rung can ship
  without feedback. _[R9] Evidence: Goncalves 2025 (retrieval g=0.14 without feedback / g=0.50 with);
  Rowland 2014 (feedback ~doubles the testing effect)._
- **Self-explanation prompt** ([R16]) — available as an **OFF-by-default** template variant gated
  by `self_explain_enabled`, an ablation flag. Do NOT bake it in. _[R16] Evidence: Barbieri 2023
  (self-explanation a negative moderator); Miller-Cotto & Auxter 2019 (ecological reversal)._

### M4 — Engine: FSRS-driven fade gating **[REVISE]**

- In the `build_queues` hook (reusing the Phase-1 `contrast.rs` post-gather seam), for each card
  read its FSRS state and its `rung::` / `cluster::` / `interactivity::` tags.
- **Fade signal** ([R10]): compute **predicted retrievability at the exam horizon** using the
  collection's **fitted decay** — R(t) = (1 + (19/81)·t/S)^decay with `decay` read from the
  collection (FSRS-6 per-user), `t` = days from now to the configured exam date. Do NOT gate on raw
  `S` nor on instantaneous R. Store S and D via `extract_fsrs_variable(data,'s')` /
  `(data,'d')` in `storage/sqlite.rs`; compute R at horizon in Rust. _[R10] Evidence: T6 —
  instantaneous R is a due-ness signal; S is only interpretable relative to a retention target;
  there is no FSRS-defined "mastered" value._
- **Two-sided hysteresis band** ([R11]): if predicted-R < `fade_down_R` keep worked/cloze; if
  predicted-R > `fade_up_R` serve solve-MCQ; set **`fade_up_R` > `fade_down_R`** (asymmetric —
  harder to fade UP than to fall back DOWN). Suggested defaults (tunable, `fade_signal`-gated):
  `fade_down_R = 0.80`, `fade_up_R = 0.90`. _[R11] Evidence: expertise reversal is asymmetric —
  novices d=+0.505 > experts −0.428 (Tetzlaff 2025); Kalyuga 2007 two-sided band._
- **Promotion gate = spaced-session count** ([R12]): unlock rung k+1 only after the prereq rung has
  logged **~3 spaced successful relearning passes** — defined as **≥1 correct recall in each of ~3
  sessions, with FSRS stability implying a real inter-session gap** (not same-session repeats) —
  AND the **last attempt was correct**. Do NOT use a fixed "fade every N" counter or a high
  within-session criterion. Derive session-count from the review log timestamps + stability jumps.
  _[R12] Evidence: relearning-override effect (Vaughn 2016 — high initial criterion doesn't persist
  once spaced relearning occurs); Rawson & Dunlosky 2011 (3 correct + 3 spaced sessions)._
- **Comprehension-first + minimum-fluency preconditions** ([R13]): block the solve rung AND any
  confusable-adjacency until (a) **≥1 successful encoding** of the KC AND (b) **both cluster members
  are above an FSRS-stability floor**. The fluency floor is **A/B-tested and time-decaying** (relax
  as intervals lengthen). _[R13] Evidence: Redifer 2025 (cognitive load a significant negative
  mediator, B≈−0.55); Hinze 2013; Danzglock 2025 (provisional — hence A/B)._
- **Format congruency = continuous multiplier** ([R14]): do NOT block promotion because an early
  rung was cloze rather than MCQ. Apply a **transfer-credit multiplier** (matched format = 1.0,
  mismatched ≈ 0.75) rather than a hard gate; keep the **terminal rung MCQ** (exam-congruent).
  _[R14] Evidence: Yang 2021 (mismatched g=0.399 still significant; matched g=0.531); Pan & Rickard
  congruency b≈0.35._
- **Re-gate on answer**: `clear_study_queues()` (or a hook in `update_queues_after_answering_card`,
  `scheduler/queue/mod.rs`) so a newly-qualified prereq unlocks its dependent mid-session.
- Gated-out cards **must not** decrement deck limits (`LimitTreeMap`) — drop them the way burying
  does. **Main correctness risk** — a dependent must reappear when its prereq qualifies.

### M5 — Toggles / ablation dimensions **[REVISE]**

New/changed fields on `DeckConfigInner` (`proto/anki/deck_config.proto`,
`rslib/src/deckconfig/mod.rs`), all synced via deck config:

| Field                        | Values                                          | Purpose                                                                 | Cite  |
| ---------------------------- | ----------------------------------------------- | ----------------------------------------------------------------------- | ----- |
| `fade_enabled`               | bool                                            | ladder on / always-worked / always-cold                                 | v1    |
| `fade_signal`                | enum {exam_horizon_R, stability, success_count} | which fade signal                                                       | [R10] |
| `fade_up_R` / `fade_down_R`  | float                                           | hysteresis band bounds (up>down)                                        | [R11] |
| `promotion_spaced_sessions`  | int (default 3)                                 | spaced-session promotion gate                                           | [R12] |
| `fluency_stability_floor`    | float (A/B, time-decaying)                      | comprehension/fluency precondition                                      | [R13] |
| `format_congruency_mult`     | float (default 0.75)                            | mismatched-format credit multiplier                                     | [R14] |
| `fade_order`                 | enum {mastery, backward, forward}               | fade-order ablation dimension                                           | [R15] |
| `self_explain_enabled`       | bool (default OFF)                              | self-explanation ablation flag                                          | [R16] |
| `element_interactivity_gate` | bool                                            | restrict ladder to `interactivity::high`                                | [R17] |
| `contrast_confusable_tag`    | string (default `confusable::high`)             | signed confusability gate for SPOV3 adjacency; empty → ungated (legacy) | [R18] |

### M6 — Ablation toggle (experiment **[Deferred]**) **[REVISE]**

- **Current deliverable:** the toggle set above wired so ON / OFF / vanilla (and per-dimension
  ablations: `fade_order`, `self_explain_enabled`, `element_interactivity_gate`) _can_ be compared.
- **[Deferred]:** running the ablation + the **delayed held-out** Performance bank + the
  Memory/Performance write-up. (Ablation stays 3 arms: full / feature-off / vanilla.)

### M7 — Build, run, verify **[REVISE]**

- `just check` (proto changes need a full build); rebuild the rsdroid `.aar` from the **same
  commit** and verify the ladder + gating + feedback reveal + compare card on the **AnkiDroid
  emulator** early. (Recon note: branch merge-base is on a 26.05 dev-line, not the 25.09.2 tag
  rsdroid 0.1.64 pins — the "same commit builds desktop + phone" story needs the rebase called out
  in the addendum [R27]; flagged here as a build-verification dependency, resolved in Phase 3.)

---

## Deliverables

1. A **two-stage generate→validate** pipeline (drafter + critic + self-consistency solve-check +
   numeric validation) with **adversarial SME gold sign-off**, pre-registered cutoff, gold set
   ≥100–200 ([R23]); **misconception-grounded distractors** from the CFA confusable set ([R22]).
2. A **retrieval-for-grounding** component that attaches a named source and **beats BOTH tuned
   BM25 AND tuned dense** (precision@k + latency) via **RRF(k=60) + cross-encoder rerank** ([R21]).
3. **Rung + interactivity + provenance tags** (`rung::*`, `interactivity::*`, `aig::graded|ungraded`)
   on the slice — no schema change, syncs natively; ungraded items excluded from readiness ([R24]).
4. worked / faded / **custom-MCQ solve** / **compare** card variants ([R20]), all **feedback-terminated**
   ([R9]), self-graded, cross-platform; self-explanation as an OFF-by-default variant ([R16]).
5. **FSRS-driven gating** in `build_queues`: **exam-horizon-R fade signal** ([R10]), **two-sided
   hysteresis** ([R11]), **spaced-session promotion gate** ([R12]), **comprehension/fluency
   preconditions** ([R13]), **continuous format-congruency multiplier** ([R14]); re-gating; the full
   toggle/ablation set ([R15]/[R16]/[R17]).
6. **[R18 — moved from Phase 1] Signed confusability gate on SPOV3 adjacency**: the
   `contrast_confusable_tag` deck-config field + the gate check in `contrast.rs` cluster
   resolution (force adjacency only above threshold; below → default SRS spacing), driven by a
   **computed** behavioral-confusion-mining signal (`surface_similarity × discrimination_need`,
   within-topic, validated vs held-out labels, must beat BM25/vector — R21) or a **curated**
   authoring-time `confusable::high` label. Confusability-gated ablation = **3 arms** (gated /
   shipped-ungated / vanilla).
7. **[Deferred]:** delayed held-out MCQ bank, Performance calibration, full ablation + write-up.

---

## Engine touch points (reference)

| Concern                                    | File / symbol                                                                                                                                                          | Cite        |
| ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| Gating + fade selection                    | `Collection::build_queues`, `add_due_card`/`add_new_card`, Phase-1 `contrast.rs` post-gather seam — `rslib/src/scheduler/queue/builder/`                               | —           |
| FSRS state at queue time                   | `extract_fsrs_variable(data,'s')` / `(data,'d')` — `rslib/src/storage/sqlite.rs`                                                                                       | [R10]       |
| Predicted-R at exam horizon                | compute R(t)=(1+(19/81)·t/S)^decay with collection's fitted `decay`; `scheduler/fsrs/`                                                                                 | [R10]       |
| Spaced-session gate                        | review-log timestamps + stability jumps → session count — `scheduler/queue/builder/`                                                                                   | [R12]       |
| Re-gate on answer                          | `update_queues_after_answering_card` / `clear_study_queues` — `scheduler/queue/mod.rs`                                                                                 | —           |
| Rung/cluster/interactivity/provenance tags | `notes.tags` (`rung::*`, `cluster::*`, `interactivity::*`, `aig::*`); `rslib/src/tags/` (native sync)                                                                  | [R17][R24]  |
| Faded (cloze), mastery-order fade          | `notetype/cardgen.rs`, `rslib/src/cloze.rs`                                                                                                                            | [R15]       |
| Solve (custom MCQ) + feedback reveal       | new note type + self-contained HTML/JS (desktop + AnkiDroid)                                                                                                           | [R9]        |
| Compare card                               | side-by-side note type/template, small confusable set                                                                                                                  | [R20]       |
| Signed confusability gate (SPOV3)          | new `contrast_confusable_tag` field; gate check in `contrast.rs` `cluster_for_tags` / cluster resolution; computed revlog-confusion signal (offline) or curated marker | [R18]       |
| Toggles                                    | `proto/anki/deck_config.proto`, `rslib/src/deckconfig/mod.rs` (see M5 table)                                                                                           | [R11]–[R18] |
| AI (offline tooling)                       | drafter+critic generation, self-consistency solve-check, gold-set SME sign-off; RRF+rerank retrieval; BM25 + dense baselines                                           | [R21]–[R24] |

---

## Risks & decisions

- **FSRS state isn't in the lightweight gather structs** → SQL extraction (S, D) + compute R at
  horizon in Rust; watch query cost ([R10]).
- **Custom MCQ / compare cross-platform** → keep templates **dependency-free** and **self-grade**
  (no `pycmd`/JS-bridge grading — it diverges desktop vs AnkiDroid); verify on the emulator early.
- **Feedback invariant enforcement** ([R9]) — a rung shipping without a feedback step silently
  nulls the effect; enforce via a generation-time template lint, not convention.
- **Re-gating consistency** — the main correctness risk; a dependent must reappear when its prereq
  passes the spaced-session gate ([R12]).
- **Hysteresis tuning** ([R11]) — `fade_up_R`/`fade_down_R` defaults (0.90/0.80) are starting
  points; the asymmetry direction (up>down) is the evidence-backed invariant, the exact values are
  A/B fodder.
- **Element-interactivity gate is a hypothesis** ([R17]) — ship it behind
  `element_interactivity_gate` and treat "atomic facts don't benefit from the ladder" as testable.
- **Limit/count bookkeeping** for gated-out cards.
- **AI (authoring-time only):** runtime is AI-free by construction (no review-time AI calls);
  generation is **adversarially validated** with a pre-registered cutoff ([R23]); distractors are
  **misconception-grounded and pruned at <5%** ([R22]); retrieval **beats BOTH tuned BM25 and
  tuned dense** and supplies the **named source** ([R21]); **ungraded generated items never feed
  readiness** and non-discriminating items (point-biserial) are auto-retired ([R24]); train/test
  kept disjoint; prompt-injection in sources handled; **adversarial review guards automation bias**.
- **[R18] Confusability gate is make-or-break for SPOV3, and must not be a strawman.**
  Confusable ≠ merely similar; wrong-side adjacency is a _measured loss_ (Carvalho & Goldstone
  d=0.76). The computed signal must be **behavioral** (revlog confusion-mining), within-topic,
  and **validated vs held-out labels / beat BM25/vector** before it drives scheduling; a
  curated `confusable::high` label is the authoring-time fallback. This is why R18 is Phase 2,
  not Phase 1 (Phase 1 has no confusability signal and no manual-labeling budget).
- **No schema/sync work** — rung/cluster/interactivity/provenance tags ride native sync; a
  first-class synced edge table is deferred to Phase 3 "only if needed."
- **Supply scope** — keep to the narrow high-element-interactivity cluster(s) until the layer is
  proven ([R17]).
