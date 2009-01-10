# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import os, types, socket, time, traceback
import ankiqt
import anki
from anki.sync import SyncClient, HttpSyncServerProxy, BulkMediaSyncerProxy
from anki.sync import BulkMediaSyncer
from anki.errors import *
from anki import DeckStorage
import ankiqt.forms

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

    def setStatus(self, msg, timeout=5000):
        self.emit(SIGNAL("setStatus"), msg, timeout)

    def run(self):
        self.syncDeck()

    def error(self, error):
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
        elif error.data.get('status') == "oldVersion":
            msg=_("The sync protocol has changed. Please upgrade.")
        elif error.data.get('type') == "noResponse":
            msg=_("Server is down or operation failed.")
        else:
            msg=_("Unknown error: %s") % `error.data`
        return msg

    def connect(self, *args):
        # connect, check auth
        proxy = HttpSyncServerProxy(self.user, self.pwd)
        proxy.sourcesToCheck = self.sourcesToCheck
        proxy.connect("ankiqt-" + ankiqt.appVersion)
        return proxy

    def syncDeck(self):
        self.setStatus(_("Connecting..."), 0)
        try:
            proxy = self.connect()
        except SyncError, e:
            return self.error(e)
        # exists on server?
        if not proxy.hasDeck(self.parent.syncName):
            if self.create:
                try:
                    proxy.createDeck(self.parent.syncName)
                except SyncError, e:
                    return self.error(e)
            else:
                keys = [k for (k,v) in proxy.decks.items() if v[1] != -1]
                self.emit(SIGNAL("noMatchingDeck"), keys, not self.onlyMerge)
                self.setStatus("")
                return
        timediff = abs(proxy.timestamp - time.time())
        if timediff > 60:
            self.emit(SIGNAL("syncClockOff"), timediff)
            return
        # reconnect
        try:
            self.deck = DeckStorage.Deck(self.parent.deckPath, backup=False)
            client = SyncClient(self.deck)
            client.setServer(proxy)
            proxy.deckName = self.parent.syncName
            # need to do anything?
            start = time.time()
            if client.prepareSync():
                # summary
                self.setStatus(_("Fetching summary from server..."), 0)
                sums = client.summaries()
                # diff
                self.setStatus(_("Determining differences..."), 0)
                payload = client.genPayload(sums)
                # send payload
                pr = client.payloadChangeReport(payload)
                self.setStatus("<br>" + pr + "<br>", 0)
                self.setStatus(_("Transferring payload..."), 0)
                res = client.server.applyPayload(payload)
                # apply reply
                self.setStatus(_("Applying reply..."), 0)
                client.applyPayloadReply(res)
                if client.mediaSyncPending:
                    self.doBulkDownload(proxy.deckName)
                # finished. save deck, preserving mod time
                self.setStatus(_("Sync complete."))
                self.deck.lastLoaded = self.deck.modified
                self.deck.s.flush()
                self.deck.s.commit()
            else:
                self.setStatus(_("No changes found."))
            # check sources
            if self.sourcesToCheck:
                start = time.time()
                self.setStatus(_("<br><br>Checking deck subscriptions..."))
                for source in self.sourcesToCheck:
                    proxy.deckName = str(source)
                    msg = "%s:" % client.syncOneWayDeckName()
                    if not proxy.hasDeck(str(source)):
                        self.setStatus(_(" * %s no longer exists.") % msg)
                        continue
                    if not client.prepareOneWaySync():
                        self.setStatus(_(" * %s no changes found.") % msg)
                        continue
                    self.setStatus(_(" * %s fetching payload...") % msg)
                    payload = proxy.genOneWayPayload(client.deck.lastSync)
                    self.setStatus(msg + _(" applied %d modified cards.") %
                                   len(payload['cards']))
                    client.applyOneWayPayload(payload)
                    if client.mediaSyncPending:
                        self.doBulkDownload("")
                self.setStatus(_("Check complete."))
                self.deck.s.flush()
                self.deck.s.commit()
            # close and send signal to main thread
            self.deck.close()
            taken = time.time() - start
            if taken < 2.5:
                time.sleep(2.5 - taken)
            self.emit(SIGNAL("syncFinished"))
        except Exception, e:
            traceback.print_exc()
            self.deck.close()
            # cheap hack to ensure message is displayed
            err = `getattr(e, 'data', None) or e`
            self.setStatus(_("Syncing failed: %(a)s") % {
                'a': err})
            time.sleep(3)
            self.emit(SIGNAL("syncFinished"))

    def doBulkDownload(self, deckname):
        self.emit(SIGNAL("openSyncProgress"))
        client = BulkMediaSyncer(self.deck)
        client.server = BulkMediaSyncerProxy(self.user, self.pwd)
        client.server.deckName = deckname
        client.progressCallback = self.bulkCallback
        try:
            client.sync()
        except:
            self.emit(SIGNAL("bulkSyncFailed"))
        time.sleep(0.1)
        self.emit(SIGNAL("closeSyncProgress"))

    def bulkCallback(self, *args):
        self.emit(SIGNAL("updateSyncProgress"), args)

# Choosing a deck to sync to
##########################################################################

class DeckChooser(QDialog):

    def __init__(self, parent, decks, create):
        QDialog.__init__(self, parent)
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
                msg = _("Merge with '%s' on server") % name
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
