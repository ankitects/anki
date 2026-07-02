# Phase 3 Plan — Readiness, Unification & the Rigorous Ablation

> Companion to `brainlift.md`, `PHASE1_PLAN.md`, `PHASE2_PLAN.md`, and `PRD.md`. Phase 3
> (Sunday) **completes the system**: unify the SPOVs over the **tag taxonomy** (cluster +
> rung), add the **Readiness** gauge (a documented pass-probability _method_ that **abstains**
> until calibration exists), the readiness-optimization allocation, and **generalize** beyond
> the curated slice (BYO / untagged decks).
>
> **Deferred** (see the `PRD.md` scope note): the **held-out mocks / calibration**, the
> **Performance/Readiness measurement**, and the **rigorous three-arm ablation run**. Phase 3's
> ablation deliverable remains the **unified on/off toggles**.

## Scope

**In scope**

- **Unification** — one graph-aware `build_queues` pass over the **tag taxonomy** (cluster
  interference edges + rung dependencies together); all three SPOVs running at once. **No
  synced edge table** — a first-class `card_relationships` table is added **only if needed**
  (and would require sync-protocol work incompatible with stock AnkiDroid/AnkiWeb).
- **Readiness gauge** — a **documented mapping** (`Σ topic_weight × performance` → logistic
  P(pass) at the assumed MPS band) that **abstains** from a single P(pass) until held-out-mock
  calibration exists; meanwhile it shows **decomposed components** (per-topic mastery, coverage
  %, expected-accuracy band) + an **"uncalibrated"** label + the **single best next topic**.
  Plus the **readiness-optimization allocation** (demoted SPOV 4: reps by `exam-weight ×
  marginal Δ pass-prob`, not uniform retention).
- **Generalization** — edge sourcing for **BYO / untagged decks** (AI cluster/rung proposals +
  behavioral confusion mining + confusability-signal similarity), validated before use.

**Deferred (measurement)**

- The **held-out mocks / calibration**, the **Performance & Readiness measurement**, and the
  **rigorous three-arm ablation run** — see the `PRD.md` scope note.

**Builds on Phases 1–2**

- Phase 1 contrast (interference edges) + Phase 2 fade (dependency edges + Performance gauge +
  content pipeline).

**Out of scope**

- General-purpose productization/shipping beyond what the experiment requires (optional future).

## What's new vs Phases 1–2

- The first two phases each shipped **one feature** in isolation. Phase 3 makes them **coexist on
  one graph**, adds the **third gauge (Readiness)** the app only _measures_ indirectly, and turns
  the informal per-feature checks into a **statistically meaningful experiment**.

## Milestones

### M0 — Unify the graph (over tags)

- Run **both** edge types through one `build_queues` graph pass over the tag taxonomy: contrast
  on **`cluster::*`** (interference) + gating/fading on **`rung::*`** (dependency). Resolve
  precedence when a card is in both (e.g., gate first, then order survivors by cluster).
- **No new synced data** — tags already sync. _(Optional, only-if-needed: a local-only
  `card_relationships` cache rebuilt per device from tags for query speed; a _synced_ edge table
  is out of scope due to the sync-protocol cost.)_

### M1 — Readiness gauge (method now; abstain until calibrated)

- Define & document the mapping: `Σ topic_weight × performance` (weights from config,
  normalized) → **logistic P(pass)** centered on an **assumed MPS band** (CFA never publishes
  the MPS → wide band).
- **Abstain** from a single P(pass) until held-out-mock calibration exists (deferred). Until
  then surface the **decomposable components** (per-topic mastery via the mastery RPC, coverage
  %, exam-weighted expected-accuracy band) under an **"uncalibrated — not yet validated"** label
  - the **best next topic** (lowest `weight × performance`). Honors the honesty rule + R10.

### M2 — Readiness-optimization allocation (the demoted SPOV 4)

- Card selection weighted by `exam-weight × marginal Δ pass-prob` (a Readiness-gradient
  selector); toggle it; ablate against vanilla uniform desired-retention.

### M3 — Held-out mock harness (calibration) — **[Deferred]**

- _(When undertaken)_ periodic **full held-out mocks** (a disjoint pool) as the ground truth
  Readiness calibrates against; **partition the held-out bank** into _Performance-probe_ vs
  _calibration-mock_ pools so Readiness isn't calibrated against its own inputs (avoid
  circularity). Deferred — out of current scope.

### M4 — Rigorous ablation — **[Deferred]**

- _(When undertaken)_ three arms — **feature ON / OFF / unmodified Anki** — on **equal total
  study time** and the **same content**; enough subjects/sessions for power; score **Memory**,
  **Performance**, **Readiness**; report per-SPOV contributions + the combined effect. Deferred
  — the current deliverable is the unified on/off toggles.

### M5 — Generalization (BYO / untagged decks)

- **AI edge sourcing** for decks without good tags: LLM tag/cluster proposals, **behavioral
  confusion mining** from review logs, and similarity **only with a confusability signal** (never
  raw embedding similarity). Validate proposed edges (human + behavioral) before use.
- Broaden content beyond the curated slice; maintain held-out hygiene at scale.

### M6 — Analyze & write up

- Does graph scheduling beat vanilla Anki on **Performance/Readiness at equal study time**? What
  does each SPOV contribute? How well is Readiness calibrated? Limitations and next steps.

## Deliverables

1. A **unified graph scheduler** over the tag taxonomy (cluster + rung in one `build_queues`
   pass) — no synced edge table.
2. A **Readiness gauge** with a documented mapping that **abstains** until calibrated (shows
   decomposed components + best-next-topic) + the **readiness-optimization** allocation toggle.
3. A **BYO/untagged-deck** edge-sourcing path (validated).
4. **[Deferred]:** held-out mock / calibration harness, Readiness calibration, and the rigorous
   three-arm ablation + write-up.

## Engine / system touch points (reference)

| Concern             | File / area                                                                                              |
| ------------------- | -------------------------------------------------------------------------------------------------------- |
| Unified graph pass  | `Collection::build_queues` — `rslib/src/scheduler/queue/builder/`                                        |
| Edges (over tags)   | `cluster::*` + `rung::*` on `notes.tags` (sync natively); optional local-only `card_relationships` cache |
| FSRS inputs         | `extract_fsrs_*` — `rslib/src/storage/sqlite.rs`; revlog                                                 |
| Readiness / metrics | new metrics module + RPC (`proto/`, `rslib/src/`)                                                        |
| Toggles             | `proto/anki/deck_config.proto`, `rslib/src/deckconfig/mod.rs`                                            |
| Behavioral mining   | `revlog` analysis (SQL)                                                                                  |

## Risks & decisions

- **Readiness abstains until calibrated** — real CFA results are coarse/late; show the _method_ +
  decomposed components, not an uncalibrated P(pass) (honesty rule + R10). Calibration **[Deferred]**.
- **Circularity** _(when calibrating)_ — keep Performance-probe and calibration-mock pools disjoint.
- **Experiment power** _(deferred)_ — the biggest practical risk to "proving" anything; needs
  enough subjects/sessions when the ablation is eventually run.
- **No synced edge table** — unify over tags; a first-class synced edge table is avoided (sync
  protocol cost + stock-client incompatibility) unless truly needed.
- **AI-sourced edge quality** — validate (human + behavioral); avoid similarity-only edges.
- **Generalization may dilute the effect** — broader/less-curated content can shrink the signal
  the curated slice showed.
