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
from ankiqt.ui.utils import saveGeom, restoreGeom
from anki.errors import *
from anki.db import *

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
            #s = self.columns[index.column()][1](self.getCardID(index))
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
        # show current card or last?
        card = None
        if self.searchStr == u"<current>" and self.parent.currentCard:
            card = self.parent.currentCard
        elif self.searchStr == u"<last>" and self.parent.lastCard:
            card = self.parent.lastCard
        if card:
            self.cards = [[card.id, card.priority, card.question,
                           card.answer, card.due, card.reps, card.factId]]
            self.reset()
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
            if self.sortKey == "ease":
                sort = ("order by cards.reps = 0, "
                        "cards.noCount / (cards.reps + 0.001) desc, "
                        "cards.reps")
            else:
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

    # Tools
    ######################################################################

    def getCardID(self, index):
        return self.cards[index.row()][0]

    def getCard(self, index):
        return self.deck.s.query(Card).get(self.getCardID(index))

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

class StatusDelegate(QItemDelegate):

    def __init__(self, parent, model):
        QItemDelegate.__init__(self, parent)
        self.model = model

    def paint(self, painter, option, index):
        row = self.model.cards[index.row()]
        standard = True
        # tagged
        if row[1] == 0:
            brush = QBrush(QColor("#ffffaa"))
            standard = False
        if self.model.isDeleted(row[0]):
            brush = QBrush(QColor("#ffaaaa"))
            standard = False
        if standard:
            QItemDelegate.paint(self, painter, option, index)
            return
        # custom render
        painter.save()
        painter.fillRect(option.rect, brush)
        data = self.model.data(index, Qt.DisplayRole)
        self.drawDisplay(painter, option, option.rect, data.toString())
        painter.restore()

