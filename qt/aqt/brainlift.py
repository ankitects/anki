# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
from dataclasses import dataclass
from datetime import datetime, timezone

from anki import stats_pb2
from anki.collection import Collection
from aqt.utils import tr

DEFAULT_MCAT_TOPICS: tuple[tuple[str, str], ...] = (
    ("Biochemistry", "mcat::biochemistry"),
    ("Biology", "mcat::biology"),
    ("General Chemistry", "mcat::general-chemistry"),
    ("Organic Chemistry", "mcat::organic-chemistry"),
    ("Physics", "mcat::physics"),
    ("Psychology and Sociology", "mcat::psychology-sociology"),
    ("Critical Analysis and Reasoning", "mcat::cars"),
)


@dataclass(frozen=True)
class BrainliftScoreView:
    label: str
    available: bool
    value: str
    interval: str
    detail: str
    coverage: str
    confidence: str
    updated: str


@dataclass(frozen=True)
class BrainliftDashboard:
    scores: tuple[BrainliftScoreView, ...]
    backend_unavailable: bool = False


def brainlift_dashboard(col: Collection) -> BrainliftDashboard:
    """Load backend-owned evidence without allowing a stats failure to break study."""
    labels = _score_labels()
    try:
        snapshot = col.brainlift_score_snapshot(DEFAULT_MCAT_TOPICS)
    except Exception:
        return BrainliftDashboard(
            scores=tuple(_unavailable_score(label) for label in labels),
            backend_unavailable=True,
        )

    return BrainliftDashboard(
        scores=(
            _score_view(labels[0], snapshot.memory),
            _score_view(labels[1], snapshot.performance),
            _score_view(labels[2], snapshot.readiness),
        )
    )


def render_brainlift_html(dashboard: BrainliftDashboard) -> str:
    """Render a small table supported by both Anki webviews and Qt rich text."""
    subtitle = (
        tr.qt_misc_brainlift_evidence_unavailable()
        if dashboard.backend_unavailable
        else tr.qt_misc_brainlift_collection_wide_evidence()
    )
    cells = "".join(_render_score(score) for score in dashboard.scores)
    return f"""
<section id="brainlift-evidence" style="margin: 10px auto; max-width: 760px;">
  <div style="margin-bottom: 5px;">
    <strong>{html.escape(tr.qt_misc_brainlift_evidence())}</strong>
    <small style="opacity: 0.7;"> &middot; {html.escape(subtitle)}</small>
  </div>
  <table width="100%" cellspacing="0" cellpadding="7"
         style="border: 1px solid #aaa; border-radius: 6px;">
    <tr>{cells}</tr>
  </table>
</section>
""".strip()


def render_brainlift_loading_html() -> str:
    return (
        f"<strong>{html.escape(tr.qt_misc_brainlift_evidence())}</strong>"
        f" &middot; {html.escape(tr.qt_misc_brainlift_loading_evidence())}"
    )


def _score_view(
    label: str, score: stats_pb2.BrainliftEvidenceScore
) -> BrainliftScoreView:
    available = score.availability == stats_pb2.BrainliftEvidenceScore.AVAILABLE
    if not available:
        return BrainliftScoreView(
            label=label,
            available=False,
            value=tr.qt_misc_brainlift_not_enough_evidence(),
            interval="",
            detail=_abstention_detail(score),
            coverage=_percent(score.coverage),
            confidence=tr.qt_misc_brainlift_confidence_none(),
            updated=_updated_text(score.updated_at_secs),
        )

    is_mcat = score.scale == stats_pb2.BrainliftEvidenceScore.MCAT
    return BrainliftScoreView(
        label=label,
        available=True,
        value=_estimate(score.estimate, is_mcat),
        interval=_interval(score.range.lower, score.range.upper, is_mcat),
        detail=tr.qt_misc_brainlift_successful_reviews(
            successful=score.successful_reviews,
            rated=score.rated_reviews,
        ),
        coverage=_percent(score.coverage),
        confidence=_confidence_label(score.confidence),
        updated=_updated_text(score.updated_at_secs),
    )


