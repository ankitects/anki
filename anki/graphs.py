# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Graphs of deck statistics
==============================
"""
__docformat__ = 'restructuredtext'

import os, sys, time
import anki.stats
from anki.lang import _

import datetime

#colours for graphs
dueYoungC = "#ffb380"
dueMatureC = "#ff5555"
dueCumulC = "#ff8080"

reviewNewC = "#80ccff"
reviewYoungC = "#3377ff"
reviewMatureC = "#0000ff"
reviewTimeC = "#0fcaff"

easesNewC = "#80b3ff"
easesYoungC = "#5555ff"
easesMatureC = "#0f5aff"

addedC = "#b3ff80"
firstC = "#b380ff"
intervC = "#80e5ff"

# support frozen distribs
if sys.platform.startswith("darwin"):
    try:
        del os.environ['MATPLOTLIBDATA']
    except:
        pass

try:
    from matplotlib.figure import Figure
except UnicodeEncodeError:
    # haven't tracked down the cause of this yet, but reloading fixes it
    try:
        from matplotlib.figure import Figure
    except ImportError:
        pass
except ImportError:
    pass

def graphsAvailable():
    return 'matplotlib' in sys.modules

class DeckGraphs(object):

    def __init__(self, deck, width=8, height=3, dpi=75, selective=True):
        self.deck = deck
        self.stats = None
        self.width = width
        self.height = height
        self.dpi = dpi
        self.selective = selective

    def calcStats (self):
        if not self.stats:
            days = {}
            daysYoung = {}
            daysMature =  {}
            months = {}
            next = {}
            lowestInDay = 0
            self.endOfDay = self.deck.failedCutoff
            t = time.time()
            young = """
select interval, combinedDue from cards c
where relativeDelay between 0 and 1 and type >= 0 and interval <= 21"""
            mature = """
select interval, combinedDue
from cards c where relativeDelay = 1 and type >= 0 and interval > 21"""
            if self.selective:
                young = self.deck._cardLimit("revActive", "revInactive",
                                             young)
                mature = self.deck._cardLimit("revActive", "revInactive",
                                             mature)
            young = self.deck.s.all(young)
            mature = self.deck.s.all(mature)
            for (src, dest) in [(young, daysYoung),
                                (mature, daysMature)]:
                for (interval, due) in src:
                    day=int(round(interval))
                    days[day] = days.get(day, 0) + 1
                    indays = int(((due - self.endOfDay) / 86400.0) + 1)
                    next[indays] = next.get(indays, 0) + 1 # type-agnostic stats
                    dest[indays] = dest.get(indays, 0) + 1 # type-specific stats
                    if indays < lowestInDay:
                        lowestInDay = indays
            self.stats = {}
            self.stats['next'] = next
            self.stats['days'] = days
            self.stats['daysByType'] = {'young': daysYoung,
                                        'mature': daysMature}
            self.stats['months'] = months
            self.stats['lowestInDay'] = lowestInDay

            dayReps = self.deck.s.all("""
select day,
       matureEase0+matureEase1+matureEase2+matureEase3+matureEase4 as matureReps,
       reps-(newEase0+newEase1+newEase2+newEase3+newEase4) as combinedYoungReps,
       reps as combinedNewReps
from stats
where type = 1""")

            dayTimes = self.deck.s.all("""
