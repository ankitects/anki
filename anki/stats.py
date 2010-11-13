# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Statistical tracking and reports
=================================
"""
__docformat__ = 'restructuredtext'

# we track statistics over the life of the deck, and per-day
STATS_LIFE = 0
STATS_DAY = 1

import unicodedata, time, sys, os, datetime
import anki, anki.utils
from datetime import date
from anki.db import *
from anki.lang import _, ngettext
from anki.utils import canonifyTags, ids2str
from anki.hooks import runFilter

# Tracking stats on the DB
##########################################################################

statsTable = Table(
    'stats', metadata,
    Column('id', Integer, primary_key=True),
    Column('type', Integer, nullable=False),
    Column('day', Date, nullable=False),
    Column('reps', Integer, nullable=False, default=0),
    Column('averageTime', Float, nullable=False, default=0),
    Column('reviewTime', Float, nullable=False, default=0),
    # next two columns no longer used
    Column('distractedTime', Float, nullable=False, default=0),
    Column('distractedReps', Integer, nullable=False, default=0),
    Column('newEase0', Integer, nullable=False, default=0),
    Column('newEase1', Integer, nullable=False, default=0),
    Column('newEase2', Integer, nullable=False, default=0),
    Column('newEase3', Integer, nullable=False, default=0),
    Column('newEase4', Integer, nullable=False, default=0),
    Column('youngEase0', Integer, nullable=False, default=0),
    Column('youngEase1', Integer, nullable=False, default=0),
    Column('youngEase2', Integer, nullable=False, default=0),
    Column('youngEase3', Integer, nullable=False, default=0),
    Column('youngEase4', Integer, nullable=False, default=0),
    Column('matureEase0', Integer, nullable=False, default=0),
    Column('matureEase1', Integer, nullable=False, default=0),
    Column('matureEase2', Integer, nullable=False, default=0),
    Column('matureEase3', Integer, nullable=False, default=0),
    Column('matureEase4', Integer, nullable=False, default=0))

class Stats(object):
    def __init__(self):
        self.day = None
        self.reps = 0
        self.averageTime = 0
        self.reviewTime = 0
        self.distractedTime = 0
        self.distractedReps = 0
        self.newEase0 = 0
        self.newEase1 = 0
        self.newEase2 = 0
        self.newEase3 = 0
        self.newEase4 = 0
        self.youngEase0 = 0
        self.youngEase1 = 0
        self.youngEase2 = 0
        self.youngEase3 = 0
        self.youngEase4 = 0
        self.matureEase0 = 0
        self.matureEase1 = 0
        self.matureEase2 = 0
        self.matureEase3 = 0
        self.matureEase4 = 0

    def fromDB(self, s, id):
        r = s.first("select * from stats where id = :id", id=id)
        (self.id,
         self.type,
         self.day,
         self.reps,
         self.averageTime,
         self.reviewTime,
         self.distractedTime,
         self.distractedReps,
         self.newEase0,
         self.newEase1,
         self.newEase2,
         self.newEase3,
         self.newEase4,
         self.youngEase0,
         self.youngEase1,
         self.youngEase2,
         self.youngEase3,
         self.youngEase4,
         self.matureEase0,
         self.matureEase1,
         self.matureEase2,
         self.matureEase3,
         self.matureEase4) = r
        self.day = datetime.date(*[int(i) for i in self.day.split("-")])

    def create(self, s, type, day):
        self.type = type
        self.day = day
        s.execute("""insert into stats
(type, day, reps, averageTime, reviewTime, distractedTime, distractedReps,
newEase0, newEase1, newEase2, newEase3, newEase4, youngEase0, youngEase1,
youngEase2, youngEase3, youngEase4, matureEase0, matureEase1, matureEase2,
matureEase3, matureEase4) values (:type, :day, 0, 0, 0, 0, 0, 0, 0, 0, 0,
0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)""", self.__dict__)
        self.id = s.scalar(
            "select id from stats where type = :type and day = :day",
            type=type, day=day)

    def toDB(self, s):
        assert self.id
        s.execute("""update stats set
