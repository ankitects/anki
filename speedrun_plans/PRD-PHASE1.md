# PRD — Phase 1 (Wednesday Checkpoint)

| Field        | Value                                                            |
| ------------ | ---------------------------------------------------------------- |
| **Product**  | MCAT all-in-one study app, forked from Anki                      |
| **Exam**     | MCAT (scored 472–528; four sections each 118–132)                |
| **Phase**    | 1 of 3 — "The core works on both screens, no AI"                 |
| **Deadline** | Wednesday                                                        |
| **Owner**    | Aryan Verma                                                      |
| **Status**   | Draft for review                                                 |
| **License**  | AGPL-3.0-or-later, with credit to Anki (some parts BSD-3-Clause) |

**Source docs:** [speedrun_spec.txt](speedrun_spec.txt) · [architecture.md](architecture.md) · [Brainlift_ MCAT Preparation.pdf](Brainlift_%20MCAT%20Preparation.pdf)

---

## 1. Summary

We are forking Anki into an all-in-one MCAT study app. The end-state product fuses the three
phases of MCAT prep the research identifies — **understanding → memorizing → applying** — into a
single engine, where every flashcard, lesson, and question is annotated with the MCAT concepts it
covers, and review is driven by a per-concept **Need-to-Review (NTR)** signal layered on top of
Anki's FSRS scheduler.

**Phase 1 is deliberately narrow.** Per the spec, the Wednesday checkpoint ships _no AI_. Its only
job is to prove the foundation is real: a forked Anki that builds from source on desktop and phone,
**one substantive change inside the Rust engine** (concept-aware NTR + scheduling), a review loop
running on a real MCAT deck, an honest **Memory** score with a stated give-up rule, and a desktop
installer plus a phone build that both run on a clean device.

The "understanding" (lessons) and "applying" (questions) phases, the reimagined dashboard,
onboarding, and the Performance/Readiness scores are **out of scope for Phase 1** and tracked in
§7 for Phase 2/3 docs.

## 2. Goals & Non-Goals

**Goals (Phase 1)**

- Fork Anki and build it from source, reproducibly, on a clean machine.
- Land a real, tested change in the **Rust** core that makes scheduling concept-aware via NTR.
- Preload and concept-annotate a real MCAT deck (AnKing MCAT).
- Run a working review loop on that deck on desktop and phone, sharing the one Rust engine.
- Display an honest **Memory** score (point estimate + range) and a topic **coverage map**, with a
  written give-up rule that abstains when data is insufficient.
- Ship a desktop installer and a phone build that both run with no AI.

**Non-Goals (Phase 1)**

- No AI of any kind (no model calls, generated cards, or chatbot) — banned before Friday.
- No Performance or Readiness score yet (those require questions + AI; Phase 2/3).
- No two-way sync yet (Wednesday requires only that both apps review the same deck).
- No lessons viewer, no quizzing section, no onboarding flow, no reimagined dashboard.

## 3. Guiding Principle (full-product context)

From the Brainlift, MCAT prep has three phases; current tools each cover only one well, which is why
prep is so fragmented:

- **Understanding** — comprehending concepts (Khan Academy, Jack Westin). _Phase 2._
- **Memorizing** — spaced retrieval of facts/terms. **Anki already does this well — Phase 1
  builds on it.**
- **Applying** — exam-style questions combining concepts across long passages (UWorld, AAMC).
  _Phase 2/3._

The unifying mechanism across all three is **concept annotation**: every card, lesson, and question
carries the MCAT concept(s) it covers, and a per-concept NTR signal ties them together. Phase 1
lays exactly this foundation — concept-annotated cards and a concept-aware engine — without yet
building the understanding/applying surfaces on top.

## 4. Wednesday Deliverables → Requirements Map

The spec's Wednesday checklist, mapped to the requirements below:

| Spec requirement (Wednesday)                                     | Requirement |
| ---------------------------------------------------------------- | ----------- |
| Anki forked and building from source                             | FR-1        |
| Rust change end-to-end: diff + 3 Rust unit tests + 1 Python test | FR-3        |
| A review loop running on the exam deck                           | FR-2, FR-4  |
| A memory model with an honest score: range + give-up rule        | FR-5        |
| An installer that runs on a clean machine                        | FR-7        |
| Phone app builds & runs a review session on the shared engine    | FR-8        |
| Coverage map drives the give-up rule (spec §7c)                  | FR-5        |

## 5. Functional Requirements (Phase 1)

Priority: **P0** = required for the Wednesday checkpoint; **P1** = strongly desired, degrade
gracefully if time-constrained.

### FR-1 — Forked build from source (P0)

