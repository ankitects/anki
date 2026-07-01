# Dashboard Plan — CFA Readiness Dashboard (Memory · Performance · Readiness)

> Companion to `brainlift.md`, `PHASE1_PLAN.md`, `PRD.md`, and `VISUALIZATION.md`. A **Phase 1
> (Wednesday), AI-free** dashboard that shows, for the whole CFA Level 1 collection, **per-subject progress**
> across the 10 official topic areas plus the project's **three separate gauges** — **Memory**,
> **Performance**, **Readiness** — each with a range and an honesty/confidence note.
>
> It is the required Wednesday **Memory display** made whole: it reuses the Phase 1 per-topic
> **mastery RPC** (`StatsService`) and adds the two harder gauges as **explicitly-labelled,
> uncalibrated estimates** (Performance) and an **abstaining** projection (Readiness). No model
> calls, no generated content — every number is derived from FSRS state + the deck's tags.

**This is a single-exam app.** The fork is centered on **CFA Level 1** — the whole app targets one
exam (spec §5: "Pick one exam"), so the dashboard represents the **entire CFA Level 1 exam (the whole
collection)** by default, not any single deck; per-deck scoping is only a convenience. To make the
focus explicit, the desktop window title reads **`User 1 - Anki - CFA Level 1`**
(`qt/aqt/main.py`, `setWindowTitle` / `updateTitleBar`).

## The three gauges (spec definitions — kept separate, never blended)

| Gauge           | Question it answers                                                                                      |
| --------------- | -------------------------------------------------------------------------------------------------------- |
| **Memory**      | The chance the student recalls a fact you taught.                                                        |
| **Performance** | The chance the student gets a new, exam-style question right — including ones never seen.                |
| **Readiness**   | A projected result on the real scale (for CFA: **P(pass)**), with a range and a note on how sure we are. |

**The trap (from the spec):** remembering a card ≠ answering a new question about it. FSRS already
estimates Memory well; the two bridges (Memory→Performance, Performance→Readiness) are the hard
part. This dashboard **measures and shows that gap — it does not hide it.**

## The honesty rule (non-negotiable, from the spec)

No score is shown as a bare number. Every gauge card must surface:

- the **point estimate**,
- the **likely range** (not a single number),
- the **% of the exam covered** so far,
- a **"how sure" / confidence** indicator,
- the **last-updated** time,
- the **main reasons** behind it,
- the **single best next thing to study**,
- and the **give-up rule** (below) — the app shows **nothing** when it lacks data.

Performance and Readiness are additionally tagged **"uncalibrated — not yet validated"** in Phase 1
(a calibrated Performance model needs the Phase 2 held-out question bank; a calibrated Readiness
P(pass) needs the Phase 3 held-out mocks).

## Scope

**In scope (Phase 1, no AI)**

- **Per-subject progress** for the **10 CFA L1 topic areas**, mapped from the deck's reading tags.
- **Memory** — real, from FSRS retrievability (the Wednesday-required gauge).
- **Performance** — an **uncalibrated proxy** of Memory (documented transfer factor), clearly labelled.
- **Readiness** — a documented **P(pass)** method that **abstains** until the give-up thresholds are met.
- **Coverage map** — % of the official outline the deck covers (spec §7c).
- The **give-up rule**, written down and enforced.

**Constraints**

- **No AI:** no LLM, no embeddings, no generation. FSRS is baseline infrastructure and is allowed.
- **FSRS required for Memory:** cards without an FSRS memory state don't contribute to Memory
  (they count against Coverage instead).

**Out of scope (later phases)**

- Calibrated **Performance** on a held-out exam-style bank + the paraphrase test (Phase 2).
- Calibrated **Readiness** P(pass) against held-out mocks; Brier/log-loss calibration proof (Phase 3).
- Readiness-optimization card allocation (Phase 3 / demoted SPOV 4).

## Subjects — the 10 CFA L1 topic areas (+ default exam weights)

Subjects are the 10 official areas; the deck's **flat reading tags** are aggregated up to them via the
curated **reading → topic map** already used by the concept graph
(`ts/routes/concept-graph/topics.ts`). Weights below are the standard L1 weights, stored as a
**documented, config-editable table** (published as ranges; midpoints shown) — the engine stays
topic-agnostic, so the map + weights + transfer factors all live in the frontend.

| Subject                          | Exam weight `w(s)` | Transfer factor `τ(s)` |
| -------------------------------- | -----------------: | ---------------------: |
| Ethical & Professional Standards |                15% |                   0.90 |
| Quantitative Methods             |                10% |                   0.65 |
| Economics                        |                10% |                   0.75 |
| Financial Statement Analysis     |                15% |                   0.75 |
| Corporate Issuers                |                10% |                   0.75 |
| Equity Investments               |                11% |                   0.75 |
| Fixed Income                     |                11% |                   0.65 |
| Derivatives                      |                 6% |                   0.60 |
| Alternative Investments          |                 6% |                   0.85 |
| Portfolio Management             |                 6% |                   0.75 |

