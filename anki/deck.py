# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
The Deck
====================
"""
__docformat__ = 'restructuredtext'

import tempfile, time, os, random, sys, re, stat, shutil
import types, traceback, simplejson, datetime

from anki.db import *
from anki.lang import _, ngettext
from anki.errors import DeckAccessError
from anki.stdmodels import BasicModel
from anki.utils import parseTags, tidyHTML, genID, ids2str, hexifyID, \
     canonifyTags, joinTags, addTags, checksum
from anki.history import CardHistoryEntry
from anki.models import Model, CardModel, formatQA
from anki.stats import dailyStats, globalStats, genToday
from anki.fonts import toPlatformFont
from anki.tags import initTagTables, tagIds
from operator import itemgetter
from itertools import groupby
from anki.hooks import runHook, hookEmpty
from anki.template import render
from anki.media import updateMediaCount, mediaFiles, \
     rebuildMediaDir
import anki.latex # sets up hook

# ensure all the DB metadata in other files is loaded before proceeding
import anki.models, anki.facts, anki.cards, anki.stats
import anki.history, anki.media

# the current code set type -= 3 for manually suspended cards, and += 3*n
# for temporary suspends, (where n=1 for bury, n=2 for review/cram).
# This way we don't need to recalculate priorities when enabling the cards
# again, and paves the way for an arbitrary number of priorities in the
# future. But until all clients are upgraded, we need to keep munging the
# priorities to prevent older clients from getting confused
# PRIORITY_REVEARLY = -1
# PRIORITY_BURIED = -2
# PRIORITY_SUSPENDED = -3

# priorities
PRIORITY_HIGH = 4
PRIORITY_MED = 3
PRIORITY_NORM = 2
PRIORITY_LOW = 1
PRIORITY_NONE = 0
# rest
MATURE_THRESHOLD = 21
NEW_CARDS_DISTRIBUTE = 0
NEW_CARDS_LAST = 1
NEW_CARDS_FIRST = 2
NEW_CARDS_RANDOM = 0
NEW_CARDS_OLD_FIRST = 1
NEW_CARDS_NEW_FIRST = 2
REV_CARDS_OLD_FIRST = 0
REV_CARDS_NEW_FIRST = 1
REV_CARDS_DUE_FIRST = 2
REV_CARDS_RANDOM = 3
SEARCH_TAG = 0
SEARCH_TYPE = 1
SEARCH_PHRASE = 2
SEARCH_FID = 3
SEARCH_CARD = 4
SEARCH_DISTINCT = 5
SEARCH_FIELD = 6
SEARCH_FIELD_EXISTS = 7
SEARCH_QA = 8
SEARCH_PHRASE_WB = 9
DECK_VERSION = 65

deckVarsTable = Table(
    'deckVars', metadata,
    Column('key', UnicodeText, nullable=False, primary_key=True),
    Column('value', UnicodeText))

# parts of the code assume we only have one deck
decksTable = Table(
    'decks', metadata,
    Column('id', Integer, primary_key=True),
    Column('created', Float, nullable=False, default=time.time),
    Column('modified', Float, nullable=False, default=time.time),
    Column('description', UnicodeText, nullable=False, default=u""),
    Column('version', Integer, nullable=False, default=DECK_VERSION),
    Column('currentModelId', Integer, ForeignKey("models.id")),
    # syncName stores an md5sum of the deck path when syncing is enabled. If
    # it doesn't match the current deck path, the deck has been moved,
    # and syncing is disabled on load.
    Column('syncName', UnicodeText),
    Column('lastSync', Float, nullable=False, default=0),
    # scheduling
    ##############
    # initial intervals
    Column('hardIntervalMin', Float, nullable=False, default=1.0),
    Column('hardIntervalMax', Float, nullable=False, default=1.1),
    Column('midIntervalMin', Float, nullable=False, default=3.0),
    Column('midIntervalMax', Float, nullable=False, default=5.0),
    Column('easyIntervalMin', Float, nullable=False, default=7.0),
    Column('easyIntervalMax', Float, nullable=False, default=9.0),
    # delays on failure
    Column('delay0', Integer, nullable=False, default=600),
    # days to delay mature fails
    Column('delay1', Integer, nullable=False, default=0),
    Column('delay2', Float, nullable=False, default=0.0),
    # collapsing future cards
    Column('collapseTime', Integer, nullable=False, default=1),
    # priorities & postponing
    Column('highPriority', UnicodeText, nullable=False, default=u"PriorityVeryHigh"),
    Column('medPriority', UnicodeText, nullable=False, default=u"PriorityHigh"),
    Column('lowPriority', UnicodeText, nullable=False, default=u"PriorityLow"),
    Column('suspended', UnicodeText, nullable=False, default=u""), # obsolete
    # 0 is random, 1 is by input date
    Column('newCardOrder', Integer, nullable=False, default=1),
    # when to show new cards
    Column('newCardSpacing', Integer, nullable=False, default=NEW_CARDS_DISTRIBUTE),
    # limit the number of failed cards in play
    Column('failedCardMax', Integer, nullable=False, default=20),
    # number of new cards to show per day
    Column('newCardsPerDay', Integer, nullable=False, default=20),
    # currently unused
    Column('sessionRepLimit', Integer, nullable=False, default=0),
    Column('sessionTimeLimit', Integer, nullable=False, default=600),
    # stats offset
    Column('utcOffset', Float, nullable=False, default=-1),
    # count cache
    Column('cardCount', Integer, nullable=False, default=0),
    Column('factCount', Integer, nullable=False, default=0),
    Column('failedNowCount', Integer, nullable=False, default=0), # obsolete
    Column('failedSoonCount', Integer, nullable=False, default=0),
    Column('revCount', Integer, nullable=False, default=0),
    Column('newCount', Integer, nullable=False, default=0),
    # rev order
    Column('revCardOrder', Integer, nullable=False, default=0))

class Deck(object):
    "Top-level object. Manages facts, cards and scheduling information."

    factorFour = 1.3
    initialFactor = 2.5
    minimumAverage = 1.7
    maxScheduleTime = 36500

    def __init__(self, path=None):
        "Create a new deck."
        # a limit of 1 deck in the table
        self.id = 1
        # db session factory and instance
        self.Session = None
        self.s = None

    def _initVars(self):
        self.tmpMediaDir = None
        self.mediaPrefix = ""
        self.lastTags = u""
        self.lastLoaded = time.time()
        self.undoEnabled = False
        self.sessionStartReps = 0
        self.sessionStartTime = 0
        self.lastSessionStart = 0
        self.queueLimit = 200
        # if most recent deck var not defined, make sure defaults are set
        if not self.s.scalar("select 1 from deckVars where key = 'revSpacing'"):
            self.setVarDefault("suspendLeeches", True)
            self.setVarDefault("leechFails", 16)
            self.setVarDefault("perDay", True)
            self.setVarDefault("newActive", "")
            self.setVarDefault("revActive", "")
            self.setVarDefault("newInactive", self.suspended)
            self.setVarDefault("revInactive", self.suspended)
            self.setVarDefault("newSpacing", 60)
            self.setVarDefault("mediaURL", "")
            self.setVarDefault("latexPre", """\
\\documentclass[12pt]{article}
\\special{papersize=3in,5in}
\\usepackage[utf8]{inputenc}
\\usepackage{amssymb,amsmath}
\\pagestyle{empty}
\\setlength{\\parindent}{0in}
\\begin{document}
""")
            self.setVarDefault("latexPost", "\\end{document}")
            self.setVarDefault("revSpacing", 0.1)
        self.updateCutoff()
        self.setupStandardScheduler()

    def modifiedSinceSave(self):
        return self.modified > self.lastLoaded

    # Queue management
    ##########################################################################

    def setupStandardScheduler(self):
        self.getCardId = self._getCardId
        self.fillFailedQueue = self._fillFailedQueue
        self.fillRevQueue = self._fillRevQueue
        self.fillNewQueue = self._fillNewQueue
        self.rebuildFailedCount = self._rebuildFailedCount
        self.rebuildRevCount = self._rebuildRevCount
        self.rebuildNewCount = self._rebuildNewCount
        self.requeueCard = self._requeueCard
        self.timeForNewCard = self._timeForNewCard
        self.updateNewCountToday = self._updateNewCountToday
        self.cardQueue = self._cardQueue
        self.finishScheduler = None
        self.answerCard = self._answerCard
        self.cardLimit = self._cardLimit
        self.answerPreSave = None
        self.spaceCards = self._spaceCards
        self.scheduler = "standard"
        # restore any cards temporarily suspended by alternate schedulers
        try:
            self.resetAfterReviewEarly()
        except OperationalError, e:
            # will fail if deck hasn't been upgraded yet
            pass

    def fillQueues(self):
        self.fillFailedQueue()
        self.fillRevQueue()
        self.fillNewQueue()

    def rebuildCounts(self):
        # global counts
        self.cardCount = self.s.scalar("select count(*) from cards")
        self.factCount = self.s.scalar("select count(*) from facts")
        # due counts
        self.rebuildFailedCount()
        self.rebuildRevCount()
        self.rebuildNewCount()

    def _cardLimit(self, active, inactive, sql):
        yes = parseTags(self.getVar(active))
        no = parseTags(self.getVar(inactive))
        if yes:
            yids = tagIds(self.s, yes).values()
            nids = tagIds(self.s, no).values()
            return sql.replace(
                "where",
                "where +c.id in (select cardId from cardTags where "
                "tagId in %s) and +c.id not in (select cardId from "
                "cardTags where tagId in %s) and" % (
                ids2str(yids),
                ids2str(nids)))
        elif no:
            nids = tagIds(self.s, no).values()
            return sql.replace(
                "where",
                "where +c.id not in (select cardId from cardTags where "
                "tagId in %s) and" % ids2str(nids))
        else:
            return sql

    def _rebuildFailedCount(self):
        # This is a count of all failed cards within the current day cutoff.
        # The cards may not be ready for review yet, but can still be
        # displayed if failedCardsMax is reached.
        self.failedSoonCount = self.s.scalar(
            self.cardLimit(
            "revActive", "revInactive",
            "select count(*) from cards c where type = 0 "
            "and combinedDue < :lim"), lim=self.failedCutoff)

    def _rebuildRevCount(self):
        self.revCount = self.s.scalar(
            self.cardLimit(
            "revActive", "revInactive",
            "select count(*) from cards c where type = 1 "
            "and combinedDue < :lim"), lim=self.dueCutoff)

    def _rebuildNewCount(self):
        self.newCount = self.s.scalar(
            self.cardLimit(
            "newActive", "newInactive",
            "select count(*) from cards c where type = 2 "
            "and combinedDue < :lim"), lim=self.dueCutoff)
        self.updateNewCountToday()
        self.spacedCards = []

    def _updateNewCountToday(self):
        self.newCountToday = max(min(
            self.newCount, self.newCardsPerDay -
            self.newCardsDoneToday()), 0)

    def _fillFailedQueue(self):
        if self.failedSoonCount and not self.failedQueue:
            self.failedQueue = self.s.all(
                self.cardLimit(
                "revActive", "revInactive", """
select c.id, factId, combinedDue from cards c where
type = 0 and combinedDue < :lim order by combinedDue
limit %d""" % self.queueLimit), lim=self.failedCutoff)
            self.failedQueue.reverse()

    def _fillRevQueue(self):
        if self.revCount and not self.revQueue:
            self.revQueue = self.s.all(
                self.cardLimit(
                "revActive", "revInactive", """
select c.id, factId from cards c where
type = 1 and combinedDue < :lim order by %s
limit %d""" % (self.revOrder(), self.queueLimit)), lim=self.dueCutoff)
            self.revQueue.reverse()

    def _fillNewQueue(self):
        if self.newCountToday and not self.newQueue and not self.spacedCards:
            self.newQueue = self.s.all(
                self.cardLimit(
                "newActive", "newInactive", """
select c.id, factId from cards c where
type = 2 and combinedDue < :lim order by %s
limit %d""" % (self.newOrder(), self.queueLimit)), lim=self.dueCutoff)
            self.newQueue.reverse()

    def queueNotEmpty(self, queue, fillFunc, new=False):
        while True:
            self.removeSpaced(queue, new)
            if queue:
                return True
            fillFunc()
            if not queue:
                return False

    def removeSpaced(self, queue, new=False):
        popped = []
        delay = None
        while queue:
            fid = queue[-1][1]
            if fid in self.spacedFacts:
                # still spaced
                id = queue.pop()[0]
                # assuming 10 cards/minute, track id if likely to expire
                # before queue refilled
                if new and self.newSpacing < self.queueLimit * 6:
                    popped.append(id)
                    delay = self.spacedFacts[fid]
            else:
                if popped:
                    self.spacedCards.append((delay, popped))
                return

    def revNoSpaced(self):
        return self.queueNotEmpty(self.revQueue, self.fillRevQueue)

    def newNoSpaced(self):
        return self.queueNotEmpty(self.newQueue, self.fillNewQueue, True)

    def _requeueCard(self, card, oldSuc):
        newType = None
        try:
            if card.reps == 1:
                if self.newFromCache:
                    # fetched from spaced cache
                    newType = 2
                    cards = self.spacedCards.pop(0)[1]
                    # reschedule the siblings
                    if len(cards) > 1:
                        self.spacedCards.append(
                            (time.time() + self.newSpacing, cards[1:]))
                else:
                    # fetched from normal queue
                    newType = 1
                    self.newQueue.pop()
            elif oldSuc == 0:
                self.failedQueue.pop()
            else:
                self.revQueue.pop()
        except:
            raise Exception("""\
requeueCard() failed. Please report this along with the steps you take to
produce the problem.

Counts %d %d %d
Queue %d %d %d
Card info: %d %d %d
New type: %s""" % (self.failedSoonCount, self.revCount, self.newCountToday,
                          len(self.failedQueue), len(self.revQueue),
                          len(self.newQueue),
                          card.reps, card.successive, oldSuc, `newType`))

    def revOrder(self):
        return ("priority desc, interval desc",
                "priority desc, interval",
                "priority desc, combinedDue",
                "priority desc, factId, ordinal")[self.revCardOrder]

    def newOrder(self):
        return ("priority desc, due",
                "priority desc, due",
                "priority desc, due desc")[self.newCardOrder]

    def rebuildTypes(self):
        "Rebuild the type cache. Only necessary on upgrade."
        # set canonical type first
        self.s.statement("""
update cards set
relativeDelay = (case
when successive then 1 when reps then 0 else 2 end)
""")
        # then current type based on that
        self.s.statement("""
update cards set
type = (case
when type >= 0 then relativeDelay else relativeDelay - 3 end)
""")

    def _cardQueue(self, card):
        return self.cardType(card)

    def cardType(self, card):
        "Return the type of the current card (what queue it's in)"
        if card.successive:
            return 1
        elif card.reps:
            return 0
        else:
            return 2

    def updateCutoff(self):
        d = datetime.datetime.utcfromtimestamp(
            time.time() - self.utcOffset) + datetime.timedelta(days=1)
        d = datetime.datetime(d.year, d.month, d.day)
        newday = self.utcOffset - time.timezone
        d += datetime.timedelta(seconds=newday)
        cutoff = time.mktime(d.timetuple())
        # cutoff must not be in the past
        while cutoff < time.time():
            cutoff += 86400
        # cutoff must not be more than 24 hours in the future
        cutoff = min(time.time() + 86400, cutoff)
        self.failedCutoff = cutoff
        if self.getBool("perDay"):
            self.dueCutoff = cutoff
        else:
            self.dueCutoff = time.time()

    def reset(self):
        # setup global/daily stats
        self._globalStats = globalStats(self)
        self._dailyStats = dailyStats(self)
        # recheck counts
        self.rebuildCounts()
        # empty queues; will be refilled by getCard()
        self.failedQueue = []
        self.revQueue = []
        self.newQueue = []
        self.spacedFacts = {}
        # determine new card distribution
        if self.newCardSpacing == NEW_CARDS_DISTRIBUTE:
            if self.newCountToday:
                self.newCardModulus = (
                    (self.newCountToday + self.revCount) / self.newCountToday)
                # if there are cards to review, ensure modulo >= 2
                if self.revCount:
                    self.newCardModulus = max(2, self.newCardModulus)
            else:
                self.newCardModulus = 0
        else:
            self.newCardModulus = 0
        # recache css
        self.rebuildCSS()
        # spacing for delayed cards - not to be confused with newCardSpacing
        # above
        self.newSpacing = self.getFloat('newSpacing')
        self.revSpacing = self.getFloat('revSpacing')

    def checkDay(self):
        # check if the day has rolled over
        if genToday(self) != self._dailyStats.day:
            self.updateCutoff()
            self.reset()

    # Review early
    ##########################################################################

    def setupReviewEarlyScheduler(self):
        self.fillRevQueue = self._fillRevEarlyQueue
        self.rebuildRevCount = self._rebuildRevEarlyCount
        self.finishScheduler = self._onReviewEarlyFinished
        self.answerPreSave = self._reviewEarlyPreSave
        self.scheduler = "reviewEarly"

    def _reviewEarlyPreSave(self, card, ease):
        if ease > 1:
            # prevent it from appearing in next queue fill
            card.type += 6

    def resetAfterReviewEarly(self):
        "Put temporarily suspended cards back into play. Caller must .reset()"
        # FIXME: can ignore priorities in the future
        ids = self.s.column0(
            "select id from cards where type between 6 and 8 or priority = -1")
        if ids:
            self.updatePriorities(ids)
            self.s.statement(
                "update cards set type = type - 6 where type between 6 and 8")
            self.flushMod()

    def _onReviewEarlyFinished(self):
        # clean up buried cards
        self.resetAfterReviewEarly()
        # and go back to regular scheduler
        self.setupStandardScheduler()

    def _rebuildRevEarlyCount(self):
        # in the future it would be nice to skip the first x days of due cards
        self.revCount = self.s.scalar(
            self.cardLimit(
            "revActive", "revInactive", """
select count() from cards c where type = 1 and combinedDue > :now
"""), now=self.dueCutoff)

    def _fillRevEarlyQueue(self):
        if self.revCount and not self.revQueue:
            self.revQueue = self.s.all(
                self.cardLimit(
                "revActive", "revInactive", """
