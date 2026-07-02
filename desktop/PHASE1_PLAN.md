# Phase 1 Plan — Contrast Scheduling for CFA (SPOV 1 + SPOV 3)

> Companion to `brainlift.md` and `PRD.md`. Phase 1 (Wednesday) ships **two Rust
> artifacts** on an existing **tagged CFA recall deck** (`453127574`) — **no AI, no
> content generation, no agentic workflows**:
>
> 1. **`SPOV 1` + `SPOV 3` — contrast scheduling** (the tag-derived edge/cluster platform
>    - surfacing confusable siblings), behind an on/off toggle.
> 2. **A per-topic mastery / metrics RPC** (extends Anki's `StatsService`) that powers the
>    required Wednesday **Memory** dashboard, fast on 50k cards.
>
> **Ablation = the toggle only this iteration**; running the ON/OFF/vanilla experiment (and
> the discrimination quiz) is **[Deferred]** (see the `PRD.md` scope note). **Engine
> baseline pinned to anki `25.09.2`** so the same commit builds desktop **and** the
> AnkiDroid phone build (see _Engine baseline & mobile_ below).

## Scope

**In scope**

- `SPOV 1` — edges/clusters derived from the deck's existing **tags** (no generation, no edge authoring).
- `SPOV 3` — **contrast scheduling**: surface confusable cards together in the review queue.
- **Mastery / metrics RPC** — per-topic `{total, mastered, avg recall}` over the tag
  taxonomy, extending `StatsService` (the second graded Rust artifact; powers Memory).
- **On/off toggle** for contrast (ablation-ready). _(Running the two-arm ablation is
  **[Deferred]** — see `PRD.md`.)_

**Constraints**

- No AI: no LLM generation, no embeddings/similarity ML, no agentic pipelines.
- No new study content (use the existing deck as-is).
- FSRS is allowed — it's baseline Anki infrastructure, not a new AI component.

**Out of scope (phase 2+)**

- `SPOV 2` (worked → faded → solve), dependency edges, FSRS-driven fade/gating.
- A `card_relationships` table, proto edges, MCQ/problem content, item generators.

## Why Phase 1 is cheap (key simplification)

The deck is a **recall deck**, so confusables are **separate notes**, not same-note siblings.
That means `SPOV 3` here is **pure reordering**:

- **No gating** (we never hold a card back behind another card's state).
- **No re-gating** on answer, and **no limit/count bookkeeping** (reordering never drops cards).
- **No schema migration** and **no new content** — edges are just the existing tags.

(Native sibling-burying only affects _same-note_ cards, so it's irrelevant to cross-note
confusables; the lever is _ordering_, not un-burying.)

## Inputs

- **Deck:** AnkiWeb shared deck `453127574` (tagged CFA recall cards).
- **Edges = tags:** a confusable **cluster** = cards whose notes share a chosen tag prefix.

## Engine baseline & mobile (do the build spike first)

- **Single engine commit @ anki `25.09.2`.** Our fork HEAD is `25.09.2` + ~266 commits
  (the `26.05` dev line), but AnkiDroid's current backend (`anki-android-backend`
  `0.1.64-anki25.09.2`) pins exactly the **`25.09.2`** tag. Develop both Rust artifacts on a
  branch based at `25.09.2` so the **same commit builds desktop and phone** (identical proto
  → clean sync). The ~266 newer upstream commits are dropped for the engine; docs are
  version-independent.
- **Phone = AnkiDroid + rsdroid, local backend.** Clone `Anki-Android` and
  `Anki-Android-Backend` side-by-side; point the backend's `anki` submodule at our fork (@
  `25.09.2` + our change); build the `.aar` (NDK + Rust Android targets); set
  `local_backend=true` in `Anki-Android/local.properties` and match `BACKEND_VERSION`. Our
  new `contrast_scheduling` proto field rides along via rsdroid's proto codegen.
