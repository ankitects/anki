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
        self.type = 0

    def report(self, type=0):
        # 0=days, 1=weeks, 2=months
        # period-dependent graphs
        self.type = type
        txt = self.dueGraph()
        txt += self.repsGraph()
        txt += self.ivlGraph()
        # other graphs
        txt += self.easeGraph()
        return "<script>%s\n</script><center>%s</center>" % (anki.js.all, txt)

    # Due and cumulative due
    ######################################################################

    def dueGraph(self):
        if self.type == 0:
            start = 0; end = 30; chunk = 1;
        elif self.type == 1:
            start = 0; end = 52; chunk = 7
        elif self.type == 2:
            start = 0; end = None; chunk = 30
        d = self._due(start, end, chunk)
        yng = []
        mtr = []
        tot = 0
        totd = []
        for day in d:
            yng.append((day[0], day[1]))
            mtr.append((day[0], day[2]))
            tot += day[1]+day[2]
            totd.append((day[0], tot))
        data = [
            dict(data=mtr, color=colMature, label=_("Mature")),
            dict(data=yng, color=colYoung, label=_("Young")),
        ]
        if len(totd) > 1:
            data.append(
                dict(data=totd, color=colCum, label=_("Cumulative"), yaxis=2,
                     bars={'show': False}, lines=dict(show=True), stack=False))
        txt = self._graph(id="due", title=_("Forecast"), data=data,
            info=_("The number of reviews due in the future."), conf=dict(
                xaxis=dict(tickDecimals=0),
                yaxes=[dict(), dict(tickDecimals=0, position="right")]))
        return txt

    def _due(self, start=None, end=None, chunk=1):
        lim = ""
        if start is not None:
            lim += " and due-:today >= %d" % start
        if end is not None:
            lim += " and day < %d" % end
        return self.deck.db.all("""
select (due-:today)/:chunk as day,
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

    def repsGraph(self):
        if self.type == 0:
            days = 30; chunk = 1
        elif self.type == 1:
            days = 52; chunk = 7
        else:
            days = None; chunk = 30
        return self._repsGraph(self._done(days, chunk),
                               days,
                               _("Review Count"),
                               _("Review Time"))

    def _repsGraph(self, data, days, reptitle, timetitle):
        if not data:
            return ""
        d = data
        conf = dict(
            xaxis=dict(tickDecimals=0),
            yaxes=[dict(), dict(position="right")])
        if days is not None:
            conf['xaxis']['min'] = -days
        def plot(id, title, data, ylabel, info):
            return self._graph(id, title=title,
                               data=data, conf=conf, ylabel=ylabel, info=info)
        # reps
        (repdata, repsum) = self._splitRepData(d, (
            (3, colMature, _("Mature")),
            (2, colYoung, _("Young")),
            (4, colRelearn, _("Relearn")),
            (1, colLearn, _("Learn")),
            (5, colCram, _("Cram"))))
        txt = plot("reps", reptitle, repdata, ylabel=_("Answers"),
               info=_("""\
The number of cards you have answered. Answering the same card twice \
counts as two answers."""))
        # time
        (timdata, timsum) = self._splitRepData(d, (
            (8, colMature, _("Mature")),
            (7, colYoung, _("Young")),
            (9, colRelearn, _("Relearn")),
            (6, colLearn, _("Learn")),
            (10, colCram, _("Cram"))))
        if self.type == 0:
            t = _("Minutes")
        else:
            t = _("Hours")
        txt += plot("time", timetitle, timdata, ylabel=t, info=_("""\
Time spent answering cards."""))
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
        if len(totd) > 1:
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
        if self.type == 0:
            tf = 60.0 # minutes
        else:
            tf = 3600.0 # hours
        return self.deck.db.all("""
select
(cast((time/1000 - :cut) / 86400.0 as int)+1)/:chunk as day,
sum(case when type = 0 then 1 else 0 end), -- lrn count
sum(case when type = 1 and lastIvl < 21 then 1 else 0 end), -- yng count
sum(case when type = 1 and lastIvl >= 21 then 1 else 0 end), -- mtr count
sum(case when type = 2 then 1 else 0 end), -- lapse count
sum(case when type = 3 then 1 else 0 end), -- cram count
sum(case when type = 0 then taken/1000 else 0 end)/:tf, -- lrn time
-- yng + mtr time
sum(case when type = 1 and lastIvl < 21 then taken/1000 else 0 end)/:tf,
sum(case when type = 1 and lastIvl >= 21 then taken/1000 else 0 end)/:tf,
sum(case when type = 2 then taken/1000 else 0 end)/:tf, -- lapse time
sum(case when type = 3 then taken/1000 else 0 end)/:tf -- cram time
from revlog %s
group by day order by day""" % lim,
                            cut=self.deck.sched.dayCutoff,
                            tf=tf,
                            chunk=chunk)

    # Intervals
    ######################################################################

    def ivlGraph(self):
        (ivls, all) = self._ivls()
        tot = 0
        totd = []
        if not ivls or not all:
            return ""
        for (grp, cnt) in ivls:
            tot += cnt
            totd.append((grp, tot/float(all)*100))
        txt = self._graph(id="ivl", title=_("Intervals"), data=[
            dict(data=ivls, color=colIvl, label=_("All Types")),
            dict(data=totd, color=colCum, label=_("% Total"), yaxis=2,
             bars={'show': False}, lines=dict(show=True), stack=False)
            ], conf=dict(
                yaxes=[dict(), dict(position="right", max=105)]),
            info=_("""\
Intervals in the review queue. New cards and cards in (re)learning \
are not included."""))
        return txt

    def _ivls(self):
        if self.type == 0:
            chunk = 1; lim = " and grp <= 30"
        elif self.type == 1:
            chunk = 7; lim = " and grp <= 52"
        else:
            chunk = 30; lim = ""
        return (self.deck.db.all("""
select ivl / :chunk as grp, count() from cards
where queue = 2 %s %s
group by grp
order by grp""" % (self._limit(), lim), chunk=chunk),
                self.deck.db.scalar("""
select count() from cards where queue = 2 %s""" % self._limit()))

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
        txt = self._graph(id="ease", title=_("Answer Buttons"), data=[
            dict(data=d['lrn'], color=colLearn, label=_("Learning")),
            dict(data=d['yng'], color=colYoung, label=_("Young")),
            dict(data=d['mtr'], color=colMature, label=_("Mature")),
            ], type="barsLine", info=_("""\
The number of times you have pressed each answer button."""), conf=dict(
            xaxis=dict(ticks=ticks, min=0, max=15)),
            ylabel=_("Answers"))
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

    def _graph(self, id, title, data, conf={}, width=600, height=200,
               type="bars", ylabel=_("Cards"), timeTicks=True, info=""):
        # display settings
        conf['legend'] = {'container': "#%sLegend" % id}
        conf['series'] = dict(stack=True)
        if not 'yaxis' in conf:
            conf['yaxis'] = {}
        conf['yaxis']['labelWidth'] = 40
        if 'xaxis' not in conf:
            conf['xaxis'] = {}
        if timeTicks:
            conf['timeTicks'] = (_("d"), _("w"), _("m"))[self.type]
        if type == "bars":
            conf['series']['bars'] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=0)
        elif type == "barsLine":
            conf['series']['bars'] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=3)
        elif type == "fill":
            conf['series']['lines'] = dict(show=True, fill=True)
        return (
"""
<h1>%(title)s</h1>
<table cellpadding=0 cellspacing=10>
<tr>
<td><div style="width: 10px; -webkit-transform: rotate(-90deg);
-moz-transform: rotate(-90deg);">%(ylab)s</div></td>

<td><div id="%(id)s" style="width:%(w)s; height:%(h)s;"></div></td>

<td width=100 valign=top><br>
<div id=%(id)sLegend></div>
<br><small>%(info)s</small>
</td></tr></table>
<script>
$(function () {
    var conf = %(conf)s;
    if (conf.timeTicks) {
        conf.xaxis.tickFormatter = function (val, axis) {
            return val.toFixed(0)+conf.timeTicks;
        }
    }
    $.plot($("#%(id)s"), %(data)s, conf);
});
</script>""" % dict(
    id=id, title=title, w=width, h=height, tw=width+100, ylab=ylabel,
    info=info,
    data=simplejson.dumps(data), conf=simplejson.dumps(conf)))

    def _limit(self):
        if self.selective:
            return self.deck.sched._groupLimit()
        else:
            return ""