select id, factId from cards c where type = 1 and combinedDue > :lim
order by combinedDue limit %d""" % self.queueLimit), lim=self.dueCutoff)
            self.revQueue.reverse()

    # Learn more
    ##########################################################################

    def setupLearnMoreScheduler(self):
        self.rebuildNewCount = self._rebuildLearnMoreCount
        self.updateNewCountToday = self._updateLearnMoreCountToday
        self.finishScheduler = self.setupStandardScheduler
        self.scheduler = "learnMore"

    def _rebuildLearnMoreCount(self):
        self.newCount = self.s.scalar(
            self.cardLimit(
            "newActive", "newInactive",
            "select count(*) from cards c where type = 2 "
            "and combinedDue < :lim"), lim=self.dueCutoff)
        self.spacedCards = []

    def _updateLearnMoreCountToday(self):
        self.newCountToday = self.newCount

    # Cramming
    ##########################################################################

    def setupCramScheduler(self, active, order):
        self.getCardId = self._getCramCardId
        self.activeCramTags = active
        self.cramOrder = order
        self.rebuildNewCount = self._rebuildCramNewCount
        self.rebuildRevCount = self._rebuildCramCount
        self.rebuildFailedCount = self._rebuildFailedCramCount
        self.fillRevQueue = self._fillCramQueue
        self.fillFailedQueue = self._fillFailedCramQueue
        self.finishScheduler = self.setupStandardScheduler
        self.failedCramQueue = []
        self.requeueCard = self._requeueCramCard
        self.cardQueue = self._cramCardQueue
        self.answerCard = self._answerCramCard
        self.spaceCards = self._spaceCramCards
        # reuse review early's code
        self.answerPreSave = self._cramPreSave
        self.cardLimit = self._cramCardLimit
        self.scheduler = "cram"

    def _cramPreSave(self, card, ease):
        # prevent it from appearing in next queue fill
        card.lastInterval = self.cramLastInterval
        card.type += 6

    def _spaceCramCards(self, card):
        self.spacedFacts[card.factId] = time.time() + self.newSpacing

    def _answerCramCard(self, card, ease):
        self.cramLastInterval = card.lastInterval
        self._answerCard(card, ease)
        if ease == 1:
            self.failedCramQueue.insert(0, [card.id, card.factId])

    def _getCramCardId(self, check=True):
        self.checkDay()
        self.fillQueues()
        if self.failedCardMax and self.failedSoonCount >= self.failedCardMax:
            return self.failedQueue[-1][0]
        # card due for review?
        if self.revNoSpaced():
            return self.revQueue[-1][0]
        if self.failedQueue:
            return self.failedQueue[-1][0]
        if check:
            # collapse spaced cards before reverting back to old scheduler
            self.reset()
            return self.getCardId(False)
        # if we're in a custom scheduler, we may need to switch back
        if self.finishScheduler:
            self.finishScheduler()
            self.reset()
            return self.getCardId()

    def _cramCardQueue(self, card):
        if self.revQueue and self.revQueue[-1][0] == card.id:
            return 1
        else:
            return 0

    def _requeueCramCard(self, card, oldSuc):
        if self.cardQueue(card) == 1:
            self.revQueue.pop()
        else:
            self.failedCramQueue.pop()

    def _rebuildCramNewCount(self):
        self.newCount = 0
        self.newCountToday = 0

    def _cramCardLimit(self, active, inactive, sql):
        # inactive is (currently) ignored
        if isinstance(active, list):
            return sql.replace(
                "where", "where +c.id in " + ids2str(active) + " and")
        else:
            yes = parseTags(active)
            if yes:
                yids = tagIds(self.s, yes).values()
                return sql.replace(
                    "where ",
                    "where +c.id in (select cardId from cardTags where "
                    "tagId in %s) and " % ids2str(yids))
            else:
                return sql

    def _fillCramQueue(self):
        if self.revCount and not self.revQueue:
            self.revQueue = self.s.all(self.cardLimit(
                self.activeCramTags, "", """
select id, factId from cards c
where type between 0 and 2
order by %s
limit %s""" % (self.cramOrder, self.queueLimit)))
            self.revQueue.reverse()

    def _rebuildCramCount(self):
        self.revCount = self.s.scalar(self.cardLimit(
            self.activeCramTags, "",
            "select count(*) from cards c where type between 0 and 2"))

    def _rebuildFailedCramCount(self):
        self.failedSoonCount = len(self.failedCramQueue)

    def _fillFailedCramQueue(self):
        self.failedQueue = self.failedCramQueue

    # Getting the next card
    ##########################################################################

    def getCard(self, orm=True):
        "Return the next card object, or None."
        id = self.getCardId()
        if id:
            return self.cardFromId(id, orm)
        else:
            self.stopSession()

    def _getCardId(self, check=True):
        "Return the next due card id, or None."
        self.checkDay()
        self.fillQueues()
        self.updateNewCountToday()
        if self.failedQueue:
            # failed card due?
            if self.delay0:
                if self.failedQueue[-1][2] + self.delay0 < time.time():
                    return self.failedQueue[-1][0]
            # failed card queue too big?
            if (self.failedCardMax and
                self.failedSoonCount >= self.failedCardMax):
                return self.failedQueue[-1][0]
        # distribute new cards?
        if self.newNoSpaced() and self.timeForNewCard():
            return self.getNewCard()
        # card due for review?
        if self.revNoSpaced():
            return self.revQueue[-1][0]
        # new cards left?
        if self.newCountToday:
            id = self.getNewCard()
            if id:
                return id
        if check:
            # check for expired cards, or new day rollover
            self.updateCutoff()
            self.reset()
            return self.getCardId(check=False)
        # display failed cards early/last
        if not check and self.showFailedLast() and self.failedQueue:
            return self.failedQueue[-1][0]
        # if we're in a custom scheduler, we may need to switch back
        if self.finishScheduler:
            self.finishScheduler()
            self.reset()
            return self.getCardId()

    # Get card: helper functions
    ##########################################################################

    def _timeForNewCard(self):
        "True if it's time to display a new card when distributing."
        if not self.newCountToday:
            return False
        if self.newCardSpacing == NEW_CARDS_LAST:
            return False
        if self.newCardSpacing == NEW_CARDS_FIRST:
            return True
        # force review if there are very high priority cards
        if self.revQueue:
            if self.s.scalar(
                "select 1 from cards where id = :id and priority = 4",
                id = self.revQueue[-1][0]):
                return False
        if self.newCardModulus:
            return self._dailyStats.reps % self.newCardModulus == 0
        else:
            return False

    def getNewCard(self):
        src = None
        if (self.spacedCards and
            self.spacedCards[0][0] < time.time()):
            # spaced card has expired
            src = 0
        elif self.newQueue:
            # card left in new queue
            src = 1
        elif self.spacedCards:
            # card left in spaced queue
            src = 0
        else:
            # only cards spaced to another day left
            return
        if src == 0:
            cards = self.spacedCards[0][1]
            self.newFromCache = True
            return cards[0]
        else:
            self.newFromCache = False
            return self.newQueue[-1][0]

    def showFailedLast(self):
        return self.collapseTime or not self.delay0

    def cardFromId(self, id, orm=False):
        "Given a card ID, return a card, and start the card timer."
        if orm:
            card = self.s.query(anki.cards.Card).get(id)
            if not card:
                return
            card.timerStopped = False
        else:
            card = anki.cards.Card()
            if not card.fromDB(self.s, id):
                return
        card.deck = self
        card.genFuzz()
        card.startTimer()
        return card

    # Answering a card
    ##########################################################################

    def _answerCard(self, card, ease):
        undoName = _("Answer Card")
        self.setUndoStart(undoName)
        now = time.time()
        # old state
        oldState = self.cardState(card)
        oldQueue = self.cardQueue(card)
        lastDelaySecs = time.time() - card.combinedDue
        lastDelay = lastDelaySecs / 86400.0
        oldSuc = card.successive
        # update card details
        last = card.interval
        card.interval = self.nextInterval(card, ease)
        card.lastInterval = last
        if card.reps:
            # only update if card was not new
            card.lastDue = card.due
        card.due = self.nextDue(card, ease, oldState)
        card.isDue = 0
        card.lastFactor = card.factor
        card.spaceUntil = 0
        if not self.finishScheduler:
            # don't update factor in custom schedulers
            self.updateFactor(card, ease)
        # spacing
        self.spaceCards(card)
        # adjust counts for current card
        if ease == 1:
            if card.due < self.failedCutoff:
                self.failedSoonCount += 1
        if oldQueue == 0:
            self.failedSoonCount -= 1
        elif oldQueue == 1:
            self.revCount -= 1
        else:
            self.newCount -= 1
        # card stats
        anki.cards.Card.updateStats(card, ease, oldState)
        # update type & ensure past cutoff
        card.type = self.cardType(card)
        card.relativeDelay = card.type
        if ease != 1:
            card.due = max(card.due, self.dueCutoff+1)
        # allow custom schedulers to munge the card
        if self.answerPreSave:
            self.answerPreSave(card, ease)
        # save
        card.combinedDue = card.due
        card.toDB(self.s)
        # global/daily stats
        anki.stats.updateAllStats(self.s, self._globalStats, self._dailyStats,
                                  card, ease, oldState)
        # review history
        entry = CardHistoryEntry(card, ease, lastDelay)
        entry.writeSQL(self.s)
        self.modified = now
        # remove from queue
        self.requeueCard(card, oldSuc)
        # leech handling - we need to do this after the queue, as it may cause
        # a reset()
        isLeech = self.isLeech(card)
        if isLeech:
            self.handleLeech(card)
        runHook("cardAnswered", card.id, isLeech)
        self.setUndoEnd(undoName)

    def _spaceCards(self, card):
        new = time.time() + self.newSpacing
        self.s.statement("""
update cards set
combinedDue = (case
when type = 1 then combinedDue + 86400 * (case
  when interval*:rev < 1 then 0
  else interval*:rev
  end)
when type = 2 then :new
end),
modified = :now, isDue = 0
where id != :id and factId = :factId
and combinedDue < :cut
and type between 1 and 2""",
                         id=card.id, now=time.time(), factId=card.factId,
                         cut=self.dueCutoff, new=new, rev=self.revSpacing)
        # update local cache of seen facts
        self.spacedFacts[card.factId] = new

    def isLeech(self, card):
        no = card.noCount
        fmax = self.getInt('leechFails')
        if not fmax:
            return
        return (
            # failed
            not card.successive and
            # greater than fail threshold
            no >= fmax and
            # at least threshold/2 reps since last time
            (fmax - no) % (max(fmax/2, 1)) == 0)

    def handleLeech(self, card):
        self.refreshSession()
        scard = self.cardFromId(card.id, True)
        tags = scard.fact.tags
        tags = addTags("Leech", tags)
        scard.fact.tags = canonifyTags(tags)
        scard.fact.setModified(textChanged=True, deck=self)
        self.updateFactTags([scard.fact.id])
        self.s.flush()
        self.s.expunge(scard)
        if self.getBool('suspendLeeches'):
            self.suspendCards([card.id])
        self.reset()
        self.refreshSession()

    # Interval management
    ##########################################################################

    def nextInterval(self, card, ease):
        "Return the next interval for CARD given EASE."
        delay = self._adjustedDelay(card, ease)
        return self._nextInterval(card, delay, ease)

    def _nextInterval(self, card, delay, ease):
        interval = card.interval
        factor = card.factor
        # if shown early
        if delay < 0:
            # FIXME: this should recreate lastInterval from interval /
            # lastFactor, or we lose delay information when reviewing early
            interval = max(card.lastInterval, card.interval + delay)
            if interval < self.midIntervalMin:
                interval = 0
            delay = 0
        # if interval is less than mid interval, use presets
        if ease == 1:
            interval *= self.delay2
            if interval < self.hardIntervalMin:
                interval = 0
        elif interval == 0:
            if ease == 2:
                interval = random.uniform(self.hardIntervalMin,
                                          self.hardIntervalMax)
            elif ease == 3:
                interval = random.uniform(self.midIntervalMin,
                                          self.midIntervalMax)
            elif ease == 4:
                interval = random.uniform(self.easyIntervalMin,
                                          self.easyIntervalMax)
        else:
            # if not cramming, boost initial 2
            if (interval < self.hardIntervalMax and
                interval > 0.166):
                mid = (self.midIntervalMin + self.midIntervalMax) / 2.0
                interval = mid / factor
            # multiply last interval by factor
            if ease == 2:
                interval = (interval + delay/4) * 1.2
            elif ease == 3:
                interval = (interval + delay/2) * factor
            elif ease == 4:
                interval = (interval + delay) * factor * self.factorFour
            fuzz = random.uniform(0.95, 1.05)
            interval *= fuzz
        if self.maxScheduleTime:
            interval = min(interval, self.maxScheduleTime)
        return interval

    def nextIntervalStr(self, card, ease, short=False):
        "Return the next interval for CARD given EASE as a string."
        int = self.nextInterval(card, ease)
        return anki.utils.fmtTimeSpan(int*86400, short=short)

    def nextDue(self, card, ease, oldState):
        "Return time when CARD will expire given EASE."
        if ease == 1:
            # 600 is a magic value which means no bonus, and is used to ease
            # upgrades
            cram = self.scheduler == "cram"
            if (not cram and oldState == "mature"
                and self.delay1 and self.delay1 != 600):
                # user wants a bonus of 1+ days. put the failed cards at the
                # start of the future day, so that failures that day will come
                # after the waiting cards
                return self.failedCutoff + (self.delay1 - 1)*86400
            else:
                due = 0
        else:
            due = card.interval * 86400.0
        return due + time.time()

    def updateFactor(self, card, ease):
        "Update CARD's factor based on EASE."
        card.lastFactor = card.factor
        if not card.reps:
            # card is new, inherit beginning factor
            card.factor = self.averageFactor
        if card.successive and not self.cardIsBeingLearnt(card):
            if ease == 1:
                card.factor -= 0.20
            elif ease == 2:
                card.factor -= 0.15
        if ease == 4:
            card.factor += 0.10
        card.factor = max(1.3, card.factor)

    def _adjustedDelay(self, card, ease):
        "Return an adjusted delay value for CARD based on EASE."
        if self.cardIsNew(card):
            return 0
        if card.combinedDue <= self.dueCutoff:
            return (self.dueCutoff - card.due) / 86400.0
        else:
            return (self.dueCutoff - card.combinedDue) / 86400.0

    def resetCards(self, ids):
        "Reset progress on cards in IDS."
        self.s.statement("""
update cards set interval = :new, lastInterval = 0, lastDue = 0,
factor = 2.5, reps = 0, successive = 0, averageTime = 0, reviewTime = 0,
youngEase0 = 0, youngEase1 = 0, youngEase2 = 0, youngEase3 = 0,
youngEase4 = 0, matureEase0 = 0, matureEase1 = 0, matureEase2 = 0,
matureEase3 = 0,matureEase4 = 0, yesCount = 0, noCount = 0,
spaceUntil = 0, type = 2, relativeDelay = 2,
combinedDue = created, modified = :now, due = created, isDue = 0
where id in %s""" % ids2str(ids), now=time.time(), new=0)
        if self.newCardOrder == NEW_CARDS_RANDOM:
            # we need to re-randomize now
            self.randomizeNewCards(ids)
        self.flushMod()
        self.refreshSession()

    def randomizeNewCards(self, cardIds=None):
        "Randomize 'due' on all new cards."
        now = time.time()
        query = "select distinct factId from cards where reps = 0"
        if cardIds:
            query += " and id in %s" % ids2str(cardIds)
        fids = self.s.column0(query)
        data = [{'fid': fid,
                 'rand': random.uniform(0, now),
                 'now': now} for fid in fids]
        self.s.statements("""
update cards
set due = :rand + ordinal,
combinedDue = :rand + ordinal,
modified = :now
where factId = :fid
and relativeDelay = 2""", data)

    def orderNewCards(self):
        "Set 'due' to card creation time."
        self.s.statement("""
update cards set
due = created,
combinedDue = created,
modified = :now
where relativeDelay = 2""", now=time.time())

    def rescheduleCards(self, ids, min, max):
        "Reset cards and schedule with new interval in days (min, max)."
        self.resetCards(ids)
        vals = []
        for id in ids:
            r = random.uniform(min*86400, max*86400)
            vals.append({
                'id': id,
                'due': r + time.time(),
                'int': r / 86400.0,
                't': time.time(),
                })
        self.s.statements("""
update cards set
interval = :int,
due = :due,
combinedDue = :due,
reps = 1,
successive = 1,
yesCount = 1,
firstAnswered = :t,
type = 1,
relativeDelay = 1,
isDue = 0
where id = :id""", vals)
        self.flushMod()

    # Times
    ##########################################################################

    def nextDueMsg(self):
        next = self.earliestTime()
        if next:
            # all new cards except suspended
            newCount = self.newCardsDueBy(self.dueCutoff + 86400)
            newCardsTomorrow = min(newCount, self.newCardsPerDay)
            cards = self.cardsDueBy(self.dueCutoff + 86400)
            msg = _('''\
<style>b { color: #00f; }</style>
At this time tomorrow:<br>
%(wait)s<br>
%(new)s''') % {
                'new': ngettext("There will be <b>%d new</b> card.",
                          "There will be <b>%d new</b> cards.",
                          newCardsTomorrow) % newCardsTomorrow,
                'wait': ngettext("There will be <b>%s review</b>.",
                          "There will be <b>%s reviews</b>.", cards) % cards,
                }
            if next > (self.dueCutoff+86400) and not newCardsTomorrow:
                msg = (_("The next review is in <b>%s</b>.") %
                       self.earliestTimeStr())
        else:
            msg = _("No cards are due.")
        return msg

    def earliestTime(self):
        """Return the time of the earliest card.
This may be in the past if the deck is not finished.
If the deck has no (enabled) cards, return None.
Ignore new cards."""
        earliestRev = self.s.scalar(self.cardLimit("revActive", "revInactive", """
select combinedDue from cards c where type = 1
order by combinedDue
limit 1"""))
        earliestFail = self.s.scalar(self.cardLimit("revActive", "revInactive", """
