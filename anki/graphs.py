# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, time, datetime, simplejson
from anki.lang import _
import anki.js

colYoung = "#7c7"
colMature = "#070"
colCum = "rgba(0,0,0,0.9)"
colLearn = "#007"
colRelearn = "#700"
colCram = "#ff0"
colIvl = "#077"
easesNewC = "#80b3ff"
easesYoungC = "#5555ff"
easesMatureC = "#0f5aff"

class Graphs(object):

    def __init__(self, deck, selective=True):
        self.deck = deck
        self._stats = None
        self.selective = selective

    def report(self):
        txt = (self.dueGraph() +
               self.repsGraph() +
               self.timeGraph() +
               self.ivlGraph() +
               self.easeGraph())
        return "<script>%s</script><center>%s</center>" % (anki.js.all, txt)

    # Due and cumulative due
    ######################################################################

    def dueGraph(self):
        self._calcStats()
        d = self._stats['due']
        yng = []
        mtr = []
        tot = 0
        totd = []
        for day in d:
            yng.append((day[0], day[1]))
            mtr.append((day[0], day[2]))
            tot += day[1]+day[2]
            totd.append((day[0], tot))
        txt = self._graph(id="due", title=_("Due Forecast"), data=[
            dict(data=mtr, color=colMature, label=_("Mature")),
            dict(data=yng, color=colYoung, label=_("Young")),
            dict(data=totd, color=colCum, label=_("Cumulative"), yaxis=2,
             bars={'show': False}, lines=dict(show=True))
            ], conf=dict(
                yaxes=[{}, {'position': 'right'}]))
        return txt

    def _due(self, days=7):
        return self.deck.db.all("""
select due-:today as day,
sum(case when ivl < 21 then 1 else 0 end), -- yng
sum(case when ivl >= 21 then 1 else 0 end) -- mtr
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
        lapse = []
        cram = []
        for row in self._stats['done']:
            lrn.append((row[0], row[1]))
            yng.append((row[0], row[2]))
            mtr.append((row[0], row[3]))
            lapse.append((row[0], row[4]))
            cram.append((row[0], row[5]))
        txt = self._graph(id="reps", title=_("Repetitions"), data=[
            dict(data=mtr, color=colMature, label=_("Mature")),
            dict(data=yng, color=colYoung, label=_("Young")),
            dict(data=lapse, color=colRelearn, label=_("Relearning")),
            dict(data=lrn, color=colLearn, label=_("Learning")),
            dict(data=cram, color=colCram, label=_("Cramming")),
            ])
        return txt

    def timeGraph(self):
        self._calcStats()
        lrn = []
        yng = []
        mtr = []
        lapse = []
        cram = []
        for row in self._stats['done']:
            lrn.append((row[0], row[6]))
            yng.append((row[0], row[7]))
            mtr.append((row[0], row[8]))
            lapse.append((row[0], row[9]))
            cram.append((row[0], row[10]))
        txt = self._graph(id="time", title=_("Time Spent"), data=[
            dict(data=mtr, color=colMature, label=_("Mature")),
            dict(data=yng, color=colYoung, label=_("Young")),
            dict(data=lapse, color=colRelearn, label=_("Relearning")),
            dict(data=lrn, color=colLearn, label=_("Learning")),
            dict(data=cram, color=colCram, label=_("Cramming")),
            ])
        return txt

    def _done(self):
        # without selective for now
        return self.deck.db.all("""
select
cast((time/1000 - :cut) / 86400.0 as int)+1 as day,
sum(case when type = 0 then 1 else 0 end), -- lrn count
sum(case when type = 1 and lastIvl < 21 then 1 else 0 end), -- yng count
sum(case when type = 1 and lastIvl >= 21 then 1 else 0 end), -- mtr count
sum(case when type = 2 then 1 else 0 end), -- lapse count
sum(case when type = 3 then 1 else 0 end), -- cram count
sum(case when type = 0 then taken/1000 else 0 end)/3600.0, -- lrn time
-- yng + mtr time
sum(case when type = 1 and lastIvl < 21 then taken/1000 else 0 end)/3600.0,
sum(case when type = 1 and lastIvl >= 21 then taken/1000 else 0 end)/3600.0,
sum(case when type = 2 then taken/1000 else 0 end)/3600.0, -- lapse time
sum(case when type = 3 then taken/1000 else 0 end)/3600.0 -- cram time
from revlog group by day order by day""", cut=self.deck.sched.dayCutoff)

    # Intervals
    ######################################################################

    def _ivls(self):
        return self.deck.db.all("""
select ivl / 7 as grp, count() from cards
where queue = 2 %s
group by grp
order by grp""" % self._limit())

    def ivlGraph(self):
        self._calcStats()
        ivls = self._stats['ivls']
        txt = self._graph(id="ivl", title=_("Intervals"), data=[
            dict(data=ivls, color=colIvl)
            ])
        return txt

    # Eases
    ######################################################################

    def _eases(self):
        # ignores selective, at least for now
        return self.deck.db.all("""
select (case
when type in (0,2) then 0
when lastIvl < 21 then 1
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
        txt = self._graph(id="ease", title=_("Eases"), data=[
            dict(data=d['lrn'], color=easesNewC, label=_("Learning")),
            dict(data=d['yng'], color=easesYoungC, label=_("Young")),
            dict(data=d['mtr'], color=easesMatureC, label=_("Mature")),
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

    def _graph(self, id, title, data, conf={}, width=600, height=200, type="bars"):
        # display settings
        conf['legend'] = {'container': "#%sLegend" % id}
        conf['series'] = dict(stack=True)
        if type == "bars":
            conf['series']['bars'] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=0)
        elif type == "fill":
            conf['series']['lines'] = dict(show=True, fill=True)
        return (
"""
<h1>%(title)s</h1>
<table width=%(tw)s>
<tr><td><div id="%(id)s" style="width:%(w)s; height:%(h)s;"></div></td>
<td width=100 valign=top><br><div id=%(id)sLegend></div></td></tr></table>
<script>
$(function () {
    $.plot($("#%(id)s"), %(data)s, %(conf)s);
});
</script>""" % dict(
    id=id, title=title, w=width, h=height, tw=width+100,
    data=simplejson.dumps(data),
    conf=simplejson.dumps(conf)))

    def _limit(self):
        if self.selective:
            return self.deck.sched._groupLimit("rev")
        else:
            return ""

    def _addMissing(self, dic, min, max):
        for i in range(min, max+1):
            if not i in dic:
                dic[i] = 0
