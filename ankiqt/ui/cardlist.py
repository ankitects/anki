# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import sre_constants
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import time, types, sys, re
from operator import attrgetter
import anki, anki.utils, ankiqt.forms
from ankiqt import ui
from anki.cards import cardsTable, Card
from anki.facts import factsTable, fieldsTable, Fact
from anki.utils import fmtTimeSpan, parseTags, findTag, addTags, deleteTags, \
     stripHTML, ids2str
from ankiqt.ui.utils import saveGeom, restoreGeom, saveSplitter, restoreSplitter
from ankiqt.ui.utils import saveHeader, restoreHeader
from anki.errors import *
from anki.db import *
from anki.stats import CardStats
from anki.hooks import runHook, addHook, removeHook

CARD_ID = 0
CARD_QUESTION = 1
CARD_ANSWER = 2
CARD_DUE = 3
CARD_REPS = 4
CARD_FACTID = 5
CARD_CREATED = 6
CARD_MODIFIED = 7
CARD_INTERVAL = 8
CARD_EASE = 9
CARD_PRIORITY = 10

# Deck editor
##########################################################################

class DeckModel(QAbstractTableModel):

    def __init__(self, parent, deck):
        QAbstractTableModel.__init__(self)
        self.parent = parent
        self.deck = deck
        self.filterTag = None
        self.sortKey = None
        # column title, display accessor, sort attr
        self.columns = [(_("Question"), self.currentQuestion),
                        (_("Answer"), self.currentAnswer),
                        [_("Due"), self.thirdColumn],
                        ]
        self.searchStr = ""
        self.lastSearch = ""
        self.cards = []
        self.deleted = {}

    # Model interface
    ######################################################################

    def rowCount(self, index):
        return len(self.cards)

    def columnCount(self, index):
        return 3

    def data(self, index, role):
        if not index.isValid():
            return QVariant()
        if role == Qt.FontRole:
            f = QFont()
            f.setPixelSize(self.parent.config['editFontSize'])
            return QVariant(f)
        elif role == Qt.DisplayRole or role == Qt.EditRole:
            if len(self.cards[index.row()]) == 1:
                # not cached yet
                self.updateCard(index)
            s = self.columns[index.column()][1](index)
            s = s.replace("<br>", u" ")
            s = s.replace("<br />", u" ")
            s = s.replace("\n", u"  ")
            s = stripHTML(s)
            s = re.sub("\[sound:[^]]+\]", "", s)
            s = s.strip()
            return QVariant(s)
        else:
            return QVariant()

    def headerData(self, section, orientation, role):
        if orientation == Qt.Vertical:
            return QVariant()
        elif role == Qt.DisplayRole:
            return QVariant(self.columns[section][0])
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
        if not self.sortKey:
            self.cards = []
            return
        # sorting
        if not self.searchStr:
            ads = ""
            self.lastSearch = ""
        else:
            if (self.searchStr.strip() == self.lastSearch.strip()
                and not force):
                # just whitespace
                return
            QApplication.instance().processEvents()
            self.lastSearch = self.searchStr
            ids = self.deck.findCards(self.searchStr)
            ads = "cards.id in %s" % ids2str(ids)
        sort = ""
        if isinstance(self.sortKey, types.StringType):
            # card property
            if self.sortKey == "fact":
                sort = "order by facts.created, cards.created"
            else:
                sort = "order by cards." + self.sortKey
            if self.sortKey in ("question", "answer"):
                sort += " collate nocase"
            if self.sortKey == "fact":
                query = """
select cards.id from cards, facts
where cards.factId = facts.id """
                if ads:
                    query += "and " + ads + " "
            else:
                query = "select id from cards "
                if ads:
                    query += "where %s " % ads
            query += sort
        else:
            # field value
            ret = self.deck.s.all(
                "select id, numeric from fieldModels where name = :name",
                name=self.sortKey[1])
            fields = ",".join([str(x[0]) for x in ret])
            # if multiple models have the same field, use the first numeric bool
            numeric = ret[0][1]
            if numeric:
                order = "cast(fields.value as real)"
            else:
                order = "fields.value collate nocase"
            if ads:
                ads = " and " + ads
            query = ("select cards.id "
                     "from fields, cards where fields.fieldModelId in (%s) "
                     "and fields.factId = cards.factId" + ads +
                     " order by cards.ordinal, %s") % (fields, order)
        # run the query
        self.cards = self.deck.s.all(query)
        if self.deck.getInt('reverseOrder'):
            self.cards.reverse()
        self.reset()

    def updateCard(self, index):
        try:
            self.cards[index.row()] = self.deck.s.first("""
    select id, question, answer, due, reps, factId, created, modified,
    interval, factor, priority from cards where id = :id""",
                                                        id=self.cards[index.row()][0])
            self.emit(SIGNAL("dataChanged(QModelIndex,QModelIndex)"),
                      index, self.index(index.row(), 1))
        except:
            # called after search changed
            pass

    # Tools
    ######################################################################

    def getCardID(self, index):
        return self.cards[index.row()][0]

    def getCard(self, index):
        try:
            return self.deck.s.query(Card).get(self.getCardID(index))
        except IndexError:
            return None

    def cardIndex(self, card):
        "Return the index of CARD, if currently displayed."
        return self.cards.index(card)

    def currentQuestion(self, index):
        return self.cards[index.row()][CARD_QUESTION]

    def currentAnswer(self, index):
        return self.cards[index.row()][CARD_ANSWER]

    def nextDue(self, index):
        d = self.cards[index.row()][CARD_DUE]
        reps = self.cards[index.row()][CARD_REPS]
        secs = d - time.time()
        if secs <= 0:
            if not reps:
                return _("(new card)")
            else:
                return _("%s ago") % fmtTimeSpan(abs(secs), pad=0)
        else:
            return _("in %s") % fmtTimeSpan(secs, pad=0)

    def thirdColumn(self, index):
        if self.sortKey == "created":
            return self.createdColumn(index)
        elif self.sortKey == "modified":
            return self.modifiedColumn(index)
        elif self.sortKey == "interval":
            return self.intervalColumn(index)
        elif self.sortKey == "reps":
            return self.repsColumn(index)
        elif self.sortKey == "factor":
            return self.easeColumn(index)
        else:
            return self.nextDue(index)

    def updateHeader(self):
        if self.sortKey == "created":
            k = _("Created")
        elif self.sortKey == "modified":
            k = _("Modified")
        elif self.sortKey == "interval":
            k = _("Interval")
        elif self.sortKey == "reps":
            k = _("Reps")
        elif self.sortKey == "factor":
            k = _("Ease")
        else:
            k = _("Due")
        self.columns[-1][0] = k

    def createdColumn(self, index):
        return time.strftime("%Y-%m-%d", time.localtime(
            self.cards[index.row()][CARD_CREATED]))

    def modifiedColumn(self, index):
        return time.strftime("%Y-%m-%d", time.localtime(
            self.cards[index.row()][CARD_MODIFIED]))

    def intervalColumn(self, index):
        return fmtTimeSpan(
            self.cards[index.row()][CARD_INTERVAL]*86400)

    def repsColumn(self, index):
        return str(self.cards[index.row()][CARD_REPS])

    def easeColumn(self, index):
        return "%0.2f" % self.cards[index.row()][CARD_EASE]

