# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sre_constants
from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtWebKit import QWebPage
import time, types, sys, re
from operator import attrgetter, itemgetter
import anki, anki.utils, aqt.forms
from anki.utils import fmtTimeSpan, parseTags, hasTag, addTags, delTags, \
    ids2str, stripHTMLMedia
from aqt.utils import saveGeom, restoreGeom, saveSplitter, restoreSplitter, \
    saveHeader, restoreHeader, saveState, restoreState, applyStyles
from anki.errors import *
from anki.db import *
from anki.hooks import runHook, addHook, removeHook

# fixme: notice added tags?

COLOUR_SUSPENDED1 = "#ffffcc"
COLOUR_SUSPENDED2 = "#ffffaa"
COLOUR_INACTIVE1 = "#ffcccc"
COLOUR_INACTIVE2 = "#ffaaaa"
COLOUR_MARKED1 = "#ccccff"
COLOUR_MARKED2 = "#aaaaff"

# Data model
##########################################################################

class DeckModel(QAbstractTableModel):

    def __init__(self, browser):
        QAbstractTableModel.__init__(self)
        self.browser = browser
        self.deck = browser.deck
        self.sortKey = None
        self.columns = ["question", "answer", "sortField", "cardDue", "cardEase"]
        self.cards = []
        self.cardObjs = {}

    def getCard(self, index):
        id = self.cards[index.row()]
        if not id in self.cardObjs:
            self.cardObjs[id] = self.deck.getCard(id)
        return self.cardObjs[id]

    # Model interface
    ######################################################################

    def rowCount(self, index):
        return len(self.cards)

    def columnCount(self, index):
        return len(self.columns)

    def data(self, index, role):
        if not index.isValid():
            return QVariant()
        if role == Qt.FontRole:
            f = QFont()
            f.setPixelSize(self.browser.mw.config['editFontSize'])
            return QVariant(f)
        if role == Qt.TextAlignmentRole:
            align = Qt.AlignVCenter
            if index.column() > 1:
                align |= Qt.AlignHCenter
            return QVariant(align)
        elif role == Qt.DisplayRole or role == Qt.EditRole:
            return QVariant(self.columnData(index))
        else:
            return QVariant()

    def headerData(self, section, orientation, role):
        if orientation == Qt.Vertical:
            return QVariant()
        elif role == Qt.DisplayRole:
            type = self.columnType(section)
            if type == "question":
                txt = _("Question")
            elif type == "answer":
                txt = _("Answer")
            else:
                for stype, name in self.browser.sortTypes:
                    if type == stype:
                        txt = name
                        break
            return QVariant(txt)
        elif role == Qt.FontRole:
            f = QFont()
            f.setPixelSize(10)
            return QVariant(f)
        else:
            return QVariant()

    def flags(self, index):
        return Qt.ItemFlag(Qt.ItemIsEnabled |
                           Qt.ItemIsSelectable)

    # Filtering
    ######################################################################

    def showMatching(self, force=True):
        self.browser.mw.progress.start()
        t = time.time()
        self.cards = self.deck.findCards(self.searchStr.strip())
        print "fetch cards in %dms" % ((time.time() - t)*1000)
        self.browser.mw.progress.finish()
        self.reset()

    def refresh(self):
        self.cardObjs = {}
        self.emit(SIGNAL("layoutChanged()"))

    # Column data
    ######################################################################

    def columnType(self, column):
        type = self.columns[column]
        if type == "sortField":
            type = self.deck.conf['sortType']
            if type == "factFld":
                type = "cardDue"
        return type

    def columnData(self, index):
        row = index.row()
        col = index.column()
        type = self.columnType(col)
        c = self.getCard(index)
        if type == "question":
            return self.formatQA(c.q())
        elif type == "answer":
            return self.formatQA(c.a())
        elif type == "factFld" or type == "cardDue":
            return self.nextDue(c, index)
        elif type == "factCrt":
            return time.strftime("%Y-%m-%d", time.localtime(c.fact().crt))
        elif type == "factMod":
            return time.strftime("%Y-%m-%d", time.localtime(c.fact().mod))
        elif type == "cardMod":
            return time.strftime("%Y-%m-%d", time.localtime(c.mod))
        elif type == "cardReps":
            return str(c.reps)
        elif type == "cardLapses":
            return str(c.lapses)
        elif type == "cardIvl":
            return fmtTimeSpan(c.ivl*86400)
        elif type == "cardEase":
            return "%d%%" % (c.factor/10)

    # def intervalColumn(self, index):
    #     return fmtTimeSpan(
    #         self.cards[index.row()][CARD_INTERVAL]*86400)

    # def limitContent(self, txt):
    #     if "<c>" in txt:
    #         matches = re.findall("(?s)<c>(.*?)</c>", txt)
    #         return " ".join(matches)
    #     else:
    #         return txt

    def formatQA(self, txt):
        #s = self.limitContent(s)
        s = txt.replace("<br>", u" ")
        s = s.replace("<br />", u" ")
        s = s.replace("\n", u" ")
        s = re.sub("\[sound:[^]]+\]", "", s)
        s = stripHTMLMedia(s)
        s = s.strip()
        return s

    def nextDue(self, c, index):
        if c.type == 0:
            return _("(new card)")
        elif c.type == 1:
            date = c.due
        elif c.type == 2:
            date = time.time() + ((c.due - self.deck.sched.today)*86400)
        return time.strftime("%Y-%m-%d", time.localtime(date))

