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
            self.mw.col.reset()
            self.mw.moveToState("review")
        elif url == "anki":
            print "anki menu"
        elif url == "cram":
            return showInfo("not yet implemented")
            #self.mw.col.cramGroups()
            #self.mw.moveToState("review")
        elif url == "opts":
            self.mw.onStudyOptions()
        elif url == "decks":
            self.mw.moveToState("deckBrowser")

    # HTML
    ############################################################

    def _renderPage(self):
        css = self.mw.sharedCSS + self._overviewCSS
        fc = self._ovForecast()
        tbl = self._overviewTable()
        but = self.mw.button
        deck = self.mw.col.decks.current()
        sid = deck.get("sharedFrom")
        if True: # sid:
            shareLink = '<a class=smallLink href="review">Reviews and Updates</a>'
        else:
            shareLink = ""
        header = """
<table id=header width=100%%>
<tr>
<td width=20%>
<a class="hitem" href="anki">Anki &#9662</a>
</td>
<td align=center>
<a class=hitem href="decks">Decks</a>
<a class=hitem href="study">Study</a>
<a class=hitem href="add">Add</a>
<a class=hitem href="browse">Browse</a>
</td>
<td width=20% align=right>
<a class=hitem href="stats"><img src="qrc:/icons/view-statistics.png"></a>
<a class=hitem href="sync"><img src="qrc:/icons/view-refresh.png"></a>
</td></tr></table>
<div id=headerSpace></div>
""" #% deck['name']

        self.web.stdHtml(self._overviewBody % dict(
            title=_("Overview"),
            table=tbl,
            fcsub=_("Reviews over next two weeks"),
            deck=deck['name'],
            shareLink=shareLink,
            desc="",
            header=header,
            fcdata=fc,
            ), css)

    _overviewBody = """
%(header)s
<center>
<h3>%(deck)s</h3>
%(shareLink)s
%(desc)s
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
.smallLink { font-size: 12px; }
h3 { margin-bottom: 0; }
#headerSpace { height: 22px; }
#header {
z-index: 100;
position: fixed;
height: 22px;
font-size: 12px;
margin:0;
background: -webkit-gradient(linear, left top, left bottom,
from(#ddd), to(#fff));
border-bottom: 1px solid #ccc;
font-weight: bold;
}
body { margin: 0; }
.deck { }
.hitem { display: inline-block; padding: 4px; padding-right: 6px;
text-decoration: none; color: #000;
}
.hborder { border: 1px solid #ddd; }
.hitem:hover {
background: #333;
color: #fff;
}
.icon { padding-top: 2px; }
"""

    def _overviewTable(self):
        return ""
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

    def _ovForecast(self):
        fc = self.mw.col.sched.dueForecast(14)
        if not sum(fc):
            return "'%s'" % _('No cards due in next two weeks')
        return simplejson.dumps(tuple(enumerate(fc)))
