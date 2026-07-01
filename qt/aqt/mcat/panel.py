# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Prediction & review-recommendations surface (the kernel's output).

A single self-contained ``QDialog`` that shows the kernel's three separate,
never-blended outputs, top to bottom:

  * **Readiness** -- a projected MCAT score (472-528) with a range and
    confidence, driven by ingested practice-test performance;
  * **Memory** -- honest FSRS card-recall (point estimate + likely range, a
    "how sure" indicator, reasons), or an *abstain* message below the give-up
    threshold;
  * **Need-to-Review (NTR)** -- the per-concept recommendations chart that says
    what to study next, blending card recall with ingested-test performance.

This is the kernel's recommendation/prediction view -- not an all-in-one
dashboard. It uses plain Qt widgets so it has no web/JS dependency, and the
student feeds it by ingesting practice tests (:mod:`aqt.mcat.ingest`).

----------------------------------------------------------------------------
Wiring (orchestrator: add ONE line to a shared aqt file; not done here so we
don't edit files we don't own):

    In ``qt/aqt/main.py``'s ``setupMenus`` (or wherever the Tools menu is
    assembled), add:

        from aqt.mcat.panel import show_memory_panel
        qconnect(self.form.actionMemoryScore.triggered,
                 lambda: show_memory_panel(self))

    or, with no .ui change, register an action directly:

        from aqt.qt import QAction, qconnect
        from aqt.mcat.panel import show_memory_panel
        act = QAction("MCAT Memory Score", mw)
        qconnect(act.triggered, lambda: show_memory_panel(mw))
        mw.form.menuTools.addAction(act)

    A single call ``show_memory_panel(mw)`` launches the panel.
----------------------------------------------------------------------------

The live backend call is isolated behind :func:`_fetch_concept_mastery`. While
the Rust ``ConceptMastery`` RPC and the FR-2 coverage module are still being
built concurrently, this falls back to a small fixture so the panel runs and
is testable standalone. The orchestrator swaps in the real calls at
integration -- only the body of ``_fetch_concept_mastery`` /
``_fetch_coverage_pct`` / ``_fetch_graded_reviews`` needs to change.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Optional

from aqt.mcat.memory_score import (
    ConceptMastery,
    MemoryScore,
    compute_memory_score,
)
from aqt.mcat.readiness import ReadinessScore, compute_readiness

if TYPE_CHECKING:
    import aqt.main
    from aqt.qt import QDialog


@dataclass(frozen=True)
class NtrRowView:
    """The per-concept NTR numbers the diagram renders (display-only mirror of
    :class:`aqt.mcat.integration.NtrRow`, kept here so this module imports
    without a built backend)."""

    concept_id: str
    name: str
    topic_weight: float
    avg_recall: float
    cards_total: int
    questions_total: int
    questions_correct: int
    question_accuracy: float
    ntr: float


# --------------------------------------------------------------------------
# Backend boundary. Everything that touches the collection/RPC lives here so
# the rest of the panel (and the score logic) stays pure and testable.
# --------------------------------------------------------------------------

# Fixture used when the real backend isn't available yet. Numbers are chosen
# to be above the give-up threshold so the populated state is demonstrable.
_FIXTURE_MASTERY: list[ConceptMastery] = [
    ConceptMastery("Amino acids, peptides, proteins", 420, 300, 0.84),
    ConceptMastery("Enzymes and metabolism", 380, 240, 0.79),
    ConceptMastery("Cellular respiration", 260, 150, 0.72),
    ConceptMastery("Genetics and heredity", 310, 210, 0.81),
    ConceptMastery("Acids, bases, equilibria", 290, 160, 0.74),
    ConceptMastery("Behavioral sciences: learning", 180, 90, 0.68),
]
_FIXTURE_CONCEPTS_TOTAL = 10
_FIXTURE_GRADED_REVIEWS = 2400
_FIXTURE_COVERAGE_PCT = 62.0

# Fixture NTR breakdown for the dev/no-collection case, so the diagram renders
# standalone. ntr = topic_weight * weakness, where weakness blends card recall
# (1 - avg_recall) and question error rate by evidence; here precomputed.
_FIXTURE_NTR: list[NtrRowView] = [
    NtrRowView("6A", "Sensing the environment", 1.0, 0.62, 180, 10, 3, 0.30, 0.62),
    NtrRowView("1D", "Bioenergetics and metabolism", 1.3, 0.74, 260, 10, 7, 0.70, 0.46),
    NtrRowView("5A", "Water and its solutions", 1.1, 0.79, 290, 0, 0, 0.0, 0.23),
    NtrRowView("1A", "Proteins and amino acids", 1.3, 0.84, 420, 12, 10, 0.83, 0.20),
    NtrRowView("4C", "Circuits and electrochemistry", 1.0, 0.88, 150, 8, 8, 1.0, 0.10),
]


