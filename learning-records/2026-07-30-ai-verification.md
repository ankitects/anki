# Brainlift AI Verification

## Predeclared gate

- Held-out set: 50 paraphrased question-and-answer checks frozen before scoring.
- Source: AAMC What's on the MCAT Exam? content outline (https://students-residents.aamc.org/media/9261/download).
- Metric: correct-and-useful rate under frozen human judgments.
- Cutoff: 90%.
- Required lift over keyword overlap: 10%.

## Result

| Method | Correct and useful | Wrong | Correct but bad teaching | Rate |
|---|---:|---:|---:|---:|
| Source-traced Codex outputs | 45 | 0 | 5 | 90% |
| Keyword-overlap baseline | 37 | 13 | 0 | 74% |

Lift: 16%. Decision: **PASSED**.

Every candidate output names an AAMC outline source ID. Human judgments are
bound to exact candidate and baseline prediction-set hashes, so changed or
contradictory answers fail closed. Source IDs, case IDs, prediction IDs, and
judgment IDs must also form exact valid relationships. The manifest freezes
the source index, held-out cases, predictions, judgments, and evaluator.

## AI-off behavior

The evaluator is a standalone script. The Rust score snapshot, Python bridge,
desktop reviewer, and mobile bridge do not import it. If the evaluator is
disabled, malformed, or unavailable, study and deterministic scoring continue.
The verifier itself returns a nonzero status when disabled, so a release gate
cannot treat AI-off mode as a passing evaluation.

## Limits

This is a small source-grounding and teaching-quality smoke, not evidence that an
AI tutor improves MCAT transfer. The 50 cases are paraphrases of ten official
foundational-concept summaries, and the static candidate outputs were produced
in the Codex implementation session. Student outcome validation remains outside
this Friday gate.

Before the final manifest freeze, adversarial review showed that token overlap
could accept a contradictory answer. Automated semantic labels were replaced
with frozen human judgments tied to exact prediction sets; the 90% cutoff, 10%
lift, questions, predictions, and reported labels did not change.
