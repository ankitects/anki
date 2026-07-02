# Phase 1 Plan V2 — Contrast Scheduling for CFA (SPOV 1 + SPOV 3), evidence-refined

> ⚠️ **GRILLING ERRATA — see [`GRILLING_NOTES.md`](./GRILLING_NOTES.md), which SUPERSEDES conflicting
> text below.** Corrected here: **C11** (FSRS is off by default → all gauges must ABSTAIN, not use the
> `reviewed/seen` proxy); **C13** (demo deck is flat-tagged — curate `cluster::`/`confusable::high` as
> checked-in data + make contrast a no-op when absent, never first-tag _blocking_); **C3** (lag-0-1
> contiguity is impossible under the default Intersperser — run contrast on the _merged_ queue);
> **C10** (the "same-card spacing guard" is a no-op → reframe as a sibling-adjacency guard on `note_id`);
> **C12** (topic weights are hardcoded & stale — ship `cfa_weights_2026.json` (min,max,midpoint); fix
> PM/AltInv/Deriv; relabel "mastered" → "high recall probability"); **C14** (expose RPCs via the Python
> `Collection` wrapper). Readiness abstention thresholds live in Phase 3 (**C5**).

> Companion to `brainlift.md`, `PRD.md`, and `RESEARCH_ADDENDUM.md`. Supersedes
> `PHASE1_PLAN.md` (v1). Phase 1 ships **two Rust artifacts** on an existing tagged
> CFA recall deck (`453127574`) — **no AI, no content generation**:
>
> 1. **`SPOV 1` + `SPOV 3` — contrast scheduling** (tag-derived edge/cluster platform
>    surfacing confusable siblings), behind an on/off toggle. **SHIPPED** — Phase 1 runs
>    on the deck's **EXISTING cluster/topic tags** (as `contrast.rs` already does, incl.
>    its first-content-tag fallback and topic alias map) with label-free engine-correctness
>    refinements (contiguity **C3**, sibling-adjacency **C10**). The _signed confusability
>    gate_ (marker tag + `contrast_confusable_tag` field) is **[R18 → Phase 2]** (needs a
>    manual `confusable::high` label / the computed behavioral signal).
> 2. **A per-topic mastery / metrics RPC** (extends `StatsService`) powering the
>    Wednesday **Memory** dashboard. **SHIPPED**.
>
> Most of v1 is implemented (see recon). V2's job is to bound the shipped code with the
> measured moderators from the research so the honesty (20%), ablation (15%), and
> held-out (12%) rubric areas hold up. `[R#]` = CONCRETE PLAN CHANGE from
> `RESEARCH_ADDENDUM.md §4`. Cross-refs to `PHASE3_PLAN_V2.md` for calibration/mobile.

## What changed vs PHASE1_PLAN.md (v1)

- **[R1] Readiness give-up is a live R10 AUTO-FAIL risk.** Shipped gate is
  `graded_reviews ≥ 15 & coverage ≥ 1%` (`MIN_GRADED_REVIEWS=15`, `MIN_COVERAGE=0.01`
  in `ts/routes/dashboard/metrics.ts`). Replace with **≥300 graded reviews AND ≥70%
  topic coverage (≥7/10 areas) AND ≥50 held-out probe items AND interval half-width ≤
  W_max**, else `"READINESS: insufficient data."` (Selective prediction / Chow;
  mock↔exam validity only r≈0.71–0.76.) Band/calibration math lives in Phase 3 →
  cross-ref `PHASE3_PLAN_V2.md` [R2–R6].
- **[R18 → Phase 2] The SIGNED confusability GATE moves to Phase 2.** Assigning the
  `confusable::high` marker requires **manual per-pair SME judgment**, which Phase 1 must
  avoid (Phase 1 stays AI-free _and_ free of a new confusability-labeling burden). Phase 1
  contrast therefore runs on the deck's **EXISTING cluster/topic tags** — the shipped
  `contrast.rs` behavior (first-content-tag fallback + topic alias map), **NO new
  `confusable::high` labeling and NO new `contrast_confusable_tag` field**. In Phase 2 the
  confusability SIGNAL is either **COMPUTED** (behavioral confusion-mining from the
  revlog × discrimination-need, validated, must beat BM25/vector = the R21 deliverable) or
  **CURATED** as part of the Phase-2 content-authoring pass. Cross-ref `PHASE2_PLAN_V2.md`.