class StatusDelegate(QItemDelegate):

    def __init__(self, parent, model):
        QItemDelegate.__init__(self, parent)
        self.model = model

    def paint(self, painter, option, index):
        if len(self.model.cards[index.row()]) == 1:
            self.model.updateCard(index)
        row = self.model.cards[index.row()]
        if row[CARD_PRIORITY] in (-3, 0):
            # custom render
            if index.row() % 2 == 0:
                brush = QBrush(QColor("#ffffcc"))
            else:
                brush = QBrush(QColor("#ffffaa"))
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()
        return QItemDelegate.paint(self, painter, option, index)

class EditDeck(QMainWindow):

    def __init__(self, parent):
        if parent.config['standaloneWindows']:
            windParent = None
        else:
            windParent = parent
        QMainWindow.__init__(self, windParent)
        self.parent = parent
        self.deck = self.parent.deck
        self.config = parent.config
        self.forceClose = False
        self.origModTime = parent.deck.modified
        self.currentRow = None
        self.lastFilter = ""
        self.dialog = ankiqt.forms.cardlist.Ui_MainWindow()
        self.dialog.setupUi(self)
        restoreGeom(self, "editor")
        restoreSplitter(self.dialog.splitter, "editor")
        # flush all changes before we load
        self.deck.s.flush()
        self.model = DeckModel(self.parent, self.parent.deck)
        self.dialog.tableView.setSortingEnabled(False)
        self.dialog.tableView.setShowGrid(False)
        self.dialog.tableView.setModel(self.model)
        self.dialog.tableView.selectionModel()
        self.connect(self.dialog.tableView.selectionModel(),
                     SIGNAL("selectionChanged(QItemSelection,QItemSelection)"),
                     self.updateFilterLabel)
        self.dialog.tableView.setItemDelegate(StatusDelegate(self, self.model))
        if self.deck.getInt("reverseOrder"):
            self.dialog.actionReverseOrder.setChecked(True)
        self.updateFont()
        self.setupMenus()
        self.setupFilter()
        self.setupSort()
        self.setupHeaders()
        self.setupHooks()
        self.setupEditor()
        self.setupCardInfo()
        self.dialog.filterEdit.setFocus()
        ui.dialogs.open("CardList", self)
        self.drawTags()
        self.updateFilterLabel()
        self.show()
        if self.parent.currentCard:
            self.currentCard = self.parent.currentCard
        self.updateSearch()
        if sys.platform.startswith("darwin"):
            self.macCloseShortcut = QShortcut(QKeySequence("Ctrl+w"), self)
            self.connect(self.macCloseShortcut, SIGNAL("activated()"),
                         self.close)

    def findCardInDeckModel(self):
        for i, thisCard in enumerate(self.model.cards):
            if thisCard[0] == self.currentCard.id:
                return i
        return -1

    def updateFont(self):
        self.dialog.tableView.setFont(QFont(
            self.config['editFontFamily'],
            self.config['editFontSize']))
        self.dialog.tableView.verticalHeader().setDefaultSectionSize(
            self.parent.config['editLineSize'])
        self.model.reset()

    def setupFilter(self):
        self.filterTimer = None
        self.connect(self.dialog.filterEdit,
                     SIGNAL("textChanged(QString)"),
                     self.filterTextChanged)
        self.connect(self.dialog.filterEdit,
                     SIGNAL("returnPressed()"),
                     self.showFilterNow)
        self.setTabOrder(self.dialog.filterEdit, self.dialog.tableView)
        self.connect(self.dialog.tagList, SIGNAL("activated(int)"),
                     self.tagChanged)

    def setupSort(self):
        self.dialog.sortBox.setMaxVisibleItems(30)
        self.sortIndex = self.deck.getInt("sortIndex") or 0
        self.drawSort()
        self.connect(self.dialog.sortBox, SIGNAL("activated(int)"),
                     self.sortChanged)
        self.sortChanged(self.sortIndex, refresh=False)

    def drawTags(self):
        self.dialog.tagList.view().setFixedWidth(200)
        self.dialog.tagList.setMaxVisibleItems(30)
        self.dialog.tagList.setFixedWidth(130)
        self.dialog.tagList.clear()
        alltags = [None, "Marked", None, None, "Leech", None, None]
        # system tags
        self.dialog.tagList.addItem(_("<Filter>"))
        self.dialog.tagList.addItem(QIcon(":/icons/rating.png"),
                                    _('Marked'))
        self.dialog.tagList.addItem(QIcon(":/icons/media-playback-pause.png"),
                                    _('Suspended'))
        self.dialog.tagList.addItem(QIcon(":/icons/chronometer.png"),
                                    _('Due'))
        self.dialog.tagList.addItem(QIcon(":/icons/emblem-important.png"),
                                    _('Leech'))
        self.dialog.tagList.addItem(QIcon(":/icons/editclear.png"),
                                    _('No fact tags'))
        self.dialog.tagList.insertSeparator(
            self.dialog.tagList.count())
        # model and card templates
        for (type, sql, icon) in (
            ("models", "select tags from models", "contents.png"),
            ("cms", "select name from cardModels", "Anki_Card.png")):
            d = {}
            tagss = self.deck.s.column0(sql)
            for tags in tagss:
                for tag in parseTags(tags):
                    d[tag] = 1
            sortedtags = sorted(d.keys())
            alltags.extend(sortedtags)
            icon = QIcon(":/icons/" + icon)
            for t in sortedtags:
                self.dialog.tagList.addItem(icon, t.replace("_", " "))
            if sortedtags:
                self.dialog.tagList.insertSeparator(
                    self.dialog.tagList.count())
                alltags.append(None)
        # fact tags
        alluser = sorted(self.deck.allTags())
        for tag in alltags:
            try:
                alluser.remove(tag)
            except:
                pass
        icon = QIcon(":/icons/Anki_Fact.png")
        for t in alluser:
            t = t.replace("_", " ")
            self.dialog.tagList.addItem(icon, t)
        alltags.extend(alluser)
        self.alltags = alltags

    def drawSort(self):
        self.sortList = [
            _("Question"),
            _("Answer"),
            _("Created"),
            _("Modified"),
            _("Due"),
            _("Interval"),
            _("Reps"),
            _("Ease"),
            _("Fact Created"),
            ]
        self.sortFields = sorted(self.deck.allFields())
        self.sortList.extend([_("'%s'") % f for f in self.sortFields])
        self.dialog.sortBox.clear()
        self.dialog.sortBox.addItems(QStringList(self.sortList))
        if self.sortIndex >= len(self.sortList):
            self.sortIndex = 0
        self.dialog.sortBox.setCurrentIndex(self.sortIndex)

    def sortChanged(self, idx, refresh=True):
        if idx == 0:
            self.sortKey = "question"
        elif idx == 1:
            self.sortKey = "answer"
        elif idx == 2:
            self.sortKey = "created"
        elif idx == 3:
            self.sortKey = "modified"
        elif idx == 4:
            self.sortKey = "due"
        elif idx == 5:
            self.sortKey = "interval"
        elif idx == 6:
            self.sortKey = "reps"
        elif idx == 7:
            self.sortKey = "factor"
        elif idx == 8:
            self.sortKey = "fact"
        else:
            self.sortKey = ("field", self.sortFields[idx-9])
        self.rebuildSortIndex(self.sortKey)
        self.sortIndex = idx
        self.deck.setVar('sortIndex', idx)
        self.model.sortKey = self.sortKey
        self.model.updateHeader()
        if refresh:
            self.model.showMatching()
            self.updateFilterLabel()
            self.onEvent()
            self.focusCurrentCard()

    def rebuildSortIndex(self, key):
        if key not in (
            "question", "answer", "created", "modified", "due", "interval",
            "reps", "factor"):
            return
        old = self.deck.s.scalar("select sql from sqlite_master where name = :k",
                                 k="ix_cards_sort")
        if old and key in old:
            return
        self.parent.setProgressParent(self)
        self.deck.startProgress(2)
        self.deck.updateProgress(_("Building Index..."))
        self.deck.s.statement("drop index if exists ix_cards_sort")
        self.deck.updateProgress()
        if key in ("question", "answer"):
            key = key + " collate nocase"
        self.deck.s.statement(
            "create index ix_cards_sort on cards (%s)" % key)
        self.deck.s.statement("analyze")
        self.deck.finishProgress()
        self.parent.setProgressParent(None)

    def tagChanged(self, idx):
        if idx == 0:
            filter = ""
        elif idx == 1:
            filter = "tag:marked"
        elif idx == 2:
            filter = "is:suspended"
        elif idx == 3:
            filter = "is:due"
        elif idx == 4:
            filter = "is:suspended tag:leech"
        elif idx == 5:
            filter = "tag:none"
        else:
            filter = "tag:" + self.alltags[idx]
        self.lastFilter = filter
        self.dialog.filterEdit.setText(filter)
        self.showFilterNow()

    def updateFilterLabel(self):
        selected = len(self.dialog.tableView.selectionModel().selectedRows())
        self.setWindowTitle(ngettext("Browser (%(cur)d "
                              "of %(tot)d card shown; %(sel)s)", "Browser (%(cur)d "
                              "of %(tot)d cards shown; %(sel)s)", self.deck.cardCount) %
                            {
            "cur": len(self.model.cards),
            "tot": self.deck.cardCount,
            "sel": ngettext("%d selected", "%d selected", selected) % selected
            } + " - " + self.parent.deck.name())

    def onEvent(self, type='field'):
        if self.deck.undoAvailable():
            self.dialog.actionUndo.setText(_("Undo %s") %
                                           self.deck.undoName())
            self.dialog.actionUndo.setEnabled(True)
        else:
            self.dialog.actionUndo.setEnabled(False)
        if self.deck.redoAvailable():
            self.dialog.actionRedo.setText(_("Redo %s") %
                                            self.deck.redoName())
            self.dialog.actionRedo.setEnabled(True)
        else:
            self.dialog.actionRedo.setEnabled(False)
        # update list
        if self.currentRow and self.model.cards:
            self.model.updateCard(self.currentRow)
        if type == "tag":
            self.drawTags()

    def filterTextChanged(self):
        interval = 300
        # update filter dropdown
        if (self.lastFilter.lower()
            not in unicode(self.dialog.filterEdit.text()).lower()):
            self.dialog.tagList.setCurrentIndex(0)
        if self.filterTimer:
            self.filterTimer.setInterval(interval)
        else:
            self.filterTimer = QTimer(self)
            self.filterTimer.setSingleShot(True)
            self.filterTimer.start(interval)
            self.connect(self.filterTimer, SIGNAL("timeout()"),
                         lambda: self.updateSearch(force=False))

    def showFilterNow(self):
        if self.filterTimer:
            self.filterTimer.stop()
        self.updateSearch()

    def updateSearch(self, force=True):
        if self.parent.inDbHandler:
            return
        self.model.searchStr = unicode(self.dialog.filterEdit.text())
        self.model.showMatching(force)
        self.updateFilterLabel()
        self.onEvent()
        self.filterTimer = None
        if self.model.cards:
            self.dialog.cardInfoGroup.show()
            self.dialog.fieldsArea.show()
        else:
            self.dialog.cardInfoGroup.hide()
            self.dialog.fieldsArea.hide()
        if not self.focusCurrentCard():
            if self.model.cards:
                self.dialog.tableView.selectRow(0)
        if not self.model.cards:
            self.editor.setFact(None)

    def focusCurrentCard(self):
        if self.currentCard:
            try:
                self.currentCard.id
            except:
                return False
            currentCardIndex = self.findCardInDeckModel()
            if currentCardIndex >= 0:
                sm = self.dialog.tableView.selectionModel()
                sm.clear()
                self.dialog.tableView.selectRow(currentCardIndex)
                self.dialog.tableView.scrollTo(
                              self.model.index(currentCardIndex,0),
                              self.dialog.tableView.PositionAtCenter)
                return True
        return False

    def setupHeaders(self):
        if not sys.platform.startswith("win32"):
            self.dialog.tableView.verticalHeader().hide()
            self.dialog.tableView.horizontalHeader().show()
        restoreHeader(self.dialog.tableView.horizontalHeader(), "editor")
        for i in range(2):
            self.dialog.tableView.horizontalHeader().setResizeMode(i, QHeaderView.Stretch)
        self.dialog.tableView.horizontalHeader().setResizeMode(2, QHeaderView.Interactive)

    def setupMenus(self):
        # actions
        self.connect(self.dialog.actionDelete, SIGNAL("triggered()"), self.deleteCards)
        self.connect(self.dialog.actionAddTag, SIGNAL("triggered()"), self.addTags)
        self.connect(self.dialog.actionDeleteTag, SIGNAL("triggered()"), self.deleteTags)
        self.connect(self.dialog.actionReschedule, SIGNAL("triggered()"), self.reschedule)
        self.connect(self.dialog.actionCram, SIGNAL("triggered()"), self.cram)
        self.connect(self.dialog.actionAddCards, SIGNAL("triggered()"), self.addCards)
        self.connect(self.dialog.actionChangeModel, SIGNAL("triggered()"), self.onChangeModel)
        self.connect(self.dialog.actionSuspend, SIGNAL("triggered()"), self.onSuspend)
        self.connect(self.dialog.actionUnsuspend, SIGNAL("triggered()"), self.onUnsuspend)
        # edit
        self.connect(self.dialog.actionFont, SIGNAL("triggered()"), self.onFont)
        self.connect(self.dialog.actionUndo, SIGNAL("triggered()"), self.onUndo)
        self.connect(self.dialog.actionRedo, SIGNAL("triggered()"), self.onRedo)
        self.connect(self.dialog.actionInvertSelection, SIGNAL("triggered()"), self.invertSelection)
        self.connect(self.dialog.actionReverseOrder, SIGNAL("triggered()"), self.reverseOrder)
        self.connect(self.dialog.actionSelectFacts, SIGNAL("triggered()"), self.selectFacts)
        self.connect(self.dialog.actionFindReplace, SIGNAL("triggered()"), self.onFindReplace)
        # jumps
        self.connect(self.dialog.actionFirstCard, SIGNAL("triggered()"), self.onFirstCard)
        self.connect(self.dialog.actionLastCard, SIGNAL("triggered()"), self.onLastCard)
        self.connect(self.dialog.actionPreviousCard, SIGNAL("triggered()"), self.onPreviousCard)
        self.connect(self.dialog.actionNextCard, SIGNAL("triggered()"), self.onNextCard)
        self.connect(self.dialog.actionFind, SIGNAL("triggered()"), self.onFind)
        self.connect(self.dialog.actionFact, SIGNAL("triggered()"), self.onFact)
        self.connect(self.dialog.actionTags, SIGNAL("triggered()"), self.onTags)
        self.connect(self.dialog.actionSort, SIGNAL("triggered()"), self.onSort)
        # help
        self.connect(self.dialog.actionGuide, SIGNAL("triggered()"), self.onHelp)
        runHook('editor.setupMenus', self)

    def onClose(self):
        saveSplitter(self.dialog.splitter, "editor")
        self.editor.saveFieldsNow()
        if not self.forceClose:
            if not self.factValid:
                ui.utils.showInfo(_(
                    "Some fields are missing or not unique."),
                                  parent=self, help="AddItems#AddError")
                return
        self.editor.setFact(None)
        self.editor.close()
        saveGeom(self, "editor")
        saveHeader(self.dialog.tableView.horizontalHeader(), "editor")
        self.hide()
        ui.dialogs.close("CardList")
        if self.parent.currentCard:
            self.parent.moveToState("showQuestion")
        else:
            self.parent.moveToState("auto")
        self.teardownHooks()
        return True

    def closeEvent(self, evt):
        if self.onClose():
            evt.accept()
        else:
            evt.ignore()

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if evt.key() in (Qt.Key_Escape,):
            self.close()

    # Editor
    ######################################################################

    def setupEditor(self):
        self.editor = ui.facteditor.FactEditor(self,
                                               self.dialog.fieldsArea,
                                               self.deck)
        self.factValid = True
        self.editor.onFactValid = self.onFactValid
        self.editor.onFactInvalid = self.onFactInvalid
        self.editor.onChange = self.onEvent
        self.connect(self.dialog.tableView.selectionModel(),
                     SIGNAL("currentRowChanged(QModelIndex, QModelIndex)"),
                     self.rowChanged)

    def onFactValid(self, fact):
        self.factValid = True
        self.dialog.tableView.setEnabled(True)
        self.dialog.filterEdit.setEnabled(True)
        self.dialog.sortBox.setEnabled(True)
        self.dialog.tagList.setEnabled(True)
        self.dialog.menubar.setEnabled(True)
        self.dialog.cardInfoGroup.setEnabled(True)

    def onFactInvalid(self, fact):
        self.factValid = False
        self.dialog.tableView.setEnabled(False)
        self.dialog.filterEdit.setEnabled(False)
        self.dialog.sortBox.setEnabled(False)
        self.dialog.tagList.setEnabled(False)
        self.dialog.menubar.setEnabled(False)
        self.dialog.cardInfoGroup.setEnabled(False)

    def rowChanged(self, current, previous):
        self.currentRow = current
        self.currentCard = self.model.getCard(current)
        if not self.currentCard:
            self.editor.setFact(None, True)
            return
        fact = self.currentCard.fact
        self.editor.setFact(fact, True)
        self.showCardInfo(self.currentCard)
        self.onEvent()

    def setupCardInfo(self):
        self.currentCard = None
        self.cardStats = CardStats(self.deck, None)

    def showCardInfo(self, card):
        self.cardStats.card = self.currentCard
        self.dialog.cardLabel.setText(
            self.cardStats.report())

    # Menu helpers
    ######################################################################

    def selectedCards(self):
        return [self.model.cards[idx.row()][0] for idx in
                self.dialog.tableView.selectionModel().selectedRows()]

    def selectedFacts(self):
        return self.deck.s.column0("""
select distinct factId from cards
where id in (%s)""" % ",".join([
            str(self.model.cards[idx.row()][0]) for idx in
            self.dialog.tableView.selectionModel().selectedRows()]))

    def selectedFactsAsCards(self):
        return self.deck.s.column0(
            "select id from cards where factId in (%s)" %
            ",".join([str(s) for s in self.selectedFacts()]))

    def updateAfterCardChange(self, reset=False):
        "Refresh info like stats on current card"
        self.currentRow = self.dialog.tableView.currentIndex()
        self.rowChanged(self.currentRow, None)
        if reset:
            self.updateSearch()
        self.drawTags()
        self.parent.moveToState("auto")

    # Menu options
    ######################################################################

    def deleteCards(self):
        cards = self.selectedCards()
        n = _("Delete Cards")
        new = self.findCardInDeckModel() + 1
        self.dialog.tableView.setFocus()
        self.deck.setUndoStart(n)
        self.deck.deleteCards(cards)
        self.deck.setUndoEnd(n)
        new = min(max(0, new), len(self.model.cards) - 1)
        self.dialog.tableView.selectRow(new)
        self.updateSearch()
        self.updateAfterCardChange()

    def addTags(self):
        (tags, r) = ui.utils.getTag(self, self.deck, _("Enter tags to add:"))
        if tags:
            n = _("Add Tags")
            self.parent.setProgressParent(self)
            self.deck.setUndoStart(n)
            self.deck.addTags(self.selectedFacts(), tags)
            self.deck.setUndoEnd(n)
            self.parent.setProgressParent(None)
        self.updateAfterCardChange(reset=True)

    def deleteTags(self):
        (tags, r) = ui.utils.getTag(self, self.deck, _("Enter tags to delete:"))
        if tags:
            n = _("Delete Tags")
            self.parent.setProgressParent(self)
            self.deck.setUndoStart(n)
            self.deck.deleteTags(self.selectedFacts(), tags)
            self.deck.setUndoEnd(n)
            self.parent.setProgressParent(None)
        self.updateAfterCardChange(reset=True)

    def onSuspend(self):
        n = _("Suspend")
        self.parent.setProgressParent(self)
        self.deck.setUndoStart(n)
        self.deck.suspendCards(self.selectedCards())
        self.deck.setUndoEnd(n)
        self.parent.setProgressParent(None)
        self.updateAfterCardChange(reset=True)

    def onUnsuspend(self):
        n = _("Unsuspend")
        self.parent.setProgressParent(self)
        self.deck.setUndoStart(n)
        self.deck.unsuspendCards(self.selectedCards())
        self.deck.setUndoEnd(n)
        self.parent.setProgressParent(None)
        self.updateAfterCardChange(reset=True)

    def reschedule(self):
        n = _("Reschedule")
        d = QDialog(self)
        frm = ankiqt.forms.reschedule.Ui_Dialog()
        frm.setupUi(d)
        if not d.exec_():
            return
        self.deck.setUndoStart(n)
        try:
            if frm.asNew.isChecked():
                self.deck.resetCards(self.selectedCards())
            else:
                try:
                    min = float(str(frm.rangeMin.text()))
                    max = float(str(frm.rangeMax.text()))
                except ValueError:
                    ui.utils.showInfo(
                        _("Please enter a valid start and end range."),
                        parent=self)
                    return
                self.deck.rescheduleCards(self.selectedCards(), min, max)
        finally:
            self.deck.rebuildCounts(full=False)
            self.deck.rebuildQueue()
            self.deck.setUndoEnd(n)
        self.updateAfterCardChange(reset=True)

    def addCards(self):
        sf = self.selectedFacts()
        if not sf:
            return
        mods = self.deck.s.column0("""
select distinct modelId from facts
where id in %s""" % ids2str(sf))
        if not len(mods) == 1:
            ui.utils.showInfo(
                _("Can only operate on one model at a time."),
                parent=self)
            return
        # get cards to enable
        cms = [x.id for x in self.deck.s.query(Fact).get(sf[0]).\
               model.cardModels]
        d = AddCardChooser(self, cms)
        if not d.exec_():
            return
        # for each fact id, generate
        n = _("Generate Cards")
        self.parent.setProgressParent(self)
        self.deck.startProgress()
        self.deck.setUndoStart(n)
        facts = self.deck.s.query(Fact).filter(
            text("id in %s" % ids2str(sf))).order_by(Fact.created).all()
        self.deck.updateProgress(_("Generating Cards..."))
        for c, fact in enumerate(facts):
            self.deck.addCards(fact, d.selectedCms)
            if c % 50 == 0:
                self.deck.updateProgress()
        self.deck.flushMod()
        self.deck.updateAllPriorities()
        self.deck.finishProgress()
        self.parent.setProgressParent(None)
        self.deck.setUndoEnd(n)
        self.updateSearch()
        self.updateAfterCardChange()

    def cram(self):
        if ui.utils.askUser(
            _("Cram selected cards in new deck?"),
            help="CramMode",
            parent=self):
            self.close()
            self.parent.onCram(self.selectedCards())

    def onChangeModel(self):
        sf = self.selectedFacts()
        mods = self.deck.s.column0("""
select distinct modelId from facts
where id in %s""" % ids2str(sf))
        if not len(mods) == 1:
            ui.utils.showInfo(
                _("Can only change one model at a time."),
                parent=self)
            return
        d = ChangeModelDialog(self, self.currentCard.fact.model,
                              self.currentCard.cardModel)
        d.exec_()
        if d.ret:
            n = _("Change Model")
            self.parent.setProgressParent(self)
            self.deck.setUndoStart(n)
            self.deck.changeModel(sf, *d.ret)
            self.deck.setUndoEnd(n)
            self.parent.setProgressParent(None)
            self.updateSearch()
            self.updateAfterCardChange()

    # Edit: selection
    ######################################################################

    def selectFacts(self):
        sm = self.dialog.tableView.selectionModel()
        cardIds = dict([(x, 1) for x in self.selectedFactsAsCards()])
        for i, card in enumerate(self.model.cards):
            if card.id in cardIds:
                sm.select(self.model.index(i, 0),
                          QItemSelectionModel.Select | QItemSelectionModel.Rows)


    def invertSelection(self):
        sm = self.dialog.tableView.selectionModel()
        items = sm.selection()
        self.dialog.tableView.selectAll()
        sm.select(items, QItemSelectionModel.Deselect | QItemSelectionModel.Rows)

    def reverseOrder(self):
        self.deck.setVar("reverseOrder", not self.deck.getInt("reverseOrder"))
        self.model.cards.reverse()
        self.model.reset()
        self.focusCurrentCard()

    # Edit: undo/redo
    ######################################################################

    def setupHooks(self):
        addHook("postUndoRedo", self.postUndoRedo)
        addHook("currentCardDeleted", self.updateSearch)

    def teardownHooks(self):
        removeHook("postUndoRedo", self.postUndoRedo)
        removeHook("currentCardDeleted", self.updateSearch)

    def postUndoRedo(self):
        self.updateFilterLabel()
        self.updateSearch()
        self.updateAfterCardChange()

    def onUndo(self):
        self.deck.undo()

    def onRedo(self):
        self.deck.redo()

    # Edit: font
    ######################################################################

    def onFont(self):
        d = QDialog(self)
        frm = ankiqt.forms.editfont.Ui_Dialog()
        frm.setupUi(d)
        frm.fontCombo.setCurrentFont(QFont(
            self.parent.config['editFontFamily']))
        frm.fontSize.setValue(self.parent.config['editFontSize'])
        frm.lineSize.setValue(self.parent.config['editLineSize'])
        if d.exec_():
            self.parent.config['editFontFamily'] = (
                unicode(frm.fontCombo.currentFont().family()))
            self.parent.config['editFontSize'] = (
                int(frm.fontSize.value()))
            self.parent.config['editLineSize'] = (
                int(frm.lineSize.value()))
            self.updateFont()

    # Edit: replacing
    ######################################################################

    def onFindReplace(self):
        sf = self.selectedFacts()
        if not sf:
            return
        mods = self.deck.s.column0("""
select distinct modelId from facts
where id in %s""" % ids2str(sf))
        if not len(mods) == 1:
            ui.utils.showInfo(
                _("Can only operate on one model at a time."),
                parent=self)
            return
        d = QDialog(self)
        frm = ankiqt.forms.findreplace.Ui_Dialog()
        frm.setupUi(d)
        fields = sorted(self.currentCard.fact.model.fieldModels, key=attrgetter("name"))
        frm.field.addItems(QStringList(
            [_("All Fields")] + [f.name for f in fields]))
        self.connect(frm.buttonBox, SIGNAL("helpRequested()"),
                     self.onFindReplaceHelp)
        if not d.exec_():
            return
        n = _("Find and Replace")
        self.parent.setProgressParent(self)
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
        self.parent.setProgressParent(None)
        self.parent.reset()
        self.updateSearch()
        self.updateAfterCardChange()
        if changed is not None:
            ui.utils.showInfo(ngettext("%(a)d of %(b)d fact updated", "%(a)d of %(b)d facts updated", len(sf)) % {
                'a': changed,
                'b': len(sf),
                }, parent=self)

    def onFindReplaceHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "Browser#FindReplace"))

    # Jumping
    ######################################################################

    def onFirstCard(self):
        if not self.model.cards:
            return
        self.editor.saveFieldsNow()
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(0)

    def onLastCard(self):
        if not self.model.cards:
            return
        self.editor.saveFieldsNow()
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(len(self.model.cards) - 1)

    def onPreviousCard(self):
        if not self.model.cards:
            return
        self.editor.saveFieldsNow()
        row = self.dialog.tableView.currentIndex().row()
        row = max(0, row - 1)
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(row)

    def onNextCard(self):
        if not self.model.cards:
            return
        self.editor.saveFieldsNow()
        row = self.dialog.tableView.currentIndex().row()
        row = min(len(self.model.cards) - 1, row + 1)
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(row)

    def onFind(self):
        self.dialog.filterEdit.setFocus()
        self.dialog.filterEdit.selectAll()

    def onFact(self):
        self.editor.focusFirst()

    def onTags(self):
        self.dialog.tagList.setFocus()

    def onSort(self):
        self.dialog.sortBox.setFocus()

    # Help
    ######################################################################

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "Browser"))

