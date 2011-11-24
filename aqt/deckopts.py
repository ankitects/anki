# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import sys, re
import aqt
from aqt.utils import maybeHideClose

class ColOptions(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.d = mw.col
        self.form = aqt.forms.colopts.Ui_Dialog()
        self.form.setupUi(self)
        self.setup()
        self.exec_()

    def setup(self):
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.helpRequested)
        maybeHideClose(self.form.buttonBox)
        # syncing
        self.form.doSync.setChecked(self.d.syncingEnabled())
        self.form.mediaURL.setText(self.d.conf['mediaURL'])

    def helpRequested(self):
        aqt.openHelp("ColOptions")

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
        QDialog.reject(self)
        if needSync:
            aqt.mw.syncCol(interactive=-1)
