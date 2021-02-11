# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Any, Callable, List, Optional

import aqt.deckchooser
import aqt.editor
import aqt.forms
import aqt.modelchooser
from anki.collection import SearchNode
from anki.consts import MODEL_CLOZE
from anki.notes import Note
from anki.utils import htmlToTextLine, isMac
from aqt import AnkiQt, gui_hooks
from aqt.main import ResetReason
from aqt.qt import *
from aqt.sound import av_player
from aqt.utils import (
    TR,
    HelpPage,
    addCloseShortcut,
    askUser,
    disable_help_button,
    downArrow,
    openHelp,
    restoreGeom,
    saveGeom,
    shortcut,
    showWarning,
    tooltip,
    tr,
)


class AddCards(QDialog):
    def __init__(self, mw: AnkiQt) -> None:
        QDialog.__init__(self, None, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = mw
        self.form = aqt.forms.addcards.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(tr(TR.ACTIONS_ADD))
        disable_help_button(self)
        self.setMinimumHeight(300)
        self.setMinimumWidth(400)
        self.setupChoosers()
        self.setupEditor()
        self.setupButtons()
        self.onReset()
        self.history: List[int] = []
        self.previousNote: Optional[Note] = None
        restoreGeom(self, "add")
        gui_hooks.state_did_reset.append(self.onReset)
        gui_hooks.current_note_type_did_change.append(self.onModelChange)
        addCloseShortcut(self)
        gui_hooks.add_cards_did_init(self)
        self.show()

    def setupEditor(self) -> None:
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self, True)

    def setupChoosers(self) -> None:
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.form.modelArea, on_activated=self.show_notetype_selector
        )
        self.deckChooser = aqt.deckchooser.DeckChooser(self.mw, self.form.deckArea)

    def helpRequested(self) -> None:
        openHelp(HelpPage.ADDING_CARD_AND_NOTE)

    def setupButtons(self) -> None:
        bb = self.form.buttonBox
        ar = QDialogButtonBox.ActionRole
        # add
        self.addButton = bb.addButton(tr(TR.ACTIONS_ADD), ar)
        qconnect(self.addButton.clicked, self.addCards)
        self.addButton.setShortcut(QKeySequence("Ctrl+Return"))
        self.addButton.setToolTip(shortcut(tr(TR.ADDING_ADD_SHORTCUT_CTRLANDENTER)))
        # close
        self.closeButton = QPushButton(tr(TR.ACTIONS_CLOSE))
        self.closeButton.setAutoDefault(False)
        bb.addButton(self.closeButton, QDialogButtonBox.RejectRole)
        # help
        self.helpButton = QPushButton(tr(TR.ACTIONS_HELP), clicked=self.helpRequested)  # type: ignore
        self.helpButton.setAutoDefault(False)
        bb.addButton(self.helpButton, QDialogButtonBox.HelpRole)
        # history
        b = bb.addButton(f"{tr(TR.ADDING_HISTORY)} {downArrow()}", ar)
        if isMac:
            sc = "Ctrl+Shift+H"
        else:
            sc = "Ctrl+H"
        b.setShortcut(QKeySequence(sc))
        b.setToolTip(tr(TR.ADDING_SHORTCUT, val=shortcut(sc)))
        qconnect(b.clicked, self.onHistory)
        b.setEnabled(False)
        self.historyButton = b

    def setAndFocusNote(self, note: Note) -> None:
        self.editor.setNote(note, focusTo=0)

    def show_notetype_selector(self) -> None:
        self.editor.saveNow(self.modelChooser.onModelChange)

    def onModelChange(self, unused: Any = None) -> None:
        oldNote = self.editor.note
        note = self.mw.col.newNote()
        self.previousNote = None
        if oldNote:
            oldFields = list(oldNote.keys())
            newFields = list(note.keys())
            for n, f in enumerate(note.model()["flds"]):
                fieldName = f["name"]
                # copy identical fields
                if fieldName in oldFields:
                    note[fieldName] = oldNote[fieldName]
                elif n < len(oldNote.model()["flds"]):
                    # set non-identical fields by field index
                    oldFieldName = oldNote.model()["flds"][n]["name"]
                    if oldFieldName not in newFields:
                        note.fields[n] = oldNote.fields[n]
        self.editor.note = note
        # When on model change is called, reset is necessarily called.
        # Reset load note, so it is not required to load it here.

    def onReset(self, model: None = None, keep: bool = False) -> None:
        oldNote = self.editor.note
        note = self.mw.col.newNote()
        flds = note.model()["flds"]
        # copy fields from old note
        if oldNote:
            for n in range(min(len(note.fields), len(oldNote.fields))):
                if not keep or flds[n]["sticky"]:
                    note.fields[n] = oldNote.fields[n]
        self.setAndFocusNote(note)

    def removeTempNote(self, note: Note) -> None:
        print("removeTempNote() will go away")

    def addHistory(self, note: Note) -> None:
        self.history.insert(0, note.id)
        self.history = self.history[:15]
        self.historyButton.setEnabled(True)

    def onHistory(self) -> None:
        m = QMenu(self)
        for nid in self.history:
            if self.mw.col.findNotes(SearchNode(nid=nid)):
                note = self.mw.col.getNote(nid)
                fields = note.fields
                txt = htmlToTextLine(", ".join(fields))
                if len(txt) > 30:
                    txt = f"{txt[:30]}..."
                line = tr(TR.ADDING_EDIT, val=txt)
                line = gui_hooks.addcards_will_add_history_entry(line, note)
                a = m.addAction(line)
                qconnect(a.triggered, lambda b, nid=nid: self.editHistory(nid))
            else:
                a = m.addAction(tr(TR.ADDING_NOTE_DELETED))
                a.setEnabled(False)
        gui_hooks.add_cards_will_show_history_menu(self, m)
        m.exec_(self.historyButton.mapToGlobal(QPoint(0, 0)))

    def editHistory(self, nid: int) -> None:
        aqt.dialogs.open("Browser", self.mw, search=(SearchNode(nid=nid),))

    def addNote(self, note: Note) -> Optional[Note]:
        note.model()["did"] = self.deckChooser.selectedId()
        ret = note.dupeOrEmpty()
        problem = None
        if ret == 1:
            problem = tr(TR.ADDING_THE_FIRST_FIELD_IS_EMPTY)
        problem = gui_hooks.add_cards_will_add_note(problem, note)
        if problem is not None:
            showWarning(problem, help=HelpPage.ADDING_CARD_AND_NOTE)
            return None
        if note.model()["type"] == MODEL_CLOZE:
            if not note.cloze_numbers_in_fields():
                if not askUser(tr(TR.ADDING_YOU_HAVE_A_CLOZE_DELETION_NOTE)):
                    return None
        self.mw.col.add_note(note, self.deckChooser.selectedId())
        self.mw.col.clearUndo()
        self.addHistory(note)
        self.previousNote = note
        self.mw.requireReset(reason=ResetReason.AddCardsAddNote, context=self)
        gui_hooks.add_cards_did_add_note(note)
        return note

    def addCards(self) -> None:
        self.editor.saveNow(self._addCards)

    def _addCards(self) -> None:
        self.editor.saveAddModeVars()
        if not self.addNote(self.editor.note):
            return

        # workaround for PyQt focus bug
        self.editor.hideCompleters()

        tooltip(tr(TR.ADDING_ADDED), period=500)
        av_player.stop_and_clear_queue()
        self.onReset(keep=True)
        self.mw.col.autosave()

    def keyPressEvent(self, evt: QKeyEvent) -> None:
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
        self.editor.cleanup()
        self.modelChooser.cleanup()
        self.deckChooser.cleanup()
        self.mw.maybeReset()
        saveGeom(self, "add")
        aqt.dialogs.markClosed("AddCards")
        QDialog.reject(self)

    def ifCanClose(self, onOk: Callable) -> None:
        def afterSave() -> None:
            ok = self.editor.fieldsAreBlank(self.previousNote) or askUser(
                tr(TR.ADDING_CLOSE_AND_LOSE_CURRENT_INPUT), defaultno=True
            )
            if ok:
                onOk()

        self.editor.saveNow(afterSave)

    def closeWithCallback(self, cb: Callable[[], None]) -> None:
        def doClose() -> None:
            self._reject()
            cb()

        self.ifCanClose(doClose)
