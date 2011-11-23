# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import os, types, socket, time, traceback
import aqt
import anki
from anki.sync import SyncClient, HttpSyncServerProxy, copyLocalMedia
from anki.sync import SYNC_HOST, SYNC_PORT
from anki.errors import *
from anki import Deck
from anki.db import sqlite
import aqt.forms
from anki.hooks import addHook, removeHook

class SyncManager(object):

    # Syncing
    ##########################################################################

    def syncDeck(self, interactive=True, onlyMerge=False, reload=True):
        "Synchronise a deck with the server."
        self.raiseMain()
        #self.setNotice()
        # vet input
        if interactive:
            self.ensureSyncParams()
        u=self.config['syncUsername']
        p=self.config['syncPassword']
        if not u or not p:
            return
        if self.deck:
            if not self.deck.path:
                if not self.save(required=True):
                    return
        if self.deck and not self.deck.syncName:
            if interactive:
                if (not self.config['mediaLocation']
                    and self.deck.db.scalar("select 1 from media limit 1")):
                    showInfo(_("""\
Syncing sounds and images requires a free file synchronization service like \
DropBox. Click help to learn more, and OK to continue syncing."""),
                                      help="SyncingMedia")
                # enable syncing
                self.deck.enableSyncing()
            else:
                return
        if self.deck is None and getattr(self, 'deckPath', None) is None:
            # sync all decks
            self.loadAfterSync = -1
            self.syncName = None
            self.syncDecks = self.decksToSync()
            if not self.syncDecks:
                if interactive:
                    showInfo(_("""\
Please open a deck and run File>Sync. After you do this once, the deck \
will sync automatically from then on."""))
                return
        else:
            # sync one deck
            # hide all deck-associated dialogs
            self.closeAllDeckWindows()
            if self.deck:
                # save first, so we can rollback on failure
                self.deck.save()
                # store data we need before closing the deck
                self.deckPath = self.deck.path
                self.syncName = self.deck.name()
                self.lastSync = self.deck.lastSync
                self.deck.close()
                self.deck = None
                self.loadAfterSync = reload
        # bug triggered by preferences dialog - underlying c++ widgets are not
        # garbage collected until the middle of the child thread
        self.state = "nostate"
        import gc; gc.collect()
        self.form.welcomeText.setText(u"")
        self.syncThread = aqt.sync.Sync(self, u, p, interactive, onlyMerge)
        self.connect(self.syncThread, SIGNAL("setStatus"), self.setSyncStatus)
        self.connect(self.syncThread, SIGNAL("showWarning"), self.showSyncWarning)
        self.connect(self.syncThread, SIGNAL("moveToState"), self.moveToState)
        self.connect(self.syncThread, SIGNAL("noMatchingDeck"), self.selectSyncDeck)
        self.connect(self.syncThread, SIGNAL("syncClockOff"), self.syncClockOff)
        self.connect(self.syncThread, SIGNAL("cleanNewDeck"), self.cleanNewDeck)
        self.connect(self.syncThread, SIGNAL("syncFinished"), self.onSyncFinished)
        self.connect(self.syncThread, SIGNAL("openSyncProgress"), self.openSyncProgress)
        self.connect(self.syncThread, SIGNAL("closeSyncProgress"), self.closeSyncProgress)
        self.connect(self.syncThread, SIGNAL("updateSyncProgress"), self.updateSyncProgress)
        self.connect(self.syncThread, SIGNAL("bulkSyncFailed"), self.bulkSyncFailed)
        self.connect(self.syncThread, SIGNAL("fullSyncStarted"), self.fullSyncStarted)
        self.connect(self.syncThread, SIGNAL("fullSyncFinished"), self.fullSyncFinished)
        self.connect(self.syncThread, SIGNAL("fullSyncProgress"), self.fullSyncProgress)
        self.connect(self.syncThread, SIGNAL("badUserPass"), self.badUserPass)
        self.connect(self.syncThread, SIGNAL("syncConflicts"), self.onConflict)
        self.connect(self.syncThread, SIGNAL("syncClobber"), self.onClobber)
        self.syncThread.start()
        self.switchToWelcomeScreen()
        self.setEnabled(False)
        self.syncFinished = False
        while not self.syncFinished:
            self.app.processEvents()
            self.syncThread.wait(100)
        self.setEnabled(True)
        return True

    def decksToSync(self):
        ok = []
        for d in self.config['recentDeckPaths']:
            if os.path.exists(d):
                ok.append(d)
        return ok

    def onConflict(self, deckName):
        diag = askUserDialog(_("""\
<b>%s</b> has been changed on both
the local and remote side. What do
you want to do?""" % deckName),
                          [_("Keep Local"),
                           _("Keep Remote"),
                           _("Cancel")])
        diag.setDefault(2)
        ret = diag.run()
        if ret == _("Keep Local"):
            self.syncThread.conflictResolution = "keepLocal"
        elif ret == _("Keep Remote"):
            self.syncThread.conflictResolution = "keepRemote"
        else:
            self.syncThread.conflictResolution = "cancel"

    def onClobber(self, deckName):
        diag = askUserDialog(_("""\
You are about to upload <b>%s</b>
to AnkiOnline. This will overwrite
the online copy of this deck.
Are you sure?""" % deckName),
                          [_("Upload"),
                           _("Cancel")])
        diag.setDefault(1)
        ret = diag.run()
        if ret == _("Upload"):
            self.syncThread.clobberChoice = "overwrite"
        else:
            self.syncThread.clobberChoice = "cancel"

    def onSyncFinished(self):
        "Reopen after sync finished."
        self.form.buttonStack.show()
        try:
            try:
                if not self.showBrowser:
                    # no deck load & no deck browser, as we're about to quit or do
                    # something manually
                    pass
                else:
                    if self.loadAfterSync == -1:
                        # after sync all, so refresh browser list
                        self.browserLastRefreshed = 0
                        self.moveToState("deckBrowser")
                    elif self.loadAfterSync and self.deckPath:
                        if self.loadAfterSync == 2:
                            name = re.sub("[<>]", "", self.syncName)
                            p = os.path.join(self.config['documentDir'], name + ".anki")
                            shutil.copy2(self.deckPath, p)
                            self.deckPath = p
                            # since we've moved the deck, we have to set sync path
                            # ourselves
                            c = sqlite.connect(p)
                            v = c.execute(
                                "select version from decks").fetchone()[0]
                            if v >= 52:
                                # deck has bene upgraded already, so we can
                                # use a checksum
                                name = checksum(p.encode("utf-8"))
                            else:
                                # FIXME: compat code because deck hasn't been
                                # upgraded yet. can be deleted in the future.
                                # strip off .anki part
                                name = os.path.splitext(
                                    os.path.basename(p))[0]
                            c.execute("update decks set syncName = ?", (name,))
                            c.commit()
                            c.close()
                        self.loadDeck(self.deckPath)
                    else:
                        self.moveToState("deckBrowser")
            except:
                self.moveToState("deckBrowser")
                raise
        finally:
            self.deckPath = None
            self.syncFinished = True

    def selectSyncDeck(self, decks):
        name = aqt.sync.DeckChooser(self, decks).getName()
        self.syncName = name
        if name:
            # name chosen
            p = os.path.join(self.config['documentDir'], name + ".anki")
            if os.path.exists(p):
                d = askUserDialog(_("""\
This deck already exists on your computer. Overwrite the local copy?"""),
                                         ["Overwrite", "Cancel"])
                d.setDefault(1)
                if d.run() == "Overwrite":
                    self.syncDeck(interactive=False, onlyMerge=True)
                else:
                    self.syncFinished = True
                    self.cleanNewDeck()
            else:
                self.syncDeck(interactive=False, onlyMerge=True)
            return
        self.syncFinished = True
        self.cleanNewDeck()

    def cleanNewDeck(self):
        "Unload a new deck if an initial sync failed."
        self.deck = None
        self.deckPath = None
        self.moveToState("deckBrowser")
        self.syncFinished = True

    def setSyncStatus(self, text, *args):
        self.form.welcomeText.append("<font size=+2>" + text + "</font>")

    def syncClockOff(self, diff):
        showWarning(
            _("The time or date on your computer is not correct.\n") +
            ngettext("It is off by %d second.\n\n",
                "It is off by %d seconds.\n\n", diff) % diff +
            _("Since this can cause many problems with syncing,\n"
              "syncing is disabled until you fix the problem.")
            )
        self.onSyncFinished()

    def showSyncWarning(self, text):
        showWarning(text, self)
        self.setStatus("")

    def badUserPass(self):
        aqt.preferences.Preferences(self, self.config).dialog.tabWidget.\
                                         setCurrentIndex(1)

    def openSyncProgress(self):
        self.syncProgressDialog = QProgressDialog(_("Syncing Media..."),
                                                  "", 0, 0, self)
        self.syncProgressDialog.setWindowTitle(_("Syncing Media..."))
        self.syncProgressDialog.setCancelButton(None)
        self.syncProgressDialog.setAutoClose(False)
        self.syncProgressDialog.setAutoReset(False)

    def closeSyncProgress(self):
        self.syncProgressDialog.cancel()

    def updateSyncProgress(self, args):
        (type, x, y, fname) = args
        self.syncProgressDialog.setMaximum(y)
        self.syncProgressDialog.setValue(x)
        self.syncProgressDialog.setMinimumDuration(0)
        if type == "up":
            self.syncProgressDialog.setLabelText("Uploading %s..." % fname)
        else:
            self.syncProgressDialog.setLabelText("Downloading %s..." % fname)

    def bulkSyncFailed(self):
        showWarning(_(
            "Failed to upload media. Please run 'check media db'."), self)

    def fullSyncStarted(self, max):
        self.startProgress(max=max)

    def fullSyncFinished(self):
        self.finishProgress()
        # need to deactivate interface again
        self.setEnabled(False)

    def fullSyncProgress(self, type, val):
        if type == "fromLocal":
            s = _("Uploaded %dKB to server...")
            self.updateProgress(label=s % (val / 1024), value=val)
        else:
            s = _("Downloaded %dKB from server...")
            self.updateProgress(label=s % (val / 1024))

    def ensureSyncParams(self):
        if not self.config['syncUsername'] or not self.config['syncPassword']:
            d = QDialog(self)
            vbox = QVBoxLayout()
            l = QLabel(_(
                '<h1>Online Account</h1>'
                'To use your free <a href="http://ankiweb.net/">online account</a>,<br>'
                "please enter your details below.<br><br>"
                "You can change your details later with<br>"
                "Settings->Preferences->Sync<br>"))
            l.setOpenExternalLinks(True)
            vbox.addWidget(l)
            g = QGridLayout()
            l1 = QLabel(_("Username:"))
            g.addWidget(l1, 0, 0)
            user = QLineEdit()
            g.addWidget(user, 0, 1)
            l2 = QLabel(_("Password:"))
            g.addWidget(l2, 1, 0)
            passwd = QLineEdit()
            passwd.setEchoMode(QLineEdit.Password)
            g.addWidget(passwd, 1, 1)
            vbox.addLayout(g)
            bb = QDialogButtonBox(QDialogButtonBox.Ok|QDialogButtonBox.Cancel)
            self.connect(bb, SIGNAL("accepted()"), d.accept)
            self.connect(bb, SIGNAL("rejected()"), d.reject)
            vbox.addWidget(bb)
            d.setLayout(vbox)
            d.exec_()
            self.config['syncUsername'] = unicode(user.text())
            self.config['syncPassword'] = unicode(passwd.text())


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
There was a connection error. If it persists, please try disabling your
firewall software temporarily, or try again from a different network.

Debugging info: %s""") % error.data.get("exc", "<none>")
        else:
            tb = traceback.format_exc()
            if "missingNotes" in tb:
                msg=_("""Notes were missing after sync, so the \
sync was aborted. Please report this error.""")
            else:
                msg=_("Unknown error: %s") % tb
        return msg

    def connect(self, *args):
        # connect, check auth
        if not self.proxy:
            self.setStatus(_("Connecting..."), 0)
            proxy = HttpSyncServerProxy(self.user, self.pwd)
            proxy.connect("aqt-" + ankiqt.appVersion)
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
                    self.deck.db.commit()
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
        self.dialog = aqt.forms.syncdeck.Ui_DeckChooser()
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
