# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import json
import math
import re
from collections.abc import Callable, Sequence
from typing import Any, cast

import aqt
import aqt.browser
import aqt.editor
import aqt.forms
import aqt.operations
from anki._legacy import deprecated
from anki.cards import Card, CardId
from anki.collection import Collection, Config, OpChanges, SearchNode
from anki.consts import *
from anki.decks import DeckId
from anki.errors import NotFoundError
from anki.lang import without_unicode_isolation
from anki.models import NotetypeId
from anki.notes import NoteId
from anki.scheduler.base import ScheduleCardsAsNew
from anki.tags import MARKED_TAG
from anki.utils import is_mac
from aqt import AnkiQt, gui_hooks
from aqt.editor import Editor, EditorWebView
from aqt.errors import show_exception
from aqt.exporting import ExportDialog as LegacyExportDialog
from aqt.import_export.exporting import ExportDialog
from aqt.operations.card import set_card_deck, set_card_flag
from aqt.operations.collection import redo, undo
from aqt.operations.note import remove_notes
from aqt.operations.scheduling import (
    bury_cards,
    forget_cards,
    reposition_new_cards_dialog,
    set_due_date_dialog,
    suspend_cards,
    unbury_cards,
    unsuspend_cards,
)
from aqt.operations.tag import (
    add_tags_to_notes,
    clear_unused_tags,
    remove_tags_from_notes,
)
from aqt.qt import *
from aqt.sound import av_player
from aqt.switch import Switch
from aqt.undo import UndoActionsInfo
from aqt.utils import (
    HelpPage,
    KeyboardModifiersPressed,
    add_ellipsis_to_action_label,
    current_window,
    ensure_editor_saved,
    getTag,
    no_arg_trigger,
    openHelp,
    qtMenuShortcutWorkaround,
    restoreGeom,
    restoreSplitter,
    restoreState,
    saveGeom,
    saveSplitter,
    saveState,
    showWarning,
    skip_if_selection_is_empty,
    tooltip,
    tr,
)

from ..addcards import AddCards
from ..changenotetype import change_notetype_dialog
from .card_info import BrowserCardInfo
from .find_and_replace import FindAndReplaceDialog
from .layout import BrowserLayout, QSplitterHandleEventFilter
from .previewer import BrowserPreviewer as PreviewDialog
from .previewer import Previewer
from .sidebar import SidebarTreeView
from .table import Table


class MockModel:
    """This class only exists to support some legacy aliases."""

    def __init__(self, browser: aqt.browser.Browser) -> None:
        self.browser = browser

    @deprecated(replaced_by=aqt.operations.CollectionOp)
    def beginReset(self) -> None:
        self.browser.begin_reset()

    @deprecated(replaced_by=aqt.operations.CollectionOp)
    def endReset(self) -> None:
        self.browser.end_reset()

    @deprecated(replaced_by=aqt.operations.CollectionOp)
    def reset(self) -> None:
        self.browser.begin_reset()
        self.browser.end_reset()


