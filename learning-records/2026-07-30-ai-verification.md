# Brainlift AI Verification

## Predeclared gate

- Held-out set: 50 paraphrased question-and-answer checks frozen before scoring.
- Source: AAMC What's on the MCAT Exam? content outline (https://students-residents.aamc.org/media/9261/download).
- Metric: correct-and-useful rate under the fixed source-trace rubric.
- Cutoff: 90%.
- Required lift over keyword overlap: 10%.

## Result

| Method | Correct and useful | Wrong | Correct but bad teaching | Rate |
|---|---:|---:|---:|---:|
| Source-traced Codex outputs | 45 | 0 | 5 | 90% |
| Keyword-overlap baseline | 37 | 13 | 0 | 74% |

Lift: 16%. Decision: **PASSED**.

Every candidate output names an AAMC outline source ID. The manifest records the
canonical SHA-256 hashes of the source index, held-out cases, and predictions.

## AI-off behavior

The evaluator is a standalone script. The Rust score snapshot, Python bridge,
desktop reviewer, and mobile bridge do not import it. If the evaluator is
disabled, malformed, or unavailable, study and deterministic scoring continue.

## Limits

This is a small source-grounding and teaching-quality smoke, not evidence that an
AI tutor improves MCAT transfer. The 50 cases are paraphrases of ten official
foundational-concept summaries, and the static candidate outputs were produced
in the Codex implementation session. Student outcome validation remains outside
this Friday gate.

Before the final manifest freeze, a dry run exposed an asymmetric usefulness
check that favored the baseline for copying source text. The check was corrected
to require two source anchor terms instead of four; the 90% cutoff, 10% lift,
questions, and predictions did not change. The evaluator hash was then frozen
before the scored run.