- **Kept in Phase 1 (all label-free — need NO confusability marker):** the shipped
  cluster-based interleaving; engine-correctness refinements **C3** (run the contrast pass
  on the _merged_ queue so the Intersperser can't split pairs) and **C10** (reframe the
  "same-card guard" as a **sibling-adjacency guard keyed on `note_id`**); the **[R1]**
  readiness give-up fix; **[R26]** topic-weight config; **C11/C12/C14**. The **[R28]**
  within-topic constraint stays but keys on **existing** `cfa::topic::*` tags / the shipped
  topic alias map — not new manual tags.
- **LIMITATION (Phase 1, ACKNOWLEDGED + RESOLVED in Phase 2):** without a confusability
  signal, Phase 1 contrast may interleave merely-similar (non-confusable) clusters, risking
  the **Carvalho & Goldstone (2014) d=0.76 blocking-loss** on dissimilar pairs. This is
  accepted for Phase 1 and resolved by the Phase-2 signed gate. The **Phase 1 3-arm
  ablation compares contrast-as-shipped: full (contrast ON) / feature-off (contrast OFF) /
  vanilla Anki**; the confusability-GATED contrast is a **Phase 2** experiment.
- **Ablation stays THREE arms** (owner decision): **full (contrast ON) / feature-off
  (contrast OFF) / vanilla Anki** — the standard rubric §8 design. The research-suggested
  4th "mis-targeted-adjacency" arm is **dropped**; signed-gate correctness is verified by
  unit tests instead.
- **[R26] Topic weights ship as (min, max, midpoint), versioned by exam year**;
  allocate by midpoint, carry range as uncertainty; single weighted-overall readiness
  target (no per-topic cutoffs), keep per-cluster learning gates; over-weight Ethics
  near the MPS boundary.
- **[R28] Mastery gate should ultimately key on spaced-session count** (Phase 2), and
  **cluster credit is within-topic-only** (St. Hilaire general g=0.04) — a design
  constraint + a free ablation arm (cross-ref `PHASE3_PLAN_V2.md` [R8]).
- **Honest anchors corrected:** adjacency (not spacing) is the active ingredient;
  math-like interleaving anchor is **g=0.34** (not 0.66); the memory→performance bridge
  must be proven on **DELAYED held-out application MCQs** (challenge 7d paraphrase test,
  ≥7-day delay), not immediate accuracy.
- Engine baseline mismatch (branch on 26.05 dev-line, not the `25.09.2` tag rsdroid
  0.1.64 pins) is a mobile-story risk → carried in Phase 3 [R27]; flagged here in M5.

## Scope

**In scope (unchanged core, refined):** `SPOV 1` tag-derived edges/clusters; `SPOV 3`
contrast scheduling on the deck's **existing cluster/topic tags** (as `contrast.rs` ships)
with label-free engine-correctness refinements (**C3** merged-queue contiguity, **C10**
sibling-adjacency); mastery/metrics RPC over the 10-area tag taxonomy; on/off toggle
(ablation-ready — 3 arms: full/off/vanilla); corrected readiness abstention [R1].

**Constraints:** No AI (no LLM generation, no embeddings/ML similarity, no agentic
pipelines). No new study content. **No new manual confusability-labeling burden** — Phase 1
must stay minimal, so there is **NO `confusable::high` marker tag and NO signed
confusability gate in Phase 1**. FSRS is baseline infra, allowed. The confusability SIGNAL
(and the `contrast_confusable_tag` field that gates on it) belongs to **Phase 2** — where it
is either COMPUTED from behavioral revlog confusion-mining (must beat BM25/vector, R21) or
hand-curated during the Phase-2 content-authoring pass **[R18 → Phase 2]**.

**Out of scope (Phase 2+):** the SIGNED confusability gate + `confusable::high` marker +
`contrast_confusable_tag` field **[R18 → Phase 2]**; SPOV 2 fade ladder, dependency edges,
FSRS-driven fade/gating, MCQ/AIG content, side-by-side compare cards, calibration backbone.

## Why Phase 1 is cheap (unchanged)

The deck is a **recall deck**, so confusables are **separate notes**. `SPOV 3` is **pure
reordering** — no gating, no re-gating, no limit/count bookkeeping, no schema migration.
Edges are the existing tags. (Sibling-burying only touches same-note cards, irrelevant
to cross-note confusables; the lever is _ordering_.)