class Browser(QMainWindow):
    mw: AnkiQt
    col: Collection
    editor: Editor | None
    table: Table

    def __init__(
        self,
        mw: AnkiQt,
        card: Card | None = None,
        search: tuple[str | SearchNode] | None = None,
    ) -> None:
        """
        card -- try to select the provided card after executing "search" or
                "deck:current" (if "search" was None)
        search -- set and perform search; caller must ensure validity
        """

        QMainWindow.__init__(self, None, Qt.WindowType.Window)
        self.mw = mw
        self.col = self.mw.col
        self.lastFilter = ""
        self.focusTo: int | None = None
        self._previewer: Previewer | None = None
        self._card_info = BrowserCardInfo(self.mw)
        self._closeEventHasCleanedUp = False
        self.auto_layout = True
        self.aspect_ratio = 0.0
        self.form = aqt.forms.browser.Ui_Dialog()
        self.form.setupUi(self)
        self.form.splitter.setChildrenCollapsible(False)
        splitter_handle_event_filter = QSplitterHandleEventFilter(self.form.splitter)

        splitter_handle = self.form.splitter.handle(1)
        assert splitter_handle is not None

        splitter_handle.installEventFilter(splitter_handle_event_filter)
        # set if exactly 1 row is selected; used by the previewer
        self.card: Card | None = None
        self.current_card: Card | None = None
        self.setupSidebar()
        self.setup_table()
        self.setupMenus()
        self.setupHooks()
        self.setupEditor()
        gui_hooks.browser_will_show(self)

        # restoreXXX() should be called after all child widgets have been created
        # and attached to QMainWindow
        self._editor_state_key = (
            "editorRTL"
            if self.layoutDirection() == Qt.LayoutDirection.RightToLeft
            else "editor"
        )
        restoreGeom(self, self._editor_state_key)
        restoreSplitter(self.form.splitter, "editor3")
        restoreState(self, self._editor_state_key)

        # responsive layout
        if self.height() != 0:
            self.aspect_ratio = self.width() / self.height()
        self.set_layout(self.mw.pm.browser_layout(), True)
        # disable undo/redo
        self.on_undo_state_change(mw.undo_actions_info())
        # legacy alias
        self.model = MockModel(self)
        self.setupSearch(card, search)
        self.show()

    def on_operation_did_execute(
        self, changes: OpChanges, handler: object | None
    ) -> None:
        focused = current_window() == self
        self.table.op_executed(changes, handler, focused)
        self.sidebar.op_executed(changes, handler, focused)
        if changes.note_text:
            if handler is not self.editor:
                # fixme: this will leave the splitter shown, but with no current
                # note being edited
                assert self.editor is not None

                note = self.editor.note
                if note:
                    try:
                        note.load()
                    except NotFoundError:
                        self.editor.set_note(None)
                        return
                    self.editor.set_note(note)

        if changes.browser_table and changes.card:
            self.card = self.table.get_single_selected_card()
            self.current_card = self.table.get_current_card()
            self._update_card_info()
            self._update_current_actions()

        # changes.card is required for updating flag icon
        if changes.note_text or changes.card:
            self._renderPreview()

    def on_focus_change(self, new: QWidget | None, old: QWidget | None) -> None:
        if current_window() == self:
            self.setUpdatesEnabled(True)
            self.table.redraw_cells()
            self.sidebar.refresh_if_needed()

    def set_layout(self, mode: BrowserLayout, init: bool = False) -> None:
        self.mw.pm.set_browser_layout(mode)

        if mode == BrowserLayout.AUTO:
            self.auto_layout = True
            self.maybe_update_layout(self.aspect_ratio, True)
            self.form.actionLayoutAuto.setChecked(True)
            self.form.actionLayoutVertical.setChecked(False)
            self.form.actionLayoutHorizontal.setChecked(False)
            if not init:
                tooltip(tr.qt_misc_layout_auto_enabled())
        else:
            self.auto_layout = False
            self.form.actionLayoutAuto.setChecked(False)

            if mode == BrowserLayout.VERTICAL:
                self.form.splitter.setOrientation(Qt.Orientation.Vertical)
                self.form.actionLayoutVertical.setChecked(True)
                self.form.actionLayoutHorizontal.setChecked(False)
                if not init:
                    tooltip(tr.qt_misc_layout_vertical_enabled())

            elif mode == BrowserLayout.HORIZONTAL:
                self.form.splitter.setOrientation(Qt.Orientation.Horizontal)
                self.form.actionLayoutHorizontal.setChecked(True)
                self.form.actionLayoutVertical.setChecked(False)
                if not init:
                    tooltip(tr.qt_misc_layout_horizontal_enabled())

    def maybe_update_layout(self, aspect_ratio: float, force: bool = False) -> None:
        if force or math.floor(aspect_ratio) != math.floor(self.aspect_ratio):
            if aspect_ratio < 1:
                self.form.splitter.setOrientation(Qt.Orientation.Vertical)
            else:
                self.form.splitter.setOrientation(Qt.Orientation.Horizontal)

    def resizeEvent(self, event: QResizeEvent | None) -> None:
        assert event is not None

        if self.height() != 0:
            aspect_ratio = self.width() / self.height()

            if self.auto_layout:
                self.maybe_update_layout(aspect_ratio)

            self.aspect_ratio = aspect_ratio

        QMainWindow.resizeEvent(self, event)

    def get_active_note_type_id(self) -> NotetypeId | None:
        """
        If multiple cards are selected the note type will be derived
        from the final card selected
        """
        if current_note := self.table.get_current_note():
            return current_note.mid

        return None

    def add_card(self, deck_id: DeckId):
        add_cards = cast(AddCards, aqt.dialogs.open("AddCards", self.mw))
        add_cards.set_deck(deck_id)

        if note_type_id := self.get_active_note_type_id():
            add_cards.set_note_type(note_type_id)

    def setupMenus(self) -> None:
        # actions
        f = self.form

        # edit
        qconnect(f.actionUndo.triggered, self.undo)
        qconnect(f.actionRedo.triggered, self.redo)
        qconnect(f.actionInvertSelection.triggered, self.table.invert_selection)
        qconnect(f.actionSelectNotes.triggered, self.selectNotes)
        if not is_mac:
            f.actionClose.setVisible(False)
        qconnect(f.actionCreateFilteredDeck.triggered, self.createFilteredDeck)
        f.actionCreateFilteredDeck.setShortcuts(["Ctrl+G", "Ctrl+Alt+G"])

        # view
        qconnect(f.actionFullScreen.triggered, self.mw.on_toggle_full_screen)
        qconnect(
            f.actionZoomIn.triggered,
            lambda: self._editor_web_view().setZoomFactor(
                self._editor_web_view().zoomFactor() + 0.1
            ),
        )
        qconnect(
            f.actionZoomOut.triggered,
            lambda: self._editor_web_view().setZoomFactor(
                self._editor_web_view().zoomFactor() - 0.1
            ),
        )
        qconnect(
            f.actionResetZoom.triggered,
            lambda: self._editor_web_view().setZoomFactor(1),
        )
        qconnect(
            self.form.actionLayoutAuto.triggered,
            lambda: self.set_layout(BrowserLayout.AUTO),
        )
        qconnect(
            self.form.actionLayoutVertical.triggered,
            lambda: self.set_layout(BrowserLayout.VERTICAL),
        )
        qconnect(
            self.form.actionLayoutHorizontal.triggered,
            lambda: self.set_layout(BrowserLayout.HORIZONTAL),
        )

        # notes
        qconnect(f.actionAdd.triggered, self.mw.onAddCard)
        qconnect(f.actionCopy.triggered, self.on_create_copy)
        qconnect(f.actionAdd_Tags.triggered, self.add_tags_to_selected_notes)
        qconnect(f.actionRemove_Tags.triggered, self.remove_tags_from_selected_notes)
        qconnect(f.actionClear_Unused_Tags.triggered, self.clear_unused_tags)
        qconnect(f.actionToggle_Mark.triggered, self.toggle_mark_of_selected_notes)
        qconnect(f.actionChangeModel.triggered, self.onChangeModel)
        qconnect(f.actionFindDuplicates.triggered, self.onFindDupes)
        qconnect(f.actionFindReplace.triggered, self.onFindReplace)
        qconnect(f.actionManage_Note_Types.triggered, self.mw.onNoteTypes)
        qconnect(f.actionDelete.triggered, self.delete_selected_notes)

        # cards
        qconnect(f.actionChange_Deck.triggered, self.set_deck_of_selected_cards)
        qconnect(f.action_Info.triggered, self.showCardInfo)
        qconnect(f.actionReposition.triggered, self.reposition)
        qconnect(f.action_set_due_date.triggered, self.set_due_date)
        qconnect(f.action_forget.triggered, self.forget_cards)
        qconnect(f.actionToggle_Suspend.triggered, self.suspend_selected_cards)
        qconnect(f.action_toggle_bury.triggered, self.bury_selected_cards)

        def set_flag_func(desired_flag: int) -> Callable:
            return lambda: self.set_flag_of_selected_cards(desired_flag)

        for flag in self.mw.flags.all():
            qconnect(
                getattr(self.form, flag.action).triggered, set_flag_func(flag.index)
            )
        self._update_flag_labels()
        qconnect(f.actionExport.triggered, self._on_export_notes)

        # jumps
        qconnect(f.actionPreviousCard.triggered, self.onPreviousCard)
        qconnect(f.actionNextCard.triggered, self.onNextCard)
        qconnect(f.actionFirstCard.triggered, self.onFirstCard)
        qconnect(f.actionLastCard.triggered, self.onLastCard)
        qconnect(f.actionFind.triggered, self.onFind)
        qconnect(f.actionNote.triggered, self.onNote)
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

        add_ellipsis_to_action_label(f.actionCopy)
        add_ellipsis_to_action_label(f.action_forget)

    def _editor_web_view(self) -> EditorWebView:
        assert self.editor is not None
        editor_web_view = self.editor.web
        assert editor_web_view is not None
        return editor_web_view

    def closeEvent(self, evt: QCloseEvent | None) -> None:
        assert evt is not None

        if self._closeEventHasCleanedUp:
            evt.accept()
            return

        assert self.editor is not None

        self.editor.call_after_note_saved(self._closeWindow)
        evt.ignore()

    def _closeWindow(self) -> None:
        assert self.editor is not None

        self._cleanup_preview()
        self._card_info.close()
        self.editor.cleanup()
        self.table.cleanup()
        self.sidebar.cleanup()
        saveSplitter(self.form.splitter, "editor3")
        saveGeom(self, self._editor_state_key)
        saveState(self, self._editor_state_key)
        self.teardownHooks()
        self.mw.maybeReset()
        aqt.dialogs.markClosed("Browser")
        self._closeEventHasCleanedUp = True
        self.mw.deferred_delete_and_garbage_collect(self)
        self.close()

    @ensure_editor_saved
    def closeWithCallback(self, onsuccess: Callable) -> None:
        self._closeWindow()
        onsuccess()

    def keyPressEvent(self, evt: QKeyEvent | None) -> None:
        assert evt is not None

        if evt.key() == Qt.Key.Key_Escape:
            self.close()
        else:
            super().keyPressEvent(evt)

    def reopen(
        self,
        _mw: AnkiQt,
        card: Card | None = None,
        search: tuple[str | SearchNode] | None = None,
    ) -> None:
        if search is not None:
            self.search_for_terms(*search)
            self.form.searchEdit.setFocus()
        if card is not None:
            if search is None:
                # implicitly assume 'card' is in the current deck
                self._default_search(card)
                self.form.searchEdit.setFocus()
            self.table.select_single_card(card.id)

    # Searching
    ######################################################################

    def setupSearch(
        self,
        card: Card | None = None,
        search: tuple[str | SearchNode] | None = None,
    ) -> None:
        assert self.mw.pm.profile is not None

        line_edit = self._line_edit()
        qconnect(line_edit.returnPressed, self.onSearchActivated)
        self.form.searchEdit.setCompleter(None)
        line_edit.setPlaceholderText(tr.browsing_search_bar_hint())
        line_edit.setMaxLength(2000000)
        self.form.searchEdit.addItems(
            [""] + self.mw.pm.profile.get("searchHistory", [])
        )
        if search is not None:
            self.search_for_terms(*search)
        else:
            self._default_search(card)
        self.form.searchEdit.setFocus()
        if card:
            self.table.select_single_card(card.id)

    # search triggered by user
    @ensure_editor_saved
    def onSearchActivated(self) -> None:
        text = self.current_search()
        try:
            normed = self.col.build_search_string(text)
        except Exception as err:
            showWarning(str(err))
        else:
            self.search_for(normed)
            self.update_history()

    def search_for(self, search: str, prompt: str | None = None) -> None:
        """Keep track of search string so that we reuse identical search when
        refreshing, rather than whatever is currently in the search field.
        Optionally set the search bar to a different text than the actual search.
        """

        self._lastSearchTxt = search
        prompt = search if prompt is None else prompt
        self.form.searchEdit.setCurrentIndex(-1)
        self._line_edit().setText(prompt)
        self.search()

    def current_search(self) -> str:
        return self._line_edit().text()

    def search(self) -> None:
        """Search triggered programmatically. Caller must have saved note first."""

        try:
            self.table.search(self._lastSearchTxt)
        except Exception as err:
            showWarning(str(err))

    def update_history(self) -> None:
        assert self.mw.pm.profile is not None

        sh = self.mw.pm.profile.get("searchHistory", [])
        if self._lastSearchTxt in sh:
            sh.remove(self._lastSearchTxt)
        sh.insert(0, self._lastSearchTxt)
        sh = sh[:30]
        self.form.searchEdit.clear()
        self.form.searchEdit.addItems(sh)
        self.mw.pm.profile["searchHistory"] = sh

    def updateTitle(self) -> None:
        selected = self.table.len_selection()
        cur = self.table.len()
        tr_title = (
            tr.browsing_window_title_notes
            if self.table.is_notes_mode()
            else tr.browsing_window_title
        )
        self.setWindowTitle(
            without_unicode_isolation(tr_title(total=cur, selected=selected))
        )

    def search_for_terms(self, *search_terms: str | SearchNode) -> None:
        search = self.col.build_search_string(*search_terms)
        self.form.searchEdit.setEditText(search)
        self.onSearchActivated()

    def _default_search(self, card: Card | None = None) -> None:
        default = self.col.get_config_string(Config.String.DEFAULT_SEARCH_TEXT)
        if default.strip():
            search = default
            prompt = default
        else:
            search = self.col.build_search_string(SearchNode(deck="current"))
            prompt = ""
        if card is not None:
            search = gui_hooks.default_search(search, card)
        self.search_for(search, prompt)

    def onReset(self) -> None:
        self.sidebar.refresh()
        self.begin_reset()
        self.end_reset()

    # caller must have called editor.saveNow() before calling this or .reset()
    def begin_reset(self) -> None:
        assert self.editor is not None

        self.editor.set_note(None, hide=False)
        self.mw.progress.start()
        self.table.begin_reset()

    def end_reset(self) -> None:
        self.table.end_reset()
        self.mw.progress.finish()

    # Table & Editor
    ######################################################################

    def setup_table(self) -> None:
        self.table = Table(self)
        self.table.set_view(self.form.tableView)
        self._switch = switch = Switch(12, tr.browsing_cards(), tr.browsing_notes())
        switch.setChecked(self.table.is_notes_mode())
        switch.setToolTip(tr.browsing_toggle_showing_cards_notes())
        qconnect(self.form.action_toggle_mode.triggered, switch.toggle)
        qconnect(switch.toggled, self.on_table_state_changed)
        self.form.gridLayout.addWidget(switch, 0, 0)

    def setupEditor(self) -> None:
        QShortcut(QKeySequence("Ctrl+Shift+P"), self, self.onTogglePreview)

        def add_preview_button(editor: Editor) -> None:
            editor._links["preview"] = lambda _editor: self.onTogglePreview()

        gui_hooks.editor_did_init.append(add_preview_button)
        self.editor = aqt.editor.Editor(
            self.mw,
            self.form.fieldsArea,
            self,
            editor_mode=aqt.editor.EditorMode.BROWSER,
        )
        gui_hooks.editor_did_init.remove(add_preview_button)

    @ensure_editor_saved
    def on_all_or_selected_rows_changed(self) -> None:
        """Called after the selected or all rows (searching, toggling mode) have
        changed. Update window title, card preview, context actions, and editor.
        """
        if self._closeEventHasCleanedUp:
            return

        self.updateTitle()
        # if there is only one selected card, use it in the editor
        # it might differ from the current card
        self.card = self.table.get_single_selected_card()
        self.singleCard = bool(self.card)

        splitter_widget = self.form.splitter.widget(1)
        assert splitter_widget is not None

        splitter_widget.setVisible(self.singleCard)

        assert self.editor is not None

        if self.singleCard:
            assert self.card is not None

            self.editor.set_note(self.card.note(), focusTo=self.focusTo)
            self.focusTo = None
            self.editor.card = self.card
        else:
            self.editor.set_note(None)
        self._renderPreview()
        self._update_row_actions()
        self._update_selection_actions()
        gui_hooks.browser_did_change_row(self)

    @deprecated(info="please use on_all_or_selected_rows_changed() instead.")
    def onRowChanged(self, *args: Any) -> None:
        self.on_all_or_selected_rows_changed()

    def on_current_row_changed(self) -> None:
        """Called after the row of the current element has changed."""
        if self._closeEventHasCleanedUp:
            return
        self.current_card = self.table.get_current_card()
        self._update_current_actions()
        self._update_card_info()

    def _update_row_actions(self) -> None:
        has_rows = bool(self.table.len())
        self.form.actionSelectAll.setEnabled(has_rows)
        self.form.actionInvertSelection.setEnabled(has_rows)
        self.form.actionFirstCard.setEnabled(has_rows)
        self.form.actionLastCard.setEnabled(has_rows)

    def _update_selection_actions(self) -> None:
        has_selection = bool(self.table.len_selection())
        self.form.actionSelectNotes.setEnabled(has_selection)
        self.form.actionExport.setEnabled(has_selection)
        self.form.actionAdd_Tags.setEnabled(has_selection)
        self.form.actionRemove_Tags.setEnabled(has_selection)
        self.form.actionToggle_Mark.setEnabled(has_selection)
        self.form.actionChangeModel.setEnabled(has_selection)
        self.form.actionDelete.setEnabled(has_selection)
        self.form.actionChange_Deck.setEnabled(has_selection)
        self.form.action_set_due_date.setEnabled(has_selection)
        self.form.action_forget.setEnabled(has_selection)
        self.form.actionReposition.setEnabled(has_selection)
        self.form.actionToggle_Suspend.setEnabled(has_selection)
        self.form.action_toggle_bury.setEnabled(has_selection)
        self.form.menuFlag.setEnabled(has_selection)

    def _update_current_actions(self) -> None:
        self._update_flags_menu()
        self._update_toggle_bury_action()
        self._update_toggle_mark_action()
        self._update_toggle_suspend_action()
        self.form.actionCopy.setEnabled(self.table.has_current())
        self.form.action_Info.setEnabled(self.table.has_current())
        self.form.actionPreviousCard.setEnabled(self.table.has_previous())
        self.form.actionNextCard.setEnabled(self.table.has_next())

    @ensure_editor_saved
    def on_table_state_changed(self, checked: bool) -> None:
        self.mw.progress.start()
        try:
            self.table.toggle_state(checked, self._lastSearchTxt)
        except Exception as err:
            self.mw.progress.finish()
            self._switch.blockSignals(True)
            self._switch.toggle()
            self._switch.blockSignals(False)
            show_exception(parent=self, exception=err)
        else:
            self.mw.progress.finish()

    # Sidebar
    ######################################################################

    def setupSidebar(self) -> None:
        dw = self.sidebarDockWidget = QDockWidget(tr.browsing_sidebar(), self)
        dw.setFeatures(QDockWidget.DockWidgetFeature.NoDockWidgetFeatures)
        dw.setObjectName("Sidebar")
        dock_area = (
            Qt.DockWidgetArea.RightDockWidgetArea
            if self.layoutDirection() == Qt.LayoutDirection.RightToLeft
            else Qt.DockWidgetArea.LeftDockWidgetArea
        )
        dw.setAllowedAreas(dock_area)

        self.sidebar = SidebarTreeView(self)
        self.sidebarTree = self.sidebar  # legacy alias
        dw.setWidget(self.sidebar)
        qconnect(
            self.form.actionSidebarFilter.triggered,
            self.focusSidebarSearchBar,
        )
        grid = QGridLayout()
        grid.addWidget(self.sidebar.searchBar, 0, 0)
        grid.addWidget(self.sidebar.toolbar, 0, 1)
        grid.addWidget(self.sidebar, 1, 0, 1, 2)
        grid.setContentsMargins(8, 4, 0, 0)
        grid.setSpacing(0)
        w = QWidget()
        w.setLayout(grid)
        dw.setWidget(w)
        self.sidebarDockWidget.setFloating(False)

        self.sidebarDockWidget.setTitleBarWidget(QWidget())
        self.addDockWidget(dock_area, dw)

        # schedule sidebar to refresh after browser window has loaded, so the
        # UI is more responsive
        self.mw.progress.timer(10, self.sidebar.refresh, False, parent=self.sidebar)

    def showSidebar(self) -> None:
        self.sidebarDockWidget.setVisible(True)

    def focusSidebar(self) -> None:
        self.showSidebar()
        self.sidebar.setFocus()

    def focusSidebarSearchBar(self) -> None:
        self.showSidebar()
        self.sidebar.searchBar.setFocus()

    def toggle_sidebar(self) -> None:
        want_visible = not self.sidebarDockWidget.isVisible()
        self.sidebarDockWidget.setVisible(want_visible)
        if want_visible:
            self.sidebar.refresh()

    # legacy

    def setFilter(self, *terms: str) -> None:
        self.sidebar.update_search(*terms)

    # Info
    ######################################################################

    def showCardInfo(self) -> None:
        self._card_info.show()

    def _update_card_info(self) -> None:
        self._card_info.set_card(self.current_card)

    # Menu helpers
    ######################################################################

    def selected_cards(self) -> Sequence[CardId]:
        return self.table.get_selected_card_ids()

    def selected_notes(self) -> Sequence[NoteId]:
        return self.table.get_selected_note_ids()

    def selectedNotesAsCards(self) -> Sequence[CardId]:
        return self.table.get_card_ids_from_selected_note_ids()

    def onHelp(self) -> None:
        openHelp(HelpPage.BROWSING)

    # legacy

    selectedCards = selected_cards
    selectedNotes = selected_notes

    # Misc menu options
    ######################################################################

    def on_create_copy(self) -> None:
        if note := self.table.get_current_note():
            current_card = self.table.get_current_card()
            assert current_card is not None

            deck_id = current_card.current_deck_id()
            aqt.dialogs.open("AddCards", self.mw).set_note(note, deck_id)

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def onChangeModel(self) -> None:
        ids = self.selected_notes()
        change_notetype_dialog(parent=self, note_ids=ids)

    def createFilteredDeck(self) -> None:
        search = self.current_search()
        if KeyboardModifiersPressed().alt:
            aqt.dialogs.open("FilteredDeckConfigDialog", self.mw, search_2=search)
        else:
            aqt.dialogs.open("FilteredDeckConfigDialog", self.mw, search=search)

    # Preview
    ######################################################################

    def onTogglePreview(self) -> None:
        assert self.editor is not None

        if self._previewer:
            self._previewer.close()
        elif self.editor.note:
            self._previewer = PreviewDialog(self, self.mw, self._on_preview_closed)
            self._previewer.open()
            self.toggle_preview_button_state(True)

    def _renderPreview(self) -> None:
        if self._previewer:
            if self.singleCard:
                self._previewer.render_card()
            else:
                self.onTogglePreview()

    def toggle_preview_button_state(self, active: bool) -> None:
        assert self.editor is not None

        if self.editor.web:
            self.editor.web.eval(f"togglePreviewButtonState({json.dumps(active)});")

    def _cleanup_preview(self) -> None:
        if self._previewer:
            self._previewer.cancel_timer()
            self._previewer.close()

    def _on_preview_closed(self) -> None:
        av_player.stop_and_clear_queue()
        self.toggle_preview_button_state(False)
        self._previewer = None

    # Card deletion
    ######################################################################

    @no_arg_trigger
    @skip_if_selection_is_empty
    def delete_selected_notes(self) -> None:
        # ensure deletion is not accidentally triggered when the user is focused
        # in the editing screen or search bar
        focus = self.focusWidget()
        if focus != self.form.tableView:
            return

        assert self.editor is not None

        self.editor.set_note(None)
        nids = self.table.to_row_of_unselected_note()
        remove_notes(parent=self, note_ids=nids).run_in_background()

    # legacy

    deleteNotes = delete_selected_notes

    # Deck change
    ######################################################################

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def set_deck_of_selected_cards(self) -> None:
        from aqt.studydeck import StudyDeck

        assert self.mw.col is not None
        assert self.mw.col.db is not None

        cids = self.table.get_selected_card_ids()
        did = self.mw.col.db.scalar("select did from cards where id = ?", cids[0])

        deck_dict = self.mw.col.decks.get(did)
        assert deck_dict is not None

        current = deck_dict["name"]

        def callback(ret: StudyDeck) -> None:
            if not ret.name:
                return
            did = self.col.decks.id(ret.name)

            assert did is not None

            set_card_deck(parent=self, card_ids=cids, deck_id=did).run_in_background()

        StudyDeck(
            self.mw,
            current=current,
            accept=tr.browsing_move_cards(),
            title=tr.browsing_change_deck(),
            help=HelpPage.BROWSING,
            parent=self,
            callback=callback,
        )

    # legacy

    setDeck = set_deck_of_selected_cards

    # Tags
    ######################################################################

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def add_tags_to_selected_notes(
        self,
        tags: str | None = None,
    ) -> None:
        "Shows prompt if tags not provided."
        if not (tags := tags or self._prompt_for_tags(tr.browsing_enter_tags_to_add())):
            return

        space_separated_tags = re.sub(r"[ \n\t\v]+", " ", tags)
        add_tags_to_notes(
            parent=self,
            note_ids=self.selected_notes(),
            space_separated_tags=space_separated_tags,
        ).run_in_background(initiator=self)

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def remove_tags_from_selected_notes(self, tags: str | None = None) -> None:
        "Shows prompt if tags not provided."
        if not (
            tags := tags or self._prompt_for_tags(tr.browsing_enter_tags_to_delete())
        ):
            return

        remove_tags_from_notes(
            parent=self, note_ids=self.selected_notes(), space_separated_tags=tags
        ).run_in_background(initiator=self)

    def _prompt_for_tags(self, prompt: str) -> str | None:
        (tags, ok) = getTag(self, self.col, prompt)
        if not ok:
            return None
        else:
            return tags

    @no_arg_trigger
    @ensure_editor_saved
    def clear_unused_tags(self) -> None:
        clear_unused_tags(parent=self).run_in_background()

    addTags = add_tags_to_selected_notes
    deleteTags = remove_tags_from_selected_notes
    clearUnusedTags = clear_unused_tags

    # Suspending
    ######################################################################

    def _update_toggle_suspend_action(self) -> None:
        is_suspended = bool(
            self.current_card and self.current_card.queue == QUEUE_TYPE_SUSPENDED
        )
        self.form.actionToggle_Suspend.setChecked(is_suspended)

    @skip_if_selection_is_empty
    @ensure_editor_saved
    def suspend_selected_cards(self, checked: bool) -> None:
        cids = self.selected_cards()
        if checked:
            suspend_cards(parent=self, card_ids=cids).run_in_background()
        else:
            unsuspend_cards(parent=self.mw, card_ids=cids).run_in_background()

    # Burying
    ######################################################################

    def _update_toggle_bury_action(self) -> None:
        is_buried = bool(
            self.current_card
            and self.current_card.queue
            in (QUEUE_TYPE_MANUALLY_BURIED, QUEUE_TYPE_SIBLING_BURIED)
        )
        self.form.action_toggle_bury.setChecked(is_buried)

    @skip_if_selection_is_empty
    @ensure_editor_saved
    def bury_selected_cards(self, checked: bool) -> None:
        cids = self.selected_cards()
        if checked:
            bury_cards(parent=self, card_ids=cids).run_in_background()
        else:
            unbury_cards(parent=self.mw, card_ids=cids).run_in_background()

    # Exporting
    ######################################################################

    @no_arg_trigger
    @skip_if_selection_is_empty
    def _on_export_notes(self) -> None:
        if not self.mw.pm.legacy_import_export():
            nids = self.selected_notes()
            ExportDialog(self.mw, nids=nids, parent=self)
        else:
            cids = self.selectedNotesAsCards()
            LegacyExportDialog(self.mw, cids=list(cids), parent=self)

    # Flags & Marking
    ######################################################################

    @skip_if_selection_is_empty
    @ensure_editor_saved
    def set_flag_of_selected_cards(self, flag: int) -> None:
        if not self.current_card:
            return

        # flag needs toggling off?
        if flag == self.current_card.user_flag():
            flag = 0

        set_card_flag(
            parent=self, card_ids=self.selected_cards(), flag=flag
        ).run_in_background()

    def _update_flags_menu(self) -> None:
        flag = self.current_card and self.current_card.user_flag()
        flag = flag or 0

        for f in self.mw.flags.all():
            getattr(self.form, f.action).setChecked(flag == f.index)

        qtMenuShortcutWorkaround(self.form.menuFlag)

    def _update_flag_labels(self) -> None:
        for flag in self.mw.flags.all():
            getattr(self.form, flag.action).setText(flag.label)

    def toggle_mark_of_selected_notes(self, checked: bool) -> None:
        if checked:
            self.add_tags_to_selected_notes(tags=MARKED_TAG)
        else:
            self.remove_tags_from_selected_notes(tags=MARKED_TAG)

    def _update_toggle_mark_action(self) -> None:
        is_marked = bool(
            self.current_card and self.current_card.note().has_tag(MARKED_TAG)
        )
        self.form.actionToggle_Mark.setChecked(is_marked)

    # Scheduling
    ######################################################################

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def reposition(self) -> None:
        if op := reposition_new_cards_dialog(
            parent=self, card_ids=self.selected_cards()
        ):
            op.run_in_background()

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def set_due_date(self) -> None:
        if op := set_due_date_dialog(
            parent=self,
            card_ids=self.selected_cards(),
            config_key=Config.String.SET_DUE_BROWSER,
        ):
            op.run_in_background()

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def forget_cards(self) -> None:
        if op := forget_cards(
            parent=self,
            card_ids=self.selected_cards(),
            context=ScheduleCardsAsNew.Context.BROWSER,
        ):
            op.run_in_background()

    # Edit: selection
    ######################################################################

    @no_arg_trigger
    @skip_if_selection_is_empty
    @ensure_editor_saved
    def selectNotes(self) -> None:
        nids = self.selected_notes()
        # clear the selection so we don't waste energy preserving it
        self.table.clear_selection()
        search = self.col.build_search_string(
            SearchNode(nids=SearchNode.IdList(ids=nids))
        )
        self.search_for(search)
        self.table.select_all()

    # Hooks
    ######################################################################

    def setupHooks(self) -> None:
        gui_hooks.undo_state_did_change.append(self.on_undo_state_change)
        gui_hooks.backend_will_block.append(self.table.on_backend_will_block)
        gui_hooks.backend_did_block.append(self.table.on_backend_did_block)
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        gui_hooks.focus_did_change.append(self.on_focus_change)
        gui_hooks.flag_label_did_change.append(self._update_flag_labels)
        gui_hooks.collection_will_temporarily_close.append(self._on_temporary_close)

    def teardownHooks(self) -> None:
        gui_hooks.undo_state_did_change.remove(self.on_undo_state_change)
        gui_hooks.backend_will_block.remove(self.table.on_backend_will_block)
        gui_hooks.backend_did_block.remove(self.table.on_backend_did_block)
        gui_hooks.operation_did_execute.remove(self.on_operation_did_execute)
        gui_hooks.focus_did_change.remove(self.on_focus_change)
        gui_hooks.flag_label_did_change.remove(self._update_flag_labels)
        gui_hooks.collection_will_temporarily_close.remove(self._on_temporary_close)

    def _on_temporary_close(self, col: Collection) -> None:
        # we could reload browser columns in the future; for now we just close
        self.close()

    # Undo
    ######################################################################

    def undo(self) -> None:
        undo(parent=self)

    def redo(self) -> None:
        redo(parent=self)

    def on_undo_state_change(self, info: UndoActionsInfo) -> None:
        self.form.actionUndo.setText(info.undo_text)
        self.form.actionUndo.setEnabled(info.can_undo)
        self.form.actionRedo.setText(info.redo_text)
        self.form.actionRedo.setEnabled(info.can_redo)
        self.form.actionRedo.setVisible(info.show_redo)

    # Edit: replacing
    ######################################################################

    @no_arg_trigger
    @ensure_editor_saved
    def onFindReplace(self) -> None:
        FindAndReplaceDialog(self, mw=self.mw, note_ids=self.selected_notes())

    # Edit: finding dupes
    ######################################################################

    @no_arg_trigger
    @ensure_editor_saved
    def onFindDupes(self) -> None:
        from aqt.browser.find_duplicates import FindDuplicatesDialog

        FindDuplicatesDialog(browser=self, mw=self.mw)

    # Jumping
    ######################################################################

    def has_previous_card(self) -> bool:
        return self.table.has_previous()

    def has_next_card(self) -> bool:
        return self.table.has_next()

    def onPreviousCard(self) -> None:
        assert self.editor is not None

        self.focusTo = self.editor.currentField
        self.editor.call_after_note_saved(self.table.to_previous_row)

    def onNextCard(self) -> None:
        assert self.editor is not None

        self.focusTo = self.editor.currentField
        self.editor.call_after_note_saved(self.table.to_next_row)

    def onFirstCard(self) -> None:
        self.table.to_first_row()

    def onLastCard(self) -> None:
        self.table.to_last_row()

    def onFind(self) -> None:
        self.form.searchEdit.setFocus()
        self._line_edit().selectAll()

    def onNote(self) -> None:
        assert self.editor is not None
        assert self.editor.web is not None

        self.editor.web.setFocus()
        self.editor.loadNote(focusTo=0)

    def onCardList(self) -> None:
        self.form.tableView.setFocus()

    def _line_edit(self) -> QLineEdit:
        line_edit = self.form.searchEdit.lineEdit()
        assert line_edit is not None
        return line_edit
