import importlib.util
import sys
from pathlib import Path

import pytest

EVALUATOR_PATH = Path(__file__).resolve().parents[2] / "python" / "ai_verification.py"
SPEC = importlib.util.spec_from_file_location("ai_verification", EVALUATOR_PATH)
assert SPEC and SPEC.loader
ai_verification = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = ai_verification
SPEC.loader.exec_module(ai_verification)


def test_frozen_eval_has_fifty_source_traced_predictions() -> None:
    manifest = ai_verification.validate_frozen_inputs()
    sources = ai_verification.load_json(ai_verification.SOURCE_PATH)
    predictions = ai_verification.parse_predictions(
        ai_verification.load_json(ai_verification.PREDICTIONS_PATH),
        {source["source_id"] for source in sources},
    )

    assert manifest["case_count"] == 50
    assert len(predictions) == 50
    assert all(prediction.source_ids for prediction in predictions.values())


def test_candidate_passes_cutoff_and_beats_keyword_baseline() -> None:
    result = ai_verification.run_verification()

    assert result["status"] == "passed"
    assert (
        result["candidate"]["correct_useful_rate"]
        >= ai_verification.PREDECLARED_CORRECT_USEFUL_CUTOFF
    )
    assert result["lift"] >= ai_verification.PREDECLARED_MINIMUM_BASELINE_LIFT


def test_changed_or_contradictory_answer_has_no_frozen_human_judgment() -> None:
    cases = ai_verification.load_json(ai_verification.GOLD_PATH)
    predictions = ai_verification.parse_predictions(
        ai_verification.load_json(ai_verification.PREDICTIONS_PATH)
    )
    original = predictions["fc1-1"]
    predictions["fc1-1"] = ai_verification.Prediction(
        case_id=original.case_id,
        answer="Foundational Concept 1 says biomolecules are unrelated to life.",
        source_ids=original.source_ids,
    )
    judgments = ai_verification.load_json(ai_verification.JUDGMENTS_PATH)

    with pytest.raises(ValueError, match="frozen human judgments"):
        ai_verification.score_predictions(
            cases,
            predictions,
            judgments["candidate"],
        )


def test_ai_off_and_evaluator_failure_leave_scoring_available(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        ai_verification,
        "validate_frozen_inputs",
        lambda: (_ for _ in ()).throw(ValueError("offline")),
    )

    result = ai_verification.safe_verification()

    assert result["status"] == "unavailable"
    assert result["app_scoring_available"] is True
    assert result["ai_runtime_required_by_app"] is False

    monkeypatch.setattr(sys, "argv", ["ai_verification.py", "--ai-off"])
    assert ai_verification.main() == 2


def test_unknown_source_fails_closed() -> None:
    raw = [{"case_id": "case-1", "answer": "answer", "source_ids": ["unknown"]}]

    with pytest.raises(ValueError, match="unknown sources"):
        ai_verification.parse_predictions(raw, {"known"})


def test_unknown_judgment_and_extra_prediction_fail_closed() -> None:
    cases = [{"case_id": "case-1"}]
    prediction = ai_verification.Prediction("case-1", "answer", ("source-1",))
    predictions = {"case-1": prediction}
    judgment = {
        "prediction_set_sha256": ai_verification.prediction_set_hash(
            cases, predictions
        ),
        "wrong": ["unknown-case"],
        "correct_bad_teaching": [],
    }

    with pytest.raises(ValueError, match="unknown cases"):
        ai_verification.score_predictions(cases, predictions, judgment)

    predictions["extra-case"] = ai_verification.Prediction(
        "extra-case", "answer", ("source-1",)
    )
    with pytest.raises(ValueError, match="do not exactly match"):
        ai_verification.score_predictions(cases, predictions, judgment)


def test_report_contains_cutoff_baseline_and_source(tmp_path: Path) -> None:
    report = ai_verification.report_markdown(ai_verification.run_verification())
    output = tmp_path / "report.md"
    output.write_text(report)

    text = output.read_text()
    assert "Cutoff: 90%" in text
    assert "Keyword-overlap baseline" in text
    assert "AAMC" in text
