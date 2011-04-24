# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, stat, shutil, re
from operator import itemgetter
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki import Deck
from anki.utils import fmtTimeSpan
from anki.hooks import addHook
import aqt

class DeckBrowser(object):
    "Display a list of remembered decks."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self._browserLastRefreshed = 0
        self._decks = []
        addHook("deckClosing", self._onClose)

    def show(self, _init=True):
        if _init:
            self.web.setLinkHandler(self._linkHandler)
            self.web.setKeyHandler(self._keyHandler)
            self._setupToolbar()
        # refresh or reorder
        self._checkDecks()
        # show
        self._renderPage()

    def _onClose(self):
        # update counts
        deck = self.mw.deck
        def add(d):
            counts = deck.sched.counts()
            d['due'] = counts[1]+counts[2]
            d['new'] = counts[0]
            d['mod'] = deck.mod
            d['time'] = deck.sched.timeToday()
            d['reps'] = deck.sched.repsToday()
            d['name'] = deck.name()
        for d in self._decks:
            if d['path'] == deck.path:
                add(d)
                return
        # not found; add new
        d = {'path': deck.path, 'state': 'ok'}
        add(d)
        self._decks.append(d)

    # Toolbar
    ##########################################################################

    # we don't use the top toolbar
    def _setupToolbar(self):
        self.mw.form.toolBar.hide()

    # instead we have a html one under the deck list
    def _toolbar(self):
        items = [
            ("download", _("Download")),
            ("new", _("Create")),
#            ("import", _("Import")),
            ("opensel", _("Open")),
#            ("synced", _("Synced")),
            ("refresh", _("Refresh")),
        ]
        h = "".join([self.mw.button(
            link=row[0], name=row[1]) for row in items])
        return h

    # Event handlers
    ##########################################################################

    def _linkHandler(self, url):
        if ":" in url:
            (cmd, arg) = url.split(":")
        else:
            cmd = url
        if cmd == "open":
            deck = self._decks[int(arg)]
            self._loadDeck(deck)
        elif cmd == "opts":
            self._optsForRow(int(arg))
        elif cmd == "download":
            self.mw.onGetSharedDeck()
        elif cmd == "new":
            self.mw.onNew()
        elif cmd == "import":
            self.mw.onImport()
        elif cmd == "opensel":
            self.mw.onOpen()
        elif cmd == "synced":
            self.mw.onOpenOnline()
        elif cmd == "refresh":
            self.refresh()

    def _keyHandler(self, evt):
        txt = evt.text()
        if ((txt >= "0" and txt <= "9") or
            (txt >= "a" and txt <= "z")):
            self._openAccel(txt)
            return True

    def _openAccel(self, txt):
        for d in self._decks:
            if d['accel'] == txt:
                self._loadDeck(d)

    def _loadDeck(self, rec):
        if rec['state'] == 'ok':
            self.mw.loadDeck(rec['path'])

    # HTML generation
    ##########################################################################

    _css = """
.sub { color: #555; }
a.deck { color: #000; text-decoration: none; font-size: 100%; }
.num { text-align: right; padding: 0 5 0 5; }
td.opts { text-align: right; white-space: nowrap; }
td.menu { text-align: center; }
a { font-size: 80%; }
.extra { font-size: 90%; }
"""

    _body = """
<center>
<h1>%(title)s</h1>
%(tb)s
<p>
<table cellspacing=0 cellpadding=3 width=100%%>
%(rows)s
</table>
<div class="extra">
%(extra)s
</div>
</center>
"""

    def _renderPage(self):
        css = self.mw.sharedCSS + self._css
        if self._decks:
            buf = ""
            max=len(self._decks)-1
            buf += "<tr><th></th><th align=right>%s</th>" % _("Due")
            buf += "<th align=right>%s</th><th></th></tr>" % _("New")
            for c, deck in enumerate(self._decks):
                buf += self._deckRow(c, max, deck)
            self.web.stdHtml(self._body%dict(
                title=_("Decks"),
                rows=buf,
                tb=self._toolbar(),
                extra="<p>%s<p>%s" % (
                    self._summary(),
                    _("Click a deck to open it, or type a number."))),
                             css)
        else:
            self.web.stdHtml(self._body%dict(
                title=_("Welcome!"),
                rows="<tr><td align=center>%s</td></tr>"%_(
                    "Click <b>Download</b> to get started."),
                extra="",
                tb=self._toolbar()),
                             css)

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
            "<a class=deck href='open:%d'>%s</a>"%(c, deck['name']))
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
        buf += "<td class=opts>%s</td>" % (
            self.mw.button(link="opts:%d"%c, name=_("Options")+'&#9660'))
        buf += "</tr>"
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
        d = self._decks[c]
        if d['state'] == "missing" or aqt.utils.askUser(_("""\
Hide %s from the list? You can File>Open it again later.""") %
                            d['name']):
            self.mw.config.delRecentDeck(d['path'])
            del self._decks[c]
            self.refresh()

    def _deleteRow(self, c):
        d = self._decks[c]
        if d['state'] == 'missing':
            return self._hideRow(c)
        if aqt.utils.askUser(_("""\
Delete %s? If this deck is synchronized the online version will \
not be touched.""") % d['name']):
            deck = d['path']
            os.unlink(deck)
            try:
                shutil.rmtree(re.sub(".anki$", ".media", deck))
            except OSError:
                pass
            self.mw.config.delRecentDeck(deck)
            self.refresh()

    # Data gathering
    ##########################################################################

    def _checkDecks(self):
        self._decks = []
        decks = self.mw.config.recentDecks()
        if not decks:
            return
        tx = time.time()
        self.mw.progress.start(max=len(decks))
        for c, d in enumerate(decks):
            self.mw.progress.update(_("Checking deck %(x)d of %(y)d...") % {
                'x': c+1, 'y': len(decks)})
            base = os.path.basename(d)
            if not os.path.exists(d):
                self._decks.append({'name': base, 'state': 'missing', 'path':d})
                continue
            try:
                mod = os.stat(d)[stat.ST_MTIME]
                t = time.time()
                deck = Deck(d, queue=False, lock=False)
                counts = deck.sched.selCounts()
                dtime = deck.sched.timeToday()
                dreps = deck.sched.repsToday()
                self._decks.append({
                    'path': d,
                    'state': 'ok',
                    'name': deck.name(),
                    'due': counts[1]+counts[2],
                    'new': counts[0],
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
                self._decks.append({'name': base, 'state':state, 'path':d})
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
