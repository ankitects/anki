# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import division
import time
import datetime
import json

import anki.js
from anki.utils import fmtTimeSpan, ids2str
from anki.lang import _, ngettext


# Card stats
##########################################################################

class CardStats(object):

    def __init__(self, col, card):
        self.col = col
        self.card = card

    def report(self):
        c = self.card
        fmt = lambda x, **kwargs: fmtTimeSpan(x, short=True, **kwargs)
        self.txt = "<table width=100%>"
        self.addLine(_("Added"), self.date(c.id/1000))
        first = self.col.db.scalar(
            "select min(id) from revlog where cid = ?", c.id)
        last = self.col.db.scalar(
            "select max(id) from revlog where cid = ?", c.id)
        if first:
            self.addLine(_("First Review"), self.date(first/1000))
            self.addLine(_("Latest Review"), self.date(last/1000))
        if c.type in (1,2):
            if c.odid or c.queue < 0:
                next = None
            else:
                if c.queue in (2,3):
                    next = time.time()+((c.due - self.col.sched.today)*86400)
                else:
                    next = c.due
                next = self.date(next)
            if next:
                self.addLine(_("Due"), next)
            if c.queue == 2:
                self.addLine(_("Interval"), fmt(c.ivl * 86400))
            self.addLine(_("Ease"), "%d%%" % (c.factor/10.0))
            self.addLine(_("Reviews"), "%d" % c.reps)
            self.addLine(_("Lapses"), "%d" % c.lapses)
            (cnt, total) = self.col.db.first(
                "select count(), sum(time)/1000 from revlog where cid = :id",
                id=c.id)
            if cnt:
                self.addLine(_("Average Time"), self.time(total / float(cnt)))
                self.addLine(_("Total Time"), self.time(total))
        elif c.queue == 0:
            self.addLine(_("Position"), c.due)
        self.addLine(_("Card Type"), c.template()['name'])
        self.addLine(_("Note Type"), c.model()['name'])
        self.addLine(_("Deck"), self.col.decks.name(c.did))
        self.addLine(_("Note ID"), c.nid)
        self.addLine(_("Card ID"), c.id)
        self.txt += "</table>"
        return self.txt

    def addLine(self, k, v):
        self.txt += self.makeLine(k, v)

    def makeLine(self, k, v):
        txt = "<tr><td align=left style='padding-right: 3px;'>"
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

# Collection stats
##########################################################################

colYoung = "#7c7"
colMature = "#070"
colCum = "rgba(0,0,0,0.9)"
colLearn = "#00F"
colRelearn = "#c00"
colCram = "#ff0"
colIvl = "#077"
colHour = "#ccc"
colTime = "#770"
colUnseen = "#000"
colSusp = "#ff0"

