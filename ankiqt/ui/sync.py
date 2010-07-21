# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import os, types, socket, time, traceback
import ankiqt
import anki
from anki.sync import SyncClient, HttpSyncServerProxy, copyLocalMedia
from anki.sync import SYNC_HOST, SYNC_PORT
from anki.errors import *
from anki import DeckStorage
from anki.db import sqlite
import ankiqt.forms
from anki.hooks import addHook, removeHook

# Synchronising a deck with a public server
##########################################################################

class Sync(QThread):

    def __init__(self, parent, user, pwd, interactive, create,
                 onlyMerge, sourcesToCheck):
        QThread.__init__(self)
        self.parent = parent
        self.interactive = interactive
        self.user = user
        self.pwd = pwd
        self.create = create
        self.ok = True
        self.onlyMerge = onlyMerge
        self.sourcesToCheck = sourcesToCheck
        self.proxy = None
        addHook('fullSyncStarted', self.fullSyncStarted)
        addHook('fullSyncFinished', self.fullSyncFinished)
        addHook('fullSyncProgress', self.fullSyncProgress)

    def setStatus(self, msg, timeout=5000):
        self.emit(SIGNAL("setStatus"), msg, timeout)

    def run(self):
        if self.parent.syncName:
            self.syncDeck()
        else:
            self.syncAllDecks()
        removeHook('fullSyncStarted', self.fullSyncStarted)
        removeHook('fullSyncFinished', self.fullSyncFinished)
        removeHook('fullSyncProgress', self.fullSyncProgress)

    def fullSyncStarted(self, max):
        self.emit(SIGNAL("fullSyncStarted"), max)

    def fullSyncFinished(self):
        self.emit(SIGNAL("fullSyncFinished"))

    def fullSyncProgress(self, type, val):
        self.emit(SIGNAL("fullSyncProgress"), type, val)

    def error(self, error):
        if getattr(error, 'data', None) is None:
            error.data = {}
        if error.data.get('type') == 'noResponse':
            self.emit(SIGNAL("noSyncResponse"))
        else:
            error = self.getErrorMessage(error)
            self.emit(SIGNAL("showWarning"), error)
        if self.onlyMerge:
            # new file needs cleaning up
            self.emit(SIGNAL("cleanNewDeck"))
        else:
            self.emit(SIGNAL("syncFinished"))

    def getErrorMessage(self, error):
        if error.data.get('status') == "invalidUserPass":
            msg=_("Please double-check your username/password.")
            self.emit(SIGNAL("badUserPass"))
        elif error.data.get('status') == "oldVersion":
            msg=_("The sync protocol has changed. Please upgrade.")
        else:
            msg=_("Unknown error: %s") % traceback.format_exc()
        return msg

    def connect(self, *args):
        # connect, check auth
        if not self.proxy:
            self.setStatus(_("Connecting..."), 0)
            proxy = HttpSyncServerProxy(self.user, self.pwd)
            proxy.sourcesToCheck = self.sourcesToCheck
            proxy.connect("ankiqt-" + ankiqt.appVersion)
            self.proxy = proxy
        return self.proxy

    def syncAllDecks(self):
        decks = self.parent.syncDecks
        for d in decks:
            self.syncDeck(deck=d)
        self.emit(SIGNAL("syncFinished"))

    def syncDeck(self, deck=None):
        # multi-mode setup
        if deck:
            c = sqlite.connect(deck)
            syncName = c.execute("select syncName from decks").fetchone()[0]
            c.close()
            if not syncName:
                return
            path = deck
        else:
            syncName = self.parent.syncName
            path = self.parent.deckPath
        # ensure deck mods cached
        try:
            proxy = self.connect()
        except SyncError, e:
            return self.error(e)
        # exists on server?
        if not proxy.hasDeck(syncName):
            if deck:
                return
            if self.create:
                try:
                    proxy.createDeck(syncName)
                except SyncError, e:
                    return self.error(e)
            else:
                keys = [k for (k,v) in proxy.decks.items() if v[1] != -1]
                self.emit(SIGNAL("noMatchingDeck"), keys, not self.onlyMerge)
                self.setStatus("")
                return
        self.setStatus(_("Syncing <b>%s</b>...") % syncName, 0)
        timediff = abs(proxy.timestamp - time.time())
        if timediff > 300:
            self.emit(SIGNAL("syncClockOff"), timediff)
            return
        # reopen
        self.deck = None
        try:
            self.deck = DeckStorage.Deck(path)
            client = SyncClient(self.deck)
            client.setServer(proxy)
            proxy.deckName = syncName
            # need to do anything?
            start = time.time()
            if client.prepareSync():
                changes = True
                # summary
                self.setStatus(_("Fetching summary from server..."), 0)
                sums = client.summaries()
                if client.needFullSync(sums):
                    self.setStatus(_("Preparing full sync..."), 0)
                    ret = client.prepareFullSync()
                    if ret[0] == "fromLocal":
                        self.setStatus(_("Uploading..."), 0)
                        client.fullSyncFromLocal(ret[1], ret[2])
                    else:
                        self.setStatus(_("Downloading..."), 0)
                        client.fullSyncFromServer(ret[1], ret[2])
                    self.setStatus(_("Sync complete."), 0)
                    # reopen the deck in case we have sources
                    self.deck = DeckStorage.Deck(path)
                    client.deck = self.deck
                else:
                    # diff
                    self.setStatus(_("Determining differences..."), 0)
                    payload = client.genPayload(sums)
                    # send payload
                    if not deck:
                        pr = client.payloadChangeReport(payload)
                        self.setStatus("<br>" + pr + "<br>", 0)
                    self.setStatus(_("Transferring payload..."), 0)
                    res = client.server.applyPayload(payload)
                    # apply reply
                    self.setStatus(_("Applying reply..."), 0)
                    client.applyPayloadReply(res)
                    # finished. save deck, preserving mod time
                    self.setStatus(_("Sync complete."))
                    self.deck.lastLoaded = self.deck.modified
                    self.deck.s.flush()
                    self.deck.s.commit()
            else:
                changes = False
                if not deck:
                    self.setStatus(_("No changes found."))
            # check sources
            srcChanged = False
            if self.sourcesToCheck:
                start = time.time()
                self.setStatus(_("<br><br>Checking deck subscriptions..."))
                srcChanged = False
                for source in self.sourcesToCheck:
                    proxy.deckName = str(source)
                    msg = "%s:" % client.syncOneWayDeckName()
                    if not proxy.hasDeck(str(source)):
                        self.setStatus(_(" * %s no longer exists.") % msg)
                        continue
                    if not client.prepareOneWaySync():
                        self.setStatus(_(" * %s no changes found.") % msg)
                        continue
                    srcChanged = True
                    self.setStatus(_(" * %s fetching payload...") % msg)
                    payload = proxy.genOneWayPayload(client.deck.lastSync)
                    self.setStatus(msg + _(" applied %d modified cards.") %
                                   len(payload['cards']))
                    client.applyOneWayPayload(payload)
                self.setStatus(_("Check complete."))
                self.deck.s.flush()
                self.deck.s.commit()
            # close and send signal to main thread
            self.deck.close()
            if not deck:
                taken = time.time() - start
                if (changes or srcChanged) and taken < 2.5:
                    time.sleep(2.5 - taken)
                else:
                    time.sleep(0.25)
                self.emit(SIGNAL("syncFinished"))
        except Exception, e:
            self.ok = False
            #traceback.print_exc()
            if self.deck:
                self.deck.close()
            # cheap hack to ensure message is displayed
            err = `getattr(e, 'data', None) or e`
            self.setStatus(_("Syncing failed: %(a)s") % {
                'a': err})
            if not deck:
                self.error(e)

