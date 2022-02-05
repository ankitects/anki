# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, Optional

import aqt.editor
import aqt.forms
from anki._legacy import deprecated
from anki.collection import OpChanges, SearchNode
from anki.decks import DeckId
from anki.models import NotetypeId
from anki.notes import Note, NoteFieldsCheckResult, NoteId
from anki.utils import html_to_text_line, is_mac
from aqt import AnkiQt, gui_hooks
from aqt.deckchooser import DeckChooser
from aqt.notetypechooser import NotetypeChooser
from aqt.operations.note import add_note
from aqt.qt import *
from aqt.sound import av_player
from aqt.utils import (
    HelpPage,
    askUser,
    downArrow,
    openHelp,
    restoreGeom,
    saveGeom,
    shortcut,
    showWarning,
    tooltip,
    tr,
)


class AddCards(QMainWindow):
    def __init__(self, mw: AnkiQt) -> None:
        super().__init__(None, Qt.WindowType.Window)
        self._close_event_has_cleaned_up = False
        self.mw = mw
        self.col = mw.col
        form = aqt.forms.addcards.Ui_Dialog()
        form.setupUi(self)
        self.form = form
        self.setWindowTitle(tr.actions_add())
        self.setMinimumHeight(300)
        self.setMinimumWidth(400)
        self.setup_choosers()
        self.setupEditor()
        self.setupButtons()
        self._load_new_note()
        self.history: list[NoteId] = []
        self._last_added_note: Optional[Note] = None
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        restoreGeom(self, "add")
        gui_hooks.add_cards_did_init(self)
        self.show()

    def set_note(self, note: Note, deck_id: DeckId | None = None) -> None:
        """Set tags, field contents and notetype according to `note`. Deck is set
        to `deck_id` or the deck last used with the notetype.
        """
        self.notetype_chooser.selected_notetype_id = note.mid
        if deck_id or (deck_id := self.col.default_deck_for_notetype(note.mid)):
            self.deck_chooser.selected_deck_id = deck_id

        new_note = self._new_note()
        new_note.fields = note.fields
        new_note.tags = note.tags

        self.setAndFocusNote(new_note)

    def setupEditor(self) -> None:
        self.editor = aqt.editor.Editor(
            self.mw,
            self.form.fieldsArea,
            self,
            editor_mode=aqt.editor.EditorMode.ADD_CARDS,
        )

    def setup_choosers(self) -> None:
        defaults = self.col.defaults_for_adding(
            current_review_card=self.mw.reviewer.card
        )
        self.notetype_chooser = NotetypeChooser(
            mw=self.mw,
            widget=self.form.modelArea,
            starting_notetype_id=NotetypeId(defaults.notetype_id),
            on_button_activated=self.show_notetype_selector,
            on_notetype_changed=self.on_notetype_change,
        )
        self.deck_chooser = DeckChooser(
            self.mw,
            self.form.deckArea,
            starting_deck_id=DeckId(defaults.deck_id),
            on_deck_changed=self.on_deck_changed,
        )

    def helpRequested(self) -> None:
        openHelp(HelpPage.ADDING_CARD_AND_NOTE)

    def setupButtons(self) -> None:
        bb = self.form.buttonBox
        ar = QDialogButtonBox.ButtonRole.ActionRole
        # add
        self.addButton = bb.addButton(tr.actions_add(), ar)
        qconnect(self.addButton.clicked, self.add_current_note)
        self.addButton.setShortcut(QKeySequence("Ctrl+Return"))
        # qt5.14 doesn't handle numpad enter on Windows
        self.compat_add_shorcut = QShortcut(QKeySequence("Ctrl+Enter"), self)
        qconnect(self.compat_add_shorcut.activated, self.addButton.click)
        self.addButton.setToolTip(shortcut(tr.adding_add_shortcut_ctrlandenter()))
        # close
        self.closeButton = QPushButton(tr.actions_close())
        self.closeButton.setAutoDefault(False)
        bb.addButton(self.closeButton, QDialogButtonBox.ButtonRole.RejectRole)
        qconnect(self.closeButton.clicked, self.close)
        # help
        self.helpButton = QPushButton(tr.actions_help(), clicked=self.helpRequested)  # type: ignore
        self.helpButton.setAutoDefault(False)
        bb.addButton(self.helpButton, QDialogButtonBox.ButtonRole.HelpRole)
        # history
        b = bb.addButton(f"{tr.adding_history()} {downArrow()}", ar)
        if is_mac:
            sc = "Ctrl+Shift+H"
        else:
            sc = "Ctrl+H"
        b.setShortcut(QKeySequence(sc))
        b.setToolTip(tr.adding_shortcut(val=shortcut(sc)))
        qconnect(b.clicked, self.onHistory)
        b.setEnabled(False)
        self.historyButton = b

    def setAndFocusNote(self, note: Note) -> None:
        self.editor.set_note(note, focusTo=0)

    def show_notetype_selector(self) -> None:
        self.editor.call_after_note_saved(self.notetype_chooser.choose_notetype)

    def on_deck_changed(self, deck_id: int) -> None:
        gui_hooks.add_cards_did_change_deck(deck_id)

    def on_notetype_change(self, notetype_id: NotetypeId) -> None:
        # need to adjust current deck?
        if deck_id := self.col.default_deck_for_notetype(notetype_id):
            self.deck_chooser.selected_deck_id = deck_id

        # only used for detecting changed sticky fields on close
        self._last_added_note = None

        # copy fields into new note with the new notetype
        old_note = self.editor.note
        new_note = self._new_note()
        if old_note:
            old_field_names = list(old_note.keys())
            new_field_names = list(new_note.keys())
            copied_field_names = set()
            for f in new_note.note_type()["flds"]:
                field_name = f["name"]
                # copy identical non-empty fields
                if field_name in old_field_names and old_note[field_name]:
                    new_note[field_name] = old_note[field_name]
                    copied_field_names.add(field_name)
            new_idx = 0
            for old_idx, old_field_value in enumerate(old_field_names):
                # skip previously copied identical fields in new note
                while (
                    new_idx < len(new_field_names)
                    and new_field_names[new_idx] in copied_field_names
                ):
                    new_idx += 1
                if new_idx >= len(new_field_names):
                    break
                # copy non-empty old fields
                if (
                    not old_field_value in copied_field_names
                    and old_note.fields[old_idx]
                ):
                    new_note.fields[new_idx] = old_note.fields[old_idx]
                    new_idx += 1

            new_note.tags = old_note.tags

        # and update editor state
        self.editor.note = new_note
        self.editor.loadNote(
            focusTo=min(self.editor.last_field_index or 0, len(new_note.fields) - 1)
        )
        gui_hooks.add_cards_did_change_note_type(
            old_note.note_type(), new_note.note_type()
        )

    def _load_new_note(self, sticky_fields_from: Optional[Note] = None) -> None:
        note = self._new_note()
        if old_note := sticky_fields_from:
            flds = note.note_type()["flds"]
            # copy fields from old note
            if old_note:
                for n in range(min(len(note.fields), len(old_note.fields))):
                    if flds[n]["sticky"]:
                        note.fields[n] = old_note.fields[n]
            # and tags
            note.tags = old_note.tags
        self.setAndFocusNote(note)

    def on_operation_did_execute(
        self, changes: OpChanges, handler: Optional[object]
    ) -> None:
        if (changes.notetype or changes.deck) and handler is not self.editor:
            self.on_notetype_change(
                NotetypeId(
                    self.col.defaults_for_adding(
                        current_review_card=self.mw.reviewer.card
                    ).notetype_id
                )
            )

    def _new_note(self) -> Note:
        return self.col.new_note(
            self.col.models.get(self.notetype_chooser.selected_notetype_id)
        )

    def addHistory(self, note: Note) -> None:
        self.history.insert(0, note.id)
        self.history = self.history[:15]
        self.historyButton.setEnabled(True)

    def onHistory(self) -> None:
        m = QMenu(self)
        for nid in self.history:
            if self.col.find_notes(self.col.build_search_string(SearchNode(nid=nid))):
                note = self.col.get_note(nid)
                fields = note.fields
                txt = html_to_text_line(", ".join(fields))
                if len(txt) > 30:
                    txt = f"{txt[:30]}..."
                line = tr.adding_edit(val=txt)
                line = gui_hooks.addcards_will_add_history_entry(line, note)
                line = line.replace("&", "&&")
                # In qt action "&i" means "underline i, trigger this line when i is pressed".
                # except for "&&" which is replaced by a single "&"
                a = m.addAction(line)
                qconnect(a.triggered, lambda b, nid=nid: self.editHistory(nid))
            else:
                a = m.addAction(tr.adding_note_deleted())
                a.setEnabled(False)
        gui_hooks.add_cards_will_show_history_menu(self, m)
        m.exec(self.historyButton.mapToGlobal(QPoint(0, 0)))

    def editHistory(self, nid: NoteId) -> None:
        aqt.dialogs.open("Browser", self.mw, search=(SearchNode(nid=nid),))

    def add_current_note(self) -> None:
        self.editor.call_after_note_saved(self._add_current_note)

    def _add_current_note(self) -> None:
        note = self.editor.note

        if not self._note_can_be_added(note):
            return

        target_deck_id = self.deck_chooser.selected_deck_id

        def on_success(changes: OpChanges) -> None:
            # only used for detecting changed sticky fields on close
            self._last_added_note = note

            self.addHistory(note)

            tooltip(tr.adding_added(), period=500)
            av_player.stop_and_clear_queue()
            self._load_new_note(sticky_fields_from=note)
            gui_hooks.add_cards_did_add_note(note)

        add_note(parent=self, note=note, target_deck_id=target_deck_id).success(
            on_success
        ).run_in_background()

    def _note_can_be_added(self, note: Note) -> bool:
        result = note.fields_check()
        # no problem, duplicate, and confirmed cloze cases
        problem = None
        if result == NoteFieldsCheckResult.EMPTY:
            problem = tr.adding_the_first_field_is_empty()
        elif result == NoteFieldsCheckResult.MISSING_CLOZE:
            if not askUser(tr.adding_you_have_a_cloze_deletion_note()):
                return False
        elif result == NoteFieldsCheckResult.NOTETYPE_NOT_CLOZE:
            problem = tr.adding_cloze_outside_cloze_notetype()
        elif result == NoteFieldsCheckResult.FIELD_NOT_CLOZE:
            problem = tr.adding_cloze_outside_cloze_field()

        # filter problem through add-ons
        problem = gui_hooks.add_cards_will_add_note(problem, note)
        if problem is not None:
            showWarning(problem, help=HelpPage.ADDING_CARD_AND_NOTE)
            return False

        return True

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() == Qt.Key.Key_Escape:
            self.close()
        else:
            super().keyPressEvent(evt)

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._close_event_has_cleaned_up:
            evt.accept()
            return
        self.ifCanClose(self._close)
        evt.ignore()

    def _close(self) -> None:
        av_player.stop_and_clear_queue()
        self.editor.cleanup()
        self.notetype_chooser.cleanup()
        self.deck_chooser.cleanup()
        gui_hooks.operation_did_execute.remove(self.on_operation_did_execute)
        self.mw.maybeReset()
        saveGeom(self, "add")
        aqt.dialogs.markClosed("AddCards")
        self._close_event_has_cleaned_up = True
        self.mw.deferred_delete_and_garbage_collect(self)
        self.close()

    def ifCanClose(self, onOk: Callable) -> None:
        def afterSave() -> None:
            ok = self.editor.fieldsAreBlank(self._last_added_note) or askUser(
                tr.adding_close_and_lose_current_input(), defaultno=True
            )
            if ok:
                onOk()

        self.editor.call_after_note_saved(afterSave)

    def closeWithCallback(self, cb: Callable[[], None]) -> None:
        def doClose() -> None:
            self._close()
            cb()

        self.ifCanClose(doClose)

    # legacy aliases

    @property
    def deckChooser(self) -> DeckChooser:
        if getattr(self, "form", None):
            # show this warning only after Qt form has been initialized,
            # or PyQt's introspection triggers it
            print("deckChooser is deprecated; use deck_chooser instead")
        return self.deck_chooser

    addCards = add_current_note
    _addCards = _add_current_note
    onModelChange = on_notetype_change

    @deprecated(info="obsolete")
    def addNote(self, note: Note) -> None:
        pass

    @deprecated(info="does nothing; will go away")
    def removeTempNote(self, note: Note) -> None:
        pass
