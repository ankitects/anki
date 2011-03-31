# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import aqt

class DeckOptions(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.d = mw.deck
        self.form = aqt.forms.deckopts.Ui_Dialog()
        self.form.setupUi(self)
        self.setup()
        self.exec_()

    def setup(self):
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.helpRequested)
        # syncing
        self.form.doSync.setChecked(self.d.syncingEnabled())
        self.form.mediaURL.setText(self.d.conf['mediaURL'])
        # latex
        self.form.latexHeader.setText(self.d.conf['latexPre'])
        self.form.latexFooter.setText(self.d.conf['latexPost'])

    def helpRequested(self):
        aqt.openHelp("DeckOptions")

    def reject(self):
        needSync = False
        # syncing
        if self.form.doSync.isChecked():
            old = self.d.syncName
            self.d.enableSyncing()
            if self.d.syncName != old:
                needSync = True
        else:
            self.d.disableSyncing()
        url = unicode(self.form.mediaURL.text())
        if url:
            if not re.match("^(http|https|ftp)://", url, re.I):
                url = "http://" + url
            if not url.endswith("/"):
                url += "/"
        self.d.conf['mediaURL'] = url
        # latex
        self.d.conf['latexPre'] = unicode(self.form.latexHeader.toPlainText())
        self.d.conf['latexPost'] = unicode(self.form.latexFooter.toPlainText())
        QDialog.reject(self)
        if needSync:
            aqt.mw.syncDeck(interactive=-1)