`τ(s)` = how much recall is expected to transfer to exam questions (recall-heavy topics like Ethics
high; computation/application-heavy like Derivatives/Quant lower). **These are documented guesses**
for Phase 1; Phase 2's measured Memory→Performance gap replaces them.

## How each value is calculated

Notation: for subject `s`, `studied(s)` = its cards that have an FSRS memory state; `R(c)` = a card's
FSRS predicted retrievability (`extract_fsrs_retrievability`, `rslib/src/storage/sqlite.rs`).

### Memory (real)

- **Per subject:** `Memory(s) = mean over c in studied(s) of R(c)` — the mean predicted
  retrievability over **studied** cards only. Unseen/new cards are **excluded** here and captured by
  **Coverage** instead (so gaps show up as low coverage, not fake-low memory).
- **Aggregation from readings:** each reading tag contributes `(mean_retrievability, studied_count)`;
  a subject's Memory is the **studied-count-weighted mean** across its readings.
- **Abstain** for a subject with `studied(s) = 0`.
- **Range:** `Memory(s) ± z · sd(R)/√|studied(s)|` (standard error of the mean; wider with fewer
  studied cards). Report a 90% band.
- **Calibration (later proof):** Brier / log-loss on held-out reviews (Sunday). Phase 1 shows the
  number + range; the calibration chart is a later deliverable.

### Performance (uncalibrated proxy)

- **Per subject:** `Performance(s) = Memory(s) × τ(s)`, rendered with an **"uncalibrated estimate"**
  badge and a wide range.
- **Range:** propagate Memory's range through `τ(s)`, then **widen by an extra ±0.15** to reflect that
  `τ` is an assumption, not a measurement. **Confidence: low** by construction in Phase 1.
- **Why a proxy, honestly labelled:** Phase 1 has no exam-style bank, so we cannot yet _measure_
  transfer. Showing `Memory × τ` with a visible gap and an "uncalibrated" flag satisfies "measure the
  gap, don't hide it"; Phase 2 (paraphrase test + held-out MCQ bank) replaces `τ` with a fitted model.

### Readiness (P(pass), abstains until calibrated)

