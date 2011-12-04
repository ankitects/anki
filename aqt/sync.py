# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import os, types, socket, time, traceback, gc
import aqt
from anki import Collection
from anki.sync import Syncer, RemoteServer, FullSyncer, MediaSyncer, \
    RemoteMediaServer
from anki.hooks import addHook, removeHook
from aqt.utils import tooltip, askUserDialog, showWarning

# Sync manager
######################################################################

# are we doing this in main?
#            self.closeAllDeckWindows()

class SyncManager(QObject):

    def __init__(self, mw, pm):
        QObject.__init__(self, mw)
        self.mw = mw
        self.pm = pm

    def sync(self, auto=False):
        if not self.pm.profile['syncKey']:
            if auto:
                return
            auth = self._getUserPass()
            if not auth:
                return
            self._sync(auth)
        else:
            self._sync()

    def _sync(self, auth=None):
        # to avoid gui widgets being garbage collected in the worker thread,
        # run gc in advance
        gc.collect()
        # create the thread, setup signals and start running
        t = self.thread = SyncThread(
            self.pm.collectionPath(), self.pm.profile['syncKey'],
            auth=auth, media=self.pm.profile['syncMedia'])
        self.connect(t, SIGNAL("event"), self.onEvent)
        self.mw.progress.start(immediate=True, label=_("Syncing..."))
        self.thread.start()
        while not self.thread.isFinished():
            self.mw.app.processEvents()
            self.thread.wait(100)
        self.mw.progress.finish()

    def onEvent(self, evt, *args):
        if evt == "badAuth":
            tooltip(
                _("AnkiWeb ID or password was incorrect; please try again."),
                parent=self.mw)
        elif evt == "newKey":
            self.pm.profile['syncKey'] = args[0]
            self.pm.save()
        elif evt == "sync":
            self.mw.progress.update(label="sync: "+args[0])
        elif evt == "mediaSync":
            self.mw.progress.update(label="media: "+args[0])
        elif evt == "error":
            showWarning(_("Syncing failed:\n%s")%
                        self._rewriteError(args[0]))
        elif evt == "clockOff":
            print "clock is wrong"
        elif evt == "noChanges":
            pass
        elif evt == "fullSync":
            self._confirmFullSync()
        elif evt == "success":
            print "sync successful"
        elif evt == "upload":
            print "upload successful"
        elif evt == "download":
            print "download successful"
        elif evt == "noMediaChanges":
            print "no media changes"
        elif evt == "mediaSuccess":
            print "media sync successful"
        else:
            print "unknown evt", evt

    def _rewriteError(self, err):
        if "Errno 61" in err:
            return _("""\
Couldn't connect to AnkiWeb. Please check your network connection \
and try again.""")
        return err

    def _getUserPass(self):
        d = QDialog(self.mw)
        d.setWindowTitle("Anki")
        vbox = QVBoxLayout()
        l = QLabel(_("""\
<h1>Account Required</h1>
A free account is required to keep your collection synchronized. Please \
<a href="http://ankiweb.net/account/login">sign up</a> for an account, then \
enter your details below."""))
        l.setOpenExternalLinks(True)
        l.setWordWrap(True)
        vbox.addWidget(l)
        vbox.addSpacing(20)
        g = QGridLayout()
        l1 = QLabel(_("AnkiWeb ID:"))
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
        bb.button(QDialogButtonBox.Ok).setAutoDefault(True)
        self.connect(bb, SIGNAL("accepted()"), d.accept)
        self.connect(bb, SIGNAL("rejected()"), d.reject)
        vbox.addWidget(bb)
        d.setLayout(vbox)
        d.show()
        d.exec_()
        u = user.text()
        p = passwd.text()
        if not u or not p:
            return
        return (u, p)

    def _confirmFullSync(self):
        diag = askUserDialog(_("""\
Because this is your first time synchronizing, or because unmergable \
changes have been made, your collection needs to be either uploaded or \
downloaded in full.

Do you want to keep the local version, overwriting the AnkiWeb version? Or \
do you want to keep the AnkiWeb version, overwriting the version here?"""),
                [_("Keep Local"),
                 _("Keep AnkiWeb"),
                 _("Cancel")])
        diag.setDefault(2)
        ret = diag.run()
        if ret == _("Keep Local"):
            self.thread.fullSyncChoice = "upload"
        elif ret == _("Keep AnkiWeb"):
            self.thread.fullSyncChoice = "download"
        else:
            self.thread.fullSyncChoice = "cancel"

    def syncClockOff(self, diff):
        showWarning(
            _("The time or date on your computer is not correct.\n") +
            ngettext("It is off by %d second.\n\n",
                "It is off by %d seconds.\n\n", diff) % diff +
            _("Since this can cause many problems with syncing,\n"
              "syncing is disabled until you fix the problem.")
            )
        self.onSyncFinished()

    def badUserPass(self):
        aqt.preferences.Preferences(self, self.pm.profile).dialog.tabWidget.\
                                         setCurrentIndex(1)

