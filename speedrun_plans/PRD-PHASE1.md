# PRD — Phase 1 (Wednesday Checkpoint)

| Field        | Value                                                            |
| ------------ | ---------------------------------------------------------------- |
| **Product**  | MCAT study **kernel**, forked from Anki                          |
| **Exam**     | MCAT (scored 472–528; four sections each 118–132)                |
| **Phase**    | 1 of 3 — "The core works on both screens, no AI"                 |
| **Deadline** | Wednesday                                                        |
| **Owner**    | Aryan Verma                                                      |
| **Status**   | Draft for review                                                 |
| **License**  | AGPL-3.0-or-later, with credit to Anki (some parts BSD-3-Clause) |

**Source docs:** [speedrun_spec.txt](speedrun_spec.txt) · [architecture.md](architecture.md) · [Brainlift_ MCAT Preparation.pdf](Brainlift_%20MCAT%20Preparation.pdf)

---

> ## ⚠️ Refocus: kernel, not all-in-one app
>
> Per the (updated) Brainlift — *"I can't do this in one week, but I think that I can create the
> **kernel** for predictions and recommendations by having my app create a schedule by **ingesting
> practice tests** and re-prioritizing review by topic weakness"* — this project is **no longer an
> all-in-one MCAT app**. It is the **kernel** of one:
>
> - **Anki stays essentially unchanged** — spaced-repetition review is the core.
> - The one new **input** is **practice-test ingestion**: the student uploads/annotates the tests
>   they already took (concept tag + right/wrong per question), feeding the per-concept NTR signal.
> - The **outputs** are predictions (projected MCAT / Readiness) and recommendations (per-concept
>   NTR: what to review next).
>
> **Removed from the earlier all-in-one plan** (and from the codebase): the in-app **quizzing
> section** with a built-in question bank (old FR-9), and the all-in-one **dashboard/home hub** with
> startup redirect (old FR-6 hub stand-in). The sections below are annotated where they changed;
> "quiz/question bank" now means **ingested practice tests**, and the FR-6 panel is reached directly
> from the Tools menu rather than via a dashboard hub.

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

To make NTR more than a card-only statistic, the kernel adds its one input surface: **practice-test
ingestion** (FR-9). The student annotates the concept-coded, right/wrong results of tests they
already took, and those results feed each concept's NTR. This demonstrates the unifying mechanism
(concept annotation across card _and_ ingested-test surfaces) end-to-end, while staying deterministic
and AI-free. Crucially, ingested-test performance feeds **NTR only**, never the displayed **Memory**
score — Memory stays a pure function
of FSRS card recall.

The spec (§1, §4) also asks for a **Readiness** score: a _projected MCAT total on the real 472–528
scale_ with a range and confidence. Phase 1 shows this as the **headline prediction**, kept as three
**separate, never-blended** scores (spec §2): **Readiness** (projected MCAT from practice-question
performance), **Memory** (FSRS card recall), and **Performance** (raw question accuracy). Because a
made-up readiness number is an automatic fail (§2), the Phase-1 projection is an explicitly
**unvalidated deterministic heuristic** — a documented linear map from question accuracy onto the
scale, widened for missing coverage, with a give-up rule and a plain disclaimer that it is not yet
validated against real practice tests (spec §9 Step 4) and has no past-guess calibration yet. Per §9,
saying so plainly beats a polished number we cannot back up.

The full "understanding" (lessons) phase, the full question bank, a **validated/calibrated**
Performance model and Readiness score (held-out testing, calibration charts — spec §9 Steps 2–4), the
reimagined dashboard, and onboarding remain **out of scope for Phase 1** and are tracked in §7.

## 2. Goals & Non-Goals

**Goals (Phase 1)**

- Fork Anki and build it from source, reproducibly, on a clean machine.
- Land a real, tested change in the **Rust** core that makes scheduling concept-aware via NTR.
- Preload and concept-annotate a real MCAT deck (AnKing MCAT).
- Run a working review loop on that deck on desktop and phone, sharing the one Rust engine.
- Display an honest **Memory** score (point estimate + range) and a topic **coverage map**, with a
  written give-up rule that abstains when data is insufficient.
