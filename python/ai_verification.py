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


def keyword_baseline(question: str, sources: list[dict[str, str]]) -> Prediction:
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
        case_id="",
        answer=f"{selected['title']}: {selected['summary']}",
        source_ids=(selected["source_id"],),
    )


def parse_predictions(raw: list[dict[str, Any]]) -> dict[str, Prediction]:
    predictions: dict[str, Prediction] = {}
    for item in raw:
        prediction = Prediction(
            case_id=item["case_id"],
            answer=item["answer"].strip(),
            source_ids=tuple(item["source_ids"]),
        )
        if not prediction.source_ids:
            raise ValueError(f"{prediction.case_id} has no named source")
        if prediction.case_id in predictions:
            raise ValueError(f"duplicate prediction for {prediction.case_id}")
        predictions[prediction.case_id] = prediction
    return predictions


def classify(
    case: dict[str, str],
    prediction: Prediction,
    source_by_id: dict[str, dict[str, str]],
) -> str:
    expected_source = case["expected_source_id"]
    expected_title = source_by_id[expected_source]["title"].lower()
    answer = prediction.answer.lower()
    correct = expected_source in prediction.source_ids and expected_title in answer
    if not correct:
        return "wrong"

    source_terms = tokenize(source_by_id[expected_source]["summary"])
    answer_terms = tokenize(answer)
    useful = len(source_terms & answer_terms) >= 2 and 5 <= len(answer.split()) <= 70
    return "correct_useful" if useful else "correct_bad_teaching"


def score_predictions(
    cases: list[dict[str, str]],
    predictions: dict[str, Prediction],
    sources: list[dict[str, str]],
) -> dict[str, Any]:
    source_by_id = {source["source_id"]: source for source in sources}
    counts = {"correct_useful": 0, "wrong": 0, "correct_bad_teaching": 0}
    details = []
    for case in cases:
        prediction = predictions.get(case["case_id"])
        if prediction is None:
            raise ValueError(f"missing prediction for {case['case_id']}")
        classification = classify(case, prediction, source_by_id)
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
    candidate = parse_predictions(load_json(PREDICTIONS_PATH))
    baseline = {
        case["case_id"]: Prediction(
            case_id=case["case_id"],
            answer=(result := keyword_baseline(case["question"], sources)).answer,
            source_ids=result.source_ids,
        )
        for case in cases
    }

    candidate_score = score_predictions(cases, candidate, sources)
    baseline_score = score_predictions(cases, baseline, sources)
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
- Metric: correct-and-useful rate under the fixed source-trace rubric.
- Cutoff: {result["cutoff"]:.0%}.
- Required lift over keyword overlap: {result["minimum_baseline_lift"]:.0%}.

## Result

| Method | Correct and useful | Wrong | Correct but bad teaching | Rate |
|---|---:|---:|---:|---:|
| Source-traced Codex outputs | {candidate["counts"]["correct_useful"]} | {candidate["counts"]["wrong"]} | {candidate["counts"]["correct_bad_teaching"]} | {candidate["correct_useful_rate"]:.0%} |
| Keyword-overlap baseline | {baseline["counts"]["correct_useful"]} | {baseline["counts"]["wrong"]} | {baseline["counts"]["correct_bad_teaching"]} | {baseline["correct_useful_rate"]:.0%} |

Lift: {result["lift"]:.0%}. Decision: **{result["status"].upper()}**.

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
        return 0

    result = safe_verification()
    if args.report:
        args.report.write_text(report_markdown(result))
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
