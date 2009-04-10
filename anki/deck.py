# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
The Deck
====================
"""
__docformat__ = 'restructuredtext'

import tempfile, time, os, random, sys, re, stat, shutil, types, traceback

from anki.db import *
from anki.lang import _, ngettext
from anki.errors import DeckAccessError
from anki.stdmodels import BasicModel
from anki.utils import parseTags, tidyHTML, genID, ids2str, hexifyID, \
     canonifyTags, joinTags
from anki.history import CardHistoryEntry
from anki.models import Model, CardModel, formatQA
from anki.stats import dailyStats, globalStats, genToday
from anki.fonts import toPlatformFont
from anki.tags import initTagTables, tagIds
import anki.features
from operator import itemgetter
from itertools import groupby
from anki.hooks import runHook

# ensure all the metadata in other files is loaded before proceeding
import anki.models, anki.facts, anki.cards, anki.stats
import anki.history, anki.media

PRIORITY_HIGH = 4
PRIORITY_MED = 3
PRIORITY_NORM = 2
PRIORITY_LOW = 1
PRIORITY_NONE = 0
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

DECK_VERSION = 32

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
    # syncing
    Column('syncName', UnicodeText),
    Column('lastSync', Float, nullable=False, default=0),
    # scheduling
    ##############
    # initial intervals
    Column('hardIntervalMin', Float, nullable=False, default=0.333),
    Column('hardIntervalMax', Float, nullable=False, default=0.5),
    Column('midIntervalMin', Float, nullable=False, default=3.0),
    Column('midIntervalMax', Float, nullable=False, default=5.0),
    Column('easyIntervalMin', Float, nullable=False, default=7.0),
    Column('easyIntervalMax', Float, nullable=False, default=9.0),
    # delays on failure
    Column('delay0', Integer, nullable=False, default=600),
    Column('delay1', Integer, nullable=False, default=600),
    Column('delay2', Float, nullable=False, default=0.0),
    # collapsing future cards
    Column('collapseTime', Integer, nullable=False, default=1),
    # priorities & postponing
    Column('highPriority', UnicodeText, nullable=False, default=u"VeryHighPriority"),
    Column('medPriority', UnicodeText, nullable=False, default=u"HighPriority"),
    Column('lowPriority', UnicodeText, nullable=False, default=u"LowPriority"),
    Column('suspended', UnicodeText, nullable=False, default=u"Suspended"),
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
    Column('utcOffset', Float, nullable=False, default=0),
    # count cache
    Column('cardCount', Integer, nullable=False, default=0),
    Column('factCount', Integer, nullable=False, default=0),
    Column('failedNowCount', Integer, nullable=False, default=0),
    Column('failedSoonCount', Integer, nullable=False, default=0),
    Column('revCount', Integer, nullable=False, default=0),
    Column('newCount', Integer, nullable=False, default=0),
    # rev order
    Column('revCardOrder', Integer, nullable=False, default=0))

class Deck(object):
    "Top-level object. Manages facts, cards and scheduling information."

    factorFour = 1.3
    initialFactor = 2.5
    maxScheduleTime = 1825

    def __init__(self, path=None):
        "Create a new deck."
        # a limit of 1 deck in the table
        self.id = 1
        # db session factory and instance
        self.Session = None
        self.s = None

    def _initVars(self):
        self.tmpMediaDir = None
        self.lastTags = u""
        self.lastLoaded = time.time()
        self.undoEnabled = False
        self.sessionStartReps = 0
        self.sessionStartTime = 0
        self.lastSessionStart = 0
        self.reviewedAheadCards = []
        self.extraNewCards = 0
        self.reviewEarly = False
        try:
            self.progressHandlerCalled = 0
            self.engine.raw_connection().set_progress_handler(
                self.progressHandler, 100)
        except:
            print "please install pysqlite 2.4 for better progress dialogs"
        self.progressHandlerEnabled = False

    def modifiedSinceSave(self):
        return self.modified > self.lastLoaded

    # Getting the next card
    ##########################################################################

    def getCard(self, orm=True):
        "Return the next card object, or None."
        self.checkDue()
        id = self.getCardId()
        if id:
            return self.cardFromId(id, orm)

    def getCardId(self):
        "Return the next due card id, or None."
        # failed card due?
        if self.delay0 and self.failedNowCount:
            return self.s.scalar("select id from failedCards limit 1")
        # failed card queue too big?
        if (self.delay0 and self.failedCardMax and
            self.failedSoonCount >= self.failedCardMax):
            return self.s.scalar(
                "select id from failedCards limit 1")
        # distribute new cards?
        if self._timeForNewCard():
            id = self._maybeGetNewCard()
            if id:
                return id
        # card due for review?
        if self.revCount:
            return self._getRevCard()
        # new cards left?
        id = self._maybeGetNewCard()
        if id:
            return id
        # review ahead?
        if self.reviewEarly:
            id = self.getCardIdAhead()
            if id:
                return id
            else:
                self.resetAfterReviewEarly()
                self.checkDue()
        # display failed cards early/last
        if self._showFailedLast():
            id = self.s.scalar(
                "select id from failedCards limit 1")
            if id:
                return id

    def getCardIdAhead(self):
        "Return the first card that would become due."
        id = self.s.scalar("""
