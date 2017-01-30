# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sre_constants
import cgi
import time
import re
from operator import  itemgetter

import anki.hooks
import aqt
import aqt.modelchooser
from anki.lang import ngettext
from anki.notes import Note

from aqt.qt import *
import anki
import aqt.forms
from anki.utils import fmtTimeSpan, ids2str, stripHTMLMedia, isWin, intTime, isMac
from aqt.utils import saveGeom, restoreGeom, saveSplitter, restoreSplitter, \
    saveHeader, restoreHeader, saveState, restoreState, applyStyles, getTag, \
    showInfo, askUser, tooltip, openHelp, showWarning, shortcut, mungeQA
from anki.hooks import runHook, addHook, remHook
from aqt.webview import AnkiWebView
from aqt.toolbar import Toolbar
from anki.consts import *
from anki.sound import playFromText, clearAudioQueue

COLOUR_SUSPENDED = "#FFFFB2"
COLOUR_MARKED = "#D9B2E9"

# fixme: need to refresh after undo

# Data model
##########################################################################

class DataModel(QAbstractTableModel):

    def __init__(self, browser):
        QAbstractTableModel.__init__(self)
        self.browser = browser
        self.col = browser.col
        self.sortKey = None
        self.activeCols = self.col.conf.get(
            "activeCols", ["noteFld", "template", "cardDue", "deck"])
        self.cards = []
        self.cardObjs = {}

    def getCard(self, index):
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
            self.layoutChanged.emit()

    # Model interface
    ######################################################################

    def rowCount(self, index):
        return len(self.cards)

    def columnCount(self, index):
        return len(self.activeCols)

    def data(self, index, role):
        if not index.isValid():
            return
        if role == Qt.FontRole:
            if self.activeCols[index.column()] not in (
                "question", "answer", "noteFld"):
                return
            f = QFont()
            row = index.row()
            c = self.getCard(index)
            t = c.template()
            f.setFamily(t.get("bfont", "arial"))
            f.setPixelSize(t.get("bsize", 12))
            return f
        elif role == Qt.TextAlignmentRole:
            align = Qt.AlignVCenter
            if self.activeCols[index.column()] not in ("question", "answer",
               "template", "deck", "noteFld", "note"):
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
            # handle case where extension has set an invalid column type
            if not txt:
                txt = self.browser.columns[0][1]
            return txt
        else:
            return

    def flags(self, index):
        return Qt.ItemFlag(Qt.ItemIsEnabled |
                           Qt.ItemIsSelectable)

    # Filtering
    ######################################################################

    def search(self, txt):
        self.beginReset()
        t = time.time()
        # the db progress handler may cause a refresh, so we need to zero out
        # old data first
        self.cards = []
        self.cards = self.col.findCards(txt, order=True)
        #self.browser.mw.pm.profile['fullSearch'])
        #print "fetch cards in %dms" % ((time.time() - t)*1000)
        self.endReset()

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
        t = time.time()
        self.endResetModel()
        self.restoreSelection()
        self.browser.mw.progress.finish()

    def reverse(self):
        self.browser.editor.saveNow(self._reverse)

    def _reverse(self):
        self.beginReset()
        self.cards.reverse()
        self.endReset()

    def saveSelection(self):
        cards = self.browser.selectedCards()
        self.selectedCards = dict([(id, True) for id in cards])
        if getattr(self.browser, 'card', None):
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
            tv.selectRow(idx.row())
            # we save and then restore the horizontal scroll position because
            # scrollTo() also scrolls horizontally which is confusing
            h = tv.horizontalScrollBar().value()
            tv.scrollTo(idx, tv.PositionAtCenter)
            tv.horizontalScrollBar().setValue(h)
            if count < 500:
                # discard large selections; they're too slow
                sm.select(items, QItemSelectionModel.SelectCurrent |
                          QItemSelectionModel.Rows)
        else:
            tv.selectRow(0)

    # Column data
    ######################################################################

    def columnType(self, column):
        return self.activeCols[column]

    def columnData(self, index):
        row = index.row()
        col = index.column()
        type = self.columnType(col)
        c = self.getCard(index)
        if type == "question":
            return self.question(c)
        elif type == "answer":
            return self.answer(c)
        elif type == "noteFld":
            f = c.note()
            return self.formatQA(f.fields[self.col.models.sortIdx(f.model())])
        elif type == "template":
            t = c.template()['name']
            if c.model()['type'] == MODEL_CLOZE:
                t += " %d" % (c.ord+1)
            return t
        elif type == "cardDue":
            # catch invalid dates
            try:
                t = self.nextDue(c, index)
            except:
                t = ""
            if c.queue < 0:
                t = "(" + t + ")"
            return t
        elif type == "noteCrt":
            return time.strftime("%Y-%m-%d", time.localtime(c.note().id/1000))
        elif type == "noteMod":
            return time.strftime("%Y-%m-%d", time.localtime(c.note().mod))
        elif type == "cardMod":
            return time.strftime("%Y-%m-%d", time.localtime(c.mod))
        elif type == "cardReps":
            return str(c.reps)
        elif type == "cardLapses":
            return str(c.lapses)
        elif type == "noteTags":
            return " ".join(c.note().tags)
        elif type == "note":
            return c.model()['name']
        elif type == "cardIvl":
            if c.type == 0:
                return _("(new)")
            elif c.type == 1:
                return _("(learning)")
            return fmtTimeSpan(c.ivl*86400)
        elif type == "cardEase":
            if c.type == 0:
                return _("(new)")
            return "%d%%" % (c.factor/10)
        elif type == "deck":
            if c.odid:
                # in a cram deck
                return "%s (%s)" % (
                    self.browser.mw.col.decks.name(c.did),
                    self.browser.mw.col.decks.name(c.odid))
            # normal deck
            return self.browser.mw.col.decks.name(c.did)

    def question(self, c):
        return self.formatQA(c.q(browser=True))

    def answer(self, c):
        if c.template().get('bafmt'):
            # they have provided a template, use it verbatim
            c.q(browser=True)
            return self.formatQA(c.a())
        # need to strip question from answer
        q = self.question(c)
        a = self.formatQA(c.a())
        if a.startswith(q):
            return a[len(q):].strip()
        return a

    def formatQA(self, txt):
        s = txt.replace("<br>", " ")
        s = s.replace("<br />", " ")
        s = s.replace("<div>", " ")
        s = s.replace("\n", " ")
        s = re.sub("\[sound:[^]]+\]", "", s)
        s = re.sub("\[\[type:[^]]+\]\]", "", s)
        s = stripHTMLMedia(s)
        s = s.strip()
        return s

    def nextDue(self, c, index):
        if c.odid:
            return _("(filtered)")
        elif c.queue == 1:
            date = c.due
        elif c.queue == 0 or c.type == 0:
            return str(c.due)
        elif c.queue in (2,3) or (c.type == 2 and c.queue < 0):
            date = time.time() + ((c.due - self.col.sched.today)*86400)
        else:
            return ""
        return time.strftime("%Y-%m-%d", time.localtime(date))

# Line painter
######################################################################

class StatusDelegate(QItemDelegate):

    def __init__(self, browser, model):
        QItemDelegate.__init__(self, browser)
        self.browser = browser
        self.model = model

    def paint(self, painter, option, index):
        self.browser.mw.progress.blockUpdates = True
        try:
            c = self.model.getCard(index)
        except:
            # in the the middle of a reset; return nothing so this row is not
            # rendered until we have a chance to reset the model
            return
        finally:
            self.browser.mw.progress.blockUpdates = True
        col = None
        if c.note().hasTag("Marked"):
            col = COLOUR_MARKED
        elif c.queue == -1:
            col = COLOUR_SUSPENDED
        if col:
            brush = QBrush(QColor(col))
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()
        return QItemDelegate.paint(self, painter, option, index)