select combinedDue+%d from cards c where type = 0
order by combinedDue
limit 1""" % self.delay0))
        if earliestRev and earliestFail:
            return min(earliestRev, earliestFail)
        elif earliestRev:
            return earliestRev
        else:
            return earliestFail

    def earliestTimeStr(self, next=None):
        """Return the relative time to the earliest card as a string."""
        if next == None:
            next = self.earliestTime()
        if not next:
            return _("unknown")
        diff = next - time.time()
        return anki.utils.fmtTimeSpan(diff)

    def cardsDueBy(self, time):
        "Number of cards due at TIME. Ignore new cards"
        return self.s.scalar(
            self.cardLimit(
            "revActive", "revInactive",
            "select count(*) from cards c where type between 0 and 1 "
            "and combinedDue < :lim"), lim=time)

    def newCardsDueBy(self, time):
        "Number of new cards due at TIME."
        return self.s.scalar(
            self.cardLimit(
            "newActive", "newInactive",
            "select count(*) from cards c where type = 2 "
            "and combinedDue < :lim"), lim=time)

    def deckFinishedMsg(self):
        spaceSusp = ""
        c= self.spacedCardCount()
        if c:
            spaceSusp += ngettext(
                'There is <b>%d delayed</b> card.',
                'There are <b>%d delayed</b> cards.', c) % c
        c2 = self.hiddenCards()
        if c2:
            if spaceSusp:
                spaceSusp += "<br>"
            spaceSusp += _(
                "Some cards are inactive or suspended.")
        if spaceSusp:
            spaceSusp = "<br><br>" + spaceSusp
        return _('''\
<div style="white-space: normal;">
<h1>Congratulations!</h1>You have finished for now.<br><br>
%(next)s
%(spaceSusp)s
</div>''') % {
    "next": self.nextDueMsg(),
    "spaceSusp": spaceSusp,
    }

    # Priorities
    ##########################################################################

    def updateAllPriorities(self, partial=False, dirty=True):
        "Update all card priorities if changed. Caller must .reset()"
        new = self.updateTagPriorities()
        if not partial:
            new = self.s.all("select id, priority as pri from tags")
        cids = self.s.column0(
            "select distinct cardId from cardTags where tagId in %s" %
                              ids2str([x['id'] for x in new]))
        self.updatePriorities(cids, dirty=dirty)

    def updateTagPriorities(self):
        "Update priority setting on tags table."
        # make sure all priority tags exist
        for s in (self.lowPriority, self.medPriority,
                  self.highPriority):
            tagIds(self.s, parseTags(s))
        tags = self.s.all("select tag, id, priority from tags")
        tags = [(x[0].lower(), x[1], x[2]) for x in tags]
        up = {}
        for (type, pri) in ((self.lowPriority, 1),
                            (self.medPriority, 3),
                            (self.highPriority, 4)):
            for tag in parseTags(type.lower()):
                up[tag] = pri
        new = []
        for (tag, id, pri) in tags:
            if tag in up and up[tag] != pri:
                new.append({'id': id, 'pri': up[tag]})
            elif tag not in up and pri != 2:
                new.append({'id': id, 'pri': 2})
        self.s.statements(
           "update tags set priority = :pri where id = :id",
           new)
        return new

    def updatePriorities(self, cardIds, suspend=[], dirty=True):
        "Update priorities for cardIds. Caller must .reset()."
        # any tags to suspend
        if suspend:
            ids = tagIds(self.s, suspend)
            self.s.statement(
                "update tags set priority = 0 where id in %s" %
                ids2str(ids.values()))
        if len(cardIds) > 1000:
            limit = ""
        else:
            limit = "and cardTags.cardId in %s" % ids2str(cardIds)
        cards = self.s.all("""
select cardTags.cardId,
case
when max(tags.priority) > 2 then max(tags.priority)
when min(tags.priority) = 1 then 1
else 2 end
from cardTags, tags
where cardTags.tagId = tags.id
%s
group by cardTags.cardId""" % limit)
        if dirty:
            extra = ", modified = :m "
        else:
            extra = ""
        for pri in range(5):
            cs = [c[0] for c in cards if c[1] == pri]
            if cs:
                # catch review early & buried but not suspended
                self.s.statement((
                    "update cards set priority = :pri %s where id in %s "
                    "and priority != :pri and priority >= -2") % (
                    extra, ids2str(cs)), pri=pri, m=time.time())

    def updatePriority(self, card):
        "Update priority on a single card."
        self.s.flush()
        self.updatePriorities([card.id])

    # Suspending
    ##########################################################################

    # when older clients are upgraded, we can remove the code which touches
    # priorities & isDue

    def suspendCards(self, ids):
        "Suspend cards. Caller must .reset()"
        self.startProgress()
        self.s.statement("""
update cards
set type = relativeDelay - 3,
priority = -3, modified = :t, isDue=0
where type >= 0 and id in %s""" % ids2str(ids), t=time.time())
        self.flushMod()
        self.finishProgress()

    def unsuspendCards(self, ids):
        "Unsuspend cards. Caller must .reset()"
        self.startProgress()
        self.s.statement("""
update cards set type = relativeDelay, priority=0, modified=:t
where type < 0 and id in %s""" %
            ids2str(ids), t=time.time())
        self.updatePriorities(ids)
        self.flushMod()
        self.finishProgress()

    def buryFact(self, fact):
        "Bury all cards for fact until next session. Caller must .reset()"
        for card in fact.cards:
            if card.type in (0,1,2):
                card.priority = -2
                card.type += 3
                card.isDue = 0
        self.flushMod()

    # Counts
    ##########################################################################

    def hiddenCards(self):
        "Assumes queue finished. True if some due cards have not been shown."
        return self.s.scalar("""
select 1 from cards where combinedDue < :now
and type between 0 and 1 limit 1""", now=self.dueCutoff)

    def newCardsDoneToday(self):
        return (self._dailyStats.newEase0 +
                self._dailyStats.newEase1 +
                self._dailyStats.newEase2 +
                self._dailyStats.newEase3 +
                self._dailyStats.newEase4)

    def spacedCardCount(self):
        "Number of spaced cards."
        return self.s.scalar("""
select count(cards.id) from cards where
combinedDue > :now and due < :now""", now=time.time())

    def isEmpty(self):
        return not self.cardCount

    def matureCardCount(self):
        return self.s.scalar(
            "select count(id) from cards where interval >= :t ",
            t=MATURE_THRESHOLD)

    def youngCardCount(self):
        return self.s.scalar(
            "select count(id) from cards where interval < :t "
            "and reps != 0", t=MATURE_THRESHOLD)

    def newCountAll(self):
        "All new cards, including spaced."
        return self.s.scalar(
            "select count(id) from cards where relativeDelay = 2")

    def seenCardCount(self):
        return self.s.scalar(
            "select count(id) from cards where relativeDelay between 0 and 1")

    # Card predicates
    ##########################################################################

    def cardState(self, card):
        if self.cardIsNew(card):
            return "new"
        elif card.interval > MATURE_THRESHOLD:
            return "mature"
        return "young"

    def cardIsNew(self, card):
        "True if a card has never been seen before."
        return card.reps == 0

    def cardIsBeingLearnt(self, card):
        "True if card should use present intervals."
        return card.lastInterval < 7

    def cardIsYoung(self, card):
        "True if card is not new and not mature."
        return (not self.cardIsNew(card) and
                not self.cardIsMature(card))

    def cardIsMature(self, card):
        return card.interval >= MATURE_THRESHOLD

    # Stats
    ##########################################################################

    def getStats(self, short=False):
        "Return some commonly needed stats."
        stats = anki.stats.getStats(self.s, self._globalStats, self._dailyStats)
        # add scheduling related stats
        stats['new'] = self.newCountToday
        stats['failed'] = self.failedSoonCount
        stats['rev'] = self.revCount
        if stats['dAverageTime']:
            stats['timeLeft'] = anki.utils.fmtTimeSpan(
                self.getETA(stats), pad=0, point=1, short=short)
        else:
            stats['timeLeft'] = _("Unknown")
        return stats

    def getETA(self, stats):
        # rev + new cards first, account for failures
        count = stats['rev'] + stats['new']
        count *= 1 + stats['gYoungNo%'] / 100.0
        left = count * stats['dAverageTime']
        # failed - higher time per card for higher amount of cards
        failedBaseMulti = 1.5
        failedMod = 0.07
        failedBaseCount = 20
        factor = (failedBaseMulti +
                  (failedMod * (stats['failed'] - failedBaseCount)))
        left += stats['failed'] * stats['dAverageTime'] * factor
        return left

    # Facts
    ##########################################################################

    def newFact(self, model=None):
        "Return a new fact with the current model."
        if model is None:
            model = self.currentModel
        return anki.facts.Fact(model)

    def addFact(self, fact, reset=True):
        "Add a fact to the deck. Return list of new cards."
        if not fact.model:
            fact.model = self.currentModel
        # validate
        fact.assertValid()
        fact.assertUnique(self.s)
        # check we have card models available
        cms = self.availableCardModels(fact)
        if not cms:
            return None
        # proceed
        cards = []
        self.s.save(fact)
        # update field cache
        self.factCount += 1
        self.flushMod()
        isRandom = self.newCardOrder == NEW_CARDS_RANDOM
        if isRandom:
            due = random.uniform(0, time.time())
        t = time.time()
        for cardModel in cms:
            created = fact.created + 0.00001*cardModel.ordinal
            card = anki.cards.Card(fact, cardModel, created)
            if isRandom:
                card.due = due
                card.combinedDue = due
            self.flushMod()
            cards.append(card)
        # update card q/a
        fact.setModified(True, self)
        self.updateFactTags([fact.id])
        # this will call reset() which will update counts
        self.updatePriorities([c.id for c in cards])
        # keep track of last used tags for convenience
        self.lastTags = fact.tags
        self.flushMod()
        if reset:
            self.reset()
        return fact

    def availableCardModels(self, fact, checkActive=True):
        "List of active card models that aren't empty for FACT."
        models = []
        for cardModel in fact.model.cardModels:
           if cardModel.active or not checkActive:
               ok = True
               for (type, format) in [("q", cardModel.qformat),
                                      ("a", cardModel.aformat)]:
                   # compat
                   format = re.sub("%\((.+?)\)s", "{{\\1}}", format)
                   empty = {}
                   local = {}; local.update(fact)
                   local['tags'] = u""
                   local['Tags'] = u""
                   local['cardModel'] = u""
                   local['modelName'] = u""
                   for k in local.keys():
                       empty[k] = u""
                       empty["text:"+k] = u""
                       local["text:"+k] = local[k]
                   empty['tags'] = ""
                   local['tags'] = fact.tags
                   try:
                       if (render(format, local) ==
                           render(format, empty)):
                           ok = False
                           break
                   except (KeyError, TypeError, ValueError):
                       ok = False
                       break
               if ok or type == "a" and cardModel.allowEmptyAnswer:
                   models.append(cardModel)
        return models

    def addCards(self, fact, cardModelIds):
        "Caller must flush first, flushMod after, rebuild priorities."
        ids = []
        for cardModel in self.availableCardModels(fact, False):
            if cardModel.id not in cardModelIds:
                continue
            if self.s.scalar("""
select count(id) from cards
where factId = :fid and cardModelId = :cmid""",
                                 fid=fact.id, cmid=cardModel.id) == 0:
                    # enough for 10 card models assuming 0.00001 timer precision
                    card = anki.cards.Card(
                        fact, cardModel,
                        fact.created+0.0001*cardModel.ordinal)
                    self.updateCardTags([card.id])
                    self.updatePriority(card)
                    self.cardCount += 1
                    self.newCount += 1
                    ids.append(card.id)

        if ids:
            fact.setModified(textChanged=True, deck=self)
            self.setModified()
        return ids

    def factIsInvalid(self, fact):
        "True if existing fact is invalid. Returns the error."
        try:
            fact.assertValid()
            fact.assertUnique(self.s)
        except FactInvalidError, e:
            return e

    def factUseCount(self, factId):
        "Return number of cards referencing a given fact id."
        return self.s.scalar("select count(id) from cards where factId = :id",
                             id=factId)

    def deleteFact(self, factId):
        "Delete a fact. Removes any associated cards. Don't flush."
        self.s.flush()
        # remove any remaining cards
        self.s.statement("insert into cardsDeleted select id, :time "
                         "from cards where factId = :factId",
                         time=time.time(), factId=factId)
        self.s.statement(
            "delete from cards where factId = :id", id=factId)
        # and then the fact
        self.deleteFacts([factId])
        self.setModified()

    def deleteFacts(self, ids):
        "Bulk delete facts by ID; don't touch cards. Caller must .reset()."
        if not ids:
            return
        self.s.flush()
        now = time.time()
        strids = ids2str(ids)
        self.s.statement("delete from facts where id in %s" % strids)
        self.s.statement("delete from fields where factId in %s" % strids)
        data = [{'id': id, 'time': now} for id in ids]
        self.s.statements("insert into factsDeleted values (:id, :time)", data)
        self.setModified()

    def deleteDanglingFacts(self):
        "Delete any facts without cards. Return deleted ids."
        ids = self.s.column0("""
select facts.id from facts
where facts.id not in (select distinct factId from cards)""")
        self.deleteFacts(ids)
        return ids

    def previewFact(self, oldFact, cms=None):
        "Duplicate fact and generate cards for preview. Don't add to deck."
        # check we have card models available
        if cms is None:
            cms = self.availableCardModels(oldFact, checkActive=True)
        if not cms:
            return []
        fact = self.cloneFact(oldFact)
        # proceed
        cards = []
        for cardModel in cms:
            card = anki.cards.Card(fact, cardModel)
            cards.append(card)
        fact.setModified(textChanged=True, deck=self, media=False)
        return cards

    def cloneFact(self, oldFact):
        "Copy fact into new session."
        model = self.s.query(Model).get(oldFact.model.id)
        fact = self.newFact(model)
        for field in fact.fields:
            fact[field.name] = oldFact[field.name]
        fact.tags = oldFact.tags
        return fact

    # Cards
    ##########################################################################

    def deleteCard(self, id):
        "Delete a card given its id. Delete any unused facts. Don't flush."
        self.deleteCards([id])

    def deleteCards(self, ids):
        "Bulk delete cards by ID. Caller must .reset()"
        if not ids:
            return
        self.s.flush()
        now = time.time()
        strids = ids2str(ids)
        self.startProgress()
        # grab fact ids
        factIds = self.s.column0("select factId from cards where id in %s"
                                 % strids)
        # drop from cards
        self.s.statement("delete from cards where id in %s" % strids)
        # note deleted
        data = [{'id': id, 'time': now} for id in ids]
        self.s.statements("insert into cardsDeleted values (:id, :time)", data)
        # gather affected tags
        tags = self.s.column0(
            "select tagId from cardTags where cardId in %s" %
            strids)
        # delete
        self.s.statement("delete from cardTags where cardId in %s" % strids)
        # find out if they're used by anything else
        unused = []
        for tag in tags:
            if not self.s.scalar(
                "select 1 from cardTags where tagId = :d limit 1", d=tag):
                unused.append(tag)
        # delete unused
        self.s.statement("delete from tags where id in %s and priority = 2" %
                         ids2str(unused))
        # remove any dangling facts
        self.deleteDanglingFacts()
        self.refreshSession()
        self.flushMod()
        self.finishProgress()

    # Models
    ##########################################################################

    def addModel(self, model):
        if model not in self.models:
            self.models.append(model)
        self.currentModel = model
        self.flushMod()

    def deleteModel(self, model):
        "Delete MODEL, and all its cards/facts. Caller must .reset()."
        if self.s.scalar("select count(id) from models where id=:id",
                         id=model.id):
            # delete facts/cards
            self.currentModel
            self.deleteCards(self.s.column0("""
select cards.id from cards, facts where
facts.modelId = :id and
facts.id = cards.factId""", id=model.id))
            # then the model
            self.models.remove(model)
            self.s.delete(model)
            self.s.flush()
            if self.currentModel == model:
                self.currentModel = self.models[0]
            self.s.statement("insert into modelsDeleted values (:id, :time)",
                             id=model.id, time=time.time())
            self.flushMod()
            self.refreshSession()
            self.setModified()

    def modelUseCount(self, model):
        "Return number of facts using model."
        return self.s.scalar("select count(facts.modelId) from facts "
                             "where facts.modelId = :id",
                             id=model.id)

    def deleteEmptyModels(self):
        for model in self.models:
            if not self.modelUseCount(model):
                self.deleteModel(model)

    def rebuildCSS(self):
        # css for all fields
        def _genCSS(prefix, row):
            (id, fam, siz, col, align, rtl, pre) = row
            t = ""
            if fam: t += 'font-family:"%s";' % toPlatformFont(fam)
            if siz: t += 'font-size:%dpx;' % siz
            if col: t += 'color:%s;' % col
            if rtl == "rtl":
                t += "direction:rtl;unicode-bidi:embed;"
            if pre:
                t += "white-space:pre-wrap;"
            if align != -1:
                if align == 0: align = "center"
                elif align == 1: align = "left"
                else: align = "right"
                t += 'text-align:%s;' % align
            if t:
                t = "%s%s {%s}\n" % (prefix, hexifyID(id), t)
            return t
        css = "".join([_genCSS(".fm", row) for row in self.s.all("""
select id, quizFontFamily, quizFontSize, quizFontColour, -1,
  features, editFontFamily from fieldModels""")])
        cardRows = self.s.all("""
select id, null, null, null, questionAlign, 0, 0 from cardModels""")
        css += "".join([_genCSS("#cmq", row) for row in cardRows])
        css += "".join([_genCSS("#cma", row) for row in cardRows])
        css += "".join([".cmb%s {background:%s;}\n" %
        (hexifyID(row[0]), row[1]) for row in self.s.all("""
select id, lastFontColour from cardModels""")])
        self.css = css
        self.setVar("cssCache", css, mod=False)
        self.addHexCache()
        return css

    def addHexCache(self):
        ids = self.s.column0("""
select id from fieldModels union
select id from cardModels union
select id from models""")
        cache = {}
        for id in ids:
            cache[id] = hexifyID(id)
        self.setVar("hexCache", simplejson.dumps(cache), mod=False)

    def copyModel(self, oldModel):
        "Add a new model to DB based on MODEL."
        m = Model(_("%s copy") % oldModel.name)
        for f in oldModel.fieldModels:
            f = f.copy()
            m.addFieldModel(f)
        for c in oldModel.cardModels:
            c = c.copy()
            m.addCardModel(c)
        for attr in ("tags", "spacing", "initialSpacing"):
            setattr(m, attr, getattr(oldModel, attr))
        self.addModel(m)
        return m

    def changeModel(self, factIds, newModel, fieldMap, cardMap):
        "Caller must .reset()"
        self.s.flush()
        fids = ids2str(factIds)
        changed = False
        # field remapping
        if fieldMap:
            changed = True
            self.startProgress(len(fieldMap)+2)
            seen = {}
            for (old, new) in fieldMap.items():
                self.updateProgress(_("Changing fields..."))
                seen[new] = 1
                if new:
                    # can rename
                    self.s.statement("""