select id from cards
where type = 1 and isDue = 0 and priority in (1,2,3,4)
order by combinedDue
limit 1""")
        return id

    # Get card: helper functions
    ##########################################################################

    def _timeForNewCard(self):
        "True if it's time to display a new card when distributing."
        if self.newCardSpacing == NEW_CARDS_LAST:
            return False
        if self.newCardSpacing == NEW_CARDS_FIRST:
            return True
        # force old if there are very high priority cards
        if self.s.scalar(
            "select 1 from cards where type = 1 and isDue = 1 "
            "and priority = 4 limit 1"):
            return False
        if self.newCardModulus:
            return self._dailyStats.reps % self.newCardModulus == 0
        else:
            return False

    def _maybeGetNewCard(self):
        "Get a new card, provided daily new card limit not exceeded."
        if not self.newCountToday:
            return
        return self._getNewCard()

    def newCardTable(self):
        return ("acqCardsRandom",
                "acqCardsOld",
                "acqCardsNew")[self.newCardOrder]

    def revCardTable(self):
        return ("revCardsOld",
                "revCardsNew",
                "revCardsDue",
                "revCardsRandom")[self.revCardOrder]

    def _getNewCard(self):
        "Return the next new card id, if exists."
        return self.s.scalar(
            "select id from %s limit 1" % self.newCardTable())

    def _getRevCard(self):
        "Return the next review card id."
        return self.s.scalar(
            "select id from %s limit 1" % self.revCardTable())

    def _showFailedLast(self):
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
        card.genFuzz()
        card.startTimer()
        return card

    # Getting cards in bulk
    ##########################################################################
    # this is used for the website and ankimini
    # done in rows for efficiency

    def getCards(self, extraMunge=None):
        "Get a number of cards and related data for client display."
        d = self._getCardTables()
        def munge(row):
            row = list(row)
            row[0] = str(row[0])
            row[1] = str(row[1])
            row[2] = int(row[2])
            row[5] = hexifyID(row[5])
            if extraMunge:
                return extraMunge(row)
            return row
        for type in ('fail', 'rev', 'acq'):
            d[type] = [munge(x) for x in d[type]]
        if d['fail'] or d['rev'] or d['acq']:
            d['stats'] = self.getStats()
            d['status'] = 'cardsAvailable'
            d['initialIntervals'] = (
                self.hardIntervalMin,
                self.hardIntervalMax,
                self.midIntervalMin,
                self.midIntervalMax,
                self.easyIntervalMin,
                self.easyIntervalMax,
                )
            d['newCardSpacing'] = self.newCardSpacing
            d['newCardModulus'] = self.newCardModulus
            return d
        else:
            if self.isEmpty():
                fin = ""
            else:
                fin = self.deckFinishedMsg()
            return {"status": "deckFinished",
                    "finishedMsg": fin}

    def _getCardTables(self):
        self.checkDue()
        sel = """
select id, factId, modified, question, answer, cardModelId,
type, due, interval, factor, priority from """
        new = self.newCardTable()
        rev = self.revCardTable()
        d = {}
        d['fail'] = self.s.all(sel + """
cards where type = 0 and isDue = 1 and
combinedDue <= :now limit 30""", now=time.time())
        d['rev'] = self.s.all(sel + rev + " limit 30")
        if self.newCountToday:
            d['acq'] = self.s.all(sel + """
%s where factId in (select distinct factId from cards
where factId in (select factId from %s limit 60))""" % (new, new))
        else:
            d['acq'] = []
        if (not d['fail'] and not d['rev'] and not d['acq']):
            d['fail'] = self.s.all(sel + "failedCards limit 100")
        return d

    # Answering a card
    ##########################################################################

    def answerCard(self, card, ease):
        undoName = _("Answer Card")
        self.setUndoStart(undoName)
        now = time.time()
        # old state
        oldState = self.cardState(card)
        lastDelaySecs = time.time() - card.combinedDue
        lastDelay = lastDelaySecs / 86400.0
        oldSuc = card.successive
        # update card details
        last = card.interval
        card.interval = self.nextInterval(card, ease)
        if lastDelay >= 0:
            # keep last interval if reviewing early
            card.lastInterval = last
        card.lastDue = card.due
        card.due = self.nextDue(card, ease, oldState)
        card.isDue = 0
        card.lastFactor = card.factor
        if lastDelay >= 0:
            # don't update factor if learning ahead
            self.updateFactor(card, ease)
        # spacing
        (minSpacing, spaceFactor) = self.s.first("""
select models.initialSpacing, models.spacing from
facts, models where facts.modelId = models.id and facts.id = :id""", id=card.factId)
        minOfOtherCards = self.s.scalar("""
select min(interval) from cards
where factId = :fid and id != :id""", fid=card.factId, id=card.id) or 0
        if minOfOtherCards:
            space = min(minOfOtherCards, card.interval)
        else:
            space = 0
        space = space * spaceFactor * 86400.0
        space = max(minSpacing, space)
        space += time.time()
        # check what other cards we've spaced
        if self.reviewEarly:
            extra = ""
        else:
            # if not reviewing early, make sure the current card is counted
            # even if it was not due yet (it's a failed card)
            extra = "or id = :cid"
        for (type, count) in self.s.all("""
