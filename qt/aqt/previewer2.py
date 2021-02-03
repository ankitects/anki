# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import json

import aqt
from anki.consts import MODEL_STD, MODEL_CLOZE
from anki.notes import Note
from anki.models import NoteType
from aqt import gui_hooks
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import (
    TR,
    addCloseShortcut,
    disable_help_button,
    maybeHideClose,
    restoreGeom,
    saveGeom,
    tr,
)


def is_cloze(model: NoteType) -> bool:
    return model["type"] == MODEL_CLOZE


class Previewer(QDialog):
    def __init__(self, parent: QDialog):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.name = "previewer"
        self.form = aqt.forms.previewer.Ui_Dialog()
        self.setMinimumWidth(400);
        disable_help_button(self)
        f = self.form
        f.setupUi(self)
        restoreGeom(self, self.name)
        addCloseShortcut(self)
        self.show()
        self.refresh()
        self.form.web.set_bridge_command(self._on_bridge_cmd, self)
        self.activateWindow()

    def reject(self):
        self.form.web = None
        saveGeom(self, self.name)
        aqt.dialogs.markClosed("Previewer")
        QDialog.reject(self)

    def closeWithCallback(self, callback):
        self.reject()
        callback()

    def _on_bridge_cmd(self, cmd: str) -> bool:
        return False

    def show_note(self, note: Note) -> None:
        model = note.model()

        if is_cloze(model):
            cards = [note.ephemeral_card(index) for index in note.cloze_numbers_in_fields()]
            data = json.dumps([[card.question(), card.answer()] for card in cards])
            self.form.web.eval(f"anki.setPreviewerClozeNote({data})")
        else:
            cards = [[note.ephemeral_card(index), tmp["name"]] for index, tmp in enumerate(model["tmpls"])]
            data = json.dumps([[card.question(), card.answer(), tmplname] for card, tmplname in cards])
            self.form.web.eval(f"anki.setPreviewerNote({data})")

    def refresh(self):
        self.form.web.load_ts_page("previewer")