update fields set
fieldModelId = :new,
ordinal = :ord
where fieldModelId = :old
and factId in %s""" % fids, new=new.id, ord=new.ordinal, old=old.id)
                else:
                    # no longer used
                    self.s.statement("""
delete from fields where factId in %s
and fieldModelId = :id""" % fids, id=old.id)
            # new
            for field in newModel.fieldModels:
                self.updateProgress()
                if field not in seen:
                    d = [{'id': genID(),
                          'fid': f,
                          'fmid': field.id,
                          'ord': field.ordinal}
                         for f in factIds]
                    self.s.statements('''
insert into fields
(id, factId, fieldModelId, ordinal, value)
values
(:id, :fid, :fmid, :ord, "")''', d)
            # fact modtime
            self.updateProgress()
            self.s.statement("""
update facts set
modified = :t,
modelId = :id
where id in %s""" % fids, t=time.time(), id=newModel.id)
            self.finishProgress()
        # template remapping
        self.startProgress(len(cardMap)+4)
        toChange = []
        self.updateProgress(_("Changing cards..."))
        for (old, new) in cardMap.items():
            if not new:
                # delete
                self.s.statement("""
delete from cards
where cardModelId = :cid and
factId in %s""" % fids, cid=old.id)
            elif old != new:
                # gather ids so we can rename x->y and y->x
                ids = self.s.column0("""
select id from cards where
cardModelId = :id and factId in %s""" % fids, id=old.id)
                toChange.append((new, ids))
        for (new, ids) in toChange:
            self.updateProgress()
            self.s.statement("""
update cards set
cardModelId = :new,
ordinal = :ord
where id in %s""" % ids2str(ids), new=new.id, ord=new.ordinal)
        self.updateProgress()
        self.updateCardQACacheFromIds(factIds, type="facts")
        self.flushMod()
        self.updateProgress()
        cardIds = self.s.column0(
            "select id from cards where factId in %s" %
            ids2str(factIds))
        self.updateCardTags(cardIds)
        self.updateProgress()
        self.updatePriorities(cardIds)
        self.updateProgress()
        self.refreshSession()
        self.finishProgress()

    # Fields
    ##########################################################################

    def allFields(self):
        "Return a list of all possible fields across all models."
        return self.s.column0("select distinct name from fieldmodels")

    def deleteFieldModel(self, model, field):
        self.startProgress()
        self.s.statement("delete from fields where fieldModelId = :id",
                         id=field.id)
        self.s.statement("update facts set modified = :t where modelId = :id",
                         id=model.id, t=time.time())
        model.fieldModels.remove(field)
        # update q/a formats
        for cm in model.cardModels:
            types = ("%%(%s)s" % field.name,
                     "%%(text:%s)s" % field.name,
                     # new style
                     "<<%s>>" % field.name,
                     "<<text:%s>>" % field.name)
            for t in types:
                for fmt in ('qformat', 'aformat'):
                    setattr(cm, fmt, getattr(cm, fmt).replace(t, ""))
        self.updateCardsFromModel(model)
        model.setModified()
        self.flushMod()
        self.finishProgress()

    def addFieldModel(self, model, field):
        "Add FIELD to MODEL and update cards."
        model.addFieldModel(field)
        # commit field to disk
        self.s.flush()
        self.s.statement("""
insert into fields (factId, fieldModelId, ordinal, value)
select facts.id, :fmid, :ordinal, "" from facts
where facts.modelId = :mid""", fmid=field.id, mid=model.id, ordinal=field.ordinal)
        # ensure facts are marked updated
        self.s.statement("""
update facts set modified = :t where modelId = :mid"""
                         , t=time.time(), mid=model.id)
        model.setModified()
        self.flushMod()

    def renameFieldModel(self, model, field, newName):
        "Change FIELD's name in MODEL and update FIELD in all facts."
        for cm in model.cardModels:
            types = ("%%(%s)s",
                     "%%(text:%s)s",
                     # new styles
                     "{{%s}}",
                     "{{text:%s}}",
                     "{{#%s}}",
                     "{{^%s}}",
                     "{{/%s}}")
            for t in types:
                for fmt in ('qformat', 'aformat'):
                    setattr(cm, fmt, getattr(cm, fmt).replace(t%field.name,
                                                              t%newName))
        field.name = newName
        model.setModified()
        self.flushMod()

    def fieldModelUseCount(self, fieldModel):
        "Return the number of cards using fieldModel."
        return self.s.scalar("""
select count(id) from fields where
fieldModelId = :id and value != ""
""", id=fieldModel.id)

    def rebuildFieldOrdinals(self, modelId, ids):
        """Update field ordinal for all fields given field model IDS.
Caller must update model modtime."""
        self.s.flush()
        strids = ids2str(ids)
        self.s.statement("""
update fields
set ordinal = (select ordinal from fieldModels where id = fieldModelId)
where fields.fieldModelId in %s""" % strids)
        # dirty associated facts
        self.s.statement("""
update facts
set modified = strftime("%s", "now")
where modelId = :id""", id=modelId)
        self.flushMod()

    # Card models
    ##########################################################################

    def cardModelUseCount(self, cardModel):
        "Return the number of cards using cardModel."
        return self.s.scalar("""
select count(id) from cards where
cardModelId = :id""", id=cardModel.id)

    def deleteCardModel(self, model, cardModel):
        "Delete all cards that use CARDMODEL from the deck."
        cards = self.s.column0("select id from cards where cardModelId = :id",
                               id=cardModel.id)
        self.deleteCards(cards)
        model.cardModels.remove(cardModel)
        model.setModified()
        self.flushMod()

    def updateCardsFromModel(self, model, dirty=True):
        "Update all card question/answer when model changes."
        ids = self.s.all("""
select cards.id, cards.cardModelId, cards.factId, facts.modelId from
cards, facts where
cards.factId = facts.id and
facts.modelId = :id""", id=model.id)
        if not ids:
            return
        self.updateCardQACache(ids, dirty)

    def updateCardsFromFactIds(self, ids, dirty=True):
        "Update all card question/answer when model changes."
        ids = self.s.all("""
select cards.id, cards.cardModelId, cards.factId, facts.modelId from
cards, facts where
cards.factId = facts.id and
facts.id in %s""" % ids2str(ids))
        if not ids:
            return
        self.updateCardQACache(ids, dirty)

    def updateCardQACacheFromIds(self, ids, type="cards"):
        "Given a list of card or fact ids, update q/a cache."
        if type == "facts":
            # convert to card ids
            ids = self.s.column0(
                "select id from cards where factId in %s" % ids2str(ids))
        rows = self.s.all("""
select c.id, c.cardModelId, f.id, f.modelId
from cards as c, facts as f
where c.factId = f.id
and c.id in %s""" % ids2str(ids))
        self.updateCardQACache(rows)

    def updateCardQACache(self, ids, dirty=True):
        "Given a list of (cardId, cardModelId, factId, modId), update q/a cache."
        if dirty:
            mod = ", modified = %f" % time.time()
        else:
            mod = ""
        # tags
        cids = ids2str([x[0] for x in ids])
        tags = dict([(x[0], x[1:]) for x in
                     self.splitTagsList(
            where="and cards.id in %s" % cids)])
        facts = {}
        # fields
        for k, g in groupby(self.s.all("""
select fields.factId, fieldModels.name, fieldModels.id, fields.value
from fields, fieldModels where fields.factId in %s and
fields.fieldModelId = fieldModels.id
order by fields.factId""" % ids2str([x[2] for x in ids])),
                            itemgetter(0)):
            facts[k] = dict([(r[1], (r[2], r[3])) for r in g])
        # card models
        cms = {}
        for c in self.s.query(CardModel).all():
            cms[c.id] = c
        pend = [formatQA(cid, mid, facts[fid], tags[cid], cms[cmid], self)
                for (cid, cmid, fid, mid) in ids]
        if pend:
            # find existing media references
            files = {}
            for txt in self.s.column0(
                "select question || answer from cards where id in %s" %
                cids):
                for f in mediaFiles(txt):
                    if f in files:
                        files[f] -= 1
                    else:
                        files[f] = -1
            # determine ref count delta
            for p in pend:
                for type in ("question", "answer"):
                    txt = p[type]
                    for f in mediaFiles(txt):
                        if f in files:
                            files[f] += 1
                        else:
                            files[f] = 1
            # update references - this could be more efficient
            for (f, cnt) in files.items():
                if not cnt:
                    continue
                updateMediaCount(self, f, cnt)
            # update q/a
            self.s.execute("""
    update cards set
    question = :question, answer = :answer
    %s
    where id = :id""" % mod, pend)
            # update fields cache
            self.updateFieldCache(facts.keys())
        if dirty:
            self.flushMod()

    def updateFieldCache(self, fids):
        "Add stripped HTML cache for sorting/searching."
        try:
            all = self.s.all(
                ("select factId, group_concat(value, ' ') from fields "
                 "where factId in %s group by factId") % ids2str(fids))
        except:
            # older sqlite doesn't support group_concat. this code taken from
            # the wm port
            all=[]
            for factId in fids:
                values=self.s.all("select value from fields where value is not NULL and factId=%(factId)i" % {"factId": factId})
                value_list=[]
                for row in values:
                        value_list.append(row[0])
                concatenated_values=' '.join(value_list)
                all.append([factId, concatenated_values])
        r = []
        from anki.utils import stripHTMLMedia
        for a in all:
            r.append({'id':a[0], 'v':stripHTMLMedia(a[1])})
        self.s.statements(
            "update facts set spaceUntil=:v where id=:id", r)

    def rebuildCardOrdinals(self, ids):
        "Update all card models in IDS. Caller must update model modtime."
        self.s.flush()
        strids = ids2str(ids)
        self.s.statement("""
update cards set
ordinal = (select ordinal from cardModels where id = cardModelId),
modified = :now
where cardModelId in %s""" % strids, now=time.time())
        self.flushMod()

    def changeCardModel(self, cardIds, newCardModelId):
        self.s.statement("""
update cards set cardModelId = :newId
where id in %s""" % ids2str(cardIds), newId=newCardModelId)
        self.updateCardQACacheFromIds(cardIds)
        self.flushMod()

    # Tags: querying
    ##########################################################################

    def tagsList(self, where="", priority=", cards.priority", kwargs={}):
        "Return a list of (cardId, allTags, priority)"
        return self.s.all("""
select cards.id, facts.tags || " " || models.tags || " " ||
cardModels.name %s from cards, facts, models, cardModels where
cards.factId == facts.id and facts.modelId == models.id
and cards.cardModelId = cardModels.id %s""" % (priority, where),
                          **kwargs)

        return self.s.all("""
select cards.id, facts.tags || " " || models.tags || " " ||
cardModels.name %s from cards, facts, models, cardModels where
cards.factId == facts.id and facts.modelId == models.id
and cards.cardModelId = cardModels.id %s""" % (priority, where))

    def splitTagsList(self, where=""):
        return self.s.all("""
select cards.id, facts.tags, models.tags, cardModels.name
from cards, facts, models, cardModels where
cards.factId == facts.id and facts.modelId == models.id
and cards.cardModelId = cardModels.id
%s""" % where)

    def cardsWithNoTags(self):
        return self.s.column0("""
select cards.id from cards, facts where
facts.tags = ""
and cards.factId = facts.id""")

    def cardsWithTags(self, tagStr, search="and"):
        tagIds = []
        # get ids
        for tag in tagStr.split(" "):
            tag = tag.replace("*", "%")
            if "%" in tag:
                ids = self.s.column0(
                    "select id from tags where tag like :tag", tag=tag)
                if search == "and" and not ids:
                    return []
                tagIds.append(ids)
            else:
                id = self.s.scalar(
                    "select id from tags where tag = :tag", tag=tag)
                if search == "and" and not id:
                    return []
                tagIds.append(id)
        # search for any
        if search == "or":
            return self.s.column0(
                "select cardId from cardTags where tagId in %s" %
                ids2str(tagIds))
        else:
            # search for all
            l = []
            for ids in tagIds:
                if isinstance(ids, types.ListType):
                    l.append("select cardId from cardTags where tagId in %s" %
                             ids2str(ids))
                else:
                    l.append("select cardId from cardTags where tagId = %d" %
                             ids)
            q = " intersect ".join(l)
            return self.s.column0(q)

    def allTags(self):
        return self.s.column0("select tag from tags order by tag")

    def allTags_(self, where=""):
        t = self.s.column0("select tags from facts %s" % where)
        t += self.s.column0("select tags from models")
        t += self.s.column0("select name from cardModels")
        return sorted(list(set(parseTags(joinTags(t)))))

    def allUserTags(self):
        return sorted(list(set(parseTags(joinTags(self.s.column0(
            "select tags from facts"))))))

    def factTags(self, ids):
        return self.s.all("""
select id, tags from facts
where id in %s""" % ids2str(ids))

    # Tags: caching
    ##########################################################################

    def updateFactTags(self, factIds):
        self.updateCardTags(self.s.column0(
            "select id from cards where factId in %s" %
            ids2str(factIds)))

    def updateModelTags(self, modelId):
        self.updateCardTags(self.s.column0("""
select cards.id from cards, facts where
cards.factId = facts.id and
facts.modelId = :id""", id=modelId))

    def updateCardTags(self, cardIds=None):
        self.s.flush()
        if cardIds is None:
            self.s.statement("delete from cardTags")
            self.s.statement("delete from tags")
            tids = tagIds(self.s, self.allTags_())
            rows = self.splitTagsList()
        else:
            self.s.statement("delete from cardTags where cardId in %s" %
                             ids2str(cardIds))
            fids = ids2str(self.s.column0(
                "select factId from cards where id in %s" %
                ids2str(cardIds)))
            tids = tagIds(self.s, self.allTags_(
                where="where id in %s" % fids))
            rows = self.splitTagsList(
                where="and facts.id in %s" % fids)
        d = []
        for (id, fact, model, templ) in rows:
            for tag in parseTags(fact):
                d.append({"cardId": id,
                          "tagId": tids[tag.lower()],
                          "src": 0})
            for tag in parseTags(model):
                d.append({"cardId": id,
                          "tagId": tids[tag.lower()],
                          "src": 1})
            for tag in parseTags(templ):
                d.append({"cardId": id,
                          "tagId": tids[tag.lower()],
                          "src": 2})
        if d:
            self.s.statements("""
insert into cardTags
(cardId, tagId, src) values
(:cardId, :tagId, :src)""", d)
        self.s.execute(
            "delete from tags where priority = 2 and id not in "+
            "(select distinct tagId from cardTags)")

    def updateTagsForModel(self, model):
        cards = self.s.all("""
select cards.id, cards.cardModelId from cards, facts where
facts.modelId = :m and cards.factId = facts.id""", m=model.id)
        cardIds = [x[0] for x in cards]
        factIds = self.s.column0("""
select facts.id from facts where
facts.modelId = :m""", m=model.id)
        cmtags = " ".join([cm.name for cm in model.cardModels])
        tids = tagIds(self.s, parseTags(model.tags) +
                      parseTags(cmtags))
        self.s.statement("""
delete from cardTags where cardId in %s
and src in (1, 2)""" % ids2str(cardIds))
        d = []
        for tag in parseTags(model.tags):
            for id in cardIds:
                d.append({"cardId": id,
                          "tagId": tids[tag.lower()],
                          "src": 1})
        cmtags = {}
        for cm in model.cardModels:
            cmtags[cm.id] = parseTags(cm.name)
        for c in cards:
            for tag in cmtags[c[1]]:
                d.append({"cardId": c[0],
                          "tagId": tids[tag.lower()],
                          "src": 2})
        if d:
            self.s.statements("""
insert into cardTags
(cardId, tagId, src) values
(:cardId, :tagId, :src)""", d)
        self.s.statement("""
delete from tags where id not in (select distinct tagId from cardTags)
and priority = 2
""")

    # Tags: adding/removing in bulk
    ##########################################################################
    # these could be optimized to use the tag cache in the future

    def addTags(self, ids, tags):
        "Add tags in bulk. Caller must .reset()"
        self.startProgress()
        tlist = self.factTags(ids)
        newTags = parseTags(tags)
        now = time.time()
        pending = []
        for (id, tags) in tlist:
            oldTags = parseTags(tags)
            tmpTags = list(set(oldTags + newTags))
            if tmpTags != oldTags:
                pending.append(
                    {'id': id, 'now': now, 'tags': " ".join(tmpTags)})
        self.s.statements("""
update facts set
tags = :tags,
modified = :now
where id = :id""", pending)
        factIds = [c['id'] for c in pending]
        cardIds = self.s.column0(
            "select id from cards where factId in %s" %
            ids2str(factIds))
        self.updateCardQACacheFromIds(factIds, type="facts")
        self.updateCardTags(cardIds)
        self.updatePriorities(cardIds)
        self.flushMod()
        self.finishProgress()
        self.refreshSession()

    def deleteTags(self, ids, tags):
        "Delete tags in bulk. Caller must .reset()"
        self.startProgress()
        tlist = self.factTags(ids)
        newTags = parseTags(tags)
        now = time.time()
        pending = []
        for (id, tags) in tlist:
            oldTags = parseTags(tags)
            tmpTags = oldTags[:]
            for tag in newTags:
                try:
                    tmpTags.remove(tag)
                except ValueError:
                    pass
            if tmpTags != oldTags:
                pending.append(
                    {'id': id, 'now': now, 'tags': " ".join(tmpTags)})
        self.s.statements("""
