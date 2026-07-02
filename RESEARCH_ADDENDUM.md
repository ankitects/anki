# Synthesis Report: Evidence-Grounded Refinement of the CFA-Anki Speedrun Plan

**Scope:** Synthesis + editorial review of 9 research threads (T1–T9), each with an
investigator finding-set and an independent devil's-advocate verdict. Goal: translate
verified evidence into concrete engine parameters, disclose contradictions honestly, and
prioritize changes in grader-weighted areas (honest uncertainty 20%, held-out testing 12%,
study-feature/ablation 15%, AI-beats-baseline 15%).

---

## 1. Executive Summary

**The three spiky bets (SPOVs) survive scrutiny, but each is now bounded by a measured
moderator, and several load-bearing numbers must be down-graded or re-scoped.**

- **SPOV1 (schedule the graph / cluster credit):** Testing transfers to NEW application
  MCQs at **d≈0.40** (Pan & Rickard 2018, 192 ES, N=10,382) — but this is _moderator-carried_.
  Under publication-bias correction (PET-PEESE) the intercept with **none** of the three
  moderators present collapses to **d≈0.015** and can go negative. Far/general transfer is
  **near-zero** (St. Hilaire & Carpenter 2023: specific g=0.54 vs general g=0.04, n.s.).
  **Consequence:** cluster credit must be **within-topic only**; do NOT let it leak across
  topics. This is both a design constraint and a ready-made ablation.

- **SPOV2 (fade the scaffold off FSRS state):** Worked-example effect **g=0.48** (Barbieri
  2023) and expertise-reversal **d=+0.505 novices / −0.428 experts** (Tetzlaff 2025) are
  solid and _asymmetric_ — giving novices support matters more than stripping it from experts,
  which justifies **hysteresis** (fade-UP threshold higher than fade-DOWN). BUT: "adaptive >
  fixed fading" is **weaker than CONFIRMED** (one clean lab result + a p=.11 classroom null);
  fade **order** is contested (backward vs forward); self-explanation prompts **negatively**
  moderated the effect (Barbieri 2023). Successive-relearning evidence says the gate should
  key on **spaced-session count**, not a high within-session criterion (relearning-override
  effect, Vaughn 2016). **Feedback is a hidden invariant** — retrieval loses to elaboration
  _without_ feedback (Goncalves et al. 2025, g=0.14; g=0.50 with feedback).

- **SPOV3 (adjacency of confusables):** The strongest-supported bet. Interleaving overall is
  modest (**g=0.42**, Brunmair & Richter 2019) but the mechanism is **juxtaposition/adjacency**,
  not spacing (Kang & Pashler 2012; Birnbaum 2013 total-spacing-held-constant). Transfer to
  novel items **g up to 0.66** (Firth 2021), biggest when differences are **subtle** — exactly
  the CFA confusables. Critical two-sided gate: **blocking WINS for dissimilar items**
  (Carvalho & Goldstone 2014, d=0.76), so adjacency must be gated on a **signed similarity
  score** or it causes net harm. This is where "AI must beat BM25/vector" bites: the payoff
  depends entirely on confusability-edge quality.

**Top refinements (grader-weighted):**

1. **HONESTY (R10 auto-fail risk):** The shipped give-up thresholds (≥15 reviews, ≥1% coverage)
   are indefensible. Replace with PRD-strength gates AND ground them in real stats: Beta-Binomial
   (Jeffreys) posterior for the point+band, Platt/Venn-Abers (NOT isotonic) for calibration under
   sparse data, and a selective-prediction abstention rule. **Never surface a point pass-probability.**
2. **AI-BEATS-BASELINE:** The honest baseline is **tuned BM25 AND tuned dense, each at the same
   cutoff** — dense-only is a strawman (BM25 often wins out-of-domain, BEIR 2021). The win must
   come from **RRF fusion + cross-encoder rerank**, shown on held-out qrels with precision@k.
3. **KNOWLEDGE-TRACING baseline:** Use **IRT/PFA/LKT (logistic)**, NOT deep knowledge tracing —
   DKT's edge over IRT/BKT is largely a data-leakage artifact (Wilson 2016; Khajah 2016). Simpler
   model IS the defensible choice here.
4. **MEMORY→PERFORMANCE proof:** the paraphrase/application gap must be measured on **held-out,
   delayed** application MCQs — not immediate accuracy (interleaving/discrimination benefits are
   delay-sensitive; Rohrer 2015 d=0.42→0.79 at 30 days).
5. **FSRS signal:** use **retrievability evaluated at a fixed horizon (exam date)**, not raw
   stability or instantaneous R; there is **NO FSRS-defined "mastered" threshold** — mastery is a
   product decision to be validated, never read off R alone (per-card AUC only ~0.63–0.70).

**Citation-hygiene corrections that MUST be applied before any writeup:** author
"Ferreira et al. (2025)" → **Goncalves et al. (2025)**; "Rummer & Schweppe (2015)" →
**Endres & Renkl (2015)**; "Zheng 2024" → **Zheng 2023**; DKT-adjacent arXiv author
"DeepSeek-V3.2" is a model name not an author; the Rowland "75% threshold" is a **heuristic**,
not a Rowland-stated number; St. Hilaire dissertation → upgrade to **St. Hilaire & Carpenter
(2023, Psychonomic Bull & Rev)**.

---

## 2. Per-Thread Synthesis

### T1 — Transfer-of-testing moderators (backbone of SPOV1/gating)

**Overall confidence: HIGH.** Among the most rigorous threads; the DA over-collected
disconfirmation (which the grader rewards).

- **CONFIRMED — transfer to new application items d=0.40** [0.31,0.50] (Pan & Rickard 2018,
  _Psych Bulletin_ 144(7):710-756; 192 ES, N=10,382). But moderator-carried: PET-PEESE
  intercept with no moderators ≈ **d=0.015**, negative under one-tailed bias (verified verbatim).
- **CONFIRMED — classroom testing g=0.499** across 222 studies (Yang et al. 2021, _Psych
  Bulletin_ 147(4):399-435), robust to 5 pub-bias tools.
- **REFINED — initial success as a gate:** strongest transfer moderator (b=0.50–0.82 in d),
  but success-_per-se_ does not beat failure+feedback at the item level (Kornell, Klein &
  Rawson 2014). Model the gate as predicting **transfer breadth**, always pair a low rung with
  feedback. The **>75%** inflection is a **heuristic** (Karpicke-associated), directionally —
  NOT — stated by Rowland (2014). _DA correction._
