# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Practice-test ingestion surface -- the kernel's one input action.

A self-contained ``QDialog`` where the student **ingests a past practice test**.
Rather than the app serving its own questions (this is a kernel, not an
all-in-one quiz app), the student uploads or transcribes a test they already
took and annotates each question:

  * **which MCAT concept it tested** (a dropdown over the taxonomy), and
  * **whether they got it right or wrong**.

Two ways to populate the table:

  * **Load from CSV...** -- pick a spreadsheet exported/transcribed from the
    practice test (columns ``concept`` and ``correct``; see
    :func:`aqt.mcat.practice_tests.parse_practice_test_csv`). Rows are added
    pre-filled, ready to adjust.
  * **Add row** -- annotate a question by hand.

On save the annotated attempts are aggregated per concept and persisted into the
collection, where they feed each concept's **Need-to-Review (NTR)** signal:
concepts the student missed surface sooner in the concept-aware review queue and
show higher on the recommendations panel; concepts they aced drop down. This is
the "add your weak points to Anki" workflow, automated.

Deterministic, plain Qt widgets, **no AI**, no web/JS. It is **not** a graded
Performance score -- it only moves NTR (and feeds the separate Readiness
projection). The recommendations/prediction surface lives in
:mod:`aqt.mcat.panel`.

Wiring (one line, added in ``qt/aqt/main.py`` alongside the scores action):

    from aqt.mcat.ingest import show_ingest
    show_ingest(mw)
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any, Optional

from aqt.mcat.practice_tests import (
    ConceptChoice,
    load_concept_choices,
    parse_practice_test_csv,
    record_batch,
)

if TYPE_CHECKING:
    import aqt.main
    from aqt.qt import QDialog


def show_ingest(mw: "Optional[aqt.main.AnkiQt]" = None) -> "Optional[QDialog]":
    """Launch the practice-test ingestion dialog. The one-line entry point."""
    try:
        choices = list(load_concept_choices())
    except FileNotFoundError:
        choices = []
    dialog_cls = _build_ingest_class()
    dialog = dialog_cls(mw, choices)
    dialog.show()
    return dialog


def _import_qt():  # pragma: no cover - thin import shim
    from aqt.qt import (  # type: ignore
        QComboBox,
        QDialog,
        QFileDialog,
        QFrame,
        QHBoxLayout,
        QHeaderView,
        QLabel,
        QLineEdit,
        QMessageBox,
        QPushButton,
        QTableWidget,
        QTableWidgetItem,
        QVBoxLayout,
        qconnect,
    )

    return (
        QComboBox,
        QDialog,
        QFileDialog,
        QFrame,
        QHBoxLayout,
        QHeaderView,
        QLabel,
        QLineEdit,
        QMessageBox,
        QPushButton,
        QTableWidget,
        QTableWidgetItem,
        QVBoxLayout,
        qconnect,
    )


