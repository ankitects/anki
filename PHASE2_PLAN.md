# Phase 2 Plan — The Performance Layer (SPOV 2): worked → faded → solve

> Companion to `brainlift.md`, `PHASE1_PLAN.md`, and `PRD.md`. Phase 2 (Friday) builds the
> **Memory → Performance bridge**: `SPOV 2` (FSRS-driven faded worked examples) on the **same
> engine commit (@ anki `25.09.2`)** that ships to desktop **and** the AnkiDroid build. AI is
> now permitted — as a _validated accelerator_. **The dependency "edges" are modeled as rung
> tags (no new table), so everything rides Anki's native sync.**
>
> **Deferred this iteration** (see the `PRD.md` scope note): the held-out MCQ **bank**, the
> **Performance measurement / calibration**, and the **ablation run** — Phase 2's ablation
> deliverable is the **`fade_enabled` on/off toggle**.

## Scope

**In scope**

- `SPOV 2` — the worked → faded → solve ladder, with the **fade level read off FSRS state**.
- **Dependency model via rung tags** — `rung::worked|faded|solve` within a cluster; gating
  computed in `build_queues` from FSRS state + tags. **No `card_relationships` table** → no
  schema change, no sync-protocol work (rides native tag sync; works on stock AnkiDroid).
- **Solve-rung format** — a **custom A/B/C MCQ note type + self-contained HTML/JS template**
  (self-graded), transfer-appropriate to the exam, with misconception distractors.
- **AI (validated accelerator) — authoring-time only; runtime is AI-free by construction.**
  All AI runs in an offline dev pipeline; generated+validated cards and their sources are baked
  into the deck, so the review loop never calls AI. Split into two pieces so each half of R6
  lands cleanly:
  1. **Generation** — item/card + misconception-distractor authoring; **validated by a
     gold-set checker** (accuracy + wrong-answer rate, **pre-set cutoff**) before display.
  2. **Retrieval-for-grounding** — finds the **source passage** each card is grounded on, and
     **beats BM25 (keyword) + plain vector** on precision@k. The retrieved passage **is** the
     named source (traceability). **AI-off works by construction** (cards + sources baked in).
- **`fade_enabled` on/off toggle** (ablation-ready).

**Builds on Phase 1**

- Reuses the `build_queues` post-gather hook, the deck-config toggle mechanism, and the
  **two-level tag taxonomy** — now adding the **rung** level and **gating**.

**Deferred (measurement / Phase 3)**

- The disjoint **held-out MCQ bank**, the **Performance gauge calibration**, and the
  **three-arm ablation run**. Readiness, readiness-optimization, full SPOV unification, and
  BYO/untagged-deck edge sourcing remain Phase 3.

## What's genuinely new vs Phase 1

- **Gating, not just reordering.** Phase 1 only reordered cards. Phase 2 must **hold a rung
  back** until its prerequisite's FSRS stability crosses a threshold, and **re-gate on answer**
  (gating + re-gating + keeping deck limits/counts correct).
- **Content + a custom note type.** Phase 1 used the existing recall deck; Phase 2 _creates_
  worked / faded / solve cards (incl. the custom MCQ note type) for the narrow slice.

## Milestones

### M0 — Narrow the slice

- Pick **one or two formula clusters** (e.g., the Fixed-Income duration family, or TVM) from
  the Phase 1 taxonomy. Prove the layer on a slice before breadth.

### M1 — Content pipeline (AI-accelerated, validated)

- **Generation:** parameterized item generators (random inputs → computed answer →
  error-pattern distractors); an LLM may draft them, but **numerically validate** every
  generator. Emit structured notes (`Prompt`, `Step1..N`, `Answer`, `Distractors`) tagged with
  their **cluster** + **rung**.
- **Gold-set checker:** a small human-verified gold set gates generated cards (correct / wrong
  / bad-teaching, **pre-set cutoff**); blocked cards never reach study.
- **Retrieval-for-grounding:** index the source(s); for each card retrieve the supporting
  passage and store it as the card's **named source**. Benchmark vs **BM25 + plain vector**
  (precision@k) — this is the R6 "beats a simpler method" comparison.
- _(Deferred: the disjoint held-out MCQ bank for Performance scoring.)_

### M2 — Data model: rung tags (no new table)

- Tag each card's **rung** (`rung::worked` / `rung::faded` / `rung::solve`) alongside its
  Phase-1 `cluster::*` tag. The "dependency" is **rung order within a cluster** — **no
  `card_relationships` table, no schema bump**. Tags sync inside notes (native).

### M3 — Card variants

