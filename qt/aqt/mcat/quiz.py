# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Minimal practice-question surface -- the Phase-1 "Applying" quiz (FR-3).

A small self-contained ``QDialog`` that walks the student through the
concept-coded question bank (:mod:`aqt.mcat.questions`), grades each answer,
shows the explanation, and -- crucially -- **records the attempt into the
collection** so it feeds the per-concept NTR signal. Getting a concept's
questions wrong raises its NTR (it surfaces sooner in the concept-aware queue
and shows redder on the dashboard); getting them right lowers it.

This is intentionally lightweight (plain Qt widgets, no web/JS), matching the
honest-but-minimal spirit of the Phase-1 Memory panel. It is **not** a graded
Performance score -- it only moves NTR. No AI anywhere.

Wiring (one line, added in ``qt/aqt/main.py`` alongside the Memory action):

    from aqt.mcat.quiz import show_quiz
    show_quiz(mw)
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any, Optional

from aqt.mcat.questions import Question, load_questions, record_attempt

if TYPE_CHECKING:
    import aqt.main
    from aqt.qt import QDialog


def show_quiz(mw: "Optional[aqt.main.AnkiQt]" = None) -> "Optional[QDialog]":
    """Launch the practice-question quiz. The one-line entry point."""
    try:
        questions = list(load_questions())
    except FileNotFoundError:
        questions = []
    quiz_cls = _build_quiz_class()
    dialog = quiz_cls(mw, questions)
    dialog.show()
    return dialog


def _import_qt():  # pragma: no cover - thin import shim
    from aqt.qt import (  # type: ignore
        QButtonGroup,
        QDialog,
        QFrame,
        QHBoxLayout,
        QLabel,
        QPushButton,
        QRadioButton,
        QVBoxLayout,
        qconnect,
    )

    return (
        QButtonGroup,
        QDialog,
        QFrame,
        QHBoxLayout,
        QLabel,
        QPushButton,
        QRadioButton,
        QVBoxLayout,
        qconnect,
    )


