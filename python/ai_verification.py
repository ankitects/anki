#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EVAL_DIR = ROOT / "python" / "ai_eval"
SOURCE_PATH = EVAL_DIR / "aamc_foundational_concepts.json"
GOLD_PATH = EVAL_DIR / "held_out_questions.json"
PREDICTIONS_PATH = EVAL_DIR / "codex_predictions.json"
JUDGMENTS_PATH = EVAL_DIR / "human_judgments.json"
MANIFEST_PATH = EVAL_DIR / "manifest.json"

PREDECLARED_CORRECT_USEFUL_CUTOFF = 0.90
PREDECLARED_MINIMUM_BASELINE_LIFT = 0.10
TOKEN_RE = re.compile(r"[a-z0-9]+")
STOPWORDS = {
    "a",
    "about",
    "an",
    "and",
    "are",
    "as",
    "at",
    "be",
    "by",
    "for",
    "from",
    "how",
    "in",
    "is",
    "it",
    "of",
    "on",
    "or",
    "that",
    "the",
    "their",
    "this",
    "to",
    "what",
    "which",
    "with",
}


@dataclass(frozen=True)
class Prediction:
    case_id: str
    answer: str
    source_ids: tuple[str, ...]


def load_json(path: Path) -> Any:
    return json.loads(path.read_text())


def canonical_hash(path: Path) -> str:
    value = load_json(path)
    canonical = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(canonical).hexdigest()


def file_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_frozen_inputs() -> dict[str, Any]:
    manifest = load_json(MANIFEST_PATH)
    expected = manifest["sha256"]
    actual = {
        "sources": canonical_hash(SOURCE_PATH),
        "gold": canonical_hash(GOLD_PATH),
        "predictions": canonical_hash(PREDICTIONS_PATH),
        "judgments": canonical_hash(JUDGMENTS_PATH),
        "evaluator": file_hash(Path(__file__)),
    }
    if actual != expected:
        raise ValueError(
            f"frozen eval inputs changed: expected {expected}, got {actual}"
        )

    gold = load_json(GOLD_PATH)
    if len(gold) != manifest["case_count"]:
        raise ValueError("held-out case count does not match the frozen manifest")
    return manifest


def tokenize(text: str) -> set[str]:
    return {token for token in TOKEN_RE.findall(text.lower()) if token not in STOPWORDS}


def keyword_baseline(
    case_id: str, question: str, sources: list[dict[str, str]]
) -> Prediction:
    question_tokens = tokenize(question)
    ranked = sorted(
        sources,
        key=lambda source: (
            len(question_tokens & tokenize(f"{source['title']} {source['summary']}")),
            source["source_id"],
        ),
        reverse=True,
    )
    selected = ranked[0]
    return Prediction(
        case_id=case_id,
        answer=f"{selected['title']}: {selected['summary']}",
        source_ids=(selected["source_id"],),
    )


def parse_predictions(
    raw: list[dict[str, Any]],
    valid_source_ids: set[str] | None = None,
) -> dict[str, Prediction]:
    predictions: dict[str, Prediction] = {}
    for item in raw:
        prediction = Prediction(
            case_id=item["case_id"],
            answer=item["answer"].strip(),
            source_ids=tuple(item["source_ids"]),
        )
        if not prediction.source_ids:
            raise ValueError(f"{prediction.case_id} has no named source")
        if len(prediction.source_ids) != len(set(prediction.source_ids)):
            raise ValueError(f"{prediction.case_id} repeats a named source")
        if valid_source_ids is not None:
            unknown_sources = set(prediction.source_ids) - valid_source_ids
            if unknown_sources:
                raise ValueError(
                    f"{prediction.case_id} names unknown sources: "
                    f"{sorted(unknown_sources)}"
                )
        if prediction.case_id in predictions:
            raise ValueError(f"duplicate prediction for {prediction.case_id}")
        predictions[prediction.case_id] = prediction
    return predictions


def prediction_set_hash(
    cases: list[dict[str, str]], predictions: dict[str, Prediction]
) -> str:
    rows = [
        {
            "case_id": case["case_id"],
            "answer": predictions[case["case_id"]].answer,
            "source_ids": list(predictions[case["case_id"]].source_ids),
        }
        for case in cases
    ]
    canonical = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(canonical).hexdigest()


def score_predictions(
    cases: list[dict[str, str]],
    predictions: dict[str, Prediction],
    judgment: dict[str, Any],
) -> dict[str, Any]:
    case_ids = [case["case_id"] for case in cases]
    case_id_set = set(case_ids)
    if len(case_ids) != len(case_id_set):
        raise ValueError("held-out cases contain duplicate IDs")
    if set(predictions) != case_id_set:
        raise ValueError(
            "prediction IDs do not exactly match held-out case IDs: "
            f"expected {sorted(case_id_set)}, got {sorted(predictions)}"
        )

    actual_hash = prediction_set_hash(cases, predictions)
    if actual_hash != judgment["prediction_set_sha256"]:
        raise ValueError(
            "prediction set does not match the frozen human judgments: "
            f"expected {judgment['prediction_set_sha256']}, got {actual_hash}"
        )

    wrong_ids = judgment["wrong"]
    bad_teaching_ids = judgment["correct_bad_teaching"]
    wrong = set(wrong_ids)
    correct_bad_teaching = set(bad_teaching_ids)
    if len(wrong_ids) != len(wrong) or len(bad_teaching_ids) != len(
        correct_bad_teaching
    ):
        raise ValueError("human judgments contain duplicate case IDs")
    unknown_judgments = (wrong | correct_bad_teaching) - case_id_set
    if unknown_judgments:
        raise ValueError(
            f"human judgments name unknown cases: {sorted(unknown_judgments)}"
        )
    if wrong & correct_bad_teaching:
        raise ValueError("a human judgment cannot assign two labels to one case")

    counts = {"correct_useful": 0, "wrong": 0, "correct_bad_teaching": 0}
    details = []
    for case in cases:
        prediction = predictions.get(case["case_id"])
        if prediction is None:
            raise ValueError(f"missing prediction for {case['case_id']}")
        if not prediction.source_ids:
            raise ValueError(f"{prediction.case_id} has no named source")
        if case["case_id"] in wrong:
            classification = "wrong"
        elif case["case_id"] in correct_bad_teaching:
            classification = "correct_bad_teaching"
        else:
            classification = "correct_useful"
        counts[classification] += 1
        details.append(
            {
                "case_id": case["case_id"],
                "classification": classification,
                "source_ids": list(prediction.source_ids),
            }
        )

    total = len(cases)
    return {
        "counts": counts,
        "correct_useful_rate": counts["correct_useful"] / total,
        "details": details,
    }