# Browser window
######################################################################

# fixme: respond to reset+edit hooks

class Browser(QMainWindow):

    def __init__(self, mw):
        QMainWindow.__init__(self, None, Qt.Window)
        applyStyles(self)
        self.mw = mw
        self.col = self.mw.col
        self.lastFilter = ""
        self._previewWindow = None
        self._closeEventHasCleanedUp = False
        self.form = aqt.forms.browser.Ui_Dialog()
        self.form.setupUi(self)
        restoreGeom(self, "editor", 0)
        restoreState(self, "editor")
        restoreSplitter(self.form.splitter_2, "editor2")
        restoreSplitter(self.form.splitter, "editor3")
        self.form.splitter_2.setChildrenCollapsible(False)
        self.form.splitter.setChildrenCollapsible(False)
        self.card = None
        self.setupToolbar()
        self.setupColumns()
        self.setupTable()
        self.setupMenus()
        self.setupTree()
        self.setupHeaders()
        self.setupHooks()
        self.setupEditor()
        self.updateFont()
        self.onUndoState(self.mw.form.actionUndo.isEnabled())
        self.setupSearch()
        self.show()

    def setupToolbar(self):
        self.toolbarWeb = AnkiWebView()
        self.toolbarWeb.title = "browser toolbar"
        self.toolbar = BrowserToolbar(self.mw, self.toolbarWeb, self)
        self.form.verticalLayout_3.insertWidget(0, self.toolbarWeb)
        self.toolbar.draw()

    def setupMenus(self):
        # actions
        f = self.form
        if not isMac:
            f.actionClose.setVisible(False)
        f.actionReposition.triggered.connect(self.reposition)
        f.actionReschedule.triggered.connect(self.reschedule)
        f.actionChangeModel.triggered.connect(self.onChangeModel)
        # edit
        f.actionUndo.triggered.connect(self.mw.onUndo)
        f.previewButton.clicked.connect(self.onTogglePreview)
        f.previewButton.setToolTip(_("Preview Selected Card (%s)") %
            shortcut(_("Ctrl+Shift+P")))
        f.actionInvertSelection.triggered.connect(self.invertSelection)
        f.actionSelectNotes.triggered.connect(self.selectNotes)
        f.actionFindReplace.triggered.connect(self.onFindReplace)
        f.actionFindDuplicates.triggered.connect(self.onFindDupes)
        # jumps
        f.actionPreviousCard.triggered.connect(self.onPreviousCard)
        f.actionNextCard.triggered.connect(self.onNextCard)
        f.actionFirstCard.triggered.connect(self.onFirstCard)
        f.actionLastCard.triggered.connect(self.onLastCard)
        f.actionFind.triggered.connect(self.onFind)
        f.actionNote.triggered.connect(self.onNote)
        f.actionTags.triggered.connect(self.onTags)
        f.actionCardList.triggered.connect(self.onCardList)
        # help
        f.actionGuide.triggered.connect(self.onHelp)
        # keyboard shortcut for shift+home/end
        self.pgUpCut = QShortcut(QKeySequence("Shift+Home"), self)
        self.pgUpCut.activated.connect(self.onFirstCard)
        self.pgDownCut = QShortcut(QKeySequence("Shift+End"), self)
        self.pgDownCut.activated.connect(self.onLastCard)
        # add note
        self.addCut = QShortcut(QKeySequence("Ctrl+E"), self)
        self.addCut.activated.connect(self.mw.onAddCard)
        # card info
        self.infoCut = QShortcut(QKeySequence("Ctrl+Shift+I"), self)
        self.infoCut.activated.connect(self.showCardInfo)
        # set deck
        self.changeDeckCut = QShortcut(QKeySequence("Ctrl+D"), self)
        self.changeDeckCut.activated.connect(self.setDeck)
        # add/remove tags
        self.tagCut1 = QShortcut(QKeySequence("Ctrl+Shift+T"), self)
        self.tagCut1.activated.connect(self.addTags)
        self.tagCut2 = QShortcut(QKeySequence("Ctrl+Alt+T"), self)
        self.tagCut2.activated.connect(self.deleteTags)
        self.tagCut3 = QShortcut(QKeySequence("Ctrl+K"), self)
        self.tagCut3.activated.connect(self.onMark)
        # suspending
        self.susCut1 = QShortcut(QKeySequence("Ctrl+J"), self)
        self.susCut1.activated.connect(self.onSuspend)
        # deletion
        self.delCut1 = QShortcut(QKeySequence("Delete"), self)
        self.delCut1.setAutoRepeat(False)
        self.delCut1.activated.connect(self.deleteNotes)
        # add-on hook
        runHook('browser.setupMenus', self)
        self.mw.maybeHideAccelerators(self)

    def updateFont(self):
        # we can't choose different line heights efficiently, so we need
        # to pick a line height big enough for any card template
        curmax = 16
        for m in self.col.models.all():
            for t in m['tmpls']:
                bsize = t.get("bsize", 0)
                if bsize > curmax:
                    curmax = bsize
        self.form.tableView.verticalHeader().setDefaultSectionSize(
            curmax + 6)

    def closeEvent(self, evt):
        if not self._closeEventHasCleanedUp:
            if self.editor.note:
                # ignore event for now to allow us to save
                self.editor.saveNow(self._closeEventAfterSave)
                evt.ignore()
            else:
                self._closeEventCleanup()
                evt.accept()
                self.mw.gcWindow(self)
        else:
            evt.accept()
            self.mw.gcWindow(self)

    def _closeEventAfterSave(self):
        self._closeEventCleanup()
        self.close()

    def _closeEventCleanup(self):
        self.editor.setNote(None)
        saveSplitter(self.form.splitter_2, "editor2")
        saveSplitter(self.form.splitter, "editor3")
        saveGeom(self, "editor")
        saveState(self, "editor")
        saveHeader(self.form.tableView.horizontalHeader(), "editor")
        self.col.conf['activeCols'] = self.model.activeCols
        self.col.setMod()
        self.teardownHooks()
        self.mw.maybeReset()
        aqt.dialogs.close("Browser")
        self._closeEventHasCleanedUp = True

    def canClose(self):
        return True

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if evt.key() == Qt.Key_Escape:
            self.close()
        elif self.mw.app.focusWidget() == self.form.tree:
            if evt.key() in (Qt.Key_Return, Qt.Key_Enter):
                item = self.form.tree.currentItem()
                self.onTreeClick(item, 0)

    def setupColumns(self):
        self.columns = [
            ('question', _("Question")),
            ('answer', _("Answer")),
            ('template', _("Card")),
            ('deck', _("Deck")),
            ('noteFld', _("Sort Field")),
            ('noteCrt', _("Created")),
            ('noteMod', _("Edited")),
            ('cardMod', _("Changed")),
            ('cardDue', _("Due")),
            ('cardIvl', _("Interval")),
            ('cardEase', _("Ease")),
            ('cardReps', _("Reviews")),
            ('cardLapses', _("Lapses")),
            ('noteTags', _("Tags")),
            ('note', _("Note")),
        ]
        self.columns.sort(key=itemgetter(1))

    # Searching
    ######################################################################

    def setupSearch(self):
        self.form.searchEdit.setLineEdit(FavouritesLineEdit(self.mw, self))
        self.form.searchButton.clicked.connect(self.onSearchActivated)
        self.form.searchEdit.lineEdit().returnPressed.connect(self.onSearchActivated)
        self.form.searchEdit.setCompleter(None)
        self.form.searchEdit.addItems(self.mw.pm.profile['searchHistory'])
        self._searchPrompt = _("<type here to search; hit enter to show current deck>")
        self._lastSearchTxt = "is:current"
        self.search()
        # then replace text for easily showing the deck
        self.form.searchEdit.lineEdit().setText(self._searchPrompt)
        self.form.searchEdit.lineEdit().selectAll()
        self.form.searchEdit.setFocus()

    # search triggered by user
    def onSearchActivated(self):
        self.editor.saveNow(self._onSearchActivated)

    def _onSearchActivated(self):
        # convert guide text before we save history
        if self.form.searchEdit.lineEdit().text() == self._searchPrompt:
            self.form.searchEdit.lineEdit().setText("deck:current ")

        # update history
        txt = str(self.form.searchEdit.lineEdit().text()).strip()
        sh = self.mw.pm.profile['searchHistory']
        if txt in sh:
            sh.remove(txt)
        sh.insert(0, txt)
        sh = sh[:30]
        self.form.searchEdit.clear()
        self.form.searchEdit.addItems(sh)
        self.mw.pm.profile['searchHistory'] = sh

        # keep track of search string so that we reuse identical search when
        # refreshing, rather than whatever is currently in the search field
        self._lastSearchTxt = txt
        self.search()

    # search triggered programmatically. caller must have saved note first.
    def search(self):
        if "is:current" in self._lastSearchTxt:
            # show current card if there is one
            c = self.mw.reviewer.card
            nid = c and c.nid or 0
            self.model.search("nid:%d"%nid)
        else:
            self.model.search(self._lastSearchTxt)

        if not self.model.cards:
            # no row change will fire
            self._onRowChanged(None, None)

    def updateTitle(self):
        selected = len(self.form.tableView.selectionModel().selectedRows())
        cur = len(self.model.cards)
        self.setWindowTitle(ngettext("Browser (%(cur)d card shown; %(sel)s)",
                                     "Browser (%(cur)d cards shown; %(sel)s)",
                                 cur) % {
            "cur": cur,
            "sel": ngettext("%d selected", "%d selected", selected) % selected
            })
        return selected

    def onReset(self):
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
        self.form.tableView.selectionModel().selectionChanged.connect(self.onRowChanged)

    def setupEditor(self):
        self.editor = aqt.editor.Editor(
            self.mw, self.form.fieldsArea, self)
        self.editor.stealFocus = False

    def onRowChanged(self, current, previous):
        "Update current note and hide/show editor."
        self.editor.saveNow(lambda: self._onRowChanged(current, previous))

    def _onRowChanged(self, current, previous):
        update = self.updateTitle()
        show = self.model.cards and update == 1
        self.form.splitter.widget(1).setVisible(not not show)
        if not show:
            self.editor.setNote(None)
            self.singleCard = False
        else:
            self.card = self.model.getCard(
                self.form.tableView.selectionModel().currentIndex())
            self.editor.setNote(self.card.note(reload=True))
            self.editor.card = self.card
            self.singleCard = True
        self._renderPreview(True)
        self.toolbar.update()

    def refreshCurrentCard(self, note):
        self.model.refreshNote(note)
        self._renderPreview(False)

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
        hh.customContextMenuRequested.connect(self.onHeaderContext)
        self.setSortIndicator()
        hh.sortIndicatorChanged.connect(self.onSortChanged)
        hh.sectionMoved.connect(self.onColumnMoved)

    def onSortChanged(self, idx, ord):
        self.editor.saveNow(lambda: self._onSortChanged(idx, ord))

    def _onSortChanged(self, idx, ord):
        type = self.model.activeCols[idx]
        noSort = ("question", "answer", "template", "deck", "note", "noteTags")
        if type in noSort:
            if type == "template":
                showInfo(_("""\
This column can't be sorted on, but you can search for individual card types, \
such as 'card:1'."""))
            elif type == "deck":
                showInfo(_("""\
This column can't be sorted on, but you can search for specific decks \
by clicking on one on the left."""))
            else:
                showInfo(_("Sorting on this column is not supported. Please "
                           "choose another."))
            type = self.col.conf['sortType']
        if self.col.conf['sortType'] != type:
            self.col.conf['sortType'] = type
            # default to descending for non-text fields
            if type == "noteFld":
                ord = not ord
            self.col.conf['sortBackwards'] = ord
            self.search()
        else:
            if self.col.conf['sortBackwards'] != ord:
                self.col.conf['sortBackwards'] = ord
                self.model.reverse()
        self.setSortIndicator()

    def setSortIndicator(self):
        hh = self.form.tableView.horizontalHeader()
        type = self.col.conf['sortType']
        if type not in self.model.activeCols:
            hh.setSortIndicatorShown(False)
            return
        idx = self.model.activeCols.index(type)
        if self.col.conf['sortBackwards']:
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
            a.toggled.connect(lambda b, t=type: self.toggleField(t))
        m.exec_(gpos)

    def toggleField(self, type):
        self.editor.saveNow(lambda: self._toggleField(type))

    def _toggleField(self, type):
        self.model.beginReset()
        if type in self.model.activeCols:
            if len(self.model.activeCols) < 2:
                return showInfo(_("You must have at least one column."))
            self.model.activeCols.remove(type)
            adding=False
        else:
            self.model.activeCols.append(type)
            adding=True
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
        hh.setSectionResizeMode(hh.logicalIndex(len(self.model.activeCols)-1),
                         QHeaderView.Stretch)
        # this must be set post-resize or it doesn't work
        hh.setCascadingSectionResizes(False)

    def onColumnMoved(self, a, b, c):
        self.setColumnSizes()

    # Filter tree
    ######################################################################

    class CallbackItem(QTreeWidgetItem):
        def __init__(self, root, name, onclick, oncollapse=None):
            QTreeWidgetItem.__init__(self, root, [name])
            self.onclick = onclick
            self.oncollapse = oncollapse

    def setupTree(self):
        self.form.tree.itemClicked.connect(self.onTreeClick)
        p = QPalette()
        p.setColor(QPalette.Base, QColor("#d6dde0"))
        self.form.tree.setPalette(p)
        self.buildTree()
        self.form.tree.itemExpanded.connect(lambda item: self.onTreeCollapse(item))
        self.form.tree.itemCollapsed.connect(lambda item: self.onTreeCollapse(item))

    def buildTree(self):
        self.form.tree.clear()
        root = self.form.tree
        self._systemTagTree(root)
        self._favTree(root)
        self._decksTree(root)
        self._modelTree(root)
        self._userTagTree(root)
        self.form.tree.setIndentation(15)

    def onTreeClick(self, item, col):
        if getattr(item, 'onclick', None):
            item.onclick()

    def onTreeCollapse(self, item):
        if getattr(item, 'oncollapse', None):
            item.oncollapse()
            
    def setFilter(self, *args):
        if len(args) == 1:
            txt = args[0]
        else:
            txt = ""
            items = []
            for c, a in enumerate(args):
                if c % 2 == 0:
                    txt += a + ":"
                else:
                    txt += a
                    if " " in txt or "(" in txt or ")" in txt:
                        txt = '"%s"' % txt
                    items.append(txt)
                    txt = ""
            txt = " ".join(items)
        if self.mw.app.keyboardModifiers() & Qt.AltModifier:
            txt = "-"+txt
        if self.mw.app.keyboardModifiers() & Qt.ControlModifier:
            cur = str(self.form.searchEdit.lineEdit().text())
            if cur and cur != self._searchPrompt:
                        txt = cur + " " + txt
        elif self.mw.app.keyboardModifiers() & Qt.ShiftModifier:
            cur = str(self.form.searchEdit.lineEdit().text())
            if cur:
                txt = cur + " or " + txt
        self.form.searchEdit.lineEdit().setText(txt)
        self.onSearchActivated()

    def _systemTagTree(self, root):
        tags = (
            (_("Whole Collection"), "ankibw", ""),
            (_("Current Deck"), "deck16", "deck:current"),
            (_("Added Today"), "view-pim-calendar.png", "added:1"),
            (_("Studied Today"), "view-pim-calendar.png", "rated:1"),
            (_("Again Today"), "view-pim-calendar.png", "rated:1:1"),
            (_("New"), "plus16.png", "is:new"),
            (_("Learning"), "stock_new_template_red.png", "is:learn"),
            (_("Review"), "clock16.png", "is:review"),
            (_("Due"), "clock16.png", "is:due"),
            (_("Marked"), "star16.png", "tag:marked"),
            (_("Suspended"), "media-playback-pause.png", "is:suspended"),
            (_("Leech"), "emblem-important.png", "tag:leech"))
        for name, icon, cmd in tags:
            item = self.CallbackItem(
                root, name, lambda c=cmd: self.setFilter(c))
            item.setIcon(0, QIcon(":/icons/" + icon))
        return root

    def _favTree(self, root):
        saved = self.col.conf.get('savedFilters', [])
        if not saved:
            # Don't add favourites to tree if none saved
            return
        root = self.CallbackItem(root, _("My Searches"), None)
        root.setExpanded(True)
        root.setIcon(0, QIcon(":/icons/emblem-favorite-dark.png"))
        for name, filt in sorted(saved.items()):
            item = self.CallbackItem(root, name, lambda s=filt: self.setFilter(s))
            item.setIcon(0, QIcon(":/icons/emblem-favorite-dark.png"))
    
    def _userTagTree(self, root):
        for t in sorted(self.col.tags.all()):
            if t.lower() == "marked" or t.lower() == "leech":
                continue
            item = self.CallbackItem(
                root, t, lambda t=t: self.setFilter("tag", t))
            item.setIcon(0, QIcon(":/icons/anki-tag.png"))

    def _decksTree(self, root):
        grps = self.col.sched.deckDueTree()
        def fillGroups(root, grps, head=""):
            for g in grps:
                item = self.CallbackItem(
                    root, g[0],
                    lambda g=g: self.setFilter("deck", head+g[0]),
                    lambda g=g: self.mw.col.decks.collapseBrowser(g[1]))
                item.setIcon(0, QIcon(":/icons/deck16.png"))
                newhead = head + g[0]+"::"
                collapsed = self.mw.col.decks.get(g[1]).get('browserCollapsed', False)
                item.setExpanded(not collapsed)
                fillGroups(item, g[5], newhead)
        fillGroups(root, grps)

    def _modelTree(self, root):
        for m in sorted(self.col.models.all(), key=itemgetter("name")):
            mitem = self.CallbackItem(
                root, m['name'], lambda m=m: self.setFilter("mid", str(m['id'])))
            mitem.setIcon(0, QIcon(":/icons/product_design.png"))
            # for t in m['tmpls']:
            #     titem = self.CallbackItem(
            #     t['name'], lambda m=m, t=t: self.setFilter(
            #         "model", m['name'], "card", t['name']))
            #     titem.setIcon(0, QIcon(":/icons/stock_new_template.png"))
            #     mitem.addChild(titem)

    # Info
    ######################################################################

    def showCardInfo(self):
        if not self.card:
            return
        info, cs = self._cardInfoData()
        reps = self._revlogData(cs)
        d = QDialog(self)
        l = QVBoxLayout()
        l.setContentsMargins(0,0,0,0)
        w = AnkiWebView()
        l.addWidget(w)
        w.stdHtml(info + "<p>" + reps)
        bb = QDialogButtonBox(QDialogButtonBox.Close)
        l.addWidget(bb)
        bb.rejected.connect(d.reject)
        d.setLayout(l)
        d.setWindowModality(Qt.WindowModal)
        d.resize(500, 400)
        restoreGeom(d, "revlog")
        d.show()
        saveGeom(d, "revlog")

    def _cardInfoData(self):
        from anki.stats import CardStats
        cs = CardStats(self.col, self.card)
        rep = cs.report()
        m = self.card.model()
        rep = """
<div style='width: 400px; margin: 0 auto 0;
border: 1px solid #000; padding: 3px; '>%s</div>""" % rep
        return rep, cs

    def _revlogData(self, cs):
        entries = self.mw.col.db.all(
            "select id/1000.0, ease, ivl, factor, time/1000.0, type "
            "from revlog where cid = ?", self.card.id)
        if not entries:
            return ""
        s = "<table width=100%%><tr><th align=left>%s</th>" % _("Date")
        s += ("<th align=right>%s</th>" * 5) % (
            _("Type"), _("Rating"), _("Interval"), _("Ease"), _("Time"))
        cnt = 0
        for (date, ease, ivl, factor, taken, type) in reversed(entries):
            cnt += 1
            s += "<tr><td>%s</td>" % time.strftime(_("<b>%Y-%m-%d</b> @ %H:%M"),
                                                   time.localtime(date))
            tstr = [_("Learn"), _("Review"), _("Relearn"), _("Filtered"),
                    _("Resched")][type]
            import anki.stats as st
            fmt = "<span style='color:%s'>%s</span>"
            if type == 0:
                tstr = fmt % (st.colLearn, tstr)
            elif type == 1:
                tstr = fmt % (st.colMature, tstr)
            elif type == 2:
                tstr = fmt % (st.colRelearn, tstr)
            elif type == 3:
                tstr = fmt % (st.colCram, tstr)
            else:
                tstr = fmt % ("#000", tstr)
            if ease == 1:
                ease = fmt % (st.colRelearn, ease)
            if ivl == 0:
                ivl = _("0d")
            elif ivl > 0:
                ivl = fmtTimeSpan(ivl*86400, short=True)
            else:
                ivl = cs.time(-ivl)
            s += ("<td align=right>%s</td>" * 5) % (
                tstr,
                ease, ivl,
                "%d%%" % (factor/10) if factor else "",
                cs.time(taken)) + "</tr>"
        s += "</table>"
        if cnt < self.card.reps:
            s += _("""\
Note: Some of the history is missing. For more information, \
please see the browser documentation.""")
        return s

    # Menu helpers
    ######################################################################

    def selectedCards(self):
        return [self.model.cards[idx.row()] for idx in
                self.form.tableView.selectionModel().selectedRows()]

    def selectedNotes(self):
        return self.col.db.list("""
select distinct nid from cards
where id in %s""" % ids2str(
    [self.model.cards[idx.row()] for idx in
    self.form.tableView.selectionModel().selectedRows()]))

    def selectedNotesAsCards(self):
        return self.col.db.list(
            "select id from cards where nid in (%s)" %
            ",".join([str(s) for s in self.selectedNotes()]))

    def oneModelNotes(self):
        sf = self.selectedNotes()
        if not sf:
            return
        mods = self.col.db.scalar("""
select count(distinct mid) from notes
where id in %s""" % ids2str(sf))
        if mods > 1:
            showInfo(_("Please select cards from only one note type."))
            return
        return sf

    def onHelp(self):
        openHelp("browser")

    # Misc menu options
    ######################################################################

    def onChangeModel(self):
        self.editor.saveNow(self._onChangeModel)

    def _onChangeModel(self):
        nids = self.oneModelNotes()
        if nids:
            ChangeModel(self.mw.col, nids, parent=self).exec_()

    # Preview
    ######################################################################

    def onTogglePreview(self):
        if self._previewWindow:
            self._closePreview()
        else:
            self._openPreview()

    def _openPreview(self):
        self._previewState = "question"
        self._previewWindow = QDialog(None, Qt.Window)
        self._previewWindow.setWindowTitle(_("Preview"))

        self._previewWindow.finished.connect(self._onPreviewFinished)
        vbox = QVBoxLayout()
        vbox.setContentsMargins(0,0,0,0)
        self._previewWeb = AnkiWebView()
        vbox.addWidget(self._previewWeb)
        bbox = QDialogButtonBox()

        self._previewReplay = bbox.addButton(_("Replay Audio"), QDialogButtonBox.ActionRole)
        self._previewReplay.setAutoDefault(False)
        self._previewReplay.setShortcut(QKeySequence("R"))
        self._previewReplay.setToolTip(_("Shortcut key: %s" % "R"))

        self._previewPrev = bbox.addButton("<", QDialogButtonBox.ActionRole)
        self._previewPrev.setAutoDefault(False)
        self._previewPrev.setShortcut(QKeySequence("Left"))
        self._previewPrev.setToolTip(_("Shortcut key: Left arrow"))

        self._previewNext = bbox.addButton(">", QDialogButtonBox.ActionRole)
        self._previewNext.setAutoDefault(True)
        self._previewNext.setShortcut(QKeySequence("Right"))
        self._previewNext.setToolTip(_("Shortcut key: Right arrow or Enter"))

        self._previewPrev.clicked.connect(self._onPreviewPrev)
        self._previewNext.clicked.connect(self._onPreviewNext)
        self._previewReplay.clicked.connect(self._onReplayAudio)

        vbox.addWidget(bbox)
        self._previewWindow.setLayout(vbox)
        restoreGeom(self._previewWindow, "preview")
        self._previewWindow.show()
        self._renderPreview(True)

    def _onPreviewFinished(self, ok):
        saveGeom(self._previewWindow, "preview")
        self.mw.progress.timer(100, self._onClosePreview, False)
        self.form.previewButton.setChecked(False)

    def _onPreviewPrev(self):
        if self._previewState == "question":
            self._previewState = "answer"
            self._renderPreview()
        else:
            self.onPreviousCard()
        self._updatePreviewButtons()

    def _onPreviewNext(self):
        if self._previewState == "question":
            self._previewState = "answer"
            self._renderPreview()
        else:
            self.onNextCard()
        self._updatePreviewButtons()

    def _onReplayAudio(self):
        self.mw.reviewer.replayAudio(self)

    def _updatePreviewButtons(self):
        if not self._previewWindow:
            return
        canBack = self.currentRow() >  0 or self._previewState == "question"
        self._previewPrev.setEnabled(not not (self.singleCard and canBack))
        canForward = self.currentRow() < self.model.rowCount(None) - 1 or \
                     self._previewState == "question"
        self._previewNext.setEnabled(not not (self.singleCard and canForward))

    def _closePreview(self):
        if self._previewWindow:
            self._previewWindow.close()
            self._onClosePreview()

    def _onClosePreview(self):
        self._previewWindow = self._previewPrev = self._previewNext = None

    def _renderPreview(self, cardChanged=False):
        if not self._previewWindow:
            return
        c = self.card
        if not c:
            txt = _("(please select 1 card)")
            self._previewWeb.stdHtml(txt)
            self._updatePreviewButtons()
            return
        self._updatePreviewButtons()
        if cardChanged:
            self._previewState = "question"
        # need to force reload even if answer
        txt = c.q(reload=True)
        if self._previewState == "answer":
            txt = c.a()
        txt = re.sub("\[\[type:[^]]+\]\]", "", txt)
        ti = lambda x: x
        base = self.mw.baseHTML()
        self._previewWeb.stdHtml(
            ti(mungeQA(self.col, txt)), self.mw.reviewer._styles(),
            bodyClass="card card%d" % (c.ord+1), head=base,
            js=anki.js.browserSel)
        clearAudioQueue()
        if self.mw.reviewer.autoplay(c):
            playFromText(txt)

    # Card deletion
    ######################################################################

    def deleteNotes(self):
        self.editor.saveNow(self._deleteNotes)

    def _deleteNotes(self):
        nids = self.selectedNotes()
        if not nids:
            return
        self.mw.checkpoint(_("Delete Notes"))
        self.model.beginReset()
        # figure out where to place the cursor after the deletion
        curRow = self.form.tableView.selectionModel().currentIndex().row()
        selectedRows = [i.row() for i in
                self.form.tableView.selectionModel().selectedRows()]
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
        self.col.remNotes(nids)
        self.search()
        if len(self.model.cards):
            newRow = min(newRow, len(self.model.cards) - 1)
            newRow = max(newRow, 0)
            self.model.focusedCard = self.model.cards[newRow]
        self.model.endReset()
        self.mw.requireReset()
        tooltip(ngettext("%d note deleted.", "%d notes deleted.", len(nids)) % len(nids))

    # Deck change
    ######################################################################

    def setDeck(self):
        self.editor.saveNow(self._setDeck)

    def _setDeck(self):
        from aqt.studydeck import StudyDeck
        cids = self.selectedCards()
        if not cids:
            return
        did = self.mw.col.db.scalar(
            "select did from cards where id = ?", cids[0])
        current=self.mw.col.decks.get(did)['name']
        ret = StudyDeck(
            self.mw, current=current, accept=_("Move Cards"),
            title=_("Change Deck"), help="browse", parent=self)
        if not ret.name:
            return
        did = self.col.decks.id(ret.name)
        deck = self.col.decks.get(did)
        if deck['dyn']:
            showWarning(_("Cards can't be manually moved into a filtered deck."))
            return
        self.model.beginReset()
        self.mw.checkpoint(_("Change Deck"))
        mod = intTime()
        usn = self.col.usn()
        # normal cards
        scids = ids2str(cids)
        # remove any cards from filtered deck first
        self.col.sched.remFromDyn(cids)
        # then move into new deck
        self.col.db.execute("""
update cards set usn=?, mod=?, did=? where id in """ + scids,
                            usn, mod, did)
        self.model.endReset()
        self.mw.requireReset()

    # Tags
    ######################################################################

    def addTags(self, tags=None, label=None, prompt=None, func=None):
        self.editor.saveNow(lambda: self._addTags(tags, label, prompt, func))

    def _addTags(self, tags, label, prompt, func):
        if prompt is None:
            prompt = _("Enter tags to add:")
        if tags is None:
            (tags, r) = getTag(self, self.col, prompt)
        else:
            r = True
        if not r:
            return
        if func is None:
            func = self.col.tags.bulkAdd
        if label is None:
            label = _("Add Tags")
        if label:
            self.mw.checkpoint(label)
        self.model.beginReset()
        func(self.selectedNotes(), tags)
        self.model.endReset()
        self.mw.requireReset()

    def deleteTags(self, tags=None, label=None):
        if label is None:
            label = _("Delete Tags")
        self.addTags(tags, label, _("Enter tags to delete:"),
                     func=self.col.tags.bulkRem)

    # Suspending and marking
    ######################################################################

    def isSuspended(self):
        return not not (self.card and self.card.queue == -1)

    def onSuspend(self):
        self.editor.saveNow(self._onSuspend)

    def _onSuspend(self):
        sus = not self.isSuspended()
        c = self.selectedCards()
        if sus:
            self.col.sched.suspendCards(c)
        else:
            self.col.sched.unsuspendCards(c)
        self.model.reset()
        self.mw.requireReset()

    def isMarked(self):
        return not not (self.card and self.card.note().hasTag("Marked"))

    def onMark(self, mark=None):
        if mark is None:
            mark = not self.isMarked()
        if mark:
            self.addTags(tags="marked", label=False)
        else:
            self.deleteTags(tags="marked", label=False)

    # Repositioning
    ######################################################################

    def reposition(self):
        self.editor.saveNow(self._reposition)

    def _reposition(self):
        cids = self.selectedCards()
        cids2 = self.col.db.list(
            "select id from cards where type = 0 and id in " + ids2str(cids))
        if not cids2:
            return showInfo(_("Only new cards can be repositioned."))
        d = QDialog(self)
        d.setWindowModality(Qt.WindowModal)
        frm = aqt.forms.reposition.Ui_Dialog()
        frm.setupUi(d)
        (pmin, pmax) = self.col.db.first(
            "select min(due), max(due) from cards where type=0 and odid=0")
        pmin = pmin or 0
        pmax = pmax or 0
        txt = _("Queue top: %d") % pmin
        txt += "\n" + _("Queue bottom: %d") % pmax
        frm.label.setText(txt)
        if not d.exec_():
            return
        self.model.beginReset()
        self.mw.checkpoint(_("Reposition"))
        self.col.sched.sortCards(
            cids, start=frm.start.value(), step=frm.step.value(),
            shuffle=frm.randomize.isChecked(), shift=frm.shift.isChecked())
        self.search()
        self.mw.requireReset()
        self.model.endReset()

    # Rescheduling
    ######################################################################

    def reschedule(self):
        self.editor.saveNow(self._reschedule)

    def _reschedule(self):
        d = QDialog(self)
        d.setWindowModality(Qt.WindowModal)
        frm = aqt.forms.reschedule.Ui_Dialog()
        frm.setupUi(d)
        if not d.exec_():
            return
        self.model.beginReset()
        self.mw.checkpoint(_("Reschedule"))
        if frm.asNew.isChecked():
            self.col.sched.forgetCards(self.selectedCards())
        else:
            fmin = frm.min.value()
            fmax = frm.max.value()
            fmax = max(fmin, fmax)
            self.col.sched.reschedCards(
                self.selectedCards(), fmin, fmax)
        self.search()
        self.mw.requireReset()
        self.model.endReset()

    # Edit: selection
    ######################################################################

    def selectNotes(self):
        self.editor.saveNow(self._selectNotes)

    def _selectNotes(self):
        nids = self.selectedNotes()
        # bypass search history
        self._lastSearchTxt = "nid:"+",".join([str(x) for x in nids])
        self.form.searchEdit.lineEdit().setText(self._lastSearchTxt)
        # clear the selection so we don't waste energy preserving it
        tv = self.form.tableView
        tv.selectionModel().clear()
        self.search()
        tv.selectAll()

    def invertSelection(self):
        sm = self.form.tableView.selectionModel()
        items = sm.selection()
        self.form.tableView.selectAll()
        sm.select(items, QItemSelectionModel.Deselect | QItemSelectionModel.Rows)

    # Edit: undo
    ######################################################################

    def setupHooks(self):
        addHook("undoState", self.onUndoState)
        addHook("reset", self.onReset)
        addHook("editTimer", self.refreshCurrentCard)
        addHook("editFocusLost", self.refreshCurrentCardFilter)
        for t in "newTag", "newModel", "newDeck":
            addHook(t, self.buildTree)

        addHook("change_note_type_start", self.on_start_change_note_type)
        addHook("change_note_type_end", self.on_end_change_note_type)

    def teardownHooks(self):
        remHook("reset", self.onReset)
        remHook("editTimer", self.refreshCurrentCard)
        remHook("editFocusLost", self.refreshCurrentCardFilter)
        remHook("undoState", self.onUndoState)
        for t in "newTag", "newModel", "newDeck":
            remHook(t, self.buildTree)

        remHook("change_note_type_start", self.on_start_change_note_type)
        remHook("change_note_type_end", self.on_end_change_note_type)

    def onUndoState(self, on):
        self.form.actionUndo.setEnabled(on)
        if on:
            self.form.actionUndo.setText(self.mw.form.actionUndo.text())

    # Edit: replacing
    ######################################################################

    def onFindReplace(self):
        self.editor.saveNow(self._onFindReplace)

    def _onFindReplace(self):
        sf = self.selectedNotes()
        if not sf:
            return
        import anki.find
        fields = sorted(anki.find.fieldNames(self.col, downcase=False))
        d = QDialog(self)
        frm = aqt.forms.findreplace.Ui_Dialog()
        frm.setupUi(d)
        d.setWindowModality(Qt.WindowModal)
        frm.field.addItems([_("All Fields")] + fields)
        frm.buttonBox.helpRequested.connect(self.onFindReplaceHelp)
        restoreGeom(d, "findreplace")
        r = d.exec_()
        saveGeom(d, "findreplace")
        if not r:
            return
        if frm.field.currentIndex() == 0:
            field = None
        else:
            field = fields[frm.field.currentIndex()-1]
        self.mw.checkpoint(_("Find and Replace"))
        self.mw.progress.start()
        self.model.beginReset()
        try:
            changed = self.col.findReplace(sf,
                                            str(frm.find.text()),
                                            str(frm.replace.text()),
                                            frm.re.isChecked(),
                                            field,
                                            frm.ignoreCase.isChecked())
        except sre_constants.error:
            showInfo(_("Invalid regular expression."), parent=self)
            return
        else:
            self.search()
            self.mw.requireReset()
        finally:
            self.model.endReset()
            self.mw.progress.finish()
        showInfo(ngettext(
            "%(a)d of %(b)d note updated",
            "%(a)d of %(b)d notes updated", len(sf)) % {
                'a': changed,
                'b': len(sf),
            })

    def onFindReplaceHelp(self):
        openHelp("findreplace")

    # Edit: finding dupes
    ######################################################################

    def onFindDupes(self):
        self.editor.saveNow(self._onFindDupes)

    def _onFindDupes(self):
        d = QDialog(self)
        self.mw.setupDialogGC(d)
        frm = aqt.forms.finddupes.Ui_Dialog()
        frm.setupUi(d)
        restoreGeom(d, "findDupes")
        fields = sorted(anki.find.fieldNames(self.col, downcase=False))
        frm.fields.addItems(fields)
        self._dupesButton = None
        # links
        frm.webView.onBridgeCmd = self.dupeLinkClicked
        def onFin(code):
            saveGeom(d, "findDupes")
        d.finished.connect(onFin)
        def onClick():
            field = fields[frm.fields.currentIndex()]
            self.duplicatesReport(frm.webView, field, frm.search.text(), frm)
        search = frm.buttonBox.addButton(
            _("Search"), QDialogButtonBox.ActionRole)
        search.clicked.connect(onClick)
        d.show()

    def duplicatesReport(self, web, fname, search, frm):
        self.mw.progress.start()
        res = self.mw.col.findDupes(fname, search)
        if not self._dupesButton:
            self._dupesButton = b = frm.buttonBox.addButton(
                _("Tag Duplicates"), QDialogButtonBox.ActionRole)
            b.clicked.connect(lambda: self._onTagDupes(res))
        t = "<html><body>"
        groups = len(res)
        notes = sum(len(r[1]) for r in res)
        part1 = ngettext("%d group", "%d groups", groups) % groups
        part2 = ngettext("%d note", "%d notes", notes) % notes
        t += _("Found %(a)s across %(b)s.") % dict(a=part1, b=part2)
        t += "<p><ol>"
        for val, nids in res:
            t += '''<li><a href=# onclick="pycmd('%s')">%s</a>: %s</a>''' % (
                "nid:" + ",".join(str(id) for id in nids),
                ngettext("%d note", "%d notes", len(nids)) % len(nids),
                cgi.escape(val))
        t += "</ol>"
        t += "</body></html>"
        web.setHtml(t)
        self.mw.progress.finish()

    def _onTagDupes(self, res):
        if not res:
            return
        self.model.beginReset()
        self.mw.checkpoint(_("Tag Duplicates"))
        nids = set()
        for s, nidlist in res:
            nids.update(nidlist)
        self.col.tags.bulkAdd(nids, _("duplicate"))
        self.mw.progress.finish()
        self.model.endReset()
        self.mw.requireReset()
        tooltip(_("Notes tagged."))

    def dupeLinkClicked(self, link):
        self.form.searchEdit.lineEdit().setText(link)
        # manually, because we've already saved
        self._lastSearchTxt = link
        self.search()
        self.onNote()

    # Jumping
    ######################################################################

    def _moveCur(self, dir=None, idx=None):
        if not self.model.cards:
            return
        tv = self.form.tableView
        if idx is None:
            idx = tv.moveCursor(dir, self.mw.app.keyboardModifiers())
        tv.selectionModel().clear()
        tv.setCurrentIndex(idx)

    def onPreviousCard(self):
        self.editor.saveNow(self._onPreviousCard)

    def _onPreviousCard(self):
        f = self.editor.currentField
        self._moveCur(QAbstractItemView.MoveUp)
        self.editor.web.setFocus()
        self.editor.web.eval("focusField(%d)" % f)

    def onNextCard(self):
        self.editor.saveNow(self._onNextCard)

    def _onNextCard(self):
        f = self.editor.currentField
        self._moveCur(QAbstractItemView.MoveDown)
        self.editor.web.setFocus()
        self.editor.web.eval("focusField(%d)" % f)

    def onFirstCard(self):
        sm = self.form.tableView.selectionModel()
        idx = sm.currentIndex()
        self._moveCur(None, self.model.index(0, 0))
        if not self.mw.app.keyboardModifiers() & Qt.ShiftModifier:
            return
        idx2 = sm.currentIndex()
        item = QItemSelection(idx2, idx)
        sm.select(item, QItemSelectionModel.SelectCurrent|
                  QItemSelectionModel.Rows)

    def onLastCard(self):
        sm = self.form.tableView.selectionModel()
        idx = sm.currentIndex()
        self._moveCur(
            None, self.model.index(len(self.model.cards) - 1, 0))
        if not self.mw.app.keyboardModifiers() & Qt.ShiftModifier:
            return
        idx2 = sm.currentIndex()
        item = QItemSelection(idx, idx2)
        sm.select(item, QItemSelectionModel.SelectCurrent|
                  QItemSelectionModel.Rows)

    def onFind(self):
        self.form.searchEdit.setFocus()
        self.form.searchEdit.lineEdit().selectAll()

    def onNote(self):
        self.editor.focus()
        self.editor.web.setFocus()
        self.editor.web.eval("focusField(0);")

    def onTags(self):
        self.form.tree.setFocus()

    def onCardList(self):
        self.form.tableView.setFocus()

    def focusCid(self, cid):
        try:
            row = self.model.cards.index(cid)
        except:
            return
        self.form.tableView.selectRow(row)

    # Note update:
    ######################################################################
    def on_start_change_note_type(self):
        self.mw.checkpoint(_("Change Note Type"))
        self.mw.col.modSchema(check=True)
        self.mw.progress.start()
        self.model.beginReset()

    def on_end_change_note_type(self):
        self.search()
        self.model.endReset()
        self.mw.progress.finish()
        self.mw.reset()