- **worked** — a plain template showing the full solution.
- **faded** — **cloze** cards; backward-fading via cloze numbering (`notetype/cardgen.rs`,
  `cloze.rs`); native `{{type:...}}` is fine here.
- **solve** — a **custom A/B/C MCQ note type** with a **self-contained HTML/CSS/JS template**
  (renders choices; reveals correctness + explanation on tap), **self-graded** via the normal
  Again/Good. No engine change; renders on desktop **and** the AnkiDroid webview — **verify on
  the emulator early** (the one real cross-platform risk).

### M4 — Engine: FSRS-driven fade gating

- In the `build_queues` hook, read each card's stability via `extract_fsrs_variable(data,'s')`
  (`storage/sqlite.rs`) and its `rung::` / `cluster::` tags.
- Gate rung _k+1_ behind rung _k_'s stability threshold (solve behind faded behind worked),
  **within a cluster**.
- **Re-gate on answer**: `clear_study_queues()` (or a hook in
  `update_queues_after_answering_card`) so a newly-stable prereq unlocks its dependent.
- Gated-out cards **must not** decrement deck limits (`LimitTreeMap`) — drop them the way
  burying does.

### M5 — Toggles

- `fade_enabled` (fading vs always-worked vs always-cold) and `fade_signal` (FSRS-stability vs
  success-count) on `DeckConfigInner`. (These sync via deck config.)

### M6 — Ablation toggle (experiment **[Deferred]**)

- **Current deliverable:** the `fade_enabled` on/off toggle (ON/OFF/vanilla _can_ be compared).
- **[Deferred]:** running the three-arm ablation + the held-out Performance bank + the
  Memory/Performance write-up.

### M7 — Build, run, verify

- `just check` (proto changes need a full build); rebuild the rsdroid `.aar` from the **same
  commit** and verify the ladder + gating on the **AnkiDroid emulator**.

## Deliverables

1. A validated **generation pipeline** (numeric validation + gold-set checker + cutoff).
2. A **retrieval-for-grounding** component that attaches a named source and **beats BM25/vector**
   (precision@k) — satisfies both halves of R6.
3. **Rung tags** (`rung::*`) on the slice — no schema change, syncs natively.
4. worked / faded / **custom-MCQ solve** card variants (self-graded, cross-platform).
5. **FSRS-driven gating** in `build_queues` with re-gating; `fade_enabled` / `fade_signal` toggles.
6. **[Deferred]:** held-out MCQ bank, Performance calibration, three-arm ablation + write-up.

## Engine touch points (reference)

| Concern                  | File / symbol                                                                                    |
| ------------------------ | ------------------------------------------------------------------------------------------------ |
| Gating + fade selection  | `Collection::build_queues`, `add_due_card`/`add_new_card` — `rslib/src/scheduler/queue/builder/` |
| FSRS state at queue time | `extract_fsrs_variable(data, 's')` — `rslib/src/storage/sqlite.rs`                               |
| Re-gate on answer        | `update_queues_after_answering_card` / `clear_study_queues` — `scheduler/queue/mod.rs`           |
| Rung/cluster tags        | `notes.tags` (`rung::*`, `cluster::*`); `rslib/src/tags/` (syncs natively)                       |
| Faded (cloze) cards      | `notetype/cardgen.rs`, `rslib/src/cloze.rs`                                                      |
| Solve (custom MCQ)       | new note type + self-contained HTML/JS template (renders desktop + AnkiDroid)                    |
| Toggles                  | `proto/anki/deck_config.proto`, `rslib/src/deckconfig/mod.rs`                                    |
| AI (offline tooling)     | generation + gold-set checker; retrieval index (BM25 / vector baseline)                          |

## Risks & decisions

- **FSRS state isn't in the lightweight gather structs** → SQL extraction (or full card load);
  watch query cost.
- **Custom MCQ cross-platform** → keep the template **dependency-free** and **self-grade**
  (don't drive the grade via `pycmd` / JS bridge — that diverges across desktop vs AnkiDroid);
  verify on the emulator early.
- **Re-gating consistency** — the main correctness risk; a dependent must reappear when its
  prereq stabilizes.
- **Limit/count bookkeeping** for gated-out cards.
- **AI (authoring-time only):** runtime is AI-free by construction (no review-time AI calls);
  generation **gold-set-validated** + **pre-set cutoff**; retrieval **beats BM25/vector** and
  supplies the **named source**; train/test kept disjoint; prompt-injection in sources handled.
- **No schema/sync work** — rung/cluster tags ride native sync; a first-class synced edge table
  is deferred to Phase 3 "only if needed."
- **Supply scope** — keep to the narrow cluster(s) until the layer is proven.
