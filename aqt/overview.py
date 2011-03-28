# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import simplejson
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki.consts import NEW_CARDS_RANDOM
from anki.hooks import addHook

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
        print "refreshing"
        self._renderPage()

    # Handlers
    ############################################################

    def _keyHandler(self, evt):
        txt = evt.text()
        if txt == "s":
            self._linkHandler("study")
        elif txt == "c":
            self._linkHandler("cram")
        elif txt == "d":
            self._linkHandler("list")
        elif txt == "g":
            self._linkHandler("chgrp")
        else:
            return
        return True

    def _linkHandler(self, url):
        print "link", url
        if url == "study":
            self.mw.deck.reset()
            self.mw.moveToState("review")
        elif url == "cram":
            self.mw.deck.cramGroups(self.mw.deck.qconf['revGroups'])
            self.mw.moveToState("review")
        elif url == "opts":
            self.mw.onStudyOptions()
        elif url == "list":
            self.mw.close()
        elif url == "chgrp":
            print "change groups"

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
            opts=self._ovOpts(),
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
%(opts)s
</center>

<script>
$("#study").focus();
$(function () {
    var d = %(fcdata)s;
    if (d) {
    $.plot($("#placeholder"), [
    { data: d, bars: { show: true, barWidth: 0.8 }, color: "#0c0" }
    ], {
    xaxis: { ticks: [[0.4, "Today"]] }
    });
    } else {
    $("#placeholder").hide();
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
#         if self.mw.deck.qconf['newCardOrder'] == NEW_CARDS_RANDOM:
#             ord = _("random")
#         else:
#             ord = _("order added")
#         buf = """
# <table width=400>
# <tr><td><b>%s</b></td><td align=center>%s</td></tr>
# <tr><td><b>%s</b></td><td align=center>%s</td></tr>
# </table>""" % (
#     _("New cards per day"), self.mw.deck.qconf['newPerDay'],
#     _("New card order"), ord)
#         return buf

    # Data
    ##########################################################################

    def _ovCounts(self):
        oldNew = self.mw.deck.qconf['newGroups']
        oldRev = self.mw.deck.qconf['revGroups']
        # we have the limited count already
        selcnt = self.mw.deck.sched.selCounts()
        allcnt = self.mw.deck.sched.allCounts()
        return [
            selcnt[1] + selcnt[2],
            selcnt[0],
            allcnt[1] + allcnt[2],
            allcnt[0],
        ]

    def _ovForecast(self):
        fc = self.mw.deck.sched.dueForecast(14)
        if not sum(fc):
            return "''"
        return simplejson.dumps(tuple(enumerate(fc)))

    # Toolbar
    ##########################################################################

    def _setupToolbar(self):
        if not self.mw.config['showToolbar']:
            return
        self.mw.form.toolBar.show()
