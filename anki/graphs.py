# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, time, datetime, simplejson
from anki.lang import _

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

class Graphs(object):

    def __init__(self, deck, selective=True):
        self.deck = deck
        self._stats = None
        self.selective = selective

    def workDone(self, days=30):
        self._calcStats()

        for type in ["dayRepsNew", "dayRepsYoung", "dayRepsMature"]:
            self._addMissing(self.stats[type], -days, 0)

        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        graph = fig.add_subplot(111)

        args = sum((self._unzip(self.stats[type].items(), limit=days, reverseLimit=True) for type in ["dayRepsMature", "dayRepsYoung", "dayRepsNew"][::-1]), [])

        self._varGraph(graph, days, [reviewNewC, reviewYoungC, reviewMatureC], *args)

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
        self._calcStats()
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        times = self.stats['dayTimes']
        self._addMissing(times, -days+1, 0)
        times = self._unzip([(day,y) for (day,y) in times.items()
                            if day + days >= 0])
        graph = fig.add_subplot(111)
        self._varGraph(graph, days, reviewTimeC, *times)
        graph.set_xlim(xmin=-days+1, xmax=1)
        graph.set_ylim(ymax=max(a for a in times[1]) + 0.1)
        graph.set_xlabel("Day (0 = today)")
        graph.set_ylabel("Minutes")
        return fig

    def addedRecently(self, numdays=30, attr='crt'):
        self._calcStats()
        days = {}
        fig = Figure(figsize=(self.width, self.height), dpi=self.dpi)
        limit = self.endOfDay - (numdays) * 86400
        if attr == "created":
            res = self.deck.db.list("select %s from cards where %s >= %f" %
                                       (attr, attr, limit))
        else:
            # firstAnswered
            res = self.deck.db.list(
                "select time/1000 from revlog where rep = 1")
        for r in res:
            d = int((r - self.endOfDay) / 86400.0)
            days[d] = days.get(d, 0) + 1
        self._addMissing(days, -numdays+1, 0)
        graph = fig.add_subplot(111)
        ivls = self._unzip(days.items())
        if attr == 'created':
            colour = addedC
        else:
            colour = firstC
        self._varGraph(graph, numdays, colour, *ivls)
        graph.set_xlim(xmin=-numdays+1, xmax=1)
        graph.set_xlabel("Day (0 = today)")
        if attr == 'created':
            graph.set_ylabel("Cards Added")
        else:
            graph.set_ylabel("Cards First Answered")
        return fig

    def easeBars(self):
        fig = Figure(figsize=(3, 3), dpi=self.dpi)
        graph = fig.add_subplot(111)
        types = ("new", "young", "mature")
        enum = 5
        offset = 0
        arrsize = 16
        arr = [0] * arrsize
        colours = [easesNewC, easesYoungC, easesMatureC]
        bars = []
        eases = self.deck.db.all("""
select (case when rep = 1 then 0 when lastIvl <= 21 then 1 else 2 end)
as type, ease, count() from revlog group by type, ease""")
        if not eases:
            return None
        d = {}
        for (type, ease, count) in eases:
            type = types[type]
            if type not in d:
                d[type] = {}
            d[type][ease] = count
        for n, type in enumerate(types):
            total = float(sum(d[type].values()))
            for e in range(1, enum):
                try:
                    arr[e+offset] = (d[type][e] / total) * 100 + 1
                except ZeroDivisionError:
                    arr[e+offset] = 0
            bars.append(graph.bar(range(arrsize), arr, width=1.0,
                                  color=colours[n], align='center'))
            arr = [0] * arrsize
            offset += 5
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

    def _calcStats(self):
        if self._stats:
            return
        self._stats = {}
        self._stats['due'] = self._dueCards()
        self._stats['ivls'] = self._ivls()
        return

        days = {}
        daysYoung = {}
        daysMature =  {}
        months = {}
        next = {}
        lowestInDay = 0
        self.endOfDay = self.deck.sched.dayCutoff
        t = time.time()
        young = """
select ivl, due from cards
where queue between 0 and 1 and ivl <= 21"""
        mature = """
select ivl, due
from cards where queue = 1 and ivl > 21"""
        if self.selective:
            young += self.deck.sched._groupLimit("rev")
            mature += self.deck.sched._groupLimit("rev")
        young = self.deck.db.all(young)
        mature = self.deck.db.all(mature)
        for (src, dest) in [(young, daysYoung),
                            (mature, daysMature)]:
            for (ivl, due) in src:
                day=int(round(ivl))
                days[day] = days.get(day, 0) + 1
                indays = int(((due - self.endOfDay) / 86400.0) + 1)
                next[indays] = next.get(indays, 0) + 1 # type-agnostic stats
                dest[indays] = dest.get(indays, 0) + 1 # type-specific stats
                if indays < lowestInDay:
                    lowestInDay = indays
        self.stats['next'] = next
        self.stats['days'] = days
        self.stats['daysByType'] = {'young': daysYoung,
                                    'mature': daysMature}
        self.stats['months'] = months
        self.stats['lowestInDay'] = lowestInDay
        dayReps = self._getDayReps()
         # fixme: change 0 to correct offset
        todaydt = datetime.datetime.utcfromtimestamp(
            time.time() - 0).date()
        for dest, source in [("dayRepsNew", 0),
                             ("dayRepsYoung", 3),
                             ("dayRepsMature", 2)]:
            self.stats[dest] = dict(
                map(lambda dr: (-(todaydt - datetime.date(
                    *(int(x)for x in dr[1].split("-")))).days, dr[source]), dayReps))
            self.stats['dayTimes'] = dict(
                map(lambda dr: (-(todaydt - datetime.date(
                    *(int(x)for x in dr[1].split("-")))).days, dr[4]/60.0), dayReps))

    def _dueCards(self, days=7):
        return self.deck.db.all("""
select due-:today,
count(), -- all
sum(case when ivl >= 21 then 1 else 0 end) -- mature
from cards
where queue = 2 and due < (:today+:days) %s
group by due order by due""" % self._limit(),
                                today=self.deck.sched.today, days=days)

    def _graph(self, id, data, conf={}, width=600, height=200):
        return (
"""<div id="%(id)s" style="width:%(w)s; height:%(h)s;"></div>
<script>
$(function () {
    $.plot($("#%(id)s"), %(data)s, %(conf)s);
});
</script>""" % dict(
    id=id, w=width, h=height,
    data=simplejson.dumps(data),
    conf=simplejson.dumps(conf)))

    def dueGraph(self):
        d = self._dueCards()
        yng = []
        mtr = []
        for day in d:
            yng.append((day[0], day[1]))
            mtr.append((day[0], day[2]))
        txt = self._graph(id="due", data=[
            dict(data=yng, bars=dict(show=True, barWidth=0.8),
             color=dueYoungC, label=_("Young")),
            dict(data=mtr, bars=dict(show=True, barWidth=0.8),
             color=dueMatureC, label=_("Mature"))
            ])
        self.save(txt)

    def save(self, txt):
        open(os.path.expanduser("~/test.html"), "w").write("""
<html><head>
<script src="jquery.min.js"></script>
<script src="jquery.flot.min.js"></script>
</head><body>%s</body></html>"""%txt)

    def cumDueGraph(self):
        self._calcStats()
        d = self._stats['due']
        tot = 0
        days = []
        for day in d:
            tot += day[1]+day[2]
            days.append((day[0], tot))
        txt = self._graph(id="cum", data=[
            dict(data=days, lines=dict(show=True, fill=True),
             color=dueCumulC, label=_("Cards")),
            ])
        self.save(txt)

    def _limit(self):
        if self.selective:
            return self.deck.sched._groupLimit("rev")
        else:
            return ""

    def _ivls(self):
        return self.deck.db.all("""
select ivl / 7, count() from cards
where queue = 2 %s
group by ivl / 7
order by ivl / 7""" % self._limit())

    def ivlGraph(self):
        self._calcStats()
        ivls = self._stats['ivls']
        txt = self._graph(id="ivl", data=[
            dict(data=ivls, bars=dict(show=True, barWidth=0.8),
             color=intervC)
            ])
        self.save(txt)

    def _getDayReps(self):
        return self.deck.db.all("""
select
count() as combinedNewReps,
date(time/1000-:off, "unixepoch") as day,
sum(case when lastIvl > 21 then 1 else 0 end) as matureReps,
count() - sum(case when rep = 1 then 1 else 0 end) as combinedYoungReps,
sum(taken/1000) as reviewTime from revlog
group by day order by day
""", off=0)

    def _addMissing(self, dic, min, max):
        for i in range(min, max+1):
            if not i in dic:
                dic[i] = 0

    def _unzip(self, tuples, fillFix=True, limit=None, reverseLimit=False):
        tuples.sort(cmp=lambda x,y: cmp(x[0], y[0]))
        if limit:
            if reverseLimit:
                tuples = tuples[-limit:]
            else:
                tuples = tuples[:limit+1]
        new = zip(*tuples)
        return new