# Change model dialog
######################################################################

class ChangeModel(QDialog):
    """
    Dialog that allows user to create field and template maps from one note model to another
    """

    def __init__(self, collection, note_id_list, old_model=None, parent=None):
        QDialog.__init__(self, parent)
        self.collection = collection
        self.nids = note_id_list
        self.oldModel = old_model
        if self.oldModel is None:
            first_note = Note(collection, id=note_id_list[0])
            self.oldModel = first_note.model()

        self.form = aqt.forms.changemodel.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowModality(Qt.WindowModal)
        self.setup()

        self.pauseUpdate = False
        self.modelChanged(self.collection.models.current())

        aqt.utils.restoreGeom(self, "changeModel")
        anki.hooks.addHook("reset", self.onReset)
        anki.hooks.addHook("currentModelChanged", self.onReset)

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
        self.form.oldModelLabel.setText(self.oldModel['name'])
        self.modelChooser = aqt.modelchooser.ModelChooser(
            aqt.mw, self.form.modelChooserWidget, label=False)
        self.modelChooser.models.setFocus()
        self.form.buttonBox.helpRequested.connect(self.onHelp)

    def onReset(self):
        self.modelChanged(self.collection.models.current())

    def modelChanged(self, model):
        self.targetModel = model
        self.rebuildTemplateMap()
        self.rebuildFieldMap()

    def rebuildTemplateMap(self, key=None, attr=None):
        if not key:
            key = "t"
            attr = "tmpls"
        map_widget = getattr(self, key + "widg")
        layout = getattr(self, key + "layout")
        src = self.oldModel[attr]
        dst = self.targetModel[attr]
        if map_widget:
            layout.removeWidget(map_widget)
            map_widget.deleteLater()
            setattr(self, key + "MapWidget", None)
        map_widget = QWidget()
        map_widget_layout = QGridLayout()
        combos = []
        targets = [entity['name'] for entity in dst] + [_("Nothing")]
        indices = {}
        for i, entity in enumerate(src):
            map_widget_layout.addWidget(QLabel(_("Change %s to:") % entity['name']), i, 0)
            combo_box = QComboBox()
            combo_box.addItems(targets)
            idx = min(i, len(targets) - 1)
            combo_box.setCurrentIndex(idx)
            indices[combo_box] = idx
            combo_box.currentIndexChanged.connect(
                lambda entry_id: self.onComboChanged(entry_id, combo_box, key))
            combos.append(combo_box)
            map_widget_layout.addWidget(combo_box, i, 1)
        map_widget.setLayout(map_widget_layout)
        layout.addWidget(map_widget)
        setattr(self, key + "widg", map_widget)
        setattr(self, key + "layout", layout)
        setattr(self, key + "combos", combos)
        setattr(self, key + "indices", indices)

    def rebuildFieldMap(self):
        return self.rebuildTemplateMap(key="f", attr="flds")

    def onComboChanged(self, combo_box_index, combo_box, key):
        indices = getattr(self, key + "indices")
        if self.pauseUpdate:
            indices[combo_box] = combo_box_index
            return
        combos = getattr(self, key + "combos")
        if combo_box_index == combo_box.count() - 1:
            # set to 'nothing'
            return
        # find another combo with same index
        for c in combos:
            if c == combo_box:
                continue
            if c.currentIndex() == combo_box_index:
                self.pauseUpdate = True
                c.setCurrentIndex(indices[combo_box])
                self.pauseUpdate = False
                break
        indices[combo_box] = combo_box_index

    def getTemplateMap(self, old=None, combos=None, new=None):
        if not old:
            old = self.oldModel['tmpls']
            combos = self.tcombos
            new = self.targetModel['tmpls']
        model_map = {}
        for i, f in enumerate(old):
            idx = combos[i].currentIndex()
            if idx == len(new):
                # ignore
                model_map[f['ord']] = None
            else:
                f2 = new[idx]
                model_map[f['ord']] = f2['ord']
        return model_map

    def getFieldMap(self):
        return self.getTemplateMap(
            old=self.oldModel['flds'],
            combos=self.fcombos,
            new=self.targetModel['flds'])

    def cleanup(self):
        anki.hooks.remHook("reset", self.onReset)
        anki.hooks.remHook("currentModelChanged", self.onReset)
        self.modelChooser.cleanup()
        aqt.utils.saveGeom(self, "changeModel")

    def reject(self):
        self.cleanup()
        return QDialog.reject(self)

    def accept(self):
        # check maps
        field_map = self.getFieldMap()
        templates_map = self.getTemplateMap()
        if any(True for template in list(templates_map.values()) if template is None):
            if not aqt.utils.askUser(_(
                    "Any cards mapped to nothing will be deleted. "
                    "If a note has no remaining cards, it will be lost. "
                    "Are you sure you want to continue?")):
                return

        self.collection.models.change(self.oldModel, self.nids, self.targetModel, field_map, templates_map)

        self.cleanup()
        QDialog.accept(self)

    @staticmethod
    def onHelp():
        aqt.utils.openHelp("browsermisc")


