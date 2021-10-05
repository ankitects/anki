# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
from typing import Iterable

from anki.collection import Collection
from aqt import gui_hooks
from aqt.qt import *


class TagEdit(QLineEdit):
    _completer: QCompleter | TagCompleter

    lostFocus = pyqtSignal()

    # 0 = tags, 1 = decks
    def __init__(self, parent: QWidget, type: int = 0) -> None:
        QLineEdit.__init__(self, parent)
        self.col: Collection | None = None
        self.model = QStringListModel()
        self.type = type
        if type == 0:
            self._completer = TagCompleter(self.model, parent, self)
        else:
            self._completer = QCompleter(self.model, parent)
        self._completer.setCompletionMode(QCompleter.CompletionMode.PopupCompletion)
        self._completer.setCaseSensitivity(Qt.CaseSensitivity.CaseInsensitive)
        self._completer.setFilterMode(Qt.MatchFlag.MatchContains)
        self.setCompleter(self._completer)

    def setCol(self, col: Collection) -> None:
        "Set the current col, updating list of available tags."
        self.col = col
        l: Iterable[str]
        if self.type == 0:
            l = self.col.tags.all()
        else:
            l = (d.name for d in self.col.decks.all_names_and_ids())
        self.model.setStringList(l)

    def focusInEvent(self, evt: QFocusEvent) -> None:
        QLineEdit.focusInEvent(self, evt)

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() in (Qt.Key.Key_Up, Qt.Key.Key_Down):
            # show completer on arrow key up/down
            if not self._completer.popup().isVisible():
                self.showCompleter()
            return
        if (
            evt.key() == Qt.Key.Key_Tab
            and evt.modifiers() & Qt.KeyboardModifier.ControlModifier
        ):
            # select next completion
            if not self._completer.popup().isVisible():
                self.showCompleter()
            index = self._completer.currentIndex()
            self._completer.popup().setCurrentIndex(index)
            cur_row = index.row()
            if not self._completer.setCurrentRow(cur_row + 1):
                self._completer.setCurrentRow(0)
            return
        if (
            evt.key() in (Qt.Key.Key_Enter, Qt.Key.Key_Return)
            and self._completer.popup().isVisible()
        ):
            # apply first completion if no suggestion selected
            selected_row = self._completer.popup().currentIndex().row()
            if selected_row == -1:
                self._completer.setCurrentRow(0)
                index = self._completer.currentIndex()
                self._completer.popup().setCurrentIndex(index)
            self.hideCompleter()
            QWidget.keyPressEvent(self, evt)
            return
        QLineEdit.keyPressEvent(self, evt)
        if not evt.text():
            # if it's a modifier, don't show
            return
        if evt.key() not in (
            Qt.Key.Key_Enter,
            Qt.Key.Key_Return,
            Qt.Key.Key_Escape,
            Qt.Key.Key_Space,
            Qt.Key.Key_Tab,
            Qt.Key.Key_Backspace,
            Qt.Key.Key_Delete,
        ):
            self.showCompleter()
        gui_hooks.tag_editor_did_process_key(self, evt)

    def showCompleter(self) -> None:
        self._completer.setCompletionPrefix(self.text())
        self._completer.complete()

    def focusOutEvent(self, evt: QFocusEvent) -> None:
        QLineEdit.focusOutEvent(self, evt)
        self.lostFocus.emit()  # type: ignore
        self._completer.popup().hide()

    def hideCompleter(self) -> None:
        if sip.isdeleted(self._completer):  # type: ignore
            return
        self._completer.popup().hide()


class TagCompleter(QCompleter):
    def __init__(
        self,
        model: QStringListModel,
        parent: QWidget,
        edit: TagEdit,
    ) -> None:
        QCompleter.__init__(self, model, parent)
        self.tags: list[str] = []
        self.edit = edit
        self.cursor: int | None = None

    def splitPath(self, tags: str) -> list[str]:
        stripped_tags = tags.strip()
        stripped_tags = re.sub("  +", " ", stripped_tags)
        self.tags = self.edit.col.tags.split(stripped_tags)
        self.tags.append("")
        p = self.edit.cursorPosition()
        if tags.endswith("  "):
            self.cursor = len(self.tags) - 1
        else:
            self.cursor = stripped_tags.count(" ", 0, p)
        return [self.tags[self.cursor]]

    def pathFromIndex(self, idx: QModelIndex) -> str:
        if self.cursor is None:
            return self.edit.text()
        ret = QCompleter.pathFromIndex(self, idx)
        self.tags[self.cursor] = ret
        try:
            self.tags.remove("")
        except ValueError:
            pass
        return f"{' '.join(self.tags)} "