def _build_quiz_class():  # pragma: no cover - exercised only with Qt present
    (
        QButtonGroup,
        QDialog,
        QFrame,
        QHBoxLayout,
        QLabel,
        QPushButton,
        QRadioButton,
        QVBoxLayout,
        qconnect,
    ) = _import_qt()

    class _QuizDialog(QDialog):  # type: ignore[misc, valid-type]
        """Steps through the question bank, recording each graded attempt."""

        def __init__(self, mw, questions: list[Question]) -> None:
            super().__init__(mw)
            self.mw = mw
            self.col = getattr(mw, "col", None)
            self.questions = questions
            self.index = 0
            self.answered = 0
            self.correct = 0
            self.graded_this_q = False

            self.setWindowTitle("MCAT - Practice Questions")
            self.setMinimumWidth(560)
            self._layout = QVBoxLayout(self)

            self.progress = QLabel("")
            self.progress.setStyleSheet("color: gray; font-size: 11px;")
            self._layout.addWidget(self.progress)

            self.section = QLabel("")
            self.section.setStyleSheet("color: #2980b9; font-size: 11px;")
            self._layout.addWidget(self.section)

            self.stem = QLabel("")
            self.stem.setWordWrap(True)
            self.stem.setStyleSheet("font-size: 15px; font-weight: bold;")
            self._layout.addWidget(self.stem)

            self.choice_box = QVBoxLayout()
            self._layout.addLayout(self.choice_box)
            self.group: Any = None  # QButtonGroup, built per question
            self.radios: list = []

            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            self._layout.addWidget(line)

            self.feedback = QLabel("")
            self.feedback.setWordWrap(True)
            self.feedback.setStyleSheet("font-size: 12px;")
            self._layout.addWidget(self.feedback)

            buttons = QHBoxLayout()
            self.submit_btn = QPushButton("Submit")
            qconnect(self.submit_btn.clicked, self._on_submit)
            buttons.addWidget(self.submit_btn)
            self.dashboard_btn = QPushButton("Dashboard")
            qconnect(self.dashboard_btn.clicked, self._go_dashboard)
            buttons.addWidget(self.dashboard_btn)
            self.close_btn = QPushButton("Close")
            qconnect(self.close_btn.clicked, self.close)
            buttons.addWidget(self.close_btn)
            self._layout.addLayout(buttons)

            if not self.questions:
                self._show_empty()
            else:
                self._show_question()

        # ----- rendering ----------------------------------------------------

        def _clear_choices(self) -> None:
            while self.choice_box.count():
                item = self.choice_box.takeAt(0)
                w = item.widget()
                if w is not None:
                    w.deleteLater()
            self.radios = []
            self.group = QButtonGroup(self)

        def _show_empty(self) -> None:
            self.stem.setText("No questions found.")
            self.feedback.setText(
                "The question bank (mcat/questions.json) could not be loaded."
            )
            self.submit_btn.setEnabled(False)

        def _go_dashboard(self) -> None:
            from aqt.mcat.dashboard import show_dashboard

            show_dashboard(self.mw)
            self.close()

        def _show_question(self) -> None:
            q = self.questions[self.index]
            self.graded_this_q = False
            self.progress.setText(f"Question {self.index + 1} of {len(self.questions)}")
            self.section.setText(f"{q.section}  -  concept {q.concept_id}")
            self.stem.setText(q.stem)
            self.feedback.setText("")
            self._clear_choices()
            for i, choice in enumerate(q.choices):
                rb = QRadioButton(choice)
                rb.setStyleSheet("font-size: 13px;")
                self.group.addButton(rb, i)
                self.radios.append(rb)
                self.choice_box.addWidget(rb)
            self.submit_btn.setText("Submit")
            self.submit_btn.setEnabled(True)

        # ----- grading ------------------------------------------------------

        def _on_submit(self) -> None:
            if self.graded_this_q:
                self._advance()
                return
            self._grade()

        def _grade(self) -> None:
            q = self.questions[self.index]
            checked = self.group.checkedId()
            if checked < 0:
                self.feedback.setText("Pick an answer first.")
                return
            correct = q.is_correct(checked)
            self.answered += 1
            if correct:
                self.correct += 1
            # Persist the attempt so it feeds NTR. Guard so a missing/!writable
            # collection (e.g. launched without a profile) never crashes.
            if self.col is not None:
                try:
                    record_attempt(self.col, q.concept_id, correct)
                except Exception:
                    pass
            for rb in self.radios:
                rb.setEnabled(False)
            verdict = "Correct." if correct else "Incorrect."
            color = "#27ae60" if correct else "#c0392b"
            right = q.choices[q.answer_index]
            self.feedback.setText(
                f"<span style='color:{color}; font-weight:bold;'>{verdict}</span>"
                f"<br>Answer: {right}<br>{q.explanation}"
            )
            self.graded_this_q = True
            last = self.index == len(self.questions) - 1
            self.submit_btn.setText("Finish" if last else "Next")

        def _advance(self) -> None:
            if self.index >= len(self.questions) - 1:
                self._show_summary()
                return
            self.index += 1
            self._show_question()

        def _show_summary(self) -> None:
            self._clear_choices()
            self.progress.setText("Done")
            self.section.setText("")
            self.stem.setText(
                f"Session complete: {self.correct}/{self.answered} correct."
            )
            self.feedback.setText(
                "These results were recorded against each question's concept and "
                "now feed its Need-to-Review (NTR) score: concepts you missed will "
                "surface sooner in review and show higher NTR on the Memory panel. "
                "Your Memory score itself is unchanged -- it tracks card recall only."
            )
            self.submit_btn.setEnabled(False)

    return _QuizDialog


def __getattr__(name: str):
    if name == "QuizDialog":
        return _build_quiz_class()
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