- Show a **Readiness** projection (MCAT 472–528) as the headline prediction — a separate,
  never-blended, explicitly unvalidated heuristic with its own give-up rule and the required honesty
  fields (range, confidence, coverage, best next thing to study, disclaimer).
- Ship a desktop installer and a phone build that both run with no AI.

**Non-Goals (Phase 1)**

- No AI of any kind (no model calls, generated cards, or chatbot) — banned before Friday.
- No **validated** Performance or Readiness model. The kernel shows a Readiness (projected MCAT)
  score and a Performance (question-accuracy) number, but both are **unvalidated deterministic
  heuristics** over the student's ingested practice-test results — no held-out testing, no
  calibration, no longitudinal practice-test validation (spec §9 Steps 2–4 remain later work). The
  scores are shown separately and never blended, and abstain below their give-up rules.
- No two-way sync yet (Wednesday requires only that both apps review the same deck).
- No lessons viewer, no onboarding flow. **(Refocus)** No in-app quizzing section and no all-in-one
  dashboard — the kernel's only input is practice-test ingestion (FR-9), and Anki opens normally to
  its deck list.

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
  `topic weight × concept weakness`, where weakness blends the DSR of the cards tagged to that
  concept **and** the student's accuracy on that concept's practice questions (see FR-9).
- **Output:** card-level FSRS reviews and question attempts both update the concept's NTR, so
  reviewing cards or answering questions in a concept lowers its NTR.
- **Blended weakness (implemented):** `weakness = (cards_forgotten + questions_wrong) /
  (cards_with_memory_state + question_attempts)`, where `cards_forgotten = Σ(1 − retrievability)`.
  With no question evidence this reduces exactly to `1 − avg_recall` (the original card-only form);
  with no evidence at all it is the maximum, 1.0. `NTR = topic_weight × weakness`.
- Add a new **protobuf message/RPC** for the concept-aware queue and a **mastery query** (per
  concept: cards mastered, average recall, question accuracy, and NTR with its breakdown), callable
  from Python and fast enough to power a dashboard on 50,000 cards.
- FSRS intervals stay valid; **undo** continues to work and the collection does not corrupt — the
  RPCs are read-only and question attempts persist in the collection config, not the revlog.
- **No AI** — NTR in Phase 1 is a deterministic formula over DSR + concept/topic weights + question
  accuracy.
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
  Memory, Performance, and Readiness are shown as **three separate, never-blended** scores (blending
  is an automatic fail per the spec); Memory is card recall only. (Readiness is FR-10.)
- Show **topic coverage %** (from FR-2) alongside it.
- **Give-up rule (write it down):** the app shows _no_ score until a stated threshold is met — e.g.
  _"No Memory score until ≥200 graded reviews and ≥50% topic coverage."_ Final numbers in OD-2.
- Display the standard honesty fields: point estimate, range, % covered, a "how sure" indicator,
  last-updated time, and the main reasons.
- **Acceptance:** score shows with range + coverage; below threshold the app abstains and explains
  why; values trace to real review data.

### FR-6 — Minimal score surface + NTR diagram (P1)

_As a student, I have one place to see my projected score and the scores behind it, and to
understand which concepts the engine will prioritise and why._

- A single scrollable panel (Tools → **"MCAT: Prediction & Review Plan"**) hosting, top to bottom:
  the **Readiness** headline (FR-10), then the **Memory** score (FR-5) and **Performance** number as
  separate sections, then the **NTR diagram**. **(Refocus)** It is reached directly from the Tools
  menu — there is **no** all-in-one dashboard hub or startup redirect; Anki opens normally to its
  deck list. The panel carries an "Ingest a practice test" button linking to FR-9.
