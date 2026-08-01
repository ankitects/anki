# The Probe

*What it is, why it works, and what it costs. Standalone — assumes nothing.*

---

## 1. The one-sentence version

A **probe** is an AI-generated reworded version of a flashcard you already have — same fact,
deliberately unfamiliar phrasing — served *instead of* the original on reviews the scheduler
is already confident you'll pass.

It does not exist in Anki. It is the thing we are building.

---

## 2. The problem it solves

Anki's scheduler (FSRS) predicts exactly one thing: **the probability you will recall this
card's specific text.**

That's a real, well-calibrated prediction. It is also structurally blind to the thing an exam
actually tests, because nothing in the model ever varies the wording. Difficulty, stability,
and retrievability are all indexed to one card — one stem, one answer, one surface form.

So a student can grind a deck until their retention graph is beautiful and their practice
scores don't move. They didn't get better at biochemistry. They got better at *those cards*.

The framing that makes this precise: **your deck is a training set, the exam is a held-out
test under distribution shift.** Anki reports training accuracy. Nobody measures the
generalization gap.

---

## 3. What a probe looks like

Original card:

> **Q:** Competitive inhibitor — effect on Km and Vmax?
> **A:** Km increases, Vmax unchanged.

Probe:

> An enzyme assay shows the same maximum reaction rate, but twice as much substrate is needed
> to reach half-maximal velocity. What kind of inhibition is this?

Same fact. Unrecognizable surface. You cannot pattern-match your way through it — the phrasing
you memorized isn't there.

---

## 4. When it gets served — the actual insight

When the scheduler says your recall probability on a card is high (above roughly 0.85), a
success on that card **carries almost no information.** The model already predicted it with
high confidence. You spent fifteen seconds confirming something the system already knew.

That review is informationally near-worthless.

**So spend it.** Some fraction of the time — say one in six — serve the probe instead of the
original.

### Reading the outcomes

| What happens | What it means |
|---|---|
| Passes the original | Memory confirmed. Roughly what was predicted. Low information. |
| **Passes the probe** | Knows the *concept*, not just the wording. Evidence of transfer. |
| **Fails a card they know cold** | **Not a memory failure.** Memory was established and the model was right about it. What broke was the link to a new surface. This is a *transfer* signal — and it is invisible to every tool on the market. |

That last row is the entire product.

---

## 5. Why this is the right thing to build

**It costs zero extra study time.** Every competitor measures transfer by bolting on practice
exams — separately scheduled, high friction, universally procrastinated. This measures it
inside a habit the student already has. The marginal cost of the measurement is zero minutes.

**It generates the Performance score.** The project requires three separated scores — Memory
(DOK 1), Performance (DOK 2/3), Readiness (DOK 4). Performance requires an actual transfer
measurement. Probe outcomes *are* that measurement. Nothing else on the table produces it.

**It's additive**, so the required three-arm experiment stays clean: probes on / probes off /
stock Anki. (A subtractive feature collapses the middle arm into the control.)

**The instrument is also the treatment.** Decades of work on *desirable difficulties* says
varied retrieval conditions produce better transfer than repeated identical retrieval. Probes
are varied retrieval conditions, injected automatically. So the same mechanism that exposes
the gap also closes it.

That gives two separately falsifiable predictions instead of one:

- **A — the measurement claim.** Semantic stability predicts held-back novel-question accuracy
  better than plain retrievability does.
- **B — the treatment claim.** The probes-on group outperforms probes-off and stock on
  held-back novel questions after *equal study time*.

Either can fail independently, and either failure is publishable as "I was wrong, here's the
evidence."

---

## 6. What it means in the code

- **Probe generation is offline and cached.** The app must work with AI disabled, so probes are
  generated ahead of time and stored. AI accelerates measurement; it is never a runtime
  dependency.
- **Each probe records its parent card and its source citation** — required for traceability,
  and it's what lets you prove a probe isn't fabricated.
- **One scheduler branch:** check recall probability, flip a weighted coin, serve the variant
  instead of the original. Nanoseconds, inside a path that already meets its latency budget.
- **Two stability values per card** instead of one:
  - `S_surface` — the binding to this exact phrasing. Updated by originals.
  - `S_semantic` — the underlying concept. Updated strongly by probe outcomes.
- **The transfer gap** is the difference between them, aggregated per topic. That's the number
  no study tool has ever shown a student:
  > *"Your memory is fine. Your transfer is broken in Amino Acids."*

**Why it must live in Rust:** probe selection is a scheduling decision and the dual-state
update is a memory-state computation. Both are the scheduler, which lives in the Rust core.
And because the Android app runs that same Rust backend, one implementation serves both
platforms — which is what makes the phone requirement feasible at all.

**Why sync comes nearly free:** record the variant id as one new field in the review log. Both
stabilities are then pure functions of that log — exactly how the existing scheduler already
derives its state. Syncing reduces to Anki's existing, battle-hardened review-log sync.

---

## 7. The two real risks

**Probe quality is the whole thing.** A badly generated probe measures your generator, not the
student — a probe that's ambiguous, or that accidentally tests a different fact, produces a
failure that means nothing. The project brief anticipates this and requires showing the AI
probes beat a dumb keyword-or-template baseline. If they don't, you ship the baseline. The
thesis outranks the technology.

**Leakage.** Probes are generated from the same source material as the cards, so a probe could
end up being a near-duplicate of a held-back test item — which would make the evaluation grade
itself. Needs an embedding-similarity check with generation cutoffs logged.

---

## 8. What it looks like when AI is switched off

Originals still schedule normally, so the Memory score stays fully functional. Probe-dependent
numbers widen and then refuse to render — which *demonstrates* the give-up rule rather than
contradicting it. Decks can also ship pre-generated probe packs, so AI-off does not mean
probe-off.

---

## 9. The one-paragraph pitch

Every study app measures whether you remember the card. None measure whether you know the
thing. The gap between those two is where scores go to die — and it is measurable, per topic,
for zero additional study time, by spending the reviews the scheduler already knows the answer
to. The probe is that measurement. It is also, by accident of how memory works, the cure.