class CollectionStats(object):

    def __init__(self, col):
        self.col = col
        self._stats = None
        self.type = 0
        self.width = 600
        self.height = 200
        self.wholeCollection = False

    def report(self, type=0):
        # 0=days, 1=weeks, 2=months
        self.type = type
        from statsbg import bg
        txt = self.css % bg
        txt += self.todayStats()
        txt += self.dueGraph()
        txt += self.repsGraph()
        txt += self.introductionGraph()
        txt += self.ivlGraph()
        txt += self.hourGraph()
        txt += self.easeGraph()
        txt += self.cardGraph()
        txt += self.footer()
        return "<script>%s\n</script><center>%s</center>" % (
            anki.js.jquery+anki.js.plot, txt)

    css = """
<style>
h1 { margin-bottom: 0; margin-top: 1em; }
.pielabel { text-align:center; padding:0px; color:white; }
body {background-image: url(data:image/png;base64,%s); }
</style>
"""

    # Today stats
    ######################################################################

    def todayStats(self):
        b = self._title(_("Today"))
        # studied today
        lim = self._revlogLimit()
        if lim:
            lim = " and " + lim
        cards, thetime, failed, lrn, rev, relrn, filt = self.col.db.first("""
select count(), sum(time)/1000,
sum(case when ease = 1 then 1 else 0 end), /* failed */
sum(case when type = 0 then 1 else 0 end), /* learning */
sum(case when type = 1 then 1 else 0 end), /* review */
sum(case when type = 2 then 1 else 0 end), /* relearn */
sum(case when type = 3 then 1 else 0 end) /* filter */
from revlog where id > ? """+lim, (self.col.sched.dayCutoff-86400)*1000)
        cards = cards or 0
        thetime = thetime or 0
        failed = failed or 0
        lrn = lrn or 0
        rev = rev or 0
        relrn = relrn or 0
        filt = filt or 0
        # studied
        def bold(s):
            return "<b>"+unicode(s)+"</b>"
        msgp1 = ngettext("<!--studied-->%d card", "<!--studied-->%d cards", cards) % cards
        b += _("Studied %(a)s in %(b)s today.") % dict(
            a=bold(msgp1), b=bold(fmtTimeSpan(thetime, unit=1)))
        # again/pass count
        b += "<br>" + _("Again count: %s") % bold(failed)
        if cards:
            b += " " + _("(%s correct)") % bold(
                "%0.1f%%" %((1-failed/float(cards))*100))
        # type breakdown
        b += "<br>"
        b += (_("Learn: %(a)s, Review: %(b)s, Relearn: %(c)s, Filtered: %(d)s")
              % dict(a=bold(lrn), b=bold(rev), c=bold(relrn), d=bold(filt)))
        # mature today
        mcnt, msum = self.col.db.first("""
select count(), sum(case when ease = 1 then 0 else 1 end) from revlog
where lastIvl >= 21 and id > ?"""+lim, (self.col.sched.dayCutoff-86400)*1000)
        b += "<br>"
        if mcnt:
            b += _("Correct answers on mature cards: %(a)d/%(b)d (%(c).1f%%)") % dict(
                a=msum, b=mcnt, c=(msum / float(mcnt) * 100))
        else:
            b += _("No mature cards were studied today.")
        return b

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
        txt += self._graph(id="due", data=data,
                           ylabel2=_("Cumulative Cards"), conf=dict(
                xaxis=xaxis, yaxes=[dict(min=0), dict(
                    min=0, tickDecimals=0, position="right")]))
        txt += self._dueInfo(tot, len(totd)*chunk)
        return txt

    def _dueInfo(self, tot, num):
        i = []
        self._line(i, _("Total"), ngettext("%d review", "%d reviews", tot) % tot)
        self._line(i, _("Average"), self._avgDay(
            tot, num, _("reviews")))
        tomorrow = self.col.db.scalar("""
select count() from cards where did in %s and queue in (2,3)
and due = ?""" % self._limit(), self.col.sched.today+1)
        tomorrow = ngettext("%d card", "%d cards", tomorrow) % tomorrow
        self._line(i, _("Due tomorrow"), tomorrow)
        return self._lineTbl(i)

    def _due(self, start=None, end=None, chunk=1):
        lim = ""
        if start is not None:
            lim += " and due-:today >= %d" % start
        if end is not None:
            lim += " and day < %d" % end
        return self.col.db.all("""
select (due-:today)/:chunk as day,
sum(case when ivl < 21 then 1 else 0 end), -- yng
sum(case when ivl >= 21 then 1 else 0 end) -- mtr
from cards
where did in %s and queue in (2,3)
%s
group by day order by day""" % (self._limit(), lim),
                            today=self.col.sched.today,
                            chunk=chunk)

    # Added, reps and time spent
    ######################################################################

    def introductionGraph(self):
        if self.type == 0:
            days = 30; chunk = 1
        elif self.type == 1:
            days = 52; chunk = 7
        else:
            days = None; chunk = 30
        return self._introductionGraph(self._added(days, chunk),
                               days, _("Added"))

    def _introductionGraph(self, data, days, title):
        if not data:
            return ""
        d = data
        conf = dict(
            xaxis=dict(tickDecimals=0, max=0.5),
            yaxes=[dict(min=0), dict(position="right",min=0)])
        if days is not None:
            conf['xaxis']['min'] = -days+0.5
        def plot(id, data, ylabel, ylabel2):
            return self._graph(
                id, data=data, conf=conf, ylabel=ylabel, ylabel2=ylabel2)
        # graph
        (repdata, repsum) = self._splitRepData(d, ((1, colLearn, ""),))
        txt = self._title(
            title, _("The number of new cards you have added."))
        txt += plot("intro", repdata, ylabel=_("Cards"), ylabel2=_("Cumulative Cards"))
        # total and per day average
        tot = sum([i[1] for i in d])
        period = self._periodDays()
        if not period:
            # base off date of earliest added card
            period = self._deckAge('add')
        i = []
        self._line(i, _("Total"), ngettext("%d card", "%d cards", tot) % tot)
        self._line(i, _("Average"), self._avgDay(tot, period, _("cards")))
        txt += self._lineTbl(i)

        return txt

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
            yaxes=[dict(min=0), dict(position="right",min=0)])
        if days is not None:
            conf['xaxis']['min'] = -days+0.5
        def plot(id, data, ylabel, ylabel2):
            return self._graph(
                id, data=data, conf=conf, ylabel=ylabel, ylabel2=ylabel2)
        # reps
        (repdata, repsum) = self._splitRepData(d, (
            (3, colMature, _("Mature")),
            (2, colYoung, _("Young")),
            (4, colRelearn, _("Relearn")),
            (1, colLearn, _("Learn")),
            (5, colCram, _("Cram"))))
        txt = self._title(
            reptitle, _("The number of questions you have answered."))
        txt += plot("reps", repdata, ylabel=_("Answers"), ylabel2=_(
            "Cumulative Answers"))
        (daysStud, fstDay) = self._daysStudied()
        rep, tot = self._ansInfo(repsum, daysStud, fstDay, _("reviews"))
        txt += rep
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
        txt += plot("time", timdata, ylabel=t, ylabel2=_("Cumulative %s") % t)
        rep, tot2 = self._ansInfo(
            timsum, daysStud, fstDay, _("minutes"), convHours, total=tot)
        txt += rep
        return txt

    def _ansInfo(self, totd, studied, first, unit, convHours=False, total=None):
        if not totd:
            return
        tot = totd[-1][1]
        period = self._periodDays()
        if not period:
            # base off earliest repetition date
            period = self._deckAge('review')
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
        self._line(i, _("Average for days studied"), self._avgDay(
            tot, studied, unit))
        if studied != period:
            # don't display if you did study every day
            self._line(i, _("If you studied every day"), self._avgDay(
                tot, period, unit))
        if total and tot:
            perMin = total / float(tot)
            perMin = round(perMin, 1)
            # don't round down to zero
            if perMin < 0.1:
                text = _("less than 0.1 cards/minute")
            else:
                text = _("%.01f cards/minute") % perMin
            self._line(
                i, _("Average answer time"),
                _("%(a)0.1fs (%(b)s)") % dict(a=(tot*60)/total, b=text))
        return self._lineTbl(i), int(tot)

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
            if len(totd[n]) and totcnt[n]:
                # bars
                ret.append(dict(data=sep[n], color=col, label=lab))
                # lines
                ret.append(dict(
                    data=totd[n], color=col, label=None, yaxis=2,
                bars={'show': False}, lines=dict(show=True), stack=-n))
        return (ret, alltot)

    def _added(self, num=7, chunk=1):
        lims = []
        if num is not None:
            lims.append("id > %d" % (
                (self.col.sched.dayCutoff-(num*chunk*86400))*1000))
        lims.append("did in %s" % self._limit())
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        if self.type == 0:
            tf = 60.0 # minutes
        else:
            tf = 3600.0 # hours
        return self.col.db.all("""
select
(cast((id/1000.0 - :cut) / 86400.0 as int))/:chunk as day,
count(id)
from cards %s
group by day order by day""" % lim, cut=self.col.sched.dayCutoff,tf=tf, chunk=chunk)

    def _done(self, num=7, chunk=1):
        lims = []
        if num is not None:
            lims.append("id > %d" % (
                (self.col.sched.dayCutoff-(num*chunk*86400))*1000))
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
        return self.col.db.all("""
select
(cast((id/1000.0 - :cut) / 86400.0 as int))/:chunk as day,
sum(case when type = 0 then 1 else 0 end), -- lrn count
sum(case when type = 1 and lastIvl < 21 then 1 else 0 end), -- yng count
sum(case when type = 1 and lastIvl >= 21 then 1 else 0 end), -- mtr count
sum(case when type = 2 then 1 else 0 end), -- lapse count
sum(case when type = 3 then 1 else 0 end), -- cram count
sum(case when type = 0 then time/1000.0 else 0 end)/:tf, -- lrn time
-- yng + mtr time
sum(case when type = 1 and lastIvl < 21 then time/1000.0 else 0 end)/:tf,
sum(case when type = 1 and lastIvl >= 21 then time/1000.0 else 0 end)/:tf,
sum(case when type = 2 then time/1000.0 else 0 end)/:tf, -- lapse time
sum(case when type = 3 then time/1000.0 else 0 end)/:tf -- cram time
from revlog %s
group by day order by day""" % lim,
                            cut=self.col.sched.dayCutoff,
                            tf=tf,
                            chunk=chunk)

    def _daysStudied(self):
        lims = []
        num = self._periodDays()
        if num:
            lims.append(
                "id > %d" %
                ((self.col.sched.dayCutoff-(num*86400))*1000))
        rlim = self._revlogLimit()
        if rlim:
            lims.append(rlim)
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        return self.col.db.first("""
select count(), abs(min(day)) from (select
(cast((id/1000 - :cut) / 86400.0 as int)+1) as day
from revlog %s
group by day order by day)""" % lim,
                                   cut=self.col.sched.dayCutoff)

    # Intervals
    ######################################################################

    def ivlGraph(self):
        (ivls, all, avg, max_) = self._ivls()
        tot = 0
        totd = []
        if not ivls or not all:
            return ""
        for (grp, cnt) in ivls:
            tot += cnt
            totd.append((grp, tot/float(all)*100))
        if self.type == 0:
            ivlmax = 31
        elif self.type == 1:
            ivlmax = 52
        else:
            ivlmax = max(5, ivls[-1][0])
        txt = self._title(_("Intervals"),
                          _("Delays until reviews are shown again."))
        txt += self._graph(id="ivl", ylabel2=_("Percentage"), data=[
            dict(data=ivls, color=colIvl),
            dict(data=totd, color=colCum, yaxis=2,
             bars={'show': False}, lines=dict(show=True), stack=False)
            ], conf=dict(
                xaxis=dict(min=-0.5, max=ivlmax+0.5),
                yaxes=[dict(), dict(position="right", max=105)]))
        i = []
        self._line(i, _("Average interval"), fmtTimeSpan(avg*86400))
        self._line(i, _("Longest interval"), fmtTimeSpan(max_*86400))
        return txt + self._lineTbl(i)

    def _ivls(self):
        if self.type == 0:
            chunk = 1; lim = " and grp <= 30"
        elif self.type == 1:
            chunk = 7; lim = " and grp <= 52"
        else:
            chunk = 30; lim = ""
        data = [self.col.db.all("""
select ivl / :chunk as grp, count() from cards
where did in %s and queue = 2 %s
group by grp
order by grp""" % (self._limit(), lim), chunk=chunk)]
        return data + list(self.col.db.first("""
select count(), avg(ivl), max(ivl) from cards where did in %s and queue = 2""" %
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
        lims = []
        lim = self._revlogLimit()
        if lim:
            lims.append(lim)
        if self.type == 0:
            days = 30
        elif self.type == 1:
            days = 365
        else:
            days = None
        if days is not None:
            lims.append("id > %d" % (
                (self.col.sched.dayCutoff-(days*86400))*1000))
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        return self.col.db.all("""
select (case
when type in (0,2) then 0
when lastIvl < 21 then 1
else 2 end) as thetype,
(case when type in (0,2) and ease = 4 then 3 else ease end), count() from revlog %s
group by thetype, ease
order by thetype, ease""" % lim)

    # Hourly retention
    ######################################################################

    def hourGraph(self):
        data = self._hourRet()
        if not data:
            return ""
        shifted = []
        counts = []
        mcount = 0
        trend = []
        peak = 0
        for d in data:
            hour = (d[0] - 4) % 24
            pct = d[1]
            if pct > peak:
                peak = pct
            shifted.append((hour, pct))
            counts.append((hour, d[2]))
            if d[2] > mcount:
                mcount = d[2]
        shifted.sort()
        counts.sort()
        if len(counts) < 4:
            return ""
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
        txt = self._title(_("Hourly Breakdown"),
                          _("Review success rate for each hour of the day."))
        txt += self._graph(id="hour", data=[
            dict(data=shifted, color=colCum, label=_("% Correct")),
            dict(data=counts, color=colHour, label=_("Answers"), yaxis=2,
             bars=dict(barWidth=0.2), stack=False)
        ], conf=dict(
            xaxis=dict(ticks=[[0, _("4AM")], [6, _("10AM")],
                           [12, _("4PM")], [18, _("10PM")], [23, _("3AM")]]),
            yaxes=[dict(max=peak), dict(position="right", max=mcount)]),
        ylabel=_("% Correct"), ylabel2=_("Reviews"))
        txt += _("Hours with less than 30 reviews are not shown.")
        return txt

    def _hourRet(self):
        lim = self._revlogLimit()
        if lim:
            lim = " and " + lim
        sd = datetime.datetime.fromtimestamp(self.col.crt)
        pd = self._periodDays()
        if pd:
            lim += " and id > %d" % ((self.col.sched.dayCutoff-(86400*pd))*1000)
        return self.col.db.all("""
select
23 - ((cast((:cut - id/1000) / 3600.0 as int)) %% 24) as hour,
sum(case when ease = 1 then 0 else 1 end) /
cast(count() as float) * 100,
count()
from revlog where type in (0,1,2) %s
group by hour having count() > 30 order by hour""" % lim,
                            cut=self.col.sched.dayCutoff-(sd.hour*3600))

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
            (_("Suspended+Buried"), colSusp))):
            d.append(dict(data=div[c], label="%s: %s" % (t, div[c]), color=col))
        # text data
        i = []
        (c, f) = self.col.db.first("""
select count(id), count(distinct nid) from cards
where did in %s """ % self._limit())
        self._line(i, _("Total cards"), c)
        self._line(i, _("Total notes"), f)
        (low, avg, high) = self._factors()
        if low:
            self._line(i, _("Lowest ease"), "%d%%" % low)
            self._line(i, _("Average ease"), "%d%%" % avg)
            self._line(i, _("Highest ease"), "%d%%" % high)
        info = "<table width=100%>" + "".join(i) + "</table><p>"
        info += _('''\
A card's <i>ease</i> is the size of the next interval \
when you answer "good" on a review.''')
        txt = self._title(_("Cards Types"),
                          _("The division of cards in your deck(s)."))
        txt += "<table width=%d><tr><td>%s</td><td>%s</td></table>" % (
            self.width,
            self._graph(id="cards", data=d, type="pie"),
            info)
        return txt

    def _line(self, i, a, b, bold=True):
        colon = _(":")
        if bold:
            i.append(("<tr><td width=200 align=right>%s%s</td><td><b>%s</b></td></tr>") % (a,colon,b))
        else:
            i.append(("<tr><td width=200 align=right>%s%s</td><td>%s</td></tr>") % (a,colon,b))

    def _lineTbl(self, i):
        return "<table width=400>" + "".join(i) + "</table>"

    def _factors(self):
        return self.col.db.first("""
select
min(factor) / 10.0,
avg(factor) / 10.0,
max(factor) / 10.0
from cards where did in %s and queue = 2""" % self._limit())

    def _cards(self):
        return self.col.db.first("""
select
sum(case when queue=2 and ivl >= 21 then 1 else 0 end), -- mtr
sum(case when queue in (1,3) or (queue=2 and ivl < 21) then 1 else 0 end), -- yng/lrn
sum(case when queue=0 then 1 else 0 end), -- new
sum(case when queue<0 then 1 else 0 end) -- susp
from cards where did in %s""" % self._limit())

    # Footer
    ######################################################################

    def footer(self):
        b = "<br><br><font size=1>"
        b += _("Generated on %s") % time.asctime(time.localtime(time.time()))
        b += "<br>"
        if self.wholeCollection:
            deck = _("whole collection")
        else:
            deck = self.col.decks.current()['name']
        b += _("Scope: %s") % deck
        b += "<br>"
        b += _("Period: %s") % [
            _("1 month"),
            _("1 year"),
            _("deck life")
            ][self.type]
        return b

    # Tools
    ######################################################################

    def _graph(self, id, data, conf={},
               type="bars", ylabel=_("Cards"), timeTicks=True, ylabel2=""):
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
            conf['timeTicks'] = (_("d"), _("w"), _("mo"))[self.type]
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