## Inputs

- **Deck:** AnkiWeb shared deck `453127574` (tagged CFA recall cards).
- **Edges = EXISTING tags:** a **cluster** = notes sharing a `contrast_tag_prefix` (or the
  shipped first-content-tag fallback + topic alias map). Phase 1 uses these **as-is** — no
  new labels.
- **[R18 → Phase 2] Confusability marker (`confusable::high`) is Phase 2**, not a Phase-1
  input — it requires manual per-pair SME judgment (or the computed behavioral signal).

---

## Milestones

### M0 — Tag taxonomy (data prep, no code) **[REVISE]**

- **Topic level = the 10 CFA areas** under `cfa::topic::*`. Drives mastery RPC, coverage,
  readiness weights. Normalize deck topics onto the official 10; bulk-tag sub-decks.
- **Cluster level = EXISTING cluster/topic tags** via `cluster::*` (`cluster::fi::duration`,
  `cluster::quant::inventory_cost_flow`, `cluster::ethics::standards`) — or the shipped
  `contrast.rs` first-content-tag fallback + topic alias map. Drives contrast **as shipped**.
- **[R18 → Phase 2] NO confusability curation in Phase 1.** Marking the tight families
  with a `confusable::high` label requires **manual per-pair SME judgment**; that task —
  and the signed gate it feeds — **moves to Phase 2** (either computed from behavioral
  revlog confusion-mining, R21, or curated during the Phase-2 content-authoring pass).
  Phase 1 contrast simply runs on the existing cluster/topic tags. _LIMITATION:_ without
  the signal, contrast may interleave merely-similar groups (Carvalho & Goldstone d=0.76
  blocking-loss risk) — accepted for Phase 1, resolved in Phase 2.
- **[R26] NEW — topic-weight config** `(min, max, midpoint)` per area, versioned by exam
  year, checked in as data (e.g. `cfa_weights_2026.json`). 2025/26 L1 ranges: Ethics
  15–20, Quant 6–9, Econ 6–9, FSA 11–14, Corp 6–9, Equity 11–14, FI 11–14, Deriv 5–8,
  AltInv 7–10, PM 8–12. Allocate/weight by midpoint; carry (max−min) as uncertainty.
- **Verify-first** in Browse tag sidebar: each card maps to exactly one of the 10 areas;
  each target confusable family is isolable by its existing cluster/topic prefix. (No
  `confusable::high` marker in Phase 1 — that is a Phase-2 task.)

### M1 — Feature toggle **[DONE]**

- `contrast_scheduling` = proto field **47**, `contrast_tag_prefix` = field **48** in
  `proto/anki/deck_config.proto` (verified). Default in `rslib/src/deckconfig/mod.rs`;
  UI row in `ts/routes/deck-options/`; schema11 round-trip; read via
  `sort_options.contrast_scheduling` in `QueueBuilder`.
- **[R18 → Phase 2] NO new gate field in Phase 1.** The `contrast_confusable_tag` field
  (and the signed-gate check it drives) is **added in Phase 2**, where the confusability
  signal is computed/curated. Phase 1 ships only the existing 47/48 toggles.

### M2 — Engine: contrast pass (SPOV 1 platform + SPOV 3 ordering) **[REVISE]**

Shipped in `rslib/src/scheduler/queue/builder/contrast.rs`: `load_contrast_clusters()`
(one batch tag query, `by_note` map) + `apply_contrast()` interleaving via
`interleave_clusters()` in runs ≤ `CONTRAST_CHUNK = 4`, called between `gather_cards()`
and `build()`; empty-prefix → first content tag fallback; `IGNORED_CLUSTER_TAGS` filter.
5 Rust tests. Pure reordering, undo-safe. Refinements:

- **[R18 → Phase 2] NO signed similarity gate in Phase 1.** Phase 1 runs contrast on the
  **existing** cluster/topic tags via the shipped `cluster_for_tags` resolution (incl. the
  first-content-tag fallback + topic alias map) — it does **not** read a
  `contrast_confusable_tag` marker. The gate that forces adjacency only above a
  confusability threshold (and falls back to SRS spacing/blocking below it) is a **Phase 2**
  addition. _LIMITATION (accepted, resolved in Phase 2):_ without the gate, contrast may
  interleave merely-similar clusters — the Carvalho & Goldstone d=0.76 blocking-loss risk.
