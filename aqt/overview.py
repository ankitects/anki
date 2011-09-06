# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from aqt.qt import *
from anki.consts import NEW_CARDS_RANDOM
from anki.hooks import addHook
from aqt.utils import limitedCount, showInfo

class Overview(object):
    "Deck overview."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        addHook("reset", self.refresh)

    def show(self):
        self._setupToolbar()
        self.web.setKeyHandler(self._keyHandler)
        self.web.setLinkHandler(self._linkHandler)
        self.refresh()

    def refresh(self):
        self._renderPage()

        # Handlers
    ############################################################

    def _keyHandler(self, evt):
        txt = evt.text()
        if txt == "s":
            self._linkHandler("study")
        elif txt == "c":
            self._linkHandler("cram")
        else:
            return
        return True

    def _linkHandler(self, url):
        print "link", url
        if url == "study":
            self.mw.deck.reset()
            self.mw.moveToState("review")
        elif url == "cram":
            return showInfo("not yet implemented")
            #self.mw.deck.cramGroups()
            #self.mw.moveToState("review")
        elif url == "opts":
            self.mw.onStudyOptions()
        elif url == "chgrp":
            self.mw.onGroups()

    # HTML
    ############################################################

    def _renderPage(self):
        css = self.mw.sharedCSS + self._overviewCSS
        fc = self._ovForecast()
        tbl = self._overviewTable()
        but = self.mw.button
        self.web.stdHtml(self._overviewBody % dict(
            title=_("Overview"),
            table=tbl,
            fcsub=_("Reviews over next two weeks"),
            fcdata=fc,
            ), css)

    _overviewBody = """
<center>
<h1>%(title)s</h1>
<p>
%(table)s
<p>
<div id="placeholder" style="width:350px; height:100px;"></div>
<span class=sub>%(fcsub)s</span>
<p>
</center>

<script>
$("#study").focus();
$(function () {
    var d = %(fcdata)s;
    if (typeof(d) !== "string") {
    $.plot($("#placeholder"), [
    { data: d, bars: { show: true, barWidth: 0.8 }, color: "#0c0" }
    ], {
    xaxis: { ticks: [[0.4, "Today"]] },
    yaxis: { tickDecimals: 0 }
    });
    } else {
    $("#placeholder").text(d);
    $(".sub").hide();
    }
});
</script>
"""

    _overviewCSS = """
.due { text-align: right; }
.new { text-align: right; }
.sub { font-size: 80%; color: #555; }
"""

    def _overviewTable(self):
        counts = self._ovCounts()
        but = self.mw.button
        buf = "<table cellspacing=0 cellpadding=3 width=400>"
        buf += "<tr><th></th><th align=right>%s</th>" % _("Due")
        buf += "<th align=right>%s</th><th></th></tr>" % _("New")
        line = "<tr><td><b>%s</b></td><td class=due>%s</td>"
        line += "<td class=new>%s</td><td>%s</td></tr>"
        buf += line % (
            "<a href=chgrp>%s</a>" % _("Selected Groups"),
            counts[0], counts[1],
            but("study", _("Study"), _("s"), "gbut", id="study") +
            but("cram", _("Cram"), "c"))
        buf += line % (
            _("Whole Deck"),
            counts[2], counts[3],
            but("opts", _("Study Options")))
        buf += "</table>"
        return buf

    def _ovOpts(self):
        return ""

    # Data
    ##########################################################################

    def _ovCounts(self):
        # we have the limited count already
        selcnt = [0,0,0] #self.mw.deck.sched.selCounts()
        allcnt = [0,0,0] #self.mw.deck.sched.allCounts()
        return [
            limitedCount(selcnt[1] + selcnt[2]),
            selcnt[0],
            limitedCount(allcnt[1] + allcnt[2]),
            allcnt[0],
        ]

    def _ovForecast(self):
        fc = self.mw.deck.sched.dueForecast(14)
        if not sum(fc):
            return "'%s'" % _('No cards due in next two weeks')
        return simplejson.dumps(tuple(enumerate(fc)))

    # Toolbar
    ##########################################################################

    def _setupToolbar(self):
        if not self.mw.config['showToolbar']:
            return
        self.mw.form.toolBar.show()
