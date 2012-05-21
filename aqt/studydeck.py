# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import aqt
from anki.utils import ids2str
from aqt.utils import showInfo, showWarning, openHelp, getOnlyText
from operator import itemgetter

class StudyDeck(QDialog):
    def __init__(self, mw, names=None, accept=None, title=None,
                 help="studydeck", parent=None):
        QDialog.__init__(self, parent or mw)
        self.mw = mw
        self.form = aqt.forms.studydeck.Ui_Dialog()
        self.form.setupUi(self)
        self.form.filter.installEventFilter(self)
        if title:
            self.setWindowTitle(title)
        if not names:
            names = sorted(self.mw.col.decks.allNames())
        self.origNames = names
        self.name = None
        self.ok = self.form.buttonBox.addButton(
            accept or _("Study"), QDialogButtonBox.AcceptRole)
        self.setWindowModality(Qt.WindowModal)
        self.connect(self.form.buttonBox,
                     SIGNAL("helpRequested()"),
                     lambda: openHelp(help))
        self.connect(self.form.filter,
                     SIGNAL("textEdited(QString)"),
                     self.redraw)
        self.redraw("")
        self.exec_()

    def eventFilter(self, obj, evt):
        if evt.type() == QEvent.KeyPress:
            if evt.key() == Qt.Key_Up:
                c = self.form.list.count()
                row = self.form.list.currentRow() - 1
                if row < 0:
                    row = c - 1
                self.form.list.setCurrentRow(row)
                return True
            elif evt.key() == Qt.Key_Down:
                c = self.form.list.count()
                row = self.form.list.currentRow() + 1
                if row == c:
                    row = 0
                self.form.list.setCurrentRow(row)
                return True
        return False

    def redraw(self, filt):
        self.names = [n for n in self.origNames if self._matches(n, filt)]
        self.form.list.clear()
        self.form.list.addItems(self.names)
        self.form.list.setCurrentRow(0)

    def _matches(self, name, filt):
        name = name.lower()
        filt = filt.lower()
        if not filt:
            return True
        for c in filt:
            if c not in name:
                return False
            name = name[name.index(c):]
        return True

    def accept(self):
        self.name = self.names[self.form.list.currentRow()]
        QDialog.accept(self)
