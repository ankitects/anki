# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Callable, List, Optional

import aqt
from anki.collection import SearchTerm
from anki.decks import Deck
from anki.errors import InvalidInput
from anki.lang import without_unicode_isolation
from aqt.qt import *
from aqt.utils import (
    TR,
    HelpPage,
    askUser,
    disable_help_button,
    openHelp,
    restoreGeom,
    saveGeom,
    show_invalid_search_error,
    showWarning,
    tr,
)


class DeckConf(QDialog):
    """Dialogue to modify and build a filtered deck."""

    def __init__(self, mw, search: Optional[str] = None, deck: Optional[Deck] = None):
        """If 'deck' is an existing filtered deck, load and modify its settings.
        Otherwise, build a new one and derive settings from the current deck.
        """

        QDialog.__init__(self, mw)
        self.mw = mw
        self.did: Optional[int] = None
        self.form = aqt.forms.dyndconf.Ui_Dialog()
        self.form.setupUi(self)
        self.mw.checkpoint(tr(TR.ACTIONS_OPTIONS))
        self.initialSetup()
        self.old_deck = self.mw.col.decks.current()

        if deck and deck["dyn"]:
            # modify existing dyn deck
            label = tr(TR.ACTIONS_REBUILD)
            self.deck = deck
            self.loadConf()
        elif self.old_deck["dyn"]:
            # create new dyn deck from other dyn deck
            label = tr(TR.DECKS_BUILD)
            self.loadConf(deck=self.old_deck)
            self.new_dyn_deck()
        else:
            # create new dyn deck from regular deck
            label = tr(TR.DECKS_BUILD)
            self.new_dyn_deck()
            self.loadConf()
            self.set_default_searches(self.old_deck["name"])
        if search is not None:
            self.form.search.setText(search)

        disable_help_button(self)
        self.setWindowModality(Qt.WindowModal)
        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.FILTERED_DECK)
        )
        self.setWindowTitle(
            without_unicode_isolation(tr(TR.ACTIONS_OPTIONS_FOR, val=self.deck["name"]))
        )
        self.form.buttonBox.addButton(label, QDialogButtonBox.AcceptRole)
        self.form.search.selectAll()
        if self.mw.col.schedVer() == 1:
            self.form.secondFilter.setVisible(False)
        restoreGeom(self, "dyndeckconf")

        self.show()

    def reopen(self, _mw, search: Optional[str] = None, _deck: Optional[Deck] = None):
        if search is not None:
            self.form.search.setText(search)
        self.form.search.selectAll()

    def new_dyn_deck(self):
        suffix: int = 1
        while self.mw.col.decks.id_for_name(
            without_unicode_isolation(tr(TR.QT_MISC_FILTERED_DECK, val=suffix))
        ):
            suffix += 1
        name: str = without_unicode_isolation(tr(TR.QT_MISC_FILTERED_DECK, val=suffix))
        self.did = self.mw.col.decks.new_filtered(name)
        self.deck = self.mw.col.decks.current()

    def set_default_searches(self, deck_name):
        self.form.search.setText(
            self.mw.col.build_search_string(
                SearchTerm(deck=deck_name),
                SearchTerm(card_state=SearchTerm.CARD_STATE_DUE),
            )
        )
        self.form.search_2.setText(
            self.mw.col.build_search_string(
                SearchTerm(deck=deck_name),
                SearchTerm(card_state=SearchTerm.CARD_STATE_NEW),
            )
        )

    def initialSetup(self):
        import anki.consts as cs

        self.form.order.addItems(list(cs.dynOrderLabels(self.mw.col).values()))
        self.form.order_2.addItems(list(cs.dynOrderLabels(self.mw.col).values()))

        qconnect(self.form.resched.stateChanged, self._onReschedToggled)

    def _onReschedToggled(self, _state):
        self.form.previewDelayWidget.setVisible(
            not self.form.resched.isChecked() and self.mw.col.schedVer() > 1
        )

    def loadConf(self, deck: Optional[Deck] = None):
        f = self.form
        d = deck or self.deck

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

        search = self.mw.col.build_search_string(f.search.text())
        terms = [[search, f.limit.value(), f.order.currentIndex()]]

        if f.secondFilter.isChecked():
            search_2 = self.mw.col.build_search_string(f.search_2.text())
            terms.append([search_2, f.limit_2.value(), f.order_2.currentIndex()])

        d["terms"] = terms
        d["previewDelay"] = f.previewDelay.value()

        self.mw.col.decks.save(d)

    def reject(self):
        if self.did:
            self.mw.col.decks.rem(self.did)
            self.mw.col.decks.select(self.old_deck["id"])
        saveGeom(self, "dyndeckconf")
        QDialog.reject(self)
        aqt.dialogs.markClosed("DynDeckConfDialog")

    def accept(self):
        try:
            self.saveConf()
        except InvalidInput as err:
            show_invalid_search_error(err)
            return
        if not self.mw.col.sched.rebuild_filtered_deck(self.deck["id"]):
            if askUser(tr(TR.DECKS_THE_PROVIDED_SEARCH_DID_NOT_MATCH)):
                return
        saveGeom(self, "dyndeckconf")
        self.mw.reset()
        QDialog.accept(self)
        aqt.dialogs.markClosed("DynDeckConfDialog")

    def closeWithCallback(self, callback: Callable):
        self.reject()
        callback()

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
