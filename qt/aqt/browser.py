# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import html
import time
from concurrent.futures import Future
from dataclasses import dataclass
from operator import itemgetter
from typing import List, Optional, Sequence, Tuple, cast

import aqt
import aqt.forms
from anki.cards import Card
from anki.collection import Collection
from anki.consts import *
from anki.lang import without_unicode_isolation
from anki.models import NoteType
from anki.notes import Note
from anki.rsbackend import (
    BackendNoteTypeID,
    ConcatSeparator,
    DupeIn,
    FilterToSearchIn,
    InvalidInput,
    NamedFilter,
)
from anki.stats import CardStats
from anki.utils import htmlToTextLine, ids2str, isMac, isWin
from aqt import AnkiQt, gui_hooks
from aqt.editor import Editor
from aqt.exporting import ExportDialog
from aqt.main import ResetReason
from aqt.previewer import BrowserPreviewer as PreviewDialog
from aqt.previewer import Previewer
from aqt.qt import *
from aqt.sidebar import SidebarTreeView
from aqt.theme import theme_manager
from aqt.utils import (
    TR,
    HelpPage,
    MenuList,
    SubMenu,
    askUser,
    disable_help_button,
    getOnlyText,
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
    showWarning,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView

# legacy add-on support
# pylint: disable=unused-import
from aqt.sidebar import SidebarItem, SidebarStage  # isort: skip


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


class DataModel(QAbstractTableModel):
    def __init__(self, browser: Browser):
        QAbstractTableModel.__init__(self)
        self.browser = browser
        self.col = browser.col
        self.sortKey = None
        self.activeCols = self.col.get_config(
            "activeCols", ["noteFld", "template", "cardDue", "deck"]
        )
        self.cards: Sequence[int] = []
        self.cardObjs: Dict[int, Card] = {}

    def getCard(self, index: QModelIndex) -> Card:
        id = self.cards[index.row()]
        if not id in self.cardObjs:
            self.cardObjs[id] = self.col.getCard(id)
        return self.cardObjs[id]

    def refreshNote(self, note):
        refresh = False
        for c in note.cards():
            if c.id in self.cardObjs:
                del self.cardObjs[c.id]
                refresh = True
        if refresh:
            self.layoutChanged.emit()  # type: ignore

    # Model interface
    ######################################################################

    def rowCount(self, parent):
        if parent and parent.isValid():
            return 0
        return len(self.cards)

    def columnCount(self, parent):
        if parent and parent.isValid():
            return 0
        return len(self.activeCols)

    def data(self, index, role):
        if not index.isValid():
            return
        if role == Qt.FontRole:
            if self.activeCols[index.column()] not in ("question", "answer", "noteFld"):
                return
            c = self.getCard(index)
            t = c.template()
            if not t.get("bfont"):
                return
            f = QFont()
            f.setFamily(cast(str, t.get("bfont", "arial")))
            f.setPixelSize(cast(int, t.get("bsize", 12)))
            return f

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
            return self.columnData(index)
        else:
            return

    def headerData(self, section, orientation, role):
        if orientation == Qt.Vertical:
            return
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
            return

    def flags(self, index):
        return Qt.ItemFlag(Qt.ItemIsEnabled | Qt.ItemIsSelectable)

    # Filtering
    ######################################################################

    def search(self, txt: str) -> None:
        self.beginReset()
        self.cards = []
        exception: Optional[Exception] = None
        try:
            ctx = SearchContext(search=txt, browser=self.browser)
            gui_hooks.browser_will_search(ctx)
            if ctx.card_ids is None:
                ctx.search = self.browser.normalize_search(ctx.search)
                ctx.card_ids = self.col.find_cards(ctx.search, order=ctx.order)
            gui_hooks.browser_did_search(ctx)
            self.cards = ctx.card_ids
        except Exception as e:
            exception = e
        finally:
            self.endReset()

        if exception:
            show_invalid_search_error(exception)

    def reset(self):
        self.beginReset()
        self.endReset()

    # caller must have called editor.saveNow() before calling this or .reset()
    def beginReset(self):
        self.browser.editor.setNote(None, hide=False)
        self.browser.mw.progress.start()
        self.saveSelection()
        self.beginResetModel()
        self.cardObjs = {}

    def endReset(self):
        self.endResetModel()
        self.restoreSelection()
        self.browser.mw.progress.finish()

    def reverse(self):
        self.browser.editor.saveNow(self._reverse)

    def _reverse(self):
        self.beginReset()
        self.cards = list(reversed(self.cards))
        self.endReset()

    def saveSelection(self):
        cards = self.browser.selectedCards()
        self.selectedCards = dict([(id, True) for id in cards])
        if getattr(self.browser, "card", None):
            self.focusedCard = self.browser.card.id
        else:
            self.focusedCard = None

    def restoreSelection(self):
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

    # Column data
    ######################################################################

    def columnType(self, column):
        return self.activeCols[column]

    def time_format(self):
        return "%Y-%m-%d"

    def columnData(self, index):
        col = index.column()
        type = self.columnType(col)
        c = self.getCard(index)
        if type == "question":
            return self.question(c)
        elif type == "answer":
            return self.answer(c)
        elif type == "noteFld":
            f = c.note()
            return htmlToTextLine(f.fields[self.col.models.sortIdx(f.model())])
        elif type == "template":
            t = c.template()["name"]
            if c.model()["type"] == MODEL_CLOZE:
                t = f"{t} {c.ord + 1}"
            return t
        elif type == "cardDue":
            # catch invalid dates
            try:
                t = self.nextDue(c, index)
            except:
                t = ""
            if c.queue < 0:
                t = f"({t})"
            return t
        elif type == "noteCrt":
            return time.strftime(self.time_format(), time.localtime(c.note().id / 1000))
        elif type == "noteMod":
            return time.strftime(self.time_format(), time.localtime(c.note().mod))
        elif type == "cardMod":
            return time.strftime(self.time_format(), time.localtime(c.mod))
        elif type == "cardReps":
            return str(c.reps)
        elif type == "cardLapses":
            return str(c.lapses)
        elif type == "noteTags":
            return " ".join(c.note().tags)
        elif type == "note":
            return c.model()["name"]
        elif type == "cardIvl":
            if c.type == CARD_TYPE_NEW:
                return tr(TR.BROWSING_NEW)
            elif c.type == CARD_TYPE_LRN:
                return tr(TR.BROWSING_LEARNING)
            return self.col.format_timespan(c.ivl * 86400)
        elif type == "cardEase":
            if c.type == CARD_TYPE_NEW:
                return tr(TR.BROWSING_NEW)
            return "%d%%" % (c.factor / 10)
        elif type == "deck":
            if c.odid:
                # in a cram deck
                return "%s (%s)" % (
                    self.browser.mw.col.decks.name(c.did),
                    self.browser.mw.col.decks.name(c.odid),
                )
            # normal deck
            return self.browser.mw.col.decks.name(c.did)

    def question(self, c):
        return htmlToTextLine(c.q(browser=True))

    def answer(self, c):
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

    def nextDue(self, c, index):
        if c.odid:
            return tr(TR.BROWSING_FILTERED)
        elif c.queue == QUEUE_TYPE_LRN:
            date = c.due
        elif c.queue == QUEUE_TYPE_NEW or c.type == CARD_TYPE_NEW:
            return tr(TR.STATISTICS_DUE_FOR_NEW_CARD, number=c.due)
        elif c.queue in (QUEUE_TYPE_REV, QUEUE_TYPE_DAY_LEARN_RELEARN) or (
            c.type == CARD_TYPE_REV and c.queue < 0
        ):
            date = time.time() + ((c.due - self.col.sched.today) * 86400)
        else:
            return ""
        return time.strftime(self.time_format(), time.localtime(date))

    def isRTL(self, index):
        col = index.column()
        type = self.columnType(col)
        if type != "noteFld":
            return False

        c = self.getCard(index)
        nt = c.note().model()
        return nt["flds"][self.col.models.sortIdx(nt)]["rtl"]


# Line painter
######################################################################


class StatusDelegate(QItemDelegate):
    def __init__(self, browser, model):
        QItemDelegate.__init__(self, browser)
        self.browser = browser
        self.model = model

    def paint(self, painter, option, index):
        try:
            c = self.model.getCard(index)
        except:
            # in the the middle of a reset; return nothing so this row is not
            # rendered until we have a chance to reset the model
            return

        if self.model.isRTL(index):
            option.direction = Qt.RightToLeft

        col = None
        if c.userFlag() > 0:
            col = theme_manager.qcolor(f"flag{c.userFlag()}-bg")
        elif c.note().hasTag("Marked"):
            col = theme_manager.qcolor("marked-bg")
        elif c.queue == QUEUE_TYPE_SUSPENDED:
            col = theme_manager.qcolor("suspended-bg")
        if col:
            brush = QBrush(col)
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()

        return QItemDelegate.paint(self, painter, option, index)


# Browser window
######################################################################

# fixme: respond to reset+edit hooks


class Browser(QMainWindow):
    model: DataModel
    mw: AnkiQt
    col: Collection
    editor: Optional[Editor]

    def __init__(self, mw: AnkiQt) -> None:
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
        self.setupSearch()
        gui_hooks.browser_will_show(self)
        self.show()

    def setupMenus(self) -> None:
        # pylint: disable=unnecessary-lambda
        # actions
        f = self.form
        qconnect(f.filter.clicked, self.onFilterButton)
        # edit
        qconnect(f.actionUndo.triggered, self.mw.onUndo)
        qconnect(f.actionInvertSelection.triggered, self.invertSelection)
        qconnect(f.actionSelectNotes.triggered, self.selectNotes)
        if not isMac:
            f.actionClose.setVisible(False)
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

        # context menu
        self.form.tableView.setContextMenuPolicy(Qt.CustomContextMenu)
        qconnect(self.form.tableView.customContextMenuRequested, self.onContextMenu)

    def onContextMenu(self, _point) -> None:
        m = QMenu()
        for act in self.form.menu_Cards.actions():
            m.addAction(act)
        m.addSeparator()
        for act in self.form.menu_Notes.actions():
            m.addAction(act)
        gui_hooks.browser_will_show_context_menu(self, m)
        qtMenuShortcutWorkaround(m)
        m.exec_(QCursor.pos())

    def updateFont(self):
        # we can't choose different line heights efficiently, so we need
        # to pick a line height big enough for any card template
        curmax = 16
        for m in self.col.models.all():
            for t in m["tmpls"]:
                bsize = t.get("bsize", 0)
                if bsize > curmax:
                    curmax = bsize
        self.form.tableView.verticalHeader().setDefaultSectionSize(curmax + 6)

    def closeEvent(self, evt):
        if self._closeEventHasCleanedUp:
            evt.accept()
            return
        self.editor.saveNow(self._closeWindow)
        evt.ignore()

    def _closeWindow(self):
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

    def closeWithCallback(self, onsuccess):
        def callback():
            self._closeWindow()
            onsuccess()

        self.editor.saveNow(callback)

    def keyPressEvent(self, evt):
        if evt.key() == Qt.Key_Escape:
            self.close()
        else:
            super().keyPressEvent(evt)

    def setupColumns(self):
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

    # Searching
    ######################################################################

    def setupSearch(self):
        qconnect(self.form.searchButton.clicked, self.onSearchActivated)
        qconnect(self.form.searchEdit.lineEdit().returnPressed, self.onSearchActivated)
        self.form.searchEdit.setCompleter(None)
        self._searchPrompt = tr(TR.BROWSING_TYPE_HERE_TO_SEARCH)
        self.form.searchEdit.addItems(
            [self._searchPrompt] + self.mw.pm.profile["searchHistory"]
        )
        self.search_for("is:current", self._searchPrompt)
        # then replace text for easily showing the deck
        self.form.searchEdit.lineEdit().selectAll()
        self.form.searchEdit.setFocus()

    # search triggered by user
    def onSearchActivated(self):
        self.editor.saveNow(self._onSearchActivated)

    def _onSearchActivated(self):
        # grab search text and normalize
        prompt = self.form.searchEdit.lineEdit().text()

        # convert guide text before we save history
        txt = "deck:current " if prompt == self._searchPrompt else prompt
        self.update_history(txt)

        # keep track of search string so that we reuse identical search when
        # refreshing, rather than whatever is currently in the search field
        self.search_for(txt)

    def update_history(self, search: str) -> None:
        sh = self.mw.pm.profile["searchHistory"]
        if search in sh:
            sh.remove(search)
        sh.insert(0, search)
        sh = sh[:30]
        self.form.searchEdit.clear()
        self.form.searchEdit.addItems(sh)
        self.mw.pm.profile["searchHistory"] = sh

    def search_for(self, search: str, prompt: Optional[str] = None) -> None:
        self._lastSearchTxt = search
        self.form.searchEdit.lineEdit().setText(prompt or search)
        self.search()

    # search triggered programmatically. caller must have saved note first.
    def search(self) -> None:
        if "is:current" in self._lastSearchTxt:
            # show current card if there is one
            c = self.card = self.mw.reviewer.card
            nid = c and c.nid or 0
            if nid:
                search = "nid:%d" % nid
                search = gui_hooks.default_search(search, c)
                self.model.search(search)
                self.focusCid(c.id)
        else:
            self.model.search(self._lastSearchTxt)

        if not self.model.cards:
            # no row change will fire
            self._onRowChanged(None, None)

    def normalize_search(self, search: str) -> str:
        normed = self.col.backend.normalize_search(search)
        self._lastSearchTxt = normed
        self.form.searchEdit.lineEdit().setText(normed)
        return normed

    def updateTitle(self):
        selected = len(self.form.tableView.selectionModel().selectedRows())
        cur = len(self.model.cards)
        self.setWindowTitle(
            without_unicode_isolation(
                tr(TR.BROWSING_WINDOW_TITLE, total=cur, selected=selected)
            )
        )
        return selected

    def onReset(self):
        self.sidebar.refresh()
        self.editor.setNote(None)
        self.search()

    # Table view & editor
    ######################################################################

    def setupTable(self):
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
            grid = theme_manager.str_color("frame-bg")
            self.form.tableView.setStyleSheet(
                f"""
QTableView {{ gridline-color: {grid} }}           
            """
            )
        self.singleCard = False

    def setupEditor(self):
        def add_preview_button(leftbuttons, editor):
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

    def onRowChanged(self, current, previous):
        "Update current note and hide/show editor."
        self.editor.saveNow(lambda: self._onRowChanged(current, previous))

    def _onRowChanged(self, current, previous) -> None:
        if self._closeEventHasCleanedUp:
            return
        update = self.updateTitle()
        show = self.model.cards and update == 1
        self.form.splitter.widget(1).setVisible(bool(show))
        idx = self.form.tableView.selectionModel().currentIndex()
        if idx.isValid():
            self.card = self.model.getCard(idx)

        if not show:
            self.editor.setNote(None)
            self.singleCard = False
            self._renderPreview()
        else:
            self.editor.setNote(self.card.note(reload=True), focusTo=self.focusTo)
            self.focusTo = None
            self.editor.card = self.card
            self.singleCard = True
        self._updateFlagsMenu()
        gui_hooks.browser_did_change_row(self)

    def refreshCurrentCard(self, note: Note) -> None:
        self.model.refreshNote(note)
        self._renderPreview()

    def onLoadNote(self, editor):
        self.refreshCurrentCard(editor.note)

    def refreshCurrentCardFilter(self, flag, note, fidx):
        self.refreshCurrentCard(note)
        return flag

    def currentRow(self):
        idx = self.form.tableView.selectionModel().currentIndex()
        return idx.row()

    # Headers & sorting
    ######################################################################

    def setupHeaders(self):
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

    def onSortChanged(self, idx, ord):
        ord = bool(ord)
        self.editor.saveNow(lambda: self._onSortChanged(idx, ord))

    def _onSortChanged(self, idx, ord):
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
            self.col.conf["sortBackwards"] = ord
            self.col.setMod()
            self.col.save()
            self.search()
        else:
            if self.col.conf["sortBackwards"] != ord:
                self.col.conf["sortBackwards"] = ord
                self.col.setMod()
                self.col.save()
                self.model.reverse()
        self.setSortIndicator()

    def setSortIndicator(self):
        hh = self.form.tableView.horizontalHeader()
        type = self.col.conf["sortType"]
        if type not in self.model.activeCols:
            hh.setSortIndicatorShown(False)
            return
        idx = self.model.activeCols.index(type)
        if self.col.conf["sortBackwards"]:
            ord = Qt.DescendingOrder
        else:
            ord = Qt.AscendingOrder
        hh.blockSignals(True)
        hh.setSortIndicator(idx, ord)
        hh.blockSignals(False)
        hh.setSortIndicatorShown(True)

    def onHeaderContext(self, pos):
        gpos = self.form.tableView.mapToGlobal(pos)
        m = QMenu()
        for type, name in self.columns:
            a = m.addAction(name)
            a.setCheckable(True)
            a.setChecked(type in self.model.activeCols)
            qconnect(a.toggled, lambda b, t=type: self.toggleField(t))
        gui_hooks.browser_header_will_show_context_menu(self, m)
        m.exec_(gpos)

    def toggleField(self, type):
        self.editor.saveNow(lambda: self._toggleField(type))

    def _toggleField(self, type):
        self.model.beginReset()
        if type in self.model.activeCols:
            if len(self.model.activeCols) < 2:
                self.model.endReset()
                return showInfo(tr(TR.BROWSING_YOU_MUST_HAVE_AT_LEAST_ONE))
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

    def setColumnSizes(self):
        hh = self.form.tableView.horizontalHeader()
        hh.setSectionResizeMode(QHeaderView.Interactive)
        hh.setSectionResizeMode(
            hh.logicalIndex(len(self.model.activeCols) - 1), QHeaderView.Stretch
        )
        # this must be set post-resize or it doesn't work
        hh.setCascadingSectionResizes(False)

    def onColumnMoved(self, a, b, c):
        self.setColumnSizes()

    def setupSidebar(self) -> None:
        dw = self.sidebarDockWidget = QDockWidget(tr(TR.BROWSING_SIDEBAR), self)
        dw.setFeatures(QDockWidget.DockWidgetClosable)
        dw.setObjectName("Sidebar")
        dw.setAllowedAreas(Qt.LeftDockWidgetArea)

        self.sidebar = SidebarTreeView(self)
        self.sidebarTree = self.sidebar  # legacy alias
        dw.setWidget(self.sidebar)
        self.sidebarDockWidget.setFloating(False)

        self.sidebarDockWidget.setTitleBarWidget(QWidget())
        self.addDockWidget(Qt.LeftDockWidgetArea, dw)

        # schedule sidebar to refresh after browser window has loaded, so the
        # UI is more responsive
        self.mw.progress.timer(10, self.sidebar.refresh, False)

    def focusSidebar(self) -> None:
        # workaround for PyQt focus bug
        self.editor.hideCompleters()
        self.sidebarDockWidget.setVisible(True)
        self.sidebar.setFocus()

    # legacy
    def maybeRefreshSidebar(self) -> None:
        self.sidebar.refresh()

    def toggle_sidebar(self):
        want_visible = not self.sidebarDockWidget.isVisible()
        self.sidebarDockWidget.setVisible(want_visible)
        if want_visible:
            self.sidebar.refresh()

    # Filter button and sidebar helpers
    ######################################################################

    def onFilterButton(self):
        ml = MenuList()

        ml.addChild(self._todayFilters())
        ml.addChild(self._cardStateFilters())
        ml.addSeparator()

        toggle_sidebar = QAction(tr(TR.BROWSING_SIDEBAR))
        qconnect(toggle_sidebar.triggered, self.toggle_sidebar)
        toggle_sidebar.setCheckable(True)
        toggle_sidebar.setChecked(self.sidebarDockWidget.isVisible())
        ml.addChild(toggle_sidebar)
        ml.addSeparator()

        ml.addChild(self._savedSearches())

        ml.popupOver(self.form.filter)

    def update_search(self, *terms: str):
        "Modify the current search string based on modified keys, then refresh."
        try:
            search = self.col.backend.concatenate_searches(
                sep=ConcatSeparator.AND, searches=terms
            )
            mods = self.mw.app.keyboardModifiers()
            if mods & Qt.AltModifier:
                search = self.col.backend.negate_search(search)
            cur = str(self.form.searchEdit.lineEdit().text())
            if cur != self._searchPrompt:
                if mods & Qt.ControlModifier and mods & Qt.ShiftModifier:
                    search = self.col.backend.replace_search_term(
                        search=cur, replacement=search
                    )
                elif mods & Qt.ControlModifier:
                    search = self.col.backend.concatenate_searches(
                        # pylint: disable=no-member
                        sep=ConcatSeparator.AND,
                        searches=[cur, search],
                    )
                elif mods & Qt.ShiftModifier:
                    search = self.col.backend.concatenate_searches(
                        # pylint: disable=no-member
                        sep=ConcatSeparator.OR,
                        searches=[cur, search],
                    )
        except InvalidInput as e:
            show_invalid_search_error(e)
        else:
            self.form.searchEdit.lineEdit().setText(search)
            self.onSearchActivated()

    # legacy
    def setFilter(self, *terms: str):
        self.set_filter_then_search(*terms)

    def _simpleFilters(self, items):
        ml = MenuList()
        for row in items:
            if row is None:
                ml.addSeparator()
            else:
                label, filter_name = row
                ml.addItem(label, self.sidebar._named_filter(filter_name))
        return ml

    def _todayFilters(self):
        subm = SubMenu(tr(TR.BROWSING_TODAY))
        subm.addChild(
            self._simpleFilters(
                (
                    (tr(TR.BROWSING_ADDED_TODAY), NamedFilter.ADDED_TODAY),
                    (tr(TR.BROWSING_STUDIED_TODAY), NamedFilter.STUDIED_TODAY),
                    (tr(TR.BROWSING_AGAIN_TODAY), NamedFilter.AGAIN_TODAY),
                )
            )
        )
        return subm

    def _cardStateFilters(self):
        subm = SubMenu(tr(TR.BROWSING_CARD_STATE))
        subm.addChild(
            self._simpleFilters(
                (
                    (tr(TR.ACTIONS_NEW), NamedFilter.NEW),
                    (tr(TR.SCHEDULING_LEARNING), NamedFilter.LEARN),
                    (tr(TR.SCHEDULING_REVIEW), NamedFilter.REVIEW),
                    (tr(TR.FILTERING_IS_DUE), NamedFilter.DUE),
                    None,
                    (tr(TR.BROWSING_SUSPENDED), NamedFilter.SUSPENDED),
                    (tr(TR.BROWSING_BURIED), NamedFilter.BURIED),
                    None,
                    (tr(TR.ACTIONS_RED_FLAG), NamedFilter.RED_FLAG),
                    (tr(TR.ACTIONS_ORANGE_FLAG), NamedFilter.ORANGE_FLAG),
                    (tr(TR.ACTIONS_GREEN_FLAG), NamedFilter.GREEN_FLAG),
                    (tr(TR.ACTIONS_BLUE_FLAG), NamedFilter.BLUE_FLAG),
                    (tr(TR.BROWSING_NO_FLAG), NamedFilter.NO_FLAG),
                    (tr(TR.BROWSING_ANY_FLAG), NamedFilter.ANY_FLAG),
                )
            )
        )
        return subm

    # Favourites
    ######################################################################

    def _savedSearches(self):
        ml = MenuList()
        # make sure exists
        if "savedFilters" not in self.col.conf:
            self.col.set_config("savedFilters", {})

        ml.addSeparator()

        if self._currentFilterIsSaved():
            ml.addItem(tr(TR.BROWSING_REMOVE_CURRENT_FILTER), self._onRemoveFilter)
        else:
            ml.addItem(tr(TR.BROWSING_SAVE_CURRENT_FILTER), self._onSaveFilter)

        return ml

    def _onSaveFilter(self) -> None:
        try:
            filt = self.col.backend.normalize_search(
                self.form.searchEdit.lineEdit().text()
            )
        except InvalidInput as e:
            show_invalid_search_error(e)
        else:
            name = getOnlyText(tr(TR.BROWSING_PLEASE_GIVE_YOUR_FILTER_A_NAME))
            if not name:
                return
            conf = self.col.get_config("savedFilters")
            conf[name] = filt
            self.col.set_config("savedFilters", conf)
            self.sidebar.refresh()

    def _onRemoveFilter(self) -> None:
        self.removeFilter(self._currentFilterIsSaved())

    def removeFilter(self, name: str) -> None:
        if not askUser(tr(TR.BROWSING_REMOVE_FROM_YOUR_SAVED_SEARCHES, val=name)):
            return
        conf = self.col.get_config("savedFilters")
        del conf[name]
        self.col.set_config("savedFilters", conf)
        self.sidebar.refresh()

    def renameFilter(self, old: str) -> None:
        conf = self.col.get_config("savedFilters")
        try:
            filt = conf[old]
        except KeyError:
            return
        new = getOnlyText(tr(TR.ACTIONS_NEW_NAME), default=old)
        if new == old or not new:
            return
        conf[new] = filt
        del conf[old]
        self.col.set_config("savedFilters", conf)
        self.sidebar.refresh()

    # returns name if found
    def _currentFilterIsSaved(self) -> Optional[str]:
        filt = self.form.searchEdit.lineEdit().text()
        try:
            filt = self.col.backend.normalize_search(filt)
        except InvalidInput:
            pass
        for k, v in self.col.get_config("savedFilters").items():
            try:
                v = self.col.backend.normalize_search(v)
            except InvalidInput:
                pass
            if filt == v:
                return k
        return None

    # Info
    ######################################################################

    def showCardInfo(self):
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

    def selectedCards(self):
        return [
            self.model.cards[idx.row()]
            for idx in self.form.tableView.selectionModel().selectedRows()
        ]

    def selectedNotes(self):
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

    def selectedNotesAsCards(self):
        return self.col.db.list(
            "select id from cards where nid in (%s)"
            % ",".join([str(s) for s in self.selectedNotes()])
        )

    def oneModelNotes(self):
        sf = self.selectedNotes()
        if not sf:
            return
        mods = self.col.db.scalar(
            """
select count(distinct mid) from notes
where id in %s"""
            % ids2str(sf)
        )
        if mods > 1:
            showInfo(tr(TR.BROWSING_PLEASE_SELECT_CARDS_FROM_ONLY_ONE))
            return
        return sf

    def onHelp(self):
        openHelp(HelpPage.BROWSING)

    # Misc menu options
    ######################################################################

    def onChangeModel(self):
        self.editor.saveNow(self._onChangeModel)

    def _onChangeModel(self):
        nids = self.oneModelNotes()
        if nids:
            ChangeModel(self, nids)

    # Preview
    ######################################################################

    def onTogglePreview(self):
        if self._previewer:
            self._previewer.close()
            self._on_preview_closed()
        else:
            self._previewer = PreviewDialog(self, self.mw, self._on_preview_closed)
            self._previewer.open()

    def _renderPreview(self):
        if self._previewer:
            if self.singleCard:
                self._previewer.render_card()
            else:
                self.onTogglePreview()

    def _cleanup_preview(self):
        if self._previewer:
            self._previewer.cancel_timer()
            self._previewer.close()

    def _on_preview_closed(self):
        if self.editor.web:
            self.editor.web.eval("$('#previewButton').removeClass('highlighted')")
        self._previewer = None

    # Card deletion
    ######################################################################

    def deleteNotes(self):
        focus = self.focusWidget()
        if focus != self.form.tableView:
            return
        self._deleteNotes()

    def _deleteNotes(self):
        nids = self.selectedNotes()
        if not nids:
            return
        self.mw.checkpoint(tr(TR.BROWSING_DELETE_NOTES))
        self.model.beginReset()
        # figure out where to place the cursor after the deletion
        curRow = self.form.tableView.selectionModel().currentIndex().row()
        selectedRows = [
            i.row() for i in self.form.tableView.selectionModel().selectedRows()
        ]
        if min(selectedRows) < curRow < max(selectedRows):
            # last selection in middle; place one below last selected item
            move = sum(1 for i in selectedRows if i > curRow)
            newRow = curRow - move
        elif max(selectedRows) <= curRow:
            # last selection at bottom; place one below bottommost selection
            newRow = max(selectedRows) - len(nids) + 1
        else:
            # last selection at top; place one above topmost selection
            newRow = min(selectedRows) - 1
        self.col.remove_notes(nids)
        self.search()
        if len(self.model.cards):
            newRow = min(newRow, len(self.model.cards) - 1)
            newRow = max(newRow, 0)
            self.model.focusedCard = self.model.cards[newRow]
        self.model.endReset()
        self.mw.reset()
        tooltip(tr(TR.BROWSING_NOTE_DELETED, count=len(nids)))

    # Deck change
    ######################################################################

    def setDeck(self):
        self.editor.saveNow(self._setDeck)

    def _setDeck(self):
        from aqt.studydeck import StudyDeck

        cids = self.selectedCards()
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
        self.model.beginReset()
        self.mw.checkpoint(tr(TR.BROWSING_CHANGE_DECK))
        self.col.set_deck(cids, did)
        self.model.endReset()
        self.mw.requireReset(reason=ResetReason.BrowserSetDeck, context=self)

    # Tags
    ######################################################################

    def addTags(self, tags=None, label=None, prompt=None, func=None):
        self.editor.saveNow(lambda: self._addTags(tags, label, prompt, func))

    def _addTags(self, tags, label, prompt, func):
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
        self.model.beginReset()
        func(self.selectedNotes(), tags)
        self.model.endReset()
        self.mw.requireReset(reason=ResetReason.BrowserAddTags, context=self)

    def deleteTags(self, tags=None, label=None):
        if label is None:
            label = tr(TR.BROWSING_DELETE_TAGS)
        self.addTags(
            tags,
            label,
            tr(TR.BROWSING_ENTER_TAGS_TO_DELETE),
            func=self.col.tags.bulkRem,
        )

    def clearUnusedTags(self):
        self.editor.saveNow(self._clearUnusedTags)

    def _clearUnusedTags(self):
        def on_done(fut: Future):
            fut.result()
            self.on_tag_list_update()

        self.mw.taskman.run_in_background(self.col.tags.registerNotes, on_done)

    # Suspending
    ######################################################################

    def isSuspended(self):
        return bool(self.card and self.card.queue == QUEUE_TYPE_SUSPENDED)

    def onSuspend(self):
        self.editor.saveNow(self._onSuspend)

    def _onSuspend(self):
        sus = not self.isSuspended()
        c = self.selectedCards()
        if sus:
            self.col.sched.suspend_cards(c)
        else:
            self.col.sched.unsuspend_cards(c)
        self.model.reset()
        self.mw.requireReset(reason=ResetReason.BrowserSuspend, context=self)

    # Exporting
    ######################################################################

    def _on_export_notes(self):
        cids = self.selectedNotesAsCards()
        if cids:
            ExportDialog(self.mw, cids=cids)

    # Flags & Marking
    ######################################################################

    def onSetFlag(self, n):
        if not self.card:
            return
        self.editor.saveNow(lambda: self._on_set_flag(n))

    def _on_set_flag(self, n: int):
        # flag needs toggling off?
        if n == self.card.userFlag():
            n = 0
        self.col.setUserFlag(n, self.selectedCards())
        self.model.reset()

    def _updateFlagsMenu(self):
        flag = self.card and self.card.userFlag()
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

    def onMark(self, mark=None):
        if mark is None:
            mark = not self.isMarked()
        if mark:
            self.addTags(tags="marked", label=False)
        else:
            self.deleteTags(tags="marked", label=False)

    def isMarked(self):
        return bool(self.card and self.card.note().hasTag("Marked"))

    # Repositioning
    ######################################################################

    def reposition(self):
        self.editor.saveNow(self._reposition)

    def _reposition(self):
        cids = self.selectedCards()
        cids2 = self.col.db.list(
            f"select id from cards where type = {CARD_TYPE_NEW} and id in "
            + ids2str(cids)
        )
        if not cids2:
            return showInfo(tr(TR.BROWSING_ONLY_NEW_CARDS_CAN_BE_REPOSITIONED))
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
        self.model.beginReset()
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
        self.model.endReset()

    # Rescheduling
    ######################################################################

    def reschedule(self):
        self.editor.saveNow(self._reschedule)

    def _reschedule(self):
        d = QDialog(self)
        disable_help_button(d)
        d.setWindowModality(Qt.WindowModal)
        frm = aqt.forms.reschedule.Ui_Dialog()
        frm.setupUi(d)
        if not d.exec_():
            return
        self.model.beginReset()
        self.mw.checkpoint(tr(TR.BROWSING_RESCHEDULE))
        if frm.asNew.isChecked():
            self.col.sched.forgetCards(self.selectedCards())
        else:
            fmin = frm.min.value()
            fmax = frm.max.value()
            fmax = max(fmin, fmax)
            self.col.sched.reschedCards(self.selectedCards(), fmin, fmax)
        self.search()
        self.mw.requireReset(reason=ResetReason.BrowserReschedule, context=self)
        self.model.endReset()

    # Edit: selection
    ######################################################################

    def selectNotes(self):
        self.editor.saveNow(self._selectNotes)

    def _selectNotes(self):
        nids = self.selectedNotes()
        # clear the selection so we don't waste energy preserving it
        tv = self.form.tableView
        tv.selectionModel().clear()

        search = "nid:" + ",".join([str(x) for x in nids])
        self.search_for(search)

        tv.selectAll()

    def invertSelection(self):
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

    def on_tag_list_update(self):
        self.sidebar.refresh()

    def onUndoState(self, on):
        self.form.actionUndo.setEnabled(on)
        if on:
            self.form.actionUndo.setText(self.mw.form.actionUndo.text())

    # Edit: replacing
    ######################################################################

    def onFindReplace(self):
        self.editor.saveNow(self._onFindReplace)

    def _onFindReplace(self) -> None:
        nids = self.selectedNotes()
        if not nids:
            return
        import anki.find

        def find():
            return anki.find.fieldNamesForNotes(self.mw.col, nids)

        def on_done(fut):
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
        self.model.beginReset()

        def do_search():
            return self.col.find_and_replace(
                nids, search, replace, regex, field, nocase
            )

        def on_done(fut):
            self.search()
            self.mw.requireReset(reason=ResetReason.BrowserFindReplace, context=self)
            self.model.endReset()

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

    def onFindReplaceHelp(self):
        openHelp(HelpPage.BROWSING_FIND_AND_REPLACE)

    # Edit: finding dupes
    ######################################################################

    # filter called by the editor
    def search_dupe(self, mid: int, text: str):
        self.form.searchEdit.lineEdit().setText(
            self.col.backend.filter_to_search(
                FilterToSearchIn(
                    dupe=DupeIn(mid=BackendNoteTypeID(ntid=mid), text=text)
                )
            )
        )
        self.onSearchActivated()

    def onFindDupes(self):
        self.editor.saveNow(self._onFindDupes)

    def _onFindDupes(self):
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

        def onFin(code):
            saveGeom(d, "findDupes")

        qconnect(d.finished, onFin)

        def onClick():
            search_text = save_combo_history(frm.search, searchHistory, "findDupesFind")
            save_combo_index_for_session(frm.fields, "findDupesFields")
            field = fields[frm.fields.currentIndex()]
            self.duplicatesReport(frm.webView, field, search_text, frm, web_context)

        search = frm.buttonBox.addButton(
            tr(TR.ACTIONS_SEARCH), QDialogButtonBox.ActionRole
        )
        qconnect(search.clicked, onClick)
        d.show()

    def duplicatesReport(self, web, fname, search, frm, web_context):
        self.mw.progress.start()
        res = self.mw.col.findDupes(fname, search)
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
                    "nid:" + ",".join(str(id) for id in nids),
                    tr(TR.BROWSING_NOTE_COUNT, count=len(nids)),
                    html.escape(val),
                )
            )
        t += "</ol>"
        web.stdHtml(t, context=web_context)
        self.mw.progress.finish()

    def _onTagDupes(self, res):
        if not res:
            return
        self.model.beginReset()
        self.mw.checkpoint(tr(TR.BROWSING_TAG_DUPLICATES))
        nids = set()
        for _, nidlist in res:
            nids.update(nidlist)
        self.col.tags.bulkAdd(list(nids), tr(TR.BROWSING_DUPLICATE))
        self.mw.progress.finish()
        self.model.endReset()
        self.mw.requireReset(reason=ResetReason.BrowserTagDupes, context=self)
        tooltip(tr(TR.BROWSING_NOTES_TAGGED))

    def dupeLinkClicked(self, link):
        self.search_for(link)
        self.onNote()

    # Jumping
    ######################################################################

    def _moveCur(self, dir=None, idx=None):
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

    def onPreviousCard(self):
        self.focusTo = self.editor.currentField
        self.editor.saveNow(self._onPreviousCard)

    def _onPreviousCard(self):
        self._moveCur(QAbstractItemView.MoveUp)

    def onNextCard(self):
        self.focusTo = self.editor.currentField
        self.editor.saveNow(self._onNextCard)

    def _onNextCard(self):
        self._moveCur(QAbstractItemView.MoveDown)

    def onFirstCard(self):
        sm = self.form.tableView.selectionModel()
        idx = sm.currentIndex()
        self._moveCur(None, self.model.index(0, 0))
        if not self.mw.app.keyboardModifiers() & Qt.ShiftModifier:
            return
        idx2 = sm.currentIndex()
        item = QItemSelection(idx2, idx)
        sm.select(item, QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows)

    def onLastCard(self):
        sm = self.form.tableView.selectionModel()
        idx = sm.currentIndex()
        self._moveCur(None, self.model.index(len(self.model.cards) - 1, 0))
        if not self.mw.app.keyboardModifiers() & Qt.ShiftModifier:
            return
        idx2 = sm.currentIndex()
        item = QItemSelection(idx, idx2)
        sm.select(item, QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows)

    def onFind(self):
        # workaround for PyQt focus bug
        self.editor.hideCompleters()

        self.form.searchEdit.setFocus()
        self.form.searchEdit.lineEdit().selectAll()

    def onNote(self):
        # workaround for PyQt focus bug
        self.editor.hideCompleters()

        self.editor.web.setFocus()
        self.editor.loadNote(focusTo=0)

    def onCardList(self):
        self.form.tableView.setFocus()

    def focusCid(self, cid):
        try:
            row = self.model.cards.index(cid)
        except:
            return
        self.form.tableView.selectRow(row)


