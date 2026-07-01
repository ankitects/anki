# BrainLift — Bridging Memory → Performance in Anki (CFA Level I)

## Owners

- **William** — primary owner / author

---

## Purpose

**Core goal:** choose and implement an evidence-based study technique into Anki that bridges the gap between memory and performance. The feature should transfer memorized facts to the application-style multiple-choice questions of the CFA Level I exam beyond what spaced repetition already does, and prove that it works using a clean ablation. Plain flashcards help memorization, not transfer. Thus, the thesis is that the _schedule and selection of cards_, not just their spacing, can be engineered to close that gap for CFA L1.

**Target exam — CFA Level I:** pass/fail; 180 standalone multiple-choice questions (A/B/C);
fact- and formula-heavy with an ethics/case component; organized as 10 topic areas →
readings → Learning Outcome Statements (LOS). Its defining demands — discriminating among
_confusable_ categories (FIFO/LIFO/weighted-average, Macaulay/modified/effective duration,
forwards/futures/swaps, the Ethics Standards) and _applying_ a formula or rule to a new
mini-scenario — are exactly the Memory→Performance gap this project targets.

**What baseline Anki already does:** it is already
retrieval practice (flashcards) + an optimized spacing engine (FSRS). Queues are ordered by
**card state and due date**, never by content; FSRS assigns each card a memory state
(stability, difficulty) toward a desired
retention (default 0.90); new and review cards are mixed via an "Intersperser"; every card
is scheduled **independently** (sibling burying is the only inter-card relationship);
production is supported via type-in/cloze; feedback is immediate; grading is
Again/Hard/Good/Easy.

