# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, os, stat
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki import Deck
from anki.utils import fmtTimeSpan
from anki.hooks import addHook

_css = """
body { background-color: #ddd; }
td { border-bottom: 1px solid #000;}
#outer { margin-top: 1em; }
.sub { color: #555; }
"""

_body = """
<center>
<div id="outer">
<h1>%s</h1>
<table cellspacing=0 width=90%%>
%s
</table>
<br>
%s
</div>
"""

class DeckBrowser(object):

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self._browserLastRefreshed = 0
        self._decks = []
        addHook("deckClosing", self.onClose)

    def _bridge(self, str):
        if str == "refresh":
            pass
        elif str == "full":
            self.onFull()

    def _link(self, url):
        pass

    def show(self):
        self.web.setBridge(self._bridge)
        self.web.setLinkHandler(self._link)
        if (time.time() - self._browserLastRefreshed >
            self.mw.config['deckBrowserRefreshPeriod']):
            t = time.time()
            self._checkDecks()
            print "check decks", time.time() - t
        else:
            self._reorderDecks()
        if self._decks:
            buf = ""
            for c, deck in enumerate(self._decks):
                buf += self._deckRow(c, deck)
            self.web.stdHtml(_body%(_("Decks"), buf, self._summary()), _css)
        else:
            buf = ("""\
<br>
<font size=+1>
Welcome to Anki! Click <b>'Download'</b> to get started. You can return here
later by using File>Close.
</font>
<br>
""")
        # FIXME: ensure deck open button is focused


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

    def _deckRow(self, c, deck):
        buf = "<tr>"
        # name and status
        ok = deck['state'] == 'ok'
        if ok:
            sub = _("%s ago") % fmtTimeSpan(
                time.time() - deck['mod'])
        elif deck['state'] == 'missing':
            sub = _("(moved or removed)")
        elif deck['state'] == 'corrupt':
            sub = _("(corrupt)")
        elif deck['state'] == 'in use':
            sub = _("(already open)")
        sub = "<font size=-1>%s</font>" % sub
        buf += "<td><b>%s</b><br><span class=sub>%s</span></td>" % (deck['name'], sub)
        if ok:
            # due
            col = '<td><b><font color=#0000ff>%s</font></b></td>'
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
            buf += "<td>%s</td>" % s
        else:
            buf += "<td></td><td></td>"
        # open
        # openButton = QPushButton(_("Open"))
        # if c < 9:
        #     if sys.platform.startswith("darwin"):
        #         extra = ""
        #         # appears to be broken on osx
        #         #extra = _(" (Command+Option+%d)") % (c+1)
        #         #openButton.setShortcut(_("Ctrl+Alt+%d" % (c+1)))
        #     else:
        #         extra = _(" (Alt+%d)") % (c+1)
        #         openButton.setShortcut(_("Alt+%d" % (c+1)))
        # else:
        #     extra = ""
        # openButton.setToolTip(_("Open this deck%s") % extra)
        # self.connect(openButton, SIGNAL("clicked()"),
        #              lambda d=deck['path']: self.loadDeck(d))
        # layout.addWidget(openButton, c+1, 5)
        # if c == 0:
        #     focusButton = openButton
        # more
        # moreButton = QPushButton(_("More"))
        # moreMenu = QMenu()
        # a = moreMenu.addAction(QIcon(":/icons/edit-undo.png"),
        #                        _("Hide From List"))
        # a.connect(a, SIGNAL("triggered()"),
        #           lambda c=c: self.onDeckBrowserForget(c))
        # a = moreMenu.addAction(QIcon(":/icons/editdelete.png"),
        #                        _("Delete"))
        # a.connect(a, SIGNAL("triggered()"),
        #           lambda c=c: self.onDeckBrowserDelete(c))
        # moreButton.setMenu(moreMenu)
        # self.moreMenus.append(moreMenu)
        # layout.addWidget(moreButton, c+1, 6)
        buf += "</tr>"
        return buf

    def _buttons(self):
        # refresh = QPushButton(_("Refresh"))
        # refresh.setToolTip(_("Check due counts again (F5)"))
        # refresh.setShortcut(_("F5"))
        # self.connect(refresh, SIGNAL("clicked()"),
        #              self.refresh)
        # layout.addItem(QSpacerItem(1,20, QSizePolicy.Preferred,
        #                            QSizePolicy.Preferred), c+2, 5)
        # layout.addWidget(refresh, c+3, 5)
        # more = QPushButton(_("More"))
        # moreMenu = QMenu()
        # a = moreMenu.addAction(QIcon(":/icons/edit-undo.png"),
        #                        _("Forget Inaccessible Decks"))
        # a.connect(a, SIGNAL("triggered()"),
        #           self.onDeckBrowserForgetInaccessible)
        # more.setMenu(moreMenu)
        # layout.addWidget(more, c+3, 6)
        # self.moreMenus.append(moreMenu)
        return ""

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
                deck = Deck(d)
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
                deck.close()
                # reset modification time for the sake of backup systems
                try:
                    os.utime(d, (mod, mod))
                except:
                    # some misbehaving filesystems may fail here
                    pass
            except Exception, e:
                if "File is in use" in unicode(e):
                    state = "in use"
                else:
                    raise
                    state = "corrupt"
                self._decks.append({'name': base, 'state':state})
        if forget:
            for d in missingDecks:
                self.mw.config.delRecentDeck(d)
        self.mw.progress.finish()
        self._browserLastRefreshed = time.time()
        self._reorderDecks()

    def _reorderDecks(self):
        print "reorder decks"
        return
        if self.mw.config['deckBrowserOrder'] == 0:
            self._decks.sort(key=itemgetter('mod'),
                                   reverse=True)
        else:
            def custcmp(a, b):
                x = cmp(not not b['due'], not not a['due'])
                if x:
                    return x
                x = cmp(not not b['new'], not not a['new'])
                if x:
                    return x
                return cmp(a['mod'], b['mod'])
            self._decks.sort(cmp=custcmp)

    def refresh(self):
        self._browserLastRefreshed = 0
        self.show()

    def onDeckBrowserForget(self, c):
        if aqt.utils.askUser(_("""\
Hide %s from the list? You can File>Open it again later.""") %
                            self._decks[c]['name']):
            self.mw.config['recentDeckPaths'].remove(self._decks[c]['path'])
            del self._decks[c]
            self.doLater(100, self.showDeckBrowser)

    def onDeckBrowserDelete(self, c):
        deck = self._decks[c]['path']
        if aqt.utils.askUser(_("""\
Delete %s? If this deck is synchronized the online version will \
not be touched.""") %
                            self._decks[c]['name']):
            del self._decks[c]
            os.unlink(deck)
            try:
                shutil.rmtree(re.sub(".anki$", ".media", deck))
            except OSError:
                pass
            #self.config['recentDeckPaths'].remove(deck)
            self.doLater(100, self.showDeckBrowser)

    def onDeckBrowserForgetInaccessible(self):
        self._checkDecks(forget=True)

    def doLater(self, msecs, func):
        timer = QTimer(self)
        timer.setSingleShot(True)
        timer.start(msecs)
        self.connect(timer, SIGNAL("timeout()"), func)
