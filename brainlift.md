# BrainLift — Bridging Memory → Performance in Anki (CFA Level I)

## Owners

- **William** — primary owner / author

---

## Purpose

**Core goal:** choose and implement **one** evidence-based study technique in Anki's
**engine** (Rust scheduler/queue) that bridges **Memory → Performance** — transfer to the
novel, application-style multiple-choice questions of the **CFA Level I** exam — beyond what
spaced repetition already delivers, and prove it with a clean ablation. Plain flashcards
guarantee recall, not transfer; the thesis is that the *schedule and selection of cards*,
not just their spacing, can be engineered to close that gap for CFA L1.

**Target exam — CFA Level I:** pass/fail; 180 standalone multiple-choice questions (A/B/C);
fact- and formula-heavy with an ethics/case component; organized as 10 topic areas →
readings → Learning Outcome Statements (LOS). Its defining demands — discriminating among
*confusable* categories (FIFO/LIFO/weighted-average, Macaulay/modified/effective duration,
forwards/futures/swaps, the Ethics Standards) and *applying* a formula or rule to a new
mini-scenario — are exactly the Memory→Performance gap this project targets.

**Baseline — what the system already does (so we don't re-buy it):** Anki is already
retrieval practice (flashcards) + an optimized spacing engine (FSRS). Queues are built in
`rslib/src/scheduler/queue/`, ordered by **card state and due date**, never by content
meaning; FSRS assigns each card a memory state (stability, difficulty) toward a desired
retention (default 0.90); new and review cards are mixed via an "Intersperser"; every card
is scheduled **independently** (sibling burying is the only inter-card relationship);
production is supported via type-in/cloze; feedback is immediate; grading is
Again/Hard/Good/Easy. The app additionally measures **Memory**, **Performance**, and
**Readiness** separately.

**In scope:** engine-level, cleanly-ablatable techniques that target transfer for CFA L1 —
interleaving across confusable CFA categories (content-aware ordering), transfer-appropriate
scheduling (Memory↔Performance linking), successive relearning (mastery criterion), and
faded worked examples for the formula-heavy topics (Quant, Fixed Income, Derivatives,
Corporate, Portfolio Management); and the three-way ablation — **feature ON vs feature OFF
vs unmodified Anki** — on the **same CFA questions** and **equal total study time**, scored
on a held-out bank of CFA-style MCQs (Performance), delayed recall (Memory), and calibrated
pass-probability (Readiness).

**Out of scope:** re-implementing retrieval practice or spacing (already in Anki/FSRS);
UI-only techniques that can't be cleanly ablated at the engine (secondary at best); other
exams and **CFA Levels II–III** (vignette/essay formats), plus multi-subject breadth — this
project is deliberately scoped to **CFA Level I**; debunked ideas (learning styles); and
using this document to have an AI *generate* its own insights — **a BrainLift passes through
a human brain first** (DOK 3 / DOK 4 are authored by the owner).

---

## DOK 4 — Spiky Points of View

> Prescriptive, debatable, defensible. Spikiness test: *if 20 experts wouldn't argue about
> it, it isn't spiky enough.*

### SPOV 1 — Schedule the graph, not the card *(spine)*
Conventional SRS treats each card as an independent atom on its own due date — but for CFA L1
that independence is the ceiling on transfer; the schedulable unit should be a *linked cluster*
of cards, not a lone card.
**In the build:** make inter-card **edges** first-class in `rslib/src/scheduler/queue/` —
*dependency* edges (fact→application) and *interference* edges (confusables); interleaving,
gating, and fading all become edge-types behind one "graph-scheduling" toggle.
**Grounding:** Anki baseline (cards scheduled independently; sibling-burying is the only
inter-card relationship); transfer moderators (Pan & Rickard 2018).

### SPOV 2 — Fade the scaffold, and drive the fade off FSRS state
Novices need the worked solution while experts are slowed by it (expertise reversal), so guidance
must *fade*: worked → faded (cloze, backward-fading) → solve (format-matched A/B/C MCQ). The
spiky part isn't "fading is good" (experts agree) — it's that the fade level should be read off
FSRS's **existing** stability/difficulty, not a fixed/authored schedule or a separate counter;
the engine already measures expertise, so reuse it.
**In the build:** a per-concept rung ladder where FSRS stability → rung; toggle 1 ablates *fading*
(vs always-worked vs always-cold), toggle 2 selects the fade *signal* (FSRS-stability vs
success-count) so both claims stay testable; cloze = faded rung, A/B/C MCQ = solve rung; problems
from numerically-validated item-generators (formula clusters), held-out MCQ bank kept disjoint.
**Grounding:** worked-example effect (Sweller & Cooper 1985; Barbieri 2023); expertise reversal
(Kalyuga 2003); generation effect (Bertsch 2007); transfer-appropriate processing (Morris 1977);
FSRS stability/difficulty baseline.

### SPOV 3 — Surface confusable siblings; don't bury them
Anki *buries* siblings to avoid interference; for CFA the interference **is** the lesson — the
scheduler should place FIFO/LIFO, the duration trio, and the Ethics Standards back-to-back.
**In the build:** invert sibling-burying into **contrast scheduling** along interference edges;
ablate surface-vs-bury. **Grounding:** interleaving +0.67 for confusable categories
(Brunmair & Richter 2019); Anki sibling-burying baseline.

### SPOV 4 — Target pass-probability gain, not uniform 0.90 retention
Uniform desired-retention is wrong for a *weighted pass/fail* exam: a rep on a 60%-mastered
high-weight topic beats one on a 95%-mastered low-weight LOS.
**In the build:** a Readiness-gradient selector weighting cards by `exam-weight × marginal
Δ pass-prob`; ablate vs vanilla uniform-retention. **Grounding:** Readiness metric (baseline);
CFA pass/fail + topic weights (Purpose); Dunlosky utility ranking (2013).

---

## Expert — Carl Hendrick

**Bio.** Carl Hendrick is a professor at Academica University of Applied Sciences (Amsterdam) and
a member of the UNESCO International Bureau of Education *Science of Learning* editorial board. He
began as an English teacher in inner-city London, completed a PhD in education at King's College
London, and now leads work bridging cognitive science, educational psychology, and classroom
practice. He is co-author of *How Learning Happens* (with Paul A. Kirschner) — a curated tour of
the seminal papers this thesis rests on — and *Instructional Illusions*, and writes widely on
retrieval practice, cognitive load, and transfer.

**Why he matters here.** He is the field's foremost *synthesizer/translator* — exactly the role
this slot calls for (*How Learning Happens* assembles the very DOK 1 papers we lean on). Two
reasons he fits *this* thesis specifically:

- He states the Memory→Performance gap as sharply as anyone: the **"transfer paradox"**
  (immediate performance ≠ durable transfer), **"remembering is not knowing"** (memory vs
  *induction* — abstracting *when/how* a rule applies), and the claim that **retrieval practice
  is a consolidation tool, not a construction one**. That is our thesis in his words.
- He guards it with **boundary conditions** that keep the build honest: retrieval stalls under
  high cognitive load; sequence retrieval *after* comprehension; transfer is domain-specific and
  *near* transfer is the realistic target.

**The throughline he gives the SPOVs.** Hendrick's arc — *spacing + retrieval **consolidate**
what is already built; construction, induction, and transfer require **desirable (not excessive)
difficulty**, faded guidance, and domain-specific discrimination* — is the spine under all four:

- It licenses the project's premise: don't re-buy retrieval/spacing (consolidation); build the
  transfer layer (induction).
- **SPOV 1 (schedule the graph):** expertise is *structured, domain-specific* knowledge, not a
  bag of independent facts — so the scheduler should model that structure.
- **SPOV 2 (fade the scaffold, FSRS-driven):** worked-examples-then-fade, calibrated to
  load/expertise — his exact sequencing and load-mediation point.
- **SPOV 3 (surface confusables):** domain-specific discrimination via varied, contrastive
  practice — desirable difficulty made concrete.
- **SPOV 4 (pass-probability, not uniform retention):** "remembering is not knowing" — optimize
  for the applied outcome, not raw recall.

One line to hang it on: **desirable difficulty, kept within capacity** — productive (germane)
load, never overwhelming (extraneous) load — the idea that threads expertise reversal, fading,
gating, and contrast.

*Also considered:* Bjork (desirable difficulties — the underlying theorist Hendrick channels),
Dunlosky/Rawson (utility ratings; successive relearning), Rohrer (interleaving/discrimination),
Agarwal (retrieval practice for the classroom).

---

## DOK 3 — Insights

> Novel connections across sources that no single author states, each linked to a SPOV. Key
> question: *if this is true, what common practice is wrong, and what new rule must we follow?*

- **The transfer moderators are a scheduler spec, not study advice.** Pan & Rickard's three
  conditions — format-match, elaboration, initial success — are usually read as tips, but each
  maps to an engine knob: format = rung type (cloze vs MCQ), elaboration = card transform,
  success = promotion gate. *Common practice wrong:* treating transfer as a property of content.
  *New rule:* build the scheduler **to the moderators**. *(→ SPOV 1.)*
- **Sibling-burying optimizes against the one thing CFA needs.** Anki's only inter-card
  relationship separates exactly the items whose *contrast* is the transferable skill.
  *New rule:* for confusable clusters, contrast > bury. *(→ SPOV 3.)*
- **Fade on an expanding schedule, not per session.** Cepeda's optimal gap grows with the
  retention interval; applied to *scaffolding* (not just review), peel one step per expanding
  interval. *New rule:* fading is a spacing decision, not a per-sitting one. *(→ SPOV 2.)*
- **Retention ≠ readiness.** FSRS perfects *per-card* retention, but a weighted pass/fail exam
  makes marginal value heterogeneous — so maximizing average retention is not maximizing
  pass-probability. *New rule:* optimize the exam-level objective. *(→ SPOV 4.)*
- **Distractors and interference edges are the same artifact.** The misconception-based wrong
  answers the item-generators emit (forgot ÷k, used annual) *are* the confusability coordinates
  the interleaver needs — authoring one authors the other. *New rule:* build item-generation and
  discrimination-scheduling together. *(→ SPOV 2 + SPOV 3.)*

---

## DOK 2 — Knowledge Tree

> Restated in our own words to prove comprehension; each entry traces to a source in DOK 1.

**1. Retrieval, generation & transfer**
- **Retrieval practice (testing effect)** — Rowland (2014); Adesope et al. (2017): practice
  testing beats restudy and every other control, recall > recognition — retrieval *changes*
  memory, it doesn't just measure it. Anki's flashcards already *are* this.
- **Transfer of testing** — Pan & Rickard (2018): testing transfers to new questions at
  d≈0.40, but mainly across formats and to application/inference items, and mostly when
  practice is format-matched, elaborated, and initially successful — strip those and transfer
  collapses toward zero.
- **Generation effect** — Bertsch et al. (2007): producing an answer beats reading it
  (d≈0.40, larger after a day) — type-in/cloze out-teach passive review.
- **Transfer-appropriate processing** — Morris, Bransford & Franks (1977): memory is best
  when study processing matches the test, so practicing *application under retrieval* is what
  bridges memory→performance (i.e., drilling CFA-style application MCQs, not just recall).

**2. Spacing — already owned by FSRS**
- **Distributed practice** — Cepeda et al. (2006): spaced beats massed; the optimal gap grows
  with the retention interval (as a decreasing ratio) — exactly FSRS's per-card job.
- **Utility ranking** — Dunlosky et al. (2013): of 10 techniques, only practice testing and
  distributed practice earned "high utility" — and Anki ships both already.

**3. Interleaving — a discrimination trainer**
- **Interleaving** — Brunmair & Richter (2019): moderate on average (g=0.42) but strongly
  material-dependent — large for confusable/visual categories (paintings g=0.67), modest for
  math (0.34), *negative* for word lists (−0.39). It trains *which approach applies* — the
  transferable skill, and CFA L1's distractor-heavy MCQs are precisely a discrimination test.
  (Tension: Dunlosky rates it only "moderate"; Donoghue & Hattie d≈0.47.)

**4. Successive relearning & mastery**
- **Successive relearning** — Rawson & Dunlosky (2013, 2018): retrieval to a mastery
  criterion across spaced sessions; lab d=1.5–4.2, ~a letter-grade in class growing to ~40%
  a month later. It is retrieval + spacing + a criterion — well suited to CFA L1's large,
  long-horizon fact/ethics-rule volume.

**5. Pretesting / errorful generation**
- **Pretesting** — Pan & Carpenter (2023); 2023 meta-analysis: a wrong guess before study
  helps the *tested* item (g≈0.54) but barely transfers to *untested* material (g≈0.04) — a
  memory booster, not a transfer engine.

**6. Worked examples, fading & expertise reversal**
- **Worked-example effect** — Sweller & Cooper (1985); Barbieri et al. (2023): studying
  solutions beats unguided solving for novices — relevant to CFA's formula-heavy topics
  (TVM, bond pricing, ratios, option payoffs).
- **Expertise reversal + fading** — Kalyuga et al. (2003); Renkl & Atkinson (2003): that
  advantage *reverses* as expertise grows, so guidance must be faded toward independent
  solving.

**7. Moderate-but-costly elaboration**
- **Self-explanation** — Bisra et al. (2018): "why/how" prompts give g≈0.55, contingent on
  explanation quality and prior knowledge, at a time cost.
- **Elaborative interrogation** — Donoghue & Hattie (2021): "why is this true?" ≈ d 0.56,
  best with prior knowledge and precise, self-generated answers.

**8. What does NOT move the needle**
- **Feedback timing** — Kandemir et al. (recent); Kulik & Kulik (1988): immediate vs delayed
  ≈ null on average (g≈0.03) — not a lever; Anki's immediate feedback is already fine.
- **Learning styles** — Pashler et al. (2008); 2024 meta-analysis: no credible support for
  matching instruction to "style"; classified as a neuromyth.

---

## DOK 1 — Facts

> The raw, checkable findings behind the Knowledge Tree above.

- **Retrieval/testing** — practice testing beat restudy and all other comparison conditions
  ([Adesope et al. 2017](https://doi.org/10.3102/0034654316689306)); recall tests > recognition
  ([Rowland 2014](https://doi.org/10.1037/a0037559)).
- **Transfer of testing** — d=0.40 across 192 effect sizes / 122 experiments (N=10,382);
  near-zero once format-match, elaborated retrieval, and initial success are removed
  ([Pan & Rickard 2018](https://doi.org/10.1037/bul0000151)).
- **Utility ratings** — practice testing + distributed practice = *high*; elaborative
  interrogation, self-explanation, interleaving = *moderate*; rereading, highlighting,
  summarization, keyword mnemonic, imagery = *low*
  ([Dunlosky et al. 2013](https://doi.org/10.1177/1529100612453266)).
- **Spacing** — spaced > massed; optimal inter-study gap grows with the retention interval
  ([Cepeda et al. 2006](https://pubmed.ncbi.nlm.nih.gov/16719566/)).
- **Interleaving** — overall g=0.42; paintings 0.67; math 0.34; expository text n.s.; words
  −0.39; 59 studies / 238 effect sizes
  ([Brunmair & Richter 2019](https://pubmed.ncbi.nlm.nih.gov/31556629/)).
- **Successive relearning** — ~letter-grade classroom gain → ~40% higher a month later
  ([Rawson, Dunlosky & Sciartelli 2013](https://doi.org/10.1007/s10648-013-9240-4)); lab
  relearning potency d=1.52–4.19 ([Rawson et al. 2018](https://doi.org/10.1037/xap0000146)).
- **Pretesting** — specific (tested) effect g=0.54 (k=97); general (untested) effect g=0.04
  (k≈91) ([2023 meta-analysis](https://pubmed.ncbi.nlm.nih.gov/37640836/);
  [Pan & Carpenter 2023](https://doi.org/10.1007/s10648-023-09814-5)).
- **Generation effect** — d=0.40 (cued recall 0.55, free recall 0.32; >1-day delay 0.64);
  86 studies / 445 effect sizes ([Bertsch et al. 2007](https://doi.org/10.3758/bf03193441)).
- **Self-explanation** — g=0.55 (95% CI .45–.65); 69 effect sizes / 5,917 participants
  ([Bisra et al. 2018](https://eric.ed.gov/?id=EJ1186664)).
- **Cross-technique d's** — distributed 0.85, practice testing 0.74, elaborative
  interrogation 0.56, self-explanation 0.54, interleaving 0.47
  ([Donoghue & Hattie 2021](https://doi.org/10.3389/feduc.2021.581216)).
- **Worked examples** — worked > unguided for novices (Sweller & Cooper 1985;
  [Barbieri et al. 2023](https://www.danamillercotto.com/uploads/4/7/7/2/47725475/barbieri_et_al__2023__we_meta-analysis.pdf));
  **expertise reversal** — benefit reverses with expertise
  ([Kalyuga et al. 2003](https://doi.org/10.1007/s11251-009-9102-0)).
- **Feedback timing** — immediate vs delayed ≈ null, g=0.03 (95% CI [−0.08, 0.13]) (Kandemir
  et al., recent; Kulik & Kulik 1988).
- **Learning styles** — no support for the meshing hypothesis; a neuromyth
  ([Pashler et al. 2008](https://doi.org/10.1111/j.1539-6053.2009.01038.x);
  [2024 meta-analysis](https://doi.org/10.3389/fpsyg.2024.1428732)).
- **Transfer-appropriate processing** — memory best when study processing matches the test
  (Morris, Bransford & Franks 1977).
