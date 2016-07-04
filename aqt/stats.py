# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import os, time
from aqt.utils import saveGeom, restoreGeom, maybeHideClose, showInfo, addCloseShortcut
import aqt

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
        f.setupUi(self)
        restoreGeom(self, self.name)
        b = f.buttonBox.addButton(_("Save Image"),
                                          QDialogButtonBox.ActionRole)
        b.clicked.connect(self.browser)
        b.setAutoDefault(False)
        f.groups.clicked.connect(lambda: self.changeScope("deck"))
        f.groups.setShortcut("g")
        f.all.clicked.connect(lambda: self.changeScope("collection"))
        f.month.clicked.connect(lambda: self.changePeriod(0))
        f.year.clicked.connect(lambda: self.changePeriod(1))
        f.life.clicked.connect(lambda: self.changePeriod(2))
        maybeHideClose(self.form.buttonBox)
        addCloseShortcut(self)
        self.refresh()
        self.show()
        print("fixme: save image support in deck stats")

    def reject(self):
        saveGeom(self, self.name)
        QDialog.reject(self)

    def browser(self):
        name = time.strftime("-%Y-%m-%d@%H-%M-%S.png",
                             time.localtime(time.time()))
        name = "anki-"+_("stats")+name
        desktopPath = QDesktopServices.storageLocation(QDesktopServices.DesktopLocation)
        if not os.path.exists(desktopPath):
            os.mkdir(desktopPath)
        path = os.path.join(desktopPath, name)
        p = self.form.web.page()
        oldsize = p.viewportSize()
        p.setViewportSize(p.mainFrame().contentsSize())
        image = QImage(p.viewportSize(), QImage.Format_ARGB32)
        painter = QPainter(image)
        p.mainFrame().render(painter)
        painter.end()
        isOK = image.save(path, "png")
        if isOK:
            showInfo(_("An image was saved to your desktop."))
        else:
            showInfo(_("""\
Anki could not save the image. Please check that you have permission to write \
to your desktop."""))
        p.setViewportSize(oldsize)

    def changePeriod(self, n):
        self.period = n
        self.refresh()

    def changeScope(self, type):
        self.wholeCollection = type == "collection"
        self.refresh()

    def refresh(self):
        self.mw.progress.start(immediate=True)
        stats = self.mw.col.stats()
        stats.wholeCollection = self.wholeCollection
        self.report = stats.report(type=self.period)
        self.form.web.stdHtml("<html><body>"+self.report+"</body></html>")
        self.mw.progress.finish()
