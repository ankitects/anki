# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, os, stat, shutil
from operator import itemgetter
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki import Deck
from anki.utils import fmtTimeSpan
from anki.hooks import addHook
import aqt
#from aqt.utils import askUser

_css = """
body { background-color: #eee; }
#outer { margin-top: 1em; }
.sub { color: #555; }
hr { margin:5 0 5 0; border:0; height:1px; background-color:#ddd; }
a:hover { background-color: #aaa; }
a { color: #000; text-decoration: none; }
.num { text-align: right; padding: 0 5 0 5; }
td.opts { text-align: right; white-space: nowrap; }
a.opts { font-size: 80%; padding: 2; background-color: #ccc; border-radius: 2px; }
"""

_body = """
<center>
<div id="outer">
<h1>%(title)s</h1>
<table cellspacing=0 cellpadding=0 width=90%%>
%(rows)s
</table>
%(extra)s
</div>
"""

class DeckBrowser(object):

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self._browserLastRefreshed = 0
        self._decks = []
        mw.connect(mw.form.actionRefreshDeckBrowser, SIGNAL("activated()"),
                   self.refresh)
        addHook("deckClosing", self.onClose)

    def show(self, _init=True):
        if _init:
            self.web.setLinkHandler(self._linkHandler)
            self.mw.setKeyHandler(self._keyHandler)
            self._setupToolbar()
        # refresh or reorder
        if (time.time() - self._browserLastRefreshed >
            self.mw.config['deckBrowserRefreshPeriod']):
            t = time.time()
            self._checkDecks()
            print "check decks", time.time() - t
        else:
            self._reorderDecks()
        # show
        self._renderPage()

    def onClose(self, deck):
        print "onClose"
        return
        if deck.finishScheduler:
            self.deck.finishScheduler()
            self.deck.reset()
            # update counts
            for d in self.browserDecks:
                if d['path'] == self.deck.path:
                    d['due'] = self.deck.failedSoonCount + self.deck.revCount
                    d['new'] = self.deck.newCount
                    d['mod'] = self.deck.modified
                    d['time'] = self.deck._dailyStats.reviewTime
                    d['reps'] = self.deck._dailyStats.reps

    # Toolbar
    ##########################################################################

    def _setupToolbar(self):
        frm = self.mw.form
        tb = frm.toolBar
        tb.clear()
        tb.addAction(frm.actionDownloadSharedDeck)
        tb.addAction(frm.actionNew)
        tb.addAction(frm.actionOpen)
        tb.addAction(frm.actionImport)
        tb.addAction(frm.actionOpenOnline)
        tb.addAction(frm.actionRefreshDeckBrowser)
        tb.setToolButtonStyle(Qt.ToolButtonTextUnderIcon)
        # reshow so osx recalculates sizes
        tb.hide()
        tb.show()

    # Event handlers
    ##########################################################################

    def _linkHandler(self, url):
        (cmd, arg) = url.split(":")
        if cmd == "open":
            deck = self._decks[int(arg)]['path']
            self.mw.loadDeck(deck)
        elif cmd == "opts":
            self._optsForRow(int(arg))

    def _keyHandler(self, evt):
        txt = evt.text()
        if ((txt >= "0" and txt <= "9") or
            (txt >= "a" and txt <= "z")):
            self._openAccel(txt)
            evt.accept()
        evt.ignore()

    def _openAccel(self, txt):
        for d in self._decks:
            if d['accel'] == txt:
                self.mw.loadDeck(d['path'])

    # HTML generation
    ##########################################################################

    def _renderPage(self):
        if self._decks:
            buf = ""
            max=len(self._decks)-1
            for c, deck in enumerate(self._decks):
                buf += self._deckRow(c, max, deck)
            self.web.stdHtml(_body%dict(
                title=_("Decks"),
                rows=buf,
                extra="<p>%s<p>%s" % (
                    self._summary(),
                    _("Click a deck to open, or type a number."))),
                             _css)
        else:
            self.web.stdHtml(_body%dict(
                title=_("Welcome!"),
                rows="<tr><td align=center>%s</td></tr>"%_(
                    "Click <b>Download</b> to get started."),
                extra=""),
                             _css)

    def _deckRow(self, c, max, deck):
        buf = "<tr>"
        ok = deck['state'] == 'ok'
        def accelName(deck):
            if deck['accel']:
                return "%s. " % deck['accel']
            return ""
        if ok:
            # name/link
            buf += "<td>%s<b>%s</b></td>" % (
            accelName(deck),
            "<a href='open:%d'>%s</a>"%(c, deck['name']))
            # due
            col = '<td class=num><b><font color=#0000ff>%s</font></b></td>'
            if deck['due'] > 0:
                s = col % str(deck['due'])
            else:
                s = col % ""
            buf += s
            # new
            if deck['new']:
                s = str(deck['new'])
            else:
                s = ""
            buf += "<td class=num>%s</td>" % s
        else:
            # name/error
            if deck['state'] == 'missing':
                sub = _("(moved or removed)")
            elif deck['state'] == 'corrupt':
                sub = _("(corrupt)")
            elif deck['state'] == 'in use':
                sub = _("(already open)")
            else:
                sub = "unknown"
            buf += "<td>%s<b>%s</b><br><span class=sub>%s</span></td>" % (
                accelName(deck),
                deck['name'],
                sub)
            # no counts
            buf += "<td colspan=2></td>"
        # options
        buf += "<td class=opts><a class=opts href='opts:%d'>%s&#9660;</a></td>" % (
            c, "Options")
        buf += "</tr>"
        if c != max:
            buf += "<tr><td colspan=4><hr noshade></td></tr>"
        return buf

    def _summary(self):
        # summarize
        reps = 0
        mins = 0
        revC = 0
        newC = 0
        for d in self._decks:
            if d['state']=='ok':
                reps += d['reps']
                mins += d['time']
                revC += d['due']
                newC += d['new']
        line1 = ngettext(
            "Studied <b>%(reps)d card</b> in <b>%(time)s</b> today.",
            "Studied <b>%(reps)d cards</b> in <b>%(time)s</b> today.",
            reps) % {
            'reps': reps,
            'time': fmtTimeSpan(mins, point=2),
            }
        rev = ngettext(
            "<b><font color=#0000ff>%d</font></b> review",
            "<b><font color=#0000ff>%d</font></b> reviews",
            revC) % revC
        new = ngettext("<b>%d</b> new card", "<b>%d</b> new cards", newC) % newC
        line2 = _("Due: %(rev)s, %(new)s") % {
            'rev': rev, 'new': new}
        return line1+'<br>'+line2

    # Options
    ##########################################################################

    def _optsForRow(self, n):
        m = QMenu(self.mw)
        # hide
        a = m.addAction(QIcon(":/icons/edit-undo.png"), _("Hide From List"))
        a.connect(a, SIGNAL("activated()"), lambda n=n: self._hideRow(n))
        # delete
        a = m.addAction(QIcon(":/icons/editdelete.png"), _("Delete"))
        a.connect(a, SIGNAL("activated()"), lambda n=n: self._deleteRow(n))
        m.exec_(QCursor.pos())

    def _hideRow(self, c):
        if aqt.utils.askUser(_("""\
Hide %s from the list? You can File>Open it again later.""") %
                            self._decks[c]['name']):
            self.mw.config.delRecentDeck(self._decks[c]['path'])
            del self._decks[c]
            self.refresh()

    def _deleteRow(self, c):
        if aqt.utils.askUser(_("""\
Delete %s? If this deck is synchronized the online version will \
not be touched.""") % self._decks[c]['name']):
            deck = self._decks[c]['path']
            os.unlink(deck)
            try:
                shutil.rmtree(re.sub(".anki$", ".media", deck))
            except OSError:
                pass
            self.mw.config.delRecentDeck(deck)
            self.refresh()

    # Data gathering
    ##########################################################################

    def _checkDecks(self, forget=False):
        self._decks = []
        decks = self.mw.config.recentDecks()
        if not decks:
            return
        missingDecks = []
        tx = time.time()
        self.mw.progress.start(max=len(decks))
        for c, d in enumerate(decks):
            self.mw.progress.update(_("Checking deck %(x)d of %(y)d...") % {
                'x': c+1, 'y': len(decks)})
            base = os.path.basename(d)
            if not os.path.exists(d):
                missingDecks.append(d)
                self._decks.append({'name': base, 'state': 'missing'})
                continue
            try:
                mod = os.stat(d)[stat.ST_MTIME]
                t = time.time()
                deck = Deck(d, lock=False)
                counts = deck.sched.counts()
                dtime = deck.sched.timeToday()
                dreps = deck.sched.repsToday()
                self._decks.append({
                    'path': d,
                    'state': 'ok',
                    'name': deck.name(),
                    'due': counts[0],
                    'new': counts[1],
                    'mod': deck.mod,
                    # these multiply deck check time by a factor of 6
                    'time': dtime,
                    'reps': dreps
                    })
                deck.close(save=False)
                # reset modification time for the sake of backup systems
                try:
                    os.utime(d, (mod, mod))
                except:
                    # some misbehaving filesystems may fail here
                    pass
            except Exception, e:
                if "locked" in unicode(e):
                    state = "in use"
                else:
                    state = "corrupt"
                self._decks.append({'name': base, 'state':state})
        if forget:
            for d in missingDecks:
                self.mw.config.delRecentDeck(d)
        self.mw.progress.finish()
        self._browserLastRefreshed = time.time()
        self._reorderDecks()

    def _reorderDecks(self):
        # for now, sort by deck name
        self._decks.sort(key=itemgetter('name'))
        # after the decks are sorted, assign shortcut keys to them
        for c, d in enumerate(self._decks):
            if c > 35:
                d['accel'] = None
            elif c < 9:
                d['accel'] = str(c+1)
            else:
                d['accel'] = ord('a')+(c-10)

    def refresh(self):
        self._browserLastRefreshed = 0
        self.show(_init=False)
