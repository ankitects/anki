# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, sys, os, datetime, simplejson
import anki.js
from anki.utils import fmtTimeSpan, fmtFloat, ids2str
from anki.consts import *
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
        fmt = lambda x, **kwargs: fmtTimeSpan(x, short=True, **kwargs)
        self.txt = "<table width=100%%>"
        self.addLine(_("Added"), self.date(c.id/1000))
        first = self.deck.db.scalar(
            "select min(id) from revlog where cid = ?", c.id)
        last = self.deck.db.scalar(
            "select max(id) from revlog where cid = ?", c.id)
        if first:
            self.addLine(_("First Review"), self.date(first/1000))
            self.addLine(_("Latest Review"), self.date(last/1000))
        if c.queue in (1,2):
            if c.queue == 2:
                next = time.time()+((self.deck.sched.today - c.due)*86400)
            else:
                next = c.due
            next = self.date(next)
            self.addLine(_("Due"), next)
            self.addLine(_("Interval"), fmt(c.ivl * 86400))
            self.addLine(_("Ease"), "%d%%" % (c.factor/10.0))
            (cnt, total) = self.deck.db.first(
                "select count(), sum(time)/1000 from revlog where cid = :id",
                id=c.id)
            if cnt:
                self.addLine(_("Average Time"), self.time(total / float(cnt)))
                self.addLine(_("Total Time"), self.time(total))
        elif c.queue == 0:
            self.addLine(_("Position"), c.due)
        self.addLine(_("Model"), c.model()['name'])
        self.addLine(_("Template"), c.template()['name'])
        self.addLine(_("Current Group"), self.deck.groups.name(c.gid))
        self.addLine(_("Home Group"), self.deck.groups.name(c.note().gid))
        self.txt += "</table>"
        return self.txt

    def addLine(self, k, v):
        self.txt += self.makeLine(k, v)

    def makeLine(self, k, v):
        txt = "<tr><td align=right style='padding-right: 3px;'>"
        txt += "<b>%s</b></td><td>%s</td></tr>" % (k, v)
        return txt

    def date(self, tm):
        return time.strftime("%Y-%m-%d", time.localtime(tm))

    def time(self, tm):
        str = ""
        if tm >= 60:
            str = fmtTimeSpan((tm/60)*60, short=True, point=-1, unit=1)
        if tm%60 != 0 or not str:
            str += fmtTimeSpan(tm%60, point=2 if not str else -1, short=True)
        return str

# Deck stats
##########################################################################

colYoung = "#7c7"
colMature = "#070"
colCum = "rgba(0,0,0,0.9)"
colLearn = "#00F"
colRelearn = "#c00"
colCram = "#ff0"
colIvl = "#077"
colHour = "#777"
colTime = "#770"
colUnseen = "#000"
colSusp = "#ff0"