select day, reviewTime as reviewTime
from stats
where type = 1""")

            todaydt = self.deck._dailyStats.day
            for dest, source in [("dayRepsNew", "combinedNewReps"),
                                 ("dayRepsYoung", "combinedYoungReps"),
                                 ("dayRepsMature", "matureReps")]:
                self.stats[dest] = dict(
                    map(lambda dr: (-(todaydt -datetime.date(
                    *(int(x)for x in dr["day"].split("-")))).days, dr[source]), dayReps))

            self.stats['dayTimes'] = dict(
                map(lambda dr: (-(todaydt -datetime.date(
                *(int(x)for x in dr["day"].split("-")))).days, dr["reviewTime"]/60.0), dayTimes))

    def nextDue(self, days=30):
        self.calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        graph = fig.add_subplot(111)
        dayslists = [self.stats['next'], self.stats['daysByType']['mature']]

        for dayslist in dayslists:
            self.addMissing(dayslist, self.stats['lowestInDay'], days)

        argl = []

        for dayslist in dayslists:
            dl = [x for x in dayslist.items() if x[0] <= days]
            argl.extend(list(self.unzip(dl)))

        self.varGraph(graph, days, [dueYoungC, dueMatureC], *argl)

        cheat = fig.add_subplot(111)
        b1 = cheat.bar(0, 0, color = dueYoungC)
        b2 = cheat.bar(1, 0, color = dueMatureC)

        cheat.legend([b1, b2], [
            "Young",
            "Mature"], loc='upper right')

        graph.set_xlim(xmin=self.stats['lowestInDay'], xmax=days+1)
        graph.set_xlabel("Day (0 = today)")
        graph.set_ylabel("Cards Due")

        return fig

    def workDone(self, days=30):
        self.calcStats()

        for type in ["dayRepsNew", "dayRepsYoung", "dayRepsMature"]:
            self.addMissing(self.stats[type], -days, 0)

        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        graph = fig.add_subplot(111)

        args = sum((self.unzip(self.stats[type].items(), limit=days, reverseLimit=True) for type in ["dayRepsMature", "dayRepsYoung", "dayRepsNew"][::-1]), [])

        self.varGraph(graph, days, [reviewNewC, reviewYoungC, reviewMatureC], *args)

        cheat = fig.add_subplot(111)
        b1 = cheat.bar(-3, 0, color = reviewNewC)
        b2 = cheat.bar(-4, 0, color = reviewYoungC)
        b3 = cheat.bar(-5, 0, color = reviewMatureC)

        cheat.legend([b1, b2, b3], [
            "New",
            "Young",
            "Mature"], loc='upper left')

        graph.set_xlim(xmin=-days+1, xmax=1)
        graph.set_ylim(ymax=max(max(a for a in args[1::2])) + 10)
        graph.set_xlabel("Day (0 = today)")
        graph.set_ylabel("Cards Answered")

        return fig

    def timeSpent(self, days=30):
        self.calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        times = self.stats['dayTimes']
        self.addMissing(times, -days+1, 0)
        times = self.unzip([(day,y) for (day,y) in times.items()
                            if day + days >= 0])
        graph = fig.add_subplot(111)
        self.varGraph(graph, days, reviewTimeC, *times)
        graph.set_xlim(xmin=-days+1, xmax=1)
        graph.set_ylim(ymax=max(a for a in times[1]) + 0.1)
        graph.set_xlabel("Day (0 = today)")
        graph.set_ylabel("Minutes")
        return fig

    def cumulativeDue(self, days=30):
        self.calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        graph = fig.add_subplot(111)
        self.addMissing(self.stats['next'], 0, days-1)
        dl = [x for x in self.stats['next'].items() if x[0] <= days]
        (x, y) = self.unzip(dl)
        count=0
        y = list(y)
        for i in range(len(x)):
            count = count + y[i]
            if i == 0:
                continue
            y[i] = count
            if x[i] > days:
                break
        self._filledGraph(graph, days, dueCumulC, 1, x, y)
        graph.set_xlim(xmin=self.stats['lowestInDay'], xmax=days-1)
        graph.set_ylim(ymax=graph.get_ylim()[1]+10)
        graph.set_xlabel("Day (0 = today)")
        graph.set_ylabel("Cards Due")
        return fig

    def intervalPeriod(self, days=30):
        self.calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        ints = self.stats['days']
        self.addMissing(ints, 0, days)
        intervals = self.unzip(ints.items(), limit=days)
        graph = fig.add_subplot(111)
        self.varGraph(graph, days, intervC, *intervals)
        graph.set_xlim(xmin=0, xmax=days+1)
        graph.set_xlabel("Card Interval")
        graph.set_ylabel("Number of Cards")
        return fig

    def addedRecently(self, numdays=30, attr='created'):
        self.calcStats()
        days = {}
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        limit = self.endOfDay - (numdays) * 86400
        res = self.deck.s.column0("select %s from cards where %s >= %f" %
                                  (attr, attr, limit))
        for r in res:
            d = int((r - self.endOfDay) / 86400.0)
            days[d] = days.get(d, 0) + 1
        self.addMissing(days, -numdays+1, 0)
        graph = fig.add_subplot(111)
        intervals = self.unzip(days.items())
        if attr == 'created':
            colour = addedC
        else:
            colour = firstC
        self.varGraph(graph, numdays, colour, *intervals)
        graph.set_xlim(xmin=-numdays+1, xmax=1)
        graph.set_xlabel("Day (0 = today)")
        if attr == 'created':
            graph.set_ylabel("Cards Added")
        else:
            graph.set_ylabel("Cards First Answered")
        return fig

    def addMissing(self, dic, min, max):
        for i in range(min, max+1):
            if not i in dic:
                dic[i] = 0

    def unzip(self, tuples, fillFix=True, limit=None, reverseLimit=False):
        tuples.sort(cmp=lambda x,y: cmp(x[0], y[0]))
        if limit:
            if reverseLimit:
                tuples = tuples[-limit:]
            else:
                tuples = tuples[:limit+1]
        new = zip(*tuples)
        return new

    def varGraph(self, graph, days, colours=["b"], *args):
        if len(args[0]) < 120:
            return self.barGraph(graph, days, colours, *args)
        else:
            return self.filledGraph(graph, days, colours, *args)

    def filledGraph(self, graph, days, colours=["b"], *args):
        self._filledGraph(graph, days, colours, 0, *args)

    def _filledGraph(self, graph, days, colours, lw, *args):
        if isinstance(colours, str):
            colours = [colours]
        for triplet in [(args[n], args[n + 1], colours[n / 2]) for n in range(0, len(args), 2)]:
            x = list(triplet[0])
            y = list(triplet[1])
            c = triplet[2]
            lowest = 99999
            highest = -lowest
            for i in range(len(x)):
                if x[i] < lowest:
                    lowest = x[i]
                if x[i] > highest:
                    highest = x[i]
            # ensure the filled area reaches the bottom
            x.insert(0, lowest - 1)
            y.insert(0, 0)
            x.append(highest + 1)
            y.append(0)
            # plot
            graph.fill(x, y, c, lw=lw)
        graph.grid(True)
        graph.set_ylim(ymin=0, ymax=max(2, graph.get_ylim()[1]))

    def barGraph(self, graph, days, colours, *args):
        if isinstance(colours, str):
            colours = [colours]
        lim = None
        for triplet in [(args[n], args[n + 1], colours[n / 2]) for n in range(0, len(args), 2)]:
            x = list(triplet[0])
            y = list(triplet[1])
            c = triplet[2]
            lw = 0
            if lim is None:
                lim = (x[0], x[-1])
                length = (lim[1] - lim[0])
                if len(args) > 4:
                    if length <= 30:
                        lw = 1
                else:
                    if length <= 90:
                        lw = 1
            lowest = 99999
            highest = -lowest
            for i in range(len(x)):
                if x[i] < lowest:
                    lowest = x[i]
                if x[i] > highest:
                    highest = x[i]
            graph.bar(x, y, color=c, width=1, linewidth=lw)
        graph.grid(True)
        graph.set_ylim(ymin=0, ymax=max(2, graph.get_ylim()[1]))
        import numpy as np
        if length > 10:
            step = length / 10.0
            # python's range() won't accept float step args, so we do it manually
            if lim[0] < 0:
                ticks = [int(lim[1] - step * x) for x in range(10)]
            else:
                ticks = [int(lim[0] + step * x) for x in range(10)]
        else:
            ticks = list(xrange(lim[0], lim[1]+1))
        graph.set_xticks(np.array(ticks) + 0.5)
        graph.set_xticklabels([str(int(x)) for x in ticks])
        for tick in graph.xaxis.get_major_ticks():
            tick.tick1On = False
            tick.tick2On = False

    def easeBars(self):
        fig = Figure(figsize=(3, 3), dpi=self.dpi)
        graph = fig.add_subplot(111)
        types = ("new", "young", "mature")
        enum = 5
        offset = 0
        arrsize = 16
        arr = [0] * arrsize
        n = 0
        colours = [easesNewC, easesYoungC, easesMatureC]
        bars = []
        gs = anki.stats.globalStats(self.deck)
        for type in types:
            total = (getattr(gs, type + "Ease0") +
                     getattr(gs, type + "Ease1") +
                     getattr(gs, type + "Ease2") +
                     getattr(gs, type + "Ease3") +
                     getattr(gs, type + "Ease4"))
            setattr(gs, type + "Ease1", getattr(gs, type + "Ease0") +
                    getattr(gs, type + "Ease1"))
            setattr(gs, type + "Ease0", -1)
            for e in range(1, enum):
                try:
                    arr[e+offset] = (getattr(gs, type + "Ease%d" % e)
                                     / float(total)) * 100 + 1
                except ZeroDivisionError:
                    arr[e+offset] = 0
            bars.append(graph.bar(range(arrsize), arr, width=1.0,
                                  color=colours[n], align='center'))
            arr = [0] * arrsize
            offset += 5
            n += 1
        x = ([""] + [str(n) for n in range(1, enum)]) * 3
        graph.legend([p[0] for p in bars], ("New",
                                            "Young",
                                            "Mature"),
                     'upper left')
        graph.set_ylim(ymax=100)
        graph.set_xlim(xmax=15)
        graph.set_xticks(range(arrsize))
        graph.set_xticklabels(x)
        graph.set_ylabel("% of Answers")
        graph.set_xlabel("Answer Buttons")
        graph.grid(True)
        return fig
