# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
    def __init__(self, mw):
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
        if theme_manager.night_mode and not theme_manager.macos_dark_mode():
            self.setStyleSheet("QGroupBox { padding-top: 0; }")
        f.setupUi(self)
        restoreGeom(self, self.name)
        b = f.buttonBox.addButton(_("Save PDF"), QDialogButtonBox.ActionRole)
        b.clicked.connect(self.saveImage)
        b.setAutoDefault(False)
        f.groups.clicked.connect(lambda: self.changeScope("deck"))
        f.groups.setShortcut("g")
        f.all.clicked.connect(lambda: self.changeScope("collection"))
        f.month.clicked.connect(lambda: self.changePeriod(0))
        f.year.clicked.connect(lambda: self.changePeriod(1))
        f.life.clicked.connect(lambda: self.changePeriod(2))
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
        self.period = n
        self.refresh()

    def changeScope(self, type):
        self.wholeCollection = type == "collection"
        self.refresh()

    def refresh(self):
        self.mw.progress.start(immediate=True, parent=self)
        stats = self.mw.col.stats()
        stats.wholeCollection = self.wholeCollection
        self.report = stats.report(type=self.period)
        self.form.web.title = "deck stats"
        self.form.web.stdHtml(
            "<html><body>" + self.report + "</body></html>",
            js=["jquery.js", "plot.js"],
            context=self,
        )
        self.mw.progress.finish()
