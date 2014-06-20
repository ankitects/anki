# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import aqt
from aqt.utils import  showWarning, openHelp, askUser, saveGeom, restoreGeom

class DeckConf(QDialog):
    def __init__(self, mw, first=False, search="", deck=None):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.deck = deck or self.mw.col.decks.current()
        self.search = search
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
                     lambda: openHelp("filtered"))
        self.setWindowTitle(_("Options for %s") % self.deck['name'])
        restoreGeom(self, "dyndeckconf")
        self.setupOrder()
        self.loadConf()
        if search:
            self.form.search.setText(search)
        self.form.search.selectAll()
        self.show()
        self.exec_()
        saveGeom(self, "dyndeckconf")

    def setupOrder(self):
        import anki.consts as cs
        self.form.order.addItems(cs.dynOrderLabels().values())

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
        f.resched.setChecked(d['resched'])
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
        else:
            d['delays'] = None
        d['terms'][0] = [f.search.text(),
                         f.limit.value(),
                         f.order.currentIndex()]
        d['resched'] = f.resched.isChecked()
        self.mw.col.decks.save(d)
        return True

    def reject(self):
        self.ok = False
        QDialog.reject(self)

    def accept(self):
        if not self.saveConf():
            return
        if not self.mw.col.sched.rebuildDyn():
            if askUser(_("""\
The provided search did not match any cards. Would you like to revise \
it?""")):
                return
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
