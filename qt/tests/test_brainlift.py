# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from unittest.mock import MagicMock

import anki.lang
from anki import stats_pb2

anki.lang.set_lang("en")

from aqt.brainlift import (
    DEFAULT_MCAT_TOPICS,
    brainlift_dashboard,
    render_brainlift_html,
)
from aqt.deckbrowser import DeckBrowser
from aqt.reviewer import Reviewer


def evidence_score(
    *,
    estimate: float,
    lower: float,
    upper: float,
    scale: int = stats_pb2.BrainliftEvidenceScore.PROBABILITY,
) -> stats_pb2.BrainliftEvidenceScore:
    return stats_pb2.BrainliftEvidenceScore(
        availability=stats_pb2.BrainliftEvidenceScore.AVAILABLE,
        scale=scale,
        estimate=estimate,
        range=stats_pb2.BrainliftScoreRange(lower=lower, upper=upper),
        coverage=0.5,
        confidence=stats_pb2.BrainliftEvidenceScore.MEDIUM,
        updated_at_secs=1_700_000_000,
        rated_reviews=20,
        successful_reviews=16,
    )


def test_available_scores_remain_separate() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.return_value = (
        stats_pb2.BrainliftScoreSnapshotResponse(
            memory=evidence_score(estimate=0.8, lower=0.7, upper=0.9),
            performance=evidence_score(estimate=0.65, lower=0.5, upper=0.78),
            readiness=evidence_score(
                estimate=512,
                lower=505,
                upper=518,
                scale=stats_pb2.BrainliftEvidenceScore.MCAT,
            ),
        )
    )

    dashboard = brainlift_dashboard(col)
    html = render_brainlift_html(dashboard)

    col.brainlift_score_snapshot.assert_called_once_with(DEFAULT_MCAT_TOPICS)
    assert [score.label for score in dashboard.scores] == [
        "Memory",
        "Performance",
        "Readiness",
    ]
    assert [score.value for score in dashboard.scores] == ["80%", "65%", "512"]
    assert "Memory" in html
    assert "Performance" in html
    assert "Readiness" in html
    assert "70-90%" in html
    assert "505-518" in html


def test_abstained_score_does_not_invent_a_value() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.return_value = (
        stats_pb2.BrainliftScoreSnapshotResponse(
            memory=stats_pb2.BrainliftEvidenceScore(
                availability=stats_pb2.BrainliftEvidenceScore.ABSTAINED,
                coverage=0.1,
                confidence=stats_pb2.BrainliftEvidenceScore.NONE,
                rated_reviews=0,
                reasons=["minimum_rated_reviews_not_met:10"],
            ),
        )
    )

    dashboard = brainlift_dashboard(col)

    assert dashboard.scores[0].available is False
    assert dashboard.scores[0].value == "Not enough evidence"
    assert "0/10" in anki.lang.without_unicode_isolation(dashboard.scores[0].detail)


def test_unvalidated_readiness_mapping_is_explained() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.return_value = (
        stats_pb2.BrainliftScoreSnapshotResponse(
            memory=evidence_score(estimate=0.8, lower=0.7, upper=0.9),
            performance=evidence_score(estimate=0.65, lower=0.5, upper=0.78),
            readiness=stats_pb2.BrainliftEvidenceScore(
                availability=stats_pb2.BrainliftEvidenceScore.ABSTAINED,
                scale=stats_pb2.BrainliftEvidenceScore.MCAT,
                coverage=0.25,
                confidence=stats_pb2.BrainliftEvidenceScore.NONE,
                reasons=[
                    "readiness_score_mapping_not_validated",
                    "joint_topic_coverage_below:0.6",
                ],
            ),
        )
    )

    dashboard = brainlift_dashboard(col)
    readiness = dashboard.scores[2]

    assert [score.value for score in dashboard.scores] == [
        "80%",
        "65%",
        "Not enough evidence",
    ]
    assert readiness.available is False
    assert readiness.interval == ""
    assert readiness.confidence == "none"
    assert anki.lang.without_unicode_isolation(readiness.detail) == (
        "Readiness score mapping has not been validated"
        " · Waiting for joint topic coverage (25%/60%)"
    )


def test_all_readiness_abstention_reasons_are_explained() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.return_value = (
        stats_pb2.BrainliftScoreSnapshotResponse(
            readiness=stats_pb2.BrainliftEvidenceScore(
                availability=stats_pb2.BrainliftEvidenceScore.ABSTAINED,
                coverage=0.25,
                confidence=stats_pb2.BrainliftEvidenceScore.NONE,
                reasons=[
                    "readiness_score_mapping_not_validated",
                    "memory_unavailable",
                    "performance_unavailable",
                    "joint_topic_coverage_below:0.6",
                ],
            ),
        )
    )

    readiness = brainlift_dashboard(col).scores[2]

    assert anki.lang.without_unicode_isolation(readiness.detail) == (
        "Readiness score mapping has not been validated"
        " · Waiting for Memory evidence"
        " · Waiting for held-out Performance evidence"
        " · Waiting for joint topic coverage (25%/60%)"
    )


def test_backend_error_returns_safe_fallback() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.side_effect = RuntimeError("database details")

    dashboard = brainlift_dashboard(col)
    html = render_brainlift_html(dashboard)

    assert dashboard.backend_unavailable is True
    assert all(not score.available for score in dashboard.scores)
    assert "Evidence temporarily unavailable" in html
    assert "database details" not in html


def test_second_query_reflects_review_update() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.side_effect = [
        stats_pb2.BrainliftScoreSnapshotResponse(
            memory=evidence_score(estimate=0.7, lower=0.6, upper=0.8)
        ),
        stats_pb2.BrainliftScoreSnapshotResponse(
            memory=evidence_score(estimate=0.8, lower=0.7, upper=0.9)
        ),
    ]

    before_review = brainlift_dashboard(col)
    after_review = brainlift_dashboard(col)

    assert before_review.scores[0].value == "70%"
    assert after_review.scores[0].value == "80%"
    assert col.brainlift_score_snapshot.call_count == 2


def test_deck_browser_renders_evidence_with_existing_stats() -> None:
    col = MagicMock()
    col.brainlift_score_snapshot.return_value = (
        stats_pb2.BrainliftScoreSnapshotResponse(
            memory=evidence_score(estimate=0.8, lower=0.7, upper=0.9)
        )
    )
    deck_browser = DeckBrowser.__new__(DeckBrowser)
    deck_browser._render_data = MagicMock(
        brainlift=brainlift_dashboard(col),
        studied_today="5 cards studied today",
    )

    rendered = deck_browser._renderStats()

    assert "Brainlift evidence" in rendered
    assert "Memory" in rendered
    assert "5 cards studied today" in rendered


def test_reviewer_refreshes_evidence_after_successful_answer(
    monkeypatch,
) -> None:
    reviewer = Reviewer.__new__(Reviewer)
    reviewer.card = MagicMock(id=123)
    reviewer._answeredIds = []
    reviewer._refresh_brainlift_evidence = MagicMock()
    reviewer.check_timebox = MagicMock(return_value=True)
    did_answer = MagicMock()
    monkeypatch.setattr("aqt.reviewer.gui_hooks.reviewer_did_answer_card", did_answer)

    reviewer._after_answering(3)

    did_answer.assert_called_once_with(reviewer, reviewer.card, 3)
    reviewer._refresh_brainlift_evidence.assert_called_once_with()
    assert reviewer._answeredIds == [123]
