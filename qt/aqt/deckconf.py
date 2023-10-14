# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from operator import itemgetter
from typing import Any

import aqt
import aqt.forms
from anki.consts import NEW_CARDS_RANDOM
from anki.decks import DeckConfigDict
from anki.lang import without_unicode_isolation
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    HelpPage,
    askUser,
    disable_help_button,
    getOnlyText,
    openHelp,
    restoreGeom,
    saveGeom,
    showInfo,
    showWarning,
    tooltip,
    tr,
)


class DeckConf(QDialog):
    def __init__(self, mw: aqt.AnkiQt, deck: dict) -> None:
        QDialog.__init__(self, mw)
        self.mw = mw
        self.deck = deck
        self.childDids = [d[1] for d in self.mw.col.decks.children(self.deck["id"])]
        self._origNewOrder = None
        self.form = aqt.forms.dconf.Ui_Dialog()
        self.form.setupUi(self)
        gui_hooks.deck_conf_did_setup_ui_form(self)
        self.mw.checkpoint(tr.actions_options())
        self.setupCombos()
        self.setupConfs()
        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.DECK_OPTIONS)
        )
        qconnect(self.form.confOpts.clicked, self.confOpts)
        qconnect(
            self.form.buttonBox.button(
                QDialogButtonBox.StandardButton.RestoreDefaults
            ).clicked,
            self.onRestore,
        )
        self.setWindowTitle(
            without_unicode_isolation(tr.actions_options_for(val=self.deck["name"]))
        )
        disable_help_button(self)
        # qt doesn't size properly with altered fonts otherwise
        restoreGeom(self, "deckconf", adjustSize=True)
        gui_hooks.deck_conf_will_show(self)
        self.open()
        saveGeom(self, "deckconf")

    def setupCombos(self) -> None:
        import anki.consts as cs

        f = self.form
        f.newOrder.addItems(list(cs.new_card_order_labels(self.mw.col).values()))
        qconnect(f.newOrder.currentIndexChanged, self.onNewOrderChanged)

    # Conf list
    ######################################################################

    def setupConfs(self) -> None:
        qconnect(self.form.dconf.currentIndexChanged, self.onConfChange)
        self.conf: DeckConfigDict | None = None
        self.loadConfs()

    def loadConfs(self) -> None:
        current = self.deck["conf"]
        self.confList = self.mw.col.decks.all_config()
        self.confList.sort(key=itemgetter("name"))
        startOn = 0
        self.ignoreConfChange = True
        self.form.dconf.clear()
        for idx, conf in enumerate(self.confList):
            self.form.dconf.addItem(conf["name"])
            if str(conf["id"]) == str(current):
                startOn = idx
        self.ignoreConfChange = False
        self.form.dconf.setCurrentIndex(startOn)
        if self._origNewOrder is None:
            self._origNewOrder = self.confList[startOn]["new"]["order"]
        self.onConfChange(startOn)

    def confOpts(self) -> None:
        m = QMenu(self.mw)
        a = m.addAction(tr.actions_add())
        qconnect(a.triggered, self.addGroup)
        a = m.addAction(tr.actions_delete())
        qconnect(a.triggered, self.remGroup)
        a = m.addAction(tr.actions_rename())
        qconnect(a.triggered, self.renameGroup)
        a = m.addAction(tr.scheduling_set_for_all_subdecks())
        qconnect(a.triggered, self.setChildren)
        if not self.childDids:
            a.setEnabled(False)
        m.exec(QCursor.pos())

    def onConfChange(self, idx: int) -> None:
        if self.ignoreConfChange:
            return
        if self.conf:
            self.saveConf()
        conf = self.confList[idx]
        self.deck["conf"] = conf["id"]
        self.mw.col.decks.save(self.deck)
        self.loadConf()
        cnt = len(self.mw.col.decks.decks_using_config(conf))
        if cnt > 1:
            txt = tr.scheduling_your_changes_will_affect_multiple_decks()
        else:
            txt = ""
        self.form.count.setText(txt)

    def addGroup(self) -> None:
        name = getOnlyText(tr.scheduling_new_options_group_name())
        if not name:
            return

        # first, save currently entered data to current conf
        self.saveConf()
        # then clone the conf
        id = self.mw.col.decks.add_config_returning_id(name, clone_from=self.conf)
        gui_hooks.deck_conf_did_add_config(self, self.deck, self.conf, name, id)
        # set the deck to the new conf
        self.deck["conf"] = id
        # then reload the conf list
        self.loadConfs()

    def remGroup(self) -> None:
        if int(self.conf["id"]) == 1:
            showInfo(tr.scheduling_the_default_configuration_cant_be_removed(), self)
        else:
            gui_hooks.deck_conf_will_remove_config(self, self.deck, self.conf)
            self.mw.col.mod_schema(check=True)
            self.mw.col.decks.remove_config(self.conf["id"])
            self.conf = None
            self.deck["conf"] = 1
            self.loadConfs()

    def renameGroup(self) -> None:
        old = self.conf["name"]
        name = getOnlyText(tr.actions_new_name(), default=old)
        if not name or name == old:
            return

        gui_hooks.deck_conf_will_rename_config(self, self.deck, self.conf, name)
        self.conf["name"] = name
        self.saveConf()
        self.loadConfs()

    def setChildren(self) -> None:
        if not askUser(tr.scheduling_set_all_decks_below_to(val=self.deck["name"])):
            return
        for did in self.childDids:
            deck = self.mw.col.decks.get(did)
            if deck["dyn"]:
                continue
            deck["conf"] = self.deck["conf"]
            self.mw.col.decks.save(deck)
        tooltip(tr.scheduling_deck_updated(count=len(self.childDids)))

    # Loading
    ##################################################

    def listToUser(self, l: list[Union[int, float]]) -> str:
        def num_to_user(n: Union[int, float]) -> str:
            if n == round(n):
                return str(int(n))
            else:
                return str(n)

        return " ".join(map(num_to_user, l))

    def parentLimText(self, type: str = "new") -> str:
        # top level?
        if "::" not in self.deck["name"]:
            return ""
        lim = -1
        for d in self.mw.col.decks.parents(self.deck["id"]):
            c = self.mw.col.decks.config_dict_for_deck_id(d["id"])
            x = c[type]["perDay"]
            if lim == -1:
                lim = x
            else:
                lim = min(x, lim)
        return tr.scheduling_parent_limit(val=lim)

    def loadConf(self) -> None:
        self.conf = self.mw.col.decks.config_dict_for_deck_id(self.deck["id"])
        # new
        c = self.conf["new"]
        f = self.form
        f.lrnSteps.setText(self.listToUser(c["delays"]))
        f.lrnGradInt.setValue(c["ints"][0])
        f.lrnEasyInt.setValue(c["ints"][1])
        f.lrnFactor.setValue(int(c["initialFactor"] / 10.0))
        f.newOrder.setCurrentIndex(c["order"])
        f.newPerDay.setValue(c["perDay"])
        f.bury.setChecked(c.get("bury", True))
        f.newplim.setText(self.parentLimText("new"))
        # rev
        c = self.conf["rev"]
        f.revPerDay.setValue(c["perDay"])
        f.easyBonus.setValue(int(c["ease4"] * 100))
        f.fi1.setValue(c["ivlFct"] * 100)
        f.maxIvl.setValue(c["maxIvl"])
        f.revplim.setText(self.parentLimText("rev"))
        f.buryRev.setChecked(c.get("bury", True))
        f.hardFactor.setValue(int(c.get("hardFactor", 1.2) * 100))
        # lapse
        c = self.conf["lapse"]
        f.lapSteps.setText(self.listToUser(c["delays"]))
        f.lapMult.setValue(int(c["mult"] * 100))
        f.lapMinInt.setValue(c["minInt"])
        f.leechThreshold.setValue(c["leechFails"])
        f.leechAction.setCurrentIndex(c["leechAction"])
        # general
        c = self.conf
        f.maxTaken.setValue(c["maxTaken"])
        f.showTimer.setChecked(c.get("timer", 0))
        f.autoplaySounds.setChecked(c["autoplay"])
        f.replayQuestion.setChecked(c.get("replayq", True))
        gui_hooks.deck_conf_did_load_config(self, self.deck, self.conf)

    def onRestore(self) -> None:
        self.mw.progress.start()
        self.mw.col.decks.restore_to_default(self.conf)
        self.mw.progress.finish()
        self.loadConf()

    # New order
    ##################################################

    def onNewOrderChanged(self, new: bool) -> None:
        old = self.conf["new"]["order"]
        if old == new:
            return
        self.conf["new"]["order"] = new
        self.mw.progress.start()
        self.mw.col.sched.resort_conf(self.conf)
        self.mw.progress.finish()

    # Saving
    ##################################################

    def updateList(self, conf: Any, key: str, w: QLineEdit, minSize: int = 1) -> None:
        items = str(w.text()).split(" ")
        ret = []
        for item in items:
            if not item:
                continue
            try:
                i = float(item)
                if i <= 0:
                    raise Exception("0 invalid")
                if i == int(i):
                    i = int(i)
                ret.append(i)
            except:
                # invalid, don't update
                showWarning(tr.scheduling_steps_must_be_numbers())
                return
        if len(ret) < minSize:
            showWarning(tr.scheduling_at_least_one_step_is_required())
            return
        conf[key] = ret

    def saveConf(self) -> None:
        # new
        c = self.conf["new"]
        f = self.form
        self.updateList(c, "delays", f.lrnSteps)
        c["ints"][0] = f.lrnGradInt.value()
        c["ints"][1] = f.lrnEasyInt.value()
        c["initialFactor"] = f.lrnFactor.value() * 10
        c["order"] = f.newOrder.currentIndex()
        c["perDay"] = f.newPerDay.value()
        c["bury"] = f.bury.isChecked()
        if self._origNewOrder != c["order"]:
            # order of current deck has changed, so have to resort
            if c["order"] == NEW_CARDS_RANDOM:
                self.mw.col.sched.randomize_cards(self.deck["id"])
            else:
                self.mw.col.sched.order_cards(self.deck["id"])
        # rev
        c = self.conf["rev"]
        c["perDay"] = f.revPerDay.value()
        c["ease4"] = f.easyBonus.value() / 100.0
        c["ivlFct"] = f.fi1.value() / 100.0
        c["maxIvl"] = f.maxIvl.value()
        c["bury"] = f.buryRev.isChecked()
        c["hardFactor"] = f.hardFactor.value() / 100.0
        # lapse
        c = self.conf["lapse"]
        self.updateList(c, "delays", f.lapSteps, minSize=0)
        c["mult"] = f.lapMult.value() / 100.0
        c["minInt"] = f.lapMinInt.value()
        c["leechFails"] = f.leechThreshold.value()
        c["leechAction"] = f.leechAction.currentIndex()
        # general
        c = self.conf
        c["maxTaken"] = f.maxTaken.value()
        c["timer"] = f.showTimer.isChecked() and 1 or 0
        c["autoplay"] = f.autoplaySounds.isChecked()
        c["replayq"] = f.replayQuestion.isChecked()
        gui_hooks.deck_conf_will_save_config(self, self.deck, self.conf)
        self.mw.col.decks.save(self.deck)
        self.mw.col.decks.save(self.conf)

    def reject(self) -> None:
        self.accept()

    def accept(self) -> None:
        self.saveConf()
        self.mw.reset()
        QDialog.accept(self)
