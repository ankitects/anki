# Brainlift §2 — Source Verification Report

**Task:** verify and enrich every source in §2 of `~/learning-breakthrough/Brainlift.md`, answer the six specific questions, and gather response-latency literature. **Method:** five parallel research agents, each required to copy quotes only from documents actually fetched (publisher pages, DOI records, PubMed/Crossref/ERIC, author-hosted PDFs), with "COULD NOT VERIFY" mandated over plausible filling. Compiled 2026-07-31.

**Verification key** used throughout:
- ✅ **VERIFIED (full text)** — the actual document was fetched and read; quotes copied from it.
- ☑️ **VERIFIED (metadata)** — citation confirmed against authoritative records (Crossref/PubMed/publisher), but full text not accessible; quotes, if any, come from the registered abstract or a named secondary source.
- ⛔ **COULD NOT VERIFY** — stated explicitly wherever it applies. Nothing in this report is filled in plausibly.

---

## 1. Errors caught (read this first)

| # | The Brainlift currently says | What's actually true |
|---|------------------------------|----------------------|
| 1 | **L1: "Woźniak & Gorzelańczyk (1994), SM-2 origin paper — the six-grade scale"** | **Misattribution.** The 1994 Acta Neurobiologiae Experimentalis paper is real but is about a universal repetition-spacing formula; nothing in it describes SM-2 or the 0–5 grade scale. SM-2 and the scale come from Woźniak's 1990 master's thesis, self-published at super-memory.com. Cite both, correctly split (see §3, L1). |
| 2 | **S6 as support for "the Anki rating screen is foresight-biased"** | **Overreach as stated.** Koriat & Bjork (2005) demonstrated foresight bias only for study-time JOLs with the target present and **no prior retrieval attempt**. Their own 2006 follow-up shows retrieval/test experience is the *debiasing* manipulation. The defensible residual claim is the hindsight-flavored one: judgments made with the intact cue–answer pair in view are inflated (Dunlosky & Nelson 1997, as characterized in K&B 2005 p. 188). Details in §5.1 — this changes how §4c should be worded. |
| 3 | The famous sentence is "Strathern's 1997 restatement of Goodhart" | Half right. It **is** Strathern (1997), p. 308 — but it is **her own aphorism**, and she attributes the "Goodhart's law" label to **Hoskin (1996)**, not to Goodhart directly. She never quotes Goodhart's wording. Calling it a "restatement of Goodhart's sentence" overreaches; "the sentence everyone attributes to Goodhart is actually Strathern's" is correct. |
| 4 | (If used) Garrabrant's taxonomy post as "2016" | It is dated **December 30, 2017** (per Manheim & Garrabrant's own reference list), originally on lesserwrong.com. |
| 5 | (If used) "desirable difficulties" definition attributed to Bjork (1994) | The 1994 chapter coins the term only in passing (p. 193), with no definitional sentence. The canonical definition + list is **Bjork & Bjork (2011), p. 58**. |
| 6 | (If used) benchmark described as "~1.7B reviews" or "20k dataset" | srs-benchmark = **~727M reviews, 10k users**, Hugging Face dataset **anki-revlogs-10k**. (The Brainlift's own "~727M" figure in §4e is correct.) |
| 7 | (If used) "FSRS beats SM-2 by X on the benchmark" | SM-2 is **absent from the current srs-benchmark README results tables** (grep-verified). That comparison needs a pinned older commit or the separate fsrs-vs-sm15 / fsrs-vs-sm17 repos. |
| 8 | Two latency-source candidates | Benjamin, Bjork & Schwartz (1998) is titled "...misleading as a **metamnemonic index**" (not "mistaken for memory strength"); Mettler, Massey & Kellman (2016) is in **JEP: General** (not JARMAC). |

---

## 2. Citation-ready table (paste into §2)

### Systems lineage

| # | Full citation | Link | Status |
|---|---------------|------|--------|
| L1a | Woźniak, P. A. (1990). *Optimization of learning*. Master's thesis, University of Technology, Poznań. SM-2 algorithm description as published by SuperMemo World. | https://super-memory.com/english/ol/sm2.htm | ✅ (self-published web excerpt; see limits) |
| L1b | Woźniak, P. A., & Gorzelańczyk, E. J. (1994). Optimization of repetition spacing in the practice of learning. *Acta Neurobiologiae Experimentalis*, 54(1), 59–62. PMID 8023714. | https://pubmed.ncbi.nlm.nih.gov/8023714/ | ☑️ (abstract verified; NOT the SM-2 paper) |
| L2a | Ye, J., Su, J., & Cao, Y. (2022). A stochastic shortest path algorithm for optimizing spaced repetition scheduling. *Proceedings of the 28th ACM SIGKDD Conference on Knowledge Discovery and Data Mining*, 4381–4390. https://doi.org/10.1145/3534678.3539081 | https://dl.acm.org/doi/10.1145/3534678.3539081 | ✅ |
| L2b | Su, J., Ye, J., Nie, L., Cao, Y., & Chen, Y. (2023). Optimizing spaced repetition schedule by capturing the dynamics of memory. *IEEE Transactions on Knowledge and Data Engineering*, 35(10), 10085–10097. https://doi.org/10.1109/TKDE.2023.3251721 | https://ieeexplore.ieee.org/document/10059206/ | ✅ |
| L2c | open-spaced-repetition. *SRS Benchmark* (GitHub repository README, accessed 2026-07). | https://github.com/open-spaced-repetition/srs-benchmark | ✅ |
| L3 | Anki Manual: Deck Options (FSRS section); Studying (Answer Buttons); FSRS FAQ. Accessed 2026-07. | https://docs.ankiweb.net/deck-options.html · https://docs.ankiweb.net/studying.html · https://faqs.ankiweb.net/frequently-asked-questions-about-fsrs.html | ✅ |

### Learning science

| # | Full citation | Link | Status |
|---|---------------|------|--------|
| S1a | Bjork, R. A. (1994). Memory and metamemory considerations in the training of human beings. In J. Metcalfe & A. Shimamura (Eds.), *Metacognition: Knowing about knowing* (pp. 185–205). MIT Press. (No DOI — book chapter.) | Open author copy: https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/07/RBjork_1994a.pdf | ✅ |
| S1b | Bjork, E. L., & Bjork, R. A. (2011). Making things hard on yourself, but in a good way: Creating desirable difficulties to enhance learning. In M. A. Gernsbacher et al. (Eds.), *Psychology and the real world* (pp. 56–64). Worth Publishers. (2nd ed. 2014: pp. 59–68 — cite the edition used.) | Open author copy: https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/04/EBjork_RBjork_2011.pdf | ✅ |
| S2 | Barnett, S. M., & Ceci, S. J. (2002). When and where do we apply what we learn? A taxonomy for far transfer. *Psychological Bulletin*, 128(4), 612–637. https://doi.org/10.1037/0033-2909.128.4.612 | DOI (paywalled). Course-hosted copy: https://rapunselshair.pbworks.com/f/barnett_2002.pdf | ✅ |
| S3 | Roediger, H. L., III, & Karpicke, J. D. (2006). Test-enhanced learning: Taking memory tests improves long-term retention. *Psychological Science*, 17(3), 249–255. https://doi.org/10.1111/j.1467-9280.2006.01693.x | DOI (paywalled). Course-hosted copy: https://colinallen.dnsalias.org/Readings/2006_Roediger_Karpicke_PsychSci.pdf | ✅ |
| S4 | Sweller, J. (1988). Cognitive load during problem solving: Effects on learning. *Cognitive Science*, 12(2), 257–285. https://doi.org/10.1207/s15516709cog1202_4 | DOI (Wiley; back issues free to read). Open copy: https://mrbartonmaths.com/resourcesnew/8.%20Research/Explicit%20Instruction/Cognitive%20Load%20during%20problem%20solving.pdf | ✅ |
| S5 | Dunlosky, J., Rawson, K. A., Marsh, E. J., Nathan, M. J., & Willingham, D. T. (2013). Improving students' learning with effective learning techniques: Promising directions from cognitive and educational psychology. *Psychological Science in the Public Interest*, 14(1), 4–58. https://doi.org/10.1177/1529100612453266 | DOI. Open copy: https://www.whz.de/fileadmin/lehre/hochschuldidaktik/docs/dunloskiimprovingstudentlearning.pdf | ✅ |
| S6 | Koriat, A., & Bjork, R. A. (2005). Illusions of competence in monitoring one's knowledge during study. *Journal of Experimental Psychology: Learning, Memory, and Cognition*, 31(2), 187–194. https://doi.org/10.1037/0278-7393.31.2.187 | DOI (paywalled). Open author copy: https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/07/Koriat_RBjork_2005.pdf | ✅ |
| S6b | Koriat, A., & Bjork, R. A. (2006). Illusions of competence during study can be remedied by manipulations that enhance learners' sensitivity to retrieval conditions at test. *Memory & Cognition*, 34(5), 959–972. | Open author copy: https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/07/Koriat_Bjork_2006_MC.pdf | ✅ |
| S7 | Nelson, T. O., & Dunlosky, J. (1991). When people's judgments of learning (JOLs) are extremely accurate at predicting subsequent recall: The "delayed-JOL effect." *Psychological Science*, 2(4), 267–270. https://doi.org/10.1111/j.1467-9280.1991.tb00147.x | DOI (Sage, paywalled); JSTOR: https://www.jstor.org/stable/40062685. **No legitimate open copy found.** | ☑️ (Crossref/JSTOR; gammas verified via Dunlosky & Nelson 1992 full text) |
| S7b | Dunlosky, J., & Nelson, T. O. (1992). Importance of the kind of cue for judgments of learning (JOL) and the delayed-JOL effect. *Memory & Cognition*, 20(4), 374–380. https://doi.org/10.3758/BF03210921 | Open (Springer PDF): https://link.springer.com/content/pdf/10.3758/BF03210921.pdf | ✅ |

### Measurement framing

| # | Full citation | Link | Status |
|---|---------------|------|--------|
| M1 | Goodhart, C. A. E. (1975). Problems of monetary management: The U.K. experience. In *Papers in Monetary Economics* (Vol. I). Reserve Bank of Australia. Reprinted in A. S. Courakis (Ed.) (1981), *Inflation, Depression, and Economic Policy in the West* (p. 116), Barnes & Noble; and in Goodhart (1984), *Monetary Theory and Practice: The UK Experience* (ch. III, pp. 91–121), Macmillan. https://doi.org/10.1007/978-1-349-17295-5_4 (1984 reprint) | 1984 reprint: https://link.springer.com/chapter/10.1007/978-1-349-17295-5_4 (paywalled). No open copy of the 1975 original found. | ☑️ (venue verified via RBA + Springer; original text ⛔ — see §4.3) |
| M2 | Strathern, M. (1997). 'Improving ratings': Audit in the British University system. *European Review*, 5(3), 305–321. https://doi.org/10.1002/(SICI)1234-981X(199707)5:3<305::AID-EURO184>3.0.CO;2-4 | Publisher (Cambridge Core, paywalled). Open scan: https://gwern.net/doc/statistics/decision/1997-strathern.pdf | ✅ (full article read) |
| M3 | Manheim, D., & Garrabrant, S. (2018). Categorizing variants of Goodhart's Law. arXiv:1803.04585 [cs.AI]. https://doi.org/10.48550/arXiv.1803.04585 (v4, Feb 2019; **not peer-reviewed**) | https://arxiv.org/abs/1803.04585 (open access) | ✅ (full paper read) |

Note on M2's DOI: the SICI-style Wiley DOI above is what Cambridge Core itself lists (the journal was published by Wiley in 1997). Do **not** "correct" it to a 10.1017 DOI.

---

## 3. Systems lineage — quotes and limits

### L1 — SM-2 and the 0–5 grade scale

**The exact scale, verbatim** from https://super-memory.com/english/ol/sm2.htm (page header: "P.A.Wozniak, *Optimization of learning*, Master's Thesis, University of Technology in Poznan, 1990"):

> "5 - perfect response
> 4 - correct response after a hesitation
> 3 - correct response recalled with serious difficulty
> 2 - incorrect response; where the correct one seemed easy to recall
> 1 - incorrect response; the correct one remembered
> 0 - complete blackout."

And the pass/fail boundary, same page:

> "If the quality response was lower than 3 then start repetitions for the item from the beginning"

So grades 3/4/5 are the pass grades, and their definitions — "hesitation," "serious difficulty" — are verbal descriptions of retrieval effort/latency, exactly as the Brainlift's §4c argument needs. Anki's own current button definitions carry the same latency language (see L3 below).

**What L1 does NOT support / limits:**
- The scale is **not** in the 1994 peer-reviewed paper. The 1994 paper's abstract (verbatim, PubMed): "A universal formula for computing inter-repetition intervals in paired-associate learning has been determined for the knowledge retention level of 95%. It is claimed that the formula could be used in the practice of learning for a wide range of subjects, regardless individual learner's capacity." Cite it, if at all, as the first peer-reviewed spaced-repetition-spacing paper — not for SM-2. (Abstract-level verification only; the supermemo.guru full-text mirror returned 403.)
- The super-memory.com page is a **self-published, undated web excerpt** of a 1990 thesis; it may have been edited since 1990 and is not peer-reviewed. Safe form: "Woźniak (1990), master's thesis, as published at super-memory.com (accessed 2026-07)."

### L2 — FSRS / DSR model

The FSRS project's own wiki (https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm), verbatim:

> "The FSRS (Free Spaced Repetition Scheduler) algorithm originates in the DHP model from MaiMemo, which is a variant of the DSR (Difficulty, Stability, Retrievability) model used to predict memory states."

**Which paper to cite:** both — KDD 2022 (L2a) for the DHP memory model and scheduling algorithm; TKDE 2023 (L2b) for the extended memory-dynamics model. ("Jarrett Ye" is Junyao Ye's English pen name.)

**Limits:** Neither paper is titled "FSRS" — FSRS is a derived open-source algorithm. The D/S/R concepts trace back further, to Woźniak's "three component model of memory"; do not cite the 2022/2023 papers as inventing difficulty/stability/retrievability.

**Benchmark** (srs-benchmark README, fetched raw from `main`, 2026-07 — all verbatim):

> "The dataset for the SRS benchmark comes from 10 thousand users who use Anki, a flashcard app. In total, this dataset contains information about ~727 million reviews of flashcards. The full dataset is hosted on Hugging Face Datasets: [open-spaced-repetition/anki-revlogs-10k]"

> "We use three metrics in the SRS benchmark to evaluate how well these algorithms work: Log Loss, AUC, and a custom RMSE that we call RMSE (bins)."

> "Log Loss and RMSE (bins) measure calibration: how well predicted probabilities of recall match the real data. AUC measures discrimination: how well the algorithm can tell two (or more, generally speaking) classes apart."

Evaluation counts: "Total number of collections (each from one Anki user): 9,999." / "Total number of reviews for evaluation: 349,923,850." (without same-day reviews); 10,000 collections / 519,296,315 reviews with same-day reviews.

Current headline figures (without-same-day table, as of 2026-07): best FSRS variant "FSRS-7 recency" Log Loss 0.3414±0.0043; FSRS-7 0.3437±0.0043; FSRS-6 0.3460±0.0042; a neural model (RWKV-P) leads overall at 0.2773±0.0036 (AUC 0.8329±0.0017); older baselines far worse (HLR 0.4694±0.0073, Ebisu v2 0.4989±0.0078). These match the figures already in the teardown report (FSRS-7 recency: Log Loss 0.3414±0.0043, RMSE(bins) 0.0627±0.0010, AUC 0.7097±0.0022).

**Limits:** README changes frequently — pin an access date or commit. **SM-2 does not appear in the current results tables**; "FSRS vs SM-2" benchmark numbers need a pinned older commit or the fsrs-vs-sm15 / fsrs-vs-sm17 repos. FSRS is not the overall leader on its own benchmark (RWKV-P is) — don't state otherwise.

### L3 — Anki's own documentation

From https://docs.ankiweb.net/deck-options.html (FSRS section), verbatim:

> "The Free Spaced Repetition Scheduler (FSRS) is an alternative to Anki's legacy SuperMemo 2 (SM-2) algorithm. By more accurately determining how much information you are likely to forget, it can help you remember more material in the same amount of time."

> "FSRS can adapt to almost any habit, except for one: pressing "Hard" instead of "Again" when you forget the information. When you press "Hard", FSRS assumes you have recalled the information correctly (though with hesitation and a lot of mental effort). If you press "Hard" when you have failed to recall the information, all intervals will be unreasonably high."

From https://docs.ankiweb.net/studying.html ("Answer Buttons"), verbatim — note every pass button is defined by recall effort/time:

> "Again: Select this when your answer is incorrect or when you couldn't recall the answer."
> "Hard: Select this button when your answer is correct, but you had doubts about it or it took a long time to recall."
> "Good: Select this when your answer is correct, but it took some mental effort to recall it."
> "Easy: Select this if your answer is correct and it took no mental effort to recall it."

From https://faqs.ankiweb.net/frequently-asked-questions-about-fsrs.html, verbatim:

> "Q2: I only use Again and Good, will FSRS work fine? A2: Yes. In some cases, FSRS may even be more accurate if you only use Again and Good."

**Limits:** Living documents — quote with an access date. The FAQ does not define per-button effects on stability/difficulty; that lives in the manual/wiki.

---

## 4. Measurement framing — quotes and limits

### 4.1 Goodhart (1975)

**Original wording of the law:**

> "any observed statistical regularity will tend to collapse once pressure is placed upon it for control purposes."

⛔ **Verification status — read carefully:** this wording was verified in Manheim & Garrabrant (2018), p. 1 fn. 1 (read directly from the arXiv PDF), and independently on Wikipedia citing the Courakis 1981 reprint, p. 116. **No agent saw the 1975/1981/1984 primary text itself** — no open copy exists and the reprints are paywalled. The wording is consistent across every scholarly source checked, but if a page number is cited, point it at Courakis (1981), p. 116 (secondhand), not at an unverified RBA page number.

Venue history verified: the RBA's own bibliography confirms the *Papers in Monetary Economics* volume contains "revised versions of papers presented at a Conference in Monetary Economics held in Sydney in July 1975." The 1984 reprint (Springer, ch. III, pp. 91–121, DOI 10.1007/978-1-349-17295-5_4) is confirmed directly on SpringerLink; its visible abstract opens: "In 1971 the monetary authorities in the UK adopted a new approach to monetary management..."

**What it does NOT say:** The paper is about UK monetary policy (Competition and Credit Control, money-demand regularities). Nothing about education, KPIs, or metrics generally. **The sentence "When a measure becomes a target..." does not appear in it.** Also ⛔: the "coined half-jokingly" framing could not be verified verbatim anywhere; the standard scholarly history is Chrystal & Mizen (2003, in *Central Banking, Monetary Theory and Practice: Essays in Honour of Charles Goodhart*, Edward Elgar), which no agent could fetch — soften to "widely described as having been coined half-jokingly" or verify Chrystal & Mizen before leaning on it.

### 4.2 Strathern (1997) — fully verified (all 17 pages read)

The famous sentence **with its actual context**, p. 308 (from the published page scan at gwern.net):

> "When a measure becomes a target, it ceases to be a good measure. The more a 2.1 examination performance becomes an expectation, the poorer it becomes as a discriminator of individual performances. Hoskin describes this as 'Goodhart's law', after the latter's observation on instruments for monetary control which lead to other devices for monetary flexibility having to be invented."

Two more usable quotes, both p. 319:

> "Auditing is deliberately built on the conflation of measures with targets, and audit culture enhances the process."

> "Measurement and target rise together."

Provenance detail (p. 305, footnote): the article is adapted from the Founders' Memorial Lecture, Girton College, Cambridge, 11 March 1997.

**What it does NOT say:** Strathern **never quotes Goodhart** and does not cite Goodhart (1975) directly — her attribution runs through her Ref. 3, K. Hoskin (1996), "The 'awful idea of accountability'," in Munro & Mouritsen (Eds.), *Accountability: Power, Ethos and the Technologies of Managing*. So: the sentence is Strathern's own aphorism, framed via Hoskin's gloss on Goodhart. Its context is British university audit culture (RAE/TQA, degree classifications) — not economics, not metrics in general. The precise claim the Brainlift can make: *the popular sentence is Strathern's, not Goodhart's; even Strathern's label routes through Hoskin.*

### 4.3 Manheim & Garrabrant (2018) — fully verified (v4 read)

The four variants, verbatim from https://arxiv.org/pdf/1803.04585:

> **Regressional** (§1, p. 2): "Regressional Goodhart - When selecting for a proxy measure, you select not only for the true goal, but also for the difference between the proxy and the goal. This is also known as 'Tails come apart.'"

> **Extremal** (§2, pp. 2–3): "Extremal Goodhart - Worlds in which the proxy takes an extreme value may be very different from the ordinary worlds in which the relationship between the proxy and the goal was observed."

> **Causal** (§3, p. 4): "Causal Goodhart - When the causal path between the proxy and the goal is indirect, intervening can change the relationship between the measure and proxy. If a regulator intervenes to maximize a metric, the causal pathway can change such that the proxy no longer tracks the goal."

> **Adversarial** — no single boxed definition exists in the paper. The intro's summary (p. 2): "4) Adversarial, where an agent with different goals than the regulator causes the collapse." §4 splits it into subtypes with their own boxed definitions, e.g. (p. 8): "Adversarial Misalignment Goodhart - The agent applies selection pressure knowing the regulator will apply different selection pressure on the basis of the metric."

**What it does NOT say / limits:**
- Non-peer-reviewed arXiv preprint — cite as such.
- The paper disclaims fidelity to the originals (p. 1 fn. 1): "Because none of the terms were laid out formally, the categories proposed do not match what was originally discussed." The four variants are a taxonomy of optimization failures under the *label* Goodhart, not an exegesis of Goodhart 1975.
- The precursor is Garrabrant's LessWrong post "Goodhart Taxonomy," dated **December 30, 2017** (paper's own ref [2]), originally on lesserwrong.com — not 2016.
- For the Brainlift's §4a claim ("the failure here is the regressional/causal variant, not the adversarial one"): the regressional and causal definitions above are the right ones to quote. Note the paper frames adversarial Goodhart as requiring "an agent with different goals than the regulator" — which supports the Brainlift's point that an honest student optimizing retention is not the adversarial case.

---

## 5. The metacognition pair — the two specific questions

### 5.1 Koriat & Bjork (2005): does foresight bias apply to Anki's rating screen?

**Short answer: not as demonstrated — the paper's effect is strictly a study-time, pre-retrieval phenomenon, and the follow-up literature shows retrieval attempts are the debiasing manipulation. The defensible version of the Brainlift's claim is the hindsight-flavored one, and it needs rewording.**

Citation verified exactly as the author believed (journal masthead + PMID 15755238). One flag: the term "foresight bias" appears **nowhere in the title or abstract** — it is coined in the General Discussion (p. 193).

**Definition** (p. 193, verbatim):

> "We see the foresight bias as a kind of mirror image: Unlike the hindsight bias, which occurs when the recall of one's past answer is made in the presence of the correct answer, the foresight bias occurs when predictions about one's success in recalling the correct answer are made in the presence of that answer."

**Core problem statement** (abstract, p. 187):

> "The monitoring of one's own knowledge during study suffers from an inherent discrepancy between study and test situations: Judgments of learning (JOLs) are made in the presence of information that is absent but solicited during testing. The failure to discount the effects of that information when making JOLs can instill a sense of competence during learning that proves unwarranted during testing."

**Exact experimental setup** (Exp. 1 Method, p. 189, verbatim):

> "During the study phase, the stimulus and response words were presented at the center of the screen side by side for 4 s. Participants were instructed to study each pair so that later they would be able to recall the second word in each pair when the first was presented. ... The pair was replaced after 500 ms by the statement Probability to Recall. Participants reported their estimate orally on a 0%–100% scale. During the test phase, which began about 1 min after the end of the study phase, the 60 stimulus words were presented one after the other for up to 8 s each."

So: paired associates, both words visible during study, JOL prompt immediately after, **no retrieval attempt of any kind before the judgment** (Exps. 2–3 same). There is no test-then-judge condition anywhere in the paper.

**The effect is also item-selective, not blanket** (p. 193):

> "it is not the presence of the answer per se that produces overconfident JOLs but, rather, the presence of an answer that elicits a posteriori associations between cue and target that are inordinately strong relative to the a priori association between those words."

> "It is important to stress that the overconfidence we observed is not simply a standard feature of JOLs. By and large, JOLs do not exhibit an overconfidence bias and, in fact, for many of the items used in this study, JOLs were very well calibrated."

**What the follow-ups say about retrieval as debiasing** — Koriat & Bjork (2006), *Memory & Cognition*, 34(5), 959–972 (abstract, p. 959, verbatim):

> "The present findings demonstrate that foresight bias can be alleviated by study–test experience (Experiment 1), particularly test experience (Experiments 2 and 3), and by delaying JOLs after study (Experiment 4) and that both foresight bias and its alleviation have behavioral consequences, as measured by study time allocation (Experiment 5)."

Delay reduced but did not eliminate the bias (p. 968): immediate-JOL overconfidence of 6.4% (forward pairs) and 32.3% (backward pairs) fell to 4.3% and 12.0% with delayed JOLs; "Consistent with our hypothesis, delaying JOLs alleviated foresight bias, although not entirely" (p. 969).

**The residual claim the Brainlift CAN make** — K&B 2005 itself (p. 188) characterizes Dunlosky & Nelson (1997):

> "Dunlosky and Nelson (1997) found that delayed JOLs were consistently higher when prompted by the cue–target pair than when they were prompted by the cue alone, and they proposed that this effect might be a type of hindsight effect (Fischhoff, 1975): When both the cue and target are presented together, they evoke an 'I knew it all along' feeling."

**Recommended rewording for §4c:** don't say "the Anki rating is foresight-biased (Koriat & Bjork 2005)." Say: judgments made with the answer in view are inflated relative to judgments made from the cue alone (Dunlosky & Nelson 1997, via Koriat & Bjork 2005, p. 188; hindsight-type effect), and the accuracy-conferring ingredient the delayed-JOL literature identifies — judging from the cue alone — is precisely what the rating screen discards by collecting the judgment after the reveal. That is fully supported. What no study in this literature tests is Anki's exact configuration (overt attempt → reveal → rate, with the rater knowing the objective outcome of the attempt) — the Brainlift should say so explicitly; it is also exactly the gap the split-timer experiment probes.

### 5.2 Nelson & Dunlosky (1991): how strong is the counter-evidence?

Citation verified via Crossref + JSTOR. Pages: use **267–270** (JSTOR and the authors' own later reference lists; Crossref's "267–271" is an outlier). ⛔ **No legitimate open copy of the 1991 full text was found** (Sage/JSTOR paywalled); the numbers below come from the authors' own 1992 follow-up, whose full text WAS read.

**Strength of the effect** — Dunlosky & Nelson (1992), p. 374–375, verbatim:

> "In Nelson and Dunlosky (1991), the average Goodman-Kruskal gamma correlation (G) between JOLs and recall was +.38 for immediate JOLs, but was close to perfect (G = +.90) for delayed JOLs."

And in the 1992 experiment itself (p. 376):

> "relative JOL accuracy was much greater for delayed JOLs (median G = +.93) than for immediate JOLs (median G = +.45). The difference between these two conditions was highly reliable (p < .001, by a sign test), and relative JOL accuracy was greater for delayed than for immediate JOLs for every one of the 45 subjects whose JOLs were cued by the stimulus alone!"

Field-level corroboration: Rhodes & Tauber's meta-analysis (45 studies, 112 effect sizes) found delaying JOLs raises gamma by "nearly one standard deviation (g = 0.93)" (as reported in Rhodes 2015, Oxford Handbook chapter, p. 12).

**The boundary condition — this is the pivotal fact for the Brainlift** — Dunlosky & Nelson (1992), abstract, p. 374, verbatim:

> "following the study of stimulus-response paired associates, there is an extremely robust delayed-JOL effect when the cue for JOLs is the stimulus alone (every one of 45 subjects showed the effect); however, there is little, if any, delayed-JOL effect when the cue for JOLs is the stimulus-response pair."

With the pair in view: delayed median G = +.60 vs immediate +.55, not reliably different (p = .18) (p. 376). Standard interpretation (Rhodes 2015, p. 13): "soliciting a JOL with the cue and target eliminates the opportunity to interrogate long-term memory and thus robs the learner of diagnostic information."

**Standard explanation** (Rhodes 2015, pp. 12–13): delayed judgment "encourages participants to attempt retrieval from long-term memory, with that information informing judgment"; immediate JOLs reflect short-term-memory access that is "less diagnostic of future memory performance."

**What it does NOT say / limits:**
- G = +.90 is **relative accuracy** (item ordering / resolution), not absolute calibration. "Nearly perfect predictions" would overreach.
- Materials: unrelated noun–noun paired associates, ~10-minute retention. Educational materials and long intervals are extrapolation.
- The 1991 delayed JOLs were cued by the stimulus alone; the paper does not show delayed judgments are accurate **when the answer is visible** — the cue-alone restriction is the 1992 result.
- It does not claim delayed JOLs improve memory itself.

**Net for the Brainlift's §4c dialectic:** the counter-evidence (S7) is even sharper than the author framed it. The retrieval attempt does confer calibration — hugely (G +.38 → +.90) — but only when the judgment is made *from the cue alone*. Anki elicits the attempt (good) and then collects the judgment with the answer on screen (the condition in which the delayed-JOL advantage vanishes in this literature). The honest statement: Anki's flow combines the two conditions, and no study in this literature tested that combination. Both S6 and S7 converge on the same design implication: the informative moment is pre-reveal.

---

## 6. Learning science — quotes and limits

### S1 — Bjork, desirable difficulties

Bjork (1994), verbatim (Bjork Lab PDF):

> "Manipulations that speed the rate of acquisition during training can fail to support long-term posttraining performance, while other manipulations that appear to introduce difficulties for the learner during training can enhance posttraining performance." (p. 185)

> "the central point is that the research picture is unambiguous: A variety of manipulations that impede performance during training facilitate performance on the long term." (p. 192)

The phrase itself appears only in passing (p. 193: "...the types of desirable difficulties summarized in the preceding section."). The list appears as section headings (pp. 189–192) and is recapped on p. 201: "Manipulations such as varying the conditions of training, inducing contextual interference, distributing practice, reducing the frequency of augmented feedback, and using tests as learning events share the property that they act to better educate the learner's subjective experience."

Bjork & Bjork (2011), p. 58 — the canonical definition + list, verbatim:

> "Such desirable difficulties (Bjork, 1994) include varying the conditions of learning, rather than keeping them constant and predictable; interleaving instruction on separate topics, rather than grouping instruction by topic (called blocking); spacing, rather than massing, study sessions on a given topic; and using tests, rather than presentations, as study events."

> "Desirable difficulties, versus the array of undesirable difficulties, are desirable because they trigger encoding and retrieval processes that support learning, comprehension, and remembering. If, however, the learner does not have the background knowledge or skills to respond to them successfully, they become undesirable difficulties." (p. 58)

**Limits:** the p. 58 caveat is the authors' own — difficulty is desirable only when the learner can succeed at it; "harder is always better" contradicts the source. The 2011 piece is a general-audience anthology essay, not a peer-reviewed empirical paper. Editions differ in pagination (1st ed. 56–64; 2nd ed. 2014 59–68).

### S2 — Barnett & Ceci (2002), transfer taxonomy

**The taxonomy's actual structure — 9 dimensions, not 6** (p. 614, verbatim):

> "we argue that, at a minimum, the following dimensions are needed: (a) the nature of the skill to be transferred, the performance change measured for this skill, and the memory demands of the transfer task used to measure it and (b) the distance between the training and transfer contexts along multiple dimensions (knowledge domain, physical context, temporal context, functional context, social context, and modality)."

Content = 3 dimensions (specificity–generality of the learned skill; nature of the performance change; memory demands of the transfer task — p. 621). Context = 6 dimensions (knowledge domain, physical, temporal, functional, social, modality — p. 623).

**What the paper concludes about far transfer** (abstract, p. 612, verbatim):

> "Despite a century's worth of research, arguments surrounding the question of whether far transfer occurs have made little progress toward resolution. The authors argue the reason for this confusion is a failure to specify various dimensions along which transfer can occur, resulting in comparisons of 'apples and oranges.' ... Estimation of a single effect size for far transfer is misguided in view of this complexity. The past 100 years of research shows that evidence for transfer under some conditions is substantial, but critical conditions for many key questions are untested."

**Limits:** does not conclude far transfer is impossible ("evidence... under some conditions is substantial") nor well-established ("critical conditions... are untested"). Classification framework, not a meta-analysis; explicitly excludes teaching-regimen variables (practice variability, feedback) and learner characteristics (p. 612). Cannot be cited as evidence that any particular intervention produces far transfer — for the Brainlift, its correct role is supplying the *distance vocabulary* for what a reworded-probe experiment varies.

### S3 — Roediger & Karpicke (2006), testing effect

Core result (abstract, p. 249, verbatim):

> "When the final test was given after 5 min, repeated studying improved recall relative to repeated testing. However, on the delayed tests, prior testing produced substantially greater retention than studying, even though repeated studying increased students' confidence in their ability to remember the material. Testing is a powerful means of improving learning, not just assessing it."

**The metacognitive twist is real and verified** (Discussion, p. 253, verbatim):

> "Although students in the repeated-study condition predicted they would perform very well a week later (relative to those in the other conditions), they actually performed the worst."

Bonus link to S1 (p. 253): "Testing clearly introduced a desirable difficulty during learning."

**Limits:** the confidence measure was a single 7-point group-level rating (Exp. 2 only) — fair to call it misprediction, overreach to call it item-level calibration data. Materials were two prose passages; retention measured by free recall of the **same** material, no feedback — the paper makes **no claim about transfer to reworded or novel questions**. For transfer, the verified citation is Butler, A. C. (2010), Repeated testing produces superior transfer of learning relative to repeated studying, *JEP:LMC*, 36(5), 1118–1133, https://doi.org/10.1037/a0019902 (metadata verified via PubMed 20804289; full text not fetched — don't quote it verbatim without checking). Companion review also verified to exist: Roediger & Karpicke (2006), The power of testing memory, *Perspectives on Psychological Science*, 1(3), 181–210, DOI 10.1111/j.1745-6916.2006.00012.x.

### S4 — Sweller (1988), cognitive load

Verbatim (open copy):

> "It is suggested that a major reason for the ineffectiveness of problem solving as a learning device, is that the cognitive processes required by the two activities overlap insufficiently, and that conventional problem solving in the form of means-ends analysis requires a relatively large amount of cognitive processing capacity which is consequently unavailable for schema acquisition." (abstract, p. 257)

> "Goal attainment and schema acquisition may be two largely unrelated and even incompatible processes." (p. 283)

**Limits:** about **novices** solving math/physics-style problems; remedy is goal-free problems/worked examples. Contains no claim about capping difficulty for well-practiced learners — the "expertise reversal effect" is later work (Kalyuga, Ayres, Chandler & Sweller 2003, *Educational Psychologist* — ⛔ not verified here; check before citing). Citing Sweller 1988 to cap retrieval difficulty for experienced learners both overreaches and collides with S1/S3 (retrieval practice is a desirable difficulty, not extraneous load). The 1998/2019 Sweller–van Merriënboer–Paas reviews were ⛔ not verified — check before citing.

### S5 — Dunlosky et al. (2013), technique review

Verbatim (Summary, pp. 4–5):

> "Practice testing and distributed practice received high utility assessments because they benefit learners of different ages and abilities and have been shown to boost students' performance across many criterion tasks and even in educational contexts."

> "Five techniques received a low utility assessment: summarization, highlighting, the keyword mnemonic, imagery use for text learning, and rereading."

> "Most students report rereading and highlighting, yet these techniques do not consistently boost students' performance, so other techniques should be used in their place (e.g., practice testing instead of rereading)."

**Limits:** utility ratings measure **generalizability of evidence** ("we evaluated whether their benefits generalize across four categories of variables: learning conditions, student characteristics, materials, and criterion tasks," p. 4) — not an effect-size league table. Interleaving got only *moderate* utility. The review does not evaluate Anki, SuperMemo, or any SRS software, and predates most SRS-specific research. "Low utility" for rereading ≠ proven harm.

---

## 7. Response latency as a difficulty/effort signal (new material for the author)

Ten verified sources. Two candidate citations were wrong and are corrected here (see Errors #8).

| Source | Full citation | Link / access | Core verified content |
|--------|--------------|---------------|----------------------|
| Wise & Kong 2005 | Wise, S. L., & Kong, X. (2005). Response time effort: A new measure of examinee motivation in computer-based tests. *Applied Measurement in Education*, 18(2), 163–183. DOI 10.1207/s15324818ame1802_2 | Paywalled; free author preprint (read): https://files.eric.ed.gov/fulltext/ED490203.pdf | "This measure, termed response time effort (RTE), is based on the hypothesis that when administered an item, unmotivated examinees will answer too quickly (i.e., before they had time to read and fully consider the item)." (p. 2) — and, on self-report: "self-report measures of effort are potentially vulnerable to bias through motivational processes, and it is difficult to ascertain the degree to which these factors have influenced a particular set of self-report data." (p. 4); RTE built to be "based more on direct records of examinee behavior than on self-reported judgments of behavior." (p. 5); per-item threshold T_i = "the response time boundary between rapid-guessing behavior and solution behavior." (p. 6) |
| Schnipke & Scrams 1997 | Schnipke, D. L., & Scrams, D. J. (1997). Modeling item response times with a two-state mixture model: A new method of measuring speededness. *Journal of Educational Measurement*, 34(3), 213–232. DOI 10.1111/j.1745-3984.1997.tb00516.x | Paywalled. ⛔ No primary quote verified. | Solution-behavior vs rapid-guessing-behavior distinction; characterized secondhand in Wise & Kong's preprint (p. 5). About speededness on timed tests. |
| van der Linden 2007 | van der Linden, W. J. (2007). A hierarchical framework for modeling speed and accuracy on test items. *Psychometrika*, 72(3), 287–308. DOI 10.1007/s11336-006-1478-z | Paywalled; abstract verified | Hierarchical model treating speed as a separate examinee parameter; "allows a 'plug-and-play approach' with alternative choices of models for the response and response-time distributions". ⛔ Do not attribute a "RT carries information beyond correctness" sentence to it — unverified in body text. |
| Benjamin, Bjork & Schwartz 1998 | Benjamin, A. S., Bjork, R. A., & Schwartz, B. L. (1998). The mismeasure of memory: When retrieval fluency is misleading as a metamnemonic index. *JEP: General*, 127(1), 55–68. DOI 10.1037/0096-3445.127.1.55 | Paywalled; PubMed abstract verified: https://pubmed.ncbi.nlm.nih.gov/9503651/ | **Cuts against naive latency claims:** fluency "guides and occasionally misleads metamnemonic judgments"; conditions where "probability or speed of retrieval at one time or on one task is known to be negatively related to retrieval probability on a later task." Fast retrieval now can predict worse retention later — and participants' own judgments track fluency the wrong way. Cuts against both naive latency heuristics and self-report. |
| Pyc & Rawson 2009 | Pyc, M. A., & Rawson, K. A. (2009). Testing the retrieval effort hypothesis: Does greater difficulty correctly recalling information lead to higher levels of memory? *Journal of Memory and Language*, 60(4), 437–447. DOI 10.1016/j.jml.2009.01.004 | Paywalled; abstract verified via ERIC EJ834321 | "the 'retrieval effort hypothesis,' which states that difficult but successful retrievals are better for memory than easier successful retrievals." Effort partly indexed by latency of successful recalls. |
| Mettler, Massey & Kellman 2016 | Mettler, E., Massey, C. M., & Kellman, P. J. (2016). A comparison of adaptive and fixed schedules of practice. *JEP: General*, 145(7), 897–917. DOI 10.1037/xge0000170. (ARTS introduced in their 2011 Proc. CogSci paper.) | Free publisher PDF (read): https://www.apa.org/pubs/journals/features/xge-xge0000170.pdf | "Evidence indicates that response time (RT) is a useful indicator of retrieval difficulty, and thus of an item's current learning strength" (p. 899); "ARTS uses a priority score system, in which the priority for an item to reappear on each learning trial is computed dynamically as a function of accuracy, RT, and trials since the last presentation." (p. 899); "In both experiments, adaptive scheduling outperformed fixed conditions at immediate and delayed tests of retention." (p. 897) — **strongest domain match**: a latency-driven flashcard scheduler that works. |
| Papoušek et al. 2015 | Papoušek, J., Pelánek, R., Řihák, J., & Stanislav, V. (2015). An analysis of response times in adaptive practice of geography facts. *Proc. EDM 2015*, 562–563. | Free PDF (read): http://www.fi.muni.cz/~xpelanek/publications/poster-response-times.pdf | "The relationship between response time and correctness of the current answer is non-monotonic – very fast responses combine 'solid knowledge' and 'pure guessing', long responses mostly indicate 'weak knowledge'." / "If the current answer is correct then the probability of correct next answer is linearly dependent on the response time – it goes from 95% for very fast answers to nearly 80% for slow answers." / "When the current answer is incorrect, longer response time actually means higher chance that the next answer will be correct!" — **directly relevant to the guessing section (§4d)**: fast+correct is ambiguous between mastery and lucky guess. |
| Wise 2017 | Wise, S. L. (2017). Rapid-guessing behavior: Its identification, interpretation, and implications. *Educational Measurement: Issues and Practice*, 36(4), 52–61. DOI 10.1111/emip.12165 | Paywalled; verified via ERIC EJ1162502 | Review of rapid-guessing identification; rapid guesses indicate disengagement and don't reflect knowledge. |
| Kyllonen & Zu 2016 | Kyllonen, P. C., & Zu, J. (2016). Use of response time for measuring cognitive ability. *Journal of Intelligence*, 4(4), 14. DOI 10.3390/jintelligence4040014 | **Open access** (read): https://www.mdpi.com/2079-3200/4/4/14 | Key caveat, p. 1: "A major challenge in the measurement of response time is that there are many factors that influence it, and it is typically impossible to attribute response time uniquely to any particular factor. For example, a respondent's slow response might reflect either slow processing speed or carefulness." |
| Pelánek 2024 | Pelánek, R. (2024). Leveraging response times in learning environments: opportunities and challenges. *User Modeling and User-Adapted Interaction*, 34(3), 729–752 (online Nov 2023). DOI 10.1007/s11257-023-09386-7 | **Open access** (read): https://www.fi.muni.cz/~xpelanek/publications/umuai-response-times.pdf | "The speed of response may indicate the student's level of knowledge. Without considering response time, it is difficult to differentiate fluent and non-fluent performance." (p. 1) — and the caveats: response times "are typically noisy, influenced by random events such as interruptions and momentary lack of concentration, as well as more systematic effects like orthogonal skills" (p. 2); "response times have the potential to enhance learning environments, but it remains unclear how to practically realize this potential." (p. 2) — best single review to cite. |

**Honest net assessment (as instructed: does the literature support "latency beats self-report"?).** The literature genuinely supports latency as an informative, behavior-based signal of effort and memory strength: Wise & Kong built RTE explicitly because self-reports of effort are bias-prone; Mettler et al. showed an RT-driven flashcard scheduler beats fixed schedules; Papoušek et al. found latency on correct answers linearly predicts next-recall probability; Pyc & Rawson tie slower successful retrieval to better retention. **But no verified study directly tests the author's precise claim** — pre-reveal latency vs. post-reveal self-graded difficulty as competing scheduling inputs — so "latency beats self-report" is an extrapolation, not an established finding, and should be presented as the hypothesis his split-timer experiment tests. Also note: the self-report the rapid-guessing literature discredits is *effort* self-report on low-stakes tests, not difficulty grading by motivated learners. And the literature contradicts any naive version of the claim: latency is non-monotonic (fast = mastery *or* guessing — Papoušek), confounded and behaviorally adjustable (Kyllonen & Zu; Pelánek), and fast retrieval can accompany *worse* later retention (Benjamin, Bjork & Schwartz). Net: strong support for latency as a **valuable additional signal, conditional on correctness and per-item normalization**; weak direct support for "better than self-report." One convergent detail worth keeping: Papoušek et al. also found slower students *report* higher difficulty — latency and self-report agree at the aggregate level; the interesting question is per-review incremental value, which is exactly POV 2's "Wrong if" test.

---

## 8. Could-not-verify ledger

Everything below is explicitly unverified. Do not cite without independent checking.

1. **Goodhart (1975) primary text** — no agent saw any page of the 1975 original or its 1981/1984 reprints. The law's wording is verified only via Manheim & Garrabrant (2018) fn. 1 and Wikipedia-cited Courakis (1981), p. 116.
2. **"Coined half-jokingly"** framing of Goodhart's law — no verbatim source found; Chrystal & Mizen (2003) is the standard history but was not fetched.
3. **Nelson & Dunlosky (1991) full text** — paywalled everywhere; abstract fragments via Crossref, gammas via the authors' own 1992 paper (which WAS read). The sentence "Every subjects' accuracy on delayed JOL was greater than the mean of those same subjects' accuracy on immediate JOL" (1991, p. 269) is secondhand via Rhodes (2015) — label it as such if used.
4. **Schnipke & Scrams (1997)** — no primary quote; characterization is secondhand via Wise & Kong.
5. **Woźniak & Gorzelańczyk (1994) full body text** — abstract verified via PubMed only (full-text mirror returned 403).
6. **Butler (2010)**, **Kalyuga et al. (2003)**, **Sweller/van Merriënboer/Paas (1998, 2019)**, **Roediger & Karpicke's PPS review (2006)** — metadata verified (where stated), full texts not fetched; don't quote verbatim without checking.
7. **van der Linden (2007) body text** — abstract only.

---

## 9. Recommendations

1. **Fix L1 in §2 now** (Error #1): split into L1a (Woźniak 1990 thesis via super-memory.com — the grade scale) and L1b (Woźniak & Gorzelańczyk 1994 — the peer-reviewed spacing paper, citable for lineage only).
2. **Reword the §4c foresight-bias stub** (Error #2): the S6-supported claim is "answer-in-view inflates the judgment" (hindsight-type effect; Dunlosky & Nelson 1997 via K&B 2005 p. 188), not "the rating screen exhibits foresight bias." The S7 boundary condition (delayed-JOL advantage vanishes when the pair is in view — Dunlosky & Nelson 1992, verbatim above) actually makes the author's argument *stronger* and cleaner: the calibration-conferring moment is pre-reveal, and Anki collects the judgment post-reveal. Add S7b (Dunlosky & Nelson 1992) to §2 — it, not the 1991 paper, carries the pivotal cue-alone vs. cue+target result.
3. **Add a latency row group to §2** from §7 of this report — Mettler et al. (2016), Pelánek (2024), Wise & Kong (2005), and Papoušek et al. (2015) are the four strongest; Benjamin, Bjork & Schwartz (1998) belongs there too as declared counter-evidence (the Brainlift's style already embraces owning its objections).
4. **Papoušek et al.'s non-monotonicity finding belongs in §4d (guessing)**: fast+correct is exactly the "knew it vs. guessed it" ambiguity, measured at scale.
5. Present "latency beats self-report" as the hypothesis POV 2's experiment tests, not as established literature — the honest framing is also the one the document's thesis demands.