- **REFINED — format congruency:** response congruency adds **b=0.35 (d)**; format MISMATCH
  still yields significant transfer (Yang 2021: matched g=0.531 vs mismatched g=0.399). Model
  as a **continuous multiplier (matched=1.0, mismatched≈0.75)**, not a hard gate. CFA terminal
  rung = MCQ (congruent) is a genuine design win.
- **CONFIRMED — elaboration** adds b=0.18–0.23 (d) but is the **weakest** lever and goes n.s.
  (b=0.14, p=.10) once initial performance is in the model → moderators are **not independent**;
  don't multiply separate weights (double-counting).
- **MIXED — far transfer:** near-zero general transfer (St. Hilaire & Carpenter 2023:
  specific g=0.54, general g=0.04 n.s.). _DA upgrade of citation from the 2022 dissertation._

**DA flags:** "Ferreira et al. (2025)" is misattributed → **Goncalves et al. (2025)**,
_Educ Psych Rev_, doi:10.1007/s10648-025-10076-6 (g=0.14 vs elaborative controls; g=0.50 with
feedback). The 75% threshold and the Rowland attribution softened.

**Concrete parameter:** transfer credit = moderator-conditioned, **within-topic only**;
default ZERO performance-transfer credit when success+congruency+feedback are absent (only
memory-retention credit). Mandatory feedback step after every rung (engine invariant).

---

### T2 — Interleaving as discrimination trainer + ADJACENCY vs SPACING (SPOV3)

**Overall confidence: HIGH.** All 7 key citations verified; effect sizes current; not
cherry-picked (foregrounds its own disconfirmers).

- **CONFIRMED — interleaving g=0.42 overall** (Brunmair & Richter 2019, _Psych Bulletin_
  145(11):1029-1052): paintings 0.67, **math 0.34** (closest CFA analog), words **−0.39**
  (reversed). Stronger when between-category similarity HIGH, within-category LOW.
- **CONFIRMED — discrimination benefit grows with delay** (Rohrer, Dedrick & Stershic 2015,
  classroom RCT n=126: **d=0.42 immediate → 0.79 at 30 days**). Mechanism = "choose a strategy,
  not merely execute" (Rohrer 2020, preregistered RCT d=0.83) — the CFA MCQ task.
- **CONFIRMED — juxtaposition, not spacing, is the active ingredient** (Kang & Pashler 2012:
  simultaneous different-artist display = interleaving > massed; temporal spacing of blocked
  items = massed). _Per-condition d values (0.78/0.56) are investigator-computed — provisional;
  qualitative pattern confirmed._
- **CONFIRMED — interrupting juxtaposition harms even with total spacing constant** (Birnbaum
  et al. 2013, _Mem & Cog_ 41:392-402): interleaving beats blocking only when **contiguous**
  (d=0.68); advantage vanishes when spaced apart (subadditive). Exp3: with juxtaposition held
  constant, MORE within-category spacing still helped (**d=0.75**) → adjacency and spacing are
  **separable** benefits.
- **CONFIRMED — transfer to novel items g up to 0.66** (Firth, Rivers & Boyle 2021), biggest
  when differences subtle.
- **CONFIRMED (devil's advocate) — blocking WINS for dissimilar items** (Carvalho & Goldstone
  2014, _Mem & Cog_ 42:481-495, generalization **d=0.76** favoring blocked). The ordering rule
  sign FLIPS at low similarity.

**Concrete parameters:** (a) **signed similarity gate** — force adjacency only above a
confusability threshold; below it prefer spacing/blocking (default SRS). (b) preserve
**contiguity (lag 0–1)**: no fillers between paired confusables. (c) **space repeats of the
SAME card** but adjacently juxtapose DIFFERENT confusable cards. (d) evaluate on **delayed**
held-out MCQ. (e) for the tightest confusables, a **side-by-side compare card** (Kang & Pashler
simultaneous display) — reserved for a small set to avoid split-attention overload.

**Boundary/DA:** honest anchor = **g=0.34 (math)**, not g=0.66. Learner _preference_ is invalid
signal (metacognitive illusion: 78–83% rate massing better even when interleaving wins). CLT:
interleaving can become undesirable difficulty for high-element-interactivity material (explains
the small math effect) — a real risk for CFA formula clusters.

---

### T3 — Worked→faded→solve, expertise reversal & adaptive fading (SPOV2)

**Overall confidence: HIGH**, with two verdict softenings.

- **CONFIRMED — worked-example effect g=0.48** (Barbieri, Miller-Cotto, Clerjuste & Chawla
  2023, _Educ Psych Rev_ v35 Art 35, p=.01, RVE, 55 studies/181 ES). _Locator "35:11" wrong;
  DOI 10.1007/s10648-023-09745-1._
- **CONFIRMED — expertise reversal, asymmetric** (Tetzlaff, Simonsmeier, Peters & Brod 2025,
  _Learning & Instruction_ 102142; 60 studies, 5924 participants): novices **d=+0.505**, experts
  **d=−0.428**; weaker in humanities/language (→ Ethics), moderated by prior-knowledge measure.
- **REFINED — gradual fading > example-problem pairs on NEAR transfer** (Renkl et al. 2002);
  needs **self-explanation for far transfer** (Atkinson et al. 2003) — but self-explanation is
  contested (see below).
- **REFINED (down from CONFIRMED) — adaptive > fixed fading:** rests on ONE clean lab result
  (Reisslein/Reisslein/Seeling 2006) + a classroom **near-null** (Salden 2009: Fs<1 regular,
  **F=2.38, p=.11** delayed; authors: "fixed fading was already near optimal"). The
  performance-driven **signal** is well-defined; the adaptive>fixed _comparison_ is not reliably
  established outside individualized settings. _DA: verdict overstated; use REFINED._
- **MIXED — fade order:** Renkl 2002 backward>forward vs Moreno, Reisslein & Delgoda 2006
  forward/adaptive>backward. Sidestep with **mastery-driven order** (fade highest-stability step
  first).
- **MIXED — self-explanation:** Barbieri 2023 found it a **negative** moderator; Miller-Cotto &
  Auxter 2019 found plain problem-solving beat fading+self-explanation ecologically → keep as an
  **ablatable flag**, not default.
- **REFINED — CLT effects replicate outside lab:** Sweller (2023) catalogs conceptual
  replication failures; classroom < lab. Treat the ladder as a falsifiable ON/OFF/vanilla feature.

