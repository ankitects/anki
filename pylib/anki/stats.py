# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import datetime
import json
import time
from typing import Any, Dict, List, Optional, Sequence, Tuple, Union

import anki
from anki.consts import *
from anki.lang import TR, FormatTimeSpan
from anki.utils import ids2str

# Card stats
##########################################################################


class CardStats:
    """
    New code should just call collection.card_stats() directly - this class
    is only left around for backwards compatibility.
    """

    def __init__(self, col: anki.collection.Collection, card: anki.cards.Card) -> None:
        if col:
            self.col = col.weakref()
        self.card = card
        self.txt = ""

    def report(self, include_revlog: bool = False) -> str:
        return self.col.card_stats(self.card.id, include_revlog=include_revlog)

    # legacy

    def addLine(self, k: str, v: Union[int, str]) -> None:
        self.txt += self.makeLine(k, v)

    def makeLine(self, k: str, v: Union[str, int]) -> str:
        txt = "<tr><td align=left style='padding-right: 3px;'>"
        txt += "<b>%s</b></td><td>%s</td></tr>" % (k, v)
        return txt

    def date(self, tm: float) -> str:
        return time.strftime("%Y-%m-%d", time.localtime(tm))

    def time(self, tm: float) -> str:
        return self.col.format_timespan(tm, context=FormatTimeSpan.PRECISE)


# Collection stats
##########################################################################

PERIOD_MONTH = 0
PERIOD_YEAR = 1
PERIOD_LIFE = 2

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


