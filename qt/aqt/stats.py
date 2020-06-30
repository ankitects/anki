# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time

import aqt
from anki.lang import _
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import (
    addCloseShortcut,
    getSaveFile,
    maybeHideClose,
    restoreGeom,
    saveGeom,
    tooltip,
)

# Deck Stats
######################################################################


class DeckStats(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt):
        QDialog.__init__(self, mw, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = mw
        self.name = "deckStats"
        self.period = 0
        self.form = aqt.forms.stats.Ui_Dialog()
        self.oldPos = None
        self.wholeCollection = False
        self.setMinimumWidth(700)
        f = self.form
        f.setupUi(self)
        # no night mode support yet
        f.web._page.setBackgroundColor(QColor("white"))
        f.groupBox.setVisible(False)
        f.groupBox_2.setVisible(False)
        restoreGeom(self, self.name)
        b = f.buttonBox.addButton(_("Save PDF"), QDialogButtonBox.ActionRole)
        qconnect(b.clicked, self.saveImage)
        b.setAutoDefault(False)
        maybeHideClose(self.form.buttonBox)
        addCloseShortcut(self)
        self.show()
        self.refresh()
        self.activateWindow()

    def reject(self):
        self.form.web = None
        saveGeom(self, self.name)
        aqt.dialogs.markClosed("DeckStats")
        QDialog.reject(self)

    def closeWithCallback(self, callback):
        self.reject()
        callback()

    def _imagePath(self):
        name = time.strftime("-%Y-%m-%d@%H-%M-%S.pdf", time.localtime(time.time()))
        name = "anki-" + _("stats") + name
        file = getSaveFile(
            self,
            title=_("Save PDF"),
            dir_description="stats",
            key="stats",
            ext=".pdf",
            fname=name,
        )
        return file

    def saveImage(self):
        path = self._imagePath()
        if not path:
            return
        self.form.web.page().printToPdf(path)
        tooltip(_("Saved."))

    def changePeriod(self, n):
        pass

    def changeScope(self, type):
        pass

    def refresh(self):
        self.form.web.set_open_links_externally(False)
        if theme_manager.night_mode:
            extra = "#night"
        else:
            extra = ""
        self.form.web.load(QUrl(f"{self.mw.serverURL()}_anki/graphs.html"+extra))