def _fetch_score_inputs(
    col: object | None,
) -> tuple[list[ConceptMastery], int, float, int]:
    """Return ``(mastery rows, taxonomy size K, coverage %, graded reviews)``.

    Wired (FR-2 taxonomy + FR-3 ConceptMastery RPC) via :mod:`aqt.mcat.integration`.
    Coverage % is derived from the same RPC result as mastery, so they can never
    disagree. With no collection -- or if the wiring isn't available (e.g. the
    taxonomy file is missing, or the RPC hasn't been built) -- falls back to the
    fixture so the surface still renders honestly during development.
    """
    if col is None:
        return (
            list(_FIXTURE_MASTERY),
            _FIXTURE_CONCEPTS_TOTAL,
            _FIXTURE_COVERAGE_PCT,
            _FIXTURE_GRADED_REVIEWS,
        )
    try:
        from aqt.mcat import integration

        mastery, concepts_total = integration.fetch_mastery(col)
        coverage = integration.coverage_pct_from_mastery(mastery, concepts_total)
        graded = integration.fetch_graded_reviews(col)
        return mastery, concepts_total, coverage, graded
    except Exception:
        return (
            list(_FIXTURE_MASTERY),
            _FIXTURE_CONCEPTS_TOTAL,
            _FIXTURE_COVERAGE_PCT,
            _FIXTURE_GRADED_REVIEWS,
        )


def build_memory_score(col: object | None = None) -> MemoryScore:
    """Gather inputs from the backend boundary and compute the Memory score.

    Pure aside from :func:`_fetch_score_inputs`, so it is easy to test by
    passing ``col=None`` (fixture) or by monkeypatching the fetcher.
    """
    mastery, concepts_total, coverage, graded = _fetch_score_inputs(col)
    return compute_memory_score(
        mastery=mastery,
        graded_reviews=graded,
        topic_coverage_pct=coverage,
        concepts_total=concepts_total,
    )


def build_ntr_breakdown(col: object | None = None) -> list[NtrRowView]:
    """Gather the per-concept NTR breakdown for the diagram.

    Unlike the Memory score, NTR has no give-up threshold -- it is always
    informative -- so this returns rows whenever any concept has evidence.
    Falls back to the fixture when there is no collection or the wiring isn't
    available yet.
    """
    if col is None:
        return list(_FIXTURE_NTR)
    try:
        from aqt.mcat import integration

        rows = integration.fetch_ntr(col)
        if not rows:
            return []
        return [
            NtrRowView(
                concept_id=r.concept_id,
                name=r.name,
                topic_weight=r.topic_weight,
                avg_recall=r.avg_recall,
                cards_total=r.cards_total,
                questions_total=r.questions_total,
                questions_correct=r.questions_correct,
                question_accuracy=r.question_accuracy,
                ntr=r.ntr,
            )
            for r in rows
        ]
    except Exception:
        return list(_FIXTURE_NTR)


def build_readiness(score: MemoryScore, ntr_rows: list[NtrRowView]) -> ReadinessScore:
    """Derive the Readiness (Projected MCAT) score from already-fetched inputs.

    Pure: it reuses the NTR rows (which carry per-concept question stats and are
    sorted highest-NTR-first, so row 0 is the "best next thing to study") and the
    Memory score's coverage/taxonomy-size fields. No extra backend calls, and no
    blending -- readiness is computed independently of the Memory number.
    """
    attempts = sum(r.questions_total for r in ntr_rows)
    correct = sum(r.questions_correct for r in ntr_rows)
    concepts_with_q = sum(1 for r in ntr_rows if r.questions_total > 0)
    best_next = (ntr_rows[0].concept_id, ntr_rows[0].name) if ntr_rows else None
    return compute_readiness(
        question_attempts=attempts,
        question_correct=correct,
        concepts_with_questions=concepts_with_q,
        topic_coverage_pct=score.topic_coverage_pct,
        concepts_total=score.concepts_total,
        best_next=best_next,
    )


# --------------------------------------------------------------------------
# Qt surface.
# --------------------------------------------------------------------------