select type, count(type) from cards
where factId = :fid and
(isDue = 1 %s)
group by type""" % extra, fid=card.factId, cid=card.id):
            if type == 0:
                self.failedSoonCount -= count
            elif type == 1:
                self.revCount -= count
            else:
                self.newCount -= count
        # space other cards
        self.s.statement("""
update cards set
spaceUntil = :space,
combinedDue = max(:space, due),
modified = :now,
isDue = 0
where id != :id and factId = :factId""",
                         id=card.id, space=space, now=now, factId=card.factId)
        card.spaceUntil = 0
        # temp suspend if learning ahead
        if self.reviewEarly and lastDelay < 0:
            if oldSuc or lastDelaySecs > self.delay0 or not self._showFailedLast():
                card.priority = 0
                self.reviewedAheadCards.append(card.id)
        # card stats
        anki.cards.Card.updateStats(card, ease, oldState)
        card.toDB(self.s)
        # global/daily stats
        anki.stats.updateAllStats(self.s, self._globalStats, self._dailyStats,
                                  card, ease, oldState)
        # review history
        entry = CardHistoryEntry(card, ease, lastDelay)
        entry.writeSQL(self.s)
        self.modified = now
        self.setUndoEnd(undoName)
        # decrease new card boost
        if self.extraNewCards:
            self.extraNewCards -= 1

    # Interval management
    ##########################################################################

    def nextInterval(self, card, ease):
        "Return the next interval for CARD given EASE."
        delay = self._adjustedDelay(card, ease)
        return self._nextInterval(card, delay, ease)

    def _nextInterval(self, card, delay, ease):
        interval = card.interval
        factor = card.factor
        # if shown early and not failed
        if delay < 0 and card.successive:
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
                interval *= (mid / interval / factor)
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
            if oldState == "mature":
                due = self.delay1
            else:
                due = self.delay0
        else:
            due =  card.interval * 86400.0
        return due + time.time()

    def updateFactor(self, card, ease):
        "Update CARD's factor based on EASE."
        card.lastFactor = card.factor
        if not card.reps:
            # card is new, inherit beginning factor
            card.factor = self.averageFactor
        if self.cardIsBeingLearnt(card) and ease in [0, 1, 2]:
            # only penalize failures after success when starting
            if card.successive and ease != 2:
                card.factor -= 0.20
        elif ease in [0, 1]:
            card.factor -= 0.20
        elif ease == 2:
            card.factor -= 0.15
        elif ease == 4:
            card.factor += 0.10
        card.factor = max(1.3, card.factor)

    def _adjustedDelay(self, card, ease):
        "Return an adjusted delay value for CARD based on EASE."
        if self.cardIsNew(card):
            return 0
        if card.combinedDue <= time.time():
            return (time.time() - card.due) / 86400.0
        else:
            return (time.time() - card.combinedDue) / 86400.0

    def resetCards(self, ids):
        "Reset progress on cards in IDS."
        self.s.statement("""
update cards set interval = :new, lastInterval = 0, lastDue = 0,
factor = 2.5, reps = 0, successive = 0, averageTime = 0, reviewTime = 0,
youngEase0 = 0, youngEase1 = 0, youngEase2 = 0, youngEase3 = 0,
youngEase4 = 0, matureEase0 = 0, matureEase1 = 0, matureEase2 = 0,
matureEase3 = 0,matureEase4 = 0, yesCount = 0, noCount = 0,
spaceUntil = 0, isDue = 0, type = 2,
combinedDue = created, modified = :now, due = created
where id in %s""" % ids2str(ids), now=time.time(), new=0)
        self.flushMod()

    def rescheduleCards(self, ids, min, max):
        "Reset cards and schedule with new interval in days (min, max)."
        self.resetCards(ids)
        vals = []
        for id in ids:
            r = random.uniform(min*86400, max*86400)
            vals.append({
                'id': id,
                'due': r + time.time(),
                'int': r / 86400.0
                })
        self.s.statements("""
update cards set
interval = :int,
due = :due,
combinedDue = :due,
reps = 1,
successive = 1,
yesCount = 1,
type = 1,
isDue = 0
where id = :id""", vals)
        self.flushMod()

    # Queue/cache management
    ##########################################################################

    def rebuildTypes(self, where=""):
        "Rebuild the type cache. Only necessary on upgrade."
        self.s.statement("""
update cards
set type = (case
when successive = 0 and reps != 0
then 0 -- failed
when successive != 0 and reps != 0
then 1 -- review
else 2 -- new
end)""" + where)

    def rebuildCounts(self, full=True):
        # need to check due first, so new due cards are not added later
        self.checkDue()
        # global counts
        if full:
            self.cardCount = self.s.scalar("select count(*) from cards")
            self.factCount = self.s.scalar("select count(*) from facts")
        # due counts
        self.failedSoonCount = self.s.scalar(
            "select count(*) from failedCards")
        self.failedNowCount = self.s.scalar("""
select count(*) from cards where type = 0 and isDue = 1
and combinedDue <= :t""", t=time.time())
        self.revCount = self.s.scalar(
            "select count(*) from cards where "
            "type = 1 and priority in (1,2,3,4) and isDue = 1")
        self.newCount = self.s.scalar(
            "select count(*) from cards where "
            "type = 2 and priority in (1,2,3,4) and isDue = 1")

    def checkDue(self):
        "Mark expired cards due, and update counts."
        self.checkDailyStats()
        # mark due & update counts
        stmt = """
