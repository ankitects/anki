# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.forms
import aqt.operations
from anki.collection import OpChangesWithId, SearchNode
from anki.decks import DeckDict, DeckId, FilteredDeckConfig
from anki.errors import SearchError
from anki.lang import without_unicode_isolation
from anki.scheduler import FilteredDeckForUpdate
from aqt import AnkiQt, colors, gui_hooks
from aqt.operations import QueryOp
from aqt.operations.scheduling import add_or_update_filtered_deck
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import (
    HelpPage,
    disable_help_button,
    openHelp,
    restoreGeom,
    saveGeom,
    showWarning,
    tr,
)


class FilteredDeckConfigDialog(QDialog):
    """Dialog to modify and (re)build a filtered deck."""

    GEOMETRY_KEY = "dyndeckconf"
    DIALOG_KEY = "FilteredDeckConfigDialog"
    silentlyClose = True

    def __init__(
        self,
        mw: AnkiQt,
        deck_id: DeckId = DeckId(0),
        search: str | None = None,
        search_2: str | None = None,
    ) -> None:
        """If 'deck_id' is non-zero, load and modify its settings.
        Otherwise, build a new deck and derive settings from the current deck.

        If search or search_2 are provided, they will be used as the default
        search text.
        """

        QDialog.__init__(self, mw)
        self.mw = mw
        mw.garbage_collect_on_dialog_finish(self)
        self.col = self.mw.col
        self._desired_search_1 = search
        self._desired_search_2 = search_2

        self._initial_dialog_setup()

        # set on successful query
        self.deck: FilteredDeckForUpdate

        QueryOp(
            parent=self.mw,
            op=lambda col: col.sched.get_or_create_filtered_deck(deck_id=deck_id),
            success=self.load_deck_and_show,
        ).failure(self.on_fetch_error).run_in_background()

    def on_fetch_error(self, exc: Exception) -> None:
        showWarning(str(exc))
        self.close()

    def _initial_dialog_setup(self) -> None:
        self.form = aqt.forms.filtered_deck.Ui_Dialog()
        self.form.setupUi(self)

        order_labels = self.col.sched.filtered_deck_order_labels()

        self.form.order.addItems(order_labels)
        self.form.order_2.addItems(order_labels)

        qconnect(self.form.allow_empty.stateChanged, self._on_allow_empty_toggled)

        qconnect(self.form.resched.stateChanged, self._onReschedToggled)

        qconnect(self.form.search_button.clicked, self.on_search_button)
        qconnect(self.form.search_button_2.clicked, self.on_search_button_2)
        qconnect(self.form.hint_button.clicked, self.on_hint_button)
        blue = theme_manager.var(colors.FG_LINK)
        grey = theme_manager.var(colors.FG_DISABLED)
        self.setStyleSheet(
            f"""QPushButton[label] {{ padding: 0; border: 0 }}
            QPushButton[label]:hover {{ text-decoration: underline }}
            QPushButton[label="search"] {{ color: {blue} }}
            QPushButton[label="hint"] {{ color: {grey} }}"""
        )
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModality.WindowModal)
        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.FILTERED_DECK)
        )

        self.form.again_delay_label.setText(
            tr.decks_delay_for_button(button=tr.studying_again())
        )
        self.form.hard_delay_label.setText(
            tr.decks_delay_for_button(button=tr.studying_hard())
        )
        self.form.good_delay_label.setText(
            tr.decks_delay_for_button(button=tr.studying_good())
        )

        restoreGeom(self, self.GEOMETRY_KEY)

    def load_deck_and_show(self, deck: FilteredDeckForUpdate) -> None:
        self.deck = deck
        self._load_deck()
        self.show()

    def _load_deck(self) -> None:
        form = self.form
        deck = self.deck
        config = deck.config

        self.form.name.setText(deck.name)
        self.form.name.setPlaceholderText(deck.name)

        existing = deck.id != 0
        if existing:
            build_label = tr.actions_rebuild()
        else:
            build_label = tr.decks_build()
        self.form.buttonBox.button(QDialogButtonBox.StandardButton.Ok).setText(
            build_label
        )

        form.resched.setChecked(config.reschedule)
        self._onReschedToggled(0)

        term1: FilteredDeckConfig.SearchTerm = config.search_terms[0]
        form.search.setText(term1.search)
        form.order.setCurrentIndex(term1.order)
        form.limit.setValue(term1.limit)

        form.preview_again.setValue(config.preview_again_secs)
        form.preview_hard.setValue(config.preview_hard_secs)
        form.preview_good.setValue(config.preview_good_secs)

        if len(config.search_terms) > 1:
            term2: FilteredDeckConfig.SearchTerm = config.search_terms[1]
            form.search_2.setText(term2.search)
            form.order_2.setCurrentIndex(term2.order)
            form.limit_2.setValue(term2.limit)
            show_second = existing
        else:
            show_second = False
            form.order_2.setCurrentIndex(5)
            form.limit_2.setValue(20)

        form.secondFilter.setChecked(show_second)
        form.filter2group.setVisible(show_second)

        self.set_custom_searches(self._desired_search_1, self._desired_search_2)

        self.setWindowTitle(
            without_unicode_isolation(tr.actions_options_for(val=self.deck.name))
        )

        gui_hooks.filtered_deck_dialog_did_load_deck(self, deck)

    def reopen(
        self,
        _mw: AnkiQt,
        search: str | None = None,
        search_2: str | None = None,
        _deck: DeckDict | None = None,
    ) -> None:
        self.set_custom_searches(search, search_2)

    def set_custom_searches(self, search: str | None, search_2: str | None) -> None:
        if search is not None:
            self.form.search.setText(search)
        self.form.search.setFocus()
        self.form.search.selectAll()
        if search_2 is not None:
            self.form.secondFilter.setChecked(True)
            self.form.filter2group.setVisible(True)
            self.form.search_2.setText(search_2)
            self.form.search_2.setFocus()
            self.form.search_2.selectAll()

    def on_search_button(self) -> None:
        self._on_search_button(self.form.search)

    def on_search_button_2(self) -> None:
        self._on_search_button(self.form.search_2)

    def _on_search_button(self, line: QLineEdit) -> None:
        try:
            search = self.col.build_search_string(line.text())
        except SearchError as err:
            line.setFocus()
            line.selectAll()
            showWarning(str(err))
        else:
            aqt.dialogs.open("Browser", self.mw, search=(search,))

    def on_hint_button(self) -> None:
        """Open the browser to show cards that match the typed-in filters but cannot be included
        due to internal limitations.
        """
        manual_filters = (self.form.search.text(), *self._second_filter())
        implicit_filters = (
            SearchNode(card_state=SearchNode.CARD_STATE_SUSPENDED),
            SearchNode(card_state=SearchNode.CARD_STATE_BURIED),
            *self._filtered_search_node(),
        )
        manual_filter = self.col.group_searches(*manual_filters, joiner="OR")
        implicit_filter = self.col.group_searches(*implicit_filters, joiner="OR")
        try:
            search = self.col.build_search_string(manual_filter, implicit_filter)
        except Exception as err:
            showWarning(str(err))
        else:
            aqt.dialogs.open("Browser", self.mw, search=(search,))

    def _second_filter(self) -> tuple[str, ...]:
        if self.form.secondFilter.isChecked():
            return (self.form.search_2.text(),)
        return ()

    def _filtered_search_node(self) -> tuple[SearchNode]:
        """Return a search node that matches cards in filtered decks, if applicable excluding those
        in the deck being rebuild."""
        if self.deck.id:
            return (
                self.col.group_searches(
                    SearchNode(deck="filtered"),
                    SearchNode(negated=SearchNode(deck=self.deck.name)),
                ),
            )
        return (SearchNode(deck="filtered"),)

    def _onReschedToggled(self, _state: int) -> None:
        self.form.previewDelayWidget.setVisible(not self.form.resched.isChecked())

    def _on_allow_empty_toggled(self) -> None:
        self.deck.allow_empty = self.form.allow_empty.isChecked()

    def _update_deck(self) -> bool:
        """Update our stored deck with the details from the GUI.
        If false, abort adding."""
        form = self.form
        deck = self.deck
        config = deck.config

        deck.name = form.name.text()
        config.reschedule = form.resched.isChecked()

        del config.delays[:]
        terms = [
            FilteredDeckConfig.SearchTerm(
                search=form.search.text(),
                limit=form.limit.value(),
                order=form.order.currentIndex(),  # type: ignore[arg-type]
            )
        ]

        if form.secondFilter.isChecked():
            terms.append(
                FilteredDeckConfig.SearchTerm(
                    search=form.search_2.text(),
                    limit=form.limit_2.value(),
                    order=form.order_2.currentIndex(),  # type: ignore[arg-type]
                )
            )

        del config.search_terms[:]
        config.search_terms.extend(terms)
        config.preview_again_secs = form.preview_again.value()
        config.preview_hard_secs = form.preview_hard.value()
        config.preview_good_secs = form.preview_good.value()

        return True

    def reject(self) -> None:
        aqt.dialogs.markClosed(self.DIALOG_KEY)
        QDialog.reject(self)

    def accept(self) -> None:
        if not self._update_deck():
            return

        def success(out: OpChangesWithId) -> None:
            gui_hooks.filtered_deck_dialog_did_add_or_update_deck(
                self, self.deck, out.id
            )
            saveGeom(self, self.GEOMETRY_KEY)
            aqt.dialogs.markClosed(self.DIALOG_KEY)
            QDialog.accept(self)

        gui_hooks.filtered_deck_dialog_will_add_or_update_deck(self, self.deck)

        add_or_update_filtered_deck(parent=self, deck=self.deck).success(
            success
        ).run_in_background()
