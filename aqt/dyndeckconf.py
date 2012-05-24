# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import aqt
from anki.utils import ids2str
from aqt.utils import showInfo, showWarning, openHelp, getOnlyText
from operator import itemgetter

class DeckConf(QDialog):
    def __init__(self, mw, first=False, search=""):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.deck = self.mw.col.decks.current()
        self.form = aqt.forms.dyndconf.Ui_Dialog()
        self.form.setupUi(self)
        if first:
            label = _("Build")
        else:
            label = _("Rebuild")
        self.ok = self.form.buttonBox.addButton(
            label, QDialogButtonBox.AcceptRole)
        self.mw.checkpoint(_("Options"))
        self.setWindowModality(Qt.WindowModal)
        self.connect(self.form.buttonBox,
                     SIGNAL("helpRequested()"),
                     lambda: openHelp("cramming"))
        self.setWindowTitle(_("Options for %s") % self.deck['name'])
        self.setupCombos()
        self.loadConf()
        if first and search:
            self.form.search.setText(search)
        self.exec_()

    def setupCombos(self):
        import anki.consts as cs
        f = self.form
        f.order.addItems(cs.dynOrderLabels().values())

    def loadConf(self):
        f = self.form
        d = self.deck
        search, limit, order = d['terms'][0]
        f.search.setText(search)
        if d['delays']:
            f.steps.setText(self.listToUser(d['delays']))
            f.stepsOn.setChecked(True)
        else:
            f.steps.setText("1 10")
            f.stepsOn.setChecked(False)
        f.order.setCurrentIndex(order)
        f.limit.setValue(limit)

    def saveConf(self):
        f = self.form
        d = self.deck
        d['delays'] = None
        if f.stepsOn.isChecked():
            steps = self.userToList(f.steps)
            if steps:
                d['delays'] = steps
        d['terms'][0] = [f.search.text(),
                         f.limit.value(),
                         f.order.currentIndex()]
        self.mw.col.decks.save(d)
        return True

    def reject(self):
        self.ok = False
        QDialog.reject(self)

    def accept(self):
        if not self.saveConf():
            return
        self.mw.col.sched.rebuildDyn()
        self.mw.reset()
        QDialog.accept(self)

    # Step load/save - fixme: share with std options screen
    ########################################################

    def listToUser(self, l):
        return " ".join([str(x) for x in l])

    def userToList(self, w, minSize=1):
        items = unicode(w.text()).split(" ")
        ret = []
        for i in items:
            if not i:
                continue
            try:
                i = float(i)
                assert i > 0
                if i == int(i):
                    i = int(i)
                ret.append(i)
            except:
                # invalid, don't update
                showWarning(_("Steps must be numbers."))
                return
        if len(ret) < minSize:
            showWarning(_("At least one step is required."))
            return
        return ret