update cards set
isDue = 1 where type = %d and isDue = 0 and
priority in (1,2,3,4) and combinedDue <= :now"""
        # failed cards
        self.failedSoonCount += self.s.statement(
            stmt % 0, now=time.time()+self.delay0).rowcount
        self.failedNowCount = self.s.scalar("""
select count(*) from cards where
type = 0 and isDue = 1 and combinedDue <= :now""", now=time.time())
        # review
        self.revCount += self.s.statement(
            stmt % 1, now=time.time()).rowcount
        # new
        self.newCount += self.s.statement(
            stmt % 2, now=time.time()).rowcount
        self.newCountToday = max(min(
            self.newCount, self.newCardsPerDay -
            self.newCardsToday()), 0) + self.extraNewCards

    def rebuildQueue(self):
        "Update relative delays based on current time."
        t = time.time()
        # setup global/daily stats
        self._globalStats = globalStats(self)
        self._dailyStats = dailyStats(self)
        # mark due cards and update counts
        self.checkDue()
        # invalid card count
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
        # determine starting factor for new cards
        self.averageFactor = (self.s.scalar(
            "select avg(factor) from cards where type = 1")
                               or Deck.initialFactor)
        # recache css
        self.rebuildCSS()
        #print "rebuild queue", time.time() - t

    def checkDailyStats(self):
        # check if the day has rolled over
        if genToday(self) != self._dailyStats.day:
            self._dailyStats = dailyStats(self)

    def resetAfterReviewEarly(self):
        if self.reviewedAheadCards:
            self.updatePriorities(self.reviewedAheadCards)
            self.reviewedAheadCards = []
            self.reviewEarly = False
            self.flushMod()
        self.checkDue()

    # Times
    ##########################################################################

    def nextDueMsg(self):
        next = self.earliestTime()
        if next:
            newCardsTomorrow = min(self.newCount, self.newCardsPerDay)
            cards = self.cardsDueBy(time.time() + 86400)
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
            if next - time.time() > 86400 and not newCardsTomorrow:
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
        return self.s.scalar("""
select combinedDue from cards where priority in (1,2,3,4) and
type in (0, 1) order by combinedDue limit 1""")

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
        return self.s.scalar("""
select count(id) from cards where combinedDue < :time
and priority in (1,2,3,4) and type in (0, 1)""", time=time)

    def deckFinishedMsg(self):
        self.resetAfterReviewEarly()
        spaceSusp = ""
        c= self.newSpacedCount()
        if c: 
            spaceSusp += ngettext('There is <b>%d</b> delayed new card.',
                                  'There are <b>%d</b> delayed new cards.',
                                  c) % c
        c2 = self.suspendedCardCount()
        if c2:
            if c:
                spaceSusp += "<br>"
            spaceSusp += ngettext('There is <b>%d</b> suspended card.',
                                  'There are <b>%d</b> suspended cards.',
                                  c) % c2
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

    def updateAllPriorities(self, partial=False):
        "Update all card priorities if changed."
        new = self.updateTagPriorities()
        if not partial:
            new = self.s.all("select id, priority as pri from tags")
        cids = self.s.column0("select cardId from cardTags where tagId in %s" %
                              ids2str([x['id'] for x in new]))
        self.updatePriorities(cids)

    def updateTagPriorities(self):
        "Update priority setting on tags table."
        # make sure all priority tags exist
        for s in (self.lowPriority, self.medPriority,
                  self.highPriority, self.suspended):
            tagIds(self.s, parseTags(s))
        tags = self.s.all("select lower(tag), id, priority from tags")
        up = {}
        for (type, pri) in ((self.lowPriority, 1),
                            (self.medPriority, 3),
                            (self.highPriority, 4),
                            (self.suspended, 0)):
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

    def updatePriorities(self, cardIds, suspend=[]):
        # any tags to suspend
        if suspend:
            ids = tagIds(self.s, suspend)
            self.s.statement(
                "update tags set priority = 0 where id in %s" %
                ids2str(ids.values()))
        cards = self.s.all("""
select cardTags.cardId,
case
when min(tags.priority) = 0 then 0
when max(tags.priority) > 2 then max(tags.priority)
when min(tags.priority) = 1 then 1
else 2 end
from cardTags, tags
where cardTags.tagId = tags.id
and cardTags.cardId in %s
group by cardTags.cardId""" % ids2str(cardIds))
        self.s.statements(
            "update cards set priority = :pri where id = :id",
            [{'id': c[0], 'pri': c[1]} for c in cards])
        self.s.execute(
            "update cards set isDue = 0 where type in (0,1,2) and "
            "priority = 0 and isDue = 1")

    def updatePriority(self, card):
        "Update priority on a single card."
        self.s.flush()
        self.updatePriorities([card.id])

    # Card/fact counts - all in deck, not just due
    ##########################################################################

    def suspendedCardCount(self):
        return self.s.scalar("""
select count(id) from cards where type in (0,1,2) and priority = 0""")

    def seenCardCount(self):
        return self.s.scalar(
            "select count(id) from cards where type != 2")

    # Counts related to due cards
    ##########################################################################

    def newCardsToday(self):
        return (self._dailyStats.newEase0 +
                self._dailyStats.newEase1 +
                self._dailyStats.newEase2 +
                self._dailyStats.newEase3 +
                self._dailyStats.newEase4)

    def spacedCardCount(self):
        return self.s.scalar("""
