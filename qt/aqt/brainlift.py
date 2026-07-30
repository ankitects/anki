# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
from dataclasses import dataclass
from datetime import datetime, timezone

from anki import stats_pb2
from anki.collection import Collection

DEFAULT_MCAT_TOPICS: tuple[tuple[str, str], ...] = (
    ("Biochemistry", "mcat::biochemistry"),
    ("Biology", "mcat::biology"),
    ("General Chemistry", "mcat::general-chemistry"),
    ("Organic Chemistry", "mcat::organic-chemistry"),
    ("Physics", "mcat::physics"),
    ("Psychology and Sociology", "mcat::psychology-sociology"),
    ("Critical Analysis and Reasoning", "mcat::cars"),
)

_SCORE_LABELS = ("Memory", "Performance", "Readiness")
_CONFIDENCE_LABELS = {
    stats_pb2.BrainliftEvidenceScore.NONE: "none",
    stats_pb2.BrainliftEvidenceScore.LOW: "low",
    stats_pb2.BrainliftEvidenceScore.MEDIUM: "medium",
    stats_pb2.BrainliftEvidenceScore.HIGH: "high",
}


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
    try:
        snapshot = col.brainlift_score_snapshot(DEFAULT_MCAT_TOPICS)
    except Exception:
        return BrainliftDashboard(
            scores=tuple(_unavailable_score(label) for label in _SCORE_LABELS),
            backend_unavailable=True,
        )

    return BrainliftDashboard(
        scores=(
            _score_view("Memory", snapshot.memory),
            _score_view("Performance", snapshot.performance),
            _score_view("Readiness", snapshot.readiness),
        )
    )


def render_brainlift_html(dashboard: BrainliftDashboard) -> str:
    """Render a small table supported by both Anki webviews and Qt rich text."""
    subtitle = (
        "Evidence temporarily unavailable"
        if dashboard.backend_unavailable
        else "Collection-wide evidence; scores stay separate"
    )
    cells = "".join(_render_score(score) for score in dashboard.scores)
    return f"""
<section id="brainlift-evidence" style="margin: 10px auto; max-width: 760px;">
  <div style="margin-bottom: 5px;">
    <strong>Brainlift evidence</strong>
    <small style="opacity: 0.7;"> &middot; {html.escape(subtitle)}</small>
  </div>
  <table width="100%" cellspacing="0" cellpadding="7"
         style="border: 1px solid #aaa; border-radius: 6px;">
    <tr>{cells}</tr>
  </table>
</section>
""".strip()


def _score_view(
    label: str, score: stats_pb2.BrainliftEvidenceScore
) -> BrainliftScoreView:
    available = score.availability == stats_pb2.BrainliftEvidenceScore.AVAILABLE
    if not available:
        return BrainliftScoreView(
            label=label,
            available=False,
            value="Not enough evidence",
            interval="",
            detail=_abstention_detail(score),
            coverage=_percent(score.coverage),
            confidence="none",
            updated=_updated_text(score.updated_at_secs),
        )

    is_mcat = score.scale == stats_pb2.BrainliftEvidenceScore.MCAT
    return BrainliftScoreView(
        label=label,
        available=True,
        value=_estimate(score.estimate, is_mcat),
        interval=_interval(score.range.lower, score.range.upper, is_mcat),
        detail=f"{score.successful_reviews}/{score.rated_reviews} successful reviews",
        coverage=_percent(score.coverage),
        confidence=_CONFIDENCE_LABELS.get(score.confidence, "none"),
        updated=_updated_text(score.updated_at_secs),
    )


def _unavailable_score(label: str) -> BrainliftScoreView:
    return BrainliftScoreView(
        label=label,
        available=False,
        value="Evidence temporarily unavailable",
        interval="",
        detail="Study and review remain available.",
        coverage="0%",
        confidence="none",
        updated="No update",
    )


def _render_score(score: BrainliftScoreView) -> str:
    interval = (
        f"<br><small>Range {html.escape(score.interval)}</small>"
        if score.interval
        else ""
    )
    return f"""
<td width="33%" valign="top" style="border-right: 1px solid #bbb;">
  <strong>{html.escape(score.label)}</strong><br>
  <span style="font-size: 1.15em;">{html.escape(score.value)}</span>
  {interval}<br>
  <small>{html.escape(score.detail)}</small><br>
  <small>Coverage {html.escape(score.coverage)} &middot;
  Confidence {html.escape(score.confidence)}</small><br>
  <small>{html.escape(score.updated)}</small>
</td>
""".strip()


def _abstention_detail(score: stats_pb2.BrainliftEvidenceScore) -> str:
    for reason in score.reasons:
        if reason == "no_qualifying_reviews":
            return "No qualifying rated reviews yet"
        if reason.startswith("minimum_rated_reviews_not_met:"):
            minimum = reason.partition(":")[2]
            return f"Waiting for rated reviews ({score.rated_reviews}/{minimum})"
        if reason.startswith("joint_topic_coverage_below:"):
            minimum = float(reason.partition(":")[2])
            return (
                f"Waiting for joint topic coverage "
                f"({_percent(score.coverage)}/{_percent(minimum)})"
            )
        if reason == "memory_unavailable":
            return "Waiting for Memory evidence"
        if reason == "performance_unavailable":
            return "Waiting for held-out Performance evidence"
    return "Waiting for enough rated review evidence"


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
        return "No rated reviews yet"
    updated = datetime.fromtimestamp(updated_at_secs, tz=timezone.utc)
    return f"Updated {updated:%Y-%m-%d %H:%M} UTC"
