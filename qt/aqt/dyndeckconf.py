# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import List, Optional

import aqt
from anki.lang import without_unicode_isolation
from aqt.qt import *
from aqt.utils import TR, askUser, openHelp, restoreGeom, saveGeom, showWarning, tr


class DeckConf(QDialog):
    def __init__(self, mw, first=False, search="", deck=None):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.deck = deck or self.mw.col.decks.current()
        self.search = search
        self.form = aqt.forms.dyndconf.Ui_Dialog()
        self.form.setupUi(self)
        if first:
            label = tr(TR.DECKS_BUILD)
        else:
            label = tr(TR.ACTIONS_REBUILD)
        self.ok = self.form.buttonBox.addButton(label, QDialogButtonBox.AcceptRole)
        self.mw.checkpoint(tr(TR.ACTIONS_OPTIONS))
        self.setWindowModality(Qt.WindowModal)
        qconnect(self.form.buttonBox.helpRequested, lambda: openHelp("filtered-decks"))
        self.setWindowTitle(
            without_unicode_isolation(tr(TR.ACTIONS_OPTIONS_FOR, val=self.deck["name"]))
        )
        restoreGeom(self, "dyndeckconf")
        self.initialSetup()
        self.loadConf()
        if search:
            self.form.search.setText(search + " is:due")
            self.form.search_2.setText(search + " is:new")
        self.form.search.selectAll()

        if self.mw.col.schedVer() == 1:
            self.form.secondFilter.setVisible(False)

        self.show()
        self.exec_()
        saveGeom(self, "dyndeckconf")

    def initialSetup(self):
        import anki.consts as cs

        self.form.order.addItems(list(cs.dynOrderLabels(self.mw.col).values()))
        self.form.order_2.addItems(list(cs.dynOrderLabels(self.mw.col).values()))

        qconnect(self.form.resched.stateChanged, self._onReschedToggled)

    def _onReschedToggled(self, _state):
        self.form.previewDelayWidget.setVisible(
            not self.form.resched.isChecked() and self.mw.col.schedVer() > 1
        )

    def loadConf(self):
        f = self.form
        d = self.deck

        f.resched.setChecked(d["resched"])
        self._onReschedToggled(0)

        search, limit, order = d["terms"][0]
        f.search.setText(search)

        if self.mw.col.schedVer() == 1:
            if d["delays"]:
                f.steps.setText(self.listToUser(d["delays"]))
                f.stepsOn.setChecked(True)
        else:
            f.steps.setVisible(False)
            f.stepsOn.setVisible(False)

        f.order.setCurrentIndex(order)
        f.limit.setValue(limit)
        f.previewDelay.setValue(d.get("previewDelay", 10))

        if len(d["terms"]) > 1:
            search, limit, order = d["terms"][1]
            f.search_2.setText(search)
            f.order_2.setCurrentIndex(order)
            f.limit_2.setValue(limit)
            f.secondFilter.setChecked(True)
            f.filter2group.setVisible(True)
        else:
            f.order_2.setCurrentIndex(5)
            f.limit_2.setValue(20)
            f.secondFilter.setChecked(False)
            f.filter2group.setVisible(False)

    def saveConf(self):
        f = self.form
        d = self.deck
        d["resched"] = f.resched.isChecked()
        d["delays"] = None

        if self.mw.col.schedVer() == 1 and f.stepsOn.isChecked():
            steps = self.userToList(f.steps)
            if steps:
                d["delays"] = steps
            else:
                d["delays"] = None

        terms = [[f.search.text(), f.limit.value(), f.order.currentIndex()]]

        if f.secondFilter.isChecked():
            terms.append(
                [f.search_2.text(), f.limit_2.value(), f.order_2.currentIndex()]
            )

        d["terms"] = terms
        d["previewDelay"] = f.previewDelay.value()

        self.mw.col.decks.save(d)
        return True

    def reject(self):
        self.ok = False
        QDialog.reject(self)

    def accept(self):
        if not self.saveConf():
            return
        if not self.mw.col.sched.rebuild_filtered_deck(self.deck["id"]):
            if askUser(tr(TR.DECKS_THE_PROVIDED_SEARCH_DID_NOT_MATCH)):
                return
        self.mw.reset()
        QDialog.accept(self)

    # Step load/save - fixme: share with std options screen
    ########################################################

    def listToUser(self, l):
        return " ".join([str(x) for x in l])

    def userToList(self, w, minSize=1) -> Optional[List[Union[float, int]]]:
        items = str(w.text()).split(" ")
        ret = []
        for item in items:
            if not item:
                continue
            try:
                i = float(item)
                assert i > 0
                if i == int(i):
                    i = int(i)
                ret.append(i)
            except:
                # invalid, don't update
                showWarning(tr(TR.SCHEDULING_STEPS_MUST_BE_NUMBERS))
                return None
        if len(ret) < minSize:
            showWarning(tr(TR.SCHEDULING_AT_LEAST_ONE_STEP_IS_REQUIRED))
            return None
        return ret