select count(cards.id) from cards where
type in (1,2) and isDue = 0 and priority in (1,2,3,4) and combinedDue > :now
and due < :now""", now=time.time())

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
            "select count(id) from cards where type = 2")
            
    def newSpacedCount(self):
        "Cards that are both spaced and new."
        return self.s.scalar("""
 select count(cards.id) from cards where
type = 2 and isDue = 0 and priority in (1,2,3,4) and combinedDue > :now
and due < :now""", now=time.time())

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
        return card.interval < self.easyIntervalMin

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

    def queueForCard(self, card):
        "Return the queue the current card is in."
        if self.cardIsNew(card):
            return "new"
        elif card.successive == 0:
            return "failed"
        elif card.reps:
            return "rev"

    # Facts
    ##########################################################################

    def newFact(self, model=None):
        "Return a new fact with the current model."
        if model is None:
            model = self.currentModel
        return anki.facts.Fact(model)

    def addFact(self, fact):
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
        self.factCount += 1
        self.flushMod()
        for cardModel in cms:
            card = anki.cards.Card(fact, cardModel)
            self.flushMod()
            self.updateCardTags([card.id])
            self.updatePriority(card)
            cards.append(card)
        self.updateFactTags([fact.id])
        self.cardCount += len(cards)
        self.newCount += len(cards)
        # keep track of last used tags for convenience
        self.lastTags = fact.tags
        self.flushMod()
        return fact

    def availableCardModels(self, fact, checkActive=True):
        "List of active card models that aren't empty for FACT."
        models = []
        for cardModel in fact.model.cardModels:
           if cardModel.active or not checkActive:
               ok = True
               for (type, format) in [("q", cardModel.qformat),
                                      ("a", cardModel.aformat)]:
                   empty = {}
                   local = {}; local.update(fact)
                   for k in fact.keys():
                       empty[k] = u""
                       empty["text:"+k] = u""
                       local["text:"+k] = u""
                   empty['tags'] = ""
                   local['tags'] = fact.tags
                   try:
                       if format % local == format % empty:
                           ok = False
                   except (KeyError, TypeError, ValueError):
                       ok = False
               if ok or type == "a" and cardModel.allowEmptyAnswer:
                   models.append(cardModel)
        return models

    def addCards(self, fact, cardModelIds):
        "Caller must flush first, flushMod after, rebuild priorities."
        for cardModel in self.availableCardModels(fact, False):
            if cardModel.id not in cardModelIds:
                continue
            if self.s.scalar("""
select count(id) from cards
where factId = :fid and cardModelId = :cmid""",
                                 fid=fact.id, cmid=cardModel.id) == 0:
                    card = anki.cards.Card(fact, cardModel)
                    self.updateCardTags([card.id])
                    self.updatePriority(card)
                    self.cardCount += 1
                    self.newCount += 1
        self.setModified()

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
        "Bulk delete facts by ID. Assume any cards have already been removed."
        if not ids:
            return
        self.s.flush()
        now = time.time()
        strids = ids2str(ids)
        self.s.statement("delete from facts where id in %s" % strids)
        self.s.statement("delete from fields where factId in %s" % strids)
        data = [{'id': id, 'time': now} for id in ids]
        self.s.statements("insert into factsDeleted values (:id, :time)", data)
        self.rebuildCounts()
        self.setModified()

    def deleteDanglingFacts(self):
        "Delete any facts without cards. Return deleted ids."
        ids = self.s.column0("""
select facts.id from facts
where facts.id not in (select factId from cards)""")
        self.deleteFacts(ids)
        return ids

    def previewFact(self, oldFact):
        "Duplicate fact and generate cards for preview. Don't add to deck."
        # check we have card models available
        cms = self.availableCardModels(oldFact)
        if not cms:
            return []
        fact = self.cloneFact(oldFact)
        # proceed
        cards = []
        for cardModel in cms:
            card = anki.cards.Card(fact, cardModel)
            cards.append(card)
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
        "Bulk delete cards by ID."
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
        # remove any dangling facts
        self.deleteDanglingFacts()
        self.rebuildCounts()
        self.refresh()
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
        "Delete MODEL, and delete any referencing cards/facts. Maybe flush."
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
            self.refresh()
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
            (id, fam, siz, col, align) = row
            t = ""
            if fam: t += 'font-family:"%s";' % toPlatformFont(fam)
            if siz: t += 'font-size:%dpx;' % siz
            if col: t += 'color:%s;' % col
            if align != -1:
                if align == 0: align = "center"
                elif align == 1: align = "left"
                else: align = "right"
                t += 'text-align:%s;' % align
            if t:
                t = "%s%s {%s}\n" % (prefix, hexifyID(id), t)
            return t
        css = "".join([_genCSS(".fm", row) for row in self.s.all("""
select id, quizFontFamily, quizFontSize, quizFontColour, -1 from fieldModels""")])
        css += "".join([_genCSS("#cmq", row) for row in self.s.all("""
select id, questionFontFamily, questionFontSize, questionFontColour,
questionAlign from cardModels""")])
        css += "".join([_genCSS("#cma", row) for row in self.s.all("""