# Change model dialog
######################################################################


class ChangeModel(QDialog):
    def __init__(self, browser, nids) -> None:
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
        self.exec_()

    def on_note_type_change(self, notetype: NoteType) -> None:
        self.onReset()

    def setup(self):
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

    def onReset(self):
        self.modelChanged(self.browser.col.models.current())

    def modelChanged(self, model):
        self.targetModel = model
        self.rebuildTemplateMap()
        self.rebuildFieldMap()

    def rebuildTemplateMap(self, key=None, attr=None):
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

    def rebuildFieldMap(self):
        return self.rebuildTemplateMap(key="f", attr="flds")

    def onComboChanged(self, i, cb, key):
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

    def getTemplateMap(self, old=None, combos=None, new=None):
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

    def getFieldMap(self):
        return self.getTemplateMap(
            old=self.oldModel["flds"], combos=self.fcombos, new=self.targetModel["flds"]
        )

    def cleanup(self) -> None:
        gui_hooks.state_did_reset.remove(self.onReset)
        gui_hooks.current_note_type_did_change.remove(self.on_note_type_change)
        self.modelChooser.cleanup()
        saveGeom(self, "changeModel")

    def reject(self):
        self.cleanup()
        return QDialog.reject(self)

    def accept(self):
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

    def onHelp(self):
        openHelp(HelpPage.BROWSING_OTHER_MENU_ITEMS)


# Card Info Dialog
######################################################################


class CardInfoDialog(QDialog):
    silentlyClose = True

    def __init__(self, browser: Browser, *args, **kwargs):
        super().__init__(browser, *args, **kwargs)
        self.browser = browser
        disable_help_button(self)

    def reject(self):
        saveGeom(self, "revlog")
        return QDialog.reject(self)