- **NTR breakdown diagram.** A per-concept **NTR bar chart** (most-urgent first), each bar annotated
  with the numbers behind it: topic weight, card recall %, practice-question accuracy, and the
  resulting NTR. A short explanation states the formula and makes explicit that **NTR drives review
  order only and does not feed the Memory score**. This is the visual that ties cards + questions
  together per concept.
- The diagram has no give-up threshold (NTR is always informative); it renders whenever any concept
  has card or question evidence. The panel is scrollable so the full report is reachable.
- **Acceptance:** Readiness + Memory + coverage are visible as separate scores; the NTR diagram
  shows per-concept bars with their input numbers; answering practice questions (FR-9) visibly
  changes the bars and moves Readiness.

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

### FR-9 — Practice-test ingestion feeding NTR (P1, the kernel's input) **(Refocused)**

_As a student, I can **ingest a practice test I already took** — tagging each question with the
MCAT concept it tested and whether I got it right or wrong — and have my performance change which
concepts the engine tells me to review._

**(Refocus)** This replaces the earlier plan's in-app quiz with a built-in question bank. The kernel
does **not** serve its own questions; it consumes the student's **own real practice tests** (UWorld,
AAMC, full lengths). This is the "do UWorld first, then add your weak points to Anki" workflow from
the Brainlift, automated. It is **not** a graded Performance score (that stays deferred).

- **Ingestion surface** (Tools → "MCAT: Ingest Practice Test", `qt/aqt/mcat/ingest.py`): the student
  either **loads a CSV** exported/transcribed from a past test (columns `concept` + `correct`, parsed
  tolerantly) or **adds rows by hand**, tagging each question's concept (a taxonomy dropdown) and
  result (right/wrong). Saving **persists the attempts** (per-concept attempts/correct) in the
  collection config via `qt/aqt/mcat/practice_tests.py`.
- No PDF/OCR parsing (that would need AI, which is out of scope): ingestion is deterministic manual
  annotation / structured CSV.