# Toolbar
######################################################################

class BrowserToolbar(Toolbar):

    def __init__(self, mw, web, browser):
        self.browser = browser
        Toolbar.__init__(self, mw, web)

    def draw(self):
        self._loaded = False
        self.web.onBridgeCmd = self._linkHandler
        self.web.onLoadFinished = self.onLoaded
        self.web.stdHtml(self.html(), self.css())

    def onLoaded(self):
        super().onLoaded()
        self._loaded = True
        self.update()

    def update(self):
        if not self._loaded:
            return
        for link, enabled in (
            ("mark", self.browser.isMarked()),
            ("pause", self.browser.isSuspended())):
            if enabled:
                self.web.eval("$('#%s').addClass('buttonOn')" % link)
            else:
                self.web.eval("$('#%s').removeClass('buttonOn')" % link)

    def html(self):
        def borderImg(link, icon, title, tooltip=None):
            fmt = '''\
<a class=hitem title="%s" href=# onclick="pycmd('%s')"><img id=%s valign=bottom src="qrc:/icons/%s.png"> %s</a>'''
            return fmt % (tooltip or title, link, link, icon, title)
        right = "<div>"
        right += borderImg("add", "add16", _("Add"),
                       shortcut(_("Add Note (Ctrl+E)")))
        right += borderImg("info", "info", _("Info"),
                       shortcut(_("Card Info (Ctrl+Shift+I)")))
        right += borderImg("mark", "star16", _("Mark"),
                       shortcut(_("Mark Note (Ctrl+K)")))
        right += borderImg("pause", "pause16", _("Suspend"),
                       shortcut(_("Suspend Card (Ctrl+J)")))
        right += borderImg("setDeck", "deck16", _("Change Deck"),
                           shortcut(_("Move To Deck (Ctrl+D)")))
        right += borderImg("addtag", "addtag16", _("Add Tags"),
                       shortcut(_("Bulk Add Tags (Ctrl+Shift+T)")))
        right += borderImg("deletetag", "deletetag16", _("Remove Tags"), shortcut(_(
                               "Bulk Remove Tags (Ctrl+Alt+T)")))
        right += borderImg("delete", "delete16", _("Delete"))
        right += "</div>"
        return self._body % ("", right, "")

    def css(self):
        return self._css + """
#header { font-weight: normal; }
a { margin-right: 1em; }
.hitem { overflow: hidden; white-space: nowrap; }
.hitem img { padding: 1px; }
.buttonOn { border: 1px solid #aaa; padding: 0px !important; }
"""

    # Link handling
    ######################################################################

    def _linkHandler(self, l):
        if l == "anki":
            self.showMenu()
        elif l  == "add":
            self.browser.mw.onAddCard()
        elif l  == "delete":
            self.browser.deleteNotes()
        elif l  == "setDeck":
            self.browser.setDeck()
        # icons
        elif l  == "info":
            self.browser.showCardInfo()
        elif l == "mark":
            self.browser.onMark()
        elif l == "pause":
            self.browser.onSuspend()
        elif l == "addtag":
            self.browser.addTags()
        elif l == "deletetag":
            self.browser.deleteTags()


