# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
import time
from concurrent.futures import Future
from dataclasses import dataclass, field
from operator import itemgetter
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple, Union, cast

import aqt
import aqt.forms
from anki.cards import Card
from anki.collection import Collection, Config, OpChanges, SearchNode
from anki.consts import *
from anki.errors import InvalidInput, NotFoundError
from anki.lang import without_unicode_isolation
from anki.models import NoteType
from anki.stats import CardStats
from anki.tags import MARKED_TAG
from anki.utils import htmlToTextLine, ids2str, isMac, isWin
from aqt import AnkiQt, colors, gui_hooks
from aqt.card_ops import set_card_deck, set_card_flag
from aqt.editor import Editor
from aqt.exporting import ExportDialog
from aqt.main import ResetReason
from aqt.note_ops import (
    add_tags,
    clear_unused_tags,
    find_and_replace,
    remove_notes,
    remove_tags_for_notes,
)
from aqt.previewer import BrowserPreviewer as PreviewDialog
from aqt.previewer import Previewer
from aqt.qt import *
from aqt.scheduling_ops import (
    forget_cards,
    reposition_new_cards_dialog,
    set_due_date_dialog,
    suspend_cards,
    unsuspend_cards,
)
from aqt.sidebar import SidebarTreeView
from aqt.theme import theme_manager
from aqt.utils import (
    TR,
    HelpPage,
    KeyboardModifiersPressed,
    askUser,
    current_top_level_widget,
    disable_help_button,
    ensure_editor_saved,
    ensure_editor_saved_on_trigger,
    getTag,
    openHelp,
    qtMenuShortcutWorkaround,
    restore_combo_history,
    restore_combo_index_for_session,
    restore_is_checked,
    restoreGeom,
    restoreHeader,
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
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView


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
    card_ids: Optional[Sequence[int]] = None


# Data model
##########################################################################

# temporary cache to avoid hitting the database on redraw
@dataclass
class Cell:
    text: str = ""
    font: Optional[Tuple[str, int]] = None
    is_rtl: bool = False


@dataclass
class CellRow:
    columns: List[Cell]
    refreshed_at: float = field(default_factory=time.time)
    card_flag: int = 0
    marked: bool = False
    suspended: bool = False

    def is_stale(self, threshold: float) -> bool:
        return self.refreshed_at < threshold


class DataModel(QAbstractTableModel):
    def __init__(self, browser: Browser) -> None:
        QAbstractTableModel.__init__(self)
        self.browser = browser
        self.col = browser.col
        self.sortKey = None
        self.activeCols = self.col.get_config(
            "activeCols", ["noteFld", "template", "cardDue", "deck"]
        )
        self.cards: Sequence[int] = []
        self.cardObjs: Dict[int, Card] = {}
        self._row_cache: Dict[int, CellRow] = {}
        self._last_refresh = 0.0
        # serve stale content to avoid hitting the DB?
        self.block_updates = False

    def getCard(self, index: QModelIndex) -> Optional[Card]:
        return self._get_card_by_row(index.row())

    def _get_card_by_row(self, row: int) -> Optional[Card]:
        "None if card is not in DB."
        id = self.cards[row]
        if not id in self.cardObjs:
            try:
                card = self.col.getCard(id)
            except NotFoundError:
                # deleted
                card = None
            self.cardObjs[id] = card
        return self.cardObjs[id]

    # Card and cell data cache
    ######################################################################
    # Stopgap until we can fetch this data a row at a time from Rust.

    def get_cell(self, index: QModelIndex) -> Cell:
        row = self.get_row(index.row())
        return row.columns[index.column()]

    def get_row(self, row: int) -> CellRow:
        if entry := self._row_cache.get(row):
            if not self.block_updates and entry.is_stale(self._last_refresh):
                # need to refresh
                entry = self._build_cell_row(row)
                self._row_cache[row] = entry
                return entry
            else:
                # return entry, even if it's stale
                return entry
        elif self.block_updates:
            # blank entry until we unblock
            return CellRow(columns=[Cell(text="blocked")] * len(self.activeCols))
        else:
            # missing entry, need to build
            entry = self._build_cell_row(row)
            self._row_cache[row] = entry
            return entry

    def _build_cell_row(self, row: int) -> CellRow:
        if not (card := self._get_card_by_row(row)):
            cell = Cell(text=tr(TR.BROWSING_ROW_DELETED))
            return CellRow(columns=[cell] * len(self.activeCols))

        return CellRow(
            columns=[
                Cell(
                    text=self._column_data(card, column_type),
                    font=self._font(card, column_type),
                    is_rtl=self._is_rtl(card, column_type),
                )
                for column_type in self.activeCols
            ],
            # should probably make these an enum instead?
            card_flag=card.user_flag(),
            marked=card.note().has_tag(MARKED_TAG),
            suspended=card.queue == QUEUE_TYPE_SUSPENDED,
        )

    # Model interface
    ######################################################################

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:
        if parent and parent.isValid():
            return 0
        return len(self.cards)

    def columnCount(self, parent: QModelIndex = QModelIndex()) -> int:
        if parent and parent.isValid():
            return 0
        return len(self.activeCols)

    def data(self, index: QModelIndex = QModelIndex(), role: int = 0) -> Any:
        if not index.isValid():
            return
        if role == Qt.FontRole:
            if font := self.get_cell(index).font:
                qfont = QFont()
                qfont.setFamily(font[0])
                qfont.setPixelSize(font[1])
                return qfont
            else:
                return None

        elif role == Qt.TextAlignmentRole:
            align: Union[Qt.AlignmentFlag, int] = Qt.AlignVCenter
            if self.activeCols[index.column()] not in (
                "question",
                "answer",
                "template",
                "deck",
                "noteFld",
                "note",
                "noteTags",
            ):
                align |= Qt.AlignHCenter
            return align
        elif role == Qt.DisplayRole or role == Qt.EditRole:
            return self.get_cell(index).text
        else:
            return

    def headerData(
        self, section: int, orientation: Qt.Orientation, role: int = 0
    ) -> Optional[str]:
        if orientation == Qt.Vertical:
            return None
        elif role == Qt.DisplayRole and section < len(self.activeCols):
            type = self.columnType(section)
            txt = None
            for stype, name in self.browser.columns:
                if type == stype:
                    txt = name
                    break
            # give the user a hint an invalid column was added by an add-on
            if not txt:
                txt = tr(TR.BROWSING_ADDON)
            return txt
        else:
            return None

    def flags(self, index: QModelIndex) -> Qt.ItemFlags:
        return cast(Qt.ItemFlags, Qt.ItemIsEnabled | Qt.ItemIsSelectable)

    # Filtering
    ######################################################################

    def search(self, txt: str) -> None:
        self.beginReset()
        self.cards = []
        try:
            ctx = SearchContext(search=txt, browser=self.browser)
            gui_hooks.browser_will_search(ctx)
            if ctx.card_ids is None:
                ctx.card_ids = self.col.find_cards(ctx.search, order=ctx.order)
            gui_hooks.browser_did_search(ctx)
            self.cards = ctx.card_ids
        except Exception as err:
            raise err
        finally:
            self.endReset()

    def redraw_cells(self) -> None:
        "Update cell contents, without changing search count/columns/sorting."
        if not self.cards:
            return
        top_left = self.index(0, 0)
        bottom_right = self.index(len(self.cards) - 1, len(self.activeCols) - 1)
        self._last_refresh = time.time()
        self.dataChanged.emit(top_left, bottom_right)  # type: ignore

    def reset(self) -> None:
        self.beginReset()
        self.endReset()

    # caller must have called editor.saveNow() before calling this or .reset()
    def beginReset(self) -> None:
        self.browser.editor.set_note(None, hide=False)
        self.browser.mw.progress.start()
        self.saveSelection()
        self.beginResetModel()
        self.cardObjs = {}
        self._row_cache = {}

    def endReset(self) -> None:
        self.endResetModel()
        self.restoreSelection()
        self.browser.mw.progress.finish()

    def reverse(self) -> None:
        self.browser.editor.call_after_note_saved(self._reverse)

    def _reverse(self) -> None:
        self.beginReset()
        self.cards = list(reversed(self.cards))
        self.endReset()

    def saveSelection(self) -> None:
        cards = self.browser.selected_cards()
        self.selectedCards = {id: True for id in cards}
        if getattr(self.browser, "card", None):
            self.focusedCard = self.browser.card.id
        else:
            self.focusedCard = None

    def restoreSelection(self) -> None:
        if not self.cards:
            return
        sm = self.browser.form.tableView.selectionModel()
        sm.clear()
        # restore selection
        items = QItemSelection()
        count = 0
        firstIdx = None
        focusedIdx = None
        for row, id in enumerate(self.cards):
            # if the id matches the focused card, note the index
            if self.focusedCard == id:
                focusedIdx = self.index(row, 0)
                items.select(focusedIdx, focusedIdx)
                self.focusedCard = None
            # if the card was previously selected, select again
            if id in self.selectedCards:
                count += 1
                idx = self.index(row, 0)
                items.select(idx, idx)
                # note down the first card of the selection, in case we don't
                # have a focused card
                if not firstIdx:
                    firstIdx = idx
        # focus previously focused or first in selection
        idx = focusedIdx or firstIdx
        tv = self.browser.form.tableView
        if idx:
            row = idx.row()
            pos = tv.rowViewportPosition(row)
            visible = pos >= 0 and pos < tv.viewport().height()
            tv.selectRow(row)

            # we save and then restore the horizontal scroll position because
            # scrollTo() also scrolls horizontally which is confusing
            if not visible:
                h = tv.horizontalScrollBar().value()
                tv.scrollTo(idx, tv.PositionAtCenter)
                tv.horizontalScrollBar().setValue(h)
            if count < 500:
                # discard large selections; they're too slow
                sm.select(
                    items, QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows
                )
        else:
            tv.selectRow(0)

    def op_executed(self, op: OpChanges, focused: bool) -> None:
        print("op executed")
        if op.card or op.note or op.deck or op.notetype:
            # clear card cache
            self.cardObjs = {}
        if focused:
            self.redraw_cells()

    def begin_blocking(self) -> None:
        self.block_updates = True

    def end_blocking(self) -> None:
        self.block_updates = False
        self.redraw_cells()

    # Column data
    ######################################################################

    def columnType(self, column: int) -> str:
        return self.activeCols[column]

    def time_format(self) -> str:
        return "%Y-%m-%d"

    def _font(self, card: Card, column_type: str) -> Optional[Tuple[str, int]]:
        if column_type not in ("question", "answer", "noteFld"):
            return None

        template = card.template()
        if not template.get("bfont"):
            return None

        return (
            cast(str, template.get("bfont", "arial")),
            cast(int, template.get("bsize", 12)),
        )

    # legacy
    def columnData(self, index: QModelIndex) -> str:
        col = index.column()
        type = self.columnType(col)
        c = self.getCard(index)
        if not c:
            return tr(TR.BROWSING_ROW_DELETED)
        else:
            return self._column_data(c, type)

    def _column_data(self, card: Card, column_type: str) -> str:
        type = column_type
        if type == "question":
            return self.question(card)
        elif type == "answer":
            return self.answer(card)
        elif type == "noteFld":
            f = card.note()
            return htmlToTextLine(f.fields[self.col.models.sortIdx(f.model())])
        elif type == "template":
            t = card.template()["name"]
            if card.model()["type"] == MODEL_CLOZE:
                t = f"{t} {card.ord + 1}"
            return cast(str, t)
        elif type == "cardDue":
            # catch invalid dates
            try:
                t = self._next_due(card)
            except:
                t = ""
            if card.queue < 0:
                t = f"({t})"
            return t
        elif type == "noteCrt":
            return time.strftime(
                self.time_format(), time.localtime(card.note().id / 1000)
            )
        elif type == "noteMod":
            return time.strftime(self.time_format(), time.localtime(card.note().mod))
        elif type == "cardMod":
            return time.strftime(self.time_format(), time.localtime(card.mod))
        elif type == "cardReps":
            return str(card.reps)
        elif type == "cardLapses":
            return str(card.lapses)
        elif type == "noteTags":
            return " ".join(card.note().tags)
        elif type == "note":
            return card.model()["name"]
        elif type == "cardIvl":
            if card.type == CARD_TYPE_NEW:
                return tr(TR.BROWSING_NEW)
            elif card.type == CARD_TYPE_LRN:
                return tr(TR.BROWSING_LEARNING)
            return self.col.format_timespan(card.ivl * 86400)
        elif type == "cardEase":
            if card.type == CARD_TYPE_NEW:
                return tr(TR.BROWSING_NEW)
            return "%d%%" % (card.factor / 10)
        elif type == "deck":
            if card.odid:
                # in a cram deck
                return "%s (%s)" % (
                    self.browser.mw.col.decks.name(card.did),
                    self.browser.mw.col.decks.name(card.odid),
                )
            # normal deck
            return self.browser.mw.col.decks.name(card.did)
        else:
            return ""

    def question(self, c: Card) -> str:
        return htmlToTextLine(c.q(browser=True))

    def answer(self, c: Card) -> str:
        if c.template().get("bafmt"):
            # they have provided a template, use it verbatim
            c.q(browser=True)
            return htmlToTextLine(c.a())
        # need to strip question from answer
        q = self.question(c)
        a = htmlToTextLine(c.a())
        if a.startswith(q):
            return a[len(q) :].strip()
        return a

    # legacy
    def nextDue(self, c: Card, index: QModelIndex) -> str:
        return self._next_due(c)

    def _next_due(self, card: Card) -> str:
        date: float
        if card.odid:
            return tr(TR.BROWSING_FILTERED)
        elif card.queue == QUEUE_TYPE_LRN:
            date = card.due
        elif card.queue == QUEUE_TYPE_NEW or card.type == CARD_TYPE_NEW:
            return tr(TR.STATISTICS_DUE_FOR_NEW_CARD, number=card.due)
        elif card.queue in (QUEUE_TYPE_REV, QUEUE_TYPE_DAY_LEARN_RELEARN) or (
            card.type == CARD_TYPE_REV and card.queue < 0
        ):
            date = time.time() + ((card.due - self.col.sched.today) * 86400)
        else:
            return ""
        return time.strftime(self.time_format(), time.localtime(date))

    # legacy
    def isRTL(self, index: QModelIndex) -> bool:
        col = index.column()
        type = self.columnType(col)
        c = self.getCard(index)
        return self._is_rtl(c, type)

    def _is_rtl(self, card: Card, column_type: str) -> bool:
        if column_type != "noteFld":
            return False

        nt = card.note().model()
        return nt["flds"][self.col.models.sortIdx(nt)]["rtl"]


# Line painter
######################################################################


class StatusDelegate(QItemDelegate):
    def __init__(self, browser: Browser, model: DataModel) -> None:
        QItemDelegate.__init__(self, browser)
        self.browser = browser
        self.model = model

    def paint(
        self, painter: QPainter, option: QStyleOptionViewItem, index: QModelIndex
    ) -> None:
        row = self.model.get_row(index.row())
        cell = row.columns[index.column()]

        if cell.is_rtl:
            option.direction = Qt.RightToLeft

        if row.card_flag:
            color = getattr(colors, f"FLAG{row.card_flag}_BG")
        elif row.marked:
            color = colors.MARKED_BG
        elif row.suspended:
            color = colors.SUSPENDED_BG
        else:
            color = None

        if color:
            brush = QBrush(theme_manager.qcolor(color))
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()

        return QItemDelegate.paint(self, painter, option, index)


# Browser window
######################################################################


class Browser(QMainWindow):
    model: DataModel
    mw: AnkiQt
    col: Collection
    editor: Optional[Editor]

    def __init__(
        self,
        mw: AnkiQt,
        card: Optional[Card] = None,
        search: Optional[Tuple[Union[str, SearchNode]]] = None,
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
        self.card: Optional[Card] = None
        self.setupColumns()
        self.setupTable()
        self.setupMenus()
        self.setupHeaders()
        self.setupHooks()
        self.setupEditor()
        self.updateFont()
        self.onUndoState(self.mw.form.actionUndo.isEnabled())
        self.setupSearch(card, search)
        gui_hooks.browser_will_show(self)
        self.show()

    def on_backend_will_block(self) -> None:
        # make sure the card list doesn't try to refresh itself during the operation,
        # as that will block the UI
        self.model.begin_blocking()

    def on_backend_did_block(self) -> None:
        self.model.end_blocking()

    def on_operation_did_execute(self, changes: OpChanges) -> None:
        focused = current_top_level_widget() == self
        self.model.op_executed(changes, focused)
        self.sidebar.op_executed(changes, focused)
        if changes.note or changes.notetype:
            if not self.editor.is_updating_note():
                # fixme: this will leave the splitter shown, but with no current
                # note being edited
                note = self.editor.note
                if note:
                    try:
                        note.load()
                    except NotFoundError:
                        self.editor.set_note(None)
                        return
                    self.editor.set_note(note)

            self._renderPreview()

    def on_focus_change(self, new: Optional[QWidget], old: Optional[QWidget]) -> None:
        if current_top_level_widget() == self:
            self.setUpdatesEnabled(True)
            self.model.redraw_cells()
            self.sidebar.refresh_if_needed()

    def setupMenus(self) -> None:
        # pylint: disable=unnecessary-lambda
        # actions
        f = self.form
        # edit
        qconnect(f.actionUndo.triggered, self.undo)
        qconnect(f.actionInvertSelection.triggered, self.invertSelection)
        qconnect(f.actionSelectNotes.triggered, self.selectNotes)
        if not isMac:
            f.actionClose.setVisible(False)
        qconnect(f.actionCreateFilteredDeck.triggered, self.createFilteredDeck)
        f.actionCreateFilteredDeck.setShortcuts(["Ctrl+G", "Ctrl+Alt+G"])
        # notes
        qconnect(f.actionAdd.triggered, self.mw.onAddCard)
        qconnect(f.actionAdd_Tags.triggered, lambda: self.add_tags_to_selected_notes())
        qconnect(
            f.actionRemove_Tags.triggered,
            lambda: self.remove_tags_from_selected_notes(),
        )
        qconnect(f.actionClear_Unused_Tags.triggered, self.clear_unused_tags)
        qconnect(
            f.actionToggle_Mark.triggered, lambda: self.toggle_mark_of_selected_notes()
        )
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
        qconnect(f.actionRed_Flag.triggered, lambda: self.set_flag_of_selected_cards(1))
        qconnect(
            f.actionOrange_Flag.triggered, lambda: self.set_flag_of_selected_cards(2)
        )
        qconnect(
            f.actionGreen_Flag.triggered, lambda: self.set_flag_of_selected_cards(3)
        )
        qconnect(
            f.actionBlue_Flag.triggered, lambda: self.set_flag_of_selected_cards(4)
        )
        qconnect(f.actionExport.triggered, lambda: self._on_export_notes())
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

        # context menu
        self.form.tableView.setContextMenuPolicy(Qt.CustomContextMenu)
        qconnect(self.form.tableView.customContextMenuRequested, self.onContextMenu)

    def onContextMenu(self, _point: QPoint) -> None:
        m = QMenu()
        for act in self.form.menu_Cards.actions():
            m.addAction(act)
        m.addSeparator()
        for act in self.form.menu_Notes.actions():
            m.addAction(act)
        gui_hooks.browser_will_show_context_menu(self, m)
        qtMenuShortcutWorkaround(m)
        m.exec_(QCursor.pos())

    def updateFont(self) -> None:
        # we can't choose different line heights efficiently, so we need
        # to pick a line height big enough for any card template
        curmax = 16
        for m in self.col.models.all():
            for t in m["tmpls"]:
                bsize = t.get("bsize", 0)
                if bsize > curmax:
                    curmax = bsize
        self.form.tableView.verticalHeader().setDefaultSectionSize(curmax + 6)

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._closeEventHasCleanedUp:
            evt.accept()
            return
        self.editor.call_after_note_saved(self._closeWindow)
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
        self.mw.deferred_delete_and_garbage_collect(self)
        self.close()

    @ensure_editor_saved
    def closeWithCallback(self, onsuccess: Callable) -> None:
        self._closeWindow()
        onsuccess()

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() == Qt.Key_Escape:
            self.close()
        else:
            super().keyPressEvent(evt)

    def setupColumns(self) -> None:
        self.columns = [
            ("question", tr(TR.BROWSING_QUESTION)),
            ("answer", tr(TR.BROWSING_ANSWER)),
            ("template", tr(TR.BROWSING_CARD)),
            ("deck", tr(TR.DECKS_DECK)),
            ("noteFld", tr(TR.BROWSING_SORT_FIELD)),
            ("noteCrt", tr(TR.BROWSING_CREATED)),
            ("noteMod", tr(TR.SEARCH_NOTE_MODIFIED)),
            ("cardMod", tr(TR.SEARCH_CARD_MODIFIED)),
            ("cardDue", tr(TR.STATISTICS_DUE_DATE)),
            ("cardIvl", tr(TR.BROWSING_INTERVAL)),
            ("cardEase", tr(TR.BROWSING_EASE)),
            ("cardReps", tr(TR.SCHEDULING_REVIEWS)),
            ("cardLapses", tr(TR.SCHEDULING_LAPSES)),
            ("noteTags", tr(TR.EDITING_TAGS)),
            ("note", tr(TR.BROWSING_NOTE)),
        ]
        self.columns.sort(key=itemgetter(1))

    def reopen(
        self,
        _mw: AnkiQt,
        card: Optional[Card] = None,
        search: Optional[Tuple[Union[str, SearchNode]]] = None,
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
        search: Optional[Tuple[Union[str, SearchNode]]] = None,
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
                self.col.build_search_string(SearchNode(deck="current")), ""
            )
        self.form.searchEdit.setFocus()

    # search triggered by user
    @ensure_editor_saved
    def onSearchActivated(self) -> None:
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

    def current_search(self) -> str:
        return self.form.searchEdit.lineEdit().text()

    def search(self) -> None:
        """Search triggered programmatically. Caller must have saved note first."""

        try:
            self.model.search(self._lastSearchTxt)
        except Exception as err:
            show_invalid_search_error(err)
        if not self.model.cards:
            # no row change will fire
            self.onRowChanged(None, None)

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
        selected = len(self.form.tableView.selectionModel().selectedRows())
        cur = len(self.model.cards)
        self.setWindowTitle(
            without_unicode_isolation(
                tr(TR.BROWSING_WINDOW_TITLE, total=cur, selected=selected)
            )
        )
        return selected

    def search_for_terms(self, *search_terms: Union[str, SearchNode]) -> None:
        search = self.col.build_search_string(*search_terms)
        self.form.searchEdit.setEditText(search)
        self.onSearchActivated()

    def show_single_card(self, card: Card) -> None:
        if card.nid:

            def on_show_single_card() -> None:
                self.card = card
                search = self.col.build_search_string(SearchNode(nid=card.nid))
                search = gui_hooks.default_search(search, card)
                self.search_for(search, "")
                self.focusCid(card.id)

            self.editor.call_after_note_saved(on_show_single_card)

    def onReset(self) -> None:
        self.sidebar.refresh()
        self.model.reset()

    # Table view & editor
    ######################################################################

    def setupTable(self) -> None:
        self.model = DataModel(self)
        self.form.tableView.setSortingEnabled(True)
        self.form.tableView.setModel(self.model)
        self.form.tableView.selectionModel()
        self.form.tableView.setItemDelegate(StatusDelegate(self, self.model))
        qconnect(
            self.form.tableView.selectionModel().selectionChanged, self.onRowChanged
        )
        self.form.tableView.setWordWrap(False)
        if not theme_manager.night_mode:
            self.form.tableView.setStyleSheet(
                "QTableView{ selection-background-color: rgba(150, 150, 150, 50); "
                "selection-color: black; }"
            )
        elif theme_manager.macos_dark_mode():
            grid = colors.FRAME_BG
            self.form.tableView.setStyleSheet(
                f"""
QTableView {{ gridline-color: {grid} }}           
            """
            )
        self.singleCard = False

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

    @ensure_editor_saved
    def onRowChanged(
        self, current: Optional[QItemSelection], previous: Optional[QItemSelection]
    ) -> None:
        """Update current note and hide/show editor."""
        if self._closeEventHasCleanedUp:
            return
        update = self.updateTitle()
        show = self.model.cards and update == 1
        idx = self.form.tableView.selectionModel().currentIndex()
        if idx.isValid():
            self.card = self.model.getCard(idx)
            show = show and self.card is not None
        self.form.splitter.widget(1).setVisible(bool(show))

        if not show:
            self.editor.set_note(None)
            self.singleCard = False
            self._renderPreview()
        else:
            self.editor.set_note(self.card.note(reload=True), focusTo=self.focusTo)
            self.focusTo = None
            self.editor.card = self.card
            self.singleCard = True
        self._update_flags_menu()
        gui_hooks.browser_did_change_row(self)

    def currentRow(self) -> int:
        idx = self.form.tableView.selectionModel().currentIndex()
        return idx.row()

    # Headers & sorting
    ######################################################################

    def setupHeaders(self) -> None:
        vh = self.form.tableView.verticalHeader()
        hh = self.form.tableView.horizontalHeader()
        if not isWin:
            vh.hide()
            hh.show()
        restoreHeader(hh, "editor")
        hh.setHighlightSections(False)
        hh.setMinimumSectionSize(50)
        hh.setSectionsMovable(True)
        self.setColumnSizes()
        hh.setContextMenuPolicy(Qt.CustomContextMenu)
        qconnect(hh.customContextMenuRequested, self.onHeaderContext)
        self.setSortIndicator()
        qconnect(hh.sortIndicatorChanged, self.onSortChanged)
        qconnect(hh.sectionMoved, self.onColumnMoved)

    @ensure_editor_saved
    def onSortChanged(self, idx: int, ord: int) -> None:
        ord = bool(ord)
        type = self.model.activeCols[idx]
        noSort = ("question", "answer")
        if type in noSort:
            showInfo(tr(TR.BROWSING_SORTING_ON_THIS_COLUMN_IS_NOT))
            type = self.col.conf["sortType"]
        if self.col.conf["sortType"] != type:
            self.col.conf["sortType"] = type
            # default to descending for non-text fields
            if type == "noteFld":
                ord = not ord
            self.col.set_config_bool(Config.Bool.BROWSER_SORT_BACKWARDS, ord)
            self.col.save()
            self.search()
        else:
            if self.col.get_config_bool(Config.Bool.BROWSER_SORT_BACKWARDS) != ord:
                self.col.set_config_bool(Config.Bool.BROWSER_SORT_BACKWARDS, ord)
                self.col.save()
                self.model.reverse()
        self.setSortIndicator()

    def setSortIndicator(self) -> None:
        hh = self.form.tableView.horizontalHeader()
        type = self.col.conf["sortType"]
        if type not in self.model.activeCols:
            hh.setSortIndicatorShown(False)
            return
        idx = self.model.activeCols.index(type)
        if self.col.get_config_bool(Config.Bool.BROWSER_SORT_BACKWARDS):
            ord = Qt.DescendingOrder
        else:
            ord = Qt.AscendingOrder
        hh.blockSignals(True)
        hh.setSortIndicator(idx, ord)
        hh.blockSignals(False)
        hh.setSortIndicatorShown(True)

    def onHeaderContext(self, pos: QPoint) -> None:
        gpos = self.form.tableView.mapToGlobal(pos)
        m = QMenu()
        for type, name in self.columns:
            a = m.addAction(name)
            a.setCheckable(True)
            a.setChecked(type in self.model.activeCols)
            qconnect(a.toggled, lambda b, t=type: self.toggleField(t))
        gui_hooks.browser_header_will_show_context_menu(self, m)
        m.exec_(gpos)

    @ensure_editor_saved_on_trigger
    def toggleField(self, type: str) -> None:
        self.model.beginReset()
        if type in self.model.activeCols:
            if len(self.model.activeCols) < 2:
                self.model.endReset()
                showInfo(tr(TR.BROWSING_YOU_MUST_HAVE_AT_LEAST_ONE))
                return
            self.model.activeCols.remove(type)
            adding = False
        else:
            self.model.activeCols.append(type)
            adding = True
        self.col.conf["activeCols"] = self.model.activeCols
        # sorted field may have been hidden
        self.setSortIndicator()
        self.setColumnSizes()
        self.model.endReset()
        # if we added a column, scroll to it
        if adding:
            row = self.currentRow()
            idx = self.model.index(row, len(self.model.activeCols) - 1)
            self.form.tableView.scrollTo(idx)

    def setColumnSizes(self) -> None:
        hh = self.form.tableView.horizontalHeader()
        hh.setSectionResizeMode(QHeaderView.Interactive)
        hh.setSectionResizeMode(
            hh.logicalIndex(len(self.model.activeCols) - 1), QHeaderView.Stretch
        )
        # this must be set post-resize or it doesn't work
        hh.setCascadingSectionResizes(False)

    def onColumnMoved(self, *args: Any) -> None:
        self.setColumnSizes()

    def setupSidebar(self) -> None:
        dw = self.sidebarDockWidget = QDockWidget(tr(TR.BROWSING_SIDEBAR), self)
        dw.setFeatures(QDockWidget.DockWidgetClosable)
        dw.setObjectName("Sidebar")
        dw.setAllowedAreas(Qt.LeftDockWidgetArea)

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
        grid.setContentsMargins(0, 0, 0, 0)
        grid.setSpacing(0)
        w = QWidget()
        w.setLayout(grid)
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
        if not self.card:
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
        cs = CardStats(self.col, self.card)
        rep = cs.report(include_revlog=True)
        return rep, cs

    # legacy - revlog used to be generated here, and some add-ons
    # wrapped this function

    def _revlogData(self, cs: CardStats) -> str:
        return ""

    # Menu helpers
    ######################################################################

    def selected_cards(self) -> List[int]:
        return [
            self.model.cards[idx.row()]
            for idx in self.form.tableView.selectionModel().selectedRows()
        ]

    def selected_notes(self) -> List[int]:
        return self.col.db.list(
            """
select distinct nid from cards
where id in %s"""
            % ids2str(
                [
                    self.model.cards[idx.row()]
                    for idx in self.form.tableView.selectionModel().selectedRows()
                ]
            )
        )

    def selectedNotesAsCards(self) -> List[int]:
        return self.col.db.list(
            "select id from cards where nid in (%s)"
            % ",".join([str(s) for s in self.selected_notes()])
        )

    def oneModelNotes(self) -> List[int]:
        sf = self.selected_notes()
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

    # legacy

    selectedCards = selected_cards
    selectedNotes = selected_notes

    # Misc menu options
    ######################################################################

    @ensure_editor_saved_on_trigger
    def onChangeModel(self) -> None:
        nids = self.oneModelNotes()
        if nids:
            ChangeModel(self, nids)

    def createFilteredDeck(self) -> None:
        search = self.form.searchEdit.lineEdit().text()
        if self.mw.col.schedVer() != 1 and KeyboardModifiersPressed().alt:
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

    def delete_selected_notes(self) -> None:
        # ensure deletion is not accidentally triggered when the user is focused
        # in the editing screen or search bar
        focus = self.focusWidget()
        if focus != self.form.tableView:
            return

        # nothing selected?
        nids = self.selected_notes()
        if not nids:
            return

        # select the next card if there is one
        self._onNextCard()

        remove_notes(
            mw=self.mw,
            note_ids=nids,
            success=lambda _: tooltip(tr(TR.BROWSING_NOTE_DELETED, count=len(nids))),
        )

    # legacy

    deleteNotes = delete_selected_notes

    # Deck change
    ######################################################################

    @ensure_editor_saved_on_trigger
    def set_deck_of_selected_cards(self) -> None:
        from aqt.studydeck import StudyDeck

        cids = self.selected_cards()
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

        set_card_deck(mw=self.mw, card_ids=cids, deck_id=did)

    # legacy

    setDeck = set_deck_of_selected_cards

    # Tags
    ######################################################################

    @ensure_editor_saved_on_trigger
    def add_tags_to_selected_notes(
        self,
        tags: Optional[str] = None,
    ) -> None:
        "Shows prompt if tags not provided."
        if not (
            tags := tags or self._prompt_for_tags(tr(TR.BROWSING_ENTER_TAGS_TO_ADD))
        ):
            return
        add_tags(
            mw=self.mw,
            note_ids=self.selected_notes(),
            space_separated_tags=tags,
            success=lambda out: tooltip(
                tr(TR.BROWSING_NOTES_UPDATED, count=out.count), parent=self
            ),
        )

    @ensure_editor_saved_on_trigger
    def remove_tags_from_selected_notes(self, tags: Optional[str] = None) -> None:
        "Shows prompt if tags not provided."
        if not (
            tags := tags or self._prompt_for_tags(tr(TR.BROWSING_ENTER_TAGS_TO_DELETE))
        ):
            return
        remove_tags_for_notes(
            mw=self.mw,
            note_ids=self.selected_notes(),
            space_separated_tags=tags,
            success=lambda out: tooltip(
                tr(TR.BROWSING_NOTES_UPDATED, count=out.count), parent=self
            ),
        )

    def _prompt_for_tags(self, prompt: str) -> Optional[str]:
        (tags, ok) = getTag(self, self.col, prompt)
        if not ok:
            return None
        else:
            return tags

    @ensure_editor_saved_on_trigger
    def clear_unused_tags(self) -> None:
        clear_unused_tags(mw=self.mw, parent=self)

    addTags = add_tags_to_selected_notes
    deleteTags = remove_tags_from_selected_notes
    clearUnusedTags = clear_unused_tags

    # Suspending
    ######################################################################

    def current_card_is_suspended(self) -> bool:
        return bool(self.card and self.card.queue == QUEUE_TYPE_SUSPENDED)

    @ensure_editor_saved_on_trigger
    def suspend_selected_cards(self) -> None:
        want_suspend = not self.current_card_is_suspended()
        cids = self.selected_cards()

        if want_suspend:
            suspend_cards(mw=self.mw, card_ids=cids)
        else:
            unsuspend_cards(mw=self.mw, card_ids=cids)

    # Exporting
    ######################################################################

    def _on_export_notes(self) -> None:
        cids = self.selectedNotesAsCards()
        if cids:
            ExportDialog(self.mw, cids=cids)

    # Flags & Marking
    ######################################################################

    @ensure_editor_saved
    def set_flag_of_selected_cards(self, flag: int) -> None:
        if not self.card:
            return

        # flag needs toggling off?
        if flag == self.card.user_flag():
            flag = 0

        set_card_flag(mw=self.mw, card_ids=self.selected_cards(), flag=flag)

    def _update_flags_menu(self) -> None:
        flag = self.card and self.card.user_flag()
        flag = flag or 0

        flagActions = [
            self.form.actionRed_Flag,
            self.form.actionOrange_Flag,
            self.form.actionGreen_Flag,
            self.form.actionBlue_Flag,
        ]

        for c, act in enumerate(flagActions):
            act.setChecked(flag == c + 1)

        qtMenuShortcutWorkaround(self.form.menuFlag)

    def toggle_mark_of_selected_notes(self) -> None:
        have_mark = bool(self.card and self.card.note().has_tag(MARKED_TAG))
        if have_mark:
            self.remove_tags_from_selected_notes(tags=MARKED_TAG)
        else:
            self.add_tags_to_selected_notes(tags=MARKED_TAG)

    # Scheduling
    ######################################################################

    @ensure_editor_saved_on_trigger
    def reposition(self) -> None:
        if self.card and self.card.queue != QUEUE_TYPE_NEW:
            showInfo(tr(TR.BROWSING_ONLY_NEW_CARDS_CAN_BE_REPOSITIONED), parent=self)
            return

        reposition_new_cards_dialog(
            mw=self.mw, parent=self, card_ids=self.selected_cards()
        )

    @ensure_editor_saved_on_trigger
    def set_due_date(self) -> None:
        set_due_date_dialog(
            mw=self.mw,
            parent=self,
            card_ids=self.selected_cards(),
            config_key=Config.String.SET_DUE_BROWSER,
        )

    @ensure_editor_saved_on_trigger
    def forget_cards(self) -> None:
        forget_cards(
            mw=self.mw,
            parent=self,
            card_ids=self.selected_cards(),
        )

    # Edit: selection
    ######################################################################

    @ensure_editor_saved_on_trigger
    def selectNotes(self) -> None:
        nids = self.selected_notes()
        # clear the selection so we don't waste energy preserving it
        tv = self.form.tableView
        tv.selectionModel().clear()

        search = self.col.build_search_string(
            SearchNode(nids=SearchNode.IdList(ids=nids))
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
        # fixme: remove these once all items are using `operation_did_execute`
        gui_hooks.sidebar_should_refresh_decks.append(self.on_item_added)
        gui_hooks.sidebar_should_refresh_notetypes.append(self.on_item_added)
        gui_hooks.backend_will_block.append(self.on_backend_will_block)
        gui_hooks.backend_did_block.append(self.on_backend_did_block)
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        gui_hooks.focus_did_change.append(self.on_focus_change)

    def teardownHooks(self) -> None:
        gui_hooks.undo_state_did_change.remove(self.onUndoState)
        gui_hooks.sidebar_should_refresh_decks.remove(self.on_item_added)
        gui_hooks.sidebar_should_refresh_notetypes.remove(self.on_item_added)
        gui_hooks.backend_will_block.remove(self.on_backend_will_block)
        gui_hooks.backend_did_block.remove(self.on_backend_will_block)
        gui_hooks.operation_did_execute.remove(self.on_operation_did_execute)
        gui_hooks.focus_did_change.remove(self.on_focus_change)

    # covers the tag, note and deck case
    def on_item_added(self, item: Any = None) -> None:
        self.sidebar.refresh()

    def on_tag_list_update(self) -> None:
        self.sidebar.refresh()

    # Undo
    ######################################################################

    def undo(self) -> None:
        # need to make sure we don't hang the UI by redrawing the card list
        # during the long-running op. mw.undo will take care of the progress
        # dialog
        self.setUpdatesEnabled(False)
        self.mw.undo(lambda _: self.setUpdatesEnabled(True))

    def onUndoState(self, on: bool) -> None:
        self.form.actionUndo.setEnabled(on)
        if on:
            self.form.actionUndo.setText(self.mw.form.actionUndo.text())

    # Edit: replacing
    ######################################################################

    @ensure_editor_saved_on_trigger
    def onFindReplace(self) -> None:
        nids = self.selected_notes()
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
        frm.find._completer().setCaseSensitivity(True)
        replacehistory = restore_combo_history(frm.replace, combo + "Replace")
        frm.replace._completer().setCaseSensitivity(True)

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
        match_case = not frm.ignoreCase.isChecked()

        save_is_checked(frm.re, combo + "Regex")
        save_is_checked(frm.ignoreCase, combo + "ignoreCase")

        find_and_replace(
            mw=self.mw,
            parent=self,
            note_ids=nids,
            search=search,
            replacement=replace,
            regex=regex,
            field_name=field,
            match_case=match_case,
        )

    def onFindReplaceHelp(self) -> None:
        openHelp(HelpPage.BROWSING_FIND_AND_REPLACE)

    # Edit: finding dupes
    ######################################################################

    @ensure_editor_saved
    def onFindDupes(self) -> None:
        d = QDialog(self)
        self.mw.garbage_collect_on_dialog_finish(d)
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
                            SearchNode(nids=SearchNode.IdList(ids=nids))
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
        self.model.beginReset()
        self.mw.checkpoint(tr(TR.BROWSING_TAG_DUPLICATES))
        nids = set()
        for _, nidlist in res:
            nids.update(nidlist)
        self.col.tags.bulk_add(list(nids), tr(TR.BROWSING_DUPLICATE))
        self.mw.progress.finish()
        self.model.endReset()
        self.mw.requireReset(reason=ResetReason.BrowserTagDupes, context=self)
        tooltip(tr(TR.BROWSING_NOTES_TAGGED))

    def dupeLinkClicked(self, link: str) -> None:
        self.search_for(link)
        self.onNote()

    # Jumping
    ######################################################################

    def _moveCur(self, dir: int, idx: QModelIndex = None) -> None:
        if not self.model.cards:
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

    def onPreviousCard(self) -> None:
        self.focusTo = self.editor.currentField
        self.editor.call_after_note_saved(self._onPreviousCard)

    def _onPreviousCard(self) -> None:
        self._moveCur(QAbstractItemView.MoveUp)

    def onNextCard(self) -> None:
        self.focusTo = self.editor.currentField
        self.editor.call_after_note_saved(self._onNextCard)

    def _onNextCard(self) -> None:
        self._moveCur(QAbstractItemView.MoveDown)

    def onFirstCard(self) -> None:
        sm = self.form.tableView.selectionModel()
        idx = sm.currentIndex()
        self._moveCur(None, self.model.index(0, 0))
        if not KeyboardModifiersPressed().shift:
            return
        idx2 = sm.currentIndex()
        item = QItemSelection(idx2, idx)
        sm.select(item, QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows)

    def onLastCard(self) -> None:
        sm = self.form.tableView.selectionModel()
        idx = sm.currentIndex()
        self._moveCur(None, self.model.index(len(self.model.cards) - 1, 0))
        if not KeyboardModifiersPressed().shift:
            return
        idx2 = sm.currentIndex()
        item = QItemSelection(idx, idx2)
        sm.select(item, QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows)

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

    def focusCid(self, cid: int) -> None:
        try:
            row = list(self.model.cards).index(cid)
        except ValueError:
            return
        self.form.tableView.clearSelection()
        self.form.tableView.selectRow(row)


# Change model dialog
######################################################################


class ChangeModel(QDialog):
    def __init__(self, browser: Browser, nids: List[int]) -> None:
        QDialog.__init__(self, browser)
        self.browser = browser
        self.nids = nids
        self.oldModel = browser.card.note().model()
        self.form = aqt.forms.changemodel.Ui_Dialog()
        self.form.setupUi(self)
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModal)
        self.setup()
        restoreGeom(self, "changeModel")
        gui_hooks.state_did_reset.append(self.onReset)
        gui_hooks.current_note_type_did_change.append(self.on_note_type_change)
        # ugh - these are set dynamically by rebuildTemplateMap()
        self.tcombos: List[QComboBox] = []
        self.fcombos: List[QComboBox] = []
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
        b.model.beginReset()
        mm = b.mw.col.models
        mm.change(self.oldModel, self.nids, self.targetModel, fmap, cmap)
        b.search()
        b.model.endReset()
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