_As a developer, I can build the forked app from source on a clean machine so the engine change and
both apps are reproducible._

- The fork builds desktop (`just run`) and the phone target from a documented, clean checkout.
- README states the exam (MCAT), build instructions for both apps, and credits Anki (AGPL).
- **Acceptance:** clean-build recording + commit hash; clean checkout builds with documented steps.

### FR-2 — MCAT deck + concept taxonomy (P0)

_As a student, the app comes preloaded with a real MCAT deck whose cards are tagged with the
concepts they cover, so the engine can reason per-concept._

- Preload the **AnKing MCAT** deck (the most-used MCAT deck per the research).
- Define a **concept taxonomy** anchored to the AAMC Foundational Concepts (Khan Academy/AAMC
  structure) and reconciled with AnKing's existing card tags/metadata. This is the canonical concept
  list every later feature will annotate against.
- Each card maps to one or more concepts; store the mapping so the Rust engine can query it.
- A **coverage map** records which taxonomy concepts the deck actually covers (feeds FR-5).
- **Acceptance:** deck imports; cards carry concept annotations; coverage % is computable per concept
  and per MCAT section.

### FR-3 — Concept-aware engine change: NTR + scheduling (P0, **the Rust change**)

_As the system, scheduling considers per-concept need, not just per-card memory, so the highest-value
cards surface first._

This is the mandatory brownfield change inside Anki's **Rust** core (spec §7a). NTR is an **internal
engine signal, not a displayed score** — it is both an _input_ to and an _output_ of FSRS's
Difficulty/Stability/Retrievability (DSR):

- **Input:** NTR seeds review prioritization — a concept-aware queue orders due cards by
  `topic weight × concept weakness`, where weakness aggregates the DSR of the cards tagged to that
  concept (and, later, question/lesson signals).
- **Output:** card-level FSRS reviews update the concept's NTR, so reviewing cards in a concept
  lowers its NTR.
- Add a new **protobuf message/RPC** for the concept-aware queue and a **mastery query** (per
  concept: cards mastered + average recall), callable from Python and fast enough to power a
  dashboard on 50,000 cards.
- FSRS intervals stay valid; **undo** continues to work and the collection does not corrupt.
- **No AI** — NTR in Phase 1 is a deterministic formula over DSR + concept/topic weights.
- **Acceptance (per spec §7a):** the diff; ≥3 Rust unit tests + 1 test that calls it from Python;
  proof undo works and the collection is intact; a one-page note on why this belongs in Rust; a list
  of upstream files touched and expected merge difficulty. Must also work on the phone build (FR-8).
- ⚠️ **NTR is a rough-draft concept — see Open Decision OD-1; needs Aryan's review before build.**

### FR-4 — Review loop on the MCAT deck (P0)

_As a student, I can run real review sessions on the MCAT deck and have grades update scheduling._

- Standard Anki review loop runs on the preloaded MCAT deck, driven by the FR-3 concept-aware queue.
- **Acceptance:** a recorded review session; grades persist and update NTR/DSR; meets the spec's
  latency targets where feasible (button-press ack p95 < 50 ms, next card p95 < 100 ms).

### FR-5 — Memory score, honestly displayed (P0)

_As a student, I see an honest estimate of how likely I am to recall what I've learned — with its
uncertainty and the evidence behind it — and the app refuses to show one when it can't back it up._

- Show a **Memory** score as a **point estimate + likely range** (not a single blended number).
  Memory is the _only_ graded score in Phase 1; Performance/Readiness come later and must stay
  separate (blending them is an automatic fail per the spec).
- Show **topic coverage %** (from FR-2) alongside it.
- **Give-up rule (write it down):** the app shows _no_ score until a stated threshold is met — e.g.
  _"No Memory score until ≥200 graded reviews and ≥50% topic coverage."_ Final numbers in OD-2.
- Display the standard honesty fields: point estimate, range, % covered, a "how sure" indicator,
  last-updated time, and the main reasons.
- **Acceptance:** score shows with range + coverage; below threshold the app abstains and explains
  why; values trace to real review data.

### FR-6 — Minimal score surface (P1)

_As a student, I have one place to see the Memory score and coverage._