def run_verification() -> dict[str, Any]:
    manifest = validate_frozen_inputs()
    sources = load_json(SOURCE_PATH)
    cases = load_json(GOLD_PATH)
    judgments = load_json(JUDGMENTS_PATH)
    source_ids = [source["source_id"] for source in sources]
    if len(source_ids) != len(set(source_ids)):
        raise ValueError("source index contains duplicate IDs")
    candidate = parse_predictions(load_json(PREDICTIONS_PATH), set(source_ids))
    baseline = {
        case["case_id"]: keyword_baseline(
            case["case_id"],
            case["question"],
            sources,
        )
        for case in cases
    }

    candidate_score = score_predictions(cases, candidate, judgments["candidate"])
    baseline_score = score_predictions(
        cases, baseline, judgments["keyword_baseline"]
    )
    lift = (
        candidate_score["correct_useful_rate"] - baseline_score["correct_useful_rate"]
    )
    passed = (
        candidate_score["correct_useful_rate"] >= PREDECLARED_CORRECT_USEFUL_CUTOFF
        and lift >= PREDECLARED_MINIMUM_BASELINE_LIFT
    )
    return {
        "status": "passed" if passed else "failed",
        "app_scoring_available": True,
        "ai_runtime_required_by_app": False,
        "source": manifest["source"],
        "case_count": manifest["case_count"],
        "cutoff": PREDECLARED_CORRECT_USEFUL_CUTOFF,
        "minimum_baseline_lift": PREDECLARED_MINIMUM_BASELINE_LIFT,
        "candidate": candidate_score,
        "keyword_baseline": baseline_score,
        "lift": lift,
        "frozen_sha256": manifest["sha256"],
    }


def safe_verification() -> dict[str, Any]:
    try:
        return run_verification()
    except Exception as error:
        return {
            "status": "unavailable",
            "error": str(error),
            "app_scoring_available": True,
            "ai_runtime_required_by_app": False,
        }


def report_markdown(result: dict[str, Any]) -> str:
    if result["status"] == "unavailable":
        return (
            "# Brainlift AI Verification\n\n"
            f"Evaluator unavailable: `{result['error']}`\n\n"
            "The Anki study and score paths remain available because they do not "
            "import or call this evaluator.\n"
        )

    candidate = result["candidate"]
    baseline = result["keyword_baseline"]
    return f"""# Brainlift AI Verification

## Predeclared gate

- Held-out set: {result["case_count"]} paraphrased question-and-answer checks frozen before scoring.
- Source: {result["source"]["name"]} ({result["source"]["url"]}).
- Metric: correct-and-useful rate under frozen human judgments.
- Cutoff: {result["cutoff"]:.0%}.
- Required lift over keyword overlap: {result["minimum_baseline_lift"]:.0%}.

## Result

| Method | Correct and useful | Wrong | Correct but bad teaching | Rate |
|---|---:|---:|---:|---:|
| Source-traced Codex outputs | {candidate["counts"]["correct_useful"]} | {candidate["counts"]["wrong"]} | {candidate["counts"]["correct_bad_teaching"]} | {candidate["correct_useful_rate"]:.0%} |
| Keyword-overlap baseline | {baseline["counts"]["correct_useful"]} | {baseline["counts"]["wrong"]} | {baseline["counts"]["correct_bad_teaching"]} | {baseline["correct_useful_rate"]:.0%} |

Lift: {result["lift"]:.0%}. Decision: **{result["status"].upper()}**.

Every candidate output names an AAMC outline source ID. Human judgments are
bound to exact candidate and baseline prediction-set hashes, so changed or
contradictory answers fail closed. The manifest freezes the source index,
held-out cases, predictions, judgments, and evaluator.

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

Before the final manifest freeze, adversarial review showed that token overlap
could accept a contradictory answer. Automated semantic labels were replaced
with frozen human judgments tied to exact prediction sets; the 90% cutoff, 10%
lift, questions, predictions, and reported labels did not change.
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ai-off", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    if args.ai_off:
        print(
            json.dumps(
                {
                    "status": "disabled",
                    "app_scoring_available": True,
                    "ai_runtime_required_by_app": False,
                },
                indent=2,
            )
        )
        return 2

    result = safe_verification()
    if args.report:
        args.report.write_text(report_markdown(result))
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
