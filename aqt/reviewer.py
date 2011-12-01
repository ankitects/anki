# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, stat, shutil, difflib, simplejson, re
import unicodedata as ucd
from aqt.qt import *
from anki.utils import fmtTimeSpan, stripHTML
from anki.hooks import addHook, runHook, runFilter
from anki.sound import playFromText, clearAudioQueue, hasSound
from aqt.utils import mungeQA, getBase
import aqt

class Reviewer(object):
    "Manage reviews.  Maintains a separate state."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.card = None
        self.cardQueue = []
        self._answeredIds = []
        self.state = None
        self.keep = False
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)
        addHook("leech", self.onLeech)

    def show(self):
        self.web.setKeyHandler(self._keyHandler)
        self.web.setLinkHandler(self._linkHandler)
        if self.keep:
            self._initWeb()
        else:
            self.nextCard()
        self.keep = False
        self.bottom.web.setFixedHeight(46)
        self.bottom.web.setLinkHandler(self._linkHandler)

    def lastCard(self):
        if self._answeredIds:
            if not self.card or self._answeredIds[-1] != self.card.id:
                return self.mw.col.getCard(self._answeredIds[-1])

    def cleanup(self):
        runHook("reviewCleanup")

    # Fetching a card
    ##########################################################################

    def nextCard(self):
        if self.cardQueue:
            # a card has been retrieved from undo
            c = self.cardQueue.pop()
        else:
            c = self.mw.col.sched.getCard()
        self.card = c
        clearAudioQueue()
        if c:
            #self.updateMarkAction()
            self._initWeb()
        else:
            self.mw.moveToState("overview")

    # Audio
    ##########################################################################

    def replayAudio(self):
        clearAudioQueue()
        c = self.card
        if not c.template()['hideQ'] or self.state == "question":
            playFromText(c.q())
        if self.state == "answer":
            playFromText(c.a())

    # Initializing the webview
    ##########################################################################

    _revHtml = """
<div id=qa></div>
<script>
var ankiPlatform = "desktop";
var hideq;
var ans;
var typeans;
function _updateQA (q, answerMode) {
    $("#qa").html(q);
    typeans = document.getElementById("typeans");
    if (typeans) {
        typeans.focus();
    }
    if (answerMode) {
        window.location = "#answerStart";
    } else {
        window.location = "";
    }
};
function _showans (a) {
    $("#qa").html(a);
    if (typeans) {
        py.link("typeans:"+typeans.value);
    }
    $(".inv").removeClass('inv');
    if (hideq) {
        $("#q").html(ans);
        $("#midhr").addClass("inv");
    } else {
        location.hash = "a";
    }
    //$("#ansbut").hide();
    $("#defease").focus();
};
function _processTyped (res) {
    $("#typeans").replaceWith(res);
}
function _onSpace() {
    if (/^ease/.test(document.activeElement.href)) {
        py.link(document.activeElement.href);
    }
}
function _typeAnsPress() {
    if (window.event.keyCode === 13) {
        _showans();
    }
}
</script>
"""

    def _initWeb(self):
        base = getBase(self.mw.col)
        self.web.stdHtml(self._revHtml, self._styles(),
            bodyID="card", loadCB=lambda x: self._showQuestion(),
            head=base)

    # Showing the question
    ##########################################################################

    def _mungeQA(self, buf):
        return self.mw.col.media.escapeImages(
            self.prepareTypeAns(mungeQA(buf)))

    def _showQuestion(self):
        self.state = "question"
        c = self.card
        # mod the card so it shows up in the recently modified list
        c.flush()
        # grab the question and play audio
        q = c.q()
        if self.mw.pm.profile['autoplay']:
            playFromText(q)
        # render & update bottom
        q = self._mungeQA(q)
        self.web.eval("_updateQA(%s);" % simplejson.dumps(q))
        self._showAnswerButton()
        # user hook
        runHook('showQuestion')

    # Showing the answer
    ##########################################################################

    def _showAnswer(self):
        self.state = "answer"
        c = self.card
        a = c.a()
        # play audio?
        if self.mw.pm.profile['autoplay']:
            playFromText(a)
        # render and update bottom
        a = self._mungeQA(a)
        self.web.eval("_updateQA(%s, true);" % simplejson.dumps(a))
        self._showEaseButtons()
        # user hook
        runHook('showAnswer')

    # Answering a card
    ############################################################

    def _answerCard(self, ease):
        "Reschedule card and show next."
        self.mw.col.sched.answerCard(self.card, ease)
        self._answeredIds.append(self.card.id)
        self.mw.autosave()
        self.nextCard()

    # Handlers
    ############################################################

    def _keyHandler(self, evt):
        if self.state == "question":
            show = False
            if evt.key() == Qt.Key_Space and self.typeAns() is None:
                show = True
            elif evt.key() == Qt.Key_Escape:
                self.web.eval("$('#typeans').blur();")
            if show:
                self._showAnswer()
                self.web.eval("_showans();")
                return True
        elif self.state == "answer":
            if evt.key() == Qt.Key_Space:
                self.web.eval("_onSpace();")
            else:
                key = unicode(evt.text())
                if key and key >= "1" and key <= "4":
                    key=int(key)
                    if self.card.queue == 2 or key < 4:
                        self._answerCard(key)
                        return True

    def _linkHandler(self, url):
        if url == "ans":
            print "show ans"
            self._showAnswer()
        elif url.startswith("ease"):
            self._answerCard(int(url[4:]))
        elif url == "add":
            self.mw.onAddCard()
        elif url == "dlist":
            self.mw.close()
        elif url == "ov":
            self.mw.moveToState("overview")
        elif url.startswith("typeans:"):
            (cmd, arg) = url.split(":")
            self.processTypedAns(arg)
        else:
            QDesktopServices.openUrl(QUrl(url))

    # CSS
    ##########################################################################

    _css = """
.ansbut {
    -webkit-box-shadow: 2px 2px 6px rgba(0,0,0,0.6);
    -webkit-user-drag: none;
    -webkit-user-select: none;
    background-color: #ddd;
    border-radius: 5px;
    border: 1px solid #aaa;
    color: #000;
    display: inline-block;
    font-size: 80%;
    margin: 0 5 0 5;
    padding: 3;
    text-decoration: none;
    text-align: center;
}
.but:focus, .but:hover { background-color: #aaa; }
.ansbutbig {
    bottom: 1em;
    height: 40px;
    left: 50%;
    margin-left: -125px !important;
    position: fixed;
    width: 250px;
    font-size: 100%;
}
.ansbut:focus {
font-weight: bold;
}
div.ansbuttxt {
  position: relative; top: 25%;
}

body { margin:1.5em; }

#easebuts {
  bottom: 1em;
  height: 47px;
  left: 50%;
  margin-left: -200px;
  position: fixed;
  width: 400px;
  font-size: 100%;
}

.easebut {
  width: 60px;
  font-size: 100%;
}

.time {
  background: #eee;
  padding: 5px;
  border-radius: 10px;
}

div#filler {
  height: 30px;
}

.q { margin-bottom: 1em; }
.a { margin-top: 1em; }
.inv { visibility: hidden; }

.cloze { font-weight: bold; color: blue; }
"""

    def _styles(self):
        css = self.mw.sharedCSS
        css += self._css
        return css

    # Type in the answer
    ##########################################################################

    failedCharColour = "#FF0000"
    passedCharColour = "#00FF00"

    def prepareTypeAns(self, buf):
        self.typeField = None
        pat = "\[\[type:(.+?)\]\]"
        m = re.search(pat, buf)
        if not m:
            return buf
        fld = m.group(1)
        print "got", fld
        fobj = None
        for f in self.card.model()['flds']:
            if f['name'] == fld:
                fobj = f
                break
        if not fobj:
            return re.sub(pat, _("Type answer: unknown field %s") % fld, buf)
        self.typeField = fobj
        return re.sub(pat, """
<center>
<input type=text id=typeans onkeypress="_typeAnsPress();"
   style="font-family: '%s'; font-size: %s;">
</center>
""" % (fobj['font'], fobj['size']), buf)

    def processTypedAns(self, given):
        ord = self.typeAns()
        try:
            cor = self.mw.col.media.strip(
                stripHTML(self.card.note().fields[ord]))
        except IndexError:
            self.card.template()['typeAns'] = None
            self.card.model().flush()
            cor = ""
        if cor:
            res = self.correct(cor, given)
            self.web.eval("_processTyped(%s);" % simplejson.dumps(res))

    def getFont(self):
        print "fix getFont()"
        return ("arial", 20)
        f = self.card.model().fields[self.typeAns()]
        return (f['font'], f['qsize'])

    def calculateOkBadStyle(self):
        "Precalculates styles for correct and incorrect part of answer"
        (fn, sz) = self.getFont()
        st = "background: %s; color: #000; font-size: %dpx; font-family: %s;"
        self.styleOk  = st % (self.passedCharColour, sz, fn)
        self.styleBad = st % (self.failedCharColour, sz, fn)

    def ok(self, a):
        "returns given sring in style correct (green)"
        if len(a) == 0:
            return ""
        return "<span style='%s'>%s</span>" % (self.styleOk, a)

    def bad(self, a):
        "returns given sring in style incorrect (red)"
        if len(a) == 0:
            return ""
        return "<span style='%s'>%s</span>" % (self.styleBad, a)

    def applyStyle(self, testChar, correct, wrong):
        "Calculates answer fragment depending on testChar's unicode category"
        ZERO_SIZE = 'Mn'
        def head(a):
            return a[:len(a) - 1]
        def tail(a):
            return a[len(a) - 1:]
        if ucd.category(testChar) == ZERO_SIZE:
            return self.ok(head(correct)) + self.bad(tail(correct) + wrong)
        return self.ok(correct) + self.bad(wrong)

    def correct(self, a, b):
        "Diff-corrects the typed-in answer."
        if b == "":
            return "";
        self.calculateOkBadStyle()
        ret = ""
        lastEqual = ""
        s = difflib.SequenceMatcher(None, b, a)
        for tag, i1, i2, j1, j2 in s.get_opcodes():
            if tag == "equal":
                lastEqual = b[i1:i2]
            elif tag == "replace":
                ret += self.applyStyle(b[i1], lastEqual,
                                 b[i1:i2] + ("-" * ((j2 - j1) - (i2 - i1))))
                lastEqual = ""
            elif tag == "delete":
                ret += self.applyStyle(b[i1], lastEqual, b[i1:i2])
                lastEqual = ""
            elif tag == "insert":
                dashNum = (j2 - j1) if ucd.category(a[j1]) != 'Mn' else ((j2 - j1) - 1)
                ret += self.applyStyle(a[j1], lastEqual, "-" * dashNum)
                lastEqual = ""
        return ret + self.ok(lastEqual)

    # Bottom bar
    ##########################################################################

    _bottomCSS = """
body {
background: -webkit-gradient(linear, left top, left bottom,
from(#fff), to(#ddd));
border-bottom: 0;
border-top: 1px solid #aaa;
margin: 0;
padding: 0px;
padding-left: 5px; padding-right: 5px;
}
td { font-weight: bold; font-size: 12px; }
.hitem { margin-top: 2px; }
.stat { padding-top: 5px; }
.stattxt { padding-left: 5px; padding-right: 5px; }
.nobold { font-weight: normal; display: inline-block; padding-top: 3px; }
.spacer { height: 18px; }
.spacer2 { height: 16px; }
button { font-weight: normal; }
"""

    def _bottomHTML(self, middle):
        return """
<table width=100%% cellspacing=0 cellpadding=0>
<tr>
<td align=left width=50 valign=top class=stat><span class=stattxt>1 + 7 + 3</span><br>
<button>Edit Note</button></td>
<td align=center valign=top>
%(middle)s
</td>
<td width=50 align=right valign=top class=stat><span class=stattxt>0:53</span><br>
<button>More &#9662;</button>
</td>
</tr>
</table>
<script>$(function () { $("#ansbut").focus(); });</script>
""" % dict(middle=middle)

    def _showAnswerButton(self):
        self.bottom.web.setFocus()
        middle = '''
<div class=spacer2></div>
<button id=ansbut onclick='py.link(\"ans\");'>%s</button>''' % _("Show Answer")
        # wrap it in a table so it has the same top margin as the ease buttons
        middle = "<table cellpadding=0><tr><td>%s</td></tr></table>" % middle
        self.bottom.web.stdHtml(
            self._bottomHTML(middle),
            self.bottom._css + self._bottomCSS)

    def _showEaseButtons(self):
        print self._answerButtons()
        self.bottom.web.stdHtml(
            self._bottomHTML(self._answerButtons()),
            self.bottom._css + self._bottomCSS)

    def _defaultEase(self):
        if self.mw.col.sched.answerButtons(self.card) == 4:
            return 3
        else:
            return 2

    def _answerButtons(self):
        if self.mw.col.sched.answerButtons(self.card) == 4:
            labels = (_("Again"), _("Hard"), _("Good"), _("Easy"))
        else:
            labels = (_("Again"), _("Good"), _("Easy"))
        times = []
        buttons = []
        default = self._defaultEase()
        def but(label, i):
            if i == default:
                extra = "id=defease"
            else:
                extra = ""
            due = self._buttonTime(i-1, default-1)
            return '''
<td align=center>%s<button %s onclick='py.link("ease%d");'>\
%s</button></td>''' % (due, extra, i, label)
        buf = "<center><table cellpading=0 cellspacing=0><tr>"
        for i in range(0, len(labels)):
            buf += but(labels[i], i+1)
        buf += "</tr></table>"
        script = """
<script>$(function () { $("#defease").focus(); });</script>"""
        return buf + script

    def _buttonTime(self, i, green):
        if not self.mw.pm.profile['showDueTimes']:
            return "<div class=spacer></div>"
        txt = self.mw.col.sched.nextIvlStr(self.card, i+1, True)
        return '<span class=nobold>%s</span><br>' % txt

    # Status bar
    ##########################################################################

    def _remaining(self):
        counts = list(self.mw.col.sched.repCounts())
        idx = self.mw.col.sched.countIdx(self.card)
        counts[idx] = "<u>%s</u>" % (counts[idx]+1)
        space = "&nbsp;" * 2
        ctxt = '<font color="#000099">%s</font>' % counts[0]
        ctxt += space + '<font color="#990000">%s</font>' % counts[1]
        ctxt += space + '<font color="#007700">%s</font>' % counts[2]
        return ctxt

    # Leeches
    ##########################################################################

    # fixme: update; clear on card transition
    def onLeech(self, card):
        print "leech"
        return
        link = aqt.appHelpSite + "Leeches.html"
        txt = (_("""\
Card was a <a href="%s">leech</a>.""") % link)
        if isLeech and self.deck.db.scalar(
            "select 1 from cards where id = :id and type < 0", id=cardId):
            txt += _(" It has been suspended.")
        self.setNotice(txt)
