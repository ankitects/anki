# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

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
from anki.errors import *
from anki.db import *
from anki.stats import CardStats
from anki.hooks import runHook

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
        self.columns = [("Question", self.currentQuestion,
                         self.currentQuestion),
                        ("Answer", self.currentAnswer,
                         self.currentAnswer),
                        (" "*10 + "Due" + " "*10, self.nextDue,
                         "nextTime")]
        self.searchStr = ""
        self.tag = None
        self.cards = []
        self.deleted = {}

    # Model interface
    ######################################################################

    def rowCount(self, index):
        return len(self.cards)

    def columnCount(self, index):
        return len(self.columns)

    def data(self, index, role):
        if not index.isValid():
            return QVariant()
        if role == Qt.FontRole and index.column() == 2:
            f = QFont()
            f.setPixelSize(12)
            return QVariant(f)
        elif role == Qt.DisplayRole or role == Qt.EditRole:
            s = self.columns[index.column()][1](index)
            s = s.replace("<br>", u" ")
            s = s.replace("\n", u"  ")
            s = stripHTML(s)
            s = re.sub("\[sound:[^]]+\]", "", s)
            s = s.strip()
            return QVariant(s)
        elif role == Qt.SizeHintRole:
            if index.column() == 2:
                return QVariant(20)
        else:
            return QVariant()

    def headerData(self, section, orientation, role):
        if orientation == Qt.Vertical:
            return QVariant()
        elif role == Qt.DisplayRole:
            return QVariant(self.columns[section][0])
        else:
            return QVariant()

    def flags(self, index):
        return Qt.ItemFlag(Qt.ItemIsEnabled |
                           Qt.ItemIsSelectable)

    # Filtering
    ######################################################################

    def showMatching(self):
        if not self.sortKey:
            self.cards = []
            return
        # searching
        searchLimit = ""
        if self.searchStr:
            searchLimit = "cards.factId in (%s)" % (
                ",".join([str(x) for x in self.deck.s.column0(
                "select factId from fields where value like :val",
                val="%" + self.searchStr + "%")]))
        # tags
        tagLimit = ""
        if self.tag:
            if self.tag == "notag":
                tagLimit = "cards.id in %s" % ids2str(self.deck.cardsWithNoTags())
            else:
                tagLimit = "cards.id in %s" % ids2str(
                    [id for (id, tags, pri) in self.deck.tagsList()
                     if findTag(self.tag, parseTags(tags))])
        # sorting
        sort = ""
        ads = []
        if searchLimit: ads.append(searchLimit)
        if tagLimit: ads.append(tagLimit)
        if not self.parent.config['showSuspendedCards']:
            ads.append("cards.priority != 0")
        ads = " and ".join(ads)
        if isinstance(self.sortKey, types.StringType):
            # card property
            sort = "order by cards." + self.sortKey
            if self.sortKey in ("question", "answer"):
                sort += " collate nocase"
            query = ("select id, priority, question, answer, due, "
                     "reps, factId from cards ")
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
            query = ("select cards.id, cards.priority, cards.question, "
                     "cards.answer, cards.due, cards.reps, cards.factId "
                     "from fields, cards where fields.fieldModelId in (%s) "
                     "and fields.factId = cards.factId" + ads +
                     " order by cards.ordinal, %s") % (fields, order)
        # run the query
        self.cards = self.deck.s.all(query)
        self.reset()

    def updateCard(self, index):
        self.cards[index.row()] = self.deck.s.first("""
select id, priority, question, answer, due, reps, factId
from cards where id = :id""", id=self.cards[index.row()][0])
        self.emit(SIGNAL("dataChanged(QModelIndex,QModelIndex)"),
                  index, self.index(index.row(), 1))

    # Tools
    ######################################################################

    def getCardID(self, index):
        return self.cards[index.row()][0]

    def getCard(self, index):
        try:
            return self.deck.s.query(Card).get(self.getCardID(index))
        except IndexError:
            return None

    def isDeleted(self, id):
        return id in self.deleted

    def cardIndex(self, card):
        "Return the index of CARD, if currently displayed."
        return self.cards.index(card)

    def currentQuestion(self, index):
        return self.cards[index.row()][2]

    def currentAnswer(self, index):
        return self.cards[index.row()][3]

    def nextDue(self, index):
        d = self.cards[index.row()][4]
        reps = self.cards[index.row()][5]
        secs = d - time.time()
        if secs <= 0:
            if not reps:
                return _("(new card)")
            else:
                return _("%s ago") % fmtTimeSpan(abs(secs), pad=0)
        else:
            return _("in %s") % fmtTimeSpan(secs, pad=0)