class DeckStats(object):

    def __init__(self, deck):
        self.deck = deck
        self._stats = None
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
        txt += self.hourGraph()
        txt += self.easeGraph()
        txt += self.cardGraph()
        return "<script>%s\n</script><center>%s</center>" % (anki.js.all, txt)

    css = """
<style>
h1 { margin-bottom: 0; margin-top: 1em; }
body { font-size: 14px; }
table * { font-size: 14px; }
.pielabel { text-align:center; padding:0px; color:white; }
</style>
"""

    # Due and cumulative due
    ######################################################################

    def dueGraph(self):
        if self.type == 0:
            start = 0; end = 31; chunk = 1;
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
        xaxis = dict(tickDecimals=0, min=-0.5)
        if end is not None:
            xaxis['max'] = end-0.5
        txt += self._graph(id="due", data=data, conf=dict(
                xaxis=xaxis,
                yaxes=[dict(), dict(tickDecimals=0, position="right")]))
        txt += self._dueInfo(tot, len(totd)*chunk)
        return txt

    def _dueInfo(self, tot, num):
        i = []
        self._line(i, _("Total"), _("%d reviews") % tot)
        self._line(i, _("Average"), self._avgDay(
            tot, num, _("reviews")))
        return self._lineTbl(i)

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
where gid in %s and queue = 2
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
            xaxis=dict(tickDecimals=0, max=0.5),
            yaxes=[dict(), dict(position="right")])
        if days is not None:
            conf['xaxis']['min'] = -days-0.5
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
        (daysStud, fstDay) = self._daysStudied()
        txt += self._ansInfo(repsum, daysStud, fstDay, _("reviews"))

        # time
        (timdata, timsum) = self._splitRepData(d, (
            (8, colMature, _("Mature")),
            (7, colYoung, _("Young")),
            (9, colRelearn, _("Relearn")),
            (6, colLearn, _("Learn")),
            (10, colCram, _("Cram"))))
        if self.type == 0:
            t = _("Minutes")
            convHours = False
        else:
            t = _("Hours")
            convHours = True
        txt += self._title(timetitle, _("The time taken to answer the questions."))
        txt += plot("time", timdata, ylabel=t)
        txt += self._ansInfo(timsum, daysStud, fstDay, _("minutes"), convHours)
        return txt

    def _ansInfo(self, totd, studied, first, unit, convHours=False):
        if not totd:
            return
        tot = totd[-1][1]
        period = self._periodDays()
        if not period:
            period = self.deck.sched.today - first + 1
        i = []
        self._line(i, _("Days studied"),
                   _("<b>%(pct)d%%</b> (%(x)s of %(y)s)") % dict(
                       x=studied, y=period, pct=studied/float(period)*100),
                   bold=False)
        if convHours:
            tunit = _("hours")
        else:
            tunit = unit
        self._line(i, _("Total"), _("%(tot)s %(unit)s") % dict(
            unit=tunit, tot=int(tot)))
        if convHours:
            # convert to minutes
            tot *= 60
        self._line(i, _("Average over studied"), self._avgDay(
            tot, studied, unit))
        self._line(i, _("If you studied every day"), self._avgDay(
            tot, period, unit))
        return self._lineTbl(i)

    def _splitRepData(self, data, spec):
        sep = {}
        totcnt = {}
        totd = {}
        alltot = []
        allcnt = 0
        for (n, col, lab) in spec:
            totcnt[n] = 0
            totd[n] = []
        sum = []
        for row in data:
            for (n, col, lab) in spec:
                if n not in sep:
                    sep[n] = []
                sep[n].append((row[0], row[n]))
                totcnt[n] += row[n]
                allcnt += row[n]
                totd[n].append((row[0], totcnt[n]))
            alltot.append((row[0], allcnt))
        ret = []
        for (n, col, lab) in spec:
            if len(totd[n]) > 1 and totcnt[n]:
                # bars
                ret.append(dict(data=sep[n], color=col, label=lab))
                # lines
                ret.append(dict(
                    data=totd[n], color=col, label=None, yaxis=2,
                bars={'show': False}, lines=dict(show=True), stack=-n))
        return (ret, alltot)

    def _done(self, num=7, chunk=1):
        lims = []
        if num is not None:
            lims.append("id > %d" % (
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
(cast((id/1000 - :cut) / 86400.0 as int))/:chunk as day,
sum(case when type = 0 then 1 else 0 end), -- lrn count
sum(case when type = 1 and lastIvl < 21 then 1 else 0 end), -- yng count
sum(case when type = 1 and lastIvl >= 21 then 1 else 0 end), -- mtr count
sum(case when type = 2 then 1 else 0 end), -- lapse count
sum(case when type = 3 then 1 else 0 end), -- cram count
sum(case when type = 0 then time/1000 else 0 end)/:tf, -- lrn time
-- yng + mtr time
sum(case when type = 1 and lastIvl < 21 then time/1000 else 0 end)/:tf,
sum(case when type = 1 and lastIvl >= 21 then time/1000 else 0 end)/:tf,
sum(case when type = 2 then time/1000 else 0 end)/:tf, -- lapse time
sum(case when type = 3 then time/1000 else 0 end)/:tf -- cram time
from revlog %s
group by day order by day""" % lim,
                            cut=self.deck.sched.dayCutoff,
                            tf=tf,
                            chunk=chunk)

    def _daysStudied(self):
        lims = []
        num = self._periodDays()
        if num:
            lims.append(
                "id > %d" %
                ((self.deck.sched.dayCutoff-(num*86400))*1000))
        rlim = self._revlogLimit()
        if rlim:
            lims.append(rlim)
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        return self.deck.db.first("""
select count(), abs(min(day)) from (select
(cast((id/1000 - :cut) / 86400.0 as int)+1) as day
from revlog %s
group by day order by day)""" % lim,
                                   cut=self.deck.sched.dayCutoff)

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
                xaxis=dict(min=-0.5, max=ivls[-1][0]+0.5),
                yaxes=[dict(), dict(position="right", max=105)]))
        i = []
        self._line(i, _("Average interval"), fmtTimeSpan(avg*86400))
        self._line(i, _("Longest interval"), fmtTimeSpan(max*86400))
        return txt + self._lineTbl(i)

    def _ivls(self):
        if self.type == 0:
            chunk = 1; lim = " and grp <= 30"
        elif self.type == 1:
            chunk = 7; lim = " and grp <= 52"
        else:
            chunk = 30; lim = ""
        data = [self.deck.db.all("""
select ivl / :chunk as grp, count() from cards
where gid in %s and queue = 2 %s
group by grp
order by grp""" % (self._limit(), lim), chunk=chunk)]
        return data + list(self.deck.db.first("""
select count(), avg(ivl), max(ivl) from cards where gid in %s and queue = 2""" %
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

    # Hourly retention
    ######################################################################

    def hourGraph(self):
        data = self._hourRet()
        if not data:
            return ""
        shifted = []
        trend = []
        peak = 0
        for d in data:
            hour = (d[0] - 4) % 24
            pct = d[1]
            if pct > peak:
                peak = pct
            shifted.append((hour, pct))
        shifted.sort()
        for d in shifted:
            hour = d[0]
            pct = d[1]
            if not trend:
                trend.append((hour, pct))
            else:
                prev = trend[-1][1]
                diff = pct-prev
                diff /= 3.0
                diff = round(diff, 1)
                trend.append((hour, prev+diff))
        txt = self._title(_("Hourly Retention"),
                          _("Review success rate for each hour of the day."))
        txt += self._graph(id="hour", data=[
            dict(data=shifted, color=colHour, label=_("% Failed")),
            dict(data=trend, color=colCum, label=_("Trend"),
             bars={'show': False}, lines=dict(show=True), stack=False)
        ], conf=dict(
            xaxis=dict(ticks=[[0, _("4AM")], [6, _("10AM")],
                           [12, _("4PM")], [18, _("10PM")], [23, _("3AM")]]),
            yaxis=dict(max=peak)),
        ylabel=_("%Correct"))
        return txt

    def _hourRet(self):
        lim = self._revlogLimit()
        if lim:
            lim = " and " + lim
        sd = datetime.datetime.fromtimestamp(self.deck.crt)
        return self.deck.db.all("""
select
23 - ((cast((:cut - id/1000) / 3600.0 as int)) %% 24) as hour,
sum(case when ease = 1 then 0 else 1 end) /
cast(count() as float) * 100,
count()
from revlog where type = 1 %s
group by hour having count() > 30 order by hour""" % lim,
                            cut=self.deck.sched.dayCutoff-(sd.hour*3600))

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
        (c, f) = self.deck.db.first("""
select count(id), count(distinct nid) from cards
where gid in %s """ % self._limit())
        self._line(i, _("Total cards"), c)
        self._line(i, _("Total notes"), f)
        (low, avg, high) = self._factors()
        if low:
            self._line(i, _("Lowest ease factor"), "%d%%" % low)
            self._line(i, _("Average ease factor"), "%d%%" % avg)
            self._line(i, _("Highest ease factor"), "%d%%" % high)
        min = self.deck.db.scalar(
            "select min(id) from cards where gid in %s " % self._limit())
        if min:
            self._line(i, _("First card created"), _("%s ago") % fmtTimeSpan(
            time.time() - (min/1000)))
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

    def _line(self, i, a, b, bold=True):
        if bold:
            i.append(("<tr><td width=200 align=right>%s:</td><td><b>%s</b></td></tr>") % (a,b))
        else:
            i.append(("<tr><td width=200 align=right>%s:</td><td>%s</td></tr>") % (a,b))

    def _lineTbl(self, i):
        return "<table width=400>" + "".join(i) + "</table>"

    def _factors(self):
        return self.deck.db.first("""
select
min(factor) / 10.0,
avg(factor) / 10.0,
max(factor) / 10.0
from cards where gid in %s and queue = 2""" % self._limit())

    def _cards(self):
        return self.deck.db.first("""
select
sum(case when queue=2 and ivl >= 21 then 1 else 0 end), -- mtr
sum(case when queue=1 or (queue=2 and ivl < 21) then 1 else 0 end), -- yng/lrn
sum(case when queue=0 then 1 else 0 end), -- new
sum(case when queue=-1 then 1 else 0 end) -- susp
from cards where gid in %s""" % self._limit())

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
                radius=1,
                stroke=dict(color="#fff", width=5),
                label=dict(
                    show=True,
                    radius=0.8,
                    threshold=0.01,
                    background=dict(
                        opacity=0.5,
                        color="#000"
                    )))

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
            return '<div class=pielabel>'+Math.round(series.percent)+'%%</div>';
        };
    }
    $.plot($("#%(id)s"), %(data)s, conf);
});
</script>""" % dict(
    id=id, w=width, h=height,
    ylab=ylabel,
    data=simplejson.dumps(data), conf=simplejson.dumps(conf)))

    def _limit(self):
        return self.deck.sched._groupLimit()

    def _revlogLimit(self):
        return ("cid in (select id from cards where gid in %s)" %
                ids2str(self.deck.groups.active()))

    def _title(self, title, subtitle=""):
        return '<h1>%s</h1>%s' % (title, subtitle)

    def _periodDays(self):
        if self.type == 0:
            return 30
        elif self.type == 1:
            return 365
        else:
            return None

    def _avgDay(self, tot, num, unit):
        vals = []
        try:
            vals.append(_("%d %s/day") % (tot/float(num), unit))
            return ", ".join(vals)
        except ZeroDivisionError:
            return ""
