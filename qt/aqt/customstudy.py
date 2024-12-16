# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.forms
import aqt.operations
from anki.collection import Collection
from anki.consts import *
from anki.decks import DeckId
from anki.scheduler import CustomStudyRequest
from anki.scheduler.base import CustomStudyDefaults
from aqt.operations import QueryOp
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
    @staticmethod
    def fetch_data_and_show(mw: aqt.AnkiQt) -> None:
        def fetch_data(
            col: Collection,
        ) -> tuple[DeckId, CustomStudyDefaults]:
            deck_id = mw.col.decks.get_current_id()
            defaults = col.sched.custom_study_defaults(deck_id)
            return (deck_id, defaults)

        def show_dialog(data: tuple[DeckId, CustomStudyDefaults]) -> None:
            deck_id, defaults = data
            CustomStudy(mw=mw, deck_id=deck_id, defaults=defaults)

        QueryOp(
            parent=mw, op=fetch_data, success=show_dialog
        ).with_progress().run_in_background()

    def __init__(
        self,
        mw: aqt.AnkiQt,
        deck_id: DeckId,
        defaults: CustomStudyDefaults,
    ) -> None:
        "Don't call this directly; use CustomStudy.fetch_data_and_show()."
        QDialog.__init__(self, mw)
        self.mw = mw
        self.deck_id = deck_id
        self.defaults = defaults
        self.form = aqt.forms.customstudy.Ui_Dialog()
        self.form.setupUi(self)
        disable_help_button(self)
        self.setupSignals()
        self.form.radioNew.click()
        self.open()

    def setupSignals(self) -> None:
        f = self.form
        qconnect(f.radioNew.clicked, lambda: self.onRadioChange(RADIO_NEW))
        qconnect(f.radioRev.clicked, lambda: self.onRadioChange(RADIO_REV))
        qconnect(f.radioForgot.clicked, lambda: self.onRadioChange(RADIO_FORGOT))
        qconnect(f.radioAhead.clicked, lambda: self.onRadioChange(RADIO_AHEAD))
        qconnect(f.radioPreview.clicked, lambda: self.onRadioChange(RADIO_PREVIEW))
        qconnect(f.radioCram.clicked, lambda: self.onRadioChange(RADIO_CRAM))

    def count_with_children(self, parent: int, children: int) -> str:
        if children:
            return f"{parent} {tr.custom_study_available_child_count(children)}"
        else:
            return str(parent)

    def onRadioChange(self, idx: int) -> None:
        form = self.form
        min_spinner_value = 1
        max_spinner_value = DYN_MAX_SIZE
        current_spinner_value = 1
        text_after_spinner = tr.custom_study_cards()
        title_text = ""
        show_cram_type = False
        ok = tr.custom_study_ok()

        if idx == RADIO_NEW:
            title_text = tr.custom_study_available_new_cards_2(
                count_string=self.count_with_children(
                    self.defaults.available_new,
                    self.defaults.available_new_in_children,
                ),
            )
            text_before_spinner = tr.custom_study_increase_todays_new_card_limit_by()
            current_spinner_value = self.defaults.extend_new
            min_spinner_value = -DYN_MAX_SIZE
        elif idx == RADIO_REV:
            title_text = tr.custom_study_available_review_cards_2(
                count_string=self.count_with_children(
                    self.defaults.available_review,
                    self.defaults.available_review_in_children,
                ),
            )
            text_before_spinner = tr.custom_study_increase_todays_review_limit_by()
            current_spinner_value = self.defaults.extend_review
            min_spinner_value = -DYN_MAX_SIZE
        elif idx == RADIO_FORGOT:
            text_before_spinner = tr.custom_study_review_cards_forgotten_in_last()
            text_after_spinner = tr.scheduling_days()
            max_spinner_value = 30
        elif idx == RADIO_AHEAD:
            text_before_spinner = tr.custom_study_review_ahead_by()
            text_after_spinner = tr.scheduling_days()
        elif idx == RADIO_PREVIEW:
            text_before_spinner = tr.custom_study_preview_new_cards_added_in_the()
            text_after_spinner = tr.scheduling_days()
            current_spinner_value = 1
        elif idx == RADIO_CRAM:
            text_before_spinner = tr.custom_study_select()
            text_after_spinner = tr.custom_study_cards_from_the_deck()
            ok = tr.custom_study_choose_tags()
            current_spinner_value = 100
            show_cram_type = True
        else:
            assert 0

        form.spin.setVisible(True)
        form.cardType.setVisible(show_cram_type)
        form.title.setText(title_text)
        form.title.setVisible(bool(title_text))
        form.spin.setMinimum(min_spinner_value)
        form.spin.setMaximum(max_spinner_value)
        if max_spinner_value > 0:
            form.spin.setEnabled(True)
        else:
            form.spin.setEnabled(False)
        form.spin.setValue(current_spinner_value)
        form.preSpin.setText(text_before_spinner)
        form.postSpin.setText(text_after_spinner)

        ok_button = form.buttonBox.button(QDialogButtonBox.StandardButton.Ok)
        assert ok_button is not None
        ok_button.setText(ok)

        self.radioIdx = idx

    def accept(self) -> None:
        request = CustomStudyRequest(deck_id=self.deck_id)
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

            cram_type = self.form.cardType.currentRow()
            if cram_type == TYPE_NEW:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_NEW
            elif cram_type == TYPE_DUE:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_DUE
            elif cram_type == TYPE_REVIEW:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_REVIEW
            else:
                request.cram.kind = CustomStudyRequest.Cram.CRAM_KIND_ALL

            def on_done(include: list[str], exclude: list[str]) -> None:
                request.cram.tags_to_include.extend(include)
                request.cram.tags_to_exclude.extend(exclude)
                self._create_and_close(request)

            # continues in background
            TagLimit(self, self.defaults.tags, on_done)
            return

        # other cases are synchronous
        self._create_and_close(request)

    def _create_and_close(self, request: CustomStudyRequest) -> None:
        # keep open on failure, as the cause was most likely an empty search
        # result, which the user can remedy
        custom_study(parent=self, request=request).success(
            lambda _: QDialog.accept(self)
        ).run_in_background()