- **[C3] Contiguity on the MERGED queue (label-free).** Run the contrast pass on the
  _merged_ `main` queue (after `merge_new`/`merge_day_learning`), so the `Intersperser`
  can't splice unrelated cards **between** the two members of a paired cluster; cluster over
  the new∪review union. Keep `CONTRAST_CHUNK` small (lag 0–1, no fillers). Needs a
  review-inclusive test (the current test is all-new).
- **[C10] Sibling-adjacency guard keyed on `note_id` (label-free).** Reframe the old
  "same-card spacing guard" (a no-op — a card appears once per pile per build) as a guard
  that avoids placing two _templates of the same note_ adjacently; use
  `NewCard.template_index`. This needs no confusability label.
- **[R28 constraint] Within-topic only, on EXISTING topic tags.** A cluster must not span
  two `cfa::topic::*` areas; cross-topic "confusables" get no adjacency credit (St. Hilaire
  general g=0.04). Enforce by requiring cluster members to share an **existing** topic prefix
  (via the shipped topic alias map) — **no new manual tags**; log/skip cross-topic.
- Cross-pile (new ↔ review) contrast: the **C3 merged-queue** pass covers this within Phase 1.

### M2b — Mastery / metrics RPC **[DONE]** (one caveat)

- `TopicMastery` RPC in `proto/anki/stats.proto` (line 22), impl
  `rslib/src/stats/mastery.rs`: single SQL pass via
  `searched_cards_retrievability_and_tags`, groups by topic prefix, returns
  `{total, mastered, average_retrievability}`; `DEFAULT_MASTERED_THRESHOLD = 0.9`.
  2 Rust + 1 Python test. `GetDashboard` + `GetConceptGraph` RPCs also shipped
  (`stats/dashboard.rs`, `stats/concept_graph.rs`, co-occurrence edges only).
- **[R28 note — caveat, not a Phase-1 change] "mastered = retrievability ≥ 0.9" is a
  scheduling target, not a competence threshold** (T6: no FSRS-defined "mastered";
  per-card AUC only ≈0.63–0.70). Keep the RPC as-is (aggregate signal) but label the
  gauge "high recall probability," not "mastered," and drive the _promotion_ gate off
  **spaced-session count** in Phase 2 — not off a single card's R. Aggregate over many
  cards for readiness; never hard-gate on one card. (Documentation/labeling fix.)

### M3 — Ablation toggle (running the experiment **[Deferred]**, design **[REVISE]**)

- **Three arms** (rubric §8), not two: **(1) full** — contrast ON **as shipped** (interleave
  on the existing cluster/topic tags, with C3 contiguity + C10 sibling-adjacency); **(2)
  feature-off** — contrast toggle OFF; **(3) vanilla** — baseline Anki (no contrast field).
  The confusability-GATED variant (adjacency only above a confusability threshold) is a
  **Phase 2** experiment, not a Phase-1 ablation arm.
- **[R28 — separate Phase-3 readiness analysis, NOT a 4th contrast-ablation arm] within-topic
  vs cross-topic cluster credit** — reserved for Phase 3
  readiness scoring, but the tag structure to support it is laid here (topic-scoped
  clusters). Cross-ref `PHASE3_PLAN_V2.md` [R8].
- Same deck, same cards, equal study time, equal review counts across arms; deck presets
  / study blocks per arm. **Running** the runs remains deferred to the measurement phase.

### M4 — Measurement **[REVISE]** (live Memory gauge in scope; A/B deferred)

- **Memory gauge (in scope):** `TopicMastery` per-topic recall with a range. Relabel per
  M2b caveat.