update facts set
tags = :tags,
modified = :now
where id = :id""", pending)
        factIds = [c['id'] for c in pending]
        cardIds = self.s.column0(
            "select id from cards where factId in %s" %
            ids2str(factIds))
        self.updateCardQACacheFromIds(factIds, type="facts")
        self.updateCardTags(cardIds)
        self.updatePriorities(cardIds)
        self.flushMod()
        self.finishProgress()
        self.refreshSession()

    # Find
    ##########################################################################

    def allFMFields(self, tolower=False):
        fields = []
        try:
            fields = self.s.column0(
                "select distinct name from fieldmodels order by name")
        except:
            fields = []
        if tolower is True:
            for i, v in enumerate(fields):
                fields[i] = v.lower()
        return fields

    def _parseQuery(self, query):
        tokens = []
        res = []

        allowedfields = self.allFMFields(True)
        def addSearchFieldToken(field, value, isNeg, filter):
            if field.lower() in allowedfields:
                res.append((field + ':' + value, isNeg, SEARCH_FIELD, filter))
            elif field in ['question', 'answer']:
                res.append((field + ':' + value, isNeg, SEARCH_QA, filter))
            else:
                for p in phraselog:
                    res.append((p['value'], p['is_neg'], p['type'], p['filter']))
        # break query into words or phraselog
        # an extra space is added so the loop never ends in the middle
        # completing a token
        for match in re.findall(
            r'(-)?\'(([^\'\\]|\\.)*)\'|(-)?"(([^"\\]|\\.)*)"|(-)?([^ ]+)|([ ]+)',
            query + ' '):
            type = ' '
            if match[1]: type = "'"
            elif match[4]: type = '"'

            value = (match[1] or match[4] or match[7])
            isNeg = (match[0] == '-' or match[3] == '-' or match[6] == '-')

            tokens.append({'type': type, 'value': value, 'is_neg': isNeg,
                           'filter': ('wb' if type == "'" else 'none')})
        intoken = isNeg = False
        field = '' #name of the field for field related commands
        phraselog = [] #log of phrases in case potential command is not a commad
        for c, token in enumerate(tokens):
            doprocess = True # only look for commands when this is true
            #prevent cases such as "field" : value as being processed as a command
            if len(token['value']) == 0:
                if intoken is True and type == SEARCH_FIELD and field:
                #case: fieldname: any thing here check for existance of fieldname
                    addSearchFieldToken(field, '*', isNeg, 'none')
                    phraselog = [] # reset phrases since command is completed
                intoken = doprocess = False
            if intoken is True:
                if type == SEARCH_FIELD_EXISTS:
                #case: field:"value"
                    res.append((token['value'], isNeg, type, 'none'))
                    intoken = doprocess = False
                elif type == SEARCH_FIELD and field:
                #case: fieldname:"value"
                    addSearchFieldToken(
                        field, token['value'], isNeg, token['filter'])
                    intoken = doprocess = False

                elif type == SEARCH_FIELD and not field:
                #case: "fieldname":"name" or "field" anything
                    if token['value'].startswith(":") and len(phraselog) == 1:
                        #we now know a colon is next, so mark it as field
                        # and keep looking for the value
                        field = phraselog[0]['value']
                        parts = token['value'].split(':', 1)
                        phraselog.append(
                            {'value': token['value'], 'is_neg': False,
                             'type': SEARCH_PHRASE, 'filter': token['filter']})
                        if parts[1]:
                            #value is included with the :, so wrap it up
                            addSearchFieldToken(field, parts[1], isNeg, 'none')
                            intoken = doprocess = False
                        doprocess = False
                    else:
                    #case: "fieldname"string/"fieldname"tag:name
                        intoken = False
                if intoken is False and doprocess is False:
                #command has been fully processed
                    phraselog = [] # reset phraselog, since we used it for a command
            if intoken is False:
                #include any non-command related phrases in the query
                for p in phraselog: res.append(
                    (p['value'], p['is_neg'], p['type'], p['filter']))
                phraselog = []
            if intoken is False and doprocess is True:
                field = ''
                isNeg = token['is_neg']
                if token['value'].startswith("tag:"):
                    token['value'] = token['value'][4:]
                    type = SEARCH_TAG
                elif token['value'].startswith("is:"):
                    token['value'] = token['value'][3:].lower()
                    type = SEARCH_TYPE
                elif token['value'].startswith("fid:") and len(token['value']) > 4:
                    dec = token['value'][4:]
                    try:
                        int(dec)
                        token['value'] = token['value'][4:]
                    except:
                        try:
                            for d in dec.split(","):
                                int(d)
                            token['value'] = token['value'][4:]
                        except:
                            token['value'] = "0"
                    type = SEARCH_FID
                elif token['value'].startswith("card:"):
                    token['value'] = token['value'][5:]
                    type = SEARCH_CARD
                elif token['value'].startswith("show:"):
                    token['value'] = token['value'][5:].lower()
                    type = SEARCH_DISTINCT
                elif token['value'].startswith("field:"):
                    type = SEARCH_FIELD_EXISTS
                    parts = token['value'][6:].split(':', 1)
                    field = parts[0]
                    if len(parts) == 1 and parts[0]:
                        token['value'] = parts[0]
                    elif len(parts) == 1 and not parts[0]:
                        intoken = True
                else:
                    type = SEARCH_FIELD
                    intoken = True
                    parts = token['value'].split(':', 1)

                    phraselog.append(
                        {'value': token['value'], 'is_neg': isNeg,
                         'type': SEARCH_PHRASE, 'filter': token['filter']})
                    if len(parts) == 2 and parts[0]:
                        field = parts[0]
                        if parts[1]:
                            #simple fieldname:value case - no need to look for more data
                            addSearchFieldToken(field, parts[1], isNeg, 'none')
                            intoken = doprocess = False

                    if intoken is False: phraselog = []
                if intoken is False and doprocess is True:
                    res.append((token['value'], isNeg, type, token['filter']))
        return res

    def findCards(self, query):
        (q, cmquery, showdistinct, filters, args) = self.findCardsWhere(query)
        (factIdList, cardIdList) = self.findCardsMatchingFilters(filters)
        query = "select id from cards"
        hasWhere = False
        if q:
            query += " where " + q
            hasWhere = True
        if cmquery['pos'] or cmquery['neg']:
            if hasWhere is False:
                query += " where "
                hasWhere = True
            else: query += " and "
            if cmquery['pos']:
                query += (" factId in(select distinct factId from cards "+
                          "where id in (" + cmquery['pos'] + ")) ")
                query += " and id in(" + cmquery['pos'] + ") "
            if cmquery['neg']:
                query += (" factId not in(select distinct factId from "+
                          "cards where id in (" + cmquery['neg'] + ")) ")
        if factIdList is not None:
            if hasWhere is False:
                query += " where "
                hasWhere = True
            else: query += " and "
            query += " factId IN %s" % ids2str(factIdList)
        if cardIdList is not None:
            if hasWhere is False:
                query += " where "
                hasWhere = True
            else: query += " and "
            query += " id IN %s" % ids2str(cardIdList)
        if showdistinct:
            query += " group by factId"
        #print query, args
        return self.s.column0(query, **args)

    def findCardsWhere(self, query):
        (tquery, fquery, qquery, fidquery, cmquery, sfquery, qaquery,
         showdistinct, filters, args) = self._findCards(query)
        q = ""
        x = []
        if tquery:
            x.append(" id in (%s)" % tquery)
        if fquery:
            x.append(" factId in (%s)" % fquery)
        if qquery:
            x.append(" id in (%s)" % qquery)
        if fidquery:
            x.append(" id in (%s)" % fidquery)
        if sfquery:
            x.append(" factId in (%s)" % sfquery)
        if qaquery:
            x.append(" id in (%s)" % qaquery)
        if x:
            q += " and ".join(x)
        return q, cmquery, showdistinct, filters, args

    def findCardsMatchingFilters(self, filters):
        factFilters = []
        fieldFilters = {}
        cardFilters = {}

        factFilterMatches = []
        fieldFilterMatches = []
        cardFilterMatches = []

        if (len(filters) > 0):
            for filter in filters:
                if filter['scope'] == 'fact':
                    regexp = re.compile(
                        r'\b' + re.escape(filter['value']) + r'\b', flags=re.I)
                    factFilters.append(
                        {'value': filter['value'], 'regexp': regexp,
                         'is_neg': filter['is_neg']})
                if filter['scope'] == 'field':
                    fieldName = filter['field'].lower()
                    if (fieldName in fieldFilters) is False:
                        fieldFilters[fieldName] = []
                    regexp = re.compile(
                        r'\b' + re.escape(filter['value']) + r'\b', flags=re.I)
                    fieldFilters[fieldName].append(
                        {'value': filter['value'], 'regexp': regexp,
                         'is_neg': filter['is_neg']})
                if filter['scope'] == 'card':
                    fieldName = filter['field'].lower()
                    if (fieldName in cardFilters) is False:
                        cardFilters[fieldName] = []
                    regexp = re.compile(r'\b' + re.escape(filter['value']) +
                                        r'\b', flags=re.I)
                    cardFilters[fieldName].append(
                        {'value': filter['value'], 'regexp': regexp,
                         'is_neg': filter['is_neg']})

            if len(factFilters) > 0:
                fquery = ''
                args = {}
                for filter in factFilters:
                    c = len(args)
                    if fquery:
                        if filter['is_neg']: fquery += " except "
                        else: fquery += " intersect "
                    elif filter['is_neg']: fquery += "select id from fields except "

                    value = filter['value'].replace("*", "%")
                    args["_ff_%d" % c] = "%"+value+"%"

                    fquery += (
                        "select id from fields where value like "+
                        ":_ff_%d escape '\\'" % c)

                rows = self.s.execute(
                    'select factId, value from fields where id in (' +
                    fquery + ')', args)
                while (1):
                    row = rows.fetchone()
                    if row is None: break
                    doesMatch = False
                    for filter in factFilters:
                        res = filter['regexp'].search(row[1])
                        if ((filter['is_neg'] is False and res) or
                            (filter['is_neg'] is True and res is None)):
                            factFilterMatches.append(row[0])

            if len(fieldFilters) > 0:
                sfquery = ''
                args = {}
                for field, filters in fieldFilters.iteritems():
                    for filter in filters:
                        c = len(args)
                        if sfquery:
                            if filter['is_neg']:  sfquery += " except "
                            else: sfquery += " intersect "
                        elif filter['is_neg']: sfquery += "select id from fields except "
                        field = field.replace("*", "%")
                        value = filter['value'].replace("*", "%")
                        args["_ff_%d" % c] = "%"+value+"%"

                        ids = self.s.column0(
                            "select id from fieldmodels where name like "+
                            ":field escape '\\'", field=field)
                        sfquery += ("select id from fields where "+
                                    "fieldModelId in %s and value like "+
                                    ":_ff_%d escape '\\'") % (ids2str(ids), c)

                rows = self.s.execute(
                    'select f.factId, f.value, fm.name from fields as f '+
                    'left join fieldmodels as fm ON (f.fieldModelId = '+
                    'fm.id) where f.id in (' + sfquery + ')', args)
                while (1):
                    row = rows.fetchone()
                    if row is None: break
                    field = row[2].lower()
                    doesMatch = False
                    if field in fieldFilters:
                        for filter in fieldFilters[field]:
                            res = filter['regexp'].search(row[1])
                            if ((filter['is_neg'] is False and res) or
                                (filter['is_neg'] is True and res is None)):
                                fieldFilterMatches.append(row[0])


            if len(cardFilters) > 0:
                qaquery = ''
                args = {}
                for field, filters in cardFilters.iteritems():
                    for filter in filters:
                        c = len(args)
                        if qaquery:
                            if filter['is_neg']: qaquery += " except "
                            else: qaquery += " intersect "
                        elif filter['is_neg']: qaquery += "select id from cards except "
                        value = value.replace("*", "%")
                        args["_ff_%d" % c] = "%"+value+"%"

                        if field == 'question':
                            qaquery += "select id from cards where question "
                            qaquery += "like :_ff_%d escape '\\'" % c
                        else:
                            qaquery += "select id from cards where answer "
                            qaquery += "like :_ff_%d escape '\\'" % c

                rows = self.s.execute(
                    'select id, question, answer from cards where id IN (' +
                    qaquery + ')', args)
                while (1):
                    row = rows.fetchone()
                    if row is None: break
                    doesMatch = False
                    if field in cardFilters:
                        rowValue = row[1] if field == 'question' else row[2]
                        for filter in cardFilters[field]:
                            res = filter['regexp'].search(rowValue)
                            if ((filter['is_neg'] is False and res) or
                                (filter['is_neg'] is True and res is None)):
                                cardFilterMatches.append(row[0])

        factIds = None
        if len(factFilters) > 0 or len(fieldFilters) > 0:
            factIds = []
            factIds.extend(factFilterMatches)
            factIds.extend(fieldFilterMatches)

        cardIds = None
        if len(cardFilters) > 0:
            cardIds = []
            cardIds.extend(cardFilterMatches)

        return (factIds, cardIds)

    def _findCards(self, query):
        "Find facts matching QUERY."
        tquery = ""
        fquery = ""
        qquery = ""
        fidquery = ""
        cmquery = { 'pos': '', 'neg': '' }
        sfquery = qaquery = ""
        showdistinct = False
        filters = []
        args = {}
        for c, (token, isNeg, type, filter) in enumerate(self._parseQuery(query)):
            if type == SEARCH_TAG:
                # a tag
                if tquery:
                    if isNeg:
                        tquery += " except "
                    else:
                        tquery += " intersect "
                elif isNeg:
                    tquery += "select id from cards except "
                if token == "none":
                    tquery += """
select cards.id from cards, facts where facts.tags = '' and cards.factId = facts.id """
                else:
                    token = token.replace("*", "%")
                    ids = self.s.column0("""
select id from tags where tag like :tag escape '\\'""", tag=token)
                    tquery += """
select cardId from cardTags where cardTags.tagId in %s""" % ids2str(ids)
            elif type == SEARCH_TYPE:
                if qquery:
                    if isNeg:
                        qquery += " except "
                    else:
                        qquery += " intersect "
                elif isNeg:
                    qquery += "select id from cards except "
                if token in ("rev", "new", "failed"):
                    if token == "rev":
                        n = 1
                    elif token == "new":
                        n = 2
                    else:
                        n = 0
                    qquery += "select id from cards where type = %d" % n
                elif token == "delayed":
                    qquery += ("select id from cards where "
                               "due < %d and combinedDue > %d and "
                               "type in (0,1,2)") % (
                        self.dueCutoff, self.dueCutoff)
                elif token == "suspended":
                    qquery += ("select id from cards where "
                               "priority = -3")
                elif token == "leech":
                    qquery += (
                        "select id from cards where noCount >= (select value "
                        "from deckvars where key = 'leechFails')")
                else: # due
                    qquery += ("select id from cards where "
                               "type in (0,1) and combinedDue < %d") % self.dueCutoff
            elif type == SEARCH_FID:
                if fidquery:
                    if isNeg:
                        fidquery += " except "
                    else:
                        fidquery += " intersect "
                elif isNeg:
                    fidquery += "select id from cards except "
                fidquery += "select id from cards where factId in (%s)" % token
            elif type == SEARCH_CARD:
                token = token.replace("*", "%")
                ids = self.s.column0("""
select id from tags where tag like :tag escape '\\'""", tag=token)
                if isNeg:
                    if cmquery['neg']:
                        cmquery['neg'] += " intersect "
                    cmquery['neg'] += """
select cardId from cardTags where src = 2 and cardTags.tagId in %s""" % ids2str(ids)
                else:
                    if cmquery['pos']:
                        cmquery['pos'] += " intersect "
                    cmquery['pos'] += """
select cardId from cardTags where src = 2 and cardTags.tagId in %s""" % ids2str(ids)
            elif type == SEARCH_FIELD or type == SEARCH_FIELD_EXISTS:
                field = value = ''
                if type == SEARCH_FIELD:
                    parts = token.split(':', 1);
                    if len(parts) == 2:
                        field = parts[0]
                        value = parts[1]
                elif type == SEARCH_FIELD_EXISTS:
                    field = token
                    value = '*'
                if (type == SEARCH_FIELD and filter != 'none'):
                    if field and value:
                        filters.append(
                            {'scope': 'field', 'type': filter,
                             'field': field, 'value': value, 'is_neg': isNeg})
                else:
                    if field and value:
                        if sfquery:
                            if isNeg:
                                sfquery += " except "
                            else:
                                sfquery += " intersect "
                        elif isNeg:
                            sfquery += "select id from facts except "
                        field = field.replace("*", "%")
                        value = value.replace("*", "%")
                        args["_ff_%d" % c] = "%"+value+"%"
                        ids = self.s.column0("""
select id from fieldmodels where name like :field escape '\\'""", field=field)
                        sfquery += """
select factId from fields where fieldModelId in %s and
value like :_ff_%d escape '\\'""" % (ids2str(ids), c)
            elif type == SEARCH_QA:
                field = value = ''
                parts = token.split(':', 1);
                if len(parts) == 2:
                    field = parts[0]
                    value = parts[1]
                if (filter != 'none'):
                    if field and value:
                        filters.append(
                            {'scope': 'card', 'type': filter, 'field': field,
                             'value': value, 'is_neg': isNeg})
                else:
                    if field and value:
                        if qaquery:
                            if isNeg:
                                qaquery += " except "
                            else:
                                qaquery += " intersect "
                        elif isNeg:
                            qaquery += "select id from cards except "
                        value = value.replace("*", "%")
                        args["_ff_%d" % c] = "%"+value+"%"

                        if field == 'question':
                            qaquery += """
select id from cards where question like :_ff_%d escape '\\'""" % c
                        else:
                            qaquery += """
select id from cards where answer like :_ff_%d escape '\\'""" % c
            elif type == SEARCH_DISTINCT:
                if isNeg is False:
                    showdistinct = True if token == "one" else False
                else:
                    showdistinct = False if token == "one" else True
            else:
                if (filter != 'none'):
                    filters.append(
                        {'scope': 'fact', 'type': filter,
                         'value': token, 'is_neg': isNeg})
                else:
                    if fquery:
                        if isNeg:
                            fquery += " except "
                        else:
                            fquery += " intersect "
                    elif isNeg:
                        fquery += "select id from facts except "
                    token = token.replace("*", "%")
                    args["_ff_%d" % c] = "%"+token+"%"
                    fquery += """