**Three gauges — the questions this project's app answers (separately):** **Memory** (can the
student recall the fact?), **Performance** (can they answer a _new_, held-out question using it?),
and **Readiness** (a calibrated prediction of the score they'd get today).

**In scope:** engine-level, cleanly-ablatable techniques that target transfer for CFA L1 —
graph/cluster scheduling (`SPOV 1`), FSRS-driven faded worked examples for the formula-heavy
topics (Quant, Fixed Income, Derivatives, Corporate, Portfolio Management; `SPOV 2`), and
surfacing confusable siblings for contrast (`SPOV 3`); and
the three-way ablation — **feature ON vs feature OFF vs unmodified Anki** — on the **same CFA
questions** and **equal total study time**, scored on a held-out bank of CFA-style MCQs
(Performance), delayed recall (Memory), and calibrated pass-probability (Readiness).

**Out of scope:** re-implementing retrieval practice or spacing (already in Anki/FSRS);
UI-only techniques that can't be cleanly ablated at the engine; other
exams and **CFA Levels II–III** (vignette/essay formats), plus multi-subject breadth — this
project is deliberately scoped to **CFA Level I**; debunked ideas (learning styles).

## DOK 4 — Spiky Points of View

### SPOV 1 — Schedule the graph, not the card _(spine)_

Conventional SRS treats each card as an independent atom on its own due date — but for CFA L1
that independence is the ceiling on transfer; the schedulable unit should be a _linked cluster_
of cards, not a lone card. This way, the app can work around topics, not individual questions, and
schedule questions that are either similar or different depending on the state of the user.
**Grounding:** Anki baseline (cards scheduled independently; sibling-burying is the only
inter-card relationship); transfer moderators (Pan & Rickard 2018).

### SPOV 2 — Fade the scaffold, and drive the fade off FSRS state

Novices need the worked solution while experts are slowed by it (this is known as expertise reversal), so guidance
must _fade_ gradually: worked → faded (cloze, backward-fading) → solve (format-matched A/B/C MCQ). The
spiky part isn't "fading is good" (experts agree) — it's that the fade level should be read off
FSRS's **existing** stability/difficulty, not a fixed/authored schedule or a separate counter. This way, fading is determined dynamically on a per user basis.
**Grounding:** worked-example effect (Sweller & Cooper 1985; Barbieri 2023); expertise reversal
(Kalyuga 2003); generation effect (Bertsch 2007); transfer-appropriate processing (Morris 1977);
FSRS stability/difficulty baseline.

### SPOV 3 — Surface confusable siblings; don't bury them

Anki _buries_ siblings to avoid interference; for CFA the interference **is** the lesson — the
scheduler should place FIFO/LIFO, the duration trio, and the Ethics Standards back-to-back. These are all related ideas. This way, the user can not only memorize facts, but also see how different topics interact with each other. Furthermore, similar topics build upon each other in the user's learning experience.
**Grounding:** interleaving is strongly material-dependent — overall g=0.42, perceptual
categories ~0.67, math meta-average ~0.34 (Brunmair & Richter 2019), but _problem-type_
discrimination (the CFA case) d=0.42→0.79 at a month (Rohrer, Dedrick & Stershic 2015; Rohrer &
Taylor 2007). We wager CFA's confusables sit toward the upper end — an open question the ablation
tests. Anki sibling-burying baseline.

---

## Experts

The thesis rests on a panel: **Carl Hendrick** synthesizes the field, and four primary researchers
anchor the pillars below.

### Carl Hendrick — the synthesizer

Translates the science of learning into practice (_How Learning Happens_ with Kirschner;
_Instructional Illusions_); professor at Academica University, UNESCO IBE _Science of Learning_
board. His recurring theme is the **research–practice gap**: well-evidenced techniques become
"lethal mutations" when misapplied — e.g., retrieval drilled before comprehension, or under load —
the exact failure mode this project must design around. **Relevant to:** the overall throughline — _retrieval/spacing **consolidate**; construction,
induction, and transfer need **desirable (not excessive) difficulty**, faded guidance, and
discrimination_ — plus the consolidation-vs-construction and retrieval×load caveats that keep the
build honest. _(DOK 2 #9; ties `SPOV 1`–`3` together.)_

### Steven C. Pan — transfer of learning

Transfer-of-learning researcher behind the transfer-of-testing meta-analysis (Pan & Rickard 2018)
and the pretesting work (Pan & Carpenter 2023). His synthesis of 192 effect sizes is the field's
definitive map of _when_ the testing effect generalizes versus stays inert — the empirical backbone
of this project's "does it transfer?" question. **Relevant to:** the core Memory→Performance
thesis — _whether_ testing transfers and the moderators it needs (format-match, elaboration,
initial success), which DOK 3 turns into a scheduler spec. _(DOK 1/2 "transfer of testing,"
"pretesting"; → SPOV 1.)_

### John Sweller — cognitive load & worked examples

Originator of **Cognitive Load Theory** (UNSW); the worked-example effect (Sweller & Cooper 1985).
CLT — among the most influential theories in instructional design — explains learning through the
limits of working memory, and is the parent framework behind worked examples, expertise reversal,
and fading. **Relevant to:** `SPOV 2` (worked→faded→solve) and the retrieval-under-load boundary — the case
for building/showing before drilling. _(DOK 2 #6, #9; → SPOV 2.)_

### Doug Rohrer — interleaving & discrimination

Showed interleaved math practice forces choosing the right method _from the problem itself_
(Rohrer, Dedrick & Stershic 2015; Rohrer & Taylor 2007). His classroom randomized trials (USF)
moved interleaving out of the lab into real instruction and reframed it as a trainer of _strategy
selection_ — recognizing which method a problem calls for, the exact demand of a cumulative exam.
**Relevant to:** `SPOV 3` (surface
confusables) and the formula/problem-type contrast — the closest analog to CFA quant.
_(DOK 2 #3; → SPOV 3.)_

### John Dunlosky & Katherine Rawson — utility & successive relearning

The utility-ratings review (only testing + spacing rated "high"; Dunlosky 2013) and **successive
relearning** (retrieval-to-criterion; Rawson & Dunlosky 2013/2018). Their Kent State lab produced
both the field's most-cited "what actually works" audit and the successive-relearning paradigm —
together the closest thing to an evidence-ranked playbook for durable learning. **Relevant to:** the baseline
premise (retrieval + spacing already "won") and the **mastery criterion** inside `SPOV 2`'s gate.
_(DOK 2 #2, #4.)_

---

## DOK 3 — Insights

- **The transfer moderators are a scheduler spec, not study advice.** Pan & Rickard's three
  conditions — format-match, elaboration, initial success — are usually read as tips, but each
  maps to an engine knob: format = rung type (cloze vs MCQ), elaboration = card transform,
  success = promotion gate. _Common practice wrong:_ treating transfer as a property of content.
  _New rule:_ build the scheduler **to the moderators**. _(→ SPOV 1.)_
- **Anki's one inter-card rule trades Performance for Memory.** Sibling-burying enforces _spacing_
  (which strengthens the **Memory** gauge) but suppresses _contrast_ (which the **Performance**
  gauge needs for discrimination) — so the only relationship Anki models buys retention at
  transfer's expense, a tension no single source states. _Common practice wrong:_ treating burying
  as free. _New rule:_ for confusable clusters, schedule for contrast even at a spacing cost.
  _(→ SPOV 3.)_
- **Fade on an expanding schedule, not per session.** Cepeda's optimal gap grows with the
  retention interval; applied to _scaffolding_ (not just review), peel one step per expanding
  interval. _New rule:_ fading is a spacing decision, not a per-sitting one. _(→ SPOV 2.)_
- **Distractors and interference edges are the same artifact.** The misconception-based wrong
  answers the item-generators emit (forgot ÷k, used annual) _are_ the confusability coordinates
  the interleaver needs — authoring one authors the other. _New rule:_ build item-generation and
  discrimination-scheduling together. _(→ SPOV 2 + SPOV 3.)_

---

## DOK 2 — Knowledge Tree

**1. Retrieval, generation & transfer**

- **Retrieval practice (testing effect)** — Rowland (2014); Adesope et al. (2017): practice
  testing beats restudy and every other control, recall > recognition — retrieval _changes_
  memory, it doesn't just measure it. Anki's flashcards already _are_ this.
- **Transfer of testing** — Pan & Rickard (2018): testing transfers to new questions at
  d≈0.40, but mainly across formats and to application/inference items, and mostly when
  practice is format-matched, elaborated, and initially successful — strip those and transfer
  collapses toward zero.
- **Generation effect** — Bertsch et al. (2007): producing an answer beats reading it
  (d≈0.40, larger after a day) — type-in/cloze out-teach passive review.
- **Transfer-appropriate processing** — Morris, Bransford & Franks (1977): memory is best
  when study processing matches the test, so practicing _application under retrieval_ is what
  bridges memory→performance (i.e., drilling CFA-style application MCQs, not just recall).

**2. Spacing — already owned by FSRS**

- **Distributed practice** — Cepeda et al. (2006): spaced beats massed; the optimal gap grows
  with the retention interval (as a decreasing ratio) — exactly FSRS's per-card job.
- **Utility ranking** — Dunlosky et al. (2013): of 10 techniques, only practice testing and
  distributed practice earned "high utility" — and Anki ships both already.

**3. Interleaving — a discrimination trainer**

- **Interleaving (perceptual/visual categories)** — Brunmair & Richter (2019): moderate on
  average (g=0.42) but strongly material-dependent — large for confusable/visual categories
  (paintings g=0.67), modest for math (0.34), _negative_ for word lists (−0.39).
  (Tension: Dunlosky rates it only "moderate"; Donoghue & Hattie d≈0.47.)
- **Interleaving in math (problem-type discrimination)** — Rohrer, Dedrick & Stershic (2015);
  Rohrer & Taylor (2007): mixing _problem types_ (vs blocking) forces choosing the right method
  _from the problem itself_ — classroom RCT d=0.42 immediate → 0.79 at a month. This is the
  closer analog for CFA's _formula/problem-type_ confusables than the perceptual-category
  meta-analysis, and CFA L1's distractor-heavy MCQs are precisely a "which approach applies" test.

**4. Successive relearning & mastery**

- **Successive relearning** — Rawson & Dunlosky (2013, 2018): retrieval to a mastery
  criterion across spaced sessions; lab d=1.5–4.2, ~a letter-grade in class growing to ~40%
  a month later. It is retrieval + spacing + a criterion — well suited to CFA L1's large,
  long-horizon fact/ethics-rule volume.

**5. Pretesting — surveyed, but a memory booster, not a transfer engine** _(boundary evidence)_

- **Pretesting** — Pan & Carpenter (2023); 2023 meta-analysis: a wrong guess before study
  helps the _tested_ item (g≈0.54) but barely transfers to _untested_ material (g≈0.04). Because
  CFA is graded on _novel_ application items, this is the wrong lever — kept here only to show it
  was considered and consciously set aside (it earns no SPOV).

**6. Worked examples, fading & expertise reversal**

- **Worked-example effect** — Sweller & Cooper (1985); Barbieri et al. (2023): studying
  solutions beats unguided solving for novices — relevant to CFA's formula-heavy topics
  (TVM, bond pricing, ratios, option payoffs).
- **Expertise reversal + fading** — Kalyuga et al. (2003); Renkl & Atkinson (2003): that
  advantage _reverses_ as expertise grows, so guidance must be faded toward independent
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

**9. Boundary conditions — when retrieval & transfer _don't_ deliver (the Expert's caveats)**

- **Consolidation vs construction** — Koedinger, Corbett & Perfetti (2012): the KLI framework
  splits learning into _memory/fluency_, _induction/refinement_, and _understanding/sense-making_
  — so retrieval (a memory process) strengthens traces but doesn't by itself do induction.
  "Remembering is not knowing"; understanding must be built before drilling consolidates it.
- **Retrieval under high cognitive load** — Redifer et al. (2025): with demanding material,
  retrieval practice gave no edge over rereading and _cognitive load fully mediated_ the effect
  (higher load → worse delayed test) — so sequence retrieval _after_ comprehension, not during it.
- **Near vs far transfer** — Barnett & Ceci (2002): transfer is multi-dimensional (content ×
  context), so a single "far-transfer" effect size is misguided. Near transfer (same domain, new
  surface) is achievable — exactly the CFA target — while far transfer is rare.

---

## DOK 1 — Facts

- **Retrieval/testing** — practice testing beat restudy and all other comparison conditions
  ([Adesope et al. 2017](https://doi.org/10.3102/0034654316689306)); recall tests > recognition
  ([Rowland 2014](https://doi.org/10.1037/a0037559)). The "testing effect" is among the most
  replicated results in learning science: the _act of retrieving_ strengthens a memory more than
  re-reading it does. Production-format tests (recall) build more durable memory than recognition
  (multiple-choice), and the gap typically _widens at longer delays_ — so testing is a learning
  tool, not just a measurement.
- **Transfer of testing** — d=0.40 across 192 effect sizes / 122 experiments (N=10,382);
  near-zero once format-match, elaborated retrieval, and initial success are removed
  ([Pan & Rickard 2018](https://doi.org/10.1037/bul0000151)). Testing _does_ carry over to new
  questions, but mainly across formats and to application/inference items — and only when the
  practice was format-matched, elaborated, and initially successful. Strip those moderators and the
  benefit collapses toward zero, so transfer is real but **conditional**, not automatic.
- **Utility ratings** — practice testing + distributed practice = _high_; elaborative
  interrogation, self-explanation, interleaving = _moderate_; rereading, highlighting,
  summarization, keyword mnemonic, imagery = _low_
  ([Dunlosky et al. 2013](https://doi.org/10.1177/1529100612453266)). The review graded 10 common
  techniques across diverse learners, materials, and test types, so "high utility" means _robust
  and general_, not merely large in one study. Tellingly, the techniques students use most
  (rereading, highlighting) ranked lowest — popularity ≠ effectiveness.
- **Spacing** — spaced > massed; optimal inter-study gap grows with the retention interval
  ([Cepeda et al. 2006](https://pubmed.ncbi.nlm.nih.gov/16719566/)). Distributing study across
  sessions beats cramming the _same total time_, and the best gap scales with how long you must
  retain — a longer retention horizon calls for a longer gap (as a decreasing ratio). This
  expanding-gap principle is exactly what FSRS automates per card.
- **Interleaving** — overall g=0.42; paintings 0.67; math 0.34; expository text n.s.; words
  −0.39; 59 studies / 238 effect sizes
  ([Brunmair & Richter 2019](https://pubmed.ncbi.nlm.nih.gov/31556629/)). Mixing different
  categories or problem types (instead of blocking one at a time) helps learners _discriminate
  which approach applies_. But the effect is strongly material-dependent — large for confusable
  visual categories, modest for math, and even _negative_ for arbitrary word lists — so it is a
  discrimination tool, not a universal good.
- **Interleaving in math (problem-type)** — interleaved > blocked for choosing _which method
  applies_; classroom RCT d=0.42 immediate, 0.79 at 30-day delay
  ([Rohrer, Dedrick & Stershic 2015](https://doi.org/10.1037/edu0000001);
  [Rohrer & Taylor 2007](https://doi.org/10.1007/s11251-007-9015-8)). Blocked practice tells
  students the method _before_ they read the problem, so it never trains strategy selection;
  interleaving forces choosing the approach from the problem itself — the skill a cumulative exam
  demands. Notably the advantage _grew_ from the immediate to the delayed test, the opposite of a
  cramming illusion.
- **Successive relearning** — ~letter-grade classroom gain → ~40% higher a month later
  ([Rawson, Dunlosky & Sciartelli 2013](https://doi.org/10.1007/s10648-013-9240-4)); lab
  relearning potency d=1.52–4.19 ([Rawson et al. 2018](https://doi.org/10.1037/xap0000146)). The
  method is retrieval _to a mastery criterion_ (e.g., recall correctly N times) repeated across
  spaced sessions — i.e., retrieval + spacing + a criterion bolted together. Its effects are among
  the largest in the literature, and crucially the advantage _widens_ over the retention interval.
- **Pretesting** — specific (tested) effect g=0.54 (k=97); general (untested) effect g=0.04
  (k≈91) ([2023 meta-analysis](https://pubmed.ncbi.nlm.nih.gov/37640836/);
  [Pan & Carpenter 2023](https://doi.org/10.1007/s10648-023-09814-5)). Guessing _before_ being
  taught (errorful generation) reliably boosts memory for the _specific items_ you were pretested
  on, yet that gain barely spreads to related, untested material. So pretesting is a targeted
  memory booster, not a transfer engine — which is why it earns no SPOV here.
- **Generation effect** — d=0.40 (cued recall 0.55, free recall 0.32; >1-day delay 0.64);
  86 studies / 445 effect sizes ([Bertsch et al. 2007](https://doi.org/10.3758/bf03193441)).
  _Producing_ an answer (filling a blank, completing a cloze) beats reading the same answer,
  because generation forces effortful processing instead of passive recognition. The benefit is
  larger after a delay, which is why type-in/cloze cards out-teach plain front-and-back review.
- **Self-explanation** — g=0.55 (95% CI .45–.65); 69 effect sizes / 5,917 participants
  ([Bisra et al. 2018](https://eric.ed.gov/?id=EJ1186664)). Prompting learners to explain _how_ and
  _why_ as they study reliably improves understanding, but the size depends on the _quality_ of the
  explanations and on prior knowledge — and it adds time. It's a solid, general booster with a real
  cost attached, which is why it sits in the "moderate-but-costly" tier rather than as a lever.
- **Cross-technique d's** — distributed 0.85, practice testing 0.74, elaborative
  interrogation 0.56, self-explanation 0.54, interleaving 0.47
  ([Donoghue & Hattie 2021](https://doi.org/10.3389/feduc.2021.581216)). Placing the major
  techniques on one common scale lets you rank them directly, and the ordering broadly corroborates
  Dunlosky — distributed practice and testing sit on top. It's a useful cross-check that the two
  techniques Anki already ships are the highest-leverage ones.
- **Worked examples** — worked > unguided for novices (Sweller & Cooper 1985;
  [Barbieri et al. 2023](https://www.danamillercotto.com/uploads/4/7/7/2/47725475/barbieri_et_al__2023__we_meta-analysis.pdf));
  **expertise reversal** — benefit reverses with expertise
  ([Kalyuga et al. 2003](https://doi.org/10.1007/s11251-009-9102-0)). For a novice, studying a full
  solution beats unguided problem-solving because it avoids overloading working memory with
  means-ends search. But as competence grows the guidance becomes redundant and _slows_ the
  learner, so support must be **faded** toward independent solving — the basis for worked→faded→solve.
- **Feedback timing** — immediate vs delayed ≈ null, g=0.03 (95% CI [−0.08, 0.13]) (Kandemir
  et al., recent; Kulik & Kulik 1988). Whether feedback arrives instantly or after a delay makes
  essentially no average difference, and the confidence interval straddles zero. Timing is
  therefore not a lever worth engineering — Anki's immediate Again/Hard/Good/Easy feedback is
  already fine.
- **Learning styles** — no support for the meshing hypothesis; a neuromyth
  ([Pashler et al. 2008](https://doi.org/10.1111/j.1539-6053.2009.01038.x);
  [2024 meta-analysis](https://doi.org/10.3389/fpsyg.2024.1428732)). The "meshing" claim — that
  matching instruction to a learner's preferred style (visual/auditory/etc.) improves learning —
  repeatedly fails the proper experimental test (the style × instruction interaction). Despite
  enormous popularity it is classified as a neuromyth, and is explicitly out of scope here.
- **Transfer-appropriate processing** — memory best when study processing matches the test
  (Morris, Bransford & Franks 1977). Performance depends not just on _how well_ you encoded but on
  whether the _kind_ of processing at study matches what the test requires. For an application
  exam, that implies practicing **application under retrieval** (CFA-style MCQs), not recall alone —
  the principle that assigns the MCQ format to the solve rung.
- **Retrieval × cognitive load** — with demanding material, retrieval practice ≈ rereading on the
  final test; cognitive load _fully mediated_ performance (load→score B=−0.54)
  ([Redifer et al. 2025](https://doi.org/10.1007/s11251-025-09758-z)). When working memory is
  already saturated by complex content, the extra load of effortful retrieval cancels its usual
  benefit. The practical rule is to _sequence retrieval after comprehension_ — once the material is
  understood enough to leave spare capacity for retrieval to work.
- **Knowledge-Learning-Instruction (KLI)** — three learning-event classes: memory/fluency,
  induction/refinement, understanding/sense-making — memory ≠ induction
  ([Koedinger, Corbett & Perfetti 2012](https://doi.org/10.1111/j.1551-6709.2012.01245.x)). The
  framework holds these are _different_ processes with _different_ optimal instruction, so a memory
  process (retrieval) cannot by itself produce induction or understanding. It's the formal basis
  for "remembering is not knowing" and for treating construction as a stage in its own right.
- **Near vs far transfer** — transfer is multi-dimensional (content × context); a single
  far-transfer effect size is misguided; near transfer achievable, far transfer rare
  ([Barnett & Ceci 2002](https://doi.org/10.1037/0033-2909.128.4.612)). The taxonomy spans nine
  content/context dimensions, so "did transfer occur?" only means something relative to _how far_.
  Near transfer — same domain, new surface (a fresh CFA item of a learned LOS) — is well supported,
  whereas far transfer across domains is rare, which is why this project targets the near case.
