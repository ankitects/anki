# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, sys, os, datetime
import anki, anki.utils
from anki.lang import _, ngettext
from anki.hooks import runFilter

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
        first = self.deck.db.scalar(
            "select time/1000 from revlog where rep = 1 and cardId = :id", id=c.id)
        if first:
            self.addLine(_("First Review"), self.strTime(first))
        self.addLine(_("Changed"), self.strTime(c.modified))
        if c.reps:
            next = time.time() - c.due
            if next > 0:
                next = _("%s ago") % fmt(next)
            else:
                next = _("in %s") % fmt(abs(next))
            self.addLine(_("Due"), next)
        self.addLine(_("Interval"), fmt(c.interval * 86400))
        self.addLine(_("Ease"), fmtFloat(c.factor, point=2))
        if c.reps:
            self.addLine(_("Reviews"), "%d/%d (s=%d)" % (
                c.reps-c.lapses, c.reps, c.successive))
        (cnt, total) = self.deck.db.first(
            "select count(), sum(userTime)/1000 from revlog where cardId = :id", id=c.id)
        if cnt:
            self.addLine(_("Average Time"), fmt(total / float(cnt), point=2))
            self.addLine(_("Total Time"), fmt(total, point=2))
        self.addLine(_("Model Tags"), c.fact.model.tags)
        self.addLine(_("Card Template") + "&nbsp;"*5, c.cardModel.name)
        self.txt += "</table>"
        return self.txt

    def addLine(self, k, v):
        self.txt += "<tr><td><b>%s<b></td><td>%s</td></tr>" % (k, v)

    def strTime(self, tm):
        s = anki.utils.fmtTimeSpan(time.time() - tm)
        return _("%s ago") % s

# Deck stats
##########################################################################

class DeckStats(object):

    def __init__(self, deck):
        self.deck = deck

    def matureCardCount(self):
        return self.deck.db.scalar(
            "select count(id) from cards where interval >= :t ",
            t=MATURE_THRESHOLD)

    def youngCardCount(self):
        return self.deck.db.scalar(
            "select count(id) from cards where interval < :t "
            "and reps != 0", t=MATURE_THRESHOLD)

    def newCountAll(self):
        "All new cards, including spaced."
        return self.deck.db.scalar(
            "select count(id) from cards where type = 2")

    def report(self):
        "Return an HTML string with a report."
        fmtPerc = anki.utils.fmtPercentage
        fmtFloat = anki.utils.fmtFloat
        if self.deck.isEmpty():
            return _("Please add some cards first.") + "<p/>"
        d = self.deck
        html="<h1>" + _("Deck Statistics") + "</h1>"
        html += _("Deck created: <b>%s</b> ago<br>") % self.createdTimeStr()
        total = d.cardCount()
        new = d.newCountAll()
        young = d.youngCardCount()
        old = d.matureCardCount()
        newP = new / float(total) * 100
        youngP = young / float(total) * 100
        oldP = old / float(total) * 100
        stats = {}
        (stats["new"], stats["newP"]) = (new, newP)
        (stats["old"], stats["oldP"]) = (old, oldP)
        (stats["young"], stats["youngP"]) = (young, youngP)
        html += _("Total number of cards:") + " <b>%d</b><br>" % total
        html += _("Total number of facts:") + " <b>%d</b><br><br>" % d.factCount()

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
        (mAll, mYes, mPerc) = self.getMatureCorrect()
        (yAll, yYes, yPerc) = self.getYoungCorrect()
        (nAll, nYes, nPerc) = self.getNewCorrect()
        html += _("Mature cards: <!--correct answers-->") + " <b>" + fmtPerc(mPerc) + (
                "</b> " + _("(%(partOf)d of %(totalSum)d)") % {
                'partOf' : mYes,
                'totalSum' : mAll } + "<br>")
        html += _("Young cards: <!--correct answers-->")  + " <b>" + fmtPerc(yPerc) + (
                "</b> " + _("(%(partOf)d of %(totalSum)d)") % {
                'partOf' : yYes,
                'totalSum' : yAll } + "<br>")
        html += _("First-seen cards:") + " <b>" + fmtPerc(nPerc) + (
                "</b> " + _("(%(partOf)d of %(totalSum)d)") % {
                'partOf' : nYes,
                'totalSum' : nAll } + "<br><br>")
        # average pending time
        existing = d.cardCount() - d.newCount
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

    def getMatureCorrect(self, test=None):
        if not test:
            test = "lastInterval > 21"
        head = "select count() from revlog where %s"
        all = self.deck.db.scalar(head % test)
        yes = self.deck.db.scalar((head % test) + " and ease > 1")
        return (all, yes, yes/float(all)*100)

    def getYoungCorrect(self):
        return self.getMatureCorrect("lastInterval <= 21 and rep != 1")

    def getNewCorrect(self):
        return self.getMatureCorrect("rep = 1")

    def getDaysReviewed(self, start, finish):
        today = self.deck.failedCutoff
        x = today + 86400*start
        y = today + 86400*finish
        return self.deck.db.scalar("""
select count(distinct(cast((time/1000-:off)/86400 as integer))) from revlog
where time >= :x*1000 and time <= :y*1000""",x=x,y=y, off=self.deck.utcOffset)

    def getRepsDone(self, start, finish):
        now = datetime.datetime.today()
        x = time.mktime((now + datetime.timedelta(start)).timetuple())
        y = time.mktime((now + datetime.timedelta(finish)).timetuple())
        return self.deck.db.scalar(
            "select count() from revlog where time >= :x*1000 and time <= :y*1000",
            x=x, y=y)

    def getAverageInterval(self):
        return self.deck.db.scalar(
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
        return self.deck.cardCount() / max(1, self.ageInDays())

    def createdTimeStr(self):
        return anki.utils.fmtTimeSpan(time.time() - self.deck.created)

    def ageInDays(self):
        return (time.time() - self.deck.created) / 86400.0

    def getSumInverseRoundInterval(self):
        return self.deck.db.scalar(
            "select sum(1/round(max(interval, 1)+0.5)) from cards "
            "where cards.reps > 0 "
            "and queue != -1") or 0

    def getWorkloadPeriod(self, period):
        cutoff = time.time() + 86400 * period
        return (self.deck.db.scalar("""
select count(id) from cards
where due < :cutoff
and queue != -1 and type between 0 and 1""", cutoff=cutoff) or 0) / float(period)

    def getPastWorkloadPeriod(self, period):
        cutoff = time.time() - 86400 * period
        return (self.deck.db.scalar("""
select count(*) from revlog
where time > :cutoff*1000""", cutoff=cutoff) or 0) / float(period)

    def getNewPeriod(self, period):
        cutoff = time.time() - 86400 * period
        return (self.deck.db.scalar("""
select count(id) from cards
where created > :cutoff""", cutoff=cutoff) or 0)

    def getFirstPeriod(self, period):
        cutoff = time.time() - 86400 * period
        return (self.deck.db.scalar("""
select count(*) from revlog
where rep = 1 and time > :cutoff*1000""", cutoff=cutoff) or 0)
