# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import html
from concurrent.futures import Future
from dataclasses import dataclass
from typing import (
    TYPE_CHECKING,
    Any,
    Callable,
    Dict,
    List,
    Optional,
    Sequence,
    Tuple,
    Union,
)

import aqt
import aqt.forms
from anki.cards import Card
from anki.collection import Collection, SearchTerm
from anki.consts import *
from anki.errors import InvalidInput
from anki.lang import without_unicode_isolation
from anki.models import NoteType
from anki.notes import Note
from anki.stats import CardStats
from anki.utils import ids2str, isMac
from aqt import AnkiQt, gui_hooks
from aqt.editor import Editor
from aqt.exporting import ExportDialog
from aqt.main import ResetReason
from aqt.previewer import BrowserPreviewer as PreviewDialog
from aqt.previewer import Previewer
from aqt.qt import *
from aqt.sidebar import SidebarSearchBar, SidebarTreeView
from aqt.table import Table
from aqt.utils import (
    TR,
    HelpPage,
    MenuList,
    SubMenu,
    askUser,
    disable_help_button,
    getTag,
    openHelp,
    qtMenuShortcutWorkaround,
    restore_combo_history,
    restore_combo_index_for_session,
    restore_is_checked,
    restoreGeom,
    restoreSplitter,
    restoreState,
    save_combo_history,
    save_combo_index_for_session,
    save_is_checked,
    saveGeom,
    saveHeader,
    saveSplitter,
    saveState,
    shortcut,
    show_invalid_search_error,
    showInfo,
    showWarning,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView

# legacy add-on support
# pylint: disable=unused-import
from aqt.sidebar import SidebarItem, SidebarStage  # isort: skip


if TYPE_CHECKING:
    from anki.lang import TRValue


@dataclass
class FindDupesDialog:
    dialog: QDialog
    browser: Browser


@dataclass
class SearchContext:
    search: str
    browser: Browser
    order: Union[bool, str] = True
    # if set, provided card ids will be used instead of the regular search
    item_ids: Optional[Sequence[int]] = None


# Browser window
######################################################################

# fixme: respond to reset+edit hooks


class Browser(QMainWindow):
    mw: AnkiQt
    col: Collection
    table: Table
    editor: Optional[Editor]

    def __init__(
        self,
        mw: AnkiQt,
        card: Optional[Card] = None,
        search: Optional[Tuple[Union[str, SearchTerm]]] = None,
    ) -> None:
        """
        card  : try to search for its note and select it
        search: set and perform search; caller must ensure validity
        """

        QMainWindow.__init__(self, None, Qt.Window)
        self.mw = mw
        self.col = self.mw.col
        self.lastFilter = ""
        self.focusTo: Optional[int] = None
        self._previewer: Optional[Previewer] = None
        self._closeEventHasCleanedUp = False
        self.form = aqt.forms.browser.Ui_Dialog()
        self.form.setupUi(self)
        self.setupSidebar()
        restoreGeom(self, "editor", 0)
        restoreState(self, "editor")
        restoreSplitter(self.form.splitter, "editor3")
        self.form.splitter.setChildrenCollapsible(False)
        self.item: Optional[Union[Card, Note]] = None
        self.setupMenus()
        self.setupHooks()
        self.setupEditor()
        self.setup_table()
        self.onUndoState(self.mw.form.actionUndo.isEnabled())
        self.setupSearch(card, search)
        gui_hooks.browser_will_show(self)
        self.show()

    def setupMenus(self) -> None:
        # pylint: disable=unnecessary-lambda
        # actions
        f = self.form
        qconnect(f.filter.clicked, self.onFilterButton)
        qconnect(f.radio_cards.toggled, self.on_table_state_changed)
        # edit
        qconnect(f.actionUndo.triggered, self.mw.onUndo)
        qconnect(f.actionInvertSelection.triggered, self.invertSelection)
        qconnect(f.actionSelectNotes.triggered, self.selectNotes)
        if not isMac:
            f.actionClose.setVisible(False)
        qconnect(f.actionCreateFilteredDeck.triggered, self.createFilteredDeck)
        f.actionCreateFilteredDeck.setShortcuts(["Ctrl+G", "Ctrl+Alt+G"])
        # notes
        qconnect(f.actionAdd.triggered, self.mw.onAddCard)
        qconnect(f.actionAdd_Tags.triggered, lambda: self.addTags())
        qconnect(f.actionRemove_Tags.triggered, lambda: self.deleteTags())
        qconnect(f.actionClear_Unused_Tags.triggered, self.clearUnusedTags)
        qconnect(f.actionToggle_Mark.triggered, lambda: self.onMark())
        qconnect(f.actionChangeModel.triggered, self.onChangeModel)
        qconnect(f.actionFindDuplicates.triggered, self.onFindDupes)
        qconnect(f.actionFindReplace.triggered, self.onFindReplace)
        qconnect(f.actionManage_Note_Types.triggered, self.mw.onNoteTypes)
        qconnect(f.actionDelete.triggered, self.deleteNotes)
        # cards
        qconnect(f.actionChange_Deck.triggered, self.setDeck)
        qconnect(f.action_Info.triggered, self.showCardInfo)
        qconnect(f.actionReposition.triggered, self.reposition)
        qconnect(f.actionReschedule.triggered, self.reschedule)
        qconnect(f.actionToggle_Suspend.triggered, self.onSuspend)
        qconnect(f.actionRed_Flag.triggered, lambda: self.onSetFlag(1))
        qconnect(f.actionOrange_Flag.triggered, lambda: self.onSetFlag(2))
        qconnect(f.actionGreen_Flag.triggered, lambda: self.onSetFlag(3))
        qconnect(f.actionBlue_Flag.triggered, lambda: self.onSetFlag(4))
        qconnect(f.actionExport.triggered, lambda: self._on_export_notes())
        # jumps
        qconnect(f.actionPreviousCard.triggered, self.onPreviousCard)
        qconnect(f.actionNextCard.triggered, self.onNextCard)
        qconnect(f.actionFirstCard.triggered, self.onFirstCard)
        qconnect(f.actionLastCard.triggered, self.onLastCard)
        qconnect(f.actionFind.triggered, self.onFind)
        qconnect(f.actionNote.triggered, self.onNote)
        qconnect(f.actionTags.triggered, self.onFilterButton)
        qconnect(f.actionSidebar.triggered, self.focusSidebar)
        qconnect(f.actionCardList.triggered, self.onCardList)
        # help
        qconnect(f.actionGuide.triggered, self.onHelp)
        # keyboard shortcut for shift+home/end
        self.pgUpCut = QShortcut(QKeySequence("Shift+Home"), self)
        qconnect(self.pgUpCut.activated, self.onFirstCard)
        self.pgDownCut = QShortcut(QKeySequence("Shift+End"), self)
        qconnect(self.pgDownCut.activated, self.onLastCard)
        # add-on hook
        gui_hooks.browser_menus_did_init(self)
        self.mw.maybeHideAccelerators(self)

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._closeEventHasCleanedUp:
            evt.accept()
            return
        self.editor.saveNow(self._closeWindow)
        evt.ignore()

    def _closeWindow(self) -> None:
        self._cleanup_preview()
        self.editor.cleanup()
        saveSplitter(self.form.splitter, "editor3")
        saveGeom(self, "editor")
        saveState(self, "editor")
        saveHeader(self.form.tableView.horizontalHeader(), "editor")
        self.teardownHooks()
        self.mw.maybeReset()
        aqt.dialogs.markClosed("Browser")
        self._closeEventHasCleanedUp = True
        self.mw.gcWindow(self)
        self.close()

    def closeWithCallback(self, onsuccess: Callable) -> None:
        def callback() -> None:
            self._closeWindow()
            onsuccess()

        self.editor.saveNow(callback)

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() == Qt.Key_Escape:
            self.close()
        else:
            super().keyPressEvent(evt)

    def reopen(
        self,
        _mw: AnkiQt,
        card: Optional[Card] = None,
        search: Optional[Tuple[Union[str, SearchTerm]]] = None,
    ) -> None:
        if search is not None:
            self.search_for_terms(*search)
            self.form.searchEdit.setFocus()
        elif card:
            self.show_single_card(card)
            self.form.searchEdit.setFocus()

    # Searching
    ######################################################################

    def setupSearch(
        self,
        card: Optional[Card] = None,
        search: Optional[Tuple[Union[str, SearchTerm]]] = None,
    ) -> None:
        qconnect(self.form.searchEdit.lineEdit().returnPressed, self.onSearchActivated)
        self.form.searchEdit.setCompleter(None)
        self.form.searchEdit.lineEdit().setPlaceholderText(
            tr(TR.BROWSING_SEARCH_BAR_HINT)
        )
        self.form.searchEdit.addItems(self.mw.pm.profile["searchHistory"])
        if search is not None:
            self.search_for_terms(*search)
        elif card:
            self.show_single_card(card)
        else:
            self.search_for(
                self.col.build_search_string(SearchTerm(deck="current")), ""
            )
        self.form.searchEdit.setFocus()

    # search triggered by user
    def onSearchActivated(self) -> None:
        self.editor.saveNow(self._onSearchActivated)

    def _onSearchActivated(self) -> None:
        text = self.form.searchEdit.lineEdit().text()
        try:
            normed = self.col.build_search_string(text)
        except InvalidInput as err:
            show_invalid_search_error(err)
        else:
            self.search_for(normed)
            self.update_history()

    def search_for(self, search: str, prompt: Optional[str] = None) -> None:
        """Keep track of search string so that we reuse identical search when
        refreshing, rather than whatever is currently in the search field.
        Optionally set the search bar to a different text than the actual search.
        """

        self._lastSearchTxt = search
        prompt = search if prompt == None else prompt
        self.form.searchEdit.lineEdit().setText(prompt)
        self.search()

    def search(self) -> None:
        """Search triggered programmatically. Caller must have saved note first."""
        try:
            self.table.search(self._lastSearchTxt)
        except Exception as err:
            show_invalid_search_error(err)

    def update_history(self) -> None:
        sh = self.mw.pm.profile["searchHistory"]
        if self._lastSearchTxt in sh:
            sh.remove(self._lastSearchTxt)
        sh.insert(0, self._lastSearchTxt)
        sh = sh[:30]
        self.form.searchEdit.clear()
        self.form.searchEdit.addItems(sh)
        self.mw.pm.profile["searchHistory"] = sh

    def updateTitle(self) -> int:
        selected = self.table.len_selection()
        cur = self.table.len()
        title = (
            TR.BROWSING_WINDOW_TITLE
            if self.table.is_card_state()
            else TR.BROWSING_WINDOW_TITLE_NOTES
        )
        self.setWindowTitle(
            without_unicode_isolation(tr(title, total=cur, selected=selected))
        )
        return selected

    def search_for_terms(self, *search_terms: Union[str, SearchTerm]) -> None:
        search = self.col.build_search_string(*search_terms)
        self.form.searchEdit.setEditText(search)
        self.onSearchActivated()

    def show_single_card(self, card: Card) -> None:
        if card.nid:

            def _show_single_card() -> None:
                self.card = card
                search = self.col.build_search_string(SearchTerm(nid=card.nid))
                search = gui_hooks.default_search(search, card)
                self.search_for(search, "")
                self.table.select_single_card(card.id)

            self.editor.saveNow(_show_single_card)

    def onReset(self) -> None:
        self.sidebar.refresh()
        self.begin_reset()
        self.search()

    # Table and Editor
    ######################################################################

    def setup_table(self) -> None:
        self.table = Table(self)
        self.form.radio_cards.setChecked(self.table.is_card_state())
        self.table.set_view(self.form.tableView)

    def setupEditor(self) -> None:
        def add_preview_button(leftbuttons: List[str], editor: Editor) -> None:
            preview_shortcut = "Ctrl+Shift+P"
            leftbuttons.insert(
                0,
                editor.addButton(
                    None,
                    "preview",
                    lambda _editor: self.onTogglePreview(),
                    tr(
                        TR.BROWSING_PREVIEW_SELECTED_CARD,
                        val=shortcut(preview_shortcut),
                    ),
                    tr(TR.ACTIONS_PREVIEW),
                    id="previewButton",
                    keys=preview_shortcut,
                    disables=False,
                    rightside=False,
                    toggleable=True,
                ),
            )

        gui_hooks.editor_did_init_left_buttons.append(add_preview_button)
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self)
        gui_hooks.editor_did_init_left_buttons.remove(add_preview_button)

    def save_now(self, callback: Callable) -> None:
        self.editor.saveNow(callback)

    def begin_reset(self) -> None:
        """Must be called before modifying the current note from outside the editor to prevent
        overwriting the changes with the cache. Unsaved changes in the editor will be lost.
        After the modification, _onRowChanged() must be triggered, e.g. by end_reset() or search(),
        to move the editor to a consistent state.
        """
        self.editor.setNote(None, hide=False)

    def end_reset(self) -> None:
        """Redraw the table with reloaded collection data triggering _onRowChanged()."""
        self.table.reset()

    def on_table_state_changed(self) -> None:
        self.mw.progress.start()
        self.editor.saveNow(
            lambda: self.table.toggle_state(self.form.radio_cards.isChecked())
        )
        self.mw.progress.finish()

    def on_column_toggled(self, checked: bool, column: str) -> None:
        self.editor.saveNow(lambda: self.table.toggle_column(checked, column))

    def onSortChanged(self, idx: int, ord: int) -> None:
        ord_bool = bool(ord)
        self.editor.saveNow(lambda: self.table.change_sort_column(idx, ord_bool))

    def onRowChanged(self, _new: QItemSelection, _last: QItemSelection) -> None:
        "Update current note and hide/show editor."
        self.editor.saveNow(self._onRowChanged)

    def _onRowChanged(self) -> None:
        """Update window title, editor, previewer etc. after the table selection has changed."""
        if self._closeEventHasCleanedUp:
            return
        self.updateTitle()
        card = self.table.get_single_selected_card()
        self.form.splitter.widget(1).setVisible(card is not None)

        if card is None:
            self.editor.setNote(None)
            self.singleCard = False
            self._renderPreview()
        else:
            self.card = card
            self.editor.setNote(card.note(), focusTo=self.focusTo)
            self.focusTo = None
            self.editor.card = card
            self.singleCard = True
        self._updateFlagsMenu()
        gui_hooks.browser_did_change_row(self)

    def refreshCurrentCard(self, note: Note) -> None:
        self.table.refresh_note(note)
        self._renderPreview()

    def onLoadNote(self, editor: Editor) -> None:
        self.refreshCurrentCard(editor.note)

    # Sidebar
    ######################################################################

    def setupSidebar(self) -> None:
        dw = self.sidebarDockWidget = QDockWidget(tr(TR.BROWSING_SIDEBAR), self)
        dw.setFeatures(QDockWidget.DockWidgetClosable)
        dw.setObjectName("Sidebar")
        dw.setAllowedAreas(Qt.LeftDockWidgetArea)

        self.sidebar = SidebarTreeView(self)
        self.sidebarTree = self.sidebar  # legacy alias
        dw.setWidget(self.sidebar)
        self.sidebar.searchBar = searchBar = SidebarSearchBar(self.sidebar)
        qconnect(
            QShortcut(QKeySequence("Ctrl+Shift+B"), self).activated,
            self.focusSidebarSearchBar,
        )
        l = QVBoxLayout()
        l.addWidget(searchBar)
        l.addWidget(self.sidebar)
        l.setContentsMargins(0, 0, 0, 0)
        l.setSpacing(0)
        w = QWidget()
        w.setLayout(l)
        dw.setWidget(w)
        self.sidebarDockWidget.setFloating(False)

        self.sidebarDockWidget.setTitleBarWidget(QWidget())
        self.addDockWidget(Qt.LeftDockWidgetArea, dw)

        # schedule sidebar to refresh after browser window has loaded, so the
        # UI is more responsive
        self.mw.progress.timer(10, self.sidebar.refresh, False)

    def showSidebar(self) -> None:
        # workaround for PyQt focus bug
        self.editor.hideCompleters()
        self.sidebarDockWidget.setVisible(True)

    def focusSidebar(self) -> None:
        self.showSidebar()
        self.sidebar.setFocus()

    def focusSidebarSearchBar(self) -> None:
        self.showSidebar()
        self.sidebar.searchBar.setFocus()

    # legacy
    def maybeRefreshSidebar(self) -> None:
        self.sidebar.refresh()

    def toggle_sidebar(self) -> None:
        want_visible = not self.sidebarDockWidget.isVisible()
        self.sidebarDockWidget.setVisible(want_visible)
        if want_visible:
            self.sidebar.refresh()

    # Filter button and sidebar helpers
    ######################################################################

    def onFilterButton(self) -> None:
        ml = MenuList()

        ml.addChild(self._todayFilters())
        ml.addChild(self._cardStateFilters())
        ml.addSeparator()

        toggle_sidebar = QAction(tr(TR.BROWSING_SIDEBAR))
        qconnect(toggle_sidebar.triggered, self.toggle_sidebar)
        toggle_sidebar.setCheckable(True)
        toggle_sidebar.setChecked(self.sidebarDockWidget.isVisible())
        ml.addChild(toggle_sidebar)

        ml.popupOver(self.form.filter)

    def update_search(self, *terms: Union[str, SearchTerm]) -> None:
        """Modify the current search string based on modified keys, then refresh."""
        try:
            search = self.col.build_search_string(*terms)
            mods = self.mw.app.keyboardModifiers()
            if mods & Qt.AltModifier:
                search = self.col.build_search_string(search, negate=True)
            cur = str(self.form.searchEdit.lineEdit().text())
            if mods & Qt.ControlModifier and mods & Qt.ShiftModifier:
                search = self.col.replace_search_term(cur, search)
            elif mods & Qt.ControlModifier:
                search = self.col.build_search_string(cur, search)
            elif mods & Qt.ShiftModifier:
                search = self.col.build_search_string(cur, search, match_any=True)
        except InvalidInput as e:
            show_invalid_search_error(e)
        else:
            self.form.searchEdit.lineEdit().setText(search)
            self.onSearchActivated()

    # legacy
    def setFilter(self, *terms: str) -> None:
        self.set_filter_then_search(*terms)

    def _simpleFilters(self, items: Sequence[Tuple[str, SearchTerm]]) -> MenuList:
        ml = MenuList()
        for row in items:
            if row is None:
                ml.addSeparator()
            else:
                label, filter_name = row
                ml.addItem(label, self.sidebar._filter_func(filter_name))
        return ml

    def _todayFilters(self) -> SubMenu:
        subm = SubMenu(tr(TR.BROWSING_TODAY))
        subm.addChild(
            self._simpleFilters(
                (
                    (tr(TR.BROWSING_ADDED_TODAY), SearchTerm(added_in_days=1)),
                    (
                        tr(TR.BROWSING_STUDIED_TODAY),
                        SearchTerm(rated=SearchTerm.Rated(days=1)),
                    ),
                    (
                        tr(TR.BROWSING_AGAIN_TODAY),
                        SearchTerm(
                            rated=SearchTerm.Rated(
                                days=1, rating=SearchTerm.RATING_AGAIN
                            )
                        ),
                    ),
                )
            )
        )
        return subm

    def _cardStateFilters(self) -> SubMenu:
        subm = SubMenu(tr(TR.BROWSING_CARD_STATE))
        subm.addChild(
            self._simpleFilters(
                (
                    (
                        tr(TR.ACTIONS_NEW),
                        SearchTerm(card_state=SearchTerm.CARD_STATE_NEW),
                    ),
                    (
                        tr(TR.SCHEDULING_LEARNING),
                        SearchTerm(card_state=SearchTerm.CARD_STATE_LEARN),
                    ),
                    (
                        tr(TR.SCHEDULING_REVIEW),
                        SearchTerm(card_state=SearchTerm.CARD_STATE_REVIEW),
                    ),
                    (
                        tr(TR.FILTERING_IS_DUE),
                        SearchTerm(card_state=SearchTerm.CARD_STATE_DUE),
                    ),
                    None,
                    (
                        tr(TR.BROWSING_SUSPENDED),
                        SearchTerm(card_state=SearchTerm.CARD_STATE_SUSPENDED),
                    ),
                    (
                        tr(TR.BROWSING_BURIED),
                        SearchTerm(card_state=SearchTerm.CARD_STATE_BURIED),
                    ),
                    None,
                    (tr(TR.ACTIONS_RED_FLAG), SearchTerm(flag=SearchTerm.FLAG_RED)),
                    (
                        tr(TR.ACTIONS_ORANGE_FLAG),
                        SearchTerm(flag=SearchTerm.FLAG_ORANGE),
                    ),
                    (tr(TR.ACTIONS_GREEN_FLAG), SearchTerm(flag=SearchTerm.FLAG_GREEN)),
                    (tr(TR.ACTIONS_BLUE_FLAG), SearchTerm(flag=SearchTerm.FLAG_BLUE)),
                    (tr(TR.BROWSING_NO_FLAG), SearchTerm(flag=SearchTerm.FLAG_NONE)),
                    (tr(TR.BROWSING_ANY_FLAG), SearchTerm(flag=SearchTerm.FLAG_ANY)),
                )
            )
        )
        return subm

    # Info
    ######################################################################

    def showCardInfo(self) -> None:
        if not self.table.has_current():
            return

        info, cs = self._cardInfoData()
        reps = self._revlogData(cs)

        card_info_dialog = CardInfoDialog(self)
        l = QVBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        w = AnkiWebView(title="browser card info")
        l.addWidget(w)
        w.stdHtml(info + "<p>" + reps, context=card_info_dialog)
        bb = QDialogButtonBox(QDialogButtonBox.Close)
        l.addWidget(bb)
        qconnect(bb.rejected, card_info_dialog.reject)
        card_info_dialog.setLayout(l)
        card_info_dialog.setWindowModality(Qt.WindowModal)
        card_info_dialog.resize(500, 400)
        restoreGeom(card_info_dialog, "revlog")
        card_info_dialog.show()

    def _cardInfoData(self) -> Tuple[str, CardStats]:
        cs = CardStats(self.col, self.table.get_current_card())
        rep = cs.report(include_revlog=True)
        return rep, cs

    # legacy - revlog used to be generated here, and some add-ons
    # wrapped this function

    def _revlogData(self, cs: CardStats) -> str:
        return ""

    # Menu helpers
    ######################################################################

    def oneModelNotes(self) -> List[int]:
        sf = self.table.get_selected_note_ids()
        if not sf:
            return []
        mods = self.col.db.scalar(
            """
select count(distinct mid) from notes
where id in %s"""
            % ids2str(sf)
        )
        if mods > 1:
            showInfo(tr(TR.BROWSING_PLEASE_SELECT_CARDS_FROM_ONLY_ONE))
            return []
        return sf

    def onHelp(self) -> None:
        openHelp(HelpPage.BROWSING)

    # Misc menu options
    ######################################################################

    def onChangeModel(self) -> None:
        self.editor.saveNow(self._onChangeModel)

    def _onChangeModel(self) -> None:
        nids = self.oneModelNotes()
        if nids:
            ChangeModel(self, nids)

    def createFilteredDeck(self) -> None:
        search = self.form.searchEdit.lineEdit().text()
        if (
            self.mw.col.schedVer() != 1
            and self.mw.app.keyboardModifiers() & Qt.AltModifier
        ):
            aqt.dialogs.open("DynDeckConfDialog", self.mw, search_2=search)
        else:
            aqt.dialogs.open("DynDeckConfDialog", self.mw, search=search)

    # Preview
    ######################################################################

    def onTogglePreview(self) -> None:
        if self._previewer:
            self._previewer.close()
            self._on_preview_closed()
        else:
            self._previewer = PreviewDialog(self, self.mw, self._on_preview_closed)
            self._previewer.open()

    def _renderPreview(self) -> None:
        if self._previewer:
            if self.singleCard:
                self._previewer.render_card()
            else:
                self.onTogglePreview()

    def _cleanup_preview(self) -> None:
        if self._previewer:
            self._previewer.cancel_timer()
            self._previewer.close()

    def _on_preview_closed(self) -> None:
        if self.editor.web:
            self.editor.web.eval("$('#previewButton').removeClass('highlighted')")
        self._previewer = None

    # Card deletion
    ######################################################################

    def deleteNotes(self) -> None:
        focus = self.focusWidget()
        if focus != self.form.tableView:
            return
        self._deleteNotes()

    def _deleteNotes(self) -> None:
        nids = self.table.get_selected_note_ids()
        if not nids:
            return
        self.mw.checkpoint(tr(TR.BROWSING_DELETE_NOTES))
        self.begin_reset()
        self.table.set_current_to_unselected_note()
        self.col.remove_notes(nids)
        self.search()
        self.mw.reset()
        tooltip(tr(TR.BROWSING_NOTE_DELETED, count=len(nids)))

    # Deck change
    ######################################################################

    def setDeck(self) -> None:
        self.editor.saveNow(self._setDeck)

    def _setDeck(self) -> None:
        from aqt.studydeck import StudyDeck

        cids = self.table.get_selected_card_ids()
        if not cids:
            return
        did = self.mw.col.db.scalar("select did from cards where id = ?", cids[0])
        current = self.mw.col.decks.get(did)["name"]
        ret = StudyDeck(
            self.mw,
            current=current,
            accept=tr(TR.BROWSING_MOVE_CARDS),
            title=tr(TR.BROWSING_CHANGE_DECK),
            help=HelpPage.BROWSING,
            parent=self,
        )
        if not ret.name:
            return
        did = self.col.decks.id(ret.name)
        deck = self.col.decks.get(did)
        if deck["dyn"]:
            showWarning(tr(TR.BROWSING_CARDS_CANT_BE_MANUALLY_MOVED_INTO))
            return
        self.mw.checkpoint(tr(TR.BROWSING_CHANGE_DECK))
        self.begin_reset()
        self.col.set_deck(cids, did)
        self.end_reset()
        self.mw.requireReset(reason=ResetReason.BrowserSetDeck, context=self)

    # Tags
    ######################################################################

    def addTags(
        self,
        tags: Optional[str] = None,
        label: Optional[str] = None,
        prompt: Optional[str] = None,
        func: Optional[Callable] = None,
    ) -> None:
        self.editor.saveNow(lambda: self._addTags(tags, label, prompt, func))

    def _addTags(
        self,
        tags: Optional[str],
        label: Optional[str],
        prompt: Optional[str],
        func: Optional[Callable],
    ) -> None:
        if prompt is None:
            prompt = tr(TR.BROWSING_ENTER_TAGS_TO_ADD)
        if tags is None:
            (tags, r) = getTag(self, self.col, prompt)
        else:
            r = True
        if not r:
            return
        if func is None:
            func = self.col.tags.bulkAdd
        if label is None:
            label = tr(TR.BROWSING_ADD_TAGS)
        if label:
            self.mw.checkpoint(label)
        self.begin_reset()
        func(self.table.get_selected_note_ids(), tags)
        self.end_reset()
        self.mw.requireReset(reason=ResetReason.BrowserAddTags, context=self)

    def deleteTags(
        self, tags: Optional[str] = None, label: Optional[str] = None
    ) -> None:
        if label is None:
            label = tr(TR.BROWSING_DELETE_TAGS)
        self.addTags(
            tags,
            label,
            tr(TR.BROWSING_ENTER_TAGS_TO_DELETE),
            func=self.col.tags.bulkRem,
        )

    def clearUnusedTags(self) -> None:
        self.editor.saveNow(self._clearUnusedTags)

    def _clearUnusedTags(self) -> None:
        def on_done(fut: Future) -> None:
            fut.result()
            self.on_tag_list_update()

        self.mw.taskman.run_in_background(self.col.tags.registerNotes, on_done)

    # Suspending
    ######################################################################

    def isSuspended(self) -> bool:
        return bool(
            self.table.has_current()
            and self.table.get_current_card().queue == QUEUE_TYPE_SUSPENDED
        )

    def onSuspend(self) -> None:
        self.editor.saveNow(self._onSuspend)

    def _onSuspend(self) -> None:
        sus = not self.isSuspended()
        c = self.table.get_selected_card_ids()
        self.begin_reset()
        if sus:
            self.col.sched.suspend_cards(c)
        else:
            self.col.sched.unsuspend_cards(c)
        self.end_reset()
        self.mw.requireReset(reason=ResetReason.BrowserSuspend, context=self)

    # Exporting
    ######################################################################

    def _on_export_notes(self) -> None:
        cids = self.table.get_card_ids_from_selected_note_ids()
        if cids:
            ExportDialog(self.mw, cids=cids)

    # Flags & Marking
    ######################################################################

    def onSetFlag(self, n: int) -> None:
        if not self.table.has_current():
            return
        self.editor.saveNow(lambda: self._on_set_flag(n))

    def _on_set_flag(self, n: int) -> None:
        # flag needs toggling off?
        if n == self.table.get_current_card().userFlag():
            n = 0
        self.begin_reset()
        self.col.setUserFlag(n, self.table.get_selected_card_ids())
        self.end_reset()

    def _updateFlagsMenu(self) -> None:
        flag = self.table.has_current() and self.table.get_current_card().userFlag()
        flag = flag or 0

        f = self.form
        flagActions = [
            f.actionRed_Flag,
            f.actionOrange_Flag,
            f.actionGreen_Flag,
            f.actionBlue_Flag,
        ]

        for c, act in enumerate(flagActions):
            act.setChecked(flag == c + 1)

        qtMenuShortcutWorkaround(self.form.menuFlag)

    def onMark(self, mark: bool = None) -> None:
        if mark is None:
            mark = not self.isMarked()
        if mark:
            self.addTags(tags="marked")
        else:
            self.deleteTags(tags="marked")

    def isMarked(self) -> bool:
        return bool(
            self.table.has_current() and self.table.get_current_note().hasTag("Marked")
        )

    # Repositioning
    ######################################################################

    def reposition(self) -> None:
        self.editor.saveNow(self._reposition)

    def _reposition(self) -> None:
        cids = self.table.get_selected_card_ids()
        cids2 = self.col.db.list(
            f"select id from cards where type = {CARD_TYPE_NEW} and id in "
            + ids2str(cids)
        )
        if not cids2:
            showInfo(tr(TR.BROWSING_ONLY_NEW_CARDS_CAN_BE_REPOSITIONED))
            return
        d = QDialog(self)
        disable_help_button(d)
        d.setWindowModality(Qt.WindowModal)
        frm = aqt.forms.reposition.Ui_Dialog()
        frm.setupUi(d)
        (pmin, pmax) = self.col.db.first(
            f"select min(due), max(due) from cards where type={CARD_TYPE_NEW} and odid=0"
        )
        pmin = pmin or 0
        pmax = pmax or 0
        txt = tr(TR.BROWSING_QUEUE_TOP, val=pmin)
        txt += "\n" + tr(TR.BROWSING_QUEUE_BOTTOM, val=pmax)
        frm.label.setText(txt)
        frm.start.selectAll()
        if not d.exec_():
            return
        self.mw.checkpoint(tr(TR.ACTIONS_REPOSITION))
        self.col.sched.sortCards(
            cids,
            start=frm.start.value(),
            step=frm.step.value(),
            shuffle=frm.randomize.isChecked(),
            shift=frm.shift.isChecked(),
        )
        self.search()
        self.mw.requireReset(reason=ResetReason.BrowserReposition, context=self)

    # Rescheduling
    ######################################################################

    def reschedule(self) -> None:
        self.editor.saveNow(self._reschedule)

    def _reschedule(self) -> None:
        d = QDialog(self)
        disable_help_button(d)
        d.setWindowModality(Qt.WindowModal)
        frm = aqt.forms.reschedule.Ui_Dialog()
        frm.setupUi(d)
        if not d.exec_():
            return
        self.mw.checkpoint(tr(TR.BROWSING_RESCHEDULE))
        if frm.asNew.isChecked():
            self.col.sched.forgetCards(self.table.get_selected_card_ids())
        else:
            fmin = frm.min.value()
            fmax = frm.max.value()
            fmax = max(fmin, fmax)
            self.col.sched.reschedCards(self.table.get_selected_card_ids(), fmin, fmax)
        self.search()
        self.mw.requireReset(reason=ResetReason.BrowserReschedule, context=self)

    # Edit: selection
    ######################################################################

    def selectNotes(self) -> None:
        self.editor.saveNow(self._selectNotes)

    def _selectNotes(self) -> None:
        nids = self.table.get_selected_note_ids()
        # clear the selection so we don't waste energy preserving it
        tv = self.form.tableView
        tv.selectionModel().clear()

        search = self.col.build_search_string(
            SearchTerm(nids=SearchTerm.IdList(ids=nids))
        )
        self.search_for(search)

        tv.selectAll()

    def invertSelection(self) -> None:
        sm = self.form.tableView.selectionModel()
        items = sm.selection()
        self.form.tableView.selectAll()
        sm.select(items, QItemSelectionModel.Deselect | QItemSelectionModel.Rows)

    # Hooks
    ######################################################################

    def setupHooks(self) -> None:
        gui_hooks.undo_state_did_change.append(self.onUndoState)
        gui_hooks.state_did_reset.append(self.onReset)
        gui_hooks.editor_did_fire_typing_timer.append(self.refreshCurrentCard)
        gui_hooks.editor_did_load_note.append(self.onLoadNote)
        gui_hooks.editor_did_unfocus_field.append(self.on_unfocus_field)
        gui_hooks.sidebar_should_refresh_decks.append(self.on_item_added)
        gui_hooks.sidebar_should_refresh_notetypes.append(self.on_item_added)

    def teardownHooks(self) -> None:
        gui_hooks.undo_state_did_change.remove(self.onUndoState)
        gui_hooks.state_did_reset.remove(self.onReset)
        gui_hooks.editor_did_fire_typing_timer.remove(self.refreshCurrentCard)
        gui_hooks.editor_did_load_note.remove(self.onLoadNote)
        gui_hooks.editor_did_unfocus_field.remove(self.on_unfocus_field)
        gui_hooks.sidebar_should_refresh_decks.remove(self.on_item_added)
        gui_hooks.sidebar_should_refresh_notetypes.remove(self.on_item_added)

    def on_unfocus_field(self, changed: bool, note: Note, field_idx: int) -> None:
        self.refreshCurrentCard(note)

    # covers the tag, note and deck case
    def on_item_added(self, item: Any = None) -> None:
        self.sidebar.refresh()

    def on_tag_list_update(self) -> None:
        self.sidebar.refresh()

    def onUndoState(self, on: bool) -> None:
        self.form.actionUndo.setEnabled(on)
        if on:
            self.form.actionUndo.setText(self.mw.form.actionUndo.text())

    # Edit: replacing
    ######################################################################

    def onFindReplace(self) -> None:
        self.editor.saveNow(self._onFindReplace)

    def _onFindReplace(self) -> None:
        nids = self.table.get_selected_note_ids()
        if not nids:
            return
        import anki.find

        def find() -> List[str]:
            return anki.find.fieldNamesForNotes(self.mw.col, nids)

        def on_done(fut: Future) -> None:
            self._on_find_replace_diag(fut.result(), nids)

        self.mw.taskman.with_progress(find, on_done, self)

    def _on_find_replace_diag(self, fields: List[str], nids: List[int]) -> None:
        d = QDialog(self)
        disable_help_button(d)
        frm = aqt.forms.findreplace.Ui_Dialog()
        frm.setupUi(d)
        d.setWindowModality(Qt.WindowModal)

        combo = "BrowserFindAndReplace"
        findhistory = restore_combo_history(frm.find, combo + "Find")
        frm.find.completer().setCaseSensitivity(True)
        replacehistory = restore_combo_history(frm.replace, combo + "Replace")
        frm.replace.completer().setCaseSensitivity(True)

        restore_is_checked(frm.re, combo + "Regex")
        restore_is_checked(frm.ignoreCase, combo + "ignoreCase")

        frm.find.setFocus()
        allfields = [tr(TR.BROWSING_ALL_FIELDS)] + fields
        frm.field.addItems(allfields)
        restore_combo_index_for_session(frm.field, allfields, combo + "Field")
        qconnect(frm.buttonBox.helpRequested, self.onFindReplaceHelp)
        restoreGeom(d, "findreplace")
        r = d.exec_()
        saveGeom(d, "findreplace")
        if not r:
            return

        save_combo_index_for_session(frm.field, combo + "Field")
        if frm.field.currentIndex() == 0:
            field = None
        else:
            field = fields[frm.field.currentIndex() - 1]

        search = save_combo_history(frm.find, findhistory, combo + "Find")
        replace = save_combo_history(frm.replace, replacehistory, combo + "Replace")

        regex = frm.re.isChecked()
        nocase = frm.ignoreCase.isChecked()

        save_is_checked(frm.re, combo + "Regex")
        save_is_checked(frm.ignoreCase, combo + "ignoreCase")

        self.mw.checkpoint(tr(TR.BROWSING_FIND_AND_REPLACE))
        # starts progress dialog as well
        self.begin_reset()

        def do_search() -> int:
            return self.col.find_and_replace(
                nids, search, replace, regex, field, nocase
            )

        def on_done(fut: Future) -> None:
            self.search()
            self.mw.requireReset(reason=ResetReason.BrowserFindReplace, context=self)

            total = len(nids)
            try:
                changed = fut.result()
            except InvalidInput as e:
                show_invalid_search_error(e)
                return

            showInfo(
                tr(TR.FINDREPLACE_NOTES_UPDATED, changed=changed, total=total),
                parent=self,
            )

        self.mw.taskman.run_in_background(do_search, on_done)

    def onFindReplaceHelp(self) -> None:
        openHelp(HelpPage.BROWSING_FIND_AND_REPLACE)

    # Edit: finding dupes
    ######################################################################

    def onFindDupes(self) -> None:
        self.editor.saveNow(self._onFindDupes)

    def _onFindDupes(self) -> None:
        d = QDialog(self)
        self.mw.setupDialogGC(d)
        frm = aqt.forms.finddupes.Ui_Dialog()
        frm.setupUi(d)
        restoreGeom(d, "findDupes")
        disable_help_button(d)
        searchHistory = restore_combo_history(frm.search, "findDupesFind")

        fields = sorted(
            anki.find.fieldNames(self.col, downcase=False), key=lambda x: x.lower()
        )
        frm.fields.addItems(fields)
        restore_combo_index_for_session(frm.fields, fields, "findDupesFields")
        self._dupesButton = None

        # links
        frm.webView.title = "find duplicates"
        web_context = FindDupesDialog(dialog=d, browser=self)
        frm.webView.set_bridge_command(self.dupeLinkClicked, web_context)
        frm.webView.stdHtml("", context=web_context)

        def onFin(code: Any) -> None:
            saveGeom(d, "findDupes")

        qconnect(d.finished, onFin)

        def onClick() -> None:
            search_text = save_combo_history(frm.search, searchHistory, "findDupesFind")
            save_combo_index_for_session(frm.fields, "findDupesFields")
            field = fields[frm.fields.currentIndex()]
            self.duplicatesReport(frm.webView, field, search_text, frm, web_context)

        search = frm.buttonBox.addButton(
            tr(TR.ACTIONS_SEARCH), QDialogButtonBox.ActionRole
        )
        qconnect(search.clicked, onClick)
        d.show()

    def duplicatesReport(
        self,
        web: AnkiWebView,
        fname: str,
        search: str,
        frm: aqt.forms.finddupes.Ui_Dialog,
        web_context: FindDupesDialog,
    ) -> None:
        self.mw.progress.start()
        try:
            res = self.mw.col.findDupes(fname, search)
        except InvalidInput as e:
            self.mw.progress.finish()
            show_invalid_search_error(e)
            return
        if not self._dupesButton:
            self._dupesButton = b = frm.buttonBox.addButton(
                tr(TR.BROWSING_TAG_DUPLICATES), QDialogButtonBox.ActionRole
            )
            qconnect(b.clicked, lambda: self._onTagDupes(res))
        t = ""
        groups = len(res)
        notes = sum(len(r[1]) for r in res)
        part1 = tr(TR.BROWSING_GROUP, count=groups)
        part2 = tr(TR.BROWSING_NOTE_COUNT, count=notes)
        t += tr(TR.BROWSING_FOUND_AS_ACROSS_BS, part=part1, whole=part2)
        t += "<p><ol>"
        for val, nids in res:
            t += (
                """<li><a href=# onclick="pycmd('%s');return false;">%s</a>: %s</a>"""
                % (
                    html.escape(
                        self.col.build_search_string(
                            SearchTerm(nids=SearchTerm.IdList(ids=nids))
                        )
                    ),
                    tr(TR.BROWSING_NOTE_COUNT, count=len(nids)),
                    html.escape(val),
                )
            )
        t += "</ol>"
        web.stdHtml(t, context=web_context)
        self.mw.progress.finish()

    def _onTagDupes(self, res: List[Any]) -> None:
        if not res:
            return
        self.mw.checkpoint(tr(TR.BROWSING_TAG_DUPLICATES))
        self.begin_reset()
        nids = set()
        for _, nidlist in res:
            nids.update(nidlist)
        self.col.tags.bulkAdd(list(nids), tr(TR.BROWSING_DUPLICATE))
        self.mw.progress.finish()
        self.end_reset()
        self.mw.requireReset(reason=ResetReason.BrowserTagDupes, context=self)
        tooltip(tr(TR.BROWSING_NOTES_TAGGED))

    def dupeLinkClicked(self, link: str) -> None:
        self.search_for(link)
        self.onNote()

    # Jumping
    ######################################################################

    def _moveCur(self, dir: int, idx: QModelIndex = None) -> None:
        if not self.table.has_current():
            return
        tv = self.form.tableView
        if idx is None:
            idx = tv.moveCursor(dir, self.mw.app.keyboardModifiers())
        tv.selectionModel().setCurrentIndex(
            idx,
            QItemSelectionModel.Clear
            | QItemSelectionModel.Select
            | QItemSelectionModel.Rows,
        )

    def has_previous_card(self) -> bool:
        return self.table.has_previous()

    def has_next_card(self) -> bool:
        return self.table.has_next()

    def onPreviousCard(self) -> None:
        self.focusTo = self.editor.currentField
        self.editor.saveNow(self.table.to_previous_row)

    def onNextCard(self) -> None:
        self.focusTo = self.editor.currentField
        self.editor.saveNow(self.table.to_next_row)

    def onFirstCard(self) -> None:
        self.table.to_first_row()

    def onLastCard(self) -> None:
        self.table.to_last_row()

    def onFind(self) -> None:
        # workaround for PyQt focus bug
        self.editor.hideCompleters()

        self.form.searchEdit.setFocus()
        self.form.searchEdit.lineEdit().selectAll()

    def onNote(self) -> None:
        # workaround for PyQt focus bug
        self.editor.hideCompleters()

        self.editor.web.setFocus()
        self.editor.loadNote(focusTo=0)

    def onCardList(self) -> None:
        self.form.tableView.setFocus()