select id from facts where spaceUntil like :_ff_%d escape '\\'""" % c
        return (tquery, fquery, qquery, fidquery, cmquery, sfquery,
                qaquery, showdistinct, filters, args)

    # Find and replace
    ##########################################################################

    def findReplace(self, factIds, src, dst, isRe=False, field=None):
        "Find and replace fields in a fact."
        # find
        s = "select id, factId, value from fields where factId in %s"
        if isRe:
            isRe = re.compile(src)
        else:
            s += " and value like :v"
        if field:
            s += " and fieldModelId = :fmid"
        rows = self.s.all(s % ids2str(factIds),
                          v="%"+src.replace("%", "%%")+"%",
                          fmid=field)
        modded = []
        if isRe:
            modded = [
                {'id': id, 'fid': fid, 'val': re.sub(isRe, dst, val)}
                for (id, fid, val) in rows
                if isRe.search(val)]
        else:
            modded = [
                {'id': id, 'fid': fid, 'val': val.replace(src, dst)}
                for (id, fid, val) in rows
                if val.find(src) != -1]
        # update
        self.s.statements(
        'update fields set value = :val where id = :id', modded)
        self.updateCardQACacheFromIds([f['fid'] for f in modded],
                                          type="facts")
        return len(set([f['fid'] for f in modded]))

    # Find duplicates
    ##########################################################################

    def findDuplicates(self, fmids):
        data = self.s.all(
            "select factId, value from fields where fieldModelId in %s" %
            ids2str(fmids))
        vals = {}
        for (fid, val) in data:
            if not val.strip():
                continue
            if val not in vals:
                vals[val] = [fid]
            else:
                vals[val].append(fid)
        return [(k,v) for (k,v) in vals.items() if len(v) > 1]

    # Progress info
    ##########################################################################

    def startProgress(self, max=0, min=0, title=None):
        self.enableProgressHandler()
        runHook("startProgress", max, min, title)
        self.s.flush()

    def updateProgress(self, label=None, value=None):
        runHook("updateProgress", label, value)

    def finishProgress(self):
        runHook("updateProgress")
        runHook("finishProgress")
        self.disableProgressHandler()

    def progressHandler(self):
        if (time.time() - self.progressHandlerCalled) < 0.2:
            return
        self.progressHandlerCalled = time.time()
        if self.progressHandlerEnabled:
            runHook("dbProgress")

    def enableProgressHandler(self):
        self.progressHandlerEnabled = True

    def disableProgressHandler(self):
        self.progressHandlerEnabled = False

    # Notifications
    ##########################################################################

    def notify(self, msg):
        "Send a notice to all listeners, or display on stdout."
        if hookEmpty("notify"):
            pass
        else:
            runHook("notify", msg)

    # File-related
    ##########################################################################

    def name(self):
        if not self.path:
            return u"untitled"
        n = os.path.splitext(os.path.basename(self.path))[0]
        assert '/' not in n
        assert '\\' not in n
        return n

    # Session handling
    ##########################################################################

    def startSession(self):
        self.lastSessionStart = self.sessionStartTime
        self.sessionStartTime = time.time()
        self.sessionStartReps = self.getStats()['dTotal']

    def stopSession(self):
        self.sessionStartTime = 0

    def sessionLimitReached(self):
        if not self.sessionStartTime:
            # not started
            return False
        if (self.sessionTimeLimit and time.time() >
            (self.sessionStartTime + self.sessionTimeLimit)):
            return True
        if (self.sessionRepLimit and self.sessionRepLimit <=
            self.getStats()['dTotal'] - self.sessionStartReps):
            return True
        return False

    # Meta vars
    ##########################################################################

    def getInt(self, key, type=int):
        ret = self.s.scalar("select value from deckVars where key = :k",
                            k=key)
        if ret is not None:
            ret = type(ret)
        return ret

    def getFloat(self, key):
        return self.getInt(key, float)

    def getBool(self, key):
        ret = self.s.scalar("select value from deckVars where key = :k",
                            k=key)
        if ret is not None:
            # hack to work around ankidroid bug
            if ret.lower() == "true":
                return True
            elif ret.lower() == "false":
                return False
            else:
                ret = not not int(ret)
        return ret

    def getVar(self, key):
        "Return value for key as string, or None."
        return self.s.scalar("select value from deckVars where key = :k",
                             k=key)

    def setVar(self, key, value, mod=True):
        if self.s.scalar("""
select value = :value from deckVars
where key = :key""", key=key, value=value):
            return
        # can't use insert or replace as it confuses the undo code
        if self.s.scalar("select 1 from deckVars where key = :key", key=key):
            self.s.statement("update deckVars set value=:value where key = :key",
                             key=key, value=value)
        else:
            self.s.statement("insert into deckVars (key, value) "
                             "values (:key, :value)", key=key, value=value)
        if mod:
            self.setModified()

    def setVarDefault(self, key, value):
        if not self.s.scalar(
            "select 1 from deckVars where key = :key", key=key):
            self.s.statement("insert into deckVars (key, value) "
                             "values (:key, :value)", key=key, value=value)

    # Failed card handling
    ##########################################################################

    def setFailedCardPolicy(self, idx):
        if idx == 5:
            # custom
            return
        self.collapseTime = 0
        self.failedCardMax = 0
        if idx == 0:
            d = 600
            self.collapseTime = 1
            self.failedCardMax = 20
        elif idx == 1:
            d = 0
        elif idx == 2:
            d = 600
        elif idx == 3:
            d = 28800
        elif idx == 4:
            d = 259200
        self.delay0 = d
        self.delay1 = 0

    def getFailedCardPolicy(self):
        if self.delay1:
            return 5
        d = self.delay0
        if self.collapseTime == 1:
            if d == 600 and self.failedCardMax == 20:
                return 0
            return 5
        if d == 0 and self.failedCardMax == 0:
            return 1
        elif d == 600:
            return 2
        elif d == 28800:
            return 3
        elif d == 259200:
            return 4
        return 5

    # Media
    ##########################################################################

    def mediaDir(self, create=False):
        "Return the media directory if exists. None if couldn't create."
        if self.path:
            if self.mediaPrefix:
                dir = os.path.join(
                    self.mediaPrefix, os.path.basename(self.path))
            else:
                dir = self.path
            dir = re.sub("(?i)\.(anki)$", ".media", dir)
            if create == None:
                # don't create, but return dir
                return dir
            if not os.path.exists(dir) and create:
                try:
                    os.makedirs(dir)
                except OSError:
                    # permission denied
                    return None
        else:
            # memory-backed; need temp store
            if not self.tmpMediaDir and create:
                self.tmpMediaDir = tempfile.mkdtemp(prefix="anki")
            dir = self.tmpMediaDir
        if not dir or not os.path.exists(dir):
            return None
        # change to the current dir
        os.chdir(dir)
        return dir

    def addMedia(self, path):
        """Add PATH to the media directory.
Return new path, relative to media dir."""
        return anki.media.copyToMedia(self, path)

    def renameMediaDir(self, oldPath):
        "Copy oldPath to our current media dir. "
        assert os.path.exists(oldPath)
        newPath = self.mediaDir(create=None)
        # copytree doesn't want the dir to exist
        try:
            shutil.copytree(oldPath, newPath)
        except:
            # FIXME: should really remove everything in old dir instead of
            # giving up
            pass

    # DB helpers
    ##########################################################################

    def save(self):
        "Commit any pending changes to disk."
        if self.lastLoaded == self.modified:
            return
        self.lastLoaded = self.modified
        self.s.commit()

    def close(self):
        if self.s:
            self.s.rollback()
            self.s.clear()
            self.s.close()
        self.engine.dispose()
        runHook("deckClosed")

    def rollback(self):
        "Roll back the current transaction and reset session state."
        self.s.rollback()
        self.s.clear()
        self.s.update(self)
        self.s.refresh(self)

    def refreshSession(self):
        "Flush and expire all items from the session."
        self.s.flush()
        self.s.expire_all()

    def openSession(self):
        "Open a new session. Assumes old session is already closed."
        self.s = SessionHelper(self.Session(), lock=self.needLock)
        self.s.update(self)
        self.refreshSession()

    def closeSession(self):
        "Close the current session, saving any changes. Do nothing if no session."
        if self.s:
            self.save()
            try:
                self.s.expunge(self)
            except:
                import sys
                sys.stderr.write("ERROR expunging deck..\n")
            self.s.close()
            self.s = None

    def setModified(self, newTime=None):
        #import traceback; traceback.print_stack()
        self.modified = newTime or time.time()

    def flushMod(self):
        "Mark modified and flush to DB."
        self.setModified()
        self.s.flush()

    def saveAs(self, newPath):
        "Returns new deck. Old connection is closed without saving."
        oldMediaDir = self.mediaDir()
        self.s.flush()
        # remove new deck if it exists
        try:
            os.unlink(newPath)
        except OSError:
            pass
        self.startProgress()
        # copy tables, avoiding implicit commit on current db
        DeckStorage.Deck(newPath, backup=False).close()
        new = sqlite.connect(newPath)
        for table in self.s.column0(
            "select name from sqlite_master where type = 'table'"):
            if table.startswith("sqlite_"):
                continue
            new.execute("delete from %s" % table)
            cols = [str(x[1]) for x in new.execute(
                "pragma table_info('%s')" % table).fetchall()]
            q = "select 'insert into %(table)s values("
            q += ",".join(["'||quote(\"" + col + "\")||'" for col in cols])
            q += ")' from %(table)s"
            q = q % {'table': table}
            c = 0
            for row in self.s.execute(q):
                new.execute(row[0])
                if c % 1000:
                    self.updateProgress()
                c += 1
        # save new, close both
        new.commit()
        new.close()
        self.close()
        # open again in orm
        newDeck = DeckStorage.Deck(newPath, backup=False)
        # move media
        if oldMediaDir:
            newDeck.renameMediaDir(oldMediaDir)
        # forget sync name
        newDeck.syncName = None
        newDeck.s.commit()
        # and return the new deck
        self.finishProgress()
        return newDeck

    # Syncing
    ##########################################################################
    # toggling does not bump deck mod time, since it may happen on upgrade,
    # and the variable is not synced

    def enableSyncing(self):
        self.syncName = unicode(checksum(self.path.encode("utf-8")))
        self.s.commit()

    def disableSyncing(self):
        self.syncName = None
        self.s.commit()

    def syncingEnabled(self):
        return self.syncName

    def checkSyncHash(self):
        if self.syncName and self.syncName != checksum(self.path.encode("utf-8")):
            self.notify(_("""\
Because '%s' has been moved or copied, automatic synchronisation \
has been disabled (ERR-0100).

You can disable this check in Settings>Preferences>Network.""") % self.name())
            self.disableSyncing()
            self.syncName = None

    # DB maintenance
    ##########################################################################

    def recoverCards(self, ids):
        "Put cards with damaged facts into new facts."
        # create a new model in case the user has modified a previous one
        from anki.stdmodels import RecoveryModel
        m = RecoveryModel()
        last = self.currentModel
        self.addModel(m)
        def repl(s):
            # strip field model text
            return re.sub("<span class=\"fm.*?>(.*?)</span>", "\\1", s)
        # add new facts, pointing old card at new fact
        for (id, q, a) in self.s.all("""
select id, question, answer from cards
where id in %s""" % ids2str(ids)):
            f = self.newFact()
            f['Question'] = repl(q)
            f['Answer'] = repl(a)
            try:
                f.tags = self.s.scalar("""
select group_concat(tag, " ") from tags t, cardTags ct
where cardId = :cid and ct.tagId = t.id""", cid=id) or u""
            except:
                raise Exception("Your sqlite is too old.")
            cards = self.addFact(f)
            # delete the freshly created card and point old card to this fact
            self.s.statement("delete from cards where id = :id",
                             id=f.cards[0].id)
            self.s.statement("""
update cards set factId = :fid, cardModelId = :cmid, ordinal = 0
where id = :id""", fid=f.id, cmid=m.cardModels[0].id, id=id)
        # restore old model
        self.currentModel = last

    def fixIntegrity(self, quick=False):
        "Fix some problems and rebuild caches. Caller must .reset()"
        self.s.commit()
        self.resetUndo()
        problems = []
        recover = False
        if quick:
            num = 4
        else:
            num = 9
        self.startProgress(num)
        self.updateProgress(_("Checking integrity..."))
        if self.s.scalar("pragma integrity_check") != "ok":
            self.finishProgress()
            return _("Database file is damaged.\n"
                     "Please restore from automatic backup (see FAQ).")
        # ensure correct views and indexes are available
        self.updateProgress()
        DeckStorage._addViews(self)
        DeckStorage._addIndices(self)
        # does the user have a model?
        self.updateProgress(_("Checking schema..."))
        if not self.s.scalar("select count(id) from models"):
            self.addModel(BasicModel())
            problems.append(_("Deck was missing a model"))
        # is currentModel pointing to a valid model?
        if not self.s.all("""
select decks.id from decks, models where
decks.currentModelId = models.id"""):
            self.currentModelId = self.models[0].id
            problems.append(_("The current model didn't exist"))
        # fields missing a field model
        ids = self.s.column0("""
select id from fields where fieldModelId not in (
select distinct id from fieldModels)""")
        if ids:
            self.s.statement("delete from fields where id in %s" %
                             ids2str(ids))
            problems.append(ngettext("Deleted %d field with missing field model",
                            "Deleted %d fields with missing field model", len(ids)) %
                            len(ids))
        # facts missing a field?
        ids = self.s.column0("""
select distinct facts.id from facts, fieldModels where
facts.modelId = fieldModels.modelId and fieldModels.id not in
(select fieldModelId from fields where factId = facts.id)""")
        if ids:
            self.deleteFacts(ids)
            problems.append(ngettext("Deleted %d fact with missing fields",
                            "Deleted %d facts with missing fields", len(ids)) %
                            len(ids))
        # cards missing a fact?
        ids = self.s.column0("""
select id from cards where factId not in (select id from facts)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with missing fact",
                            "Recovered %d cards with missing fact", len(ids)) %
                            len(ids))
        # cards missing a card model?
        ids = self.s.column0("""
select id from cards where cardModelId not in
(select id from cardModels)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with no card template",
                            "Recovered %d cards with no card template", len(ids)) %
                            len(ids))
        # cards with a card model from the wrong model
        ids = self.s.column0("""
select id from cards where cardModelId not in (select cm.id from
cardModels cm, facts f where cm.modelId = f.modelId and
f.id = cards.factId)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with wrong card template",
                            "Recovered %d cards with wrong card template", len(ids)) %
                            len(ids))
        # facts missing a card?
        ids = self.deleteDanglingFacts()
        if ids:
            problems.append(ngettext("Deleted %d fact with no cards",
                            "Deleted %d facts with no cards", len(ids)) %
                            len(ids))
        # dangling fields?
        ids = self.s.column0("""
select id from fields where factId not in (select id from facts)""")
        if ids:
            self.s.statement(
                "delete from fields where id in %s" % ids2str(ids))
            problems.append(ngettext("Deleted %d dangling field",
                            "Deleted %d dangling fields", len(ids)) %
                            len(ids))
        self.s.flush()
        if not quick:
            self.updateProgress()
            # these sometimes end up null on upgrade
            self.s.statement("update models set source = 0 where source is null")
            self.s.statement(
                "update cardModels set allowEmptyAnswer = 1, typeAnswer = '' "
                "where allowEmptyAnswer is null or typeAnswer is null")
            # fix tags
            self.updateProgress(_("Rebuilding tag cache..."))
            self.updateCardTags()
            # fix any priorities
            self.updateProgress(_("Updating priorities..."))
            self.updateAllPriorities(dirty=False)
            # make sure
            self.updateProgress(_("Updating ordinals..."))
            self.s.statement("""
update fields set ordinal = (select ordinal from fieldModels
where id = fieldModelId)""")
            # fix problems with stripping html
            self.updateProgress(_("Rebuilding QA cache..."))
            fields = self.s.all("select id, value from fields")
            newFields = []
            for (id, value) in fields:
                newFields.append({'id': id, 'value': tidyHTML(value)})
            self.s.statements(
                "update fields set value=:value where id=:id",
                newFields)
            # regenerate question/answer cache
            for m in self.models:
                self.updateCardsFromModel(m, dirty=False)
            # force a full sync
            self.s.flush()
            self.s.statement("update cards set modified = :t", t=time.time())
            self.s.statement("update facts set modified = :t", t=time.time())
            self.s.statement("update models set modified = :t", t=time.time())
            self.lastSync = 0
            # rebuild
            self.updateProgress(_("Rebuilding types..."))
            self.rebuildTypes()
        # update deck and save
        if not quick:
            self.flushMod()
            self.save()
        self.refreshSession()
        self.finishProgress()
        if problems:
            if recover:
                problems.append("\n" + _("""\
Cards with corrupt or missing facts have been placed into new facts. \
Your scheduling info and card content has been preserved, but the \
original layout of the facts has been lost."""))
            return "\n".join(problems)
        return "ok"

    def optimize(self):
        oldSize = os.stat(self.path)[stat.ST_SIZE]
        self.s.commit()
        self.s.statement("vacuum")
        self.s.statement("analyze")
        newSize = os.stat(self.path)[stat.ST_SIZE]
        return oldSize - newSize

    # Undo/redo
    ##########################################################################

    def initUndo(self):
        # note this code ignores 'unique', as it's an sqlite reserved word
        self.undoStack = []
        self.redoStack = []
        self.undoEnabled = True
        self.s.statement(
            "create temporary table undoLog (seq integer primary key not null, sql text)")
        tables = self.s.column0(
            "select name from sqlite_master where type = 'table'")
        for table in tables:
            if table in ("undoLog", "sqlite_stat1"):
                continue
            columns = [r[1] for r in
                       self.s.all("pragma table_info(%s)" % table)]
            # insert
            self.s.statement("""
create temp trigger _undo_%(t)s_it
after insert on %(t)s begin
insert into undoLog values
(null, 'delete from %(t)s where rowid = ' || new.rowid); end""" % {'t': table})
            # update
            sql = """
create temp trigger _undo_%(t)s_ut
after update on %(t)s begin
insert into undoLog values (null, 'update %(t)s """ % {'t': table}
            sep = "set "
            for c in columns:
                if c == "unique":
                    continue
                sql += "%(s)s%(c)s=' || quote(old.%(c)s) || '" % {
                    's': sep, 'c': c}
                sep = ","
            sql += " where rowid = ' || old.rowid); end"
            self.s.statement(sql)
            # delete
            sql = """
create temp trigger _undo_%(t)s_dt
before delete on %(t)s begin
insert into undoLog values (null, 'insert into %(t)s (rowid""" % {'t': table}
            for c in columns:
                sql += ",\"%s\"" % c
            sql += ") values (' || old.rowid ||'"
            for c in columns:
                if c == "unique":
                    sql += ",1"
                    continue
                sql += ",' || quote(old.%s) ||'" % c
            sql += ")'); end"
            self.s.statement(sql)

    def undoName(self):
        for n in reversed(self.undoStack):
            if n:
                return n[0]

    def redoName(self):
        return self.redoStack[-1][0]

    def undoAvailable(self):
        if not self.undoEnabled:
            return
        for r in reversed(self.undoStack):
            if r:
                return True

    def redoAvailable(self):
        return self.undoEnabled and self.redoStack

    def resetUndo(self):
        try:
            self.s.statement("delete from undoLog")
        except:
            pass
        self.undoStack = []
        self.redoStack = []

    def setUndoBarrier(self):
        if not self.undoStack or self.undoStack[-1] is not None:
            self.undoStack.append(None)

    def setUndoStart(self, name, merge=False):
        if not self.undoEnabled:
            return
        self.s.flush()
        if merge and self.undoStack:
            if self.undoStack[-1] and self.undoStack[-1][0] == name:
                # merge with last entry?
                return
        start = self._latestUndoRow()
        self.undoStack.append([name, start, None])

    def setUndoEnd(self, name):
        if not self.undoEnabled:
            return
        self.s.flush()
        end = self._latestUndoRow()
        while self.undoStack[-1] is None:
            # strip off barrier
            self.undoStack.pop()
        self.undoStack[-1][2] = end
        if self.undoStack[-1][1] == self.undoStack[-1][2]:
            self.undoStack.pop()
        else:
            self.redoStack = []
        runHook("undoEnd")

    def _latestUndoRow(self):
        return self.s.scalar("select max(rowid) from undoLog") or 0

    def _undoredo(self, src, dst):
        self.s.flush()
        while 1:
            u = src.pop()
            if u:
                break
        (start, end) = (u[1], u[2])
        if end is None:
            end = self._latestUndoRow()
        sql = self.s.column0("""
select sql from undoLog where
seq > :s and seq <= :e order by seq desc""", s=start, e=end)
        mod = len(sql) / 35
        if mod:
            self.startProgress(36)
            self.updateProgress(_("Processing..."))
        newstart = self._latestUndoRow()
        for c, s in enumerate(sql):
            if mod and not c % mod:
                self.updateProgress()
            self.engine.execute(s)
        newend = self._latestUndoRow()
        dst.append([u[0], newstart, newend])
        if mod:
            self.finishProgress()

    def undo(self):
        "Undo the last action(s). Caller must .reset()"
        self._undoredo(self.undoStack, self.redoStack)
        self.refreshSession()
        runHook("postUndoRedo")

    def redo(self):
        "Redo the last action(s). Caller must .reset()"
        self._undoredo(self.redoStack, self.undoStack)
        self.refreshSession()
        runHook("postUndoRedo")

    # Dynamic indices
    ##########################################################################

    def updateDynamicIndices(self):
        indices = {
            'intervalDesc':
            '(type, priority desc, interval desc, factId, combinedDue)',
            'intervalAsc':
            '(type, priority desc, interval, factId, combinedDue)',
            'randomOrder':
            '(type, priority desc, factId, ordinal, combinedDue)',
            'dueAsc':
            '(type, priority desc, due, factId, combinedDue)',
            'dueDesc':
            '(type, priority desc, due desc, factId, combinedDue)',
            }
        # determine required
        required = []
        if self.revCardOrder == REV_CARDS_OLD_FIRST:
            required.append("intervalDesc")
        if self.revCardOrder == REV_CARDS_NEW_FIRST:
            required.append("intervalAsc")
        if self.revCardOrder == REV_CARDS_RANDOM:
            required.append("randomOrder")
        if (self.revCardOrder == REV_CARDS_DUE_FIRST or
            self.newCardOrder == NEW_CARDS_OLD_FIRST or
            self.newCardOrder == NEW_CARDS_RANDOM):
            required.append("dueAsc")
        if (self.newCardOrder == NEW_CARDS_NEW_FIRST):
            required.append("dueDesc")
        # add/delete
        analyze = False
        for (k, v) in indices.items():
            n = "ix_cards_%s2" % k
            if k in required:
                if not self.s.scalar(
                    "select 1 from sqlite_master where name = :n", n=n):
                    self.s.statement(
                        "create index %s on cards %s" %
                        (n, v))
                    analyze = True
            else:
                # leave old indices for older clients
                #self.s.statement("drop index if exists ix_cards_%s" % k)
                self.s.statement("drop index if exists %s" % n)
        if analyze:
            self.s.statement("analyze")