class EditDeck(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.deck = self.parent.deck
        self.config = parent.config
        self.origModTime = parent.deck.modified
        self.dialog = ankiqt.forms.cardlist.Ui_EditDeck()
        self.dialog.setupUi(self)
        # flush all changes before we load
        self.deck.s.flush()
        self.model = DeckModel(self.parent, self.parent.deck)
        self.dialog.tableView.setSortingEnabled(False)
        self.dialog.tableView.setModel(self.model)
        self.dialog.tableView.setItemDelegate(StatusDelegate(self, self.model))
        self.dialog.tableView.selectionModel()
        self.dialog.tableView.setFont(QFont(
            self.config['editFontFamily'],
            self.config['editFontSize']))
        self.setupButtons()
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
        self.show()
        self.selectLastCard()

    def findCardInDeckModel( self, model, card ):
        for i, thisCard in enumerate( model.cards ):
            if thisCard.id == card.id:
                return i
        return -1

    def selectLastCard(self):
        "Show the row corresponding to the current card."
        if self.parent.config['editCurrentOnly']:
            if self.parent.currentCard:
                self.dialog.filterEdit.setText("<current>")
                self.dialog.filterEdit.selectAll()
        self.updateSearch()
        if not self.parent.config['editCurrentOnly'] and self.parent.currentCard:
            currentCardIndex = self.findCardInDeckModel(
                                 self.model, self.parent.currentCard )
            if currentCardIndex >= 0:
                self.dialog.tableView.selectRow( currentCardIndex )
                self.dialog.tableView.scrollTo(
                              self.model.index(currentCardIndex,0),
                              self.dialog.tableView.PositionAtTop )

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
        self.sortIndex = 0
        self.drawSort()
        self.connect(self.dialog.sortBox, SIGNAL("activated(int)"),
                     self.sortChanged)
        self.sortChanged(self.sortIndex, refresh=False)

    def drawTags(self):
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
            _("Creation date"),
            _("Modified date"),
            _("Due date"),
            _("Interval"),
            _("Answer count"),
            _("Difficulty"),
            ]
        self.sortFields = sorted(self.deck.allFields())
        self.sortList.extend([_("Field '%s'") % f for f in self.sortFields])
        self.dialog.sortBox.clear()
        self.dialog.sortBox.addItems(QStringList(self.sortList))
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
            self.sortKey = "ease"
        else:
            self.sortKey = ("field", self.sortFields[idx-8])
        self.sortIndex = idx
        self.model.sortKey = self.sortKey
        if refresh:
            self.model.showMatching()
            self.updateFilterLabel()

    def tagChanged(self, idx):
        if idx == 0:
            self.currentTag = None
        elif idx == 1:
            self.currentTag = "notag"
        else:
            self.currentTag = self.alltags[idx-2]
            if unicode(self.dialog.filterEdit.text()) in (
                u"<current>", u"<last>"):
                self.dialog.filterEdit.blockSignals(True)
                self.dialog.filterEdit.setText("")
                self.dialog.filterEdit.blockSignals(False)
        self.updateSearch()

    def updateFilterLabel(self):
        self.setWindowTitle(_("Anki - Edit Items (%(cur)d "
                              "of %(tot)d cards shown)") %
                            {"cur": len(self.model.cards),
                             "tot": self.deck.cardCount})

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

    def setupHeaders(self):
        if not sys.platform.startswith("win32"):
            self.dialog.tableView.verticalHeader().hide()
            self.dialog.tableView.horizontalHeader().hide()
        for i in range(2):
            self.dialog.tableView.horizontalHeader().setResizeMode(i, QHeaderView.Stretch)
        self.dialog.tableView.horizontalHeader().setResizeMode(2, QHeaderView.ResizeToContents)

    def setupButtons(self):
        # buttons
        self.connect(self.dialog.factsButton,
                     SIGNAL("clicked()"),
                     self.factsMenu)
        self.connect(self.dialog.cardsButton,
                     SIGNAL("clicked()"),
                     self.cardsMenu)
        # menus
        self.connect(self.dialog.action_Delete_card, SIGNAL("triggered()"), self.deleteCards)
        self.connect(self.dialog.actionAdd_fact_tag, SIGNAL("triggered()"), self.addFactTags)
        self.connect(self.dialog.actionAdd_card_tag, SIGNAL("triggered()"), self.addCardTags)
        self.connect(self.dialog.actionDelete_fact_tag, SIGNAL("triggered()"), self.deleteFactTags)
        self.connect(self.dialog.actionDelete_card_tag, SIGNAL("triggered()"), self.deleteCardTags)
        self.connect(self.dialog.actionAdd_Missing_Cards, SIGNAL("triggered()"), self.addMissingCards)
        self.connect(self.dialog.actionDelete_Fact, SIGNAL("triggered()"), self.deleteFacts)
        self.connect(self.dialog.actionResetCardProgress, SIGNAL("triggered()"), self.resetCardProgress)
        self.connect(self.dialog.actionResetFactProgress, SIGNAL("triggered()"), self.resetFactProgress)
        self.parent.runHook('editor.setupButtons', self)

    def factsMenu(self):
        menu = QMenu(self)
        menu.addAction(self.dialog.actionAdd_fact_tag)
        menu.addAction(self.dialog.actionDelete_fact_tag)
        menu.addSeparator()
        menu.addAction(self.dialog.actionAdd_Missing_Cards)
        menu.addSeparator()
        menu.addAction(self.dialog.actionResetFactProgress)
        menu.addAction(self.dialog.actionDelete_Fact)
        self.parent.runHook('editor.factsMenu', self, menu)
        menu.exec_(self.dialog.factsButton.mapToGlobal(QPoint(0,0)))

    def cardsMenu(self):
        menu = QMenu(self)
        menu.addAction(self.dialog.actionAdd_card_tag)
        menu.addAction(self.dialog.actionDelete_card_tag)
        menu.addSeparator()
        menu.addAction(self.dialog.actionResetCardProgress)
        menu.addAction(self.dialog.action_Delete_card)
        self.parent.runHook('editor.cardsMenu', self, menu)
        menu.exec_(self.dialog.cardsButton.mapToGlobal(QPoint(0,0)))

    def deleteCards(self):
        cards = self.selectedCards()
        self.dialog.tableView.selectionModel().blockSignals(True)
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectionModel().blockSignals(False)
        for id in cards:
            if id in self.model.deleted:
                del self.model.deleted[id]
            else:
                self.model.deleted[id] = True
        self.model.emit(SIGNAL("layoutChanged()"))

    def deleteFacts(self):
        cardIds = self.selectedFactsAsCards()
        self.dialog.tableView.selectionModel().blockSignals(True)
        self.dialog.tableView.selectionModel().clear()
        self.dialog.tableView.selectionModel().blockSignals(False)
        for id in cardIds:
            if id in self.model.deleted:
                del self.model.deleted[id]
            else:
                self.model.deleted[id] = True
        self.model.emit(SIGNAL("layoutChanged()"))

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

    def setupEditor(self):
        self.editor = ui.facteditor.FactEditor(self,
                                               self.dialog.fieldsArea,
                                               self.deck)
        self.editor.onFactValid = self.onFactValid
        self.editor.onFactInvalid = self.onFactInvalid
        self.connect(self.dialog.tableView.selectionModel(),
                     SIGNAL("currentRowChanged(QModelIndex, QModelIndex)"),
                     self.rowChanged)

    def onFactValid(self, fact):
        self.dialog.tableView.setEnabled(True)
        self.dialog.searchGroup.setEnabled(True)
        self.dialog.sortGroup.setEnabled(True)
        self.dialog.actionGroup.setEnabled(True)
        self.dialog.cardInfoGroup.setEnabled(True)

    def onFactInvalid(self, fact):
        self.dialog.tableView.setEnabled(False)
        self.dialog.searchGroup.setEnabled(False)
        self.dialog.sortGroup.setEnabled(False)
        self.dialog.actionGroup.setEnabled(False)
        self.dialog.cardInfoGroup.setEnabled(False)

    def rowChanged(self, current, previous):
        self.currentRow = current
        self.currentCard = self.model.getCard(current)
        self.deck.s.flush()
        self.deck.s.refresh(self.currentCard)
        self.deck.s.refresh(self.currentCard.fact)
        fact = self.currentCard.fact
        self.editor.setFact(fact, True)
        self.showCardInfo(self.currentCard)

    def setupCardInfo(self):
        self.currentCard = None
        self.cardInfoGrid = QGridLayout(self.dialog.cardInfoGroup)
        self.cardInfoGrid.setMargin(6)
        # card
        l = QLabel(_("<b>Tags</b>"))
        self.cardInfoGrid.addWidget(l, 0, 0)
        self.cardStaticTags = QLabel()
        self.cardStaticTags.setWordWrap(True)
        self.cardInfoGrid.addWidget(self.cardStaticTags, 1, 0)
        l = QLabel(_("<b>Card-specific tags</b>"))
        self.cardInfoGrid.addWidget(l, 2, 0)
        self.cardTags = QLineEdit()
        self.connect(self.cardTags, SIGNAL("textChanged(QString)"), self.saveCardInfo)
        self.cardInfoGrid.addWidget(self.cardTags, 3, 0)
        l = QLabel(_("<b>Statistics</b>"))
        self.cardInfoGrid.addWidget(l, 4, 0)
        self.cardStats = QLabel()
        self.cardInfoGrid.addWidget(self.cardStats, 5, 0)
        item = QSpacerItem(20, 20, QSizePolicy.Expanding,
                           QSizePolicy.Expanding)
        self.cardInfoGrid.addItem(item, 6, 0)

    def updateStaticTags(self):
        card = self.currentCard
        self.cardStaticTags.setText(
            ", ".join(parseTags(
            card.fact.model.tags + "," +
            card.cardModel.name + "," +
            card.fact.tags)))

    def showCardInfo(self, card):
        self.cardTags.setText(card.tags)
        self.updateStaticTags()
        # stats
        next = time.time() - card.due
        if next > 0:
            next = "%s ago" % anki.utils.fmtTimeSpan(next)
        else:
            next = "in %s" % anki.utils.fmtTimeSpan(abs(next))
        self.cardStats.setText(
            _("Created: %(c)s ago<br>"
              "Next due: %(n)s<br>"
              "Interval: %(i)0.0f days<br>"
              "Average: %(a)s<br>"
              "Total: %(t)s<br>"
              "Reviews: %(cor)d/%(tot)d<br>"
              "Successive: %(suc)d")% {
            "c": fmtTimeSpan(time.time() - card.created),
            "n": next,
            "i": card.interval,
            "a": fmtTimeSpan(card.averageTime),
            "t": fmtTimeSpan(card.reviewTime),
            "cor": card.yesCount,
            "tot": card.reps,
            "suc": card.successive,
            })

    def saveCardInfo(self, text):
        if self.currentCard:
            tags = unicode(text)
            if self.currentCard.tags != tags:
                self.currentCard.tags = tags
                self.currentCard.setModified()
                self.deck.setModified()

    def addFactTags(self):
        tags = ui.utils.getOnlyText(_("Enter tag(s) to add to each fact:"), self)
        if tags: self.deck.addFactTags(self.selectedFacts(), tags)
        self.updateAfterCardChange()

    def addCardTags(self):
        tags = ui.utils.getOnlyText(_("Enter tag(s) to add to each card:"), self)
        if tags: self.deck.addCardTags(self.selectedCards(), tags)
        self.updateAfterCardChange()

    def deleteFactTags(self):
        tags = ui.utils.getOnlyText(_("Enter tag(s) to delete from each fact:"), self)
        if tags: self.deck.deleteFactTags(self.selectedFacts(), tags)
        self.updateAfterCardChange()

    def deleteCardTags(self):
        tags = ui.utils.getOnlyText(_("Enter tag(s) to delete from each card:"), self)
        if tags: self.deck.deleteCardTags(self.selectedCards(), tags)
        self.updateAfterCardChange()

    def updateAfterCardChange(self, reset=False):
        "Refresh info like stats on current card"
        self.rowChanged(self.currentRow, None)
        if reset:
            self.updateSearch()

    def addMissingCards(self):
        for id in self.selectedFacts():
            self.deck.addMissingCards(self.deck.s.query(Fact).get(id))
        self.updateSearch()

    def resetCardProgress(self):
        self.deck.resetCards(self.selectedCards())
        self.updateAfterCardChange(reset=True)

    def resetFactProgress(self):
        self.deck.resetCards(self.selectedFactsAsCards())
        self.updateAfterCardChange(reset=True)

    def accept(self):
        self.hide()
        self.deck.deleteCards(self.model.deleted.keys())
        if len(self.model.deleted):
            self.parent.setStatus(
                _("%(del)d deleted.") %
                {"del": len(self.model.deleted)})
        if self.origModTime != self.deck.modified:
            self.parent.reset()
        ui.dialogs.close("CardList")
        QDialog.accept(self)

    def reject(self):
        saveGeom(self, "editor")
        self.accept()