def show_memory_panel(mw: "Optional[aqt.main.AnkiQt]" = None) -> "QDialog":
    """Launch the scores panel (Readiness + Memory + NTR). One-line entry point."""
    col = getattr(mw, "col", None) if mw is not None else None
    score = build_memory_score(col)
    ntr_rows = build_ntr_breakdown(col)
    readiness = build_readiness(score, ntr_rows)
    # Build the panel class on demand. We can't reference ``MemoryPanel`` as a
    # bare name here: it's provided via module ``__getattr__`` (PEP 562), which
    # is only consulted for ``module.attr`` access, not in-function globals
    # lookups -- so a bare reference would raise ``NameError`` at call time.
    panel_cls = _build_panel_class()
    dialog = panel_cls(mw, score, readiness, ntr_rows)
    dialog.show()
    return dialog


def _import_qt():  # pragma: no cover - thin import shim
    # Imported lazily so the module (and its logic) can be imported without a
    # Qt environment, e.g. under plain pytest on the score module.
    from aqt.qt import (  # type: ignore
        QDialog,
        QFrame,
        QLabel,
        QProgressBar,
        QPushButton,
        QScrollArea,
        QSizePolicy,
        Qt,
        QVBoxLayout,
        QWidget,
        qconnect,
    )

    return (
        QDialog,
        QFrame,
        QLabel,
        QProgressBar,
        QPushButton,
        QScrollArea,
        QSizePolicy,
        Qt,
        QVBoxLayout,
        QWidget,
        qconnect,
    )