- A minimal screen/panel hosting the FR-5 outputs. **Not** the reimagined three-mode dashboard
  (that's Phase 2, deferred §7); just enough surface to display the Phase 1 numbers honestly.
- **Acceptance:** Memory score + coverage map are visible in the running app.

### FR-7 — Desktop installer (P0)

_As a user, I can install and run the desktop app on a clean machine with AI off._

- Briefcase-based installer (`qt/installer`) for the target desktop OS.
- **Acceptance:** clean-machine install recording; app launches and runs a review session.

### FR-8 — Mobile review session on the shared engine (P0)

_As a student, I can run a real review session on the MCAT deck on a phone, using the same Rust
engine as desktop._

- A phone app builds and runs on a real device or emulator, loads the MCAT deck, and runs a real
  review session **on the shared Rust backend** (not a re-implemented scheduler).
- Two-way sync is **not** required Wednesday — reviewing the same deck is.
- The FR-3 Rust change must function on the phone build too.
- **Acceptance:** screen recording of a phone review session on the shared engine.
- ⚠️ **Build approach is an open decision — see OD-3.**

## 6. The Rust Change — required artifacts (spec §7a)

Collect these for the checkpoint, tied to FR-3:

1. The **diff** of the engine change.
2. **≥3 Rust unit tests** + **1 Python test** exercising the new RPC.
3. Proof **undo works** and the collection is not corrupted (crash/undo check).
4. A **one-page note**: why NTR + the concept-aware queue belong in Rust, not Python.
5. A **list of upstream files touched** and an estimate of future-merge difficulty.

## 7. Deferred to Phase 2 / Phase 3 (full-MVP FRs)

These are the rest of the all-in-one vision, intentionally **out of Phase 1**. They will get their
own PRDs. Captured here so nothing from the original brief is lost:

- **Reimagined dashboard** — three modes (Learn / Test / Flashcards), with Flashcards routing to the
  original Anki view. _(orig. FR-1.1)_
- **Onboarding flow** — user-state initializer: study frequency, targets, section time budgets,
  progress; experience adapts to the user. _(orig. FR-1.2)_
- **Lessons (Understanding)** — document viewer with notes + highlighting, lessons annotated with
  page-range → concept mappings, sourced from trusted free material. _(orig. FR-2)_
- **Questions (Applying)** — quizzing by selected concepts or NTR-driven general review, sourced from
  online banks. Feeds the **Performance** score. _(orig. FR-4)_
- **NTR ↔ all surfaces** — NTR also driven by question performance; suggests lessons, spaces lessons
  in-app, and recommends questions to review. _(extends orig. FR-3)_
- **Performance & Readiness scores** — exam-style-question accuracy (Performance) and a projected
  MCAT score with range + confidence + give-up rule (Readiness), each shown separately. _(orig.
  FR-6; spec §4)_
- **Full mobile app** — React Native / Expo companion on the shared Rust backend, with two-way
  offline sync and conflict resolution. _(orig. FR-5; matures FR-8)_
- **AI layer** — RAG-grounded generation, source-traced outputs, held-out evals beating a baseline,
  all degrading to "AI off." _(Friday onward)_

## 8. Open Decisions (need Aryan's input)

- **OD-1 — NTR definition.** NTR is a rough draft. Proposed Phase 1 form: a deterministic per-concept
  signal = `f(topic weight, aggregated DSR of the concept's cards)`, used as both input (queue order)
  and output (updated by reviews). **Please review the exact formula and weighting before FR-3
  build.**
- **OD-2 — Give-up thresholds.** Proposed: _no Memory score until ≥200 graded reviews and ≥50% topic
  coverage._ Confirm or adjust the numbers.
- **OD-3 — Mobile build approach.** Your full-MVP FR-5 wants **React Native + Expo on the Rust
  backend** — but wiring RN→Rust via FFI by Wednesday is high-risk. The spec explicitly allows
  building on **AnkiDroid** (already shares the Rust backend) or running the Rust backend on-device.
  **Recommendation:** fork **AnkiDroid** for the Phase 1 checkpoint to guarantee a working
  shared-engine review session, then migrate to RN/Expo for the full mobile app in Phase 2.
  Confirm which path.

## 9. Proof Artifacts (hand-in for Wednesday)

- Commit hash + clean-build recording (FR-1).
- Rust-change diff, 3 Rust tests + 1 Python test results, undo/no-corruption proof (FR-3, §6).
- Clean-machine desktop install recording (FR-7).
- Screen recording of a phone review session on the shared engine (FR-8).

## 10. Risks

- **Mobile build is the day-one bottleneck** (spec's explicit warning). De-risk via OD-3
  (AnkiDroid). Get the phone build green _before_ feature work.
- **Concept taxonomy churn** — if AnKing tags don't cleanly map to AAMC concepts, FR-2/FR-3 slip.
  Lock a versioned taxonomy early; coverage gaps are acceptable as long as they're reported.
- **Scope creep into Phase 2** — lessons/questions/dashboard are tempting but banned (no AI; not due
  Wednesday). Hold the line.
- **Engine-change merge cost** — touching the Rust scheduler risks future upstream-merge pain;
  document touched files (§6.5) and keep the change additive where possible.
