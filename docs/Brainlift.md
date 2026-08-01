# Brainlift v1 — MCAT

**Spike:** TODO — one sentence. Not "how people study." Something like *"why MCAT students
plateau after month two despite rising Anki retention."*

> **How to use this file.** Every heading below is a stub: a prompt telling you what the
> point is, in one line. Write the actual paragraph yourself. If a stub doesn't earn its
> place once you try to write it, delete the stub. Nothing here is prose you have to keep.

---

## 1. Purpose and scope

- **Stub:** what this document is for, in three sentences.
- **Stub:** out of scope — content creation, tutoring, question banks, multi-exam. We measure
  the gap between remembering and performing. Nothing else.

---

## 2. Sources (DOK 1)

All citations below were independently verified. Quotes, page numbers, and — most
usefully — **what each source does NOT say** are in the companion file
`Brainlift-Sources.md`. Read that before leaning on any of these.

✅ = full text read · ☑️ = citation confirmed, full text not accessible

### Systems lineage

| # | Source | Status |
|---|--------|--------|
| L1a | Woźniak, P. A. (1990). *Optimization of learning*. Master's thesis, University of Technology, Poznań. SM-2 and the 0–5 grade scale, as published at [super-memory.com](https://super-memory.com/english/ol/sm2.htm) | ✅ |
| L1b | Woźniak, P. A., & Gorzelańczyk, E. J. (1994). Optimization of repetition spacing in the practice of learning. *Acta Neurobiologiae Experimentalis*, 54(1), 59–62. PMID 8023714 | ☑️ **not** the SM-2 paper — lineage only |
| L2a | Ye, J., Su, J., & Cao, Y. (2022). A stochastic shortest path algorithm for optimizing spaced repetition scheduling. *KDD '22*, 4381–4390. doi:10.1145/3534678.3539081 | ✅ |
| L2b | Su, J., Ye, J., Nie, L., Cao, Y., & Chen, Y. (2023). Optimizing spaced repetition schedule by capturing the dynamics of memory. *IEEE TKDE*, 35(10), 10085–10097. doi:10.1109/TKDE.2023.3251721 | ✅ |
| L2c | open-spaced-repetition, [*SRS Benchmark*](https://github.com/open-spaced-repetition/srs-benchmark) (~727M reviews, 10k users) | ✅ |
| L3 | Anki Manual — [Deck Options](https://docs.ankiweb.net/deck-options.html) (FSRS), [Studying](https://docs.ankiweb.net/studying.html) (answer buttons), [FSRS FAQ](https://faqs.ankiweb.net/frequently-asked-questions-about-fsrs.html) | ✅ |

### Learning science

| # | Source | Why it's here | Status |
|---|--------|---------------|--------|
| S1a | Bjork, R. A. (1994). Memory and metamemory considerations in the training of human beings. In *Metacognition: Knowing about knowing* (pp. 185–205). MIT Press | Coins "desirable difficulties" (p. 193) | ✅ |
| S1b | Bjork, E. L., & Bjork, R. A. (2011). Making things hard on yourself, but in a good way. In *Psychology and the real world* (pp. 56–64) | The actual definition (p. 58) — cite this one | ✅ |
| S2 | **Barnett, S. M., & Ceci, S. J. (2002).** When and where do we apply what we learn? A taxonomy for far transfer. *Psychological Bulletin*, 128(4), 612–637. doi:10.1037/0033-2909.128.4.612 | Formal taxonomy of transfer *distance*. Read first. | ✅ |
| S3 | Roediger, H. L., & Karpicke, J. D. (2006). Test-enhanced learning. *Psychological Science*, 17(3), 249–255. doi:10.1111/j.1467-9280.2006.01693.x | Baseline you argue past | ✅ |
| S4 | Sweller, J. (1988). Cognitive load during problem solving. *Cognitive Science*, 12(2), 257–285. doi:10.1207/s15516709cog1202_4 | Ceiling on how hard a probe may be | ✅ |
| S5 | Dunlosky, J., et al. (2013). Improving students' learning with effective learning techniques. *PSPI*, 14(1), 4–58. doi:10.1177/1529100612453266 | Will contradict something you want to believe | ✅ |
| S6 | **Koriat, A., & Bjork, R. A. (2005).** Illusions of competence in monitoring one's knowledge during study. *JEP:LMC*, 31(2), 187–194. doi:10.1037/0278-7393.31.2.187 | Foresight bias. ⚠️ **Narrower than it looks** — see the note below. | ✅ |
| S6b | Koriat, A., & Bjork, R. A. (2006). Illusions of competence during study can be remedied… *Memory & Cognition*, 34(5), 959–972 | Their own follow-up: retrieval practice is what *fixes* the bias | ✅ |
| S7 | Nelson, T. O., & Dunlosky, J. (1991). …the "delayed-JOL effect." *Psychological Science*, 2(4), 267–270. doi:10.1111/j.1467-9280.1991.tb00147.x | **Counter-evidence.** A prior retrieval attempt improves calibration — γ +.38 → +.90 | ☑️ paywalled everywhere |
| S7b | **Dunlosky, J., & Nelson, T. O. (1992).** Importance of the kind of cue for judgments of learning. *Memory & Cognition*, 20(4), 374–380. doi:10.3758/BF03210921 | **The pivotal one.** The calibration gain appears only when judging *from the cue alone*; it vanishes when the answer is on screen. | ✅ |

> ⚠️ **On S6 and S7 — the correction that makes the argument stronger.**
> Koriat & Bjork's foresight bias was demonstrated only at *study* time, with the
> answer visible and **no retrieval attempt first**. Anki does make you attempt
> first, so "the rating screen is foresight-biased" overreaches.
> What survives, and is fully supported: judging with the answer in view inflates
> the judgment (hindsight-type effect), *and* — S7b — the calibration benefit of a
> retrieval attempt only shows up when you judge from the cue alone. Anki elicits
> the attempt, then throws away the moment that would have made the judgment
> accurate. No study tests Anki's exact configuration; say so, and note that this
> is precisely the gap the pre-reveal timing measures.

### Response latency as a measurement signal

| # | Source | Why it's here | Status |
|---|--------|---------------|--------|
| R1 | **Mettler, E., Massey, C. M., & Kellman, P. J. (2016).** A comparison of adaptive and fixed schedules of practice. *JEP: General*, 145(7), 897–917. doi:10.1037/xge0000170 | **Closest match.** A latency-driven flashcard scheduler that beat fixed schedules. | ✅ |
| R2 | Pelánek, R. (2024). Leveraging response times in learning environments. *UMUAI*, 34(3), 729–752. doi:10.1007/s11257-023-09386-7 | Best single review — opportunities *and* caveats | ✅ open |
| R3 | Wise, S. L., & Kong, X. (2005). Response time effort. *Applied Measurement in Education*, 18(2), 163–183 | Built explicitly because self-reported effort is bias-prone | ✅ |
| R4 | **Papoušek, J., et al. (2015).** An analysis of response times in adaptive practice of geography facts. *EDM 2015*, 562–563 | **For the guessing section.** Fast+correct is ambiguous between mastery and lucky guess, measured at scale. | ✅ open |
| R5 | Benjamin, A. S., Bjork, R. A., & Schwartz, B. L. (1998). The mismeasure of memory. *JEP: General*, 127(1), 55–68 | **Counter-evidence.** Fast retrieval now can predict *worse* retention later. | ☑️ |
| R6 | Kyllonen, P. C., & Zu, J. (2016). Use of response time for measuring cognitive ability. *Journal of Intelligence*, 4(4), 14 | Slow could mean careful, not weak | ✅ open |

> ⚠️ **"Latency beats self-report" is your hypothesis, not a finding.** No study
> tests pre-reveal latency against post-reveal self-graded difficulty as competing
> scheduling inputs. Present it as what the experiment tests. R4 and R5 are the
> honest objections and belong in your own bibliography.

### Measurement framing

| # | Source | Status |
|---|--------|--------|
| M1 | Goodhart, C. A. E. (1975). Problems of monetary management: The U.K. experience. In *Papers in Monetary Economics* (Vol. I), Reserve Bank of Australia. Reprinted 1981 (Courakis, p. 116) and 1984 (doi:10.1007/978-1-349-17295-5_4) | ☑️ venue verified; no open copy of the original |
| M2 | Strathern, M. (1997). 'Improving ratings': Audit in the British University system. *European Review*, 5(3), 305–321 | ✅ full text read |
| M3 | Manheim, D., & Garrabrant, S. (2018). Categorizing variants of Goodhart's Law. arXiv:1803.04585 | ✅ not peer-reviewed |

> ⚠️ **On M2.** The famous sentence is on p. 308 and it is **Strathern's own
> aphorism** — she never quotes Goodhart, and credits the "Goodhart's law" label to
> Hoskin (1996). "The sentence everyone attributes to Goodhart is actually
> Strathern's" is correct; calling it a *restatement of Goodhart's sentence* is not.

---

## 3. In my own words (DOK 2)

- **Stub:** one short block per source. What it says, what you took, what you rejected.
  If you need a tab open to write it, you're not at DOK 2 yet.

---

## 4. Analysis (DOK 3)

### 4a. Goodhart — the frame

- **Stub:** the famous sentence isn't Goodhart's, it's Strathern's 1997 restatement.
- **Stub:** the failure here is the regressional/causal variant, not the adversarial one.
  Nobody is cheating. The student honestly optimizes retention; retention is honestly
  correlated with performance; the correlation doesn't survive being optimized against.
- **Stub:** therefore — Anki isn't broken and FSRS isn't wrong. The proxy is correct and
  decoupling under pressure. Say why that's a more interesting claim than "Anki bad."

### 4b. How the machine actually works

- **Stub:** define difficulty, stability, retrievability. Three sentences, no manual.
- **Stub:** the scheduler's entire objective is landing recall probability on a target.
- **Stub:** the payoff — there is no variable anywhere for the *form of the question*. All
  three are indexed to one card, one phrasing. So the model cannot distinguish knowing the
  concept from recognizing the phrasing, because nothing in it ever varies the phrasing.

### 4c. The rating buttons are not a measurement

- **Stub:** SM-2 had six grades — three fail, three pass. Anki collapsed the fail side to one
  button and kept three on the pass side. Ask why success needs gradation and failure doesn't.
- **Stub:** what the three pass grades actually distinguish: "correct with serious difficulty
  / after hesitation / perfect." That's a verbal description of response latency, written by
  someone grading himself on paper with no clock.
- **Stub:** the rating is collected *after the answer is on screen*, and a judgment made with
  the answer in view is inflated (S6, p. 188, citing Dunlosky & Nelson 1997 — hindsight-type).
  The student reports a memory of difficulty the reveal already contaminated.
  ⚠️ Word this carefully: do **not** say "foresight bias" — see the S6 note in §2.
- **Stub:** answer the objection yourself (S7): the retrieval attempt does improve calibration,
  and enormously — γ +.38 → +.90. But S7b is the kicker: that gain appears only when the
  judgment is made *from the cue alone*, and vanishes when the answer is on screen. Anki
  elicits the attempt, then discards the only moment that would have made the rating accurate.
- **Stub:** say plainly that no study tests Anki's exact configuration — attempt, reveal, then
  rate knowing the outcome. That gap is the thing worth measuring, not a weakness in the argument.
- **Stub:** the button is both a sensor and a dosage dial — pressing Hard changes the interval.
  Anyone who wants to see a card sooner writes a false difficulty observation into the log.

### 4d. Guessing

- **Stub:** nothing distinguishes "I knew it" from "I guessed right." Both record a pass.
- **Stub:** a lucky guess is a false positive in the memory model — retention inflated by
  exactly the amount that won't survive the real exam.
- **Stub:** important — do *not* argue for banning guessing. The MCAT has no guessing penalty;
  training students not to guess costs them points. The argument is measure it, don't forbid it.
- **Stub:** R4 measured exactly this at scale: fast-and-correct is genuinely ambiguous between
  solid knowledge and a lucky guess, and the relationship between speed and correctness is
  non-monotonic. Their odd finding is worth a sentence — when the answer is *wrong*, a slower
  response predicts a *better* next attempt.

### 4e. What the teardown exposed

Three tools, evidence in `~/firstmate/data/teardown-tools/report.md`.

| Tool | Measures | Implies | The gap |
|------|----------|---------|---------|
| Anki + MilesDown | DOK 1, self-graded recall of fixed text | Product implies little; **ecosystem** implies MCAT readiness | Only system in the landscape with a published calibration record (~727M reviews) — and the community still treats mature-card counts as readiness |
| UWorld | DOK 2–3 items; metrics reduce to % correct vs. other users | "Confirm your readiness," "highly predictive of your real MCAT score" | Product abstains from prediction; marketing doesn't. Own forum shows help text promising a percentile the product never renders |
| Blueprint | DOK 2–3 items; displays a 472–528 scaled score | "Statistically equivalent to the real MCAT, average difference of just 0.3 point" | That figure: n=91, retrospective, diagnostic only — sold next to ten full-lengths with no published equating. Independent analyses put them 2–7 points low |

- **Stub, and this is the headline:** **not one of the three ever shows a student a confidence
  interval.** The only thing resembling "I don't know" anywhere in the landscape is Anki's
  optimizer warning it lacks data.
- **Stub:** your own transfer exhibit — five facts you answered correctly in Anki, then passage
  items on those same topics. Recall-success vs. transfer-success, measured on yourself.
  *(Fill after the hands-on session.)*

### 4f. What the field assumes and nobody checks

- **Stub:** that a scheduler optimizing recall of a card is optimizing knowledge of the fact on
  it. Every system since SM-2 assumes the card *is* the knowledge.

---

## 5. Spiky POVs (DOK 4)

Each one: **consensus says X / I think Y / here's my evidence / here's what would prove me wrong.**

### POV 1 — flagship, tested in the ablation

**The probe.** A review the model is ~95% sure you'll pass carries almost no information —
spend it on a reworded variant instead. Measures transfer inside the habit the student already
has, at zero extra study time, and the same varied retrieval that exposes the gap also closes it.

- Consensus says: TODO
- I think: TODO
- Evidence: TODO
- **Wrong if:** semantic stability adds no predictive power over plain FSRS on held-back novel
  questions. State the threshold before you unblind.

### POV 2 — the rating instrument

**Self-reported difficulty is a paper-era affordance, not a measurement.** Contaminated by the
reveal, entangled with the scheduling control, and the clean signal — pre-reveal latency — was
never even recorded separately from it.

- Consensus says: TODO
- I think: TODO
- Evidence: §4c, plus the code finding (Anki times question-shown → rating-pressed as one number)
- **Wrong if:** the button predicts held-back transfer performance better than normalized
  pre-reveal latency does. Testable on existing data, costs zero study time.

### POV 3 — abstention

**Refusing to show a number is a feature.** Uncertainty is the progression system, not a caveat.

- Consensus says: TODO
- I think: TODO
- Evidence: §4e — nobody in the landscape does this
- **Wrong if:** TODO

---

## 6. AI consensus check

- **Stub:** pass one, POV cold, no evidence. Log objections word for word. Agreement is a bad sign.
- **Stub:** pass two, with evidence. Supply it and stop — don't argue. Make it name what changed.
- **Stub:** what moved.

---

## 7. Traceability table

| POV | What it forced me to build | How I'll know it was wrong |
|-----|---------------------------|----------------------------|
| 1 — probe | `probes` table + `AddProbe` (schema 20); substitution branch in `get_queued_cards` gated on retrievability; `variant_id` on the review log; AI generation pipeline with a quality gate (12.5% measured rejection) and a no-AI baseline | Probes pass at the same rate as the originals they replace — i.e. no measurable transfer gap. Second stability per card was **deliberately not built**: outcomes are recorded, not yet modelled. |
| 2 — rating instrument | Split the review timer: `revlog.reveal_millis` (schema 19), recording question→reveal separately from the total | Pre-reveal latency fails to beat the button at predicting probe outcomes. Note this is untested in the literature (§2, R-group) — it is my hypothesis, not a finding I inherited. |
| 3 — abstention | Give-up rule with published thresholds; Readiness withheld at launch and stating why; a visible `unmapped` bucket for cards the app can't place | The app withholds numbers it could honestly have shown — abstention that is really just missing work. Falsified in the other direction if it ever shows a number the design says is untrustworthy. |

---

## 8. By Sunday — what changed

- **Stub:** which POV survived contact with data, which didn't. "I was wrong, here's the
  evidence" scores well.
