# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import aqt
from anki.collection import SearchNode
from anki.consts import *
from aqt.qt import *
from aqt.utils import disable_help_button, showInfo, showWarning, tr

RADIO_NEW = 1
RADIO_REV = 2
RADIO_FORGOT = 3
RADIO_AHEAD = 4
RADIO_PREVIEW = 5
RADIO_CRAM = 6

TYPE_NEW = 0
TYPE_DUE = 1
TYPE_REVIEW = 2
TYPE_ALL = 3


class CustomStudy(QDialog):
    def __init__(self, mw: aqt.AnkiQt) -> None:
        QDialog.__init__(self, mw)
        self.mw = mw
        self.deck = self.mw.col.decks.current()
        self.conf = self.mw.col.decks.get_config(self.deck["conf"])
        self.form = f = aqt.forms.customstudy.Ui_Dialog()
        self.created_custom_study = False
        f.setupUi(self)
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModal)
        self.setupSignals()
        f.radioNew.click()
        self.exec_()

    def setupSignals(self) -> None:
        f = self.form
        qconnect(f.radioNew.clicked, lambda: self.onRadioChange(RADIO_NEW))
        qconnect(f.radioRev.clicked, lambda: self.onRadioChange(RADIO_REV))
        qconnect(f.radioForgot.clicked, lambda: self.onRadioChange(RADIO_FORGOT))
        qconnect(f.radioAhead.clicked, lambda: self.onRadioChange(RADIO_AHEAD))
        qconnect(f.radioPreview.clicked, lambda: self.onRadioChange(RADIO_PREVIEW))
        qconnect(f.radioCram.clicked, lambda: self.onRadioChange(RADIO_CRAM))

    def onRadioChange(self, idx: int) -> None:
        f = self.form
        sp = f.spin
        smin = 1
        smax = DYN_MAX_SIZE
        sval = 1
        post = tr.custom_study_cards()
        tit = ""
        spShow = True
        typeShow = False
        ok = tr.custom_study_ok()

        def plus(num: Union[int, str]) -> str:
            if num == 1000:
                num = "1000+"
            return f"<b>{str(num)}</b>"

        if idx == RADIO_NEW:
            new = self.mw.col.sched.totalNewForCurrentDeck()
            # get the number of new cards in deck that exceed the new cards limit
            newUnderLearning = min(
                new, self.conf["new"]["perDay"] - self.deck["newToday"][1]
            )
            newExceeding = min(new, new - newUnderLearning)
            tit = tr.custom_study_new_cards_in_deck_over_today(val=plus(newExceeding))
            pre = tr.custom_study_increase_todays_new_card_limit_by()
            sval = min(new, self.deck.get("extendNew", 10))
            smin = -DYN_MAX_SIZE
            smax = newExceeding
        elif idx == RADIO_REV:
            rev = self.mw.col.sched.totalRevForCurrentDeck()
            # get the number of review due in deck that exceed the review due limit
            revUnderLearning = min(
                rev, self.conf["rev"]["perDay"] - self.deck["revToday"][1]
            )
            revExceeding = min(rev, rev - revUnderLearning)
            tit = tr.custom_study_reviews_due_in_deck_over_today(val=plus(revExceeding))
            pre = tr.custom_study_increase_todays_review_limit_by()
            sval = min(rev, self.deck.get("extendRev", 10))
            smin = -DYN_MAX_SIZE
            smax = revExceeding
        elif idx == RADIO_FORGOT:
            pre = tr.custom_study_review_cards_forgotten_in_last()
            post = tr.scheduling_days()
            smax = 30
        elif idx == RADIO_AHEAD:
            pre = tr.custom_study_review_ahead_by()
            post = tr.scheduling_days()
        elif idx == RADIO_PREVIEW:
            pre = tr.custom_study_preview_new_cards_added_in_the()
            post = tr.scheduling_days()
            sval = 1
        elif idx == RADIO_CRAM:
            pre = tr.custom_study_select()
            post = tr.custom_study_cards_from_the_deck()
            # tit = _("After pressing OK, you can choose which tags to include.")
            ok = tr.custom_study_choose_tags()
            sval = 100
            typeShow = True
        sp.setVisible(spShow)
        f.cardType.setVisible(typeShow)
        f.title.setText(tit)
        f.title.setVisible(not not tit)
        f.spin.setMinimum(smin)
        f.spin.setMaximum(smax)
        if smax > 0:
            f.spin.setEnabled(True)
        else:
            f.spin.setEnabled(False)
        f.spin.setValue(sval)
        f.preSpin.setText(pre)
        f.postSpin.setText(post)
        f.buttonBox.button(QDialogButtonBox.Ok).setText(ok)
        self.radioIdx = idx

    def accept(self) -> None:
        f = self.form
        i = self.radioIdx
        spin = f.spin.value()
        if i == RADIO_NEW:
            self.deck["extendNew"] = spin
            self.mw.col.decks.save(self.deck)
            self.mw.col.sched.extendLimits(spin, 0)
            self.mw.reset()
            QDialog.accept(self)
            return
        elif i == RADIO_REV:
            self.deck["extendRev"] = spin
            self.mw.col.decks.save(self.deck)
            self.mw.col.sched.extendLimits(0, spin)
            self.mw.reset()
            QDialog.accept(self)
            return
        elif i == RADIO_CRAM:
            tags = self._getTags()
        # the rest create a filtered deck
        cur = self.mw.col.decks.by_name(tr.custom_study_custom_study_session())
        if cur:
            if not cur["dyn"]:
                showInfo(tr.custom_study_must_rename_deck())
                QDialog.accept(self)
                return
            else:
                # safe to empty
                self.mw.col.sched.empty_filtered_deck(cur["id"])
                # reuse; don't delete as it may have children
                dyn = cur
                self.mw.col.decks.select(cur["id"])
        else:
            did = self.mw.col.decks.new_filtered(tr.custom_study_custom_study_session())
            dyn = self.mw.col.decks.get(did)
        # and then set various options
        if i == RADIO_FORGOT:
            search = self.mw.col.build_search_string(
                SearchNode(
                    rated=SearchNode.Rated(days=spin, rating=SearchNode.RATING_AGAIN)
                )
            )
            dyn["terms"][0] = [search, DYN_MAX_SIZE, DYN_RANDOM]
            dyn["resched"] = False
        elif i == RADIO_AHEAD:
            search = self.mw.col.build_search_string(SearchNode(due_in_days=spin))
            dyn["terms"][0] = [search, DYN_MAX_SIZE, DYN_DUE]
            dyn["resched"] = True
        elif i == RADIO_PREVIEW:
            search = self.mw.col.build_search_string(
                SearchNode(card_state=SearchNode.CARD_STATE_NEW),
                SearchNode(added_in_days=spin),
            )
            dyn["terms"][0] = [search, DYN_MAX_SIZE, DYN_OLDEST]
            dyn["resched"] = False
        elif i == RADIO_CRAM:
            type = f.cardType.currentRow()
            if type == TYPE_NEW:
                terms = self.mw.col.build_search_string(
                    SearchNode(card_state=SearchNode.CARD_STATE_NEW)
                )
                ord = DYN_ADDED
                dyn["resched"] = True
            elif type == TYPE_DUE:
                terms = self.mw.col.build_search_string(
                    SearchNode(card_state=SearchNode.CARD_STATE_DUE)
                )
                ord = DYN_DUE
                dyn["resched"] = True
            elif type == TYPE_REVIEW:
                terms = self.mw.col.build_search_string(
                    SearchNode(negated=SearchNode(card_state=SearchNode.CARD_STATE_NEW))
                )
                ord = DYN_RANDOM
                dyn["resched"] = True
            else:
                terms = ""
                ord = DYN_RANDOM
                dyn["resched"] = False
            dyn["terms"][0] = [(terms + tags).strip(), spin, ord]
        # add deck limit
        dyn["terms"][0][0] = self.mw.col.build_search_string(
            dyn["terms"][0][0], SearchNode(deck=self.deck["name"])
        )
        self.mw.col.decks.save(dyn)
        # generate cards
        self.created_custom_study = True
        if not self.mw.col.sched.rebuild_filtered_deck(dyn["id"]):
            showWarning(tr.custom_study_no_cards_matched_the_criteria_you())
            return
        self.mw.moveToState("overview")
        QDialog.accept(self)

    def reject(self) -> None:
        if self.created_custom_study:
            # set the original deck back to current
            self.mw.col.decks.select(self.deck["id"])
            # fixme: clean up the empty custom study deck
        QDialog.reject(self)

    def _getTags(self) -> str:
        from aqt.taglimit import TagLimit

        return TagLimit(self.mw, self).tags