<td><div style="width: 150px; text-align: center; position:absolute;
 -webkit-transform: rotate(-90deg) translateY(-85px);
font-weight: bold;
">%(ylab)s</div></td>

<td>
<center><div id=%(id)sLegend></div></center>
<div id="%(id)s" style="width:%(w)spx; height:%(h)spx;"></div>
</td>

<td><div style="width: 150px; text-align: center; position:absolute;
 -webkit-transform: rotate(90deg) translateY(65px);
font-weight: bold;
">%(ylab2)s</div></td>

</tr></table>
<script>
$(function () {
    var conf = %(conf)s;
    if (conf.timeTicks) {
        conf.xaxis.tickFormatter = function (val, axis) {
            return val.toFixed(0)+conf.timeTicks;
        }
    }
    conf.yaxis.minTickSize = 1;
    conf.yaxis.tickFormatter = function (val, axis) {
            return val.toFixed(0);
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
    ylab=ylabel, ylab2=ylabel2,
    data=json.dumps(data), conf=json.dumps(conf)))

    def _limit(self):
        if self.wholeCollection:
            return ids2str([d['id'] for d in self.col.decks.all()])
        return self.col.sched._deckLimit()

    def _revlogLimit(self):
        if self.wholeCollection:
            return ""
        return ("cid in (select id from cards where did in %s)" %
                ids2str(self.col.decks.active()))

    def _title(self, title, subtitle=""):
        return '<h1>%s</h1>%s' % (title, subtitle)

    def _deckAge(self, by):
        lim = self._revlogLimit()
        if lim:
            lim = " where " + lim
        if by == 'review':
            t = self.col.db.scalar("select id from revlog %s order by id limit 1" % lim)
        elif by == 'add':
            lim = "where did in %s" % ids2str(self.col.decks.active())
            t = self.col.db.scalar("select id from cards %s order by id limit 1" % lim)
        if not t:
            period = 1
        else:
            period = max(
                1, int(1+((self.col.sched.dayCutoff - (t/1000)) / 86400)))
        return period

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
            vals.append(_("%(a)0.1f %(b)s/day") % dict(a=tot/float(num), b=unit))
            return ", ".join(vals)
        except ZeroDivisionError:
            return ""
