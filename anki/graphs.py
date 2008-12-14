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

import datetime

# support frozen distribs
if getattr(sys, "frozen", None):
    os.environ['MATPLOTLIBDATA'] = os.path.join(
        os.path.dirname(sys.argv[0]),
        "matplotlibdata")

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

    def __init__(self, deck, width=8, height=3, dpi=75):
        self.deck = deck
        self.stats = None
        self.width = width
        self.height = height
        self.dpi = dpi

    def calcStats (self):
        if not self.stats:
            days = {}
            daysYoung = {}
            daysMature =  {}
            months = {}
            next = {}
            lowestInDay = 0
            midnightOffset = time.timezone - self.deck.utcOffset
            now = list(time.localtime(time.time()))
            now[3] = 23; now[4] = 59
            self.endOfDay = time.mktime(now) - midnightOffset
            t = time.time()
            young = self.deck.s.all("""
select interval, combinedDue
from cards where priority in (1,2,3,4) and
type in (0, 1) and interval <= 21""")
            mature = self.deck.s.all("""
select interval, combinedDue
from cards where type = 1 and priority in (1,2,3,4) and interval > 21""")

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
from stats""")

            todaydt = datetime.datetime(*list(time.localtime(time.time())[:3]))
            for dest, source in [("dayRepsNew", "combinedNewReps"), ("dayRepsYoung", "combinedYoungReps"), ("dayRepsMature", "matureReps")]:
                self.stats[dest] = dict(
                    map(lambda dr: (-(todaydt -datetime.datetime(
                    *(int(x)for x in dr["day"].split("-")))).days, dr[source]), dayReps))

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

        self.filledGraph(graph, days, ["#f54949", "#c63434"], *argl)

        cheat = fig.add_subplot(111)
        b1 = cheat.bar(0, 0, color = "#d33d3e")
        b2 = cheat.bar(1, 0, color = "#a43635")

        cheat.legend([b1, b2], [
            _("Young"),
            _("Mature")], loc='upper right')

        graph.set_xlim(xmin=self.stats['lowestInDay'], xmax=days)
        return fig

    def workDone(self, days=30):
        
        for type in ["dayRepsNew", "dayRepsYoung", "dayRepsMature"]:
            self.addMissing(self.stats[type], -days, 0)

        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        graph = fig.add_subplot(111)

        args = sum((self.unzip(self.stats[type].items(), limit=days, reverseLimit=True) for type in ["dayRepsMature", "dayRepsYoung", "dayRepsNew"][::-1]), [])

        self.filledGraph(graph, days, ["#afeca6", "#4cd33d", "#3c9731"], *args)
        
        cheat = fig.add_subplot(111)
        b1 = cheat.bar(-3, 0, color = "#afeca6")
        b2 = cheat.bar(-4, 0, color = "#4cd33d")
        b3 = cheat.bar(-5, 0, color = "#3c9731")

        cheat.legend([b1, b2, b3], [
            _("New"),
            _("Young"),
            _("Mature")], loc='upper left')
        
        graph.set_xlim(xmin=-days, xmax=0)
        graph.set_ylim(ymax=max(max(a for a in args[1::2])) + 10)
        
        return fig

    def cumulativeDue(self, days=30):
        self.calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        graph = fig.add_subplot(111)
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
        x = list(x); x.append(99999)
        y.append(count)
        self.filledGraph(graph, days, "#fc9c9c", x, y)
        graph.set_xlim(xmin=self.stats['lowestInDay'], xmax=days)
        graph.set_ylim(ymax=graph.get_ylim()[1]+10)
        return fig

    def intervalPeriod(self, days=30):
        self.calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        ints = self.stats['days']
        self.addMissing(ints, 0, days)
        intervals = self.unzip(ints.items(), limit=days)
        graph = fig.add_subplot(111)
        self.filledGraph(graph, days, "#ece9a6", *intervals)
        graph.set_xlim(xmin=0, xmax=days)
        return fig

    def addedRecently(self, numdays=30, attr='created'):
        self.calcStats()
        days = {}
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        limit = self.endOfDay - (numdays + 1) * 86400
        res = self.deck.s.column0("select %s from cards where %s >= %f" %
                                  (attr, attr, limit))
        for r in res:
            d = int((r - self.endOfDay) / 86400.0)
            days[d] = days.get(d, 0) + 1
        self.addMissing(days, -numdays, 0)
        graph = fig.add_subplot(111)
        intervals = self.unzip(days.items())
        if attr == 'created':
            colour = "#a6e9eb"
        else:
            colour = "#e8a7e9"
        self.filledGraph(graph, numdays, colour, *intervals)
        graph.set_xlim(xmin=-numdays, xmax=0)
        return fig

    def addMissing(self, dic, min, max):
        for i in range(min, max+1):
            if not i in dic:
                dic[i] = 0

    def unzip(self, tuples, fillFix=True, limit=None, reverseLimit=False):
        tuples.sort(cmp=lambda x,y: cmp(x[0], y[0]))
        if limit:
            if reverseLimit:
                tuples = tuples[-limit - 1:]
            else:
                tuples = tuples[:limit + 1]

        new = zip(*tuples)
        return new

    def filledGraph(self, graph, days, colours=["b"], *args):
        if isinstance(colours, str):
            colours = [colours]
        thick = True
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
            lw = 0
            if days < 180:
                lw += 1
            if thick:
                lw += 1
            if days > 360:
                lw = 0
            graph.fill(x, y, c, lw = lw)
            thick = False

        graph.grid(True)
        graph.set_ylim(ymin=0, ymax=max(2, graph.get_ylim()[1]))

    def easeBars(self):
        fig = Figure(figsize=(3, 3), dpi=self.dpi)
        graph = fig.add_subplot(111)
        types = ("new", "young", "mature")
        enum = 5
        offset = 0
        arrsize = 16
        arr = [0] * arrsize
        n = 0
        colours = ["#a7afeb", "#3d4fd4", "#1b2ba3"]
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
        graph.legend([p[0] for p in bars], (_("New"),
                                            _("Young"),
                                            _("Mature")),
                     'upper left')
        graph.set_ylim(ymax=100)
        graph.set_xlim(xmax=15)
        graph.set_xticks(range(arrsize))
        graph.set_xticklabels(x)
        graph.grid(True)
        return fig
