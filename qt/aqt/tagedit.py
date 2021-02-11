# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
from typing import Iterable, List, Optional, Union

from anki.collection import Collection
from aqt import gui_hooks
from aqt.qt import *


class TagEdit(QLineEdit):
    completer: Union[QCompleter, TagCompleter]

    lostFocus = pyqtSignal()

    # 0 = tags, 1 = decks
    def __init__(self, parent: QDialog, type: int = 0) -> None:
        QLineEdit.__init__(self, parent)
        self.col: Optional[Collection] = None
        self.model = QStringListModel()
        self.type = type
        if type == 0:
            self.completer = TagCompleter(self.model, parent, self)
        else:
            self.completer = QCompleter(self.model, parent)
        self.completer.setCompletionMode(QCompleter.PopupCompletion)
        self.completer.setCaseSensitivity(Qt.CaseInsensitive)
        self.completer.setFilterMode(Qt.MatchContains)
        self.setCompleter(self.completer)

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
        if evt.key() in (Qt.Key_Up, Qt.Key_Down):
            # show completer on arrow key up/down
            if not self.completer.popup().isVisible():
                self.showCompleter()
            return
        if evt.key() == Qt.Key_Tab and evt.modifiers() & Qt.ControlModifier:
            # select next completion
            if not self.completer.popup().isVisible():
                self.showCompleter()
            index = self.completer.currentIndex()
            self.completer.popup().setCurrentIndex(index)
            cur_row = index.row()
            if not self.completer.setCurrentRow(cur_row + 1):
                self.completer.setCurrentRow(0)
            return
        if (
            evt.key() in (Qt.Key_Enter, Qt.Key_Return)
            and self.completer.popup().isVisible()
        ):
            # apply first completion if no suggestion selected
            selected_row = self.completer.popup().currentIndex().row()
            if selected_row == -1:
                self.completer.setCurrentRow(0)
                index = self.completer.currentIndex()
                self.completer.popup().setCurrentIndex(index)
            self.hideCompleter()
            QWidget.keyPressEvent(self, evt)
            return
        QLineEdit.keyPressEvent(self, evt)
        if not evt.text():
            # if it's a modifier, don't show
            return
        if evt.key() not in (
            Qt.Key_Enter,
            Qt.Key_Return,
            Qt.Key_Escape,
            Qt.Key_Space,
            Qt.Key_Tab,
            Qt.Key_Backspace,
            Qt.Key_Delete,
        ):
            self.showCompleter()
        gui_hooks.tag_editor_did_process_key(self, evt)

    def showCompleter(self) -> None:
        self.completer.setCompletionPrefix(self.text())
        self.completer.complete()

    def focusOutEvent(self, evt: QFocusEvent) -> None:
        QLineEdit.focusOutEvent(self, evt)
        self.lostFocus.emit()  # type: ignore
        self.completer.popup().hide()

    def hideCompleter(self) -> None:
        if sip.isdeleted(self.completer):
            return
        self.completer.popup().hide()


class TagCompleter(QCompleter):
    def __init__(
        self,
        model: QStringListModel,
        parent: QWidget,
        edit: TagEdit,
    ) -> None:
        QCompleter.__init__(self, model, parent)
        self.tags: List[str] = []
        self.edit = edit
        self.cursor: Optional[int] = None

    def splitPath(self, tags: str) -> List[str]:
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