def _unavailable_score(label: str) -> BrainliftScoreView:
    return BrainliftScoreView(
        label=label,
        available=False,
        value=tr.qt_misc_brainlift_evidence_unavailable(),
        interval="",
        detail=tr.qt_misc_brainlift_study_remains_available(),
        coverage="0%",
        confidence=tr.qt_misc_brainlift_confidence_none(),
        updated=tr.qt_misc_brainlift_no_update(),
    )


def _render_score(score: BrainliftScoreView) -> str:
    interval = (
        f"<br><small>{html.escape(tr.qt_misc_brainlift_range(range=score.interval))}</small>"
        if score.interval
        else ""
    )
    coverage = tr.qt_misc_brainlift_coverage_confidence(
        coverage=score.coverage,
        confidence=score.confidence,
    )
    return f"""
<td width="33%" valign="top" style="border-right: 1px solid #bbb;">
  <strong>{html.escape(score.label)}</strong><br>
  <span style="font-size: 1.15em;">{html.escape(score.value)}</span>
  {interval}<br>
  <small>{html.escape(score.detail)}</small><br>
  <small>{html.escape(coverage)}</small><br>
  <small>{html.escape(score.updated)}</small>
</td>
""".strip()


def _abstention_detail(score: stats_pb2.BrainliftEvidenceScore) -> str:
    for reason in score.reasons:
        if reason == "no_qualifying_reviews":
            return tr.qt_misc_brainlift_no_qualifying_reviews()
        if reason.startswith("minimum_rated_reviews_not_met:"):
            minimum = reason.partition(":")[2]
            return tr.qt_misc_brainlift_waiting_rated_reviews(
                rated=score.rated_reviews,
                minimum=minimum,
            )
        if reason.startswith("joint_topic_coverage_below:"):
            minimum = float(reason.partition(":")[2])
            return tr.qt_misc_brainlift_waiting_topic_coverage(
                coverage=_percent(score.coverage),
                minimum=_percent(minimum),
            )
        if reason == "memory_unavailable":
            return tr.qt_misc_brainlift_waiting_memory()
        if reason == "performance_unavailable":
            return tr.qt_misc_brainlift_waiting_performance()
    return tr.qt_misc_brainlift_waiting_evidence()


def _estimate(value: float, is_mcat: bool) -> str:
    return f"{value:.0f}" if is_mcat else _percent(value)


def _interval(lower: float, upper: float, is_mcat: bool) -> str:
    if is_mcat:
        return f"{lower:.0f}-{upper:.0f}"
    return f"{lower * 100:.0f}-{upper * 100:.0f}%"


def _percent(value: float) -> str:
    return f"{value * 100:.0f}%"


def _updated_text(updated_at_secs: int) -> str:
    if not updated_at_secs:
        return tr.qt_misc_brainlift_no_rated_reviews()
    updated = datetime.fromtimestamp(updated_at_secs, tz=timezone.utc)
    return tr.qt_misc_brainlift_updated(datetime=f"{updated:%Y-%m-%d %H:%M}")


def _score_labels() -> tuple[str, str, str]:
    return (
        tr.qt_misc_brainlift_memory(),
        tr.qt_misc_brainlift_performance(),
        tr.qt_misc_brainlift_readiness(),
    )


def _confidence_label(confidence: int) -> str:
    return {
        stats_pb2.BrainliftEvidenceScore.LOW: tr.qt_misc_brainlift_confidence_low(),
        stats_pb2.BrainliftEvidenceScore.MEDIUM: tr.qt_misc_brainlift_confidence_medium(),
        stats_pb2.BrainliftEvidenceScore.HIGH: tr.qt_misc_brainlift_confidence_high(),
    }.get(confidence, tr.qt_misc_brainlift_confidence_none())