type=:type,
day=:day,
reps=:reps,
averageTime=:averageTime,
reviewTime=:reviewTime,
newEase0=:newEase0,
newEase1=:newEase1,
newEase2=:newEase2,
newEase3=:newEase3,
newEase4=:newEase4,
youngEase0=:youngEase0,
youngEase1=:youngEase1,
youngEase2=:youngEase2,
youngEase3=:youngEase3,
youngEase4=:youngEase4,
matureEase0=:matureEase0,
matureEase1=:matureEase1,
matureEase2=:matureEase2,
matureEase3=:matureEase3,
matureEase4=:matureEase4
where id = :id""", self.__dict__)

mapper(Stats, statsTable)

def genToday(deck):
    return datetime.datetime.utcfromtimestamp(
        time.time() - deck.utcOffset).date()

def updateAllStats(s, gs, ds, card, ease, oldState):
    "Update global and daily statistics."
    updateStats(s, gs, card, ease, oldState)
    updateStats(s, ds, card, ease, oldState)

def updateStats(s, stats, card, ease, oldState):
    stats.reps += 1
    delay = card.totalTime()
    if delay >= 60:
        stats.reviewTime += 60
    else:
        stats.reviewTime += delay
        stats.averageTime = (
            stats.reviewTime / float(stats.reps))
    # update eases
    attr = oldState + "Ease%d" % ease
    setattr(stats, attr, getattr(stats, attr) + 1)
    stats.toDB(s)

def globalStats(deck):
    s = deck.s
    type = STATS_LIFE
    today = genToday(deck)
    id = s.scalar("select id from stats where type = :type",
                  type=type)
    stats = Stats()
    if id:
        stats.fromDB(s, id)
        return stats
    else:
        stats.create(s, type, today)
    stats.type = type
    return stats

def dailyStats(deck):
    s = deck.s
    type = STATS_DAY
    today = genToday(deck)
    id = s.scalar("select id from stats where type = :type and day = :day",
                  type=type, day=today)
    stats = Stats()
    if id:
        stats.fromDB(s, id)
        return stats
    else:
        stats.create(s, type, today)
    return stats

def summarizeStats(stats, pre=""):
    "Generate percentages and total counts for STATS. Optionally prefix."
    cardTypes = ("new", "young", "mature")
    h = {}
    # total counts
    ###############
    for type in cardTypes:
        # total yes/no for type, eg. gNewYes
        h[pre + type.capitalize() + "No"] = (getattr(stats, type + "Ease0") +
                                             getattr(stats, type + "Ease1"))
        h[pre + type.capitalize() + "Yes"] = (getattr(stats, type + "Ease2") +
                                              getattr(stats, type + "Ease3") +
                                              getattr(stats, type + "Ease4"))
        # total for type, eg. gNewTotal
        h[pre + type.capitalize() + "Total"] = (
            h[pre + type.capitalize() + "No"] +
            h[pre + type.capitalize() + "Yes"])
    # total yes/no, eg. gYesTotal
    for answer in ("yes", "no"):
        num = 0
        for type in cardTypes:
            num += h[pre + type.capitalize() + answer.capitalize()]
        h[pre + answer.capitalize() + "Total"] = num
    # total over all, eg. gTotal
    num = 0
    for type in cardTypes:
        num += h[pre + type.capitalize() + "Total"]
    h[pre + "Total"] = num
    # percentages
    ##############
    for type in cardTypes:
        # total yes/no % by type, eg. gNewYes%
        for answer in ("yes", "no"):
            setPercentage(h, pre + type.capitalize() + answer.capitalize(),
                          pre + type.capitalize())
    for answer in ("yes", "no"):
        # total yes/no, eg. gYesTotal%
        setPercentage(h, pre + answer.capitalize() + "Total", pre)
    h[pre + 'AverageTime'] = stats.averageTime
    h[pre + 'ReviewTime'] = stats.reviewTime
    return h

def setPercentage(h, a, b):
    try:
        h[a + "%"] = (h[a] / float(h[b + "Total"])) * 100
    except ZeroDivisionError:
        h[a + "%"] = 0

def getStats(s, gs, ds):
    "Return a handy dictionary exposing a number of internal stats."
    h = {}
    h.update(summarizeStats(gs, "g"))
    h.update(summarizeStats(ds, "d"))
    return h

# Card stats
##########################################################################

class CardStats(object):

    def __init__(self, deck, card):
        self.deck = deck
        self.card = card

    def report(self):
        c = self.card
        fmt = anki.utils.fmtTimeSpan
        fmtFloat = anki.utils.fmtFloat
        self.txt = "<table>"
        self.addLine(_("Added"), self.strTime(c.created))
        if c.firstAnswered:
            self.addLine(_("First Review"), self.strTime(c.firstAnswered))
        self.addLine(_("Changed"), self.strTime(c.modified))
        if c.reps:
            next = time.time() - c.combinedDue
            if next > 0:
                next = _("%s ago") % fmt(next)
            else:
                next = _("in %s") % fmt(abs(next))
            self.addLine(_("Due"), next)
        self.addLine(_("Interval"), fmt(c.interval * 86400))
        self.addLine(_("Ease"), fmtFloat(c.factor, point=2))
        if c.lastDue:
            last = _("%s ago") % fmt(time.time() - c.lastDue)
            self.addLine(_("Last Due"), last)
        if c.interval != c.lastInterval:
            # don't show the last interval if it hasn't been updated yet
            self.addLine(_("Last Interval"), fmt(c.lastInterval * 86400))
        self.addLine(_("Last Ease"), fmtFloat(c.lastFactor, point=2))
        if c.reps:
            self.addLine(_("Reviews"), "%d/%d (s=%d)" % (
                c.yesCount, c.reps, c.successive))
        avg = fmt(c.averageTime, point=2)
        self.addLine(_("Average Time"),avg)
        total = fmt(c.reviewTime, point=2)
        self.addLine(_("Total Time"), total)
        self.addLine(_("Model Tags"), c.fact.model.tags)
        self.addLine(_("Card Template") + "&nbsp;"*5, c.cardModel.name)
        self.txt += "</table>"
        return self.txt

    def addLine(self, k, v):
        self.txt += "<tr><td><b>%s<b></td><td>%s</td></tr>" % (k, v)

    def strTime(self, tm):
        s = anki.utils.fmtTimeSpan(time.time() - tm)
        return _("%s ago") % s

# Deck stats (specific to the 'sched' scheduler)
##########################################################################

class DeckStats(object):

    def __init__(self, deck):
        self.deck = deck

    def report(self):
        "Return an HTML string with a report."
        fmtPerc = anki.utils.fmtPercentage
        fmtFloat = anki.utils.fmtFloat
        if self.deck.isEmpty():
            return _("Please add some cards first.") + "<p/>"
        d = self.deck
        html="<h1>" + _("Deck Statistics") + "</h1>"
        html += _("Deck created: <b>%s</b> ago<br>") % self.createdTimeStr()
        total = d.cardCount
        new = d.newCountAll()
        young = d.youngCardCount()
        old = d.matureCardCount()
        newP = new / float(total) * 100
        youngP = young / float(total) * 100
        oldP = old / float(total) * 100
        stats = d.getStats()
        (stats["new"], stats["newP"]) = (new, newP)
        (stats["old"], stats["oldP"]) = (old, oldP)
        (stats["young"], stats["youngP"]) = (young, youngP)
        html += _("Total number of cards:") + " <b>%d</b><br>" % total
        html += _("Total number of facts:") + " <b>%d</b><br><br>" % d.factCount

        html += "<b>" + _("Card Maturity") + "</b><br>"
        html += _("Mature cards: <!--card count-->") + " <b>%(old)d</b> (%(oldP)s)<br>" % {
                'old': stats['old'], 'oldP' : fmtPerc(stats['oldP'])}
        html += _("Young cards: <!--card count-->") + " <b>%(young)d</b> (%(youngP)s)<br>" % {
                'young': stats['young'], 'youngP' : fmtPerc(stats['youngP'])}
        html += _("Unseen cards:") + " <b>%(new)d</b> (%(newP)s)<br>" % {
                'new': stats['new'], 'newP' : fmtPerc(stats['newP'])}
        avgInt = self.getAverageInterval()
        if avgInt:
            html += _("Average interval: ") + ("<b>%s</b> ") % fmtFloat(avgInt) + _("days")
            html += "<br>"
        html += "<br>"
        html += "<b>" + _("Correct Answers") + "</b><br>"
        html += _("Mature cards: <!--correct answers-->") + " <b>" + fmtPerc(stats['gMatureYes%']) + (
                "</b> " + _("(%(partOf)d of %(totalSum)d)") % {
                'partOf' : stats['gMatureYes'],
                'totalSum' : stats['gMatureTotal'] } + "<br>")
        html += _("Young cards: <!--correct answers-->")  + " <b>" + fmtPerc(stats['gYoungYes%']) + (
                "</b> " + _("(%(partOf)d of %(totalSum)d)") % {
                'partOf' : stats['gYoungYes'],
                'totalSum' : stats['gYoungTotal'] } + "<br>")
        html += _("First-seen cards:") + " <b>" + fmtPerc(stats['gNewYes%']) + (
                "</b> " + _("(%(partOf)d of %(totalSum)d)") % {
                'partOf' : stats['gNewYes'],
                'totalSum' : stats['gNewTotal'] } + "<br><br>")

        # average pending time
        existing = d.cardCount - d.newCountToday
        def tr(a, b):
            return "<tr><td>%s</td><td align=right>%s</td></tr>" % (a, b)
        def repsPerDay(reps,days):
            retval =  ("<b>%d</b> " % reps)  + ngettext("rep", "reps", reps)
            retval += ("/<b>%d</b> " % days) + ngettext("day", "days", days)
            return retval
        if existing and avgInt:
            html += "<b>" + _("Recent Work") + "</b>"
            if sys.platform.startswith("darwin"):
                html += "<table width=250>"
            else:
                html += "<table width=200>"
            html += tr(_("In last week"), repsPerDay(
                self.getRepsDone(-7, 0),
                self.getDaysReviewed(-7, 0)))
            html += tr(_("In last month"), repsPerDay(
                self.getRepsDone(-30, 0),
                self.getDaysReviewed(-30, 0)))
            html += tr(_("In last 3 months"), repsPerDay(
                self.getRepsDone(-92, 0),
                self.getDaysReviewed(-92, 0)))
            html += tr(_("In last 6 months"), repsPerDay(
                self.getRepsDone(-182, 0),
                self.getDaysReviewed(-182, 0)))
            html += tr(_("In last year"), repsPerDay(
                self.getRepsDone(-365, 0),
                self.getDaysReviewed(-365, 0)))
            html += tr(_("Deck life"), repsPerDay(
                self.getRepsDone(-13000, 0),
                self.getDaysReviewed(-13000, 0)))
            html += "</table>"

            html += "<br><br><b>" + _("Average Daily Reviews") + "</b>"
            if sys.platform.startswith("darwin"):
                html += "<table width=250>"
            else:
                html += "<table width=200>"
            html += tr(_("Deck life"), ("<b>%s</b> ") % (
                fmtFloat(self.getSumInverseRoundInterval())) + _("cards/day"))
            html += tr(_("In next week"), ("<b>%s</b> ") % (
                fmtFloat(self.getWorkloadPeriod(7))) + _("cards/day"))
            html += tr(_("In next month"), ("<b>%s</b> ") % (
                fmtFloat(self.getWorkloadPeriod(30))) + _("cards/day"))
            html += tr(_("In last week"), ("<b>%s</b> ") % (
                fmtFloat(self.getPastWorkloadPeriod(7))) + _("cards/day"))
            html += tr(_("In last month"), ("<b>%s</b> ") % (
                fmtFloat(self.getPastWorkloadPeriod(30))) + _("cards/day"))
            html += tr(_("In last 3 months"), ("<b>%s</b> ") % (
                fmtFloat(self.getPastWorkloadPeriod(92))) + _("cards/day"))
            html += tr(_("In last 6 months"), ("<b>%s</b> ") % (
                fmtFloat(self.getPastWorkloadPeriod(182))) + _("cards/day"))
            html += tr(_("In last year"), ("<b>%s</b> ") % (
                fmtFloat(self.getPastWorkloadPeriod(365))) + _("cards/day"))
            html += "</table>"

            html += "<br><br><b>" + _("Average Added") + "</b>"
            if sys.platform.startswith("darwin"):
                html += "<table width=250>"
            else:
                html += "<table width=200>"
            html += tr(_("Deck life"), _("<b>%(a)s</b>/day, <b>%(b)s</b>/mon") % {
                'a': fmtFloat(self.newAverage()), 'b': fmtFloat(self.newAverage()*30)})
            np = self.getNewPeriod(7)
            html += tr(_("In last week"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(7))}))
            np = self.getNewPeriod(30)
            html += tr(_("In last month"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(30))}))
            np = self.getNewPeriod(92)
            html += tr(_("In last 3 months"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(92))}))
            np = self.getNewPeriod(182)
            html += tr(_("In last 6 months"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(182))}))
            np = self.getNewPeriod(365)
            html += tr(_("In last year"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(365))}))
            html += "</table>"

            html += "<br><br><b>" + _("Average New Seen") + "</b>"
            if sys.platform.startswith("darwin"):
                html += "<table width=250>"
            else:
                html += "<table width=200>"
            np = self.getFirstPeriod(7)
            html += tr(_("In last week"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(7))}))
            np = self.getFirstPeriod(30)
            html += tr(_("In last month"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(30))}))
            np = self.getFirstPeriod(92)
            html += tr(_("In last 3 months"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(92))}))
            np = self.getFirstPeriod(182)
            html += tr(_("In last 6 months"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(182))}))
            np = self.getFirstPeriod(365)
            html += tr(_("In last year"), _("<b>%(a)d</b> (<b>%(b)s</b>/day)") % (
                {'a': np, 'b': fmtFloat(np / float(365))}))
            html += "</table>"

            html += "<br><br><b>" + _("Card Ease") + "</b><br>"
            html += _("Lowest factor: %.2f") % d.s.scalar(
                "select min(factor) from cards") + "<br>"
            html += _("Average factor: %.2f") % d.s.scalar(
                "select avg(factor) from cards") + "<br>"
            html += _("Highest factor: %.2f") % d.s.scalar(
                "select max(factor) from cards") + "<br>"

            html = runFilter("deckStats", html)
        return html

    def getDaysReviewed(self, start, finish):
        now = datetime.datetime.today()
        x = now + datetime.timedelta(start)
        y = now + datetime.timedelta(finish)
        return self.deck.s.scalar(
            "select count() from stats where "
            "day >= :x and day <= :y and reps > 0",
            x=x, y=y)

    def getRepsDone(self, start, finish):
        now = datetime.datetime.today()
        x = time.mktime((now + datetime.timedelta(start)).timetuple())
        y = time.mktime((now + datetime.timedelta(finish)).timetuple())
        return self.deck.s.scalar(
            "select count() from reviewHistory where time >= :x and time <= :y",
            x=x, y=y)

    def getAverageInterval(self):
        return self.deck.s.scalar(
            "select sum(interval) / count(interval) from cards "
            "where cards.reps > 0") or 0

    def intervalReport(self, intervals, labels, total):
        boxes = self.splitIntoIntervals(intervals)
        keys = boxes.keys()
        keys.sort()
        html = ""
        for key in keys:
            html += ("<tr><td align=right>%s</td><td align=right>" +
                     "%d</td><td align=right>%s</td></tr>") % (
                labels[key],
                boxes[key],
                fmtPerc(boxes[key] / float(total) * 100))
        return html

    def splitIntoIntervals(self, intervals):
        boxes = {}
        n = 0
        for i in range(len(intervals) - 1):
            (min, max) = (intervals[i], intervals[i+1])
            for c in self.deck:
                if c.interval > min and c.interval <=  max:
                    boxes[n] = boxes.get(n, 0) + 1
            n += 1
        return boxes

    def newAverage(self):
        "Average number of new cards added each day."
        return self.deck.cardCount / max(1, self.ageInDays())

    def createdTimeStr(self):
        return anki.utils.fmtTimeSpan(time.time() - self.deck.created)

    def ageInDays(self):
        return (time.time() - self.deck.created) / 86400.0

    def getSumInverseRoundInterval(self):
        return self.deck.s.scalar(
            "select sum(1/round(max(interval, 1)+0.5)) from cards "
            "where cards.reps > 0 "
            "and priority > 0") or 0

    def getWorkloadPeriod(self, period):
        cutoff = time.time() + 86400 * period
        return (self.deck.s.scalar("""
select count(id) from cards
where combinedDue < :cutoff
and priority > 0 and relativeDelay in (0,1)""", cutoff=cutoff) or 0) / float(period)

    def getPastWorkloadPeriod(self, period):
        cutoff = time.time() - 86400 * period
        return (self.deck.s.scalar("""
select count(*) from reviewHistory
where time > :cutoff""", cutoff=cutoff) or 0) / float(period)

    def getNewPeriod(self, period):
        cutoff = time.time() - 86400 * period
        return (self.deck.s.scalar("""
select count(id) from cards
where created > :cutoff""", cutoff=cutoff) or 0)

    def getFirstPeriod(self, period):
        cutoff = time.time() - 86400 * period
        return (self.deck.s.scalar("""
select count(*) from reviewHistory
where reps = 1 and time > :cutoff""", cutoff=cutoff) or 0)