# Line painter
######################################################################

class StatusDelegate(QItemDelegate):

    def __init__(self, browser, model):
        QItemDelegate.__init__(self, browser)
        self.model = model

    def paint(self, painter, option, index):
        c = self.model.getCard(index)
        if c.queue < 0:
            # custom render
            if index.row() % 2 == 0:
                brush = QBrush(QColor(COLOUR_SUSPENDED1))
            else:
                brush = QBrush(QColor(COLOUR_SUSPENDED2))
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()
        elif c.fact().hasTag("Marked"):
            if index.row() % 2 == 0:
                brush = QBrush(QColor(COLOUR_MARKED1))
            else:
                brush = QBrush(QColor(COLOUR_MARKED2))
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()
        return QItemDelegate.paint(self, painter, option, index)

# Browser window
######################################################################

# fixme: respond to reset+edit hooks

class Browser(QMainWindow):

    def __init__(self, mw):
        QMainWindow.__init__(self, None)
        #applyStyles(self)
        self.mw = mw
        self.deck = self.mw.deck
        self.currentRow = None
        self.lastFilter = ""
        self.form = aqt.forms.browser.Ui_Dialog()
        self.form.setupUi(self)
        self.setUnifiedTitleAndToolBarOnMac(True)
        restoreGeom(self, "editor", 38)
        restoreState(self, "editor")
        restoreSplitter(self.form.splitter, "editor1")
        restoreSplitter(self.form.splitter_2, "editor2")
        restoreSplitter(self.form.splitter_3, "editor3")
        self.form.splitter.setChildrenCollapsible(False)
        self.form.splitter_2.setChildrenCollapsible(False)
        self.form.splitter_3.setChildrenCollapsible(False)
        self.setupSort()
        self.setupToolbar()
        self.setupTable()
        self.setupMenus()
        self.setupSearch()
        self.setupTree()
        self.setupHeaders()
        self.setupHooks()
        self.setupEditor()
        self.setupCardInfo()
        self.updateSortIcon()
        self.updateFont()
        self.form.searchEdit.setFocus()
        self.updateFilterLabel()
        self.show()
        self.form.searchEdit.setText("is:recent")
        self.form.searchEdit.selectAll()
        self.onSearch()
        # if self.parent.card:
        #     self.card = self.parent.card
        #self.updateSearch()

    def setupToolbar(self):
        self.form.toolBar.setIconSize(QSize(self.mw.config['iconSize'],
                                              self.mw.config['iconSize']))
        self.form.toolBar.toggleViewAction().setText(_("Toggle Toolbar"))

    def setupHeaders(self):
        vh = self.form.tableView.verticalHeader()
        hh = self.form.tableView.horizontalHeader()
        if not sys.platform.startswith("win32"):
            vh.hide()
            hh.show()
        restoreHeader(hh, "editor")
        for i in range(2):
            hh.setResizeMode(i, QHeaderView.Stretch)
        hh.setResizeMode(2, QHeaderView.Interactive)

    def setupMenus(self):
        # actions
        c = self.connect; f = self.form; s = SIGNAL("triggered()")
        c(f.actionAddItems, s, self.mw.onAddCard)
        c(f.actionDelete, s, self.deleteCards)
        c(f.actionAddTag, s, self.addTags)
        c(f.actionDeleteTag, s, self.deleteTags)
        c(f.actionReschedule, s, self.reschedule)
        c(f.actionCram, s, self.cram)
        c(f.actionAddCards, s, self.addCards)
        c(f.actionChangeModel, s, self.onChangeModel)
        c(f.actionToggleSuspend, SIGNAL("triggered(bool)"), self.onSuspend)
        c(f.actionToggleMark, SIGNAL("triggered(bool)"), self.onMark)
        # edit
        c(f.actionFont, s, self.onFont)
        c(f.actionUndo, s, self.mw.onUndo)
        c(f.actionInvertSelection, s, self.invertSelection)
        c(f.actionSelectFacts, s, self.selectFacts)
        c(f.actionFindReplace, s, self.onFindReplace)
        c(f.actionFindDuplicates, s, self.onFindDupes)
        # jumps
        c(f.actionFirstCard, s, self.onFirstCard)
        c(f.actionLastCard, s, self.onLastCard)
        c(f.actionPreviousCard, s, self.onPreviousCard)
        c(f.actionNextCard, s, self.onNextCard)
        c(f.actionFind, s, self.onFind)
        c(f.actionFact, s, self.onFact)
        c(f.actionTags, s, self.onTags)
        c(f.actionSort, s, self.onSort)
        c(f.actionCardList, s, self.onCardList)
        # help
        c(f.actionGuide, s, self.onHelp)
        runHook('browser.setupMenus', self)

    def updateFont(self):
        self.form.tableView.setFont(QFont(
            self.mw.config['editFontFamily'],
            self.mw.config['editFontSize']))
        self.form.tableView.verticalHeader().setDefaultSectionSize(
            self.mw.config['editLineSize'])
        self.model.reset()

    def closeEvent(self, evt):
        saveSplitter(self.form.splitter, "editor1")
        saveSplitter(self.form.splitter_2, "editor2")
        saveSplitter(self.form.splitter_3, "editor3")
        self.editor.saveNow()
        self.editor.setFact(None)
        saveGeom(self, "editor")
        saveState(self, "editor")
        saveHeader(self.form.tableView.horizontalHeader(), "editor")
        self.hide()
        aqt.dialogs.close("Browser")
        self.teardownHooks()
        evt.accept()

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if evt.key() in (Qt.Key_Escape,):
            self.close()

    # Searching
    ######################################################################

    def setupSearch(self):
        self.filterTimer = None
        self.connect(self.form.searchButton,
                     SIGNAL("clicked()"),
                     self.onSearch)
        self.connect(self.form.searchEdit,
                     SIGNAL("returnPressed()"),
                     self.onSearch)
        self.setTabOrder(self.form.searchEdit, self.form.tableView)

    def onSearch(self, force=True):
        # fixme:
        # if self.mw.inDbHandler:
        #     return
        self.model.searchStr = unicode(self.form.searchEdit.text())
        self.model.showMatching(force)
        self.updateFilterLabel()
        self.filterTimer = None
        if self.model.cards:
            self.form.cardInfoGroup.show()
            self.form.fieldsArea.show()
        else:
            self.form.cardInfoGroup.hide()
            self.form.fieldsArea.hide()
        if not self.focusCard():
            if self.model.cards:
                self.form.tableView.selectRow(0)
        if not self.model.cards:
            self.editor.setFact(None)

    def updateFilterLabel(self):
        selected = len(self.form.tableView.selectionModel().selectedRows())
        self.setWindowTitle(ngettext("Browser (%(cur)d "
                              "of %(tot)d card shown; %(sel)s)", "Browser (%(cur)d "
                              "of %(tot)d cards shown; %(sel)s)", self.deck.cardCount) %
                            {
            "cur": len(self.model.cards),
            "tot": self.deck.cardCount(),
            "sel": ngettext("%d selected", "%d selected", selected) % selected
            } + " - " + self.deck.name())

    # Table view
    ######################################################################

    def setupTable(self):
        self.model = DeckModel(self)
        self.form.tableView.setSortingEnabled(False)
        self.form.tableView.setShowGrid(False)
        self.form.tableView.setModel(self.model)
        self.form.tableView.selectionModel()
        self.connect(self.form.tableView.selectionModel(),
                     SIGNAL("selectionChanged(QItemSelection,QItemSelection)"),
                     self.updateFilterLabel)
        self.form.tableView.setItemDelegate(StatusDelegate(self, self.model))

    def rowChanged(self, current, previous):
        self.currentRow = current
        self.card = self.model.getCard(current)
        if not self.card:
            self.editor.setFact(None, True)
            return
        fact = self.card.fact()
        self.editor.setFact(fact)
        self.editor.card = self.card
        self.showCardInfo(self.card)
        self.updateToggles()

    def cardRow(self):
        try:
            return self.model.cards.index(self.card.id)
        except:
            return -1

    def focusCard(self):
        print "focus"
        if self.card:
            try:
                self.card.id
            except:
                return False
            row = self.cardRow()
            if row >= 0:
                sm = self.form.tableView.selectionModel()
                sm.clear()
                self.form.tableView.selectRow(row)
                self.form.tableView.scrollTo(
                              self.model.index(row,0),
                              self.form.tableView.PositionAtCenter)
                return True
        return False

    # Sorting
    ######################################################################

    def setupSort(self):
        self.sortTypes = [
            ('factFld', _("Fields")),
            ('factCrt', _("Creation date")),
            ('factMod', _("Edit date")),
            ('cardMod', _("Review date")),
            ('cardDue', _("Due date")),
            ('cardIvl', _("Card interval")),
            ('cardEase', _("Ease factor")),
            ('cardReps', _("Review count")),
            ('cardLapses', _("Lapse count")),
        ]
        for c, (type, name) in enumerate(self.sortTypes):
            self.form.sortBox.addItem(name)
            if type == self.deck.conf['sortType']:
                idx = c
        self.form.sortBox.setCurrentIndex(idx)
        self.connect(self.form.sortBox, SIGNAL("activated(int)"),
                     self.sortChanged)
        self.sortChanged(idx, refresh=False)
        self.connect(self.form.sortOrder, SIGNAL("clicked()"),
                     self.toggleSortOrder)

    def toggleSortOrder(self):
        self.deck.conf['sortBackwards'] = not self.deck.conf['sortBackwards']
        self.model.cards.reverse()
        self.model.reset()
        self.focusCard()
        self.updateSortIcon()

    def updateSortIcon(self):
        if self.deck.conf['sortBackwards']:
            self.form.sortOrder.setIcon(QIcon(":/icons/view-sort-descending.png"))
        else:
            self.form.sortOrder.setIcon(QIcon(":/icons/view-sort-ascending.png"))

    def sortChanged(self, idx, refresh=True):
        self.deck.conf['sortType'] = self.sortTypes[idx][0]
        if refresh:
            self.model.showMatching()
            # fixme: we do this in various locations
            self.updateFilterLabel()
            self.focusCard()

    # Filter tree
    ######################################################################

    class CallbackItem(QTreeWidgetItem):
        def __init__(self, name, onclick):
            QTreeWidgetItem.__init__(self, [name])
            self.onclick = onclick

    def setupTree(self):
        self.form.tree.addTopLevelItem(self._modelTree())
        self.form.tree.addTopLevelItem(self._groupTree())
        self.form.tree.addTopLevelItem(self._systemTagTree())
        self.form.tree.addTopLevelItem(self._userTagTree())
        self.form.tree.expandToDepth(0)
        self.form.tree.setIndentation(15)
        self.connect(
            self.form.tree, SIGNAL("itemClicked(QTreeWidgetItem*,int)"),
            self.onTreeClick)

    def onTreeClick(self, item, col):
        if getattr(item, 'onclick', None):
            item.onclick()

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
                    if " " in txt:
                        txt = "'%s'" % txt
                    items.append(txt)
                    txt = ""
            txt = " ".join(items)
        self.form.searchEdit.setText(txt)
        self.onSearch()

    def _modelTree(self):
        root = QTreeWidgetItem([_("Models")])
        root.setIcon(0, QIcon(":/icons/product_design.png"))
        for m in sorted(self.deck.models().values(), key=attrgetter("name")):
            mitem = self.CallbackItem(
                m.name, lambda m=m: self.setFilter("model", m.name))
            mitem.setIcon(0, QIcon(":/icons/product_design.png"))
            root.addChild(mitem)
            for t in m.templates:
                titem = self.CallbackItem(
                t['name'], lambda m=m, t=t: self.setFilter(
                    "model", m.name, "card", t['name']))
                titem.setIcon(0, QIcon(":/icons/stock_new_template.png"))
                mitem.addChild(titem)
        return root

    def _groupTree(self):
        root = QTreeWidgetItem([_("Groups")])
        root.setIcon(0, QIcon(":/icons/stock_group.png"))
        grps = self.deck.sched.groupTree()
        def fillGroups(root, grps, head=""):
            for g in grps:
                item = self.CallbackItem(
                g[0], lambda g=g: self.setFilter(
                    "group", head+g[0]))
                item.setIcon(0, QIcon(":/icons/stock_group.png"))
                root.addChild(item)
                fillGroups(item, g[5], g[0]+"::")
        fillGroups(root, grps)
        return root

    def _systemTagTree(self):
        root = QTreeWidgetItem([_("System Tags")])
        root.setIcon(0, QIcon(":/icons/anki-tag.png"))
        tags = ((_("New"), "anki-tag.png", "is:new"),
                (_("Learning"), "anki-tag.png", "is:lrn"),
                (_("Review"), "anki-tag.png", "is:rev"),
                (_("Due"), "anki-tag.png", "is:due"),
                (_("Marked"), "anki-tag.png", "tag:marked"),
                (_("Suspended"), "anki-tag.png", "is:suspended"),
                (_("Leech"), "anki-tag.png", "tag:leech"))
        for name, icon, cmd in tags:
            item = self.CallbackItem(
                name, lambda c=cmd: self.setFilter(c))
            item.setIcon(0, QIcon(":/icons/" + icon))
            root.addChild(item)
        return root

    def _userTagTree(self):
        root = QTreeWidgetItem([_("User Tags")])
        root.setIcon(0, QIcon(":/icons/anki-tag.png"))
        for t in self.deck.tagList():
            item = self.CallbackItem(
                t, lambda t=t: self.setFilter("tag", t))
            item.setIcon(0, QIcon(":/icons/anki-tag.png"))
            root.addChild(item)
        return root

    # Editor
    ######################################################################

    def setupEditor(self):
        self.editor = aqt.editor.Editor(self.mw,
                                        self.form.fieldsArea)
        self.editor.stealFocus = False
        self.connect(self.form.tableView.selectionModel(),
                     SIGNAL("currentRowChanged(QModelIndex, QModelIndex)"),
                     self.rowChanged)

    # Card info
    ######################################################################

    def setupCardInfo(self):
        from anki.stats import CardStats
        self.card = None
        self.cardStats = CardStats(self.deck, None)
        self.connect(self.form.cardLabel,
                     SIGNAL("linkActivated(const QString&)"),
                     self.onChangeSortField)

    def showCardInfo(self, card):
        self.cardStats.card = self.card
        rep = self.cardStats.report()
        rep = "<style>table * { font-size: 12px; }</style>" + rep
        m = self.card.model()
        sortf = m.fields[m.sortIdx()]['name']
        rep = rep.replace(
            "</table>",
            "<tr><td><b>%s</b></td><td>%s</td></tr></table>" % (
                _("Sort Field"),
                "<a href=foo>%s</a>" % sortf))
        self.form.cardLabel.setText(rep)

    def onChangeSortField(self):
        from aqt.utils import chooseList
        m = self.card.model()
        fields = [f['name'] for f in m.fields]
        idx = chooseList(_("Choose field to sort this model by:"),
                         fields, m.sortIdx())
        if idx != m.sortIdx():
            self.mw.progress.start()
            m.setSortIdx(idx)
            self.mw.progress.finish()
            self.onSearch()

    # Menu helpers
    ######################################################################

    def selectedCards(self):
        return [self.model.cards[idx.row()][0] for idx in
                self.form.tableView.selectionModel().selectedRows()]

    def selectedFacts(self):
        return self.deck.db.column0("""
select distinct factId from cards
where id in (%s)""" % ",".join([
            str(self.model.cards[idx.row()][0]) for idx in
            self.form.tableView.selectionModel().selectedRows()]))

    def selectedFactsAsCards(self):
        return self.deck.db.column0(
            "select id from cards where factId in (%s)" %
            ",".join([str(s) for s in self.selectedFacts()]))

    def updateAfterCardChange(self):
        "Refresh info like stats on current card, and rebuild mw queue."
        self.currentRow = self.form.tableView.currentIndex()
        self.rowChanged(self.currentRow, None)
        self.model.refresh()
        self.mw.reset()

    # Menu options
    ######################################################################

    def deleteCards(self):
        cards = self.selectedCards()
        n = _("Delete Cards")
        try:
            new = self.cardRow() + 1
        except:
            # card has been deleted
            return
        # ensure the change timer doesn't fire after deletion but before reset
        self.editor.saveNow()
        self.editor.fact = None
        self.form.tableView.setFocus()
        self.deck.setUndoStart(n)
        self.deck.deleteCards(cards)
        self.deck.setUndoEnd(n)
        new = min(max(0, new), len(self.model.cards) - 1)
        self.form.tableView.selectRow(new)
        self.onSearch()
        self.updateAfterCardChange()

    def addTags(self, tags=None, label=None):
        # focus lost hook may not have chance to fire
        self.editor.saveNow()
        if tags is None:
            (tags, r) = ui.utils.getTag(self, self.deck, _("Enter tags to add:"))
        else:
            r = True
        if label is None:
            label = _("Add Tags")
        if r:
            self.deck.setUndoStart(label)
            self.deck.addTags(self.selectedFacts(), tags)
            self.deck.setUndoEnd(label)
        self.updateAfterCardChange()

    def deleteTags(self, tags=None, label=None):
        # focus lost hook may not have chance to fire
        self.editor.saveNow()
        if tags is None:
            (tags, r) = ui.utils.getTag(self, self.deck, _("Enter tags to delete:"))
        else:
            r = True
        if label is None:
            label = _("Delete Tags")
        if r:
            self.deck.setUndoStart(label)
            self.deck.deleteTags(self.selectedFacts(), tags)
            self.deck.setUndoEnd(label)
        self.updateAfterCardChange()

    def updateToggles(self):
        self.form.actionToggleSuspend.setChecked(self.isSuspended())
        self.form.actionToggleMark.setChecked(self.isMarked())

    def isSuspended(self):
        return self.card and self.card.type < 0

    def onSuspend(self, sus):
        # focus lost hook may not have chance to fire
        self.editor.saveNow()
        if sus:
            self._onSuspend()
        else:
            self._onUnsuspend()

    def _onSuspend(self):
        n = _("Suspend")
        self.deck.setUndoStart(n)
        self.deck.suspendCards(self.selectedCards())
        self.mw.reset()
        self.deck.setUndoEnd(n)
        self.model.refresh()
        self.updateAfterCardChange()

    def _onUnsuspend(self):
        n = _("Unsuspend")
        self.deck.setUndoStart(n)
        self.deck.unsuspendCards(self.selectedCards())
        self.mw.reset()
        self.deck.setUndoEnd(n)
        self.model.refresh()
        self.updateAfterCardChange()

    def isMarked(self):
        return self.card and self.card.fact().hasTag("Marked")

    def onMark(self, mark):
        if mark:
            self._onMark()
        else:
            self._onUnmark()

    def _onMark(self):
        self.addTags(tags="Marked", label=_("Toggle Mark"))

    def _onUnmark(self):
        self.deleteTags(tags="Marked", label=_("Toggle Mark"))

    def reschedule(self):
        n = _("Reschedule")
        d = QDialog(self)
        frm = aqt.forms.reschedule.Ui_Dialog()
        frm.setupUi(d)
        if not d.exec_():
            return
        self.deck.setUndoStart(n)
        try:
            if frm.asNew.isChecked():
                self.deck.resetCards(self.selectedCards())
            else:
                try:
                    min = float(frm.rangeMin.value())
                    max = float(frm.rangeMax.value())
                except ValueError:
                    ui.utils.showInfo(
                        _("Please enter a valid range."),
                        parent=self)
                    return
                self.deck.rescheduleCards(self.selectedCards(), min, max)
        finally:
            self.deck.reset()
            self.deck.setUndoEnd(n)
        self.updateAfterCardChange()

    def addCards(self):
        sf = self.selectedFacts()
        if not sf:
            return
        mods = self.deck.db.column0("""
select distinct modelId from facts
where id in %s""" % ids2str(sf))
        if not len(mods) == 1:
            ui.utils.showInfo(
                _("Can only operate on one model at a time."),
                parent=self)
            return
        # get cards to enable
        cms = [x.id for x in self.deck.db.query(Fact).get(sf[0]).\
               model.cardModels]
        d = AddCardChooser(self, cms)
        if not d.exec_():
            return
        # for each fact id, generate
        n = _("Generate Cards")
        self.deck.startProgress()
        self.deck.setUndoStart(n)
        facts = self.deck.db.query(Fact).filter(
            text("id in %s" % ids2str(sf))).order_by(Fact.created).all()
        self.deck.updateProgress(_("Generating Cards..."))
        ids = []
        for c, fact in enumerate(facts):
            ids.extend(self.deck.addCards(fact, d.selectedCms))
            if c % 50 == 0:
                self.deck.updateProgress()
        self.deck.flushMod()
        self.deck.finishProgress()
        self.deck.setUndoEnd(n)
        self.onSearch()
        self.updateAfterCardChange()

    def cram(self):
        self.close()
        self.mw.onCram(self.selectedCards())

    def onChangeModel(self):
        sf = self.selectedFacts()
        mods = self.deck.db.column0("""
select distinct modelId from facts
where id in %s""" % ids2str(sf))
        if not len(mods) == 1:
            ui.utils.showInfo(
                _("Can only change one model at a time."),
                parent=self)
            return
        d = ChangeModelDialog(self, self.card.fact.model,
                              self.card.cardModel)
        d.exec_()
        if d.ret:
            n = _("Change Model")
            self.deck.setUndoStart(n)
            self.deck.changeModel(sf, *d.ret)
            self.deck.setUndoEnd(n)
            self.onSearch()
            self.updateAfterCardChange()

    # Edit: selection
    ######################################################################

    def selectFacts(self):
        self.deck.startProgress()
        sm = self.form.tableView.selectionModel()
        sm.blockSignals(True)
        cardIds = dict([(x, 1) for x in self.selectedFactsAsCards()])
        for i, card in enumerate(self.model.cards):
            if card[0] in cardIds:
                sm.select(self.model.index(i, 0),
                          QItemSelectionModel.Select | QItemSelectionModel.Rows)
            if i % 100 == 0:
                self.deck.updateProgress()
        sm.blockSignals(False)
        self.deck.finishProgress()
        self.updateFilterLabel()
        self.updateAfterCardChange()

    def invertSelection(self):
        sm = self.form.tableView.selectionModel()
        items = sm.selection()
        self.form.tableView.selectAll()
        sm.select(items, QItemSelectionModel.Deselect | QItemSelectionModel.Rows)

    # Edit: undo
    ######################################################################

    def setupHooks(self):
        addHook("checkpoint", self.onCheckpoint)

    def teardownHooks(self):
        removeHook("checkpoint", self.onCheckpoint)

    def onCheckpoint(self):
        if self.mw.form.actionUndo.isEnabled():
            self.form.actionUndo.setEnabled(True)
            self.form.actionUndo.setText(self.mw.form.actionUndo.text())
        else:
            self.form.actionUndo.setEnabled(False)

    # Edit: font
    ######################################################################

    def onFont(self):
        d = QDialog(self)
        frm = aqt.forms.editfont.Ui_Dialog()
        frm.setupUi(d)
        frm.fontCombo.setCurrentFont(QFont(
            self.mw.config['editFontFamily']))
        frm.fontSize.setValue(self.mw.config['editFontSize'])
        frm.lineSize.setValue(self.mw.config['editLineSize'])
        if d.exec_():
            self.mw.config['editFontFamily'] = (
                unicode(frm.fontCombo.currentFont().family()))
            self.mw.config['editFontSize'] = (
                int(frm.fontSize.value()))
            self.mw.config['editLineSize'] = (
                int(frm.lineSize.value()))
            self.updateFont()

    # Edit: replacing
    ######################################################################

    def onFindReplace(self):
        sf = self.selectedFacts()
        if not sf:
            return
        mods = self.deck.db.column0("""
select distinct modelId from facts
where id in %s""" % ids2str(sf))
        if not len(mods) == 1:
            ui.utils.showInfo(
                _("Can only operate on one model at a time."),
                parent=self)
            return
        d = QDialog(self)
        frm = aqt.forms.findreplace.Ui_Dialog()
        frm.setupUi(d)
        fields = sorted(self.card.fact.model.fieldModels, key=attrgetter("name"))
        frm.field.addItems(QStringList(
            [_("All Fields")] + [f.name for f in fields]))
        self.connect(frm.buttonBox, SIGNAL("helpRequested()"),
                     self.onFindReplaceHelp)
        if not d.exec_():
            return
        n = _("Find and Replace")
        self.deck.startProgress(2)
        self.deck.updateProgress(_("Replacing..."))
        self.deck.setUndoStart(n)
        self.deck.updateProgress()
        changed = None
        try:
            if frm.field.currentIndex() == 0:
                field = None
            else:
                field = fields[frm.field.currentIndex()-1].id
            changed = self.deck.findReplace(sf,
                                            unicode(frm.find.text()),
                                            unicode(frm.replace.text()),
                                            frm.re.isChecked(),
                                            field)
        except sre_constants.error:
            ui.utils.showInfo(_("Invalid regular expression."),
                              parent=self)
        self.deck.setUndoEnd(n)
        self.deck.finishProgress()
        self.mw.reset()
        self.onSearch()
        self.updateAfterCardChange()
        if changed is not None:
            ui.utils.showInfo(ngettext("%(a)d of %(b)d fact updated", "%(a)d of %(b)d facts updated", len(sf)) % {
                'a': changed,
                'b': len(sf),
                }, parent=self)

    def onFindReplaceHelp(self):
        aqt.openHelp("Browser#FindReplace")

    # Edit: finding dupes
    ######################################################################

    def onFindDupes(self):
        win = QDialog(self)
        aqt = ankiqt.forms.finddupes.Ui_Dialog()
        dialog.setupUi(win)
        restoreGeom(win, "findDupes")
        fields = sorted(self.card.fact.model.fieldModels, key=attrgetter("name"))
        # per-model data
        data = self.deck.db.all("""
select fm.id, m.name || '>' || fm.name from fieldmodels fm, models m
where fm.modelId = m.id""")
        data.sort(key=itemgetter(1))
        # all-model data
        data2 = self.deck.db.all("""
select fm.id, fm.name from fieldmodels fm""")
        byName = {}
        for d in data2:
            if d[1] in byName:
                byName[d[1]].append(d[0])
            else:
                byName[d[1]] = [d[0]]
        names = byName.keys()
        names.sort()
        alldata = [(byName[n], n) for n in names] + data
        dialog.searchArea.addItems(QStringList([d[1] for d in alldata]))
        # links
        dialog.webView.page().setLinkDelegationPolicy(
            QWebPage.DelegateAllLinks)
        self.connect(dialog.webView,
                     SIGNAL("linkClicked(QUrl)"),
                     self.dupeLinkClicked)

        def onFin(code):
            saveGeom(win, "findDupes")
        self.connect(win, SIGNAL("finished(int)"), onFin)

        def onClick():
            idx = dialog.searchArea.currentIndex()
            data = alldata[idx]
            if isinstance(data[0], list):
                # all models
                fmids = data[0]
            else:
                # single model
                fmids = [data[0]]
            self.duplicatesReport(dialog.webView, fmids)

        self.connect(dialog.searchButton, SIGNAL("clicked()"),
                     onClick)
        win.show()

    def duplicatesReport(self, web, fmids):
        self.deck.startProgress(2)
        self.deck.updateProgress(_("Finding..."))
        res = self.deck.findDuplicates(fmids)
        t = "<html><body>"
        t += _("Duplicate Groups: %d") % len(res)
        t += "<p><ol>"

        for group in res:
            t += '<li><a href="%s">%s</a>' % (
                "fid:" + ",".join(str(id) for id in group[1]),
                group[0])

        t += "</ol>"
        t += "</body></html>"
        web.setHtml(t)
        self.deck.finishProgress()

    def dupeLinkClicked(self, link):
        self.form.searchEdit.setText(str(link.toString()))
        self.onSearch()
        self.onFact()

    # Jumping
    ######################################################################

    def onFirstCard(self):
        if not self.model.cards:
            return
        self.editor.saveNow()
        self.form.tableView.selectionModel().clear()
        self.form.tableView.selectRow(0)

    def onLastCard(self):
        if not self.model.cards:
            return
        self.editor.saveNow()
        self.form.tableView.selectionModel().clear()
        self.form.tableView.selectRow(len(self.model.cards) - 1)

    def onPreviousCard(self):
        if not self.model.cards:
            return
        self.editor.saveNow()
        row = self.form.tableView.currentIndex().row()
        row = max(0, row - 1)
        self.form.tableView.selectionModel().clear()
        self.form.tableView.selectRow(row)

    def onNextCard(self):
        if not self.model.cards:
            return
        self.editor.saveNow()
        row = self.form.tableView.currentIndex().row()
        row = min(len(self.model.cards) - 1, row + 1)
        self.form.tableView.selectionModel().clear()
        self.form.tableView.selectRow(row)

    def onFind(self):
        self.form.searchEdit.setFocus()
        self.form.searchEdit.selectAll()

    def onFact(self):
        self.editor.focusFirst()

    def onTags(self):
        self.form.tagList.setFocus()

    def onSort(self):
        self.form.sortBox.setFocus()

    def onCardList(self):
        self.form.tableView.setFocus()

    # Help
    ######################################################################

    def onHelp(self):
        aqt.openHelp("Browser")