- **[R1] Readiness gauge — REVISE the give-up rule now (highest-leverage honesty fix).**
  In `ts/routes/dashboard/metrics.ts` replace the shipped abstain block
  (`gradedReviews < MIN_GRADED_REVIEWS(=15) || coverage < MIN_COVERAGE(=0.01)`) with:
  emit P(pass) **only if** `graded_reviews ≥ 300` AND `topic_coverage ≥ 0.70` (≥7/10
  areas covered) AND `held_out_probe_items ≥ 50` AND `interval_half_width ≤ W_max`;
  otherwise render `"READINESS: insufficient data."` Add `HELD_OUT_PROBE_ITEMS` and
  `W_MAX` config; thread probe-item count in from the held-out bank. The actual
  band/point math (Beta-Binomial Jeffreys, Platt/Venn-Abers, distance-to-1600) is Phase
  3 → cross-ref `PHASE3_PLAN_V2.md` [R2–R6]. Until then the gauge abstains by default —
  which is the honest and rubric-safe behavior.
  - **Preserve dev/test-ability (intent behind the original 15/1% values).** The shipped
    low thresholds were a deliberate affordance to force the code path and verify the
    readiness pipeline renders end-to-end. Keep that: make the gates **configurable**
    (deck-config / env / test fixture) rather than hardcoded constants, with the honest
    gate as the **default that ships and demos**, and expose an explicitly **LABELED test
    mode** ("test data — not a real prediction") so a number can be shown during dev
    without ever being mistaken for a measurement. Note the two senses of "does it work":
    a visible number proves the **plumbing** (RPC→weighting→logistic→render) but NOT
    **accuracy** — the current point is `logistic(k·(recall×TRANSFER[]))` with a _guessed_
    `TRANSFER[]`, so accuracy is only demonstrable via a calibration curve on the real
    delayed held-out outcomes (the 30×2 set), never from thin live data.
- **[R26] Readiness weighting.** Single weighted-overall target using topic **midpoint**
  weights; no per-topic pass cutoffs; keep per-cluster _learning_ gates. Over-weight
  **Ethics** when the estimate sits near the MPS boundary (dual role: largest weight +
  tie-break).
- **[R7/bridge — DEFERRED design, stated here] Held-out = DELAYED application MCQs.** The
  memory→performance bridge (challenge 7d) must be measured on **held-out, ≥7-day-delayed
  paraphrased application MCQs** (30 cards × 2 reworded; report the gap), NOT immediate
  accuracy — discrimination/transfer benefits are delay-sensitive (Rohrer 2015
  d=0.42→0.79 at 30 days). Honest interleaving anchor for math-like material is
  **g=0.34** (not 0.66). Bank construction/leakage-scan is Phase 3.

### M5 — Build, run, analyze **[REVISE]**

- `just check`; verify the toggle changes queue order (existing-tag cluster siblings land
  adjacent when ON, at lag 0–1 on the merged queue per C3; sibling templates of one note
  never adjoin per C10). (The `confusable::high`-gated ordering is a Phase-2 check.)
- **Mobile-story risk (carry-over):** branch merge-base is the **26.05 dev-line**, not
  the **`25.09.2`** tag that rsdroid `0.1.64` pins — this breaks "same commit builds
  desktop + AnkiDroid" and risks the 70% cap on R2. Rebase-onto-`25.09.2` is tracked in
  Phase 3 [R27]; do the build spike before claiming the mobile deliverable.
- Running the 3-arm ablation + delayed held-out quiz remains **[Deferred]** to
  measurement.

---

## Deliverables

1. `contrast_scheduling` (47) + `contrast_tag_prefix` (48) toggle **[DONE]** (ablation-ready
   — 3 arms: full/off/vanilla). The `contrast_confusable_tag` signed gate is **[R18 →
   Phase 2]** (not shipped in Phase 1).
2. `build_queues` contrast pass **[DONE]**, refined with **label-free** engine-correctness
   fixes — **C3** merged-queue contiguity (lag 0–1), **C10** sibling-adjacency guard on
   `note_id`, and **[R28]** within-topic enforcement on existing topic tags. The
   confusability gate is **[R18 → Phase 2]**.
3. Per-topic mastery/metrics RPC (`StatsService`) powering Memory + coverage **[DONE]**;
   relabel "mastered" → "high recall probability" **[REVISE, R28]**.
4. Corrected Readiness abstention in `metrics.ts` (≥300 / ≥70% / ≥50 probes / half-width
   ≤ W_max) **[NEW, R1]**; topic-weight `(min,max,midpoint)` config **[NEW, R26]**.
5. ≥3 Rust unit tests + 1 Python test **[DONE]** (contrast ×5, mastery ×2, +1 Py); add
   a **review-inclusive** merged-queue contiguity test (C3), a sibling-adjacency test (C10),
   and a within-topic enforcement test **[NEW]**. (Signed-gate tests are **[R18 → Phase 2]**.)
6. Same engine commit running desktop + AnkiDroid build + desktop installer — **at risk**
   until rebased onto `25.09.2` (Phase 3 [R27]).