# MemoryPanel is defined via a factory so importing this module never requires
# Qt to be present (keeps the logic import-safe for standalone tests).
def _build_panel_class():  # pragma: no cover - exercised only with Qt present
    (
        QDialog,
        QFrame,
        QLabel,
        QProgressBar,
        QPushButton,
        QScrollArea,
        QSizePolicy,
        Qt,
        QVBoxLayout,
        QWidget,
        qconnect,
    ) = _import_qt()

    class _MemoryPanel(QDialog):  # type: ignore[misc, valid-type]
        """Self-contained dialog: Readiness (projected MCAT) + Memory + NTR."""

        def __init__(
            self, mw, score: MemoryScore, readiness=None, ntr_rows=None
        ) -> None:
            super().__init__(mw)
            self.mw = mw
            self.setWindowTitle("MCAT - Prediction & Scores")
            self.setMinimumWidth(480)
            self.resize(500, 720)
            outer = QVBoxLayout(self)

            # Put everything in a scroll area so the full report stays reachable
            # when it is taller than the window.
            scroll = QScrollArea()
            scroll.setWidgetResizable(True)
            scroll.setFrameShape(QFrame.Shape.NoFrame)
            outer.addWidget(scroll)
            content = QWidget()
            scroll.setWidget(content)
            layout = QVBoxLayout(content)

            title = QLabel("MCAT prediction")
            title.setStyleSheet("font-size: 20px; font-weight: bold;")
            layout.addWidget(title)

            subtitle = QLabel(
                "Three separate scores, never blended: Readiness is a projected "
                "MCAT score from your ingested practice tests, Memory is fact "
                "recall, and the concept map shows what to review next."
            )
            subtitle.setWordWrap(True)
            subtitle.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(subtitle)

            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            layout.addWidget(line)

            # 1) Readiness -- the headline projected MCAT score.
            if readiness is not None:
                self._build_readiness_section(layout, readiness, QLabel, QFrame)

            # 2) Memory -- fact recall, computed independently.
            mem_head = QLabel("Memory (fact recall)")
            mem_head.setStyleSheet("font-size: 15px; font-weight: bold;")
            layout.addWidget(mem_head)
            if score.abstained:
                self._build_abstain(layout, score, QLabel)
            else:
                self._build_populated(layout, score, QLabel)

            # 3) NTR breakdown diagram.
            self._build_ntr_section(
                layout, ntr_rows or [], QLabel, QFrame, QProgressBar
            )

            footer = QLabel(f"Last updated: {score.last_updated}")
            footer.setStyleSheet("color: gray; font-size: 10px;")
            layout.addWidget(footer)

            method = QLabel(f"Method: {score.method}")
            method.setWordWrap(True)
            method.setStyleSheet("color: gray; font-size: 10px;")
            layout.addWidget(method)
            layout.addStretch(1)

            # Actions stay pinned below the scroll area, always visible.
            ingest = QPushButton("Ingest a practice test")
            ingest.setSizePolicy(QSizePolicy.Policy.Maximum, QSizePolicy.Policy.Fixed)
            qconnect(ingest.clicked, self._go_ingest)
            outer.addWidget(ingest)

            close = QPushButton("Close")
            close.setSizePolicy(QSizePolicy.Policy.Maximum, QSizePolicy.Policy.Fixed)
            qconnect(close.clicked, self.close)
            outer.addWidget(close)

        def _go_ingest(self) -> None:
            from aqt.mcat.ingest import show_ingest

            show_ingest(self.mw)

        @staticmethod
        def _build_readiness_section(layout, r: ReadinessScore, QLabel, QFrame) -> None:
            """The headline Readiness (Projected MCAT) score with honesty fields."""
            head = QLabel("Readiness - projected MCAT score")
            head.setStyleSheet("font-size: 15px; font-weight: bold;")
            layout.addWidget(head)

            if r.abstained:
                big = QLabel("No projection yet")
                big.setStyleSheet("font-size: 22px; font-weight: bold; color: #c0392b;")
                layout.addWidget(big)
                why = QLabel(
                    "The app won't project a score until you've ingested enough "
                    "practice-test evidence to back it up:"
                )
                why.setWordWrap(True)
                layout.addWidget(why)
                for reason in r.reasons:
                    item = QLabel(f"  - {reason}")
                    item.setWordWrap(True)
                    layout.addWidget(item)
            else:
                big = QLabel(f"Projected MCAT: {r.projected}")
                big.setStyleSheet("font-size: 34px; font-weight: bold;")
                layout.addWidget(big)

                rng = QLabel(f"Likely range: {r.range_low} to {r.range_high} (472-528)")
                rng.setStyleSheet("font-size: 14px;")
                layout.addWidget(rng)

                sure = QLabel(f"Confidence: {r.confidence}")
                sure.setStyleSheet("font-size: 13px;")
                layout.addWidget(sure)

                perf = QLabel(
                    f"Performance (ingested practice tests): {r.performance_pct:.0f}% "
                    f"correct over {r.question_attempts} questions"
                )
                perf.setWordWrap(True)
                perf.setStyleSheet("font-size: 13px;")
                layout.addWidget(perf)

                if r.best_next_id:
                    nxt = QLabel(
                        f"Best next thing to study: {r.best_next_id} - {r.best_next_name}"
                    )
                    nxt.setWordWrap(True)
                    nxt.setStyleSheet("font-size: 13px; font-weight: bold;")
                    layout.addWidget(nxt)

                reasons_title = QLabel("Why:")
                reasons_title.setStyleSheet("font-weight: bold; margin-top: 6px;")
                layout.addWidget(reasons_title)
                for reason in r.reasons:
                    item = QLabel(f"  - {reason}")
                    item.setWordWrap(True)
                    layout.addWidget(item)

            disc = QLabel(r.disclaimer)
            disc.setWordWrap(True)
            disc.setStyleSheet("color: #c0392b; font-size: 10px; margin-top: 4px;")
            layout.addWidget(disc)

            sep = QFrame()
            sep.setFrameShape(QFrame.Shape.HLine)
            layout.addWidget(sep)

        @staticmethod
        def _build_abstain(layout, score: MemoryScore, QLabel) -> None:
            big = QLabel("No score yet")
            big.setStyleSheet("font-size: 22px; font-weight: bold; color: #c0392b;")
            layout.addWidget(big)

            why = QLabel(
                "The app won't show a Memory score until it has enough data "
                "to back it up:"
            )
            why.setWordWrap(True)
            layout.addWidget(why)

            for reason in score.reasons:
                item = QLabel(f"  - {reason}")
                item.setWordWrap(True)
                layout.addWidget(item)

            ctx = QLabel(
                f"Topic coverage: {score.topic_coverage_pct:.0f}%   "
                f"Graded reviews: {score.graded_reviews}   "
                f"Cards with memory state: {score.cards_contributing}"
            )
            ctx.setStyleSheet("color: gray; font-size: 11px;")
            ctx.setWordWrap(True)
            layout.addWidget(ctx)

        @staticmethod
        def _build_populated(layout, score: MemoryScore, QLabel) -> None:
            assert score.point_estimate_pct is not None
            big = QLabel(f"{score.point_estimate_pct:.0f}%")
            big.setStyleSheet("font-size: 40px; font-weight: bold;")
            layout.addWidget(big)

            rng = QLabel(
                f"Likely range: {score.range_low_pct:.0f}% to "
                f"{score.range_high_pct:.0f}%"
            )
            rng.setStyleSheet("font-size: 14px;")
            layout.addWidget(rng)

            sure = QLabel(f"How sure: {score.how_sure}")
            sure.setStyleSheet("font-size: 13px;")
            layout.addWidget(sure)

            cov = QLabel(f"Topic coverage: {score.topic_coverage_pct:.0f}%")
            cov.setStyleSheet("font-size: 13px;")
            layout.addWidget(cov)

            reasons_title = QLabel("Why:")
            reasons_title.setStyleSheet("font-weight: bold; margin-top: 6px;")
            layout.addWidget(reasons_title)
            for reason in score.reasons:
                item = QLabel(f"  - {reason}")
                item.setWordWrap(True)
                layout.addWidget(item)

        # Top concepts to chart, so the panel stays compact.
        _NTR_TOP_N = 8

        def _build_ntr_section(
            self, layout, rows, QLabel, QFrame, QProgressBar
        ) -> None:
            """Per-concept NTR diagram + the numbers behind each bar.

            NTR is a separate engine signal from the Memory score above: it
            drives *what to review next*, and -- unlike Memory -- it folds in
            ingested practice-test performance. Shown here so the student can see
            which concepts the engine will prioritise, and exactly why.
            """
            if not rows:
                return

            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            layout.addWidget(line)

            heading = QLabel("Need-to-Review (NTR) by concept")
            heading.setStyleSheet("font-size: 15px; font-weight: bold;")
            layout.addWidget(heading)

            explain = QLabel(
                "NTR = topic weight x weakness. Weakness blends how much you "
                "forget on cards (1 - recall) with how often you missed the "
                "concept on your ingested practice tests, weighted by how much "
                "evidence each side has. Higher NTR = reviewed sooner. NTR does "
                "not feed the Memory score above; it only orders review."
            )
            explain.setWordWrap(True)
            explain.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(explain)

            shown = rows[: self._NTR_TOP_N]
            max_ntr = max((r.ntr for r in shown), default=0.0) or 1.0

            for r in shown:
                label = QLabel(f"{r.concept_id} - {r.name}")
                label.setWordWrap(True)
                label.setStyleSheet("font-size: 12px; font-weight: bold;")
                layout.addWidget(label)

                bar = QProgressBar()
                bar.setRange(0, 1000)
                bar.setValue(int(round(r.ntr / max_ntr * 1000)))
                bar.setFormat(f"NTR {r.ntr:.2f}")
                bar.setTextVisible(True)
                # Red for the most urgent, easing toward green as NTR falls.
                frac = r.ntr / max_ntr
                colour = _ntr_colour(frac)
                bar.setStyleSheet(
                    "QProgressBar { border: 1px solid #bbb; border-radius: 3px; "
                    "text-align: center; height: 16px; } "
                    f"QProgressBar::chunk {{ background-color: {colour}; }}"
                )
                layout.addWidget(bar)

                detail = QLabel(_ntr_detail_text(r))
                detail.setWordWrap(True)
                detail.setStyleSheet("color: gray; font-size: 10px;")
                layout.addWidget(detail)

            if len(rows) > self._NTR_TOP_N:
                more = QLabel(
                    f"... and {len(rows) - self._NTR_TOP_N} more concepts "
                    "with lower NTR."
                )
                more.setStyleSheet("color: gray; font-size: 10px;")
                layout.addWidget(more)

    return _MemoryPanel


def _ntr_colour(frac: float) -> str:
    """Map an NTR fraction in [0, 1] to a traffic-light colour (most urgent =
    red). Deterministic; purely cosmetic."""
    if frac >= 0.66:
        return "#c0392b"
    if frac >= 0.33:
        return "#e67e22"
    return "#27ae60"


def _ntr_detail_text(r: "NtrRowView") -> str:
    """The numbers behind one NTR bar, so the diagram shows its work."""
    parts = [f"weight x{r.topic_weight:.2g}"]
    if r.cards_total > 0:
        parts.append(f"card recall {r.avg_recall * 100:.0f}% ({r.cards_total} cards)")
    else:
        parts.append("no card data")
    if r.questions_total > 0:
        parts.append(
            f"questions {r.questions_correct}/{r.questions_total} "
            f"({r.question_accuracy * 100:.0f}%)"
        )
    else:
        parts.append("no questions yet")
    return "   ".join(parts) + f"   ->   NTR {r.ntr:.2f}"


# Public name. Resolved lazily; if Qt is unavailable (e.g. plain pytest on the
# logic), attribute access raises a clear error instead of a hard import error
# at module load.
def __getattr__(name: str):
    if name == "MemoryPanel":
        return _build_panel_class()
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