# Generate card dialog
######################################################################

class AddCardChooser(QDialog):

    def __init__(self, parent, cms):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.cms = cms
        self.dialog = ankiqt.forms.addcardmodels.Ui_Dialog()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.displayCards()
        restoreGeom(self, "addCardModels")

    def displayCards(self):
        self.cms = self.parent.deck.s.all("""
select id, name, active from cardModels
where id in %s
order by ordinal""" % ids2str(self.cms))
        self.items = []
        for cm in self.cms:
            item = QListWidgetItem(cm[1], self.dialog.list)
            self.dialog.list.addItem(item)
            self.items.append(item)
            idx = self.dialog.list.indexFromItem(item)
            if cm[2]:
                mode = QItemSelectionModel.Select
            else:
                mode = QItemSelectionModel.Deselect
            self.dialog.list.selectionModel().select(idx, mode)

    def accept(self):
        self.selectedCms = []
        for i, item in enumerate(self.items):
            idx = self.dialog.list.indexFromItem(item)
            if self.dialog.list.selectionModel().isSelected(idx):
                self.selectedCms.append(self.cms[i][0])
        saveGeom(self, "addCardModels")
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "Browser#GenerateCards"))

# Change model dialog
######################################################################

class ChangeModelDialog(QDialog):

    def __init__(self, parent, oldModel, oldTemplate):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.origModel = self.parent.deck.currentModel
        self.oldModel = oldModel
        self.oldTemplate = oldTemplate
        self.form = ankiqt.forms.changemodel.Ui_Dialog()
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
            return ui.utils.showInfo(
                _("Targets must be unique."), parent=self)
        if not any(cmap.values()):
            return ui.utils.showInfo(
                _("Must map at least one template."), parent=self)
        if self.targetModel == self.oldModel:
            self.ret = (self.targetModel, None, cmap)
            return QDialog.accept(self)
        self.modelChooser.deinit()
        self.ret = (self.targetModel, fmap, cmap)
        return QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "Browser#ChangeModel"))