7. **[Deferred]:** 3-arm ablation run + delayed held-out application-MCQ quiz + write-up.

## Engine touch points (reference — verified against recon)

| Concern                                 | File / symbol                                                                                                                                                                                                                             |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Queue build entry                       | `Collection::build_queues` — `rslib/src/scheduler/queue/builder/mod.rs`                                                                                                                                                                   |
| Gather buckets                          | `QueueBuilder.new` / `.review` — `builder/gathering.rs`                                                                                                                                                                                   |
| Contrast pass **[REVISE — label-free]** | `rslib/src/scheduler/queue/builder/contrast.rs` — `load_contrast_clusters`, `apply_contrast`, `interleave_clusters`, `cluster_for_tags`, `CONTRAST_CHUNK=4`; run on the **merged** queue (C3); sibling-adjacency guard on `note_id` (C10) |
| Signed gate **[R18 → Phase 2]**         | `contrast_confusable_tag` field + `cluster_for_tags` gate check are **added in Phase 2**, not Phase 1                                                                                                                                     |
| Tags                                    | `notes.tags` (space-padded); `rslib/src/tags/`                                                                                                                                                                                            |
| Toggle (per-deck)                       | `proto/anki/deck_config.proto` fields 47/48; `rslib/src/deckconfig/mod.rs`; `ts/routes/deck-options/` (the gate field is Phase 2)                                                                                                         |
| Mastery RPC                             | `proto/anki/stats.proto:22` `TopicMastery`; `rslib/src/stats/mastery.rs` (`DEFAULT_MASTERED_THRESHOLD=0.9`); `searched_cards_retrievability_and_tags`                                                                                     |
| Dashboard / graph                       | `proto/anki/stats.proto:26,29`; `rslib/src/stats/dashboard.rs`, `concept_graph.rs` (co-occurrence edges only)                                                                                                                             |
| Readiness gate **[REVISE, R1]**         | `ts/routes/dashboard/metrics.ts` — `MIN_GRADED_REVIEWS`(15→300), `MIN_COVERAGE`(0.01→0.70), add `HELD_OUT_PROBE_ITEMS≥50`, `W_MAX`; abstain block ~L226                                                                                   |
| Topic weights **[NEW, R26]**            | `(min,max,midpoint)` data file, versioned by exam year; consumed by `metrics.ts` weighting                                                                                                                                                |
| Mobile build                            | `Anki-Android` + `Anki-Android-Backend` (rsdroid) `anki` submodule → fork @ `25.09.2`; `local_backend=true` (rebase risk, Phase 3 [R27])                                                                                                  |

## Risks & decisions

- **[R18 → Phase 2] The signed gate is a Phase-2 deliverable.** Confusable ≠ merely
  similar; wrong-side adjacency is a _measured loss_ (Carvalho & Goldstone d=0.76). Phase 1
  runs contrast on existing cluster/topic tags **without** the gate (LIMITATION accepted
  here, resolved in Phase 2). The gate needs either a manual `confusable::high` label (an
  SME burden Phase 1 avoids) or the computed behavioral signal that must beat BM25/vector
  (R21) — both are Phase 2.
- **[R1] Readiness honesty is the top R10 auto-fail lever.** Abstain by default until the
  four gates pass; never surface a point pass-probability from thin data. Coordinates
  with Phase 3 band/calibration — cross-ref `PHASE3_PLAN_V2.md`.
- **[R28] "Mastered" is a product decision, not an FSRS artifact.** Aggregate over cards;
  Phase 2 keys promotion on spaced-session count; within-topic-only cluster credit.
- **[C3/C10] Adjacency correctness is label-free in Phase 1** — run contrast on the merged
  queue (lag 0–1, no fillers) and guard sibling templates on `note_id`. The confusability
  gate that decides _which_ clusters to force-adjoin is **[R18 → Phase 2]**.
- **Ablation stays 3 arms** (full contrast-as-shipped / feature-off / vanilla) per rubric
  §8; the confusability-gated contrast is a **Phase 2** experiment, not a 4th arm.
- **Engine baseline must move to `25.09.2`** for the phone build (Phase 3 [R27]).
- **Mastery RPC perf:** single SQL pass (no per-card load) — met by the shipped impl.
- **Bridge is proven on DELAYED held-out application MCQs (7d paraphrase test)**, not
  immediate accuracy; honest math-like anchor g=0.34.