- **Verify on the emulator:** the contrast toggle (set on desktop, **synced** as deck
  config) changes queue order on the phone — no new AnkiDroid UI needed for v1.

---

## Milestones

### M0 — Tag taxonomy (data prep, no code) — _verify on `453127574` first_

One taxonomy, two levels, feeding **both** Rust artifacts + coverage + (later) readiness:

- **Topic level = the 10 CFA areas** under a configurable prefix (default `cfa::topic::*`,
  e.g. `cfa::topic::fixed_income`). Drives the **mastery RPC**, coverage, and readiness
  weights. Normalize the deck's topic tags/sub-decks onto the official 10 areas; if topics
  are encoded as **sub-decks**, bulk-add `cfa::topic::*` tags (Browse → select → Add Tag) so
  the engine has one mechanism.
- **Cluster level = confusable families** at LOS/sub-topic via `cluster::*` (e.g.
  `cluster::fi::duration`, `cluster::quant::inventory_cost_flow`, `cluster::ethics::standards`).
  Drives **contrast**. Curate **only the 2–4 families** you'll surface/demo (AI-free Browse
  bulk-tag). Rule: _cards whose note shares the chosen `cluster::` prefix → one cluster._
- **Verify-first:** in Browse → tag sidebar confirm (i) every card maps to exactly one of the
  10 areas, and (ii) each target confusable family is isolable by a prefix. Fix gaps by
  bulk-tagging **before** writing engine code. Pitfall: pointing contrast at _merely-similar_
  (non-confusable) groups — interleaving is neutral-to-negative there.

### M1 — Feature toggle

- Add `contrast_scheduling: bool` (and optionally `contrast_tag_level: string`) to
  `DeckConfig.Config` in `proto/anki/deck_config.proto`.
- Default in `rslib/src/deckconfig/mod.rs` (`DEFAULT_DECK_CONFIG_INNER`); UI row in
  `ts/routes/deck-options/`.
- Read it in `QueueBuilder::new` (`rslib/src/scheduler/queue/builder/mod.rs`).
- `just check` (proto changes need a full build).

### M2 — Engine: contrast pass (`SPOV 1` platform + `SPOV 3` ordering)

- Insert a post-gather hook in `Collection::build_queues`
  (`rslib/src/scheduler/queue/builder/mod.rs`) **between `gather_cards()` and `build()`**.
- In a new `rslib/src/scheduler/queue/builder/contrast.rs`:
  1. Collect gathered `note_id`s from `self.new` (`Vec<NewCard>`) and `self.review` (`Vec<DueCard>`).
  2. **Batch-load tags** for those notes (one query on `notes.tags`) → map each card to a
     cluster id (chosen tag prefix).
  3. **Reorder within `self.new` and within `self.review`** so same-cluster cards are adjacent
     (block or tight-interleave clusters), overriding the random tiebreaker.
- Leave cross-group mixing (`Intersperser` / `merge_new`) as-is for v1.
- Add an anti-bury skip only if a cluster contains _same-note_ siblings; otherwise no burying
  changes are needed.

### M2b — Mastery / metrics RPC (the second Rust artifact)

- Add a `TopicMastery` RPC to **`StatsService`** (`proto/anki/stats.proto`), implemented in a
  new `rslib/src/stats/` function (mirror `stats/graphs/retrievability.rs`).
- Group cards by the **topic prefix** (`cfa::topic::*`, prefix as a parameter) in **one SQL
  pass** over `notes.tags`; return per topic `{total, mastered, avg_retrievability}` where
  _mastered_ = retrievability ≥ a threshold (reuse `extract_fsrs_*` from `storage/sqlite.rs`).
- Must be **fast on 50k cards** (single query, no per-card load) — this is the backend for the
  required Wednesday **Memory** dashboard + coverage map.
- Call it from Python (the 1 Python test) and surface it with a range + the give-up rule.

### M3 — Ablation toggle (experiment **[Deferred]**)