# Generate card dialog
######################################################################

class AddCardChooser(QDialog):

    def __init__(self, parent, cms):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.cms = cms
        self.form = aqt.forms.addcardmodels.Ui_Dialog()
        self.form.setupUi(self)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.displayCards()
        restoreGeom(self, "addCardModels")

    def displayCards(self):
        self.cms = self.parent.deck.db.all("""
select id, name, active from cardModels
where id in %s
order by ordinal""" % ids2str(self.cms))
        self.items = []
        for cm in self.cms:
            item = QListWidgetItem(cm[1], self.form.list)
            self.form.list.addItem(item)
            self.items.append(item)
            idx = self.form.list.indexFromItem(item)
            if cm[2]:
                mode = QItemSelectionModel.Select
            else:
                mode = QItemSelectionModel.Deselect
            self.form.list.selectionModel().select(idx, mode)

    def accept(self):
        self.selectedCms = []
        for i, item in enumerate(self.items):
            idx = self.form.list.indexFromItem(item)
            if self.form.list.selectionModel().isSelected(idx):
                self.selectedCms.append(self.cms[i][0])
        saveGeom(self, "addCardModels")
        QDialog.accept(self)

    def onHelp(self):
        aqt.openHelp("Browser#GenerateCards")

# Change model dialog
######################################################################