select id, answerFontFamily, answerFontSize, answerFontColour,
answerAlign from cardModels""")])
        css += "".join([".cmb%s {background:%s;}\n" %
        (hexifyID(row[0]), row[1]) for row in self.s.all("""
select id, lastFontColour from cardModels""")])
        self.css = css
        #print css
        return css

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
        "Caller must call reset."
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
        self.updateCardTags()
        self.updateProgress()
        self.updateAllPriorities()
        self.updateProgress()
        self.rebuildCounts()
        self.refresh()
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
            cm.qformat = cm.qformat.replace("%%(%s)s" % field.name, "")
            cm.aformat = cm.aformat.replace("%%(%s)s" % field.name, "")
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
            cm.qformat = cm.qformat.replace(
                "%%(%s)s" % field.name, "%%(%s)s" % newName)
            cm.aformat = cm.aformat.replace(
                "%%(%s)s" % field.name, "%%(%s)s" % newName)
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
        tags = dict([(x[0], x[1:]) for x in
                     self.splitTagsList(
            where="and cards.id in %s" %
            ids2str([x[0] for x in ids]))])
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
        pend = [formatQA(cid, mid, facts[fid], tags[cid], cms[cmid])
                for (cid, cmid, fid, mid) in ids]
        if pend:
            self.s.execute("""
    update cards set
    question = :question, answer = :answer
    %s
    where id = :id""" % mod, pend)
        self.flushMod()

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
        self.s.statement("""
