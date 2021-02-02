# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import json

import aqt
from anki.consts import MODEL_STD
from anki.notes import Note
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


class Previewer(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt):
        QDialog.__init__(self, mw, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = mw
        self.name = "previewer"
        self.form = aqt.forms.previewer.Ui_Dialog()
        self.setMinimumWidth(700)
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
        data = json.dumps([[card.question(), card.answer()] for card in note.cards()])
        model = note.model()

        if model["type"] == MODEL_STD:
            card_type_names = [tmpl["name"] for tmpl in model["tmpls"]]
            self.form.web.eval(f"anki.setPreviewerNote({data}, {card_type_names})")
        else:
            self.form.web.eval(f"anki.setPreviewerClozeNote({data})")

    def refresh(self):
        self.form.web.load_ts_page("previewer")