# Change model dialog
######################################################################


class ChangeModel(QDialog):
    def __init__(self, browser: Browser, nids: List[int]) -> None:
        QDialog.__init__(self, browser)
        self.browser = browser
        self.nids = nids
        self.oldModel = browser.table.get_current_note().model()
        self.form = aqt.forms.changemodel.Ui_Dialog()
        self.form.setupUi(self)
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModal)
        self.setup()
        restoreGeom(self, "changeModel")
        gui_hooks.state_did_reset.append(self.onReset)
        gui_hooks.current_note_type_did_change.append(self.on_note_type_change)
        self.exec_()

    def on_note_type_change(self, notetype: NoteType) -> None:
        self.onReset()

    def setup(self) -> None:
        # maps
        self.flayout = QHBoxLayout()
        self.flayout.setContentsMargins(0, 0, 0, 0)
        self.fwidg = None
        self.form.fieldMap.setLayout(self.flayout)
        self.tlayout = QHBoxLayout()
        self.tlayout.setContentsMargins(0, 0, 0, 0)
        self.twidg = None
        self.form.templateMap.setLayout(self.tlayout)
        if self.style().objectName() == "gtk+":
            # gtk+ requires margins in inner layout
            self.form.verticalLayout_2.setContentsMargins(0, 11, 0, 0)
            self.form.verticalLayout_3.setContentsMargins(0, 11, 0, 0)
        # model chooser
        import aqt.modelchooser

        self.oldModel = self.browser.col.models.get(
            self.browser.col.db.scalar(
                "select mid from notes where id = ?", self.nids[0]
            )
        )
        self.form.oldModelLabel.setText(self.oldModel["name"])
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.browser.mw, self.form.modelChooserWidget, label=False
        )
        self.modelChooser.models.setFocus()
        qconnect(self.form.buttonBox.helpRequested, self.onHelp)
        self.modelChanged(self.browser.mw.col.models.current())
        self.pauseUpdate = False

    def onReset(self) -> None:
        self.modelChanged(self.browser.col.models.current())

    def modelChanged(self, model: Dict[str, Any]) -> None:
        self.targetModel = model
        self.rebuildTemplateMap()
        self.rebuildFieldMap()

    def rebuildTemplateMap(
        self, key: Optional[str] = None, attr: Optional[str] = None
    ) -> None:
        if not key:
            key = "t"
            attr = "tmpls"
        map = getattr(self, key + "widg")
        lay = getattr(self, key + "layout")
        src = self.oldModel[attr]
        dst = self.targetModel[attr]
        if map:
            lay.removeWidget(map)
            map.deleteLater()
            setattr(self, key + "MapWidget", None)
        map = QWidget()
        l = QGridLayout()
        combos = []
        targets = [x["name"] for x in dst] + [tr(TR.BROWSING_NOTHING)]
        indices = {}
        for i, x in enumerate(src):
            l.addWidget(QLabel(tr(TR.BROWSING_CHANGE_TO, val=x["name"])), i, 0)
            cb = QComboBox()
            cb.addItems(targets)
            idx = min(i, len(targets) - 1)
            cb.setCurrentIndex(idx)
            indices[cb] = idx
            qconnect(
                cb.currentIndexChanged,
                lambda i, cb=cb, key=key: self.onComboChanged(i, cb, key),
            )
            combos.append(cb)
            l.addWidget(cb, i, 1)
        map.setLayout(l)
        lay.addWidget(map)
        setattr(self, key + "widg", map)
        setattr(self, key + "layout", lay)
        setattr(self, key + "combos", combos)
        setattr(self, key + "indices", indices)

    def rebuildFieldMap(self) -> None:
        return self.rebuildTemplateMap(key="f", attr="flds")

    def onComboChanged(self, i: int, cb: QComboBox, key: str) -> None:
        indices = getattr(self, key + "indices")
        if self.pauseUpdate:
            indices[cb] = i
            return
        combos = getattr(self, key + "combos")
        if i == cb.count() - 1:
            # set to 'nothing'
            return
        # find another combo with same index
        for c in combos:
            if c == cb:
                continue
            if c.currentIndex() == i:
                self.pauseUpdate = True
                c.setCurrentIndex(indices[cb])
                self.pauseUpdate = False
                break
        indices[cb] = i

    def getTemplateMap(
        self,
        old: Optional[List[Dict[str, Any]]] = None,
        combos: Optional[List[QComboBox]] = None,
        new: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[int, Optional[int]]:
        if not old:
            old = self.oldModel["tmpls"]
            combos = self.tcombos
            new = self.targetModel["tmpls"]
        template_map: Dict[int, Optional[int]] = {}
        for i, f in enumerate(old):
            idx = combos[i].currentIndex()
            if idx == len(new):
                # ignore
                template_map[f["ord"]] = None
            else:
                f2 = new[idx]
                template_map[f["ord"]] = f2["ord"]
        return template_map

    def getFieldMap(self) -> Dict[int, Optional[int]]:
        return self.getTemplateMap(
            old=self.oldModel["flds"], combos=self.fcombos, new=self.targetModel["flds"]
        )

    def cleanup(self) -> None:
        gui_hooks.state_did_reset.remove(self.onReset)
        gui_hooks.current_note_type_did_change.remove(self.on_note_type_change)
        self.modelChooser.cleanup()
        saveGeom(self, "changeModel")

    def reject(self) -> None:
        self.cleanup()
        return QDialog.reject(self)

    def accept(self) -> None:
        # check maps
        fmap = self.getFieldMap()
        cmap = self.getTemplateMap()
        if any(True for c in list(cmap.values()) if c is None):
            if not askUser(tr(TR.BROWSING_ANY_CARDS_MAPPED_TO_NOTHING_WILL)):
                return
        self.browser.mw.checkpoint(tr(TR.BROWSING_CHANGE_NOTE_TYPE))
        b = self.browser
        b.mw.col.modSchema(check=True)
        b.mw.progress.start()
        b.begin_reset()
        mm = b.mw.col.models
        mm.change(self.oldModel, self.nids, self.targetModel, fmap, cmap)
        b.search()
        b.mw.progress.finish()
        b.mw.reset()
        self.cleanup()
        QDialog.accept(self)

    def onHelp(self) -> None:
        openHelp(HelpPage.BROWSING_OTHER_MENU_ITEMS)


# Card Info Dialog
######################################################################


class CardInfoDialog(QDialog):
    silentlyClose = True

    def __init__(self, browser: Browser) -> None:
        super().__init__(browser)
        self.browser = browser
        disable_help_button(self)

    def reject(self) -> None:
        saveGeom(self, "revlog")
        return QDialog.reject(self)
