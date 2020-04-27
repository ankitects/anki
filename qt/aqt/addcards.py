# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Callable, List, Optional

import aqt.deckchooser
import aqt.editor
import aqt.forms
import aqt.modelchooser
from anki.consts import MODEL_CLOZE
from anki.lang import _
from anki.notes import Note
from anki.utils import htmlToTextLine, isMac
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.sound import av_player
from aqt.utils import (
    addCloseShortcut,
    askUser,
    downArrow,
    openHelp,
    restoreGeom,
    saveGeom,
    shortcut,
    showWarning,
    tooltip,
)


class AddCards(QDialog):
    def __init__(self, mw: AnkiQt) -> None:
        QDialog.__init__(self, None, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = mw
        self.form = aqt.forms.addcards.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Add"))
        self.setMinimumHeight(300)
        self.setMinimumWidth(400)
        self.setupChoosers()
        self.setupEditor()
        self.setupButtons()
        self.onReset()
        self.history: List[int] = []
        self.previousNote = None
        restoreGeom(self, "add")
        gui_hooks.state_did_reset.append(self.onReset)
        gui_hooks.current_note_type_did_change.append(self.onModelChange)
        addCloseShortcut(self)
        gui_hooks.add_cards_did_init(self)
        self.show()

    def setupEditor(self) -> None:
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self, True)

    def setupChoosers(self) -> None:
        self.modelChooser = aqt.modelchooser.ModelChooser(self.mw, self.form.modelArea)
        self.deckChooser = aqt.deckchooser.DeckChooser(self.mw, self.form.deckArea)

    def helpRequested(self):
        openHelp("addingnotes")

    def setupButtons(self) -> None:
        bb = self.form.buttonBox
        ar = QDialogButtonBox.ActionRole
        # add
        self.addButton = bb.addButton(_("Add"), ar)
        self.addButton.clicked.connect(self.addCards)
        self.addButton.setShortcut(QKeySequence("Ctrl+Return"))
        self.addButton.setToolTip(shortcut(_("Add (shortcut: ctrl+enter)")))
        # close
        self.closeButton = QPushButton(_("Close"))
        self.closeButton.setAutoDefault(False)
        bb.addButton(self.closeButton, QDialogButtonBox.RejectRole)
        # help
        self.helpButton = QPushButton(_("Help"), clicked=self.helpRequested)  # type: ignore
        self.helpButton.setAutoDefault(False)
        bb.addButton(self.helpButton, QDialogButtonBox.HelpRole)
        # history
        b = bb.addButton(_("History") + " " + downArrow(), ar)
        if isMac:
            sc = "Ctrl+Shift+H"
        else:
            sc = "Ctrl+H"
        b.setShortcut(QKeySequence(sc))
        b.setToolTip(_("Shortcut: %s") % shortcut(sc))
        b.clicked.connect(self.onHistory)
        b.setEnabled(False)
        self.historyButton = b

    def setAndFocusNote(self, note: Note) -> None:
        self.editor.setNote(note, focusTo=0)

    def onModelChange(self, unused=None) -> None:
        oldNote = self.editor.note
        note = self.mw.col.newNote()
        self.previousNote = None
        if oldNote:
            oldFields = list(oldNote.keys())
            newFields = list(note.keys())
            for n, f in enumerate(note.model()["flds"]):
                fieldName = f["name"]
                try:
                    oldFieldName = oldNote.model()["flds"][n]["name"]
                except IndexError:
                    oldFieldName = None
                # copy identical fields
                if fieldName in oldFields:
                    note[fieldName] = oldNote[fieldName]
                # set non-identical fields by field index
                elif oldFieldName and oldFieldName not in newFields:
                    try:
                        note.fields[n] = oldNote.fields[n]
                    except IndexError:
                        pass
            self.removeTempNote(oldNote)

    def onReset(self, model: None = None, keep: bool = False) -> None:
        oldNote = self.editor.note
        note = self.mw.col.newNote()
        flds = note.model()["flds"]
        # copy fields from old note
        if oldNote:
            if not keep:
                self.removeTempNote(oldNote)
            for n in range(len(note.fields)):
                try:
                    if not keep or flds[n]["sticky"]:
                        note.fields[n] = oldNote.fields[n]
                    else:
                        note.fields[n] = ""
                except IndexError:
                    break
        self.setAndFocusNote(note)

    def removeTempNote(self, note: Note) -> None:
        if not note or not note.id:
            return
        # we don't have to worry about cards; just the note
        self.mw.col._remNotes([note.id])

    def addHistory(self, note):
        self.history.insert(0, note.id)
        self.history = self.history[:15]
        self.historyButton.setEnabled(True)

    def onHistory(self) -> None:
        m = QMenu(self)
        for nid in self.history:
            if self.mw.col.findNotes("nid:%s" % nid):
                fields = self.mw.col.getNote(nid).fields
                txt = htmlToTextLine(", ".join(fields))
                if len(txt) > 30:
                    txt = txt[:30] + "..."
                a = m.addAction(_('Edit "%s"') % txt)
                qconnect(a.triggered, lambda b, nid=nid: self.editHistory(nid))
            else:
                a = m.addAction(_("(Note deleted)"))
                a.setEnabled(False)
        gui_hooks.add_cards_will_show_history_menu(self, m)
        m.exec_(self.historyButton.mapToGlobal(QPoint(0, 0)))

    def editHistory(self, nid):
        browser = aqt.dialogs.open("Browser", self.mw)
        browser.form.searchEdit.lineEdit().setText("nid:%d" % nid)
        browser.onSearchActivated()

    def addNote(self, note) -> Optional[Note]:
        note.model()["did"] = self.deckChooser.selectedId()
        ret = note.dupeOrEmpty()
        problem = None
        if ret == 1:
            problem = _("The first field is empty.")
        problem = gui_hooks.add_cards_will_add_note(problem, note)
        if problem is not None:
            showWarning(problem, help="AddItems#AddError")
            return None
        if note.model()["type"] == MODEL_CLOZE:
            if not self.mw.col.models._availClozeOrds(
                note.model(), note.joinedFields(), False
            ):
                if not askUser(
                    _(
                        "You have a cloze deletion note type "
                        "but have not made any cloze deletions. Proceed?"
                    )
                ):
                    return None
        cards = self.mw.col.addNote(note)
        if not cards:
            showWarning(
                _(
                    """\
The input you have provided would make an empty \
question on all cards."""
                ),
                help="AddItems",
            )
            return None
        self.mw.col.clearUndo()
        self.addHistory(note)
        self.mw.requireReset()
        self.previousNote = note
        gui_hooks.add_cards_did_add_note(note)
        return note

    def addCards(self):
        self.editor.saveNow(self._addCards)

    def _addCards(self):
        self.editor.saveAddModeVars()
        if not self.addNote(self.editor.note):
            return
        tooltip(_("Added"), period=500)
        av_player.stop_and_clear_queue()
        self.onReset(keep=True)
        self.mw.col.autosave()

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if evt.key() in (Qt.Key_Enter, Qt.Key_Return) and self.editor.tags.hasFocus():
            evt.accept()
            return
        return QDialog.keyPressEvent(self, evt)

    def reject(self) -> None:
        self.ifCanClose(self._reject)

    def _reject(self) -> None:
        gui_hooks.state_did_reset.remove(self.onReset)
        gui_hooks.current_note_type_did_change.remove(self.onModelChange)
        av_player.stop_and_clear_queue()
        self.removeTempNote(self.editor.note)
        self.editor.cleanup()
        self.modelChooser.cleanup()
        self.deckChooser.cleanup()
        self.mw.maybeReset()
        saveGeom(self, "add")
        aqt.dialogs.markClosed("AddCards")
        QDialog.reject(self)

    def ifCanClose(self, onOk: Callable) -> None:
        def afterSave():
            ok = self.editor.fieldsAreBlank(self.previousNote) or askUser(
                _("Close and lose current input?"), defaultno=True
            )
            if ok:
                onOk()

        self.editor.saveNow(afterSave)

    def closeWithCallback(self, cb):
        def doClose():
            self._reject()
            cb()

        self.ifCanClose(doClose)