class ChangeModelDialog(QDialog):

    def __init__(self, parent, oldModel, oldTemplate):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.origModel = self.parent.deck.currentModel
        self.oldModel = oldModel
        self.oldTemplate = oldTemplate
        self.form = aqt.forms.changemodel.Ui_Dialog()
        self.form.setupUi(self)
        # maps
        self.fieldMapWidget = None
        self.fieldMapLayout = QHBoxLayout()
        self.form.fieldMap.setLayout(self.fieldMapLayout)
        self.templateMapWidget = None
        self.templateMapLayout = QHBoxLayout()
        self.form.templateMap.setLayout(self.templateMapLayout)
        # model chooser
        self.parent.deck.currentModel = oldModel
        self.form.oldModelLabel.setText(self.oldModel.name)
        self.modelChooser = ui.modelchooser.ModelChooser(self,
                                                         self.parent,
                                                         self.parent.deck,
                                                         self.modelChanged,
                                                         cards=False,
                                                         label=False)
        self.form.modelChooserWidget.setLayout(self.modelChooser)
        self.modelChooser.models.setFocus()
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        restoreGeom(self, "changeModel")
        self.modelChanged(self.oldModel)
        self.ret = None
        self.pauseUpdate = False

    def modelChanged(self, model):
        self.targetModel = model
        # just changing template?
        self.form.fieldMap.setEnabled(self.targetModel != self.oldModel)
        self.rebuildTemplateMap()
        self.rebuildFieldMap()

    def rebuildTemplateMap(self, key=None, attr=None):
        if not key:
            key = "template"
            attr = "cardModels"
        map = getattr(self, key + "MapWidget")
        lay = getattr(self, key + "MapLayout")
        src = getattr(self.oldModel, attr)
        dst = getattr(self.targetModel, attr)
        if map:
            lay.removeWidget(map)
            map.deleteLater()
            setattr(self, key + "MapWidget", None)
        map = QWidget()
        l = QGridLayout()
        combos = []
        targets = [x.name for x in dst] + [_("Nothing")]
        qtargets = QStringList(targets)
        indices = {}
        for i, x in enumerate(src):
            l.addWidget(QLabel(_("Change %s to:") % x.name), i, 0)
            cb = QComboBox()
            cb.addItems(qtargets)
            idx = min(i, len(targets)-1)
            cb.setCurrentIndex(idx)
            indices[cb] = idx
            self.connect(cb, SIGNAL("currentIndexChanged(int)"),
                         lambda i, cb=cb, key=key: self.onComboChanged(i, cb, key))
            combos.append(cb)
            l.addWidget(cb, i, 1)
        map.setLayout(l)
        lay.addWidget(map)
        setattr(self, key + "MapWidget", map)
        setattr(self, key + "MapLayout", lay)
        setattr(self, key + "Combos", combos)
        setattr(self, key + "Indices", indices)

    def rebuildFieldMap(self):
        return self.rebuildTemplateMap(key="field", attr="fieldModels")

    def onComboChanged(self, i, cb, key):
        indices = getattr(self, key + "Indices")
        if self.pauseUpdate:
            indices[cb] = i
            return
        combos = getattr(self, key + "Combos")
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
            old = self.oldModel.cardModels
            combos = self.templateCombos
            new = self.targetModel.cardModels
        map = {}
        for i, f in enumerate(old):
            idx = combos[i].currentIndex()
            if idx == len(new):
                # ignore
                map[f] = None
            else:
                f2 = new[idx]
                if f2 in map.values():
                    return None
                map[f] = f2
        return map

    def getFieldMap(self):
        return self.getTemplateMap(
            old=self.oldModel.fieldModels,
            combos=self.fieldCombos,
            new=self.targetModel.fieldModels)

    def reject(self):
        self.parent.deck.currentModel = self.origModel
        self.modelChooser.deinit()
        return QDialog.reject(self)

    def accept(self):
        saveGeom(self, "changeModel")
        self.parent.deck.currentModel = self.origModel
        # check maps
        fmap = self.getFieldMap()
        cmap = self.getTemplateMap()
        if not cmap or (self.targetModel != self.oldModel and
                        not fmap):
            ui.utils.showInfo(
                _("Targets must be unique."), parent=self)
            return
        if [c for c in cmap.values() if not c]:
            if not ui.utils.askUser(_("""\
Any cards with templates mapped to nothing will be deleted.
If a fact has no remaining cards, it will be lost.
Are you sure you want to continue?"""), parent=self):
                return
        self.modelChooser.deinit()
        if self.targetModel == self.oldModel:
            self.ret = (self.targetModel, None, cmap)
            return QDialog.accept(self)
        self.ret = (self.targetModel, fmap, cmap)
        return QDialog.accept(self)

    def onHelp(self):
        aqt.openHelp("Browser#ChangeModel")
