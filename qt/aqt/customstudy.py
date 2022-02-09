# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import aqt
from anki.consts import *
from anki.scheduler import CustomStudyRequest
from aqt.operations.scheduling import custom_study
from aqt.qt import *
from aqt.taglimit import TagLimit
from aqt.utils import disable_help_button, tr

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
        self.setupSignals()
        f.radioNew.click()
        self.open()

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
            rev = self.mw.col.sched.total_rev_for_current_deck()
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
        f.buttonBox.button(QDialogButtonBox.StandardButton.Ok).setText(ok)
        self.radioIdx = idx

    def accept(self) -> None:
        request = CustomStudyRequest()
        if self.radioIdx == RADIO_NEW:
            request.new_limit_delta = self.form.spin.value()
        elif self.radioIdx == RADIO_REV:
            request.review_limit_delta = self.form.spin.value()
        elif self.radioIdx == RADIO_FORGOT:
            request.forgot_days = self.form.spin.value()
        elif self.radioIdx == RADIO_AHEAD:
            request.review_ahead_days = self.form.spin.value()
        elif self.radioIdx == RADIO_PREVIEW:
            request.preview_days = self.form.spin.value()
        else:
            request.cram.card_limit = self.form.spin.value()

            tags = TagLimit.get_tags(self.mw, self)
            request.cram.tags_to_include.extend(tags[0])
            request.cram.tags_to_exclude.extend(tags[1])

            cram_type = self.form.cardType.currentRow()
            if cram_type == TYPE_NEW:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_NEW
            elif cram_type == TYPE_DUE:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_DUE
            elif cram_type == TYPE_REVIEW:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_REVIEW
            else:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_ALL

        # keep open on failure, as the cause was most likely an empty search
        # result, which the user can remedy
        custom_study(parent=self, request=request).success(
            lambda _: QDialog.accept(self)
        ).run_in_background()

    def reject(self) -> None:
        if self.created_custom_study:
            # set the original deck back to current
            self.mw.col.decks.select(self.deck["id"])
            # fixme: clean up the empty custom study deck
        QDialog.reject(self)