- Those per-concept tallies are passed to the FR-3 RPC as `question_stats` and **blended into NTR**
  (see FR-3's weakness formula). Missing a concept's questions raises its NTR; getting them right
  lowers it.
- **Honesty guard:** ingested-test performance feeds **NTR** and the separate Readiness projection
  **only**, never the displayed Memory score. Storing attempts in config (not the revlog) keeps FSRS
  scheduling and undo untouched.
- **No AI** — deterministic parsing, aggregation, and blending.
- **Acceptance:** ingesting a test changes the per-concept NTR returned by the RPC and the
  FR-6 chart; ≥1 Rust unit test and ≥1 Python test cover the blend; Memory score is unchanged by
  ingested results.

### FR-10 — Readiness: projected MCAT score, honestly (P1, spec §4)

_As a student, I see a projected MCAT score with a range and a confidence level, and the app refuses
to project one — or dress up a guess as a measurement — when it can't back it up._

The spec (§1, §4) wants a **Readiness** score: a projected total on the real **472–528** scale, shown
**separately** from Memory (§2 forbids blending). A made-up readiness number is an **automatic fail**
(§2), and §9 grades the _steps of the bridge_, not a fabricated final number — so Phase 1's Readiness
is a documented, deterministic, **explicitly unvalidated** heuristic.

- **Method (written down, reproducible):** `projected = 472 + 56 × p`, where `p` is observed accuracy
  on the concept-coded practice questions (FR-9). The likely range = a Wald interval on `p` scaled to
  the 56-point span **plus** a coverage-gap term that widens the band as topic/question coverage
  falls; clamped to [472, 528]. Confidence is coverage-dominated (matching the spec's example).
- **Honesty fields (all shown):** point estimate, likely range, % of exam covered, a confidence
  indicator, last-updated time, the main reasons, the **single best next thing to study** (the
  highest-NTR concept), and a plain **disclaimer** that the projection is unvalidated against real
  practice tests (spec §9 Step 4) and has no past-guess calibration yet.
- **Give-up rule (write it down):** no projection until **≥30 practice-question attempts across ≥3
  concepts AND ≥50% topic coverage**; otherwise abstain and name the failing condition.
- **Separation:** Readiness takes _no_ memory/recall input — it cannot blend with Memory by
  construction (enforced by a test).
- **No AI** — deterministic map, no model calls.
- **Acceptance:** projection shows point + range + confidence + best-next-topic on the 472–528 scale;
  below threshold it abstains and explains why; Readiness, Memory, and Performance render as three
  separate scores; ≥1 test covers the mapping/give-up/no-blend behaviour.

## 6. The Rust Change — required artifacts (spec §7a)

Collect these for the checkpoint, tied to FR-3:

1. The **diff** of the engine change.
2. **≥3 Rust unit tests** + **1 Python test** exercising the new RPC, including coverage of the
   question-performance blend into NTR (FR-9).
3. Proof **undo works** and the collection is not corrupted (crash/undo check).
4. A **one-page note**: why NTR + the concept-aware queue belong in Rust, not Python.
5. A **list of upstream files touched** and an estimate of future-merge difficulty.

## 7. Deferred to Phase 2 / Phase 3 (full-MVP FRs)

These are the rest of the all-in-one vision, intentionally **out of Phase 1**. They will get their
own PRDs. Captured here so nothing from the original brief is lost:

- **All-in-one dashboard / home hub** — **(Refocus) removed, not deferred.** The earlier plan
  shipped a minimal hub that the app opened onto at startup (routing to Flashcards / Practice
  Questions / Memory & NTR), plus a "Dashboard" toolbar link and startup redirect. A kernel does not
  reframe Anki's home screen: **Anki opens normally to its deck list**, and the two kernel surfaces
  (Ingest Practice Test, Prediction & Review Plan) are plain Tools-menu entries. The reimagined
  three-mode home (Learn / Test / Flashcards) is **not** part of the kernel.
- **Onboarding flow** — user-state initializer: study frequency, targets, section time budgets,
  progress; experience adapts to the user. _(orig. FR-1.2)_
- **Lessons (Understanding)** — document viewer with notes + highlighting, lessons annotated with
  page-range → concept mappings, sourced from trusted free material. _(orig. FR-2)_
- **Questions (Applying)** — the **full** question bank: quizzing by selected concepts or NTR-driven
  general review, sourced from online banks, feeding a graded **Performance** score. _(orig. FR-4.)_
  Phase 1 ships only the small concept-coded slice in FR-9 (feeds NTR, no Performance score).
- **NTR ↔ all surfaces** — NTR already driven by question performance in Phase 1 (FR-9); Phase 2+
  extends this to suggest lessons, space lessons in-app, and recommend questions to review.
  _(extends orig. FR-3)_
- **Validated Performance & Readiness models** — Phase 1 already _shows_ a heuristic Performance
  number and a heuristic Readiness projection (FR-10). Phase 2/3 makes them **real**: a performance
  model over held-out exam-style questions, a memory-calibration chart (Brier/log-loss), a
  documented score mapping validated against practice tests, and the leakage/paraphrase checks —
  spec §9 Steps 1–4. _(orig. FR-6; spec §4, §9)_
- **Full mobile app** — React Native / Expo companion on the shared Rust backend, with two-way
  offline sync and conflict resolution. _(orig. FR-5; matures FR-8)_
- **AI layer** — RAG-grounded generation, source-traced outputs, held-out evals beating a baseline,
  all degrading to "AI off." _(Friday onward)_

## 8. Open Decisions (need Aryan's input)

- **OD-1 — NTR definition.** _Resolved for Phase 1._ NTR is a deterministic per-concept signal
  `NTR = topic_weight × weakness`, where `weakness = (cards_forgotten + questions_wrong) /
  (cards_with_memory_state + question_attempts)` and `cards_forgotten = Σ(1 − retrievability)`. It is
  used as both input (queue order) and output (lowered by card reviews and correct question answers).
  Question accuracy feeds NTR only, never the Memory score. **Open for tuning:** the relative
  weighting of card vs. question evidence is currently 1:1 per item; revisit once real question
  volume exists.
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