def _build_ingest_class():  # pragma: no cover - exercised only with Qt present
    (
        QComboBox,
        QDialog,
        QFileDialog,
        QFrame,
        QHBoxLayout,
        QHeaderView,
        QLabel,
        QLineEdit,
        QMessageBox,
        QPushButton,
        QTableWidget,
        QTableWidgetItem,
        QVBoxLayout,
        qconnect,
    ) = _import_qt()

    # Column layout for the annotation table.
    COL_QUESTION = 0
    COL_CONCEPT = 1
    COL_RESULT = 2

    class _IngestDialog(QDialog):  # type: ignore[misc, valid-type]
        """Upload/annotate a past practice test; save feeds NTR."""

        def __init__(self, mw, choices: list[ConceptChoice]) -> None:
            super().__init__(mw)
            self.mw = mw
            self.col = getattr(mw, "col", None)
            self.choices = choices
            self._QComboBox = QComboBox
            self._QLineEdit = QLineEdit
            self._QTableWidgetItem = QTableWidgetItem

            self.setWindowTitle("MCAT - Ingest Practice Test")
            self.setMinimumWidth(680)
            self.resize(720, 560)
            layout = QVBoxLayout(self)

            title = QLabel("Ingest a practice test")
            title.setStyleSheet("font-size: 18px; font-weight: bold;")
            layout.addWidget(title)

            blurb = QLabel(
                "Log a test you already took (UWorld, AAMC, a full length...). "
                "For each question, tag the MCAT concept it tested and whether "
                "you got it right or wrong. This re-prioritises your Anki review "
                "toward your weak concepts and feeds your projected score. "
                "It does not change your Memory (card-recall) score."
            )
            blurb.setWordWrap(True)
            blurb.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(blurb)

            if not self.choices:
                warn = QLabel(
                    "Concept taxonomy not found (mcat/taxonomy.json) -- cannot "
                    "tag questions. Ingestion is disabled."
                )
                warn.setWordWrap(True)
                warn.setStyleSheet("color: #c0392b; font-size: 12px;")
                layout.addWidget(warn)

            # ----- top action row: load CSV / add / remove --------------------
            actions = QHBoxLayout()
            self.load_btn = QPushButton("Load from CSV...")
            qconnect(self.load_btn.clicked, self._on_load_csv)
            actions.addWidget(self.load_btn)
            self.add_btn = QPushButton("Add row")
            qconnect(self.add_btn.clicked, lambda: self._add_row())
            actions.addWidget(self.add_btn)
            self.remove_btn = QPushButton("Remove selected")
            qconnect(self.remove_btn.clicked, self._on_remove_selected)
            actions.addWidget(self.remove_btn)
            actions.addStretch(1)
            layout.addLayout(actions)

            # ----- annotation table -------------------------------------------
            self.table = QTableWidget(0, 3, self)
            self.table.setHorizontalHeaderLabels(
                ["Question (optional)", "Concept tested", "Result"]
            )
            header = self.table.horizontalHeader()
            header.setSectionResizeMode(COL_QUESTION, QHeaderView.ResizeMode.Stretch)
            header.setSectionResizeMode(
                COL_CONCEPT, QHeaderView.ResizeMode.ResizeToContents
            )
            header.setSectionResizeMode(
                COL_RESULT, QHeaderView.ResizeMode.ResizeToContents
            )
            layout.addWidget(self.table)

            self.status = QLabel("")
            self.status.setWordWrap(True)
            self.status.setStyleSheet("color: gray; font-size: 11px;")
            layout.addWidget(self.status)

            line = QFrame()
            line.setFrameShape(QFrame.Shape.HLine)
            layout.addWidget(line)

            # ----- bottom buttons ---------------------------------------------
            buttons = QHBoxLayout()
            self.save_btn = QPushButton("Save to review signal")
            self.save_btn.setStyleSheet("font-weight: bold;")
            qconnect(self.save_btn.clicked, self._on_save)
            buttons.addWidget(self.save_btn)
            self.plan_btn = QPushButton("View review plan")
            qconnect(self.plan_btn.clicked, self._on_view_plan)
            buttons.addWidget(self.plan_btn)
            buttons.addStretch(1)
            self.close_btn = QPushButton("Close")
            qconnect(self.close_btn.clicked, self.close)
            buttons.addWidget(self.close_btn)
            layout.addLayout(buttons)

            enabled = bool(self.choices)
            for btn in (self.load_btn, self.add_btn, self.remove_btn, self.save_btn):
                btn.setEnabled(enabled)
            if enabled:
                self._add_row()  # start with one blank row to annotate

            self._QMessageBox = QMessageBox
            self._QFileDialog = QFileDialog

        # ----- table helpers ------------------------------------------------

        def _concept_combo(self, concept_id: str | None = None):
            combo = self._QComboBox()
            for c in self.choices:
                combo.addItem(c.label(), c.concept_id)
            if concept_id is not None:
                idx = combo.findData(concept_id)
                if idx >= 0:
                    combo.setCurrentIndex(idx)
            return combo

        def _result_combo(self, correct: bool | None = None):
            combo = self._QComboBox()
            combo.addItem("Correct", True)
            combo.addItem("Incorrect", False)
            if correct is not None:
                combo.setCurrentIndex(0 if correct else 1)
            return combo

        def _add_row(
            self,
            label: str = "",
            concept_id: str | None = None,
            correct: bool | None = None,
        ) -> None:
            row = self.table.rowCount()
            self.table.insertRow(row)
            self.table.setCellWidget(row, COL_QUESTION, self._QLineEdit(label))
            self.table.setCellWidget(row, COL_CONCEPT, self._concept_combo(concept_id))
            self.table.setCellWidget(row, COL_RESULT, self._result_combo(correct))

        def _on_remove_selected(self) -> None:
            rows = sorted({i.row() for i in self.table.selectedIndexes()}, reverse=True)
            for r in rows:
                self.table.removeRow(r)

        # ----- CSV load -----------------------------------------------------

        def _on_load_csv(self) -> None:
            path, _ = self._QFileDialog.getOpenFileName(
                self,
                "Choose a practice-test CSV",
                "",
                "CSV files (*.csv);;All files (*)",
            )
            if not path:
                return
            try:
                with open(path, encoding="utf-8-sig") as fh:
                    text = fh.read()
            except OSError as exc:
                self.status.setText(f"Could not read file: {exc}")
                return
            attempts, warnings = parse_practice_test_csv(text)
            for a in attempts:
                self._add_row(a.label, a.concept_id, a.correct)
            msg = f"Loaded {len(attempts)} question(s) from CSV."
            if warnings:
                msg += f" {len(warnings)} row(s) skipped: " + " ".join(warnings[:3])
                if len(warnings) > 3:
                    msg += f" (+{len(warnings) - 3} more)"
            self.status.setText(msg)

        # ----- save ---------------------------------------------------------

        def _collect_rows(self) -> list[tuple[str, bool]]:
            out: list[tuple[str, bool]] = []
            for r in range(self.table.rowCount()):
                concept_widget: Any = self.table.cellWidget(r, COL_CONCEPT)
                result_widget: Any = self.table.cellWidget(r, COL_RESULT)
                if concept_widget is None or result_widget is None:
                    continue
                concept_id = concept_widget.currentData()
                correct = bool(result_widget.currentData())
                if concept_id:
                    out.append((concept_id, correct))
            return out

        def _on_save(self) -> None:
            rows = self._collect_rows()
            if not rows:
                self.status.setText("Nothing to save -- add at least one question.")
                return
            if self.col is None:
                self.status.setText("No collection open, so attempts can't be saved.")
                return
            try:
                record_batch(self.col, rows)
            except Exception as exc:  # noqa: BLE001 - never crash the student
                self.status.setText(f"Could not save: {exc}")
                return
            correct = sum(1 for _cid, ok in rows if ok)
            concepts = len({cid for cid, _ok in rows})
            self._QMessageBox.information(
                self,
                "Practice test ingested",
                f"Recorded {len(rows)} question(s) ({correct} correct) across "
                f"{concepts} concept(s).\n\nYour Need-to-Review signal has been "
                "updated: weak concepts will surface sooner in review. Open "
                "'View review plan' to see the updated recommendations.",
            )
            self.status.setText(
                f"Saved {len(rows)} question(s). You can ingest another test or "
                "view your review plan."
            )
            # Clear the table so a second test starts fresh.
            self.table.setRowCount(0)
            self._add_row()

        def _on_view_plan(self) -> None:
            from aqt.mcat.panel import show_memory_panel

            show_memory_panel(self.mw)

    return _IngestDialog


def __getattr__(name: str):
    if name == "IngestDialog":
        return _build_ingest_class()
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
