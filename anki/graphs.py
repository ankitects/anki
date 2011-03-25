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
lrnTimeC = "#0fcaff"
revTimeC = "#ffcaff"
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

    # Due and cumulative due
    ######################################################################

    def dueGraph(self):
        self._calcStats()
        d = self._stats['due']
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
        return txt

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
        return txt

    def _due(self, days=7):
        return self.deck.db.all("""
select due-:today,
count(), -- all
sum(case when ivl >= 21 then 1 else 0 end) -- mature
from cards
where queue = 2 and due < (:today+:days) %s
group by due order by due""" % self._limit(),
                                today=self.deck.sched.today, days=days)

    # Reps and time spent
    ######################################################################

    def repsGraph(self):
        self._calcStats()
        lrn = []
        yng = []
        mtr = []
        for row in self._stats['done']:
            lrn.append((row[0], row[1]))
            yng.append((row[0], row[2]))
            mtr.append((row[0], row[3]))
        txt = self._graph(id="due", data=[
            dict(data=lrn, bars=dict(show=True, barWidth=0.8, align="center"),
             color=reviewNewC, label=_("Learning")),
            dict(data=yng, bars=dict(show=True, barWidth=0.8, align="center"),
             color=reviewYoungC, label=_("Young")),
            dict(data=mtr, bars=dict(show=True, barWidth=0.8, align="center"),
             color=reviewMatureC, label=_("Mature")),
            ])
        return txt

    def timeGraph(self):
        self._calcStats()
        lrn = []
        rev = []
        for row in self._stats['done']:
            lrn.append((row[0], row[4]))
            rev.append((row[0], row[5]))
        txt = self._graph(id="due", data=[
            dict(data=lrn, bars=dict(show=True, barWidth=0.8, align="center"),
             color=lrnTimeC, label=_("Learning")),
            dict(data=rev, bars=dict(show=True, barWidth=0.8, align="center"),
             color=revTimeC, label=_("Reviews")),
            ])
        self.save(txt)
        return txt

    def _done(self):
        # without selective for now
        return self.deck.db.all("""
select
cast((time/1000 - :cut) / 86400.0 as int)+1 as day,
sum(case when type = 0 then 1 else 0 end), -- lrn count
sum(case when type = 1 and lastIvl < 21 then 1 else 0 end), -- yng count
sum(case when type = 1 and lastIvl >= 21 then 1 else 0 end), -- mtr count
sum(case when type = 0 then taken/1000 else 0 end)/3600.0, -- lrn time
sum(case when type = 1 then taken/1000 else 0 end)/3600.0 -- rev time
from revlog group by day order by day""", cut=self.deck.sched.dayCutoff)

    # Intervals
    ######################################################################

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
        return txt

    # Eases
    ######################################################################

    def _eases(self):
        # ignores selective, at least for now
        return self.deck.db.all("""
select (case
when type = 0 then 0
when lastIvl <= 21 then 1
else 2 end) as thetype,
ease, count() from revlog
group by thetype, ease
order by thetype, ease""")

    def easeGraph(self):
        self._calcStats()
        # 3 + 4 + 4 + spaces on sides and middle = 15
        # yng starts at 1+3+1 = 5
        # mtr starts at 5+4+1 = 10
        d = {'lrn':[], 'yng':[], 'mtr':[]}
        types = ("lrn", "yng", "mtr")
        for (type, ease, cnt) in self._stats['eases']:
            if type == 1:
                ease += 5
            elif type == 2:
                ease += 10
            n = types[type]
            d[n].append((ease, cnt))
        ticks = [[1,1],[2,2],[3,3],
                 [6,1],[7,2],[8,3],[9,4],
                 [11, 1],[12,2],[13,3],[14,4]]
        txt = self._graph(id="ease", data=[
            dict(data=d['lrn'], bars=dict(show=True, barWidth=0.8, align="center"),
             color=easesNewC, label=_("Learning")),
            dict(data=d['yng'], bars=dict(show=True, barWidth=0.8, align="center"),
             color=easesYoungC, label=_("Young")),
            dict(data=d['mtr'], bars=dict(show=True, barWidth=0.8, align="center"),
             color=easesMatureC, label=_("Mature")),
            ], conf=dict(
            xaxis=dict(ticks=ticks, min=0, max=15)))
        return txt

    # Tools
    ######################################################################

    def _calcStats(self):
        if self._stats:
            return
        self._stats = {}
        self._stats['due'] = self._due()
        self._stats['ivls'] = self._ivls()
        self._stats['done'] = self._done()
        self._stats['eases'] = self._eases()

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

    def _limit(self):
        if self.selective:
            return self.deck.sched._groupLimit("rev")
        else:
            return ""

    def save(self, txt):
        open(os.path.expanduser("~/test.html"), "w").write("""
<html><head>
<script src="jquery.min.js"></script>
<script src="jquery.flot.min.js"></script>
</head><body>%s</body></html>"""%txt)

    def _addMissing(self, dic, min, max):
        for i in range(min, max+1):
            if not i in dic:
                dic[i] = 0
