# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Focus-a-category picker (Interleaved Study Mode, Part 2).

Builds/rebuilds a "Focus: <Category>" filtered deck scoped to a single
depth-2 topic (or the untagged "Other" bucket), reusing the existing
filtered-deck plumbing. Not the Custom Study dialog: its search terms can't
express due+new+single-tag+reschedule, nor the untagged case."""

from __future__ import annotations

import aqt
import aqt.main
from anki.collection import Collection, OpChangesWithId
from anki.decks import DeckId, FilteredDeckConfig
from anki.scheduler import FilteredDeckForUpdate
from anki.stats_pb2 import TagMasteryResponse
from aqt.operations import QueryOp
from aqt.operations.deck import set_current_deck
from aqt.operations.scheduling import add_or_update_filtered_deck
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, showInfo, tr

# Search scope shared with Part 1's "today's studyable set", so the two
# entry points agree (data-model.md "Eligible category (Part 2)").
ELIGIBILITY_SEARCH = "(is:due or is:new) -is:suspended"

# `TagMasteryResponse` (unlike `CardTopicsResponse`) does not echo an
# `untagged_sentinel` field, but rslib's tag_mastery.rs groups untagged notes
# under the same literal constant ("(untagged)") that CardTopics echoes -- see
# contracts/api.md section 1 / rslib/src/stats/tag_mastery.rs's `UNTAGGED`.
UNTAGGED_SENTINEL = "(untagged)"


class FocusCategory(QDialog):
    TITLE = "focusCategory"
    silentlyClose = True

    @staticmethod
    def fetch_data_and_show(mw: aqt.AnkiQt) -> None:
        def fetch_data(col: Collection) -> TagMasteryResponse:
            return col._backend.tag_mastery(
                group_depth=2,
                mastered_threshold=0.0,
                search=ELIGIBILITY_SEARCH,
            )

        def show_dialog(response: TagMasteryResponse) -> None:
            FocusCategory(mw, response)

        QueryOp(
            parent=mw, op=fetch_data, success=show_dialog
        ).with_progress().run_in_background()

    def __init__(self, mw: aqt.main.AnkiQt, response: TagMasteryResponse) -> None:
        "Don't call this directly; use FocusCategory.fetch_data_and_show()."
        self.mw = mw
        self.untagged_sentinel = UNTAGGED_SENTINEL

        # eligible = at least one card in today's studyable set (AC4)
        eligible = [g.tag for g in response.groups if g.total_cards > 0]

        # sort alphabetically, but the untagged sentinel ("Other") always
        # sorts last regardless of its alphabetical position
        real_tags = sorted(tag for tag in eligible if tag != self.untagged_sentinel)
        self.categories: list[str] = real_tags
        if self.untagged_sentinel in eligible:
            self.categories.append(self.untagged_sentinel)

        if not self.categories:
            showInfo(tr.studying_no_cards_are_due_yet(), parent=mw)
            return

        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self._setup_ui()
        self.show()

    def _display_label(self, category: str) -> str:
        if category == self.untagged_sentinel:
            return tr.studying_other()
        return category

    def _setup_ui(self) -> None:
        self.setWindowTitle(tr.studying_focus_a_category())
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumWidth(400)
        disable_help_button(self)
        restoreGeom(self, self.TITLE)

        box = QVBoxLayout()

        self.list = QListWidget()
        for category in self.categories:
            self.list.addItem(QListWidgetItem(self._display_label(category)))
        self.list.setCurrentRow(0)
        box.addWidget(self.list)

        button_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        qconnect(button_box.accepted, self.accept)
        qconnect(button_box.rejected, self.reject)
        box.addWidget(button_box)

        self.setLayout(box)

    def accept(self) -> None:
        row = self.list.currentRow()
        if row < 0:
            QDialog.reject(self)
            return
        raw_category = self.categories[row]
        display_category = self._display_label(raw_category)

        saveGeom(self, self.TITLE)
        QDialog.accept(self)

        self._build_filtered_deck(display_category, raw_category)

    def reject(self) -> None:
        saveGeom(self, self.TITLE)
        QDialog.reject(self)

    def _build_filtered_deck(self, display_category: str, raw_category: str) -> None:
        name = f"Focus: {display_category}"
        if raw_category == self.untagged_sentinel:
            tag_clause = "-tag:*"
        else:
            tag_clause = f"(tag:{raw_category} OR tag:{raw_category}::*)"
        search = f"{ELIGIBILITY_SEARCH} {tag_clause}"

        def build(col: Collection) -> FilteredDeckForUpdate:
            deck_id = col.decks.id_for_name(name) or DeckId(0)
            dfu = col.sched.get_or_create_filtered_deck(deck_id=deck_id)
            dfu.name = name
            dfu.config.reschedule = True
            del dfu.config.search_terms[:]
            dfu.config.search_terms.extend(
                [
                    FilteredDeckConfig.SearchTerm(
                        search=search,
                        limit=99999,
                        order=FilteredDeckConfig.SearchTerm.Order.DUE,
                    )
                ]
            )
            return dfu

        QueryOp(
            parent=self.mw,
            op=build,
            success=self._on_deck_built,
        ).run_in_background()

    def _on_deck_built(self, dfu: FilteredDeckForUpdate) -> None:
        add_or_update_filtered_deck(parent=self.mw, deck=dfu).success(
            self._on_deck_saved
        ).run_in_background()

    def _on_deck_saved(self, out: OpChangesWithId) -> None:
        # Focus-a-category is explicitly NOT interleave mode; leave
        # _interleave_mode at its existing (False) value.
        set_current_deck(parent=self.mw, deck_id=DeckId(out.id)).success(
            lambda _: self.mw.moveToState("review")
        ).run_in_background()