delete from tags where id not in (select distinct tagId from cardTags)
and priority = 2
""")

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
        self.refresh()

    def deleteTags(self, ids, tags):
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
                    {'id': id, 'now': now, 'tags': ", ".join(tmpTags)})
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
        self.refresh()

    # Find
    ##########################################################################

    def _parseQuery(self, query):
        tokens = []
        res = []
        # break query into words or phrases
        for match in re.findall('"(.+?)"|([^ "]+)', query):
            tokens.append(match[0] or match[1])
        for c, token in enumerate(tokens):
            isNeg = token.startswith("-")
            if isNeg:
                token = token[1:]
            isTag = token.startswith("tag:")
            if isTag:
                # tag
                token = token[4:]
            res.append((token, isNeg, isTag))
        return res

    def _findCards(self, query):
        "Find facts matching QUERY."
        tquery = ""
        fquery = ""
        args = {}
        for c, (token, isNeg, isTag) in enumerate(self._parseQuery(query)):
            if isTag:
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
select cards.id from cards, facts where facts.tags = ''
and cards.factId = facts.id """
                else:
                    token = token.replace("*", "%")
                    ids = self.s.column0(
                        "select id from tags where tag like :tag", tag=token)
                    tquery += """
select cardId from cardTags where
cardTags.tagId in %s""" % ids2str(ids)
            else:
                # a field
                if fquery:
                    if isNeg:
                        fquery += " except "
                    else:
                        fquery += " intersect "
                elif isNeg:
                    fquery += "select id from facts except "
                args["_ff_%d" % c] = "%"+token+"%"
                q = "select factId from fields where value like :_ff_%d" % c
                fquery += q
        return (tquery, fquery, args)

    def findCardsWhere(self, query):
        (tquery, fquery, args) = self._findCards(query)
        q = ""
        x = []
        if tquery:
            x.append(" id in (%s)" % tquery)
        if fquery:
            x.append(" factId in (%s)" % fquery)
        if x:
            q += " and ".join(x)
        return q, args

    def findCards(self, query):
        q, args = self.findCardsWhere(query)
        query = "select id from cards"
        if q:
            query += " where " + q
        return self.s.column0(query, **args)

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

    # Progress info
    ##########################################################################

    def startProgress(self, max=0, min=0, title=None):
        self.enableProgressHandler()
        runHook("startProgress", max, min, title)

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

    def getInt(self, key):
        ret = self.s.scalar("select value from deckVars where key = :k",
                            k=key)
        if ret is not None:
            ret = int(ret)
        return ret

    def setVar(self, key, value):
        self.s.statement("insert or replace into deckVars (key, value) "
                         "values (:key, :value)", key=key, value=value)
        self.flushMod()

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
        self.delay1 = d

    def getFailedCardPolicy(self):
        if self.delay0 != self.delay1:
            return 5
        d = self.delay0
        if self.collapseTime == 1:
            if d == 600 and self.failedCardMax == 20:
                return 0
            return 5
        if self.failedCardMax != 0:
            return 5
        if d == 0:
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
            # file-backed
            dir = re.sub("(?i)\.(anki)$", ".media", self.path)
            if create == None:
                # don't create, but return dir
                return dir
            if not os.path.exists(dir) and create:
                try:
                    os.mkdir(dir)
                    # change to the current dir
                    os.chdir(dir)
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

    def refresh(self):
        "Flush and expire all items from the session."
        self.s.flush()
        self.s.expire_all()

    def openSession(self):
        "Open a new session. Assumes old session is already closed."
        self.s = SessionHelper(self.Session(), lock=self.needLock)
        self.s.update(self)
        self.refresh()

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
        self.modified = newTime or time.time()

    def flushMod(self):
        "Mark modified and flush to DB."
        self.setModified()
        self.s.flush()

    def saveAs(self, newPath):
        oldMediaDir = self.mediaDir()
        self.s.flush()
        # remove new deck if it exists
        try:
            os.unlink(newPath)
        except OSError:
            pass
        # setup new db tables, then close
        newDeck = DeckStorage.Deck(newPath)
        newDeck.close()
        # attach new db and copy everything in
        s = self.s.statement
        s("attach database :path as new", path=newPath)
        s("delete from new.decks")
        s("delete from new.stats")
        s("delete from new.tags")
        s("delete from new.cardTags")
        s("insert into new.decks select * from decks")
        s("insert into new.fieldModels select * from fieldModels")
        s("insert into new.modelsDeleted select * from modelsDeleted")
        s("insert into new.cardModels select * from cardModels")
        s("insert into new.facts select * from facts")
        s("insert into new.fields select * from fields")
        s("insert into new.cards select * from cards")
        s("insert into new.factsDeleted select * from factsDeleted")
        s("insert into new.reviewHistory select * from reviewHistory")
        s("insert into new.cardsDeleted select * from cardsDeleted")
        s("insert into new.models select * from models")
        s("insert into new.stats select * from stats")
        s("insert into new.media select * from media")
        s("insert into new.tags select * from tags")
        s("insert into new.cardTags select * from cardTags")
        s("detach database new")
        # close ourselves
        self.s.commit()
        self.close()
        # open new db
        newDeck = DeckStorage.Deck(newPath)
        # move media
        if oldMediaDir:
            newDeck.renameMediaDir(oldMediaDir)
        # and return the new deck object
        return newDeck

    # DB maintenance
    ##########################################################################

    def fixIntegrity(self):
        "Responsibility of caller to call rebuildQueue()"
        self.s.commit()
        self.resetUndo()
        self.startProgress(12)
        self.updateProgress(_("Checking integrity..."))
        if self.s.scalar("pragma integrity_check") != "ok":
            self.finishProgress()
            return _("Database file damaged. Restore from backup.")
        # ensure correct views and indexes are available
        self.updateProgress()
        DeckStorage._addViews(self)
        DeckStorage._addIndices(self)
        problems = []
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
        # forget all deletions (do this before deleting anything)
        self.updateProgress()
        self.s.statement("delete from cardsDeleted")
        self.s.statement("delete from factsDeleted")
        self.s.statement("delete from modelsDeleted")
        self.s.statement("delete from mediaDeleted")
        # facts missing a field?
        self.updateProgress()
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
            self.deleteCards(ids)
            problems.append(ngettext("Deleted %d card with missing fact", 
                            "Deleted %d cards with missing fact", len(ids)) %
                            len(ids))
        # cards missing a card model?
        ids = self.s.column0("""
select id from cards where cardModelId not in
(select id from cardModels)""")
        if ids:
            self.deleteCards(ids)
            problems.append(ngettext("Deleted %d card with no card template", 
                            "Deleted %d cards with no card template", len(ids)) %
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
        # fix problems with cards being scheduled when not due
        self.updateProgress()
        self.s.statement("update cards set isDue = 0")
        # fix problems with conflicts on merge
        self.s.statement("update fields set id = random()")
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
        self.updateAllPriorities()
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
            self.updateCardsFromModel(m)
        # mark everything changed to force sync
        self.s.flush()
        self.s.statement("update cards set modified = :t", t=time.time())
        self.s.statement("update facts set modified = :t", t=time.time())
        self.s.statement("update models set modified = :t", t=time.time())
        self.lastSync = 0
        # rebuild
        self.updateProgress(_("Rebuilding types..."))
        self.rebuildTypes()
        self.updateProgress(_("Rebuilding counts..."))
        self.rebuildCounts()
        # update deck and save
        self.flushMod()
        self.save()
        self.refresh()
        self.updateProgress(_("Rebuilding queue..."))
        self.rebuildQueue()
        self.finishProgress()
        if problems:
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
        self.s.statement("delete from undoLog")
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
            #print "--", s.encode("utf-8")[0:30]
            self.engine.execute(s)
        newend = self._latestUndoRow()
        dst.append([u[0], newstart, newend])
        if mod:
            self.finishProgress()

    def undo(self):
        self._undoredo(self.undoStack, self.redoStack)
        self.refresh()
        self.rebuildCounts()
        runHook("postUndoRedo")

    def redo(self):
        self._undoredo(self.redoStack, self.undoStack)
        self.refresh()
        self.rebuildCounts()
        runHook("postUndoRedo")

    # Dynamic indices
    ##########################################################################

    def updateDynamicIndices(self):
        indices = {
            'intervalDesc':
            '(type, isDue, priority desc, interval desc)',
            'intervalAsc':
            '(type, isDue, priority desc, interval)',
            'randomOrder':
            '(type, isDue, priority desc, factId, ordinal)',
            'dueAsc':
            '(type, isDue, priority desc, due)',
            'dueDesc':
            '(type, isDue, priority desc, due desc)',
            }
        # determine required
        required = []
        if self.revCardOrder == REV_CARDS_OLD_FIRST:
            required.append("intervalDesc")
        if self.revCardOrder == REV_CARDS_NEW_FIRST:
            required.append("intervalAsc")
        if (self.revCardOrder == REV_CARDS_RANDOM or
            self.newCardOrder == NEW_CARDS_RANDOM):
            required.append("randomOrder")
        if (self.revCardOrder == REV_CARDS_DUE_FIRST or
            self.newCardOrder == NEW_CARDS_OLD_FIRST):
            required.append("dueAsc")
        if (self.newCardOrder == NEW_CARDS_NEW_FIRST):
            required.append("dueDesc")
        # add/delete
        for (k, v) in indices.items():
            if k in required:
                self.s.statement(
                    "create index if not exists ix_cards_%s on cards %s" %
                    (k, v))
            else:
                self.s.statement("drop index if exists ix_cards_%s" % k)

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

    def Deck(path=None, backup=True, lock=True):
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
            (engine, session) = DeckStorage._attach(sqlpath, create)
            s = session()
            metadata.create_all(engine)
            if create:
                ver = 999
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
                deck = s.query(Deck).get(1)
            # attach db vars
            deck.path = path
            deck.engine = engine
            deck.Session = session
            deck.needLock = lock
            deck.s = SessionHelper(s, lock=lock)
            deck.s.execute("pragma locking_mode = exclusive")
            # force a write lock
            deck.s.execute("update decks set modified = modified")
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
                    DeckStorage.backup(deck.modified, path)
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
        if deck.utcOffset == -1:
            # needs a reset
            deck.startProgress()
            DeckStorage._setUTCOffset(deck)
            DeckStorage._addIndices(deck)
            for m in deck.models:
                deck.updateCardsFromModel(m)
            deck.finishProgress()
        # fix a bug with current model being unset
        if not deck.currentModel and deck.models:
            deck.currentModel = deck.models[0]
        # ensure the necessary indices are available
        deck.updateDynamicIndices()
        # check counts
        oldc = deck.failedSoonCount + deck.revCount + deck.newCount
        deck.rebuildQueue()
        if ((oldc != deck.failedSoonCount + deck.revCount + deck.newCount) or
            deck.modifiedSinceSave()):
            # save
            deck.s.commit()
        return deck
    Deck = staticmethod(Deck)

    def _attach(path, create):
        "Attach to a file, initializing DB"
        if path is None:
            path = "sqlite://"
        else:
            path = "sqlite:///" + path
        engine = create_engine(path,
                               strategy='threadlocal',
                               connect_args={'timeout': 0})
        session = sessionmaker(bind=engine,
                               autoflush=False,
                               autocommit=True)
        return (engine, session)
    _attach = staticmethod(_attach)

    def _init(s):
        "Add a new deck to the database. Return saved deck."
        deck = Deck()
        s.save(deck)
        s.flush()
        return deck
    _init = staticmethod(_init)

    def _addIndices(deck):
        "Add indices to the DB."
        # failed cards, review early, check due
        deck.s.statement("""
create index if not exists ix_cards_duePriority on cards
(type, isDue, combinedDue, priority)""")
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
        deck.s.statement("""
create index if not exists ix_tags_tag on tags (tag)""")
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
create view acqCardsRandom as
select * from cards
where type = 2 and isDue = 1
order by priority desc, factId, ordinal""")
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
            deck.rebuildQueue()
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
            deck.updateAllPriorities()
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
            # rerun check
            anki.media.rebuildMediaDir(deck, dirty=False)
            # no need to track deleted media yet
            deck.s.execute("delete from mediaDeleted")
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
            deck.rebuildQueue()
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
            deck.updateAllPriorities()
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
        # this check we do regardless of version number since doing it on init
        # seems to crash
        if (deck.s.scalar("pragma page_size") == 1024 or
            deck.s.scalar("pragma legacy_file_format") == 1):
            deck.s.commit()
            deck.s.execute("pragma page_size = 4096")
            deck.s.execute("pragma legacy_file_format = 0")
            deck.s.execute("vacuum")
        if prog:
            deck.finishProgress()
        return deck
    _upgradeDeck = staticmethod(_upgradeDeck)

    def _setUTCOffset(deck):
        # 4am
        deck.utcOffset = time.timezone + 60*60*4
    _setUTCOffset = staticmethod(_setUTCOffset)

    def backup(modified, path):
        # need a non-unicode path
        def backupName(path, num):
            path = os.path.abspath(path)
            path = path.replace("\\", "!")
            path = path.replace("/", "!")
            path = path.replace(":", "")
            path = os.path.join(backupDir, path)
            path = re.sub("\.anki$", ".backup-%d.anki" % num, path)
            return path
        if not os.path.exists(backupDir):
            os.makedirs(backupDir)
        # if the mod time is identical, don't make a new backup
        firstBack = backupName(path, 0)
        if os.path.exists(firstBack):
            s1 = int(modified)
            s2 = int(os.stat(firstBack)[stat.ST_MTIME])
            if s1 == s2:
                return
        # remove the oldest backup if it exists
        oldest = backupName(path, numBackups)
        if os.path.exists(oldest):
            os.chmod(oldest, 0666)
            os.unlink(oldest)
        # move all the other backups up one
        for n in range(numBackups - 1, -1, -1):
            name = backupName(path, n)
            if os.path.exists(name):
                newname = backupName(path, n+1)
                if os.path.exists(newname):
                    os.chmod(newname, 0666)
                    os.unlink(newname)
                os.rename(name, newname)
        # save the current path
        newpath = backupName(path, 0)
        if os.path.exists(newpath):
            os.chmod(newpath, 0666)
            os.unlink(newpath)
        shutil.copy2(path, newpath)
        # set mtimes to be identical
        os.utime(newpath, (modified, modified))
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
