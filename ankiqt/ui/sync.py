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

    def __init__(self, parent, user, pwd, interactive, onlyMerge):
        QThread.__init__(self)
        self.parent = parent
        self.interactive = interactive
        self.user = user
        self.pwd = pwd
        self.ok = True
        self.onlyMerge = onlyMerge
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
        if error.data.get('type') == 'clockOff':
            pass
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
        elif "busy" in error.data.get('status', ''):
            msg=_("""\
AnkiWeb is under heavy load at the moment. Please try again in a little while.""")
        elif error.data.get('type') == 'noResponse':
            msg=_("""\
The server didn't reply. Please try again shortly, and if the problem \
persists, please report it on the forums.""")
        elif error.data.get('type') == 'connectionError':
            msg=_("""\
There was a connection error. If it persists, please try disabing your
firewall software temporarily, or try again from a different network.

Debugging info: %s""") % error.data.get("exc", "<none>")
        else:
            tb = traceback.format_exc()
            if "missingFacts" in tb:
                msg=_("""Facts were missing after sync, so the \
sync was aborted. Please report this error.""")
            else:
                msg=_("Unknown error: %s") % tb
        return msg

    def connect(self, *args):
        # connect, check auth
        if not self.proxy:
            self.setStatus(_("Connecting..."), 0)
            proxy = HttpSyncServerProxy(self.user, self.pwd)
            proxy.connect("ankiqt-" + ankiqt.appVersion)
            self.proxy = proxy
            # check clock
            if proxy.timediff > 300:
                self.emit(SIGNAL("syncClockOff"), proxy.timediff)
                raise SyncError(type="clockOff")
        return self.proxy

    def syncAllDecks(self):
        decks = self.parent.syncDecks
        for d in decks:
            ret = self.syncDeck(deck=d)
            if not ret:
                # failed but not cleaned up
                break
            elif ret == -1:
                # failed and already cleaned up
                return
            elif ret == -2:
                # current deck set not to sync
                continue
        self.setStatus(_("Sync Finished."), 0)
        time.sleep(1)
        self.emit(SIGNAL("syncFinished"))

    def syncDeck(self, deck=None):
        try:
            if deck:
                # multi-mode setup
                sqlpath = deck.encode("utf-8")
                c = sqlite.connect(sqlpath)
                (syncName, localMod, localSync) = c.execute(
                    "select syncName, modified, lastSync from decks").fetchone()
                c.close()
                if not syncName:
                    return -2
                syncName = os.path.splitext(os.path.basename(deck))[0]
                path = deck
            else:
                syncName = self.parent.syncName
                path = self.parent.deckPath
                sqlpath = path.encode("utf-8")
                c = sqlite.connect(sqlpath)
                (localMod, localSync) = c.execute(
                    "select modified, lastSync from decks").fetchone()
                c.close()
        except Exception, e:
            # we don't know which db library we're using, so do string match
            if "locked" in unicode(e):
                return
            # unknown error
            self.error(e)
            return -1
        # ensure deck mods cached
        try:
            proxy = self.connect()
        except SyncError, e:
            self.error(e)
            return -1
        # exists on server?
        deckCreated = False
        if not proxy.hasDeck(syncName):
            if self.onlyMerge:
                keys = [k for (k,v) in proxy.decks.items() if v[1] != -1]
                self.emit(SIGNAL("noMatchingDeck"), keys)
                self.setStatus("")
                return
            try:
                proxy.createDeck(syncName)
                deckCreated = True
            except SyncError, e:
                self.error(e)
                return -1
        # check conflicts
        proxy.deckName = syncName
        remoteMod = proxy.modified()
        remoteSync = proxy._lastSync()
        minSync = min(localSync, remoteSync)
        self.conflictResolution = None
        if (localMod != remoteMod and minSync > 0 and
            localMod > minSync and remoteMod > minSync):
            self.emit(SIGNAL("syncConflicts"), syncName)
            while not self.conflictResolution:
                time.sleep(0.2)
            if self.conflictResolution == "cancel":
                # alert we're finished early
                self.emit(SIGNAL("syncFinished"))
                return -1
        # reopen
        self.setStatus(_("Syncing <b>%s</b>...") % syncName, 0)
        self.deck = None
        try:
            self.deck = DeckStorage.Deck(path)
            disable = False
            if deck and not self.deck.syncName:
                # multi-mode sync and syncing has been disabled by upgrade
                disable = True
            client = SyncClient(self.deck)
            client.setServer(proxy)
            # need to do anything?
            start = time.time()
            if client.prepareSync(proxy.timediff) and not disable:
                if self.deck.lastSync <= 0:
                    if client.remoteTime > client.localTime:
                        self.conflictResolution = "keepRemote"
                    else:
                        self.conflictResolution = "keepLocal"
                changes = True
                # summary
                if not self.conflictResolution and not self.onlyMerge:
                    self.setStatus(_("Fetching summary from server..."), 0)
                    sums = client.summaries()
                if (self.conflictResolution or
                    self.onlyMerge or client.needFullSync(sums)):
                    self.setStatus(_("Preparing full sync..."), 0)
                    if self.conflictResolution == "keepLocal":
                        client.remoteTime = 0
                    elif self.conflictResolution == "keepRemote" or self.onlyMerge:
                        client.localTime = 0
                    lastSync = self.deck.lastSync
                    ret = client.prepareFullSync()
                    if ret[0] == "fromLocal":
                        if not self.conflictResolution:
                            if lastSync <= 0 and not deckCreated:
                                self.clobberChoice = None
                                self.emit(SIGNAL("syncClobber"), syncName)
                                while not self.clobberChoice:
                                    time.sleep(0.2)
                                if self.clobberChoice == "cancel":
                                    # disable syncing on this deck
                                    c = sqlite.connect(sqlpath)
                                    c.execute(
                                        "update decks set syncName = null, "
                                        "lastSync = 0")
                                    c.commit()
                                    c.close()
                                    if not deck:
                                        # alert we're finished early
                                        self.emit(SIGNAL("syncFinished"))
                                    return True
                        self.setStatus(_("Uploading..."), 0)
                        client.fullSyncFromLocal(ret[1], ret[2])
                    else:
                        self.setStatus(_("Downloading..."), 0)
                        client.fullSyncFromServer(ret[1], ret[2])
                    self.setStatus(_("Sync complete."), 0)
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
                    # now that both sides have successfully applied, tell
                    # server to save, then save local
                    client.server.finish()
                    self.deck.lastLoaded = self.deck.modified
                    self.deck.s.commit()
                    self.setStatus(_("Sync complete."))
            else:
                changes = False
                if disable:
                    self.setStatus(_("Disabled by upgrade."))
                elif not deck:
                    self.setStatus(_("No changes found."))
            # close and send signal to main thread
            self.deck.close()
            if not deck:
                taken = time.time() - start
                if changes and taken < 2.5:
                    time.sleep(2.5 - taken)
                else:
                    time.sleep(0.25)
                self.emit(SIGNAL("syncFinished"))
            return True
        except Exception, e:
            self.ok = False
            if self.deck:
                self.deck.close()
            self.error(e)
            return -1

# Downloading personal decks
##########################################################################

class DeckChooser(QDialog):

    def __init__(self, parent, decks):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.decks = decks
        self.dialog = ankiqt.forms.syncdeck.Ui_DeckChooser()
        self.dialog.setupUi(self)
        self.dialog.topLabel.setText(_("<h1>Download Personal Deck</h1>"))
        self.decks.sort()
        for name in decks:
            name = os.path.splitext(name)[0]
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
        self.name = self.decks[self.dialog.decks.currentRow()]
        self.close()
