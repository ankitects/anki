# Speedrun Brainlift — source layer (DOK 1 + DOK 2)

**Exam: MCAT** (472–528, four sections scored 118–132).

Status: v0.1 draft. This file covers §2's required contents #1, #2, #3 and opens #4.
POVs (#5), the AI consensus check (#6), and the traceability table (#7) are downstream
of the spike decision and are **not** drafted here on purpose — an inherited POV is
worth nothing.

---

## 0. Purpose, and what is out of scope

**Purpose.** Establish the evidence base for a claim about *where* MCAT preparation
breaks down, sharp enough that it forces a specific feature and can be shown wrong by
a specific number.

**In scope.** Retrieval practice, spaced scheduling, transfer of tested knowledge to
novel items, and metacognitive calibration — the chain from "student reviewed a card"
to "student answers an exam item they have never seen."

**Explicitly out of scope**, and why:

- *Motivation, adherence, gamification.* Real effects on outcomes, but unmeasurable in
  a week and not what the three scores claim to measure.
- *Content authoring quality at scale.* We generate cards, but we are not competing on
  deck breadth.
- *Claims about real score improvement.* §10 is explicit that we grade the bridge, not
  the final number. We can calibrate a memory model on held-back reviews; we cannot
  honestly gather student outcome data in three days, and will say so.

---

## 1. DOK 1 — Sources

Verification column is deliberate: it records which links were checked against the
publisher record on 2026-07-30 and which are still on trust. Anything marked `unverified`
must be confirmed before submission — a Brainlift that fakes a citation fails on its own terms.

### 1a. Systems lineage

**S1 — Woźniak, P. A., & Gorzelańczyk, E. J. (1994). "Optimization of repetition spacing
in the practice of learning." *Acta Neurobiologiae Experimentalis*, 54, 59–62.**
PubMed: <https://pubmed.ncbi.nlm.nih.gov/8023714/> (PMID 8023714) · journal:
<https://ane.pl/index.php/ane/article/view/1003/1003> · SM-2 spec (1987):
<https://www.supermemo.com/en/archives1990-2015/english/ol/sm2>
`verified 2026-07-30`

The origin of scheduling-by-algorithm. SM-2 keeps per-item ease and interval, multiplies
the interval on success, collapses it on failure. The 1994 paper derives a universal
inter-repetition interval formula **for a 95% retention target**, claimed to hold across
subjects regardless of learner capacity. Later SuperMemo work splits memory into
*retrievability* (probability of recall right now) and *stability* (how fast that decays) —
the two-component model everything downstream inherits.

Note the assumption baked in at the origin: the optimisation target is *retention of the
studied item set*. Every scheduler descended from this inherits that objective.

**S2 — Ye, J., Su, J., & Cao, Y. (2022). "A Stochastic Shortest Path Algorithm for
Optimizing Spaced Repetition Scheduling." *KDD '22*, 4381–4390.**
<https://dl.acm.org/doi/10.1145/3534678.3539081> · code: <https://github.com/maimemo/SSP-MMC>
`verified 2026-07-30`

The research foundation under FSRS. Models memory as DSR — Difficulty, Stability,
Retrievability — and treats scheduling as a stochastic shortest path problem that
minimises review cost subject to a memorisation target. Reports a **12.6% improvement over
prior state of the art** on MaiMemo data. FSRS4Anki grew out of this line of work and
FSRS is now built into Anki.

**S3 — Anki's own design decisions.** Deck options / desired retention in the manual
(<https://docs.ankiweb.net/deck-options.html>), the architecture note in this repo
(`docs/architecture.md`), and the choice to put scheduling in Rust behind a protobuf
boundary shared by every client. `verified 2026-07-30`

The load-bearing decision for us: **scheduling is backend, not UI.** Desktop, AnkiDroid
and AnkiMobile all call the same Rust. That is precisely why §3 demands the change live
in Rust and §8 demands we verify it on the phone — it is Anki's actual architecture, not
an arbitrary hoop.

### 1b. Learning science

**S4 — Roediger, H. L., & Karpicke, J. D. (2006). "Test-enhanced learning: Taking memory
tests improves long-term retention." *Psychological Science*, 17(3), 249–255.**
doi:10.1111/j.1467-9280.2006.01693.x `verified 2026-07-30`

Students read prose passages, then either restudied or took free-recall tests without
feedback. **At a 5-minute delay, restudying won. At 2 days and 1 week, testing won, and
by a wide margin.** The interaction is the whole point: the measurement interval decides
which method looks better.

**S5 — Pan, S. C., & Rickard, T. C. (2018). "Transfer of test-enhanced learning:
Meta-analytic review and synthesis." *Psychological Bulletin*, 144(7), 710–756.**
doi:10.1037/bul0000151 · PDF: <https://pdf.retrievalpractice.org/transfer/Pan_Rickard_2018.pdf>
`verified 2026-07-30`

**The crux paper for this project.** 192 transfer effect sizes, 122 experiments, 67
articles, N = 10,382, 40+ years. Random-effects transfer effect vs. a non-testing
re-exposure control: **d = 0.40, 95% CI [0.31, 0.50]**. Transfer is *greatest* across test
formats and to application and inference questions.

**S6 — Barnett, S. M., & Ceci, S. J. (2002). "When and where do we apply what we learn?
A taxonomy for far transfer." *Psychological Bulletin*, 128(4), 612–637.**
doi:10.1037/0033-2909.128.4.612 `verified 2026-07-30`

Argues a century of transfer research stalled because "transfer" was never dimensionalised —
people compared apples and oranges. Supplies **9 dimensions** (knowledge domain, physical
context, temporal context, functional context, social context, modality, and so on) along
which a transfer claim must be located.

**S7 — Soderstrom, N. C., & Bjork, R. A. (2015). "Learning versus performance: An
integrative review." *Perspectives on Psychological Science*, 10(2), 176–199.**
doi:10.1177/1745691615569000 · PDF via UCLA Bjork Lab `verified 2026-07-30`

Performance during training is an **unreliable index** of durable learning. Learning can
occur with no visible performance change, and — the direction that matters commercially —
performance can improve while learning does not. Some manipulations move the two in
*opposite* directions.

**S8 — Dunlosky, J., & Rawson, K. A. (2012). "Overconfidence produces underachievement:
Inaccurate self evaluations undermine students' learning and retention."
*Learning and Instruction*, 22(4), 271–280.** `verified 2026-07-30`

Students self-paced study of key-term definitions, took cued-recall tests, and judged their
own correctness. **Students who were less overconfident retained substantially more.**
Miscalibration is not merely a reporting problem; it changes study allocation and therefore
outcomes.

**S9 — Tulving, E., & Thomson, D. M. (1973). "Encoding specificity and retrieval processes
in episodic memory." *Psychological Review*, 80, 352–373.**
ERIC: <https://eric.ed.gov/?id=EJ083912> `verified 2026-07-30`

Retrieval succeeds to the extent that cues at test overlap cues at encoding. The mechanism
that predicts a cloze-shaped cue producing cloze-shaped competence.

**S10 — Brier, G. W. (1950). "Verification of forecasts expressed in terms of probability."
*Monthly Weather Review*, 78(1), 1–3.**
doi:10.1175/1520-0493(1950)078&lt;0001:VOFEIT&gt;2.0.CO;2 ·
<https://journals.ametsoc.org/view/journals/mwre/78/1/1520-0493_1950_078_0001_vofeit_2_0_co_2.xml>
`verified 2026-07-30`

The scoring rule §10 asks for. Mean squared error of probabilistic forecasts; decomposes
into calibration and refinement. This is the instrument, not a finding.

---

## 2. DOK 2 — In my own words: what I take, what I reject

**From S1/S2 (SuperMemo → FSRS).**
*Take:* the DSR framing, and that scheduling is a solved-enough optimisation problem with a
published objective function. We do not touch FSRS intervals; §8 requires our Rust change
keep them valid.
*Reject:* the implied scope. Both optimise *cost of maintaining recall of a fixed item set*.
Neither claims anything about performance on items the learner has never seen. FSRS is an
excellent answer to a question the MCAT does not ask.

**From S3 (Anki).**
*Take:* the backend boundary. Any signal we want on both desktop and phone must be computed
in Rust, or it will drift between clients.
*Reject:* the deck-options framing of "desired retention" as the top-level dial a student
tunes. Retention is an input to a score, not a goal a pre-med should be optimising directly.

**From S4 (Roediger & Karpicke).**
*Take:* testing beats restudy at exam-relevant delays; retrieval practice is the right
primitive. Also the methodological lesson — **report the delay, or the comparison is
meaningless.**
*Reject:* the leap the market makes from this to "therefore more cards." The dependent
variable was recall of studied prose, i.e. DOK 1.

**From S5 (Pan & Rickard).** *This is where I have to be careful, because it partly cuts
against me.*
*Take:* d = 0.40 is real, positive, and well-powered. Tested knowledge **does** transfer.
Anyone claiming "flashcards don't transfer" is contradicted by the best available evidence,
and a POV shaped that way would be a hot take, not a thesis.
*Reject / bound:* the generalisation to the MCAT regime. The corpus is dominated by lab
studies — modest item sets, short intervals, materials studied as prose and tested as
rephrased prose. That is **near transfer** in S6's terms: same knowledge domain, same
modality, adjacent functional context. An AnKing-scale cloze deck tested against an AAMC
passage item is several of S6's dimensions further out. d = 0.40 is a ceiling estimate for
our regime, not a point estimate — and it is measured against a *re-exposure control*, not
against the alternative allocation of the same study hour.

**From S6 (Barnett & Ceci).**
*Take:* the discipline of naming the dimensions. "Does it transfer?" is not a well-posed
question. Our coverage map and paraphrase test are two different dimensions and must be
reported separately.
*Reject:* nothing substantive. The taxonomy is a lens, not a claim.

**From S7 (Soderstrom & Bjork).**
*Take:* the sharpest available statement of why one blended number is dishonest. §5's
memory-vs-performance split is this distinction operationalised. Also the warning that our
own thesis feature could raise in-app performance while lowering learning — which is exactly
what §9's ablation exists to catch.
*Reject:* nothing. This is the paper the assignment is quietly built on.

**From S8 (Dunlosky & Rawson).**
*Take:* calibration is causal, not cosmetic, and it is cheap to measure — one confidence tap
before reveal. It also supplies the confidence indicator §5 requires on every score.
*Reject:* the setting. Key-term definitions, college students, lab conditions. The effect
direction should hold; the effect size should not be assumed.

**From S9 (Tulving & Thomson).**
*Take:* the mechanism that makes a paraphrase gap predictable rather than a hunch. If the
cue at test is a cloze frame, competence attaches to the cloze frame.
*Reject:* treating encoding specificity as destiny. It predicts a gap; it does not predict
the gap is large enough to matter at MCAT scale. That is an empirical question — ours.

---

## 3. DOK 3 — The tension already visible (opening; needs the teardown to complete)

Writing S5 next to S9 surfaces the real crux, and it is not the one I expected:

> The consensus does **not** say flashcards fail to transfer. It says they transfer at
> d ≈ 0.40, best of all to rephrased and inference items.

So the defensible claim cannot be "transfer doesn't happen." It has to be about **regime**:
that the meta-analytic estimate is drawn from a population of studies structurally unlike
high-volume cloze MCAT decks, and that in *this* regime the gap between card recall and
reworded-item accuracy is materially larger than d = 0.40 would lead a tool builder to expect.

Two things make that a thesis rather than a hot take:

1. It names its own falsifier. §8's paraphrase test — 30 cards, 2 rewordings each — measures
   the gap directly. If the gap comes back at or below the meta-analytic expectation, the
   claim is wrong, and that is a reportable result that §9 says scores well.
2. It is a **measurement** claim before it is a product claim. It says the number is not
   currently on anyone's dashboard, which the teardown can confirm or refute by observation.

**What everyone assumes and nobody checks (candidate, needs the teardown):** a targeted
search for primary literature on transfer *specifically from cloze-deletion flashcard
practice to passage-based reasoning items* returned no primary studies — only vendor blogs
and language-learning marketing. If that survives a proper database search, then the single
most-used study format in American medical admissions has essentially no direct transfer
evidence behind it. That absence, if real, is the frontier this Brainlift can legitimately
claim.

---

## 4. Open — blocked on Emily

- [ ] **Teardown** (§2): three tools used for real, six probes each. Cannot be delegated —
      §2 requires observed behaviour.
- [ ] **The spike sentence.** "Why MCAT students plateau around 505 after month two" is the
      assignment's own example and graders have read it. Needs a variant that is ours.
- [ ] **Three POVs**, one of which becomes the §9 ablation.
- [x] ~~Confirm the four `unverified` citations above.~~ All 10 sources verified 2026-07-30.
- [ ] Proper database search (PubMed / PsycINFO / ERIC) on cloze-to-passage transfer, to
      convert §3's "candidate" into a claim or kill it.