class EditDeck(QMainWindow):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.deck = self.parent.deck
        self.config = parent.config
        self.origModTime = parent.deck.modified
        self.currentRow = None
        self.dialog = ankiqt.forms.cardlist.Ui_MainWindow()
        self.dialog.setupUi(self)
        # flush all changes before we load
        self.deck.s.flush()
        self.model = DeckModel(self.parent, self.parent.deck)
        self.dialog.tableView.setSortingEnabled(False)
        self.dialog.tableView.setModel(self.model)
        self.dialog.tableView.selectionModel()
        self.connect(self.dialog.tableView.selectionModel(),
                     SIGNAL("selectionChanged(QItemSelection,QItemSelection)"),
                     self.updateFilterLabel)
        self.dialog.tableView.setFont(QFont(
            self.config['editFontFamily'],
            self.config['editFontSize']))
        self.setupMenus()
        self.setupFilter()
        self.setupSort()
        self.setupHeaders()
        self.setupEditor()
        self.setupCardInfo()
        self.dialog.filterEdit.setFocus()
        ui.dialogs.open("CardList", self)
        self.drawTags()
        self.updateFilterLabel()
        restoreGeom(self, "editor")
        restoreSplitter(self.dialog.splitter, "editor")
        self.show()
        self.updateSearch()

    def findCardInDeckModel(self, model, card):
        for i, thisCard in enumerate(model.cards):
            if thisCard[0] == card.id:
                return i
        return -1

    def setupFilter(self):
        self.filterTimer = None
        self.currentTag = None
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
        self.sortIndex = self.config['sortIndex']
        self.drawSort()
        self.connect(self.dialog.sortBox, SIGNAL("activated(int)"),
                     self.sortChanged)
        self.sortChanged(self.sortIndex, refresh=False)

    def drawTags(self):
        self.dialog.tagList.setMaxVisibleItems(30)
        tags = self.deck.allTags()
        self.alltags = tags
        self.alltags.sort()
        self.dialog.tagList.clear()
        self.dialog.tagList.addItems(QStringList(
            [_('All tags'), _('No tags')] + self.alltags))
        if self.currentTag:
            try:
                idx = self.alltags.index(self.currentTag) + 2
            except ValueError:
                idx = 0
            self.dialog.tagList.setCurrentIndex(idx)

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
            ]
        self.sortFields = sorted(self.deck.allFields())
        self.sortList.extend([_("Field '%s'") % f for f in self.sortFields])
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
        else:
            self.sortKey = ("field", self.sortFields[idx-8])
        self.sortIndex = idx
        self.config['sortIndex'] = idx
        self.model.sortKey = self.sortKey
        if refresh:
            self.model.showMatching()
            self.updateFilterLabel()
            self.onEvent()
            self.focusCurrentCard()

    def tagChanged(self, idx):
        if idx == 0:
            self.currentTag = None
        elif idx == 1:
            self.currentTag = "notag"
        else:
            self.currentTag = self.alltags[idx-2]
        self.updateSearch()

    def updateFilterLabel(self):
        self.setWindowTitle(_("Editor (%(cur)d "
                              "of %(tot)d cards shown; %(sel)d selected)") %
                            {
            "cur": len(self.model.cards),
            "tot": self.deck.cardCount,
            "sel": len(self.dialog.tableView.selectionModel().selectedRows())
            })

    def onEvent(self):
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

    def filterTextChanged(self):
        interval = 500
        if self.filterTimer:
            self.filterTimer.setInterval(interval)
        else:
            self.filterTimer = QTimer(self)
            self.filterTimer.setSingleShot(True)
            self.filterTimer.start(interval)
            self.connect(self.filterTimer, SIGNAL("timeout()"), self.updateSearch)

    def showFilterNow(self):
        if self.filterTimer:
            self.filterTimer.stop()
        self.updateSearch()

    def updateSearch(self):
        self.model.searchStr = unicode(self.dialog.filterEdit.text())
        self.model.tag = self.currentTag
        self.model.showMatching()
        self.updateFilterLabel()
        self.onEvent()
        self.filterTimer = None
        if self.model.cards:
            self.dialog.cardInfoGroup.show()
            self.dialog.fieldsArea.show()
            self.dialog.tableView.selectionModel().setCurrentIndex(
                self.model.index(0, 0),
                QItemSelectionModel.Select | QItemSelectionModel.Rows)
        else:
            self.dialog.cardInfoGroup.hide()
            self.dialog.fieldsArea.hide()
        self.focusCurrentCard()

    def focusCurrentCard(self):
        if self.parent.currentCard:
            currentCardIndex = self.findCardInDeckModel(
                                 self.model, self.parent.currentCard)
            if currentCardIndex >= 0:
                sm = self.dialog.tableView.selectionModel()
                sm.clear()
                self.dialog.tableView.selectRow(currentCardIndex)
                self.dialog.tableView.scrollTo(
                              self.model.index(currentCardIndex,0),
                              self.dialog.tableView.PositionAtTop)

    def setupHeaders(self):
        if not sys.platform.startswith("win32"):
            self.dialog.tableView.verticalHeader().hide()
            self.dialog.tableView.horizontalHeader().hide()
        for i in range(2):
            self.dialog.tableView.horizontalHeader().setResizeMode(i, QHeaderView.Stretch)
        self.dialog.tableView.horizontalHeader().setResizeMode(2, QHeaderView.ResizeToContents)

    def setupMenus(self):
        # actions
        self.connect(self.dialog.actionDelete, SIGNAL("triggered()"), self.deleteCards)
        self.connect(self.dialog.actionAddTag, SIGNAL("triggered()"), self.addTags)
        self.connect(self.dialog.actionDeleteTag, SIGNAL("triggered()"), self.deleteTags)
        self.connect(self.dialog.actionAddCards, SIGNAL("triggered()"), self.addCards)
        self.connect(self.dialog.actionChangeTemplate, SIGNAL("triggered()"), self.onChangeTemplate)
        self.connect(self.dialog.actionResetProgress, SIGNAL("triggered()"), self.resetProgress)
        self.connect(self.dialog.actionSelectFacts, SIGNAL("triggered()"), self.selectFacts)
        self.connect(self.dialog.actionInvertSelection, SIGNAL("triggered()"), self.invertSelection)
        self.connect(self.dialog.actionUndo, SIGNAL("triggered()"), self.onUndo)
        self.connect(self.dialog.actionRedo, SIGNAL("triggered()"), self.onRedo)
        # jumps
        self.connect(self.dialog.actionFirstCard, SIGNAL("triggered()"), self.onFirstCard)
        self.connect(self.dialog.actionLastCard, SIGNAL("triggered()"), self.onLastCard)
        self.connect(self.dialog.actionPreviousCard, SIGNAL("triggered()"), self.onPreviousCard)
        self.connect(self.dialog.actionNextCard, SIGNAL("triggered()"), self.onNextCard)
        self.connect(self.dialog.actionFind, SIGNAL("triggered()"), self.onFind)
        self.connect(self.dialog.actionFact, SIGNAL("triggered()"), self.onFact)
        runHook('editor.setupMenus', self)

    def onClose(self):
        saveSplitter(self.dialog.splitter, "editor")
        self.editor.saveFieldsNow()
        if not self.factValid:
            ui.utils.showInfo(_(
                "Some fields are missing or not unique."),
                              parent=self, help="AddItems#AddError")
            return
        self.editor.setFact(None)
        saveGeom(self, "editor")
        self.hide()
        ui.dialogs.close("CardList")
        self.parent.moveToState("auto")
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
        self.deck.s.flush()
        self.deck.s.refresh(self.currentCard)
        self.deck.s.refresh(self.currentCard.fact)
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
        self.parent.moveToState("auto")

    # Menu options
    ######################################################################

    def deleteCards(self):
        cards = self.selectedCards()
        n = _("Delete Cards")
        self.deck.setUndoStart(n)
        self.deck.deleteCards(cards)
        self.deck.setUndoEnd(n)
        self.updateSearch()
        self.updateAfterCardChange()

    def addTags(self):
        (tags, r) = ui.utils.getTag(self, self.deck, _("Enter tags to add:"))
        if tags:
            n = _("Add Tags")
            self.deck.setUndoStart(n)
            self.deck.addTags(self.selectedFacts(), tags)
            self.deck.setUndoEnd(n)
        self.updateAfterCardChange()

    def deleteTags(self):
        (tags, r) = ui.utils.getTag(self, self.deck, _("Enter tags to delete:"))
        if tags:
            n = _("Delete Tags")
            self.deck.setUndoStart(n)
            self.deck.deleteTags(self.selectedFacts(), tags)
            self.deck.setUndoEnd(n)
        self.updateAfterCardChange()

    def resetProgress(self):
        n = _("Reset Progress")
        self.deck.setUndoStart(n)
        self.deck.resetCards(self.selectedCards())
        self.deck.setUndoEnd(n)
        self.updateAfterCardChange(reset=True)

    def addCards(self):
        sf = self.selectedFacts()
        if not sf:
            return
        cms = [x.id for x in self.deck.s.query(Fact).get(sf[0]).\
               model.cardModels]
        d = AddCardChooser(self, cms)
        if not d.exec_():
            return
        n = _("Add Cards")
        self.deck.setUndoStart(n)
        for id in sf:
            self.deck.addCards(self.deck.s.query(Fact).get(id),
                               d.selectedCms)
        self.deck.flushMod()
        self.deck.updateAllPriorities()
        self.deck.setUndoEnd(n)
        self.updateSearch()
        self.updateAfterCardChange()

    def onChangeTemplate(self):
        sc = self.selectedCards()
        models = self.deck.s.column0("""
select distinct modelId from cards, facts where
cards.id in %s and cards.factId = facts.id""" % ids2str(sc))
        if not len(models) == 1:
            ui.utils.showInfo(
                _("Can only change templates in a single model."),
                parent=self)
            return
        cms = [x.id for x in
               self.currentCard.fact.model.cardModels]
        d = ChangeTemplateDialog(self, cms)
        d.exec_()
        if d.newId:
            self.deck.changeCardModel(sc, d.newId)
            self.updateAfterCardChange()
            ### XXX: UNDO

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

    # Undo/Redo
    ######################################################################

    def onUndo(self):
        self.deck.undo()
        self.updateFilterLabel()
        self.updateSearch()
        self.updateAfterCardChange()

    def onRedo(self):
        self.deck.redo()
        self.updateFilterLabel()
        self.updateSearch()
        self.updateAfterCardChange()

    # Jumping
    ######################################################################

    def onFirstCard(self):
        if not self.model.cards:
            return
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(0)

    def onLastCard(self):
        if not self.model.cards:
            return
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(len(self.model.cards) - 1)

    def onPreviousCard(self):
        if not self.model.cards:
            return
        row = self.dialog.tableView.currentIndex().row()
        row = max(0, row - 1)
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(row)

    def onNextCard(self):
        if not self.model.cards:
            return
        row = self.dialog.tableView.currentIndex().row()
        row = min(len(self.model.cards) - 1, row + 1)
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectRow(row)

    def onFind(self):
        self.dialog.filterEdit.setFocus()

    def onFact(self):
        self.editor.focusFirst()

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
                                      "Editor#AddCards"))

class ChangeTemplateDialog(QDialog):

    def __init__(self, parent, cms):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.cms = cms
        self.newId = None
        self.dialog = ankiqt.forms.addcardmodels.Ui_Dialog()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.setWindowTitle(_("Change Template"))
        self.dialog.list.setSelectionMode(QAbstractItemView.SingleSelection)
        self.displayCards()
        restoreGeom(self, "changeTemplate")

    def displayCards(self):
        self.cms = self.parent.deck.s.all("""
select id, name from cardModels
where id in %s
order by ordinal""" % ids2str(self.cms))
        self.items = []
        for cm in self.cms:
            item = QListWidgetItem(cm[1], self.dialog.list)
            self.dialog.list.addItem(item)
            self.items.append(item)

    def accept(self):
        ret = None
        r = self.dialog.list.selectionModel().selectedRows()
        if r:
            self.newId = self.cms[r[0].row()][0]
        saveGeom(self, "changeTemplate")
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "Editor#ChangeTemplate"))
