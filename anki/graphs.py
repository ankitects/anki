# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, time, datetime, simplejson
from anki.lang import _
import anki.js

colYoung = "#7c7"
colMature = "#070"
colCum = "rgba(0,0,0,0.9)"
colLearn = "#00F"
colRelearn = "#c00"
colCram = "#ff0"
colIvl = "#077"
colTime = "#770"
easesNewC = "#80b3ff"
easesYoungC = "#5555ff"
easesMatureC = "#0f5aff"

class Graphs(object):

    def __init__(self, deck, selective=True):
        self.deck = deck
        self._stats = None
        self.selective = selective

    def report(self, type=0):
        # 0=days, 1=weeks, 2=months
        # period-dependent graphs
        txt = self.dueGraph(type)
        txt += self.repsGraph(type)
        # other graphs
        txt += self.ivlGraph()
        txt += self.easeGraph()
        return "<script>%s</script><center>%s</center>" % (anki.js.all, txt)

    # Due and cumulative due
    ######################################################################

    def dueGraph(self, type):
        if type == 0:
            start = 0; end = 30; chunk = 1
        elif type == 1:
            start = 0; end = 52; chunk = 7
        elif type == 2:
            start = 0; end = None; chunk = 30
        return self._dueGraph(self._due(start, end, chunk), _("Forecast"))

    def _dueGraph(self, data, title):
        d = data
        yng = []
        mtr = []
        tot = 0
        totd = []
        for day in d:
            yng.append((day[0], day[1]))
            mtr.append((day[0], day[2]))
            tot += day[1]+day[2]
            totd.append((day[0], tot))
        txt = self._graph(id=hash(title), title=title, data=[
            dict(data=mtr, color=colMature, label=_("Mature")),
            dict(data=yng, color=colYoung, label=_("Young")),
            dict(data=totd, color=colCum, label=_("Cumulative"), yaxis=2,
             bars={'show': False}, lines=dict(show=True), stack=False)
            ], conf=dict(
                xaxis=dict(tickDecimals=0),
                yaxes=[dict(), dict(position="right")]))
        return txt

    def _due(self, start=None, end=None, chunk=1):
        lim = ""
        if start is not None:
            lim += " and due-:today >= %d" % start
        if end is not None:
            lim += " and day < %d" % end
        return self.deck.db.all("""
select (due-:today+1)/:chunk as day,
sum(case when ivl < 21 then 1 else 0 end), -- yng
sum(case when ivl >= 21 then 1 else 0 end) -- mtr
from cards
where queue = 2 %s
%s
group by day order by day""" % (self._limit(), lim),
                            today=self.deck.sched.today,
                            chunk=chunk)

    # Reps and time spent
    ######################################################################

    def repsGraph(self, type):
        if type == 0:
            days = 30; chunk = 1
        elif type == 1:
            days = 52; chunk = 7
        else:
            days = None; chunk = 30
        return self._repsGraph(self._done(days, chunk),
                               days,
                               _("Review Count"),
                               _("Review Time"))

    def _repsGraph(self, data, days, reptitle, timetitle):
        d = data
        conf = dict(
            xaxis=dict(tickDecimals=0),
            yaxes=[dict(), dict(position="right")])
        if days is not None:
            conf['xaxis']['min'] = -days
        def plot(title, data):
            return self._graph("g%d"%hash(title), title=title,
                               data=data, conf=conf)
        # reps
        (repdata, repsum) = self._splitRepData(d, (
            (3, colMature, _("Mature")),
            (2, colYoung, _("Young")),
            (4, colRelearn, _("Relearn")),
            (1, colLearn, _("Learn")),
            (5, colCram, _("Cram"))))
        txt = plot(reptitle, repdata)
        # time
        (timdata, timsum) = self._splitRepData(d, (
            (8, colMature, _("Mature")),
            (7, colYoung, _("Young")),
            (9, colRelearn, _("Relearn")),
            (6, colLearn, _("Learn")),
            (10, colCram, _("Cram"))))
        txt += plot(timetitle, timdata)
        return txt

    def _splitRepData(self, data, spec):
        sep = {}
        tot = 0
        totd = []
        sum = []
        for row in data:
            rowtot = 0
            for (n, col, lab) in spec:
                if n not in sep:
                    sep[n] = []
                sep[n].append((row[0], row[n]))
                tot += row[n]
                rowtot += row[n]
            totd.append((row[0], tot))
            sum.append((row[0], rowtot))
        ret = []
        for (n, col, lab) in spec:
            ret.append(dict(data=sep[n], color=col, label=lab))
        ret.append(dict(
            data=totd, color=colCum, label=_("Cumulative"), yaxis=2,
            bars={'show': False}, lines=dict(show=True), stack=False))
        return (ret, sum)

    def _done(self, num=7, chunk=1):
        # without selective for now
        lim = ""
        if num is not None:
            lim += "where time > %d" % (
                (self.deck.sched.dayCutoff-(num*chunk*86400))*1000)
        return self.deck.db.all("""
select
(cast((time/1000 - :cut) / 86400.0 as int)+1)/:chunk as day,
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
from revlog %s
group by day order by day""" % lim,
                            cut=self.deck.sched.dayCutoff,
                            chunk=chunk)

    # Intervals
    ######################################################################

    def ivlGraph(self):
        ivls = self._ivls()
        txt = self._graph(id="ivl", title=_("Intervals"), data=[
            dict(data=ivls, color=colIvl)
            ])
        return txt

    def _ivls(self):
        return self.deck.db.all("""
select ivl / 7 as grp, count() from cards
where queue = 2 %s
group by grp
order by grp""" % self._limit())


    # Eases
    ######################################################################

    def easeGraph(self):
        # 3 + 4 + 4 + spaces on sides and middle = 15
        # yng starts at 1+3+1 = 5
        # mtr starts at 5+4+1 = 10
        d = {'lrn':[], 'yng':[], 'mtr':[]}
        types = ("lrn", "yng", "mtr")
        for (type, ease, cnt) in self._eases():
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

    # Tools
    ######################################################################

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
<tr>
<td><div style="-webkit-transform: rotate(-90deg);-moz-transform: rotate(-90deg);">Cards</div></td>
<td><div id="%(id)s" style="width:%(w)s; height:%(h)s;"></div></td>
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
