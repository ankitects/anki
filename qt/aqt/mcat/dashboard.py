# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Minimal MCAT dashboard -- a hub with two redirects (Phase 1).

The full reimagined three-mode dashboard (Learn / Test / Flashcards) is Phase 2.
This is the small Phase-1 stand-in: a single screen that routes to the two
study surfaces that exist today --

  * **Flashcards** -> the original Anki deck browser (spaced-repetition review),
  * **Practice Questions** -> the concept-coded quiz that feeds NTR.

It is intentionally a thin launcher: no scores are computed here (the honest
Memory score + NTR diagram live in :mod:`aqt.mcat.panel`). Plain Qt widgets, no
web/JS, no AI.

Wiring (one line, added in ``qt/aqt/main.py`` alongside the other MCAT actions):

    from aqt.mcat.dashboard import show_dashboard
    show_dashboard(mw)
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    import aqt.main
    from aqt.qt import QDialog


def show_dashboard(mw: "Optional[aqt.main.AnkiQt]" = None) -> "QDialog":
    """Launch the MCAT dashboard. The one-line entry point."""
    dialog_cls = _build_dashboard_class()
    dialog = dialog_cls(mw)
    dialog.show()
    return dialog


def _import_qt():  # pragma: no cover - thin import shim
    from aqt.qt import (  # type: ignore
        QDialog,
        QFrame,
        QLabel,
        QPushButton,
        QVBoxLayout,
        qconnect,
    )

    return (QDialog, QFrame, QLabel, QPushButton, QVBoxLayout, qconnect)


def _build_dashboard_class():  # pragma: no cover - exercised only with Qt present
    (QDialog, QFrame, QLabel, QPushButton, QVBoxLayout, qconnect) = _import_qt()

    class _Dashboard(QDialog):  # type: ignore[misc, valid-type]
        """A two-redirect hub: Flashcards and Practice Questions."""

        def __init__(self, mw) -> None:
            super().__init__(mw)
            self.mw = mw
            self.setWindowTitle("MCAT - Dashboard")
            self.setMinimumWidth(420)
            layout = QVBoxLayout(self)

            title = QLabel("MCAT Dashboard")
            title.setStyleSheet("font-size: 20px; font-weight: bold;")
            layout.addWidget(title)

            subtitle = QLabel("Choose what to study.")
            subtitle.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(subtitle)

            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            layout.addWidget(line)

            self._add_redirect(
                layout,
                QPushButton,
                QLabel,
                qconnect,
                "Flashcards",
                "Spaced-repetition review (the Anki deck browser).",
                self._go_flashcards,
            )
            self._add_redirect(
                layout,
                QPushButton,
                QLabel,
                qconnect,
                "Practice Questions",
                "Concept-coded questions that update your Need-to-Review (NTR).",
                self._go_questions,
            )
            self._add_redirect(
                layout,
                QPushButton,
                QLabel,
                qconnect,
                "Memory & NTR",
                "Your honest Memory score and the per-concept NTR breakdown.",
                self._go_memory,
            )

            close = QPushButton("Close")
            qconnect(close.clicked, self.close)
            layout.addWidget(close)

        @staticmethod
        def _add_redirect(
            layout, QPushButton, QLabel, qconnect, label, blurb, handler
        ) -> None:
            btn = QPushButton(label)
            btn.setStyleSheet(
                "font-size: 15px; font-weight: bold; padding: 10px; margin-top: 8px;"
            )
            qconnect(btn.clicked, handler)
            layout.addWidget(btn)

            desc = QLabel(blurb)
            desc.setWordWrap(True)
            desc.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(desc)

        def _go_flashcards(self) -> None:
            # Redirect to the original Anki flashcard view (deck browser).
            if self.mw is not None:
                self.mw.moveToState("deckBrowser")
            self.close()

        def _go_questions(self) -> None:
            # Redirect to the concept-coded practice-question quiz.
            from aqt.mcat.quiz import show_quiz

            show_quiz(self.mw)
            self.close()

        def _go_memory(self) -> None:
            # Redirect to the honest Memory score + NTR breakdown panel.
            from aqt.mcat.panel import show_memory_panel

            show_memory_panel(self.mw)
            self.close()

    return _Dashboard


def __getattr__(name: str):
    if name == "Dashboard":
        return _build_dashboard_class()
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