# Sync thread
######################################################################

class SyncThread(QThread):

    def __init__(self, path, hkey, auth=None, media=True):
        QThread.__init__(self)
        self.path = path
        self.hkey = hkey
        self.auth = auth
        self.media = media

    def run(self):
        self.col = Collection(self.path)
        self.server = RemoteServer(self.hkey)
        self.client = Syncer(self.col, self.server)
        def syncEvent(type):
            self.fireEvent("sync", type)
        def mediaSync(type):
            self.fireEvent("mediaSync", type)
        addHook("sync", syncEvent)
        addHook("mediaSync", mediaSync)
        # run sync and catch any errors
        try:
            self._sync()
        except:
            err = traceback.format_exc()
            print err
            self.fireEvent("error", err)
        finally:
            # don't bump mod time unless we explicitly save
            self.col.close(save=False)

    def _sync(self):
        if self.auth:
            # need to authenticate and obtain host key
            hkey = self.server.hostKey(*self.auth)
            if not hkey:
                # provided details were invalid
                return self.fireEvent("badAuth")
            else:
                # write new details and tell calling thread to save
                self.fireEvent("newKey", hkey)
        # run sync and check state
        ret = self.client.sync()
        if ret == "badAuth":
            return self.fireEvent("badAuth")
        elif ret == "clockOff":
            return self.fireEvent("clockOff")
        # note mediaUSN for later
        self.mediaUsn = self.client.mediaUsn
        # full sync?
        if ret == "fullSync":
            return self._fullSync()
        # save and note success state
        self.col.save()
        if ret == "noChanges":
            self.fireEvent("noChanges")
        else:
            self.fireEvent("success")
        # then move on to media sync
        self._syncMedia()

    def _fullSync(self):
        # tell the calling thread we need a decision on sync direction, and
        # wait for a reply
        self.fullSyncChoice = False
        self.fireEvent("fullSync")
        while not self.fullSyncChoice:
            time.sleep(0.1)
        f = self.fullSyncChoice
        if f == "cancel":
            return
        self.client = FullSyncer(self.col, self.hkey, self.server.con)
        if f == "upload":
            self.client.upload()
            self.fireEvent("upload")
        else:
            self.client.download()
            self.fireEvent("download")
        # move on to media sync
        self._syncMedia()

    def _syncMedia(self):
        if not self.media:
            return
        self.server = RemoteMediaServer(self.hkey, self.server.con)
        self.client = MediaSyncer(self.col, self.server)
        ret = self.client.sync(self.mediaUsn)
        if ret == "noChanges":
            self.fireEvent("noMediaChanges")
        else:
            self.fireEvent("mediaSuccess")

    def fireEvent(self, *args):
        self.emit(SIGNAL("event"), *args)
