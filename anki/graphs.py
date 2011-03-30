# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, time, datetime, simplejson
from anki.utils import fmtTimeSpan, ids2str
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
colUnseen = "#000"
colSusp = "#ff0"

class Graphs(object):

    def __init__(self, deck, selective=True):
        self.deck = deck
        self._stats = None
        self.selective = selective
        self.type = 0
        self.width = 600
        self.height = 200

    def report(self, type=0):
        # 0=days, 1=weeks, 2=months
        # period-dependent graphs
        self.type = type
        txt = self.css
        txt += self.dueGraph()
        txt += self.repsGraph()
        txt += self.ivlGraph()
        # other graphs
        txt += self.easeGraph()
        txt += self.cardGraph()
        return "<script>%s\n</script><center>%s</center>" % (anki.js.all, txt)

    css = """
<style>
h1 { margin-bottom: 0; margin-top: 1em; }
body { font-size: 14px; }
table * { font-size: 14px; }
.pielabel { text-align:center; padding:2px; color:black; }
</style>
"""

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
        txt = self._title(
            _("Forecast"),
            _("The number of reviews due in the future."))
        txt += self._graph(id="due", data=data, conf=dict(
                xaxis=dict(tickDecimals=0),
                yaxes=[dict(), dict(tickDecimals=0, position="right")]))
        txt += self._dueInfo(tot, len(totd))
        return txt

    def _dueInfo(self, tot, num):
        if self.type == 0:
            days = num
        elif self.type == 1:
            days = num*7
        else:
            days = num*30
        vals = []
        vals.append(_("%d/day") % (tot/days))
        if self.type > 0:
            vals.append(_("%d/week") % (tot/(days/7)))
        if self.type > 1:
            vals.append(_("%d/month") % (tot/(days/30)))
        txt = _("Average reviews: <b>%s</b>") % ", ".join(vals)
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
        def plot(id, data, ylabel):
            return self._graph(
                id, data=data, conf=conf, ylabel=ylabel)
        # reps
        (repdata, repsum) = self._splitRepData(d, (
            (3, colMature, _("Mature")),
            (2, colYoung, _("Young")),
            (4, colRelearn, _("Relearn")),
            (1, colLearn, _("Learn")),
            (5, colCram, _("Cram"))))
        txt = self._title(
            reptitle, _("The number of questions you have answered."))
        txt += plot("reps", repdata, ylabel=_("Answers"))
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
        txt += self._title(timetitle, _("The time taken to answer the questions."))
        txt += plot("time", timdata, ylabel=t)
        return txt

    def _ansInfo(self, data):
        return ""

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
        lims = []
        if num is not None:
            lims.append("time > %d" % (
                (self.deck.sched.dayCutoff-(num*chunk*86400))*1000))
        lim = self._revlogLimit()
        if lim:
            lims.append(lim)
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
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
        (ivls, all, avg, max) = self._ivls()
        tot = 0
        totd = []
        if not ivls or not all:
            return ""
        for (grp, cnt) in ivls:
            tot += cnt
            totd.append((grp, tot/float(all)*100))
        txt = self._title(_("Intervals"),
                          _("Delays until reviews are shown again."))
        txt += self._graph(id="ivl", data=[
            dict(data=ivls, color=colIvl, label=_("All Types")),
            dict(data=totd, color=colCum, label=_("% Total"), yaxis=2,
             bars={'show': False}, lines=dict(show=True), stack=False)
            ], conf=dict(
                yaxes=[dict(), dict(position="right", max=105)]))
        txt += _("Average interval: <b>%s</b>") % fmtTimeSpan(avg*86400)
        txt += "<br>" + _("Longest interval: <b>%s</b>") % fmtTimeSpan(max*86400)
        return txt

    def _ivls(self):
        if self.type == 0:
            chunk = 1; lim = " and grp <= 30"
        elif self.type == 1:
            chunk = 7; lim = " and grp <= 52"
        else:
            chunk = 30; lim = ""
        data = [self.deck.db.all("""
select ivl / :chunk as grp, count() from cards
where queue = 2 %s %s
group by grp
order by grp""" % (self._limit(), lim), chunk=chunk)]
        return data + list(self.deck.db.first("""
select count(), avg(ivl), max(ivl) from cards where queue = 2 %s""" %
                                         self._limit()))

    # Eases
    ######################################################################

    def easeGraph(self):
        # 3 + 4 + 4 + spaces on sides and middle = 15
        # yng starts at 1+3+1 = 5
        # mtr starts at 5+4+1 = 10
        d = {'lrn':[], 'yng':[], 'mtr':[]}
        types = ("lrn", "yng", "mtr")
        eases = self._eases()
        for (type, ease, cnt) in eases:
            if type == 1:
                ease += 5
            elif type == 2:
                ease += 10
            n = types[type]
            d[n].append((ease, cnt))
        ticks = [[1,1],[2,2],[3,3],
                 [6,1],[7,2],[8,3],[9,4],
                 [11, 1],[12,2],[13,3],[14,4]]
        txt = self._title(_("Answer Buttons"),
                          _("The number of times you have pressed each button."))
        txt += self._graph(id="ease", data=[
            dict(data=d['lrn'], color=colLearn, label=_("Learning")),
            dict(data=d['yng'], color=colYoung, label=_("Young")),
            dict(data=d['mtr'], color=colMature, label=_("Mature")),
            ], type="barsLine", conf=dict(
                xaxis=dict(ticks=ticks, min=0, max=15)),
            ylabel=_("Answers"))
        txt += self._easeInfo(eases)
        return txt

    def _easeInfo(self, eases):
        types = {0: [0, 0], 1: [0, 0], 2: [0,0]}
        for (type, ease, cnt) in eases:
            if ease == 1:
                types[type][0] += cnt
            else:
                types[type][1] += cnt
        i = []
        for type in range(3):
            (bad, good) = types[type]
            tot = bad + good
            try:
                pct = good / float(tot) * 100
            except:
                pct = 0
            i.append(_(
                "Correct: <b>%(pct)0.2f%%</b><br>(%(good)d of %(tot)d)") % dict(
                pct=pct, good=good, tot=tot))
        return ("""
<center><table width=%dpx><tr><td width=50></td><td align=center>""" % self.width +
                "</td><td align=center>".join(i) +
                "</td></tr></table></center>")

    def _eases(self):
        lim = self._revlogLimit()
        if lim:
            lim = "where " + lim
        return self.deck.db.all("""
select (case
when type in (0,2) then 0
when lastIvl < 21 then 1
else 2 end) as thetype,
ease, count() from revlog %s
group by thetype, ease
order by thetype, ease""" % lim)

    # Cards
    ######################################################################

    def cardGraph(self):
        # graph data
        div = self._cards()
        d = []
        for c, (t, col) in enumerate((
            (_("Mature"), colMature),
            (_("Young+Learn"), colYoung),
            (_("Unseen"), colUnseen),
            (_("Suspended"), colSusp))):
            d.append(dict(data=div[c], label=t, color=col))
        # text data
        i = []
        self._line(i, _("Total Cards"), self.deck.cardCount())
        self._line(i, _("Total Facts"), self.deck.factCount())
        (low, avg, high) = self._factors()
        self._line(i, _("Lowest ease factor"), "%d%%" % low)
        self._line(i, _("Average ease factor"), "%d%%" % avg)
        self._line(i, _("Highest ease factor"), "%d%%" % high)
        min = self.deck.db.scalar(
            "select min(crt) from cards where 1 " + self._limit())
        if min:
            self._line(i, _("First card created"), _("%s ago") % fmtTimeSpan(
            time.time() - min))
        info = "<table width=100%>" + "".join(i) + "</table><p>"
        info += _('''\
A card's <i>ease factor</i> is the size of the next interval \
when you answer "good" on a review.''')
        txt = self._title(_("Cards Types"),
                          _("The division of cards in your deck."))
        txt += "<table width=%d><tr><td>%s</td><td>%s</td></table>" % (
            self.width,
            self._graph(id="cards", data=d, type="pie"),
            info)
        return txt

    def _line(self, i, a, b):
        i.append(("<tr><td>%s:</td><td>%s</td></tr>") % (a,b))

    def _factors(self):
        return self.deck.db.first("""
select
min(factor) / 10.0,
avg(factor) / 10.0,
max(factor) / 10.0
from cards where queue = 2 %s""" % self._limit())

    def _cards(self):
        return self.deck.db.first("""
select
sum(case when queue=2 and ivl >= 21 then 1 else 0 end), -- mtr
sum(case when queue=1 or (queue=2 and ivl < 21) then 1 else 0 end), -- yng/lrn
sum(case when queue=0 then 1 else 0 end), -- new
sum(case when queue=-1 then 1 else 0 end) -- susp
%s
from cards""" % self._limit())

    # Tools
    ######################################################################

    def _graph(self, id, data, conf={},
               type="bars", ylabel=_("Cards"), timeTicks=True):
        # display settings
        if type == "pie":
            conf['legend'] = {'container': "#%sLegend" % id, 'noColumns':2}
        else:
            conf['legend'] = {'container': "#%sLegend" % id, 'noColumns':10}
        conf['series'] = dict(stack=True)
        if not 'yaxis' in conf:
            conf['yaxis'] = {}
        conf['yaxis']['labelWidth'] = 40
        if 'xaxis' not in conf:
            conf['xaxis'] = {}
        if timeTicks:
            conf['timeTicks'] = (_("d"), _("w"), _("m"))[self.type]
        # types
        width = self.width
        height = self.height
        if type == "bars":
            conf['series']['bars'] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=0)
        elif type == "barsLine":
            conf['series']['bars'] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=3)
        elif type == "fill":
            conf['series']['lines'] = dict(show=True, fill=True)
        elif type == "pie":
            width /= 2.3
            height *= 1.5
            ylabel = ""
            conf['series']['pie'] = dict(
                show=True,
                radius=0.8,
                stroke=dict(color="#000", width=3),
                label=dict(
                    show=True,
                    radius=1,
                    threshold=0.01))

            #conf['legend'] = dict(show=False)
        return (
"""
<table cellpadding=0 cellspacing=10>
<tr>
<td><div style="width: 10px; -webkit-transform: rotate(-90deg);
-moz-transform: rotate(-90deg);">%(ylab)s</div></td>
<td>
<center><div id=%(id)sLegend></div></center>
<div id="%(id)s" style="width:%(w)s; height:%(h)s;"></div>
</td></tr></table>
<script>
$(function () {
    var conf = %(conf)s;
    if (conf.timeTicks) {
        conf.xaxis.tickFormatter = function (val, axis) {
            return val.toFixed(0)+conf.timeTicks;
        }
    }
    if (conf.series.pie) {
        conf.series.pie.label.formatter = function(label, series){
            return '<div class=pielabel>'+'<small>'+label+'</small><br>'+Math.round(series.percent)+'%%</div>';
        };
    }
    $.plot($("#%(id)s"), %(data)s, conf);
});
</script>""" % dict(
    id=id, w=width, h=height,
    ylab=ylabel,
    data=simplejson.dumps(data), conf=simplejson.dumps(conf)))

    def _limit(self):
        if self.selective:
            return self.deck.sched._groupLimit()
        else:
            return ""

    def _revlogLimit(self):
        lim = self.deck.qconf['groups']
        if self.selective and lim:
            return ("cid in (select id from cards where gid in %s)" %
                    ids2str(lim))
        else:
            return ""

    def _title(self, title, subtitle=""):
        return '<h1>%s</h1>%s' % (title, subtitle)
