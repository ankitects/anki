# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Callable, List, Optional, Tuple

import aqt
from anki.collection import SearchNode
from anki.decks import Deck
from anki.errors import DeckIsFilteredError, InvalidInput
from anki.lang import without_unicode_isolation
from aqt import AnkiQt, colors, gui_hooks
from aqt.qt import *
from aqt.theme import theme_manager
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

    def __init__(
        self,
        mw: AnkiQt,
        search: Optional[str] = None,
        search_2: Optional[str] = None,
        deck: Optional[Deck] = None,
    ) -> None:
        """If 'deck' is an existing filtered deck, load and modify its settings.
        Otherwise, build a new one and derive settings from the current deck.
        """

        QDialog.__init__(self, mw)
        self.mw = mw
        self.col = self.mw.col
        self.did: Optional[int] = None
        self.form = aqt.forms.dyndconf.Ui_Dialog()
        self.form.setupUi(self)
        self.mw.checkpoint(tr(TR.ACTIONS_OPTIONS))
        self.initialSetup()
        self.old_deck = self.col.decks.current()

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

        self.form.name.setText(self.deck["name"])
        self.form.name.setPlaceholderText(self.deck["name"])
        self.set_custom_searches(search, search_2)
        qconnect(self.form.search_button.clicked, self.on_search_button)
        qconnect(self.form.search_button_2.clicked, self.on_search_button_2)
        qconnect(self.form.hint_button.clicked, self.on_hint_button)
        blue = theme_manager.color(colors.LINK)
        grey = theme_manager.color(colors.DISABLED)
        self.setStyleSheet(
            f"""QPushButton[label] {{ padding: 0; border: 0 }}
            QPushButton[label]:hover {{ text-decoration: underline }}
            QPushButton[label="search"] {{ color: {blue} }}
            QPushButton[label="hint"] {{ color: {grey} }}"""
        )
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModal)
        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.FILTERED_DECK)
        )
        self.setWindowTitle(
            without_unicode_isolation(tr(TR.ACTIONS_OPTIONS_FOR, val=self.deck["name"]))
        )
        self.form.buttonBox.button(QDialogButtonBox.Ok).setText(label)
        if self.col.schedVer() == 1:
            self.form.secondFilter.setVisible(False)
        restoreGeom(self, "dyndeckconf")

        self.show()

    def reopen(
        self,
        _mw: AnkiQt,
        search: Optional[str] = None,
        search_2: Optional[str] = None,
        _deck: Optional[Deck] = None,
    ) -> None:
        self.set_custom_searches(search, search_2)

    def new_dyn_deck(self) -> None:
        suffix: int = 1
        while self.col.decks.id_for_name(
            without_unicode_isolation(tr(TR.QT_MISC_FILTERED_DECK, val=suffix))
        ):
            suffix += 1
        name: str = without_unicode_isolation(tr(TR.QT_MISC_FILTERED_DECK, val=suffix))
        self.did = self.col.decks.new_filtered(name)
        self.deck = self.col.decks.current()

    def set_default_searches(self, deck_name: str) -> None:
        self.form.search.setText(
            self.col.build_search_string(
                SearchNode(deck=deck_name),
                SearchNode(card_state=SearchNode.CARD_STATE_DUE),
            )
        )
        self.form.search_2.setText(
            self.col.build_search_string(
                SearchNode(deck=deck_name),
                SearchNode(card_state=SearchNode.CARD_STATE_NEW),
            )
        )

    def set_custom_searches(
        self, search: Optional[str], search_2: Optional[str]
    ) -> None:
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

    def initialSetup(self) -> None:
        import anki.consts as cs

        self.form.order.addItems(list(cs.dynOrderLabels(self.mw.col).values()))
        self.form.order_2.addItems(list(cs.dynOrderLabels(self.mw.col).values()))

        qconnect(self.form.resched.stateChanged, self._onReschedToggled)

    def on_search_button(self) -> None:
        self._on_search_button(self.form.search)

    def on_search_button_2(self) -> None:
        self._on_search_button(self.form.search_2)

    def _on_search_button(self, line: QLineEdit) -> None:
        try:
            search = self.col.build_search_string(line.text())
        except InvalidInput as err:
            line.setFocus()
            line.selectAll()
            show_invalid_search_error(err)
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
            *self._learning_search_node(),
            *self._filtered_search_node(),
        )
        manual_filter = self.col.group_searches(*manual_filters, joiner="OR")
        implicit_filter = self.col.group_searches(*implicit_filters, joiner="OR")
        try:
            search = self.col.build_search_string(manual_filter, implicit_filter)
        except InvalidInput as err:
            show_invalid_search_error(err)
        else:
            aqt.dialogs.open("Browser", self.mw, search=(search,))

    def _second_filter(self) -> Tuple[str, ...]:
        if self.form.secondFilter.isChecked():
            return (self.form.search_2.text(),)
        return ()

    def _learning_search_node(self) -> Tuple[SearchNode, ...]:
        """Return a search node that matches learning cards if the old scheduler is enabled.
        If it's a rebuild, exclude cards from this filtered deck as those will be reset.
        """
        if self.col.schedVer() == 1:
            if self.did is None:
                return (
                    self.col.group_searches(
                        SearchNode(card_state=SearchNode.CARD_STATE_LEARN),
                        SearchNode(negated=SearchNode(deck=self.deck["name"])),
                    ),
                )
            return (SearchNode(card_state=SearchNode.CARD_STATE_LEARN),)
        return ()

    def _filtered_search_node(self) -> Tuple[SearchNode]:
        """Return a search node that matches cards in filtered decks, if applicable excluding those
        in the deck being rebuild."""
        if self.did is None:
            return (
                self.col.group_searches(
                    SearchNode(deck="filtered"),
                    SearchNode(negated=SearchNode(deck=self.deck["name"])),
                ),
            )
        return (SearchNode(deck="filtered"),)

    def _onReschedToggled(self, _state: int) -> None:
        self.form.previewDelayWidget.setVisible(
            not self.form.resched.isChecked() and self.col.schedVer() > 1
        )

    def loadConf(self, deck: Optional[Deck] = None) -> None:
        f = self.form
        d = deck or self.deck

        f.resched.setChecked(d["resched"])
        self._onReschedToggled(0)

        search, limit, order = d["terms"][0]
        f.search.setText(search)

        if self.col.schedVer() == 1:
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

    def saveConf(self) -> None:
        f = self.form
        d = self.deck

        if f.name.text() and d["name"] != f.name.text():
            self.col.decks.rename(d, f.name.text())
            gui_hooks.sidebar_should_refresh_decks()

        d["resched"] = f.resched.isChecked()
        d["delays"] = None

        if self.col.schedVer() == 1 and f.stepsOn.isChecked():
            steps = self.userToList(f.steps)
            if steps:
                d["delays"] = steps
            else:
                d["delays"] = None

        search = self.col.build_search_string(f.search.text())
        terms = [[search, f.limit.value(), f.order.currentIndex()]]

        if f.secondFilter.isChecked():
            search_2 = self.col.build_search_string(f.search_2.text())
            terms.append([search_2, f.limit_2.value(), f.order_2.currentIndex()])

        d["terms"] = terms
        d["previewDelay"] = f.previewDelay.value()

        self.col.decks.save(d)

    def reject(self) -> None:
        if self.did:
            self.col.decks.rem(self.did)
            self.col.decks.select(self.old_deck["id"])
            self.mw.reset()
        saveGeom(self, "dyndeckconf")
        QDialog.reject(self)
        aqt.dialogs.markClosed("DynDeckConfDialog")

    def accept(self) -> None:
        try:
            self.saveConf()
        except InvalidInput as err:
            show_invalid_search_error(err)
        except DeckIsFilteredError as err:
            showWarning(str(err))
        else:
            if not self.col.sched.rebuild_filtered_deck(self.deck["id"]):
                if askUser(tr(TR.DECKS_THE_PROVIDED_SEARCH_DID_NOT_MATCH)):
                    return
            saveGeom(self, "dyndeckconf")
            self.mw.reset()
            QDialog.accept(self)
            aqt.dialogs.markClosed("DynDeckConfDialog")

    def closeWithCallback(self, callback: Callable) -> None:
        self.reject()
        callback()

    # Step load/save - fixme: share with std options screen
    ########################################################

    def listToUser(self, values: List[Union[float, int]]) -> str:
        return " ".join(
            [str(int(val)) if int(val) == val else str(val) for val in values]
        )

    def userToList(
        self, line: QLineEdit, minSize: int = 1
    ) -> Optional[List[Union[float, int]]]:
        items = str(line.text()).split(" ")
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