class CollectionStats:
    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self._stats = None
        self.type = PERIOD_MONTH
        self.width = 600
        self.height = 200
        self.wholeCollection = False

    # assumes jquery & plot are available in document
    def report(self, type: int = PERIOD_MONTH) -> str:
        # 0=month, 1=year, 2=deck life
        self.type = type
        from .statsbg import bg

        txt = self.css % bg
        txt += self._section(self.todayStats())
        txt += self._section(self.dueGraph())
        txt += self.repsGraphs()
        txt += self._section(self.introductionGraph())
        txt += self._section(self.ivlGraph())
        txt += self._section(self.hourGraph())
        txt += self._section(self.easeGraph())
        txt += self._section(self.cardGraph())
        txt += self._section(self.footer())
        return "<center>%s</center>" % txt

    def _section(self, txt: str) -> str:
        return "<div class=section>%s</div>" % txt

    css = """
<style>
h1 { margin-bottom: 0; margin-top: 1em; }
.pielabel { text-align:center; padding:0px; color:white; }
body:not(.night_mode) {background-image: url(data:image/png;base64,%s); }
@media print {
    .section { page-break-inside: avoid; padding-top: 5mm; }
}
body { direction: ltr !important; }
</style>
"""

    # Today stats
    ######################################################################

    def todayStats(self) -> str:
        b = self._title("Today")
        # studied today
        lim = self._revlogLimit()
        if lim:
            lim = " and " + lim
        cards, thetime, failed, lrn, rev, relrn, filt = self.col.db.first(
            f"""
select count(), sum(time)/1000,
sum(case when ease = 1 then 1 else 0 end), /* failed */
sum(case when type = {REVLOG_LRN} then 1 else 0 end), /* learning */
sum(case when type = {REVLOG_REV} then 1 else 0 end), /* review */
sum(case when type = {REVLOG_RELRN} then 1 else 0 end), /* relearn */
sum(case when type = {REVLOG_CRAM} then 1 else 0 end) /* filter */
from revlog where id > ? """
            + lim,
            (self.col.sched.dayCutoff - 86400) * 1000,
        )
        cards = cards or 0
        thetime = thetime or 0
        failed = failed or 0
        lrn = lrn or 0
        rev = rev or 0
        relrn = relrn or 0
        filt = filt or 0
        # studied
        def bold(s: str) -> str:
            return "<b>" + str(s) + "</b>"

        if cards:
            b += self.col._backend.studied_today_message(
                cards=cards, seconds=float(thetime)
            )
            # again/pass count
            b += "<br>" + "Again count: %s" % bold(failed)
            if cards:
                b += " " + "(%s correct)" % bold(
                    "%0.1f%%" % ((1 - failed / float(cards)) * 100)
                )
            # type breakdown
            b += "<br>"
            b += "Learn: %(a)s, Review: %(b)s, Relearn: %(c)s, Filtered: %(d)s" % dict(
                a=bold(lrn), b=bold(rev), c=bold(relrn), d=bold(filt)
            )
            # mature today
            mcnt, msum = self.col.db.first(
                """
    select count(), sum(case when ease = 1 then 0 else 1 end) from revlog
    where lastIvl >= 21 and id > ?"""
                + lim,
                (self.col.sched.dayCutoff - 86400) * 1000,
            )
            b += "<br>"
            if mcnt:
                b += "Correct answers on mature cards: %(a)d/%(b)d (%(c).1f%%)" % dict(
                    a=msum, b=mcnt, c=(msum / float(mcnt) * 100)
                )
            else:
                b += "No mature cards were studied today."
        else:
            b += "No cards have been studied today."
        return b

    # Due and cumulative due
    ######################################################################

    def get_start_end_chunk(self, by: str = "review") -> Tuple[int, Optional[int], int]:
        start = 0
        if self.type == PERIOD_MONTH:
            end, chunk = 31, 1
        elif self.type == PERIOD_YEAR:
            end, chunk = 52, 7
        else:  #  self.type == 2:
            end = None
            if self._deckAge(by) <= 100:
                chunk = 1
            elif self._deckAge(by) <= 700:
                chunk = 7
            else:
                chunk = 31
        return start, end, chunk

    def dueGraph(self) -> str:
        start, end, chunk = self.get_start_end_chunk()
        d = self._due(start, end, chunk)
        yng = []
        mtr = []
        tot = 0
        totd = []
        for day in d:
            yng.append((day[0], day[1]))
            mtr.append((day[0], day[2]))
            tot += day[1] + day[2]
            totd.append((day[0], tot))
        data = [
            dict(data=mtr, color=colMature, label="Mature"),
            dict(data=yng, color=colYoung, label="Young"),
        ]
        if len(totd) > 1:
            data.append(
                dict(
                    data=totd,
                    color=colCum,
                    label="Cumulative",
                    yaxis=2,
                    bars={"show": False},
                    lines=dict(show=True),
                    stack=False,
                )
            )
        txt = self._title("Forecast", "The number of reviews due in the future.")
        xaxis = dict(tickDecimals=0, min=-0.5)
        if end is not None:
            xaxis["max"] = end - 0.5
        txt += self._graph(
            id="due",
            data=data,
            xunit=chunk,
            ylabel2="Cumulative Cards",
            conf=dict(
                xaxis=xaxis,
                yaxes=[dict(min=0), dict(min=0, tickDecimals=0, position="right")],
            ),
        )
        txt += self._dueInfo(tot, len(totd) * chunk)
        return txt

    def _dueInfo(self, tot: int, num: int) -> str:
        i: List[str] = []
        self._line(
            i,
            "Total",
            self.col.tr(TR.STATISTICS_REVIEWS, reviews=tot),
        )
        self._line(i, "Average", self._avgDay(tot, num, "reviews"))
        tomorrow = self.col.db.scalar(
            f"""
select count() from cards where did in %s and queue in ({QUEUE_TYPE_REV},{QUEUE_TYPE_DAY_LEARN_RELEARN})
and due = ?"""
            % self._limit(),
            self.col.sched.today + 1,
        )
        tomorrow = "%d cards" % tomorrow
        self._line(i, "Due tomorrow", tomorrow)
        return self._lineTbl(i)

    def _due(
        self, start: Optional[int] = None, end: Optional[int] = None, chunk: int = 1
    ) -> Any:
        lim = ""
        if start is not None:
            lim += " and due-%d >= %d" % (self.col.sched.today, start)
        if end is not None:
            lim += " and day < %d" % end
        return self.col.db.all(
            f"""
select (due-?)/? as day,
sum(case when ivl < 21 then 1 else 0 end), -- yng
sum(case when ivl >= 21 then 1 else 0 end) -- mtr
from cards
where did in %s and queue in ({QUEUE_TYPE_REV},{QUEUE_TYPE_DAY_LEARN_RELEARN})
%s
group by day order by day"""
            % (self._limit(), lim),
            self.col.sched.today,
            chunk,
        )

    # Added, reps and time spent
    ######################################################################

    def introductionGraph(self) -> str:
        start, days, chunk = self.get_start_end_chunk()
        data = self._added(days, chunk)
        if not data:
            return ""
        conf: Dict[str, Any] = dict(
            xaxis=dict(tickDecimals=0, max=0.5),
            yaxes=[dict(min=0), dict(position="right", min=0)],
        )
        if days is not None:
            # pylint: disable=invalid-unary-operand-type
            conf["xaxis"]["min"] = -days + 0.5

        def plot(id: str, data: Any, ylabel: str, ylabel2: str) -> str:
            return self._graph(
                id, data=data, conf=conf, xunit=chunk, ylabel=ylabel, ylabel2=ylabel2
            )

        # graph
        repdata, repsum = self._splitRepData(data, ((1, colLearn, ""),))
        txt = self._title("Added", "The number of new cards you have added.")
        txt += plot("intro", repdata, ylabel="Cards", ylabel2="Cumulative Cards")
        # total and per day average
        tot = sum([i[1] for i in data])
        period = self._periodDays()
        if not period:
            # base off date of earliest added card
            period = self._deckAge("add")
        i: List[str] = []
        self._line(i, "Total", "%d cards" % tot)
        self._line(i, "Average", self._avgDay(tot, period, "cards"))
        txt += self._lineTbl(i)

        return txt

    def repsGraphs(self) -> str:
        start, days, chunk = self.get_start_end_chunk()
        data = self._done(days, chunk)
        if not data:
            return ""
        conf: Dict[str, Any] = dict(
            xaxis=dict(tickDecimals=0, max=0.5),
            yaxes=[dict(min=0), dict(position="right", min=0)],
        )
        if days is not None:
            # pylint: disable=invalid-unary-operand-type
            conf["xaxis"]["min"] = -days + 0.5

        def plot(id: str, data: Any, ylabel: str, ylabel2: str) -> str:
            return self._graph(
                id, data=data, conf=conf, xunit=chunk, ylabel=ylabel, ylabel2=ylabel2
            )

        # reps
        (repdata, repsum) = self._splitRepData(
            data,
            (
                (3, colMature, "Mature"),
                (2, colYoung, "Young"),
                (4, colRelearn, "Relearn"),
                (1, colLearn, "Learn"),
                (5, colCram, "Cram"),
            ),
        )
        txt1 = self._title("Review Count", "The number of questions you have answered.")
        txt1 += plot("reps", repdata, ylabel="Answers", ylabel2="Cumulative Answers")
        (daysStud, fstDay) = self._daysStudied()
        rep, tot = self._ansInfo(repsum, daysStud, fstDay, "reviews")
        txt1 += rep
        # time
        (timdata, timsum) = self._splitRepData(
            data,
            (
                (8, colMature, "Mature"),
                (7, colYoung, "Young"),
                (9, colRelearn, "Relearn"),
                (6, colLearn, "Learn"),
                (10, colCram, "Cram"),
            ),
        )
        if self.type == PERIOD_MONTH:
            t = "Minutes"
            convHours = False
        else:
            t = "Hours"
            convHours = True
        txt2 = self._title("Review Time", "The time taken to answer the questions.")
        txt2 += plot("time", timdata, ylabel=t, ylabel2="Cumulative %s" % t)
        rep, tot2 = self._ansInfo(
            timsum, daysStud, fstDay, "minutes", convHours, total=tot
        )
        txt2 += rep
        return self._section(txt1) + self._section(txt2)

    def _ansInfo(
        self,
        totd: List[Tuple[int, float]],
        studied: int,
        first: int,
        unit: str,
        convHours: bool = False,
        total: Optional[int] = None,
    ) -> Tuple[str, int]:
        assert totd
        tot = totd[-1][1]
        period = self._periodDays()
        if not period:
            # base off earliest repetition date
            period = self._deckAge("review")
        i: List[str] = []
        self._line(
            i,
            "Days studied",
            "<b>%(pct)d%%</b> (%(x)s of %(y)s)"
            % dict(x=studied, y=period, pct=studied / float(period) * 100),
            bold=False,
        )
        if convHours:
            tunit = "hours"
        else:
            tunit = unit
        # T: unit: can be hours, minutes, reviews... tot: the number of unit.
        self._line(i, "Total", "%(tot)s %(unit)s" % dict(unit=tunit, tot=int(tot)))
        if convHours:
            # convert to minutes
            tot *= 60
        self._line(i, "Average for days studied", self._avgDay(tot, studied, unit))
        if studied != period:
            # don't display if you did study every day
            self._line(i, "If you studied every day", self._avgDay(tot, period, unit))
        if total and tot:
            perMin = total / float(tot)
            average_secs = (tot * 60) / total
            self._line(
                i,
                "Average answer time",
                self.col.tr(
                    TR.STATISTICS_AVERAGE_ANSWER_TIME,
                    **{"cards-per-minute": perMin, "average-seconds": average_secs},
                ),
            )
        return self._lineTbl(i), int(tot)

    def _splitRepData(
        self,
        data: List[Tuple[Any, ...]],
        spec: Sequence[Tuple[int, str, str]],
    ) -> Tuple[List[Dict[str, Any]], List[Tuple[Any, Any]]]:
        sep: Dict[int, Any] = {}
        totcnt = {}
        totd: Dict[int, Any] = {}
        alltot = []
        allcnt: float = 0
        for (n, col, lab) in spec:
            totcnt[n] = 0.0
            totd[n] = []
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
                ret.append(
                    dict(
                        data=totd[n],
                        color=col,
                        label=None,
                        yaxis=2,
                        bars={"show": False},
                        lines=dict(show=True),
                        stack=-n,
                    )
                )
        return (ret, alltot)

    def _added(self, num: Optional[int] = 7, chunk: int = 1) -> Any:
        lims = []
        if num is not None:
            lims.append(
                "id > %d" % ((self.col.sched.dayCutoff - (num * chunk * 86400)) * 1000)
            )
        lims.append("did in %s" % self._limit())
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        if self.type == PERIOD_MONTH:
            tf = 60.0  # minutes
        else:
            tf = 3600.0  # hours
        return self.col.db.all(
            """
select
(cast((id/1000.0 - ?) / 86400.0 as int))/? as day,
count(id)
from cards %s
group by day order by day"""
            % lim,
            self.col.sched.dayCutoff,
            chunk,
        )

    def _done(self, num: Optional[int] = 7, chunk: int = 1) -> Any:
        lims = []
        if num is not None:
            lims.append(
                "id > %d" % ((self.col.sched.dayCutoff - (num * chunk * 86400)) * 1000)
            )
        lim = self._revlogLimit()
        if lim:
            lims.append(lim)
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        if self.type == PERIOD_MONTH:
            tf = 60.0  # minutes
        else:
            tf = 3600.0  # hours
        return self.col.db.all(
            f"""
select
(cast((id/1000.0 - ?) / 86400.0 as int))/? as day,
sum(case when type = {REVLOG_LRN} then 1 else 0 end), -- lrn count
sum(case when type = {REVLOG_REV} and lastIvl < 21 then 1 else 0 end), -- yng count
sum(case when type = {REVLOG_REV} and lastIvl >= 21 then 1 else 0 end), -- mtr count
sum(case when type = {REVLOG_RELRN} then 1 else 0 end), -- lapse count
sum(case when type = {REVLOG_CRAM} then 1 else 0 end), -- cram count
sum(case when type = {REVLOG_LRN} then time/1000.0 else 0 end)/?, -- lrn time
-- yng + mtr time
sum(case when type = {REVLOG_REV} and lastIvl < 21 then time/1000.0 else 0 end)/?,
sum(case when type = {REVLOG_REV} and lastIvl >= 21 then time/1000.0 else 0 end)/?,
sum(case when type = {REVLOG_RELRN} then time/1000.0 else 0 end)/?, -- lapse time
sum(case when type = {REVLOG_CRAM} then time/1000.0 else 0 end)/? -- cram time
from revlog %s
group by day order by day"""
            % lim,
            self.col.sched.dayCutoff,
            chunk,
            tf,
            tf,
            tf,
            tf,
            tf,
        )

    def _daysStudied(self) -> Any:
        lims = []
        num = self._periodDays()
        if num:
            lims.append("id > %d" % ((self.col.sched.dayCutoff - (num * 86400)) * 1000))
        rlim = self._revlogLimit()
        if rlim:
            lims.append(rlim)
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        ret = self.col.db.first(
            """
select count(), abs(min(day)) from (select
(cast((id/1000 - ?) / 86400.0 as int)+1) as day
from revlog %s
group by day order by day)"""
            % lim,
            self.col.sched.dayCutoff,
        )
        assert ret
        return ret

    # Intervals
    ######################################################################

    def ivlGraph(self) -> str:
        (ivls, all, avg, max_), chunk = self._ivls()
        tot = 0
        totd = []
        if not ivls or not all:
            return ""
        for (grp, cnt) in ivls:
            tot += cnt
            totd.append((grp, tot / float(all) * 100))
        if self.type == PERIOD_MONTH:
            ivlmax = 31
        elif self.type == PERIOD_YEAR:
            ivlmax = 52
        else:
            ivlmax = max(5, ivls[-1][0])
        txt = self._title("Intervals", "Delays until reviews are shown again.")
        txt += self._graph(
            id="ivl",
            ylabel2="Percentage",
            xunit=chunk,
            data=[
                dict(data=ivls, color=colIvl),
                dict(
                    data=totd,
                    color=colCum,
                    yaxis=2,
                    bars={"show": False},
                    lines=dict(show=True),
                    stack=False,
                ),
            ],
            conf=dict(
                xaxis=dict(min=-0.5, max=ivlmax + 0.5),
                yaxes=[dict(), dict(position="right", max=105)],
            ),
        )
        i: List[str] = []
        self._line(i, "Average interval", self.col.format_timespan(avg * 86400))
        self._line(i, "Longest interval", self.col.format_timespan(max_ * 86400))
        return txt + self._lineTbl(i)

    def _ivls(self) -> Tuple[List[Any], int]:
        start, end, chunk = self.get_start_end_chunk()
        lim = "and grp <= %d" % end if end else ""
        data = [
            self.col.db.all(
                f"""
select ivl / ? as grp, count() from cards
where did in %s and queue = {QUEUE_TYPE_REV} %s
group by grp
order by grp"""
                % (self._limit(), lim),
                chunk,
            )
        ]
        return (
            data
            + list(
                self.col.db.first(
                    f"""
select count(), avg(ivl), max(ivl) from cards where did in %s and queue = {QUEUE_TYPE_REV}"""
                    % self._limit()
                )
            ),
            chunk,
        )

    # Eases
    ######################################################################

    def easeGraph(self) -> str:
        # 3 + 4 + 4 + spaces on sides and middle = 15
        # yng starts at 1+3+1 = 5
        # mtr starts at 5+4+1 = 10
        d: Dict[str, List] = {"lrn": [], "yng": [], "mtr": []}
        types = ("lrn", "yng", "mtr")
        eases = self._eases()
        for (type, ease, cnt) in eases:
            if type == CARD_TYPE_LRN:
                ease += 5
            elif type == CARD_TYPE_REV:
                ease += 10
            n = types[type]
            d[n].append((ease, cnt))
        ticks = [
            [1, 1],
            [2, 2],
            [3, 3],  # [4,4]
            [6, 1],
            [7, 2],
            [8, 3],
            [9, 4],
            [11, 1],
            [12, 2],
            [13, 3],
            [14, 4],
        ]
        if self.col.schedVer() != 1:
            ticks.insert(3, [4, 4])
        txt = self._title(
            "Answer Buttons", "The number of times you have pressed each button."
        )
        txt += self._graph(
            id="ease",
            data=[
                dict(data=d["lrn"], color=colLearn, label="Learning"),
                dict(data=d["yng"], color=colYoung, label="Young"),
                dict(data=d["mtr"], color=colMature, label="Mature"),
            ],
            type="bars",
            conf=dict(xaxis=dict(ticks=ticks, min=0, max=15)),
            ylabel="Answers",
        )
        txt += self._easeInfo(eases)
        return txt

    def _easeInfo(self, eases: List[Tuple[int, int, int]]) -> str:
        types = {PERIOD_MONTH: [0, 0], PERIOD_YEAR: [0, 0], PERIOD_LIFE: [0, 0]}
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
            i.append(
                "Correct: <b>%(pct)0.2f%%</b><br>(%(good)d of %(tot)d)"
                % dict(pct=pct, good=good, tot=tot)
            )
        return (
            """
<center><table width=%dpx><tr><td width=50></td><td align=center>"""
            % self.width
            + "</td><td align=center>".join(i)
            + "</td></tr></table></center>"
        )

    def _eases(self) -> Any:
        lims = []
        lim = self._revlogLimit()
        if lim:
            lims.append(lim)
        days = self._periodDays()
        if days is not None:
            lims.append(
                "id > %d" % ((self.col.sched.dayCutoff - (days * 86400)) * 1000)
            )
        if lims:
            lim = "where " + " and ".join(lims)
        else:
            lim = ""
        if self.col.schedVer() == 1:
            ease4repl = "3"
        else:
            ease4repl = "ease"
        return self.col.db.all(
            f"""
select (case
when type in ({REVLOG_LRN},{REVLOG_RELRN}) then 0
when lastIvl < 21 then 1
else 2 end) as thetype,
(case when type in ({REVLOG_LRN},{REVLOG_RELRN}) and ease = 4 then %s else ease end), count() from revlog %s
group by thetype, ease
order by thetype, ease"""
            % (ease4repl, lim)
        )

    # Hourly retention
    ######################################################################

    def hourGraph(self) -> str:
        data = self._hourRet()
        if not data:
            return ""
        shifted = []
        counts = []
        mcount = 0
        trend: List[Tuple[int, int]] = []
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
                diff = pct - prev
                diff /= 3.0
                diff = round(diff, 1)
                trend.append((hour, prev + diff))
        txt = self._title(
            "Hourly Breakdown", "Review success rate for each hour of the day."
        )
        txt += self._graph(
            id="hour",
            data=[
                dict(data=shifted, color=colCum, label="% Correct"),
                dict(
                    data=counts,
                    color=colHour,
                    label="Answers",
                    yaxis=2,
                    bars=dict(barWidth=0.2),
                    stack=False,
                ),
            ],
            conf=dict(
                xaxis=dict(
                    ticks=[
                        [0, "4AM"],
                        [6, "10AM"],
                        [12, "4PM"],
                        [18, "10PM"],
                        [23, "3AM"],
                    ]
                ),
                yaxes=[dict(max=peak), dict(position="right", max=mcount)],
            ),
            ylabel="% Correct",
            ylabel2="Reviews",
        )
        txt += "Hours with less than 30 reviews are not shown."
        return txt

    def _hourRet(self) -> Any:
        lim = self._revlogLimit()
        if lim:
            lim = " and " + lim
        if self.col.schedVer() == 1:
            sd = datetime.datetime.fromtimestamp(self.col.crt)
            rolloverHour = sd.hour
        else:
            rolloverHour = self.col.conf.get("rollover", 4)
        pd = self._periodDays()
        if pd:
            lim += " and id > %d" % ((self.col.sched.dayCutoff - (86400 * pd)) * 1000)
        return self.col.db.all(
            f"""
select
23 - ((cast((? - id/1000) / 3600.0 as int)) %% 24) as hour,
sum(case when ease = 1 then 0 else 1 end) /
cast(count() as float) * 100,
count()
from revlog where type in ({REVLOG_LRN},{REVLOG_REV},{REVLOG_RELRN}) %s
group by hour having count() > 30 order by hour"""
            % lim,
            self.col.sched.dayCutoff - (rolloverHour * 3600),
        )

    # Cards
    ######################################################################

    def cardGraph(self) -> str:
        # graph data
        div = self._cards()
        d = []
        for c, (t, col) in enumerate(
            (
                ("Mature", colMature),
                ("Young+Learn", colYoung),
                ("Unseen", colUnseen),
                ("Suspended+Buried", colSusp),
            )
        ):
            d.append(dict(data=div[c], label="%s: %s" % (t, div[c]), color=col))
        # text data
        i: List[str] = []
        (c, f) = self.col.db.first(
            """
select count(id), count(distinct nid) from cards
where did in %s """
            % self._limit()
        )
        self._line(i, "Total cards", c)
        self._line(i, "Total notes", f)
        (low, avg, high) = self._factors()
        if low:
            self._line(i, "Lowest ease", "%d%%" % low)
            self._line(i, "Average ease", "%d%%" % avg)
            self._line(i, "Highest ease", "%d%%" % high)
        info = "<table width=100%>" + "".join(i) + "</table><p>"
        info += """\
A card's <i>ease</i> is the size of the next interval \
when you answer "good" on a review."""
        txt = self._title("Card Types", "The division of cards in your deck(s).")
        txt += "<table width=%d><tr><td>%s</td><td>%s</td></table>" % (
            self.width,
            self._graph(id="cards", data=d, type="pie"),
            info,
        )
        return txt

    def _line(
        self, i: List[str], a: str, b: Union[int, str], bold: bool = True
    ) -> None:
        # T: Symbols separating first and second column in a statistics table. Eg in "Total:    3 reviews".
        colon = ":"
        if bold:
            i.append(
                ("<tr><td width=200 align=right>%s%s</td><td><b>%s</b></td></tr>")
                % (a, colon, b)
            )
        else:
            i.append(
                ("<tr><td width=200 align=right>%s%s</td><td>%s</td></tr>")
                % (a, colon, b)
            )

    def _lineTbl(self, i: List[str]) -> str:
        return "<table width=400>" + "".join(i) + "</table>"

    def _factors(self) -> Any:
        return self.col.db.first(
            f"""
select
min(factor) / 10.0,
avg(factor) / 10.0,
max(factor) / 10.0
from cards where did in %s and queue = {QUEUE_TYPE_REV}"""
            % self._limit()
        )

    def _cards(self) -> Any:
        return self.col.db.first(
            f"""
select
sum(case when queue={QUEUE_TYPE_REV} and ivl >= 21 then 1 else 0 end), -- mtr
sum(case when queue in ({QUEUE_TYPE_LRN},{QUEUE_TYPE_DAY_LEARN_RELEARN}) or (queue={QUEUE_TYPE_REV} and ivl < 21) then 1 else 0 end), -- yng/lrn
sum(case when queue={QUEUE_TYPE_NEW} then 1 else 0 end), -- new
sum(case when queue<{QUEUE_TYPE_NEW} then 1 else 0 end) -- susp
from cards where did in %s"""
            % self._limit()
        )

    # Footer
    ######################################################################

    def footer(self) -> str:
        b = "<br><br><font size=1>"
        b += "Generated on %s" % time.asctime(time.localtime(time.time()))
        b += "<br>"
        if self.wholeCollection:
            deck = "whole collection"
        else:
            deck = self.col.decks.current()["name"]
        b += "Scope: %s" % deck
        b += "<br>"
        b += "Period: %s" % ["1 month", "1 year", "deck life"][self.type]
        return b

    # Tools
    ######################################################################

    def _graph(
        self,
        id: str,
        data: Any,
        conf: Optional[Any] = None,
        type: str = "bars",
        xunit: int = 1,
        ylabel: str = "Cards",
        ylabel2: str = "",
    ) -> str:
        if conf is None:
            conf = {}
        # display settings
        if type == "pie":
            conf["legend"] = {"container": "#%sLegend" % id, "noColumns": 2}
        else:
            conf["legend"] = {"container": "#%sLegend" % id, "noColumns": 10}
        conf["series"] = dict(stack=True)
        if not "yaxis" in conf:
            conf["yaxis"] = {}
        conf["yaxis"]["labelWidth"] = 40
        if "xaxis" not in conf:
            conf["xaxis"] = {}
        if xunit is None:
            conf["timeTicks"] = False
        else:
            # T: abbreviation of day
            d = "d"
            # T: abbreviation of week
            w = "w"
            # T: abbreviation of month
            mo = "mo"
            conf["timeTicks"] = {1: d, 7: w, 31: mo}[xunit]
        # types
        width = self.width
        height = self.height
        if type == "bars":
            conf["series"]["bars"] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=0
            )  # pytype: disable=unsupported-operands
        elif type == "barsLine":
            print("deprecated - use 'bars' instead")
            conf["series"]["bars"] = dict(
                show=True, barWidth=0.8, align="center", fill=0.7, lineWidth=3
            )
        elif type == "fill":
            conf["series"]["lines"] = dict(show=True, fill=True)
        elif type == "pie":
            width = int(float(width) / 2.3)
            height = int(float(height) * 1.5)
            ylabel = ""
            conf["series"]["pie"] = dict(
                show=True,
                radius=1,
                stroke=dict(color="#fff", width=5),
                label=dict(
                    show=True,
                    radius=0.8,
                    threshold=0.01,
                    background=dict(opacity=0.5, color="#000"),
                ),
            )
        return """
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
    // prevent ticks from having decimals (use whole numbers instead)
    conf.yaxis.tickDecimals = 0;
    conf.yaxis.tickFormatter = function (val, axis) {
            // Just in case we get ticks with decimals, render to one decimal position.  If it's
            // a whole number then render without any decimal (i.e. without the trailing .0).
            return val === Math.round(val) ? val.toFixed(0) : val.toFixed(1);
    }
    if (conf.series.pie) {
        conf.series.pie.label.formatter = function(label, series){
            return '<div class=pielabel>'+Math.round(series.percent)+'%%</div>';
        };
    }
    $.plot($("#%(id)s"), %(data)s, conf);
});
</script>""" % dict(
            id=id,
            w=width,
            h=height,
            ylab=ylabel,
            ylab2=ylabel2,
            data=json.dumps(data),
            conf=json.dumps(conf),
        )

    def _limit(self) -> Any:
        if self.wholeCollection:
            return ids2str([d["id"] for d in self.col.decks.all()])
        return self.col.sched._deckLimit()

    def _revlogLimit(self) -> str:
        if self.wholeCollection:
            return ""
        return "cid in (select id from cards where did in %s)" % ids2str(
            self.col.decks.active()
        )

    def _title(self, title: str, subtitle: str = "") -> str:
        return "<h1>%s</h1>%s" % (title, subtitle)

    def _deckAge(self, by: str) -> int:
        lim = self._revlogLimit()
        if lim:
            lim = " where " + lim
        if by == "review":
            t = self.col.db.scalar("select id from revlog %s order by id limit 1" % lim)
        elif by == "add":
            if self.wholeCollection:
                lim = ""
            else:
                lim = "where did in %s" % ids2str(self.col.decks.active())
            t = self.col.db.scalar("select id from cards %s order by id limit 1" % lim)
        if not t:
            period = 1
        else:
            period = max(1, int(1 + ((self.col.sched.dayCutoff - (t / 1000)) / 86400)))
        return period

    def _periodDays(self) -> Optional[int]:
        start, end, chunk = self.get_start_end_chunk()
        if end is None:
            return None
        return end * chunk

    def _avgDay(self, tot: float, num: int, unit: str) -> str:
        vals = []
        try:
            vals.append("%(a)0.1f %(b)s/day" % dict(a=tot / float(num), b=unit))
            return ", ".join(vals)
        except ZeroDivisionError:
            return ""