**DA flags:** Reisslein author-string conflates two real papers (JEE 2006 = J. Reisslein,
M. Reisslein & Seeling, NO Atkinson; the L&I 2006 paper is the one with Atkinson). Element-
interactivity gate ("facts stay on plain FSRS") is a **theoretical extrapolation** — test it.

**Concrete parameters:** fade level = f(FSRS stability/retrievability) with **two-sided band +
hysteresis** (fade-UP threshold > fade-DOWN); middle rung = **cloze/completion**; enable ladder
only for **high-element-interactivity (formula) clusters**; keep scaffold longer for Ethics.

---

### T4 — Successive relearning & the mastery criterion (the gate inside SPOV2)

**Overall confidence: MEDIUM.** ⚠️ **The submitted findings JSON was a placeholder
("test"/"t").** The DA supplied the real, verified evidence the resubmission must use:

- **CONFIRMED (recall/durability) — successive relearning to a mastery criterion** produces
  large durable gains: Rawson & Dunlosky (2011, _JEP:General_ 140:283-302) prescribe **3 initial
  correct recalls + 3 spaced relearning sessions**; Janes, Dunlosky, Rawson & Jasnow (2020,
  _ACP_ 34:1118-1132) report **≥10% exam boost, ds 0.54–1.10** in a real course.
- **REFUTED as stated — criterion LEVEL is the load-bearing knob:** the **relearning-override
  effect** (Vaughn, Dunlosky & Rawson 2016, _Mem & Cog_ 44:897-909): benefits of a high initial
  criterion do NOT persist once spaced relearning occurs (~20% vs ~69% recall). Gate on
  **NUMBER OF SPACED SESSIONS PASSED**, not within-session criterion. A stiff initial criterion
  is largely wasted (overlearning).
- **CONFIRMED-CONDITIONAL — recall gate → new-MCQ competence:** partially supported (Rawson et
  al. 2013 ~10pp on application items) but **conditional** (Pan & Rickard 2018; Tran, Rohrer &
  Pashler 2014 "lack of transfer to deductive inferences"). Must be **measured on held-out
  application items, never assumed.**

**Concrete parameters:** gate = **≥1 correct recall in each of ~3 spaced sessions** with FSRS
stability crossing a threshold implying a real inter-session gap — NOT a same-session repeat
count. Fade-content format is **low-leverage** (2024 JARMAC: example vs definition SR made no
difference on application questions; the schedule did the work) → don't over-engineer scaffold
variety.

⚠️ **Action: T4 must be resubmitted with real findings; the above verified evidence is the spec.**

---

### T5 — Readiness: calibrated pass-probability under SPARSE data (honesty / R10)

**Overall confidence: HIGH.** One of the cleanest threads; 12 load-bearing citations verified,
several verbatim.

- **REFUTED — isotonic for sparse data:** Niculescu-Mizil & Caruana (ICML 2005, verified
  verbatim): "Platt Scaling performs better than Isotonic Regression for small to medium
  calibration (**less than 1000 cases**)." → default **Platt/temperature**; switch to isotonic
  only above ~1000 labeled pass/fail outcomes (a single user will never reach this).
- **REFINED — logistic P(pass):** apply shrinkage (global factor ≥0.9); cap predictors by
  Events-Per-Parameter. Riley et al. (2019, _Stat Med_): EPP requirement is **4.8–23**, NOT the
  "10" myth. Use few aggregate features + CFA-blueprint topic weights as fixed priors.
- **CONFIRMED — honest RANGE:** Beta-Binomial posterior with **Jeffreys prior Beta(0.5,0.5)**,
  report **Wilson/Jeffreys** interval (Brown, Cai & DasGupta 2001, recommended for n≤40). NOT Wald.
- **MIXED — conformal:** split-conformal coverage guaranteed only **in expectation**; small
  calibration sets under-cover. **Small Sample Beta Correction** (Zwart 2025, arXiv:2509.15349)
  inflates α via exact Beta(n+1−l, l). For cold-start it will often say "abstain" — the honest
  behavior.
- **CONFIRMED — ABSTAIN when thin:** selective prediction / risk-coverage (El-Yaniv & Wiener
  2010; Chow 1970). Emit P(pass) only if (a) reviews ≥ N_min AND (b) topic coverage ≥ C_min AND
  (c) interval half-width ≤ W_max; else "READINESS: insufficient data."
- **REFINED — mock predicts pass, only moderately:** MIR r≈0.71–0.76 (Castro 2025); CPA R≈0.47;
  degrades at extremes → wide bands mandatory.
- **REFUTED — deep knowledge tracing:** DKT's AUC≈0.85 edge is a **data-leakage artifact**
  (Wilson et al. 2016 arXiv:1604.02336; Khajah et al. 2016 arXiv:1604.02416) → use **IRT/PFA/LKT**.
- **NEW — IRT classification accuracy/consistency** (Rudner 2005; R `cacIRT`): a second honest
  number = "confidence of the pass/fail CALL" distinct from P(pass); item-selection rule = target
  test information near the MPS band; abstain when SEE(θ) too large near the cut.

**DA addition:** add **Venn-Abers predictors** (Vovk & Petej, arXiv:1211.0025) / **Beta
calibration** as the modern default (distribution-free calibration guarantee under exchangeability)
— arguably better than Platt. Soften the "1000" gate to a **200–1000, learner-dependent** range.

---

### T6 — FSRS internals as the fade/mastery signal (implementation reality)

**Overall confidence: HIGH.** Strongest thread; primary-source-code claims literally correct
in this repo.

- **CONFIRMED — DSR meanings distinct:** Stability = days for R to fall 100%→90%; Difficulty ∈
  [1,10]; Retrievability = P(recall) ∈ [0,1], computed dynamically R(t)=(1+(19/81)·t/S)^decay.
  DECAY is **user-fit in FSRS-6** (was −0.5) → read the collection's fitted decay, don't hardcode.
- **REFINED — R (not raw S) is the fade proxy, but must be evaluated at a fixed horizon:**
  instantaneous R is a **due-ness** signal (decays with age). Use **predicted R at exam date**
  (or S expressed in target-retention days) for a **competence** signal.
- **REFUTED — an S/R value that means "mastered":** no FSRS artifact defines mastery. 0.9 is a
  **scheduling target** (keeps R oscillating 90–100%), not competence. Anki default desired
  retention 0.9; code clamps optimal retention to [0.7, 0.95] (rslib/src/scheduler/fsrs/
  retention.rs:39, verified). A high R can coexist with S from a single review → R alone overstates
  durability.