CFA L1 is **pass/fail with no published numeric score**, so Readiness is a **probability of passing**,
not a number on a scale (mirrors the spec's USMLE Step 1 guidance).

1. **Overall performance:** `P̄ = Σ_s w(s)·Performance(s) / Σ_s w(s)` over subjects that have data.
2. **Map to P(pass):** `P(pass) = logistic(k · (P̄ − MPS))`, where `MPS` = the assumed **minimum
   passing standard** (CFA never publishes it → use a **wide band**, default `MPS ∈ [0.60, 0.70]`,
   centre 0.65) and `k` = a documented slope (default 14).
3. **Range:** evaluate P(pass) at the pessimistic corner `(P̄_low, MPS_high)` and the optimistic corner
   `(P̄_high, MPS_low)`, where `P̄_low/high` fold in the per-subject ranges **and a coverage penalty**
   (uncovered exam weight pulls `P̄_low` down). Report `[P_low, P_high]`.
4. **Confidence:** derived from **coverage %** and **total graded reviews** → low / medium / high.
5. **Best next topic:** `argmax_s w(s) · (target − Performance(s))` — the largest **weighted** gap
   (default `target = 0.80`).

### Coverage

- **Coverage (studied) `= Σ_s w(s)·[studied(s) > 0]`** — the exam weight whose topics have at least one
  studied card. This is the headline "% of exam covered so far" and feeds confidence + the give-up rule.
- Also render the **outline map** (spec §7c): which of the official readings/topics the deck contains
  at all (deck coverage) vs has been studied.

## Give-up rule (written down, enforced)

> **Readiness shows no P(pass)** until the collection has **≥ 15 graded reviews** **and**
> **≥ 1% topic coverage** (studied). Below either line, Readiness displays only its **decomposed
> components** (per-subject Memory/Performance, coverage %, best-next-topic) under an
> **"insufficient data — no score"** label.
>
> _(The 15-review and 1%-coverage bars are deliberately low so the score path is easy to trigger and
> confirm during testing/demo; raise them for real use.)_

Performance is **always** labelled "uncalibrated" in Phase 1. Memory shows whenever a subject has ≥ 1
studied card (otherwise that subject abstains).

## Access — where the user opens it

- **Primary: the top toolbar.** A **"CFA Dashboard"** link sits in the main window's top toolbar
  (next to Decks / Add / Browse / Stats / Sync) and opens the dashboard for the **whole collection** —
  visible in the window itself, one click from anywhere, not tied to a single deck.
- **Optional: per-deck.** A "Dashboard" entry in the deck gear menu (next to "Concept map") opens the
  same page scoped to that deck, for users juggling several exam decks.
- **Routes:** `dashboard` (whole collection, `deck_id = 0`) and `dashboard/[deckId]` (scoped); the
  metrics RPC treats `deck_id = 0` as the whole collection.
- **How (Anki convention):** the toolbar links live in `qt/aqt/toolbar.py` (`_centerLinks` +
  `create_link` + a link handler) — the same path the _Stats_ link uses. The handler opens a dialog
  hosting the page in an **API-enabled** `AnkiWebView` (`AnkiWebViewKind.CFA_DASHBOARD`).

## Milestones

### M0 — Data prep (reuse, no new engine data)

- Reuse the **reading → topic map** (`concept-graph/topics.ts`) and add the **weights** + **transfer
  factor** tables as frontend constants (config-editable). Confirm every reading maps to one of the 10
  topics; noise tags dropped.

### M1 — Backend metrics RPC (extends `StatsService`, one SQL pass)

- Reuse/extend the existing per-topic **mastery** path (`rslib/src/stats/`, and the single-pass
  helper `searched_cards_graph_data` already added for the concept graph) so one deck-scoped call
  returns, per **reading tag**: `{ total, studied (with memory state), mean_retrievability, reviewed }`,
  **plus** the collection's **graded-reviews count** (revlog entries with a real grade) for the
  give-up rule. Must stay fast on 50k cards (no per-card load). Engine returns raw per-tag numbers
  only — **no CFA topic logic in Rust**.

### M2 — Dashboard page (Svelte, Anki tokens)

- New mediasrv page (mirror `concept-graph`: API-enabled webview). Routes: `dashboard` (whole
  collection) and `dashboard/[deckId]` (deck-scoped). **Opened from the top-toolbar "CFA Dashboard"
  link**; optionally also from the deck gear menu next to "Concept map" (see _Access_ above).
- **Three gauge cards** (Memory / Performance / Readiness) each rendering point estimate + range +
  coverage % + confidence + last-updated + reasons + best-next-topic, honouring the give-up rule.
- **Per-subject table**: 10 rows (Memory bar, Performance badge, studied/total, coverage), sortable by
  weighted gap. Reuse `ts/lib/components/` + design tokens (light/dark).

### M3 — Honesty & give-up enforcement

- Implement the give-up thresholds; the abstain state; the "uncalibrated" badges; the coverage map.

### M4 — Calibration hooks (**[Deferred]** to Sunday/Phase 3)

- Brier/log-loss for Memory on held-out reviews; P(pass) calibration against held-out mocks. Phase 1
  ships the _method_ + ranges + abstention, not the calibration proof.

## Deliverables

1. A **deck-scoped metrics RPC** (per-reading stats + graded-review count), fast on 50k cards.
2. A **dashboard page** with per-subject progress and the **three separate gauges**, each with a range
   and the honesty fields.
3. **Memory** real (FSRS); **Performance** uncalibrated proxy (documented `τ`); **Readiness** P(pass)
   that **abstains** under the give-up rule.
4. A **coverage map** of the official outline.
5. Tests: Rust unit tests for the metrics RPC (counts/means/coverage); ≥ 1 Python test; frontend
   left to lint/type checks.

## Engine / UI touch points (reference)

| Concern                         | File / area                                                                                                      |
| ------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Metrics RPC                     | `proto/anki/stats.proto` (`StatsService`), `rslib/src/stats/` (reuse `mastery.rs` / `searched_cards_graph_data`) |
| FSRS retrievability / stability | `extract_fsrs_*` — `rslib/src/storage/sqlite.rs`                                                                 |
| Graded-reviews count            | `revlog` count (SQL) for the give-up rule                                                                        |
| Reading→topic map, weights, `τ` | frontend constants (`ts/routes/concept-graph/topics.ts` + a dashboard config)                                    |
| Dashboard page                  | new `ts/routes/dashboard/` (pattern: `concept-graph`), API-enabled webview                                       |
| Launch point                    | **top toolbar** link in `qt/aqt/toolbar.py` (`_centerLinks`, next to Stats); `AnkiWebViewKind.CFA_DASHBOARD`     |
| Styling / components            | `ts/lib/sass/_vars.scss` tokens; `ts/lib/components/`                                                            |

## Risks & decisions

- **Performance is a proxy, not a measurement, in Phase 1** — must be visibly labelled "uncalibrated";
  the honest gap is the point (spec §7d paraphrase test comes in Phase 2).
- **Readiness must abstain** — never show a bare P(pass) below the give-up thresholds (spec: a confident
  number with no evidence is an automatic fail).
- **CFA has no numeric score** — Readiness is **P(pass)**; do not invent a points scale.
- **MPS is unpublished** — use a wide `MPS` band and propagate it into the P(pass) range.
- **FSRS dependency** — Memory needs FSRS; a non-FSRS collection shows low coverage / abstains rather
  than faking numbers.
- **Keep CFA specifics out of the engine** — the RPC returns generic per-tag numbers; topic mapping,
  weights, and transfer factors live in the frontend.
- **Coverage honesty (spec §7c)** — a huge deck that skips a high-weight section must **not** read as
  "ready"; the coverage penalty + give-up rule enforce this.