# Favourites button
######################################################################
class FavouritesLineEdit(QLineEdit):
    buttonClicked = pyqtSignal(bool)

    def __init__(self, mw, browser, parent=None):
        super(FavouritesLineEdit, self).__init__(parent)
        self.mw = mw
        self.browser = browser
        # add conf if missing
        if 'savedFilters' not in self.mw.col.conf:
            self.mw.col.conf['savedFilters'] = {}
        self.button = QToolButton(self)
        self.button.setStyleSheet('border: 0px;')
        self.button.setCursor(Qt.ArrowCursor)
        self.button.clicked.connect(self.buttonClicked.emit)
        self.setIcon(':/icons/emblem-favorite-off.png')
        # flag to raise save or delete dialog on button click
        self.doSave = True
        # name of current saved filter (if query matches)
        self.name = None
        self.buttonClicked.connect(self.onClicked)
        self.textChanged.connect(self.updateButton)

    def resizeEvent(self, event):
        buttonSize = self.button.sizeHint()
        frameWidth = self.style().pixelMetric(QStyle.PM_DefaultFrameWidth)
        self.button.move(self.rect().right() - frameWidth - buttonSize.width(),
                         (self.rect().bottom() - buttonSize.height() + 1) / 2)
        self.setTextMargins(0, 0, buttonSize.width() * 1.5, 0)
        super(FavouritesLineEdit, self).resizeEvent(event)

    def setIcon(self, path):
        self.button.setIcon(QIcon(path))

    def setText(self, txt):
        super(FavouritesLineEdit, self).setText(txt)
        self.updateButton()
        
    def updateButton(self, reset=True):
        # If search text is a saved query, switch to the delete button.
        # Otherwise show save button.
        txt = str(self.text()).strip()
        for key, value in list(self.mw.col.conf['savedFilters'].items()):
            if txt == value:
                self.doSave = False
                self.name = key
                self.setIcon(QIcon(":/icons/emblem-favorite.png"))
                return
        self.doSave = True
        self.setIcon(QIcon(":/icons/emblem-favorite-off.png"))
    
    def onClicked(self):
        if self.doSave:
            self.saveClicked()
        else:
            self.deleteClicked()
    
    def saveClicked(self):
        txt = str(self.text()).strip()
        dlg = QInputDialog(self)
        dlg.setInputMode(QInputDialog.TextInput)
        dlg.setLabelText(_("The current search terms will be added as a new "
                           "item in the sidebar.\n"
                           "Search name:"))
        dlg.setWindowTitle(_("Save search"))
        ok = dlg.exec_()
        name = dlg.textValue()
        if ok:
            self.mw.col.conf['savedFilters'][name] = txt
            self.mw.col.setMod()
            
        self.updateButton()
        self.browser.buildTree()
    
    def deleteClicked(self):
        msg = _('Remove "%s" from your saved searches?') % self.name
        ok = QMessageBox.question(self, _('Remove search'),
                         msg, QMessageBox.Yes, QMessageBox.No)
    
        if ok == QMessageBox.Yes:
            self.mw.col.conf['savedFilters'].pop(self.name, None)
            self.mw.col.setMod()
            self.updateButton()
            self.browser.buildTree()
