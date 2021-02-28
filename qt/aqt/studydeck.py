# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import List, Optional

import aqt
from anki.errors import DeckIsFilteredError
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    TR,
    HelpPage,
    HelpPageArgument,
    disable_help_button,
    getOnlyText,
    openHelp,
    restoreGeom,
    saveGeom,
    shortcut,
    showInfo,
    showWarning,
    tr,
)


class StudyDeck(QDialog):
    def __init__(
        self,
        mw: aqt.AnkiQt,
        names: Callable = None,
        accept: str = None,
        title: str = None,
        help: HelpPageArgument = HelpPage.KEYBOARD_SHORTCUTS,
        current: Optional[str] = None,
        cancel: bool = True,
        parent: Optional[QDialog] = None,
        dyn: bool = False,
        buttons: Optional[List[str]] = None,
        geomKey: str = "default",
    ) -> None:
        QDialog.__init__(self, parent or mw)
        self.mw = mw
        self.form = aqt.forms.studydeck.Ui_Dialog()
        self.form.setupUi(self)
        self.form.filter.installEventFilter(self)
        self.cancel = cancel
        gui_hooks.state_did_reset.append(self.onReset)
        self.geomKey = f"studyDeck-{geomKey}"
        restoreGeom(self, self.geomKey)
        disable_help_button(self)
        if not cancel:
            self.form.buttonBox.removeButton(
                self.form.buttonBox.button(QDialogButtonBox.Cancel)
            )
        if buttons is not None:
            for b in buttons:
                self.form.buttonBox.addButton(b, QDialogButtonBox.ActionRole)
        else:
            b = QPushButton(tr(TR.ACTIONS_ADD))
            b.setShortcut(QKeySequence("Ctrl+N"))
            b.setToolTip(shortcut(tr(TR.DECKS_ADD_NEW_DECK_CTRLANDN)))
            self.form.buttonBox.addButton(b, QDialogButtonBox.ActionRole)
            qconnect(b.clicked, self.onAddDeck)
        if title:
            self.setWindowTitle(title)
        if not names:
            names_ = [
                d.name
                for d in self.mw.col.decks.all_names_and_ids(
                    include_filtered=dyn, skip_empty_default=True
                )
            ]
            self.nameFunc = None
            self.origNames = names_
        else:
            self.nameFunc = names
            self.origNames = names()
        self.name: Optional[str] = None
        self.ok = self.form.buttonBox.addButton(
            accept or tr(TR.DECKS_STUDY), QDialogButtonBox.AcceptRole
        )
        self.setWindowModality(Qt.WindowModal)
        qconnect(self.form.buttonBox.helpRequested, lambda: openHelp(help))
        qconnect(self.form.filter.textEdited, self.redraw)
        qconnect(self.form.list.itemDoubleClicked, self.accept)
        self.show()
        # redraw after show so position at center correct
        self.redraw("", current)
        self.exec_()

    def eventFilter(self, obj: QObject, evt: QEvent) -> bool:
        if evt.type() == QEvent.KeyPress:
            new_row = current_row = self.form.list.currentRow()
            rows_count = self.form.list.count()
            key = evt.key()

            if key == Qt.Key_Up:
                new_row = current_row - 1
            elif key == Qt.Key_Down:
                new_row = current_row + 1
            elif evt.modifiers() & Qt.ControlModifier and Qt.Key_1 <= key <= Qt.Key_9:
                row_index = key - Qt.Key_1
                if row_index < rows_count:
                    new_row = row_index

            if rows_count:
                new_row %= rows_count  # don't let row index overflow/underflow
            if new_row != current_row:
                self.form.list.setCurrentRow(new_row)
                return True
        return False

    def redraw(self, filt: str, focus: Optional[str] = None) -> None:
        self.filt = filt
        self.focus = focus
        self.names = [n for n in self.origNames if self._matches(n, filt)]
        l = self.form.list
        l.clear()
        l.addItems(self.names)
        if focus in self.names:
            idx = self.names.index(focus)
        else:
            idx = 0
        l.setCurrentRow(idx)
        l.scrollToItem(l.item(idx), QAbstractItemView.PositionAtCenter)

    def _matches(self, name: str, filt: str) -> bool:
        name = name.lower()
        filt = filt.lower()
        if not filt:
            return True
        for word in filt.split(" "):
            if word not in name:
                return False
        return True

    def onReset(self) -> None:
        # model updated?
        if self.nameFunc:
            self.origNames = self.nameFunc()
        self.redraw(self.filt, self.focus)

    def accept(self) -> None:
        saveGeom(self, self.geomKey)
        gui_hooks.state_did_reset.remove(self.onReset)
        row = self.form.list.currentRow()
        if row < 0:
            showInfo(tr(TR.DECKS_PLEASE_SELECT_SOMETHING))
            return
        self.name = self.names[self.form.list.currentRow()]
        QDialog.accept(self)

    def reject(self) -> None:
        saveGeom(self, self.geomKey)
        gui_hooks.state_did_reset.remove(self.onReset)
        QDialog.reject(self)

    def onAddDeck(self) -> None:
        row = self.form.list.currentRow()
        if row < 0:
            default = self.form.filter.text()
        else:
            default = self.names[self.form.list.currentRow()]
        n = getOnlyText(tr(TR.DECKS_NEW_DECK_NAME), default=default)
        n = n.strip()
        if n:
            try:
                did = self.mw.col.decks.id(n)
            except DeckIsFilteredError as err:
                showWarning(str(err))
                return
            # deck name may not be the same as user input. ex: ", ::
            self.name = self.mw.col.decks.name(did)
            # make sure we clean up reset hook when manually exiting
            gui_hooks.state_did_reset.remove(self.onReset)
            if self.mw.state == "deckBrowser":
                self.mw.deckBrowser.refresh()
            gui_hooks.sidebar_should_refresh_decks()
            QDialog.accept(self)