# Shared decks
##########################################################################

sourcesTable = Table(
    'sources', metadata,
    Column('id', Integer, nullable=False, primary_key=True),
    Column('name', UnicodeText, nullable=False, default=""),
    Column('created', Float, nullable=False, default=time.time),
    Column('lastSync', Float, nullable=False, default=0),
    # -1 = never check, 0 = always check, 1+ = number of seconds passed.
    # not currently exposed in the GUI
    Column('syncPeriod', Integer, nullable=False, default=0))

# Maps
##########################################################################

mapper(Deck, decksTable, properties={
    'currentModel': relation(anki.models.Model, primaryjoin=
                             decksTable.c.currentModelId ==
                             anki.models.modelsTable.c.id),
    'models': relation(anki.models.Model, post_update=True,
                       primaryjoin=
                       decksTable.c.id ==
                       anki.models.modelsTable.c.deckId),
    })

# Deck storage
##########################################################################

numBackups = 30
backupDir = os.path.expanduser("~/.anki/backups")

class DeckStorage(object):

    def Deck(path=None, backup=True, lock=True, pool=True, rebuild=True):
        "Create a new deck or attach to an existing one."
        create = True
        if path is None:
            sqlpath = None
        else:
            path = os.path.abspath(path)
            # check if we need to init
            if os.path.exists(path):
                create = False
            # sqlite needs utf8
            sqlpath = path.encode("utf-8")
        try:
            (engine, session) = DeckStorage._attach(sqlpath, create, pool)
            s = session()
            if create:
                ver = 999
                metadata.create_all(engine)
                deck = DeckStorage._init(s)
            else:
                ver = s.scalar("select version from decks limit 1")
                if ver < 19:
                    for st in (
                        "decks add column newCardsPerDay integer not null default 20",
                        "decks add column sessionRepLimit integer not null default 100",
                        "decks add column sessionTimeLimit integer not null default 1800",
                        "decks add column utcOffset numeric(10, 2) not null default 0",
                        "decks add column cardCount integer not null default 0",
                        "decks add column factCount integer not null default 0",
                        "decks add column failedNowCount integer not null default 0",
                        "decks add column failedSoonCount integer not null default 0",
                        "decks add column revCount integer not null default 0",
                        "decks add column newCount integer not null default 0",
                        "decks add column revCardOrder integer not null default 0",
                        "cardModels add column allowEmptyAnswer boolean not null default 1",
                        "cardModels add column typeAnswer text not null default ''"):
                        try:
                            s.execute("alter table " + st)
                        except:
                            pass
                if ver < DECK_VERSION:
                    metadata.create_all(engine)
                deck = s.query(Deck).get(1)
                if not deck:
                    raise DeckAccessError(_("Deck missing core table"),
                                          type="nocore")
            # attach db vars
            deck.path = path
            deck.engine = engine
            deck.Session = session
            deck.needLock = lock
            deck.progressHandlerCalled = 0
            deck.progressHandlerEnabled = False
            if pool:
                try:
                    deck.engine.raw_connection().set_progress_handler(
                        deck.progressHandler, 100)
                except:
                    print "please install pysqlite 2.4 for better progress dialogs"
            deck.engine.execute("pragma locking_mode = exclusive")
            deck.s = SessionHelper(s, lock=lock)
            # force a write lock
            deck.s.execute("update decks set modified = modified")
            needUnpack = False
            if deck.utcOffset in (-1, -2):
                # do the rest later
                needUnpack = deck.utcOffset == -1
                # make sure we do this before initVars
                DeckStorage._setUTCOffset(deck)
                deck.created = time.time()
            if ver < 27:
                initTagTables(deck.s)
            if create:
                # new-style file format
                deck.s.commit()
                deck.s.execute("pragma legacy_file_format = off")
                deck.s.execute("pragma default_cache_size= 20000")
                deck.s.execute("vacuum")
                # add views/indices
                initTagTables(deck.s)
                DeckStorage._addViews(deck)
                DeckStorage._addIndices(deck)
                deck.s.statement("analyze")
                deck._initVars()
                deck.updateTagPriorities()
            else:
                if backup:
                    DeckStorage.backup(deck, path)
                deck._initVars()
                try:
                    deck = DeckStorage._upgradeDeck(deck, path)
                except:
                    traceback.print_exc()
                    deck.fixIntegrity()
                    deck = DeckStorage._upgradeDeck(deck, path)
        except OperationalError, e:
            engine.dispose()
            if (str(e.orig).startswith("database table is locked") or
                str(e.orig).startswith("database is locked")):
                raise DeckAccessError(_("File is in use by another process"),
                                      type="inuse")
            else:
                raise e
        if not rebuild:
            # minimal startup
            deck._globalStats = globalStats(deck)
            deck._dailyStats = dailyStats(deck)
            return deck
        if needUnpack:
            deck.startProgress()
            DeckStorage._addIndices(deck)
            for m in deck.models:
                deck.updateCardsFromModel(m)
            deck.finishProgress()
        oldMod = deck.modified
        # fix a bug with current model being unset
        if not deck.currentModel and deck.models:
            deck.currentModel = deck.models[0]
        # ensure the necessary indices are available
        deck.updateDynamicIndices()
        # FIXME: temporary code for upgrade
        # - ensure cards suspended on older clients are recognized
        deck.s.statement("""
update cards set type = type - 3 where type between 0 and 2 and priority = -3""")
        # - new delay1 handling
        if deck.delay1 > 7:
            deck.delay1 = 0
        # unsuspend buried/rev early - can remove priorities in the future
        ids = deck.s.column0(
            "select id from cards where type > 2 or priority between -2 and -1")
        if ids:
            deck.updatePriorities(ids)
            deck.s.statement(
                "update cards set type = relativeDelay where type > 2")
            deck.s.commit()
        # check if deck has been moved, and disable syncing
        deck.checkSyncHash()
        # determine starting factor for new cards
        deck.averageFactor = (deck.s.scalar(
            "select avg(factor) from cards where type = 1")
                               or Deck.initialFactor)
        deck.averageFactor = max(deck.averageFactor, Deck.minimumAverage)
        # rebuild queue
        deck.reset()
        # make sure we haven't accidentally bumped the modification time
        assert deck.modified == oldMod
        return deck
    Deck = staticmethod(Deck)

    def _attach(path, create, pool=True):
        "Attach to a file, initializing DB"
        if path is None:
            path = "sqlite://"
        else:
            path = "sqlite:///" + path
        if pool:
            # open and lock connection for single use
            engine = create_engine(path, connect_args={'timeout': 0},
                                   strategy="threadlocal")
        else:
            # no pool & concurrent access w/ timeout
            engine = create_engine(path,
                                   poolclass=NullPool,
                                   connect_args={'timeout': 60})
        session = sessionmaker(bind=engine,
                               autoflush=False,
                               autocommit=True)
        return (engine, session)
    _attach = staticmethod(_attach)

    def _init(s):
        "Add a new deck to the database. Return saved deck."
        deck = Deck()
        if sqlalchemy.__version__.startswith("0.4."):
            s.save(deck)
        else:
            s.add(deck)
        s.flush()
        return deck
    _init = staticmethod(_init)

    def _addIndices(deck):
        "Add indices to the DB."
        # counts, failed cards
        deck.s.statement("""
create index if not exists ix_cards_typeCombined on cards
(type, combinedDue, factId)""")
        # scheduler-agnostic type
        deck.s.statement("""
create index if not exists ix_cards_relativeDelay on cards
(relativeDelay)""")
        # index on modified, to speed up sync summaries
        deck.s.statement("""
create index if not exists ix_cards_modified on cards
(modified)""")
        deck.s.statement("""
create index if not exists ix_facts_modified on facts
(modified)""")
        # priority - temporary index to make compat code faster. this can be
        # removed when all clients are on 1.2, as can the ones below
        deck.s.statement("""
create index if not exists ix_cards_priority on cards
(priority)""")
        # average factor
        deck.s.statement("""
create index if not exists ix_cards_factor on cards
(type, factor)""")
        # card spacing
        deck.s.statement("""
create index if not exists ix_cards_factId on cards (factId)""")
        # stats
        deck.s.statement("""
create index if not exists ix_stats_typeDay on stats (type, day)""")
        # fields
        deck.s.statement("""
create index if not exists ix_fields_factId on fields (factId)""")
        deck.s.statement("""
create index if not exists ix_fields_fieldModelId on fields (fieldModelId)""")
        deck.s.statement("""
create index if not exists ix_fields_value on fields (value)""")
        # media
        deck.s.statement("""
create unique index if not exists ix_media_filename on media (filename)""")
        deck.s.statement("""
create index if not exists ix_media_originalPath on media (originalPath)""")
        # deletion tracking
        deck.s.statement("""
create index if not exists ix_cardsDeleted_cardId on cardsDeleted (cardId)""")
        deck.s.statement("""
create index if not exists ix_modelsDeleted_modelId on modelsDeleted (modelId)""")
        deck.s.statement("""
create index if not exists ix_factsDeleted_factId on factsDeleted (factId)""")
        deck.s.statement("""
create index if not exists ix_mediaDeleted_factId on mediaDeleted (mediaId)""")
        # tags
        txt = "create unique index if not exists ix_tags_tag on tags (tag)"
        try:
            deck.s.statement(txt)
        except:
            deck.s.statement("""
delete from tags where exists (select 1 from tags t2 where tags.tag = t2.tag
and tags.rowid > t2.rowid)""")
            deck.s.statement(txt)
        deck.s.statement("""
create index if not exists ix_cardTags_tagCard on cardTags (tagId, cardId)""")
        deck.s.statement("""
create index if not exists ix_cardTags_cardId on cardTags (cardId)""")
    _addIndices = staticmethod(_addIndices)

    def _addViews(deck):
        "Add latest version of SQL views to DB."
        s = deck.s
        # old views
        s.statement("drop view if exists failedCards")
        s.statement("drop view if exists revCardsOld")
        s.statement("drop view if exists revCardsNew")
        s.statement("drop view if exists revCardsDue")
        s.statement("drop view if exists revCardsRandom")
        s.statement("drop view if exists acqCardsRandom")
        s.statement("drop view if exists acqCardsOld")
        s.statement("drop view if exists acqCardsNew")
        # failed cards
        s.statement("""
create view failedCards as
select * from cards
where type = 0 and isDue = 1
order by type, isDue, combinedDue
""")
        # rev cards
        s.statement("""
create view revCardsOld as
select * from cards
where type = 1 and isDue = 1
order by priority desc, interval desc""")
        s.statement("""
create view revCardsNew as
select * from cards
where type = 1 and isDue = 1
order by priority desc, interval""")
        s.statement("""
create view revCardsDue as
select * from cards
where type = 1 and isDue = 1
order by priority desc, due""")
        s.statement("""
create view revCardsRandom as
select * from cards
where type = 1 and isDue = 1
order by priority desc, factId, ordinal""")
        # new cards
        s.statement("""
create view acqCardsOld as
select * from cards
where type = 2 and isDue = 1
order by priority desc, due""")
        s.statement("""
create view acqCardsNew as
select * from cards
where type = 2 and isDue = 1
order by priority desc, due desc""")
    _addViews = staticmethod(_addViews)

    def _upgradeDeck(deck, path):
        "Upgrade deck to the latest version."
        if deck.version < DECK_VERSION:
            prog = True
            deck.startProgress()
            deck.updateProgress(_("Upgrading Deck..."))
            if deck.utcOffset == -1:
                # we're opening a shared deck with no indices - we'll need
                # them if we want to rebuild the queue
                DeckStorage._addIndices(deck)
            oldmod = deck.modified
        else:
            prog = False
        deck.path = path
        if deck.version == 0:
            # new columns
            try:
                deck.s.statement("""
    alter table cards add column spaceUntil float not null default 0""")
                deck.s.statement("""
    alter table cards add column relativeDelay float not null default 0.0""")
                deck.s.statement("""
    alter table cards add column isDue boolean not null default 0""")
                deck.s.statement("""
    alter table cards add column type integer not null default 0""")
                deck.s.statement("""
    alter table cards add column combinedDue float not null default 0""")
                # update cards.spaceUntil based on old facts
                deck.s.statement("""
    update cards
    set spaceUntil = (select (case
    when cards.id = facts.lastCardId
    then 0
    else facts.spaceUntil
    end) from cards as c, facts
    where c.factId = facts.id
    and cards.id = c.id)""")
                deck.s.statement("""
    update cards
    set combinedDue = max(due, spaceUntil)
    """)
            except:
                print "failed to upgrade"
            # rebuild with new file format
            deck.s.commit()
            deck.s.execute("pragma legacy_file_format = off")
            deck.s.execute("vacuum")
            # add views/indices
            DeckStorage._addViews(deck)
            DeckStorage._addIndices(deck)
            # rebuild type and delay cache
            deck.rebuildTypes()
            deck.reset()
            # bump version
            deck.version = 1
            # optimize indices
            deck.s.statement("analyze")
        if deck.version == 1:
            # fix indexes and views
            deck.s.statement("drop index if exists ix_cards_newRandomOrder")
            deck.s.statement("drop index if exists ix_cards_newOrderedOrder")
            DeckStorage._addViews(deck)
            DeckStorage._addIndices(deck)
            deck.rebuildTypes()
            # optimize indices
            deck.s.statement("analyze")
            deck.version = 2
        if deck.version == 2:
            # compensate for bug in 0.9.7 by rebuilding isDue and priorities
            deck.s.statement("update cards set isDue = 0")
            deck.updateAllPriorities(dirty=False)
            # compensate for bug in early 0.9.x where fieldId was not unique
            deck.s.statement("update fields set id = random()")
            deck.version = 3
        if deck.version == 3:
            # remove conflicting and unused indexes
            deck.s.statement("drop index if exists ix_cards_isDueCombined")
            deck.s.statement("drop index if exists ix_facts_lastCardId")
            deck.s.statement("drop index if exists ix_cards_successive")
            deck.s.statement("drop index if exists ix_cards_priority")
            deck.s.statement("drop index if exists ix_cards_reps")
            deck.s.statement("drop index if exists ix_cards_due")
            deck.s.statement("drop index if exists ix_stats_type")
            deck.s.statement("drop index if exists ix_stats_day")
            deck.s.statement("drop index if exists ix_factsDeleted_cardId")
            deck.s.statement("drop index if exists ix_modelsDeleted_cardId")
            DeckStorage._addIndices(deck)
            deck.s.statement("analyze")
            deck.version = 4
        if deck.version == 4:
            # decks field upgraded earlier
            deck.version = 5
        if deck.version == 5:
            # new spacing
            deck.newCardSpacing = NEW_CARDS_DISTRIBUTE
            deck.version = 6
            # low priority cards now stay in same queue
            deck.rebuildTypes()
        if deck.version == 6:
            # removed 'new cards first' option, so order has changed
            deck.newCardSpacing = NEW_CARDS_DISTRIBUTE
            deck.version = 7
        # <version 7->8 upgrade code removed as obsolete>
        if deck.version < 9:
            # back up the media dir again, just in case
            shutil.copytree(deck.mediaDir(create=True),
                            deck.mediaDir() + "-old-%s" %
                            hash(time.time()))
            # backup media
            media = deck.s.all("""
select filename, size, created, originalPath, description from media""")
            # fix mediaDeleted definition
            deck.s.execute("drop table mediaDeleted")
            deck.s.execute("drop table media")
            metadata.create_all(deck.engine)
            # restore
            h = []
            for row in media:
                h.append({
                    'id': genID(),
                    'filename': row[0],
                    'size': row[1],
                    'created': row[2],
                    'originalPath': row[3],
                    'description': row[4]})
            if h:
                deck.s.statements("""
insert into media values (
:id, :filename, :size, :created, :originalPath, :description)""", h)
            deck.version = 9
        if deck.version < 10:
            deck.s.statement("""
alter table models add column source integer not null default 0""")
            deck.version = 10
        if deck.version < 11:
            DeckStorage._setUTCOffset(deck)
            deck.version = 11
            deck.s.commit()
        if deck.version < 12:
            deck.s.statement("drop index if exists ix_cards_revisionOrder")
            deck.s.statement("drop index if exists ix_cards_newRandomOrder")
            deck.s.statement("drop index if exists ix_cards_newOrderedOrder")
            deck.s.statement("drop index if exists ix_cards_markExpired")
            deck.s.statement("drop index if exists ix_cards_failedIsDue")
            deck.s.statement("drop index if exists ix_cards_failedOrder")
            deck.s.statement("drop index if exists ix_cards_type")
            deck.s.statement("drop index if exists ix_cards_priority")
            DeckStorage._addViews(deck)
            DeckStorage._addIndices(deck)
            deck.s.statement("analyze")
        if deck.version < 13:
            deck.reset()
            deck.rebuildCounts()
            # regenerate question/answer cache
            for m in deck.models:
                deck.updateCardsFromModel(m, dirty=False)
            deck.version = 13
        if deck.version < 14:
            deck.s.statement("""
update cards set interval = 0
where interval < 1""")
            deck.version = 14
        if deck.version < 15:
            deck.delay1 = deck.delay0
            deck.delay2 = 0.0
            deck.version = 15
        if deck.version < 16:
            deck.version = 16
        if deck.version < 17:
            deck.s.statement("drop view if exists acqCards")
            deck.s.statement("drop view if exists futureCards")
            deck.s.statement("drop view if exists revCards")
            deck.s.statement("drop view if exists typedCards")
            deck.s.statement("drop view if exists failedCardsNow")
            deck.s.statement("drop view if exists failedCardsSoon")
            deck.s.statement("drop index if exists ix_cards_revisionOrder")
            deck.s.statement("drop index if exists ix_cards_newRandomOrder")
            deck.s.statement("drop index if exists ix_cards_newOrderedOrder")
            deck.s.statement("drop index if exists ix_cards_combinedDue")
            # add new views
            DeckStorage._addViews(deck)
            DeckStorage._addIndices(deck)
            deck.version = 17
        if deck.version < 18:
            deck.s.statement(
                "create table undoLog (seq integer primary key, sql text)")
            deck.version = 18
            deck.s.commit()
            DeckStorage._addIndices(deck)
            deck.s.statement("analyze")
        if deck.version < 19:
            # permanent undo log causes various problems, revert to temp
            deck.s.statement("drop table undoLog")
            deck.sessionTimeLimit = 600
            deck.sessionRepLimit = 0
            deck.version = 19
            deck.s.commit()
        if deck.version < 20:
            DeckStorage._addViews(deck)
            DeckStorage._addIndices(deck)
            deck.version = 20
            deck.s.commit()
        if deck.version < 21:
            deck.s.statement("vacuum")
            deck.s.statement("analyze")
            deck.version = 21
            deck.s.commit()
        if deck.version < 22:
            deck.s.statement(
                'update cardModels set typeAnswer = ""')
            deck.version = 22
            deck.s.commit()
        if deck.version < 23:
            try:
                deck.s.execute("drop table undoLog")
            except:
                pass
            deck.version = 23
            deck.s.commit()
        if deck.version < 24:
            deck.s.statement(
                "update cardModels set lastFontColour = '#ffffff'")
            deck.version = 24
            deck.s.commit()
        if deck.version < 25:
            deck.s.statement("drop index if exists ix_cards_priorityDue")
            deck.s.statement("drop index if exists ix_cards_priorityDueReal")
            DeckStorage._addViews(deck)
            DeckStorage._addIndices(deck)
            deck.updateDynamicIndices()
            deck.version = 25
            deck.s.commit()
        if deck.version < 26:
            # no spaces in tags anymore, as separated by space
            def munge(tags):
                tags = re.sub(", ?", "--tmp--", tags)
                tags = re.sub(" - ", "-", tags)
                tags = re.sub(" ", "-", tags)
                tags = re.sub("--tmp--", " ", tags)
                tags = canonifyTags(tags)
                return tags
            rows = deck.s.all('select id, tags from facts')
            d = []
            for (id, tags) in rows:
                d.append({
                    'i': id,
                    't': munge(tags),
                    })
            deck.s.statements(
                "update facts set tags = :t where id = :i", d)
            for k in ('highPriority', 'medPriority',
                      'lowPriority', 'suspended'):
                x = getattr(deck, k)
                setattr(deck, k, munge(x))
            for m in deck.models:
                for cm in m.cardModels:
                    cm.name = munge(cm.name)
                m.tags = munge(m.tags)
                deck.updateCardsFromModel(m, dirty=False)
            deck.version = 26
            deck.s.commit()
            deck.s.statement("vacuum")
        if deck.version < 27:
            DeckStorage._addIndices(deck)
            deck.updateCardTags()
            deck.updateAllPriorities(dirty=False)
            deck.version = 27
            deck.s.commit()
        if deck.version < 28:
            deck.s.statement("pragma default_cache_size= 20000")
            deck.version = 28
            deck.s.commit()
        if deck.version < 30:
            # remove duplicates from review history
            deck.s.statement("""
delete from reviewHistory where id not in (
select min(id) from reviewHistory group by cardId, time);""")
            deck.version = 30
            deck.s.commit()
        if deck.version < 31:
            # recreate review history table
            deck.s.statement("drop index if exists ix_reviewHistory_unique")
            schema = """
CREATE TABLE %s (
cardId INTEGER NOT NULL,
time NUMERIC(10, 2) NOT NULL,
lastInterval NUMERIC(10, 2) NOT NULL,
nextInterval NUMERIC(10, 2) NOT NULL,
ease INTEGER NOT NULL,
delay NUMERIC(10, 2) NOT NULL,
lastFactor NUMERIC(10, 2) NOT NULL,
nextFactor NUMERIC(10, 2) NOT NULL,
reps NUMERIC(10, 2) NOT NULL,
thinkingTime NUMERIC(10, 2) NOT NULL,
yesCount NUMERIC(10, 2) NOT NULL,
noCount NUMERIC(10, 2) NOT NULL,
PRIMARY KEY (cardId, time))"""
            deck.s.statement(schema % "revtmp")
            deck.s.statement("""
insert into revtmp
select cardId, time, lastInterval, nextInterval, ease, delay, lastFactor,
nextFactor, reps, thinkingTime, yesCount, noCount from reviewHistory""")
            deck.s.statement("drop table reviewHistory")
            metadata.create_all(deck.engine)
            deck.s.statement(
                "insert into reviewHistory select * from revtmp")
            deck.s.statement("drop table revtmp")
            deck.version = 31
            deck.s.commit()
            deck.s.statement("vacuum")
        if deck.version < 32:
            deck.s.execute("drop index if exists ix_cardTags_tagId")
            deck.s.execute("drop index if exists ix_cardTags_cardId")
            DeckStorage._addIndices(deck)
            deck.s.execute("analyze")
            deck.version = 32
            deck.s.commit()
        if deck.version < 33:
            deck.s.execute("drop index if exists ix_tags_tag")
            DeckStorage._addIndices(deck)
            deck.version = 33
            deck.s.commit()
        if deck.version < 34:
            deck.s.execute("drop view if exists acqCardsRandom")
            deck.s.execute("drop index if exists ix_cards_factId")
            DeckStorage._addIndices(deck)
            deck.updateDynamicIndices()
            deck.version = 34
            deck.s.commit()
        if deck.version < 36:
            deck.s.statement("drop index if exists ix_cards_priorityDue")
            DeckStorage._addIndices(deck)
            deck.s.execute("analyze")
            deck.version = 36
            deck.s.commit()
        if deck.version < 37:
            if deck.getFailedCardPolicy() == 1:
                deck.failedCardMax = 0
            deck.version = 37
            deck.s.commit()
        if deck.version < 39:
            deck.reset()
            # manually suspend all suspended cards
            ids = deck.findCards("tag:suspended")
            if ids:
                # unrolled from suspendCards() to avoid marking dirty
                deck.s.statement(
                    "update cards set isDue=0, priority=-3 "
                    "where id in %s" % ids2str(ids))
                deck.rebuildCounts()
            # suspended tag obsolete - don't do this yet
            deck.suspended = re.sub(u" ?Suspended ?", u"", deck.suspended)
            deck.updateTagPriorities()
            deck.version = 39
            deck.s.commit()
        if deck.version < 40:
            # now stores media url
            deck.s.statement("update models set features = ''")
            deck.version = 40
            deck.s.commit()
        if deck.version < 43:
            deck.s.statement("update fieldModels set features = ''")
            deck.version = 43
            deck.s.commit()
        if deck.version < 44:
            # leaner indices
            deck.s.statement("drop index if exists ix_cards_factId")
            deck.version = 44
            deck.s.commit()
        if deck.version < 48:
            deck.updateFieldCache(deck.s.column0("select id from facts"))
            deck.version = 48
            deck.s.commit()
        if deck.version < 50:
            # more new type handling
            deck.rebuildTypes()
            deck.version = 50
            deck.s.commit()
        if deck.version < 52:
            dname = deck.name()
            sname = deck.syncName
            if sname and dname != sname:
                deck.notify(_("""\
When syncing, Anki now uses the same deck name on the server as the deck \
name on your computer. Because you had '%(dname)s' set to sync to \
'%(sname)s' on the server, syncing has been temporarily disabled.

If you want to keep your changes to the online version, please use \
File>Download>Personal Deck to download the online version.

If you want to keep the version on your computer, please enable \
syncing again via Settings>Deck Properties>Synchronisation.

If you have syncing disabled in the preferences, you can ignore \
this message. (ERR-0101)""") % {
                    'sname':sname, 'dname':dname})
                deck.disableSyncing()
            elif sname:
                deck.enableSyncing()
            deck.version = 52
            deck.s.commit()
        if deck.version < 53:
            if deck.getBool("perDay"):
                if deck.hardIntervalMin == 0.333:
                    deck.hardIntervalMin = max(1.0, deck.hardIntervalMin)
                    deck.hardIntervalMax = max(1.1, deck.hardIntervalMax)
            deck.version = 53
            deck.s.commit()
        if deck.version < 54:
            # broken versions of the DB orm die if this is a bool with a
            # non-int value
            deck.s.statement("update fieldModels set editFontFamily = 1");
            deck.version = 54
            deck.s.commit()
        if deck.version < 57:
            deck.version = 57
            deck.s.commit()
        if deck.version < 61:
            # do our best to upgrade templates to the new style
            txt = '''\
<span style="font-family: %s; font-size: %spx; color: %s; white-space: pre-wrap;">%s</span>'''
            for m in deck.models:
                unstyled = []
                for fm in m.fieldModels:
                    # find which fields had explicit formatting
                    if fm.quizFontFamily or fm.quizFontSize or fm.quizFontColour:
                        pass
                    else:
                        unstyled.append(fm.name)
                    # fill out missing info
                    fm.quizFontFamily = fm.quizFontFamily or u"Arial"
                    fm.quizFontSize = fm.quizFontSize or 20
                    fm.quizFontColour = fm.quizFontColour or "#000000"
                    fm.editFontSize = fm.editFontSize or 20
                unstyled = set(unstyled)
                for cm in m.cardModels:
                    # embed the old font information into card templates
                    cm.qformat = txt % (
                        cm.questionFontFamily,
                        cm.questionFontSize,
                        cm.questionFontColour,
                        cm.qformat)
                    cm.aformat = txt % (
                        cm.answerFontFamily,
                        cm.answerFontSize,
                        cm.answerFontColour,
                        cm.aformat)
                    # escape fields that had no previous styling
                    for un in unstyled:
                        cm.qformat = cm.qformat.replace("%("+un+")s", "{{{%s}}}"%un)
                        cm.aformat = cm.aformat.replace("%("+un+")s", "{{{%s}}}"%un)
            # rebuild q/a for the above & because latex has changed
            for m in deck.models:
                deck.updateCardsFromModel(m, dirty=False)
            # rebuild the media db based on new format
            rebuildMediaDir(deck, dirty=False)
            deck.version = 61
            deck.s.commit()
        if deck.version < 62:
            # updated indices
            for d in ("intervalDesc", "intervalAsc", "randomOrder",
                      "dueAsc", "dueDesc"):
                deck.s.statement("drop index if exists ix_cards_%s2" % d)
            deck.s.statement("drop index if exists ix_cards_typeCombined")
            DeckStorage._addIndices(deck)
            deck.updateDynamicIndices()
            deck.s.execute("vacuum")
            deck.version = 62
            deck.s.commit()
        if deck.version < 64:
            # remove old static indices, as all clients should be libanki1.2+
            for d in ("ix_cards_duePriority",
                      "ix_cards_priorityDue"):
                deck.s.statement("drop index if exists %s" % d)
            # remove old dynamic indices
            for d in ("intervalDesc", "intervalAsc", "randomOrder",
                      "dueAsc", "dueDesc"):
                deck.s.statement("drop index if exists ix_cards_%s" % d)
            deck.s.execute("analyze")
            deck.version = 64
            deck.s.commit()
            # note: we keep the priority index for now
        if deck.version < 65:
            # we weren't correctly setting relativeDelay when answering cards
            # in previous versions, so ensure everything is set correctly
            deck.rebuildTypes()
            deck.version = 65
            deck.s.commit()
        # executing a pragma here is very slow on large decks, so we store
        # our own record
        if not deck.getInt("pageSize") == 4096:
            deck.s.commit()
            deck.s.execute("pragma page_size = 4096")
            deck.s.execute("pragma legacy_file_format = 0")
            deck.s.execute("vacuum")
            deck.setVar("pageSize", 4096, mod=False)
            deck.s.commit()
        if prog:
            assert deck.modified == oldmod
            deck.finishProgress()
        return deck
    _upgradeDeck = staticmethod(_upgradeDeck)

    def _setUTCOffset(deck):
        # 4am
        deck.utcOffset = time.timezone + 60*60*4
    _setUTCOffset = staticmethod(_setUTCOffset)

    def backup(deck, path):
        """Path must not be unicode."""
        if not numBackups:
            return
        def escape(path):
            path = os.path.abspath(path)
            path = path.replace("\\", "!")
            path = path.replace("/", "!")
            path = path.replace(":", "")
            return path
        escp = escape(path)
        # make sure backup dir exists
        try:
            os.makedirs(backupDir)
        except (OSError, IOError):
            pass
        # find existing backups
        gen = re.sub("\.anki$", ".backup-(\d+).anki", re.escape(escp))
        backups = []
        for file in os.listdir(backupDir):
            m = re.match(gen, file)
            if m:
                backups.append((int(m.group(1)), file))
        backups.sort()
        # check if last backup is the same
        if backups:
            latest = os.path.join(backupDir, backups[-1][1])
            if int(deck.modified) == int(
                os.stat(latest)[stat.ST_MTIME]):
                return
        # check integrity
        if not deck.s.scalar("pragma integrity_check") == "ok":
            raise DeckAccessError(_("Deck is corrupt."), type="corrupt")
        # get next num
        if not backups:
            n = 1
        else:
            n = backups[-1][0] + 1
        # do backup
        newpath = os.path.join(backupDir, os.path.basename(
            re.sub("\.anki$", ".backup-%s.anki" % n, escp)))
        shutil.copy2(path, newpath)
        # set mtimes to be identical
        if deck.modified:
            os.utime(newpath, (deck.modified, deck.modified))
        # remove if over
        if len(backups) + 1 > numBackups:
            delete = len(backups) + 1 - numBackups
            delete = backups[:delete]
            for file in delete:
                os.unlink(os.path.join(backupDir, file[1]))
    backup = staticmethod(backup)

def newCardOrderLabels():
    return {
        0: _("Show new cards in random order"),
        1: _("Show new cards in order added"),
        2: _("Show new cards in reverse order added"),
        }

def newCardSchedulingLabels():
    return {
        0: _("Spread new cards out through reviews"),
        1: _("Show new cards after all other cards"),
        2: _("Show new cards before reviews"),
        }

def revCardOrderLabels():
    return {
        0: _("Review cards from largest interval"),
        1: _("Review cards from smallest interval"),
        2: _("Review cards in order due"),
        3: _("Review cards in random order"),
        }

def failedCardOptionLabels():
    return {
        0: _("Show failed cards soon"),
        1: _("Show failed cards at end"),
        2: _("Show failed cards in 10 minutes"),
        3: _("Show failed cards in 8 hours"),
        4: _("Show failed cards in 3 days"),
        5: _("Custom failed cards handling"),
        }