- **Current deliverable:** the `contrast_scheduling` on/off toggle (ON vs vanilla) — the two
  arms _can_ be compared, but **running** the comparison is **[Deferred]**.
- (When run: same deck, same cards, equal study time, two deck presets / study blocks. The
  doc's third "bury" arm is N/A for cross-note confusables → contrast-vs-vanilla.)

### M4 — Measurement (**[Deferred]** except the live Memory gauge)

- **Memory gauge (in scope):** the **mastery RPC** (M2b) surfaces per-topic recall with a
  range + give-up rule — this is the required Wednesday Memory display, not a deferred item.
- **[Deferred] Discrimination quiz:** the small held-out A/B/C quiz (~20–50 "which concept is
  this?" items over the confusable clusters) + the contrast-benefit measurement — out of
  current scope (`PRD.md` scope note).

### M5 — Build, run, analyze

- `just check`; verify the toggle changes queue order (confusables adjacent when ON).
- Run the ablation; compare discrimination-quiz accuracy (and retention) ON vs OFF.

---

## Deliverables

1. A `contrast_scheduling` deck-config toggle (ablation-ready).
2. A `build_queues` contrast pass that clusters by tag and surfaces confusables together
   (the `SPOV 1` tag-edge platform + `SPOV 3` ordering).
3. A **per-topic mastery/metrics RPC** (`StatsService`) powering the Memory dashboard + the
   coverage map, fast on 50k cards.
4. **≥ 3 Rust unit tests + 1 Python test** (across both artifacts); undo-safe; no corruption.
5. The same engine commit (@ `25.09.2`) running on **desktop + the AnkiDroid build**, with a
   desktop installer.
6. **[Deferred]:** the two-arm ablation run + held-out discrimination quiz + write-up.

## Engine touch points (reference)

| Concern                              | File / symbol                                                                                                                                        |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Queue build entry                    | `Collection::build_queues` — `rslib/src/scheduler/queue/builder/mod.rs`                                                                              |
| Gather buckets                       | `QueueBuilder` `self.new` / `self.review` — `builder/gathering.rs`                                                                                   |
| New contrast pass                    | `rslib/src/scheduler/queue/builder/contrast.rs` (new)                                                                                                |
| Tags                                 | `notes.tags` (space-padded); `rslib/src/tags/`                                                                                                       |
| Toggle (per-deck)                    | `proto/anki/deck_config.proto`, `rslib/src/deckconfig/mod.rs`, read in `QueueBuilder::new`                                                           |
| Burying (only if same-note clusters) | `builder/burying.rs`, `scheduler/answering/mod.rs`                                                                                                   |
| Mastery/metrics RPC                  | `proto/anki/stats.proto` (`StatsService`), new fn in `rslib/src/stats/` (mirror `graphs/retrievability.rs`), `extract_fsrs_*` in `storage/sqlite.rs` |
| Tag taxonomy                         | topic `cfa::topic::*` + `cluster::*` on `notes.tags`; `rslib/src/tags/`                                                                              |
| Mobile build                         | `Anki-Android` + `Anki-Android-Backend` (rsdroid) `anki` submodule → our fork @ `25.09.2`; `local_backend=true`                                      |

## Risks & decisions

- **Tag granularity is make-or-break** — confusable ≠ merely similar (interleaving is
  neutral-to-negative for non-confusable groups), so choose the cluster level carefully (M0).
- **Tags live on notes**, cards reference notes → one batch tag lookup for gathered cards.
- **No re-gating / no limit bookkeeping** since it's reordering — the reason Phase 1 is cheap.
- **Cross-pile (new ↔ review) contrast is deferred**; within-pile grouping is the v1.
- Measuring discrimination needs the small held-out quiz (the only hand-authoring) — **[Deferred]**.
- **Engine baseline must stay @ `25.09.2`** for the phone build; develop the change there, not on `26.05`.
- **Mastery RPC perf:** must be a single SQL pass (no per-card load) to hit the 50k dashboard target.