- **CONFIRMED — FSRS ≫ SM-2:** ~99.6% lower log loss (srs-benchmark, ~350M reviews, 9,999 users);
  academic lineage KDD 2022 (12.6% cost ↓) + IEEE TKDE 2023 (64% error / 17% cost ↓). BUT:
  superiority is on **recall prediction**, not exam performance; **AUC only ≈0.63–0.70** → R is
  well-calibrated in aggregate but a **weak per-card classifier**.

**DA note (COI):** the benchmark maintainer is a co-maintainer with the FSRS author; SuperMemo
publicly disputes the ML metrics. No RCT shows FSRS improving downstream EXAM outcomes — which is
exactly why the memory→performance bridge needs its own held-out MCQ test.

**Concrete parameters:** store S, D; compute R with the collection's fitted decay; **gate on
predicted R at exam horizon**; combine R with S (durability) for any "mastery" label; **aggregate
over many cards** for readiness (never hard-gate on a single card's R). Exam-prep desired
retention can push to ~0.95 (ceiling), not above ~0.97 (degenerates to massed).

---

### T7 — Desirable difficulties & retrieval-under-load GUARDRAILS

**Overall confidence: HIGH**, with one moderate cherry-picking flag and two citation fixes.

- **CONFIRMED — under high load, RP loses its edge; load mediates** (Redifer et al. 2025,
  _Instructional Science_, n=213: RP did NOT beat rereading on advanced material; load a
  significant negative mediator, B=**−0.547** [JSON said −0.543]).
- **MIXED / PROVISIONAL (DA down from CONFIRMED) — interleaving harms novices:** rests mainly
  on Danzglock et al. (2025) — an **un-peer-reviewed preprint** whose PK×schedule interaction
  **vanished at 8 weeks**. Brunmair & Richter (2019) found interleaving **stronger** for complex
  material and no robust PK reversal. → treat the **minimum-fluency gate as an A/B-tested
  default**, not settled.
- **MIXED — reverse testing effect** under high element interactivity (Hanham, Leahy & Sweller
  2017, 6 experiments; van Gog & Sweller 2015) vs **rebuttal** (Karpicke & Aue 2015: "alive and
  well with complex materials"). Honestly unresolved → complexity is a **knob**, not a hard ban;
  prevent MASSED immediate solve on complex clusters.
- **CONFIRMED — KLI taxonomy** (Koedinger, Corbett & Perfetti 2012): memory ≠ induction ≠
  understanding → type cards and route: fluency→plain SRS; induction→SPOV3 adjacency;
  understanding→SPOV2 ladder.
- **CONFIRMED — two-sided fade band** (Kalyuga 2007; Bjork & Bjork 2020): keep scaffold below a
  floor, remove above a ceiling.
- **REFINED — comprehension-first gate:** solve rung blocked until ≥1 successful encoding
  (Hinze et al. 2013; Endres & Renkl 2015).

**DA citation fixes:** "Rummer & Schweppe (2015), Frontiers 6:1054" → **Endres & Renkl (2015)**;
"Zheng 2024" → **Zheng, Sun & Liu (2023)**, _npj Sci Learn_ 8:8 (n=30, caveat). The "40–50%
error → drop a rung" cutoff is **invented** — a tunable default. **Missed meta-analyses:**
Pan & Rickard 2018 and Brunmair & Richter 2019 should be cited on both sides.

**Concrete parameters:** (1) comprehension gate (≥1 successful encoding) before solve/adjacency;
(2) minimum-fluency gate (FSRS-stability floor) before interleaving confusables — **A/B tested,
time-decaying** (relax as intervals lengthen); (3) tolerate productive failure on fluency cards,
guard only high-load failure on complex clusters.

---

### T8 — CFA-specific practitioner evidence: topic weights, MPS, confusables, tactics

**Overall confidence: HIGH.** Primary spine (topic weights, pass rates) confirmed verbatim on
cfainstitute.org; two stale numbers + one missed disconfirmer.

- **CONFIRMED — 2025/26 topic weight ranges:** Ethics 15–20, Quant 6–9, Econ 6–9, FSA 11–14,
  Corp Issuers 6–9, Equity 11–14, Fixed Income 11–14, Derivatives 5–8, Alt Inv 7–10, PM 8–12.
  Ethics+FSA+Equity+FI ≈ 48–66% of weight.
- **REFINED — MPS undisclosed; scaled score indexed to 1600 (from Feb 2025).** 300Hours estimate
  **56–74%**; DA correction: **~62% (13-yr avg)** [not "65%/14-yr"]; per-window ~**68–69%**
  [not "67–69%"]. Recent pass rates **43–45%**; first-timers 49–52% vs deferrers 28–30%.
- **CONFIRMED (as hypotheses) — confusable clusters:** duration trio (Macaulay/modified/effective
  - Z-spread/OAS, callable/putable); FIFO/LIFO/WAC + LIFO-reserve direction; forwards/futures/
    swaps (firm-commitment, linear) vs options (contingent-claim, non-linear); ethics standards
    I–VII (primary-vs-secondary, duty-vs-outcome, disclosure-doesn't-cure); equity model selection
    (constant-growth DDM vs two-stage vs multiples). Expert-curated — validate via the app's own
    error-rate ablation.
- **CONFIRMED (direction) — application-MCQ practice is the bottleneck:** passers spend 70–80% of
  time on active practice; mocks are the best community-cited predictor. Observational,
  survivorship-biased.

**DA fixes/additions:** the **70% mock threshold is drifting** — 300Hours revised its L1 target
DOWN to **68%** (Nov 2025). **Missed disconfirmer:** Nathan Ronen, CFA argues high mock scores
do NOT guarantee a pass and mocks "reward memorization" while the exam "demands application" — a
two-edged find that _affirms the memory→performance thesis_.

**Concrete parameters:** ship topic-weight config (min,max,midpoint), versioned by exam year;
budget/weight by midpoint, carry range as uncertainty. **No topic-level cutoffs** (only overall
MPS) → optimize a single weighted-overall readiness target (but keep per-cluster learning gates).
Ethics has a **dual role** (largest weight + tie-break) → over-weight when readiness is near the
boundary. Readiness output mirrors the **distance-to-1600** scaled metric with a band; readiness
gate = mock proxy as a **configurable band (~68–75%)**, not a hardcoded 70%.

---

### T9 — AI item generation + retrieval-for-grounding validity (beats BM25/vector)

**Overall confidence: HIGH.** 11 citations verified; two attribution defects (no conclusion change).

- **MIXED — LLM-generated + validated MCQs usable/comparable:** GPT-4 items indistinguishable
  from human on relevance/clarity/distractor quality (Riehm et al. 2026, _PLOS One_, network
  meta-analysis, 15 studies) — but **ALL GRADE VERY LOW certainty**; weaker models significantly
  worse. RCT n=258 (BMC Med Educ 2026): comparable acceptability (|d|≤0.31), **5.6× efficiency**,
  student items slightly higher discrimination. AI items skew **easier / less discriminating**
  (Ahmed 2025: facility 0.70 vs 0.64). Keep-rates ~58–70% after review.
- **REFINED — distractor generation:** use **misconception-grounded** (solve-first, simulate
  student errors) not surface similarity; small fine-tuned models can beat GPT-4o (DiVERT, EMNLP
  2024, on 1,434 real math MCQs). Retire distractors chosen by **<5%** of examinees.
- **CONFIRMED — hybrid retrieval beats either alone:** BM25 is a robust zero-shot baseline that
  dense often UNDERPERFORMS out-of-domain (BEIR, Thakur et al. NeurIPS 2021); **RRF** beats any
  single ranker (Cormack et al. SIGIR 2009, p<.003); cross-encoder rerank adds **>4 nDCG points**
  (Rosa et al. 2022) at ~10–100× latency cost.
- **REFINED — contamination/leakage:** generating NOVEL items is itself a mitigation; wall off
  the grounding corpus and gold eval set from any generator prompt/fine-tune; log provenance;
  n-gram check stems aren't verbatim.
- **NEW — validation protocol:** gold-set ≥100–200 human-vetted items; pre-registered acceptance
  cutoff (2 SMEs: accuracy AND single-best-answer AND ≥3 functioning distractors); held-out
  administration for facility + point-biserial; ON/OFF/vanilla ablation. **Automation bias can
  BACKFIRE** (Frontiers 2026: AI-assisted workflow INCREASED item-writing flaws) → review must be
  adversarial, not rubber-stamp.

**DA fixes:** arXiv 2603.15547 author "DeepSeek-V3.2" is a **model name**, not an author (real:
Zengaffinen et al. 2026); "Wu et al." preprint is now published as **Pham et al., Medical Teacher
2025;47(12):1961-1974** — cite that, and treat the per-item IWF numbers as unverified.

**Concrete parameters:** two-stage generate→validate (drafter + independent critic) → SME gold
sign-off; acceptance cutoff at observed keep-rate; **never let ungraded generated items feed the
readiness estimate**; track per-item discrimination and auto-retire non-discriminating items.
Fair "beats a simpler method" claim = beat **BOTH tuned BM25 AND tuned dense** at the same cutoff
on held-out qrels; win via RRF+rerank; report precision@k AND latency. Seed CFA distractors from
the **known confusable set** (ties to SPOV3).

---

## 3. Cross-Cutting Insights & New Techniques

1. **Feedback is a global invariant, not a feature.** Retrieval loses to elaboration without
   feedback (Goncalves 2025, g=0.14 vs g=0.50) and feedback nearly doubles the testing effect
   (Rowland 2014). ADD a mandatory feedback step after every rung across SPOV2 — currently
   omitted from the plan.

2. **Adjacency and spacing are separable and can cancel.** Birnbaum Exp2 (subadditivity) means
   naively combining SPOV3 back-to-back placement with FSRS wide spacing can null the
   discrimination gain. Rule: **juxtapose DIFFERENT confusables; keep SPACING repeats of the SAME
   card.**

3. **Two honest numbers, not one.** P(pass) (Beta-Binomial band) AND classification-accuracy/
   consistency of the pass/fail CALL (Rudner/`cacIRT`). Surfacing both, plus abstention, is the
   strongest single move for the honesty-uncertainty (20%) weight.

4. **The simpler model IS the defensible AI choice.** DKT's edge is a leakage artifact; IRT/PFA/
   LKT wins on interpretability, sparse data, and the "beat a simpler baseline" framing. Do NOT
   build deep knowledge tracing.

5. **Within-topic-only cluster credit is a free ablation.** St. Hilaire general g=0.04 predicts
   cross-topic leakage should not help (and may hurt calibration) — a concrete, evidence-backed
   ablation arm for SPOV1.

6. **Signed similarity gate is the crux of SPOV3 (and where AI must earn its keep).** Wrong-side
   application (adjacency on dissimilar pairs) is a predicted **performance loss** (Carvalho &
   Goldstone d=0.76), so **gate correctness is load-bearing** (owner decision: verified via unit
   tests, and the ablation is kept to 3 arms — full/feature-off/vanilla — rather than adding a 4th
   mis-targeted arm), and the confusability edges must beat BM25/vector.

7. **Side-by-side compare card (NEW technique to ADD, scoped).** Kang & Pashler's simultaneous
   display equals interleaving for the tightest confusables (duration trio, FIFO/LIFO) — a
   stronger UI move than sequencing, reserved for a small high-value set (split-attention caveat).

8. **DROP / de-prioritize:** aggressive self-explanation defaults (negative moderator);
   deep-neural knowledge tracing; isotonic calibration; any hardcoded fade order; over-engineered
   scaffold-content variety (low leverage per T4).

---

## 4. CONCRETE PLAN CHANGES

> Each tagged **[Phase]** and **[ADD|CHANGE|DROP|DEFER]**. Prioritized by grader weight:
> honesty/calibration (20%), ablation (15%), AI-beats-baseline (15%), held-out testing (12%).

1. **[Phase 1][CHANGE] Fix the Readiness give-up thresholds (R10 AUTO-FAIL risk).** Replace the
   shipped `≥15 reviews & ≥1% coverage` with the abstention gate: emit P(pass) **only if**
   `graded_reviews ≥ 300` AND `topic_coverage ≥ 70%` (≥7/10 areas) AND `held_out_probe_items ≥ 50`
   AND `interval_half_width ≤ W_max`; else return "READINESS: insufficient data." _Evidence:_
   selective prediction / Chow rule (El-Yaniv & Wiener 2010); mock predictive validity only
   moderate (Castro 2025 r≈0.71–0.76). This is the single highest-leverage honesty fix.

2. **[Phase 3][CHANGE] Never emit a point pass-probability — emit a band.** Point estimate =
   Beta-Binomial posterior mean with **Jeffreys prior Beta(0.5,0.5)** = (x+0.5)/(n+1); band =
   **Wilson/Jeffreys 90%** interval; widen when propagating through the MPS map. Output as
   **distance-to-1600 scaled-score band**. _Evidence:_ Brown, Cai & DasGupta (2001).

3. **[Phase 3][CHANGE] Calibration method = Platt/temperature (or Venn-Abers), NOT isotonic.**
   Gate: `calibration_n < ~1000 → Platt`; prefer **Venn-Abers / Beta calibration** for a
   distribution-free guarantee under exchangeability. _Evidence:_ Niculescu-Mizil & Caruana
   (2005, "<1000 cases"); Vovk & Petej (Venn-Abers). Soften the cutoff to a 200–1000
   learner-dependent range.

4. **[Phase 3][CHANGE] Readiness backbone = IRT / PFA / LKT (logistic), NOT deep knowledge
   tracing.** Cap free predictors by EPP (max_predictors ≈ floor(min(events, non-events)/10),
   EPP range 4.8–23); use CFA-blueprint topic weights as **fixed priors**; global shrinkage ≥0.9.
   _Evidence:_ Wilson 2016 & Khajah 2016 (DKT edge = leakage artifact); Riley et al. (2019).
   Directly satisfies "beat a simpler baseline" by making the simple model the defensible choice.

5. **[Phase 3][ADD] Second honest number: IRT Classification Accuracy/Consistency at the MPS**
   (Rudner method, R `cacIRT`) surfaced as "confidence of this pass/fail call," distinct from
   P(pass); abstain when SEE(θ) too large near the cut; item-selection targets test information
   near the MPS band. _Evidence:_ Rudner (2005); Wyse & Babcock (2016 — don't always maximize
   info exactly at the cut).

6. **[Phase 3][ADD] Conformal backstop with Small Sample Beta Correction** for validated coverage
   claims once a held-out outcome bank exists; expect frequent "abstain (interval too wide)" at
   cold start (the honest behavior). _Evidence:_ Angelopoulos & Bates (2023); Zwart (2025,
   arXiv:2509.15349).

7. **[Phase 3][CHANGE] Held-out testing must be DELAYED (≥1 week) application MCQs, not immediate
   accuracy.** _Evidence:_ discrimination/transfer benefits are delay-sensitive (Rohrer 2015
   d=0.42→0.79 at 30 days). This is the correct measurement for the memory→performance bridge.

8. **[Phase 3][ADD] SPOV1 ablation arm — within-topic vs cross-topic cluster credit.** Restrict
   cluster performance credit to **within-topic near transfer** by default; test cross-cluster
   leakage as the ablation. _Evidence:_ St. Hilaire & Carpenter (2023) general g=0.04; Pan &
   Rickard (2018) PEESE intercept ≈0. Default ZERO performance-transfer credit when
   success+congruency+feedback absent (only memory-retention credit).

9. **[Phase 2][ADD] Mandatory feedback step after EVERY rung (engine invariant) + a
   feedback-present readiness moderator flag.** _Evidence:_ Goncalves et al. (2025) g=0.14 without
   / g=0.50 with feedback; Rowland (2014) feedback ≈ doubles the effect. Currently omitted.

10. **[Phase 2][CHANGE] Fade signal = predicted retrievability at the EXAM horizon**, computed
    with the collection's **fitted decay** (FSRS-6 makes decay per-user), NOT raw stability nor
    instantaneous R. _Evidence:_ T6 — instantaneous R is a due-ness signal; S is only interpretable
    relative to a retention target.

11. **[Phase 2][CHANGE] Fade band is two-sided with hysteresis.** Below a lower R-bound keep
    worked/cloze; above an upper bound serve solve-MCQ; set **fade-UP threshold > fade-DOWN
    threshold**. _Evidence:_ expertise reversal is asymmetric — novices d=+0.505 > experts
    −0.428 (Tetzlaff 2025); Kalyuga (2007).

12. **[Phase 2][CHANGE] Promotion gate = spaced-session count, not within-session criterion.**
    Require **~3 spaced successful relearning passes** (≥1 correct recall per session with FSRS
    stability implying a real inter-session gap) AND correct last-attempt, to unlock rung k+1.
    Do NOT gate on a high same-session repeat count. _Evidence:_ relearning-override effect
    (Vaughn et al. 2016); Rawson & Dunlosky (2011). Beats a fixed "fade every N" counter.

13. **[Phase 2][ADD] Comprehension-first + minimum-fluency preconditions (T7 guardrails).**
    Solve rung and confusable-adjacency blocked until (a) ≥1 successful encoding of the KC AND
    (b) both members above an FSRS-stability floor. Make the fluency floor **A/B-tested and
    time-decaying**. _Evidence:_ Redifer (2025) load mediation; Hinze (2013); Danzglock (2025,
    provisional — hence A/B).

14. **[Phase 2][CHANGE] Format congruency = continuous multiplier, not a hard gate**
    (matched=1.0, mismatched≈0.75); do NOT block promotion because an early rung was cloze not
    MCQ; keep the terminal rung MCQ (exam-congruent). _Evidence:_ Yang et al. (2021) mismatched
    g=0.399 still significant; Pan & Rickard b=0.35.

15. **[Phase 2][CHANGE] Fade order = mastery-driven (fade highest-stability step first);** do NOT
    hardcode backward. Expose backward vs forward/adaptive as an ablation dimension. _Evidence:_
    contested (Renkl 2002 vs Moreno et al. 2006).

16. **[Phase 2][CHANGE/DROP] Self-explanation prompts = ablatable OFF-by-default flag, not baked
    in.** _Evidence:_ Barbieri (2023) negative moderator; Miller-Cotto & Auxter (2019) ecological
    reversal.

17. **[Phase 2][CHANGE] Enable the fade ladder only for high-element-interactivity (formula)
    clusters; low-load atomic facts stay on plain FSRS.** Tag clusters by element interactivity.
    _Evidence:_ Sweller CLT boundary (treat as a testable hypothesis, not settled).

18. **[Phase 2 (gate) + Phase 1 (contiguity only)] SPOV3 adjacency behind a SIGNED similarity gate + contiguity constraint.**
    Force back-to-back placement ONLY above a confusability threshold; for dissimilar pairs prefer
    spacing/blocking; keep paired confusables at **lag 0–1** (no fillers); **space repeats of the
    SAME card**. _Owner decision:_ the **contiguity constraint (C3 merged-queue + C10
    sibling-adjacency) stays Phase 1** (label-free); the **confusability labeling/gate is Phase 2**
    (manual-label burden, or needs the computed behavioral signal that must beat BM25/vector, R21).
    _Evidence:_ Carvalho & Goldstone (2014) blocking wins d=0.76 for low similarity;
    Birnbaum (2013) contiguity + separable spacing d=0.75.

19. **[Phase 1][ADD — SUPERSEDED by owner decision: ablation kept to 3 arms (full / feature-off /
    vanilla); this 4th arm is NOT used — signed-gate correctness is covered by unit tests instead]
    MIS-TARGETED-adjacency arm** (adjacency forced on
    dissimilar pairs) to quantify the predicted downside, plus ON/OFF/vanilla. _Evidence:_ the
    ordering payoff depends entirely on edge quality (Carvalho & Goldstone sign-flip).

20. **[Phase 2][ADD] Side-by-side compare card for the tightest confusables** (duration trio,
    FIFO/LIFO), scoped to a small high-value set. _Evidence:_ Kang & Pashler (2012) simultaneous
    display ≈ interleaving; split-attention caveat (Kalyuga/Sweller).

21. **[Phase 2][CHANGE] "AI-beats-baseline" retrieval evaluation must beat BOTH tuned BM25 AND
    tuned dense at the SAME cutoff on held-out qrels**, reporting precision@k AND latency. Pipeline:
    BM25 top-100 + dense top-100 → **RRF (k=60)** → **cross-encoder rerank** top-N. Dense-only is a
    strawman. _Evidence:_ BEIR (2021); Cormack RRF (2009, p<.003); Rosa (2022, >4 nDCG).

22. **[Phase 2][ADD] Misconception-grounded distractor generation seeded from the known CFA
    confusable set** (ties to SPOV3); retire distractors chosen by <5% of examinees; auto-flag
    non-functioning distractors for regeneration. _Evidence:_ DiVERT (EMNLP 2024) beats GPT-4o;
    <5% non-functional rule (PMC7372664).

23. **[Phase 2][ADD] Two-stage generate→validate AIG pipeline with adversarial human gold
    sign-off.** Drafter + independent critic model; pre-registered acceptance cutoff (2 SMEs:
    accuracy AND single-best-answer AND ≥3 functioning distractors); acceptance at observed
    keep-rate (~58–70%); a self-consistency solve-check pre-filter to catch the #1 defect
    (>1 correct answer). **Review must be adversarial** — automation bias increases flaws.
    _Evidence:_ Riehm (2026, GRADE VERY LOW); Frontiers (2026) automation bias; Kowal (2025)
    keep-rates.

24. **[Phase 2/3][ADD] Never let ungraded generated items feed the readiness estimate; track
    per-item discrimination (point-biserial) from live responses and auto-retire non-discriminating
    generated items.** _Evidence:_ AI items skew easier/less-discriminating (Ahmed 2025); items
    are "live" only after SME rubric + minimum-response psychometric thresholds.

25. **[Phase 3][CHANGE] Cap max certainty by the mock↔exam correlation and use a configurable
    mock-proxy band, not a hardcoded 70%.** Set `prior_mock_exam_corr ≈ 0.7`; readiness gate =
    configurable band **~68–75%** (300Hours revised L1 target to 68%); enforce a minimum interval
    width reflecting residual error. _Evidence:_ Castro (2025); 300Hours (2025 revision); Ronen
    (mocks reward recall, exam demands application — affirms the bridge).

26. **[Phase 1][CHANGE] Ship topic-weight config as (min, max, midpoint), versioned by exam
    year;** allocate budget/readiness weight by midpoint, propagate range as uncertainty. Optimize
    a **single weighted-overall** readiness target (no topic-level cutoffs), while keeping
    per-cluster learning gates. Over-weight **Ethics** when readiness is near the MPS boundary
    (dual role: largest weight + tie-break). _Evidence:_ CFA Institute Level I page (verbatim);
    Ethics Adjustment (300Hours/UWorld).

27. **[Phase 3][CHANGE — mobile story] Rebase the branch onto the 25.09.2 tag** that AnkiDroid's
    rsdroid 0.1.64 pins, so "same commit builds desktop + phone" holds; do not stay on the 26.05
    dev-line. _Evidence:_ Known Problem B (engine baseline mismatch). (Engineering constraint, not
    literature — flagged for completeness.)

28. **[Phase 1][ADD] Resubmit T4 with real findings** (the submitted JSON was a placeholder); the
    verified successive-relearning evidence in §T4 above is the spec. Gate = spaced-session count;
    fade-content format is low-leverage. _Evidence:_ Rawson & Dunlosky (2011); Vaughn (2016);
    Janes (2020).

29. **[All phases][ADD] Apply the citation-hygiene corrections** before any writeup/grading
    artifact: Goncalves (not Ferreira) 2025; Endres & Renkl (not Rummer & Schweppe) 2015; Zheng
    2023 (not 2024); Pham et al. Medical Teacher 2025 (not Wu preprint); Zengaffinen et al. 2026
    (author, not "DeepSeek-V3.2"); St. Hilaire & Carpenter 2023 (upgrade from dissertation); the
    ">75% initial success" is a heuristic, not a Rowland-stated threshold. _Evidence:_ devil's-
    advocate verification across T1, T3, T7, T9 (IRON RULE — a mis-cite is a failure).

---

## 5. References (verified key sources)

**Transfer / testing effect (T1, T4):**

- Pan, S. C. & Rickard, T. C. (2018). Transfer of test-enhanced learning: meta-analytic review.
  _Psychological Bulletin_ 144(7):710-756. doi:10.1037/bul0000151. (d=0.40; PEESE intercept ≈0.)
- Yang, C., Luo, L., Vadillo, M. A., Yu, R. & Shanks, D. R. (2021). Testing (quizzing) boosts
  classroom learning. _Psychological Bulletin_ 147(4):399-435. (g=0.499.)
- Rowland, C. A. (2014). The effect of testing versus restudy. _Psychological Bulletin_
  140(6):1432-1463. doi:10.1037/a0037559.
- Kornell, N., Klein, P. J. & Rawson, K. A. (2014). _JEP:LMC_. doi:10.1037/a0037850.
- St. Hilaire, K. J. & Carpenter, S. K. (2023). Prequestion effect. _Psychonomic Bulletin &
  Review_. doi:10.3758/s13423-023-02353-8. (specific g=0.54, general g=0.04.)
- Goncalves, A. de O., Muniz & Jaeger (2025). Retrieval practice vs elaborative encoding.
  _Educational Psychology Review_. doi:10.1007/s10648-025-10076-6. (g=0.14; g=0.50 w/ feedback.)
- Rawson, K. A. & Dunlosky, J. (2011). _JEP:General_ 140:283-302. doi:10.1037/a0023956.
- Vaughn, K. E., Dunlosky, J. & Rawson, K. A. (2016). _Memory & Cognition_ 44(6):897-909.
- Janes, Dunlosky, Rawson & Jasnow (2020). _Applied Cognitive Psychology_ 34(5):1118-1132.

**Interleaving / adjacency (T2, T7):**

- Brunmair, M. & Richter, T. (2019). Similarity matters. _Psychological Bulletin_
  145(11):1029-1052. doi:10.1037/bul0000209. (g=0.42; math 0.34; words −0.39.)
- Kang, S. H. K. & Pashler, H. (2012). _Applied Cognitive Psychology_ 26:97-103.
- Birnbaum, Kornell, E. L. Bjork & R. A. Bjork (2013). _Memory & Cognition_ 41(3):392-402.
- Firth, J., Rivers, I. & Boyle, J. (2021). _Review of Education_ 9(2):642-684. (transfer g≈0.66.)
- Carvalho, P. F. & Goldstone, R. L. (2014). _Memory & Cognition_ 42(3):481-495. (blocking d=0.76.)
- Rohrer, D., Dedrick, R. F. & Stershic, S. (2015). _J. Educational Psychology_ 107:900-908.
- Rohrer, Dedrick, Hartwig & Cheung (2020). _J. Educational Psychology_ 112(1):40-52. (d=0.83.)

**Worked examples / fading / CLT (T3, T7):**

- Tetzlaff, Simonsmeier, Peters & Brod (2025). Expertise reversal meta-analysis.
  _Learning and Instruction_ 98:102142. (d=+0.505 / −0.428.)
- Barbieri, Miller-Cotto, Clerjuste & Chawla (2023). _Educational Psychology Review_ 35, Art 35.
  doi:10.1007/s10648-023-09745-1. (g=0.48.)
- Renkl, Atkinson, Maier & Staley (2002). _J. Experimental Education_ 70(4).
- Atkinson, Renkl & Merrill (2003). _J. Educational Psychology_ 95(4):774-783.
- Reisslein, J., Reisslein, M. & Seeling, P. (2006). _J. Engineering Education_ 95(3).
- Moreno, Reisslein & Delgoda (2006). FIE 2006. doi:10.1109/fie.2006.322285.
- Miller-Cotto, D. & Auxter, A. E. (2019). _Educational Psychology_ 40(4).
- Kalyuga, S. (2007). _Educational Psychology Review_ 19(4):509-539.
- Sweller, J. (2023). _Educational Psychology Review_. doi:10.1007/s10648-023-09817-2.
- Koedinger, Corbett & Perfetti (2012). KLI framework. _Cognitive Science_ 36(5):757-798.
- Redifer, Myers, Bae, Naas & Scott (2025). _Instructional Science_. doi:10.1007/s11251-025-09758-z.
- Endres, T. & Renkl, A. (2015). _Frontiers in Psychology_ 6:1054.
- Hanham, Leahy & Sweller (2017). _Applied Cognitive Psychology_ 31(3):265-280.
- Karpicke, J. D. & Aue, W. R. (2015). _Educational Psychology Review_ 27(2):317-326.

**Calibration / readiness / knowledge tracing (T5, T6):**

- Niculescu-Mizil, A. & Caruana, R. (2005). Predicting good probabilities. ICML 2005.
- Brown, Cai & DasGupta (2001). Interval estimation for a binomial proportion.
  _Statistical Science_ 16(2):101-133.
- Riley, Snell, Ensor, Burke, Harrell, Moons & Collins (2019). _Statistics in Medicine_ 38(7).
- Angelopoulos, A. & Bates, S. (2023). Conformal Prediction: A Gentle Introduction.
  _Foundations & Trends in ML_ 16(4).
- Zwart, P. H. (2025). Small Sample Beta Correction. arXiv:2509.15349.
- Vovk, V. & Petej, I. Venn-Abers predictors. arXiv:1211.0025.
- Wilson, Karklin, Han & Ekanadham (2016). Bayesian IRT > DKT. arXiv:1604.02336 / EDM 2016.
- Khajah, Lindsey & Mozer (2016). How Deep is Knowledge Tracing? arXiv:1604.02416 / EDM 2016.
- Pavlik, Cen & Koedinger (2009). Performance Factors Analysis. AIED 2009.
- Rudner, L. (2005). Expected Classification Accuracy. _PARE_. doi:10.7275/56a5-6b14.
- El-Yaniv, R. & Wiener, Y. (2010). Selective classification. _JMLR_ 11.
- Ye, Su & Cao (2022). SSP-MMC. KDD '22:4381-4390. doi:10.1145/3534678.3539081.
- Su, Ye, Nie, Cao & Chen (2023). DHP. _IEEE TKDE_. doi:10.1109/TKDE.2023.3251721.
- open-spaced-repetition/srs-benchmark (Expertium & Ye). (~350M reviews; note COI.)

**CFA-specific (T8):**

- CFA Institute — CFA Program Level I exam page (topic weights, 2026).
- CFA Institute — Level I results press releases (Feb/May/Aug 2025).
- 300Hours — CFA Passing Score / Grading Process / mock-target revision (2025-2026).
- Castro, Campos-Pavón & Carazo-Casas (2025). Mock-vs-MIR concordance. _Educación Médica_.
  doi:10.6018/edumed.681901. (r≈0.71–0.76.)

**AI item generation / retrieval (T9):**

- Riehm, Nanji, Lakhani et al. (2026). LLM MCQ generation NMA. _PLOS One_ 21(1):e0340277.
  (GRADE VERY LOW.)
- Thakur, Reimers, Rücklé, Srivastava & Gurevych (2021). BEIR. NeurIPS 2021 D&B. arXiv:2104.08663.
- Cormack, Clarke & Büttcher (2009). Reciprocal Rank Fusion. SIGIR 2009.
- Rosa, Bonifacio, Jerónymo et al. (2022). In Defense of Cross-Encoders. arXiv:2212.06121.
- Feng, Fernandez, Scarlatos et al. (2024). DiVERT. EMNLP 2024.
- Frontiers in Computer Science (2026). AI-assisted MCQ automation bias. 1831250.
- Pham, Zerner, Lee et al. (2025). GPT-4 vs human MCQ creation. _Medical Teacher_
  47(12):1961-1974. doi:10.1080/0142159x.2025.2505122.
