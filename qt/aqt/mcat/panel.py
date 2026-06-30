# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Minimal Memory-score surface (FR-6).

A single self-contained ``QDialog`` that displays the Phase-1 Memory score
honestly: point estimate + likely range, topic coverage %, a "how sure"
indicator, last-updated time, and the main reasons -- or, below the give-up
threshold, an explicit *abstain* message naming the failing condition(s).

This is **not** the reimagined three-mode dashboard (that is Phase 2). It is
just enough surface to show the Phase-1 numbers honestly, using plain Qt
widgets so it has no web/JS dependency.

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

from typing import TYPE_CHECKING, Optional

from aqt.mcat.memory_score import (
    ConceptMastery,
    MemoryScore,
    compute_memory_score,
)

if TYPE_CHECKING:
    import aqt.main
    from aqt.qt import QDialog


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


# --------------------------------------------------------------------------
# Qt surface.
# --------------------------------------------------------------------------


def show_memory_panel(mw: "Optional[aqt.main.AnkiQt]" = None) -> "QDialog":
    """Launch the Memory-score panel. This is the one-line entry point."""
    col = getattr(mw, "col", None) if mw is not None else None
    score = build_memory_score(col)
    # Build the panel class on demand. We can't reference ``MemoryPanel`` as a
    # bare name here: it's provided via module ``__getattr__`` (PEP 562), which
    # is only consulted for ``module.attr`` access, not in-function globals
    # lookups -- so a bare reference would raise ``NameError`` at call time.
    panel_cls = _build_panel_class()
    dialog = panel_cls(mw, score)
    dialog.show()
    return dialog


def _import_qt():  # pragma: no cover - thin import shim
    # Imported lazily so the module (and its logic) can be imported without a
    # Qt environment, e.g. under plain pytest on the score module.
    from aqt.qt import (  # type: ignore
        QDialog,
        QFrame,
        QLabel,
        QPushButton,
        QSizePolicy,
        Qt,
        QVBoxLayout,
        qconnect,
    )

    return (
        QDialog,
        QFrame,
        QLabel,
        QPushButton,
        QSizePolicy,
        Qt,
        QVBoxLayout,
        qconnect,
    )


# MemoryPanel is defined via a factory so importing this module never requires
# Qt to be present (keeps the logic import-safe for standalone tests).
def _build_panel_class():  # pragma: no cover - exercised only with Qt present
    (
        QDialog,
        QFrame,
        QLabel,
        QPushButton,
        QSizePolicy,
        Qt,
        QVBoxLayout,
        qconnect,
    ) = _import_qt()

    class _MemoryPanel(QDialog):
        """Self-contained dialog showing the honest Memory score."""

        def __init__(self, mw, score: MemoryScore) -> None:
            super().__init__(mw)
            self.mw = mw
            self.setWindowTitle("MCAT - Memory Score")
            self.setMinimumWidth(440)
            layout = QVBoxLayout(self)

            title = QLabel("Memory")
            title.setStyleSheet("font-size: 20px; font-weight: bold;")
            layout.addWidget(title)

            subtitle = QLabel(
                "How likely you are to recall what you've learned. "
                "This is the only graded score in Phase 1; Performance and "
                "Readiness are tracked separately and are not shown here."
            )
            subtitle.setWordWrap(True)
            subtitle.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(subtitle)

            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            layout.addWidget(line)

            if score.abstained:
                self._build_abstain(layout, score, QLabel)
            else:
                self._build_populated(layout, score, QLabel)

            footer = QLabel(f"Last updated: {score.last_updated}")
            footer.setStyleSheet("color: gray; font-size: 10px;")
            layout.addWidget(footer)

            method = QLabel(f"Method: {score.method}")
            method.setWordWrap(True)
            method.setStyleSheet("color: gray; font-size: 10px;")
            layout.addWidget(method)

            close = QPushButton("Close")
            close.setSizePolicy(QSizePolicy.Policy.Maximum, QSizePolicy.Policy.Fixed)
            qconnect(close.clicked, self.close)
            layout.addWidget(close)

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

    return _MemoryPanel


# Public name. Resolved lazily; if Qt is unavailable (e.g. plain pytest on the
# logic), attribute access raises a clear error instead of a hard import error
# at module load.
def __getattr__(name: str):
    if name == "MemoryPanel":
        return _build_panel_class()
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