# Choosing a deck to sync to
##########################################################################

class DeckChooser(QDialog):

    def __init__(self, parent, decks, create):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.decks = decks
        self.dialog = ankiqt.forms.syncdeck.Ui_DeckChooser()
        self.dialog.setupUi(self)
        self.create = create
        if self.create:
            self.dialog.topLabel.setText(_("<h1>Synchronize</h1>"))
        else:
            self.dialog.topLabel.setText(_("<h1>Open Online Deck</h1>"))
        if self.create:
            self.dialog.decks.addItem(QListWidgetItem(
                _("Create '%s' on server") % self.parent.syncName))
        self.decks.sort()
        for name in decks:
            name = os.path.splitext(name)[0]
            if self.create:
                msg = _("Overwrite '%s' on server") % name
            else:
                msg = name
            item = QListWidgetItem(msg)
            self.dialog.decks.addItem(item)
        self.dialog.decks.setCurrentRow(0)
        # the list widget will swallow the enter key
        s = QShortcut(QKeySequence("Return"), self)
        self.connect(s, SIGNAL("activated()"), self.accept)
        self.name = None

    def getName(self):
        self.exec_()
        return self.name

    def accept(self):
        idx = self.dialog.decks.currentRow()
        if self.create:
            offset = 1
        else:
            offset = 0
        if idx == 0 and self.create:
            self.name = self.parent.syncName
        else:
            self.name = self.decks[self.dialog.decks.currentRow() - offset]
        self.close()
