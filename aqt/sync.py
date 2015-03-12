# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import division
import socket
import time
import traceback
import gc

from aqt.qt import *
import aqt
from anki import Collection
from anki.sync import Syncer, RemoteServer, FullSyncer, MediaSyncer, \
    RemoteMediaServer
from anki.hooks import addHook, remHook
from aqt.utils import tooltip, askUserDialog, showWarning, showText, showInfo


# Sync manager
######################################################################

class SyncManager(QObject):

    def __init__(self, mw, pm):
        QObject.__init__(self, mw)
        self.mw = mw
        self.pm = pm

    def sync(self):
        if not self.pm.profile['syncKey']:
            auth = self._getUserPass()
            if not auth:
                return
            self.pm.profile['syncUser'] = auth[0]
            self._sync(auth)
        else:
            self._sync()

    def _sync(self, auth=None):
        # to avoid gui widgets being garbage collected in the worker thread,
        # run gc in advance
        self._didFullUp = False
        self._didError = False
        gc.collect()
        # create the thread, setup signals and start running
        t = self.thread = SyncThread(
            self.pm.collectionPath(), self.pm.profile['syncKey'],
            auth=auth, media=self.pm.profile['syncMedia'])
        self.connect(t, SIGNAL("event"), self.onEvent)
        self.label = _("Connecting...")
        self.mw.progress.start(immediate=True, label=self.label)
        self.sentBytes = self.recvBytes = 0
        self._updateLabel()
        self.thread.start()
        while not self.thread.isFinished():
            self.mw.app.processEvents()
            self.thread.wait(100)
        self.mw.progress.finish()
        if self.thread.syncMsg:
            showText(self.thread.syncMsg)
        if self.thread.uname:
            self.pm.profile['syncUser'] = self.thread.uname
        def delayedInfo():
            if self._didFullUp and not self._didError:
                showInfo(_("""\
Your collection was successfully uploaded to AnkiWeb.

If you use any other devices, please sync them now, and choose \
to download the collection you have just uploaded from this computer. \
After doing so, future reviews and added cards will be merged \
automatically."""))
        self.mw.progress.timer(1000, delayedInfo, False)

    def _updateLabel(self):
        self.mw.progress.update(label="%s\n%s" % (
            self.label,
            _("%(a)dkB up, %(b)dkB down") % dict(
                a=self.sentBytes // 1024,
                b=self.recvBytes // 1024)))

    def onEvent(self, evt, *args):
        pu = self.mw.progress.update
        if evt == "badAuth":
            tooltip(
                _("AnkiWeb ID or password was incorrect; please try again."),
                parent=self.mw)
            # blank the key so we prompt user again
            self.pm.profile['syncKey'] = None
            self.pm.save()
        elif evt == "corrupt":
            pass
        elif evt == "newKey":
            self.pm.profile['syncKey'] = args[0]
            self.pm.save()
        elif evt == "offline":
            tooltip(_("Syncing failed; internet offline."))
        elif evt == "upbad":
            self._didFullUp = False
            self._checkFailed()
        elif evt == "sync":
            m = None; t = args[0]
            if t == "login":
                m = _("Syncing...")
            elif t == "upload":
                self._didFullUp = True
                m = _("Uploading to AnkiWeb...")
            elif t == "download":
                m = _("Downloading from AnkiWeb...")
            elif t == "sanity":
                m = _("Checking...")
            elif t == "findMedia":
                m = _("Syncing Media...")
            elif t == "upgradeRequired":
                showText(_("""\
Please visit AnkiWeb, upgrade your deck, then try again."""))
            if m:
                self.label = m
                self._updateLabel()
        elif evt == "syncMsg":
            self.label = args[0]
            self._updateLabel()
        elif evt == "error":
            self._didError = True
            showText(_("Syncing failed:\n%s")%
                     self._rewriteError(args[0]))
        elif evt == "clockOff":
            self._clockOff()
        elif evt == "checkFailed":
            self._checkFailed()
        elif evt == "mediaSanity":
            showWarning(_("""\
A problem occurred while syncing media. Please use Tools>Check Media, then \
sync again to correct the issue."""))
        elif evt == "noChanges":
            pass
        elif evt == "fullSync":
            self._confirmFullSync()
        elif evt == "send":
            # posted events not guaranteed to arrive in order
            self.sentBytes = max(self.sentBytes, args[0])
            self._updateLabel()
        elif evt == "recv":
            self.recvBytes = max(self.recvBytes, args[0])
            self._updateLabel()

    def _rewriteError(self, err):
        if "Errno 61" in err:
            return _("""\
Couldn't connect to AnkiWeb. Please check your network connection \
and try again.""")
        elif "timed out" in err or "10060" in err:
            return _("""\
The connection to AnkiWeb timed out. Please check your network \
connection and try again.""")
        elif "code: 500" in err:
            return _("""\
AnkiWeb encountered an error. Please try again in a few minutes, and if \
the problem persists, please file a bug report.""")
        elif "code: 501" in err:
            return _("""\
Please upgrade to the latest version of Anki.""")
        # 502 is technically due to the server restarting, but we reuse the
        # error message
        elif "code: 502" in err:
            return _("AnkiWeb is under maintenance. Please try again in a few minutes.")
        elif "code: 503" in err:
            return _("""\
AnkiWeb is too busy at the moment. Please try again in a few minutes.""")
        elif "code: 504" in err:
            return _("504 gateway timeout error received. Please try temporarily disabling your antivirus.")
        elif "code: 409" in err:
            return _("Only one client can access AnkiWeb at a time. If a previous sync failed, please try again in a few minutes.")
        elif "10061" in err or "10013" in err or "10053" in err:
            return _(
                "Antivirus or firewall software is preventing Anki from connecting to the internet.")
        elif "10054" in err or "Broken pipe" in err:
            return _("Connection timed out. Either your internet connection is experiencing problems, or you have a very large file in your media folder.")
        elif "Unable to find the server" in err:
            return _(
                "Server not found. Either your connection is down, or antivirus/firewall "
                "software is blocking Anki from connecting to the internet.")
        elif "code: 407" in err:
            return _("Proxy authentication required.")
        elif "code: 413" in err:
            return _("Your collection or a media file is too large to sync.")
        elif "EOF occurred in violation of protocol" in err:
            return _("Error establishing a secure connection. This is usually caused by antivirus, firewall or VPN software, or problems with your ISP.")
        elif "certificate verify failed" in err:
            return _("Error establishing a secure connection. This is usually caused by antivirus, firewall or VPN software, or problems with your ISP.")
        return err

    def _getUserPass(self):
        d = QDialog(self.mw)
        d.setWindowTitle("Anki")
        d.setWindowModality(Qt.WindowModal)
        vbox = QVBoxLayout()
        l = QLabel(_("""\
<h1>Account Required</h1>
A free account is required to keep your collection synchronized. Please \
<a href="%s">sign up</a> for an account, then \
enter your details below.""") %
                   "https://ankiweb.net/account/login")
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
        accepted = d.exec_()
        u = user.text()
        p = passwd.text()
        if not accepted or not u or not p:
            return
        return (u, p)

    def _confirmFullSync(self):
        diag = askUserDialog(_("""\
Your decks here and on AnkiWeb differ in such a way that they can't \
be merged together, so it's necessary to overwrite the decks on one \
side with the decks from the other.

If you choose download, Anki will download the collection from AnkiWeb, \
and any changes you have made on your computer since the last sync will \
be lost.

If you choose upload, Anki will upload your collection to AnkiWeb, and \
any changes you have made on AnkiWeb or your other devices since the \
last sync to this device will be lost.

After all devices are in sync, future reviews and added cards can be merged \
automatically."""),
                [_("Upload to AnkiWeb"),
                 _("Download from AnkiWeb"),
                 _("Cancel")])
        diag.setDefault(2)
        ret = diag.run()
        if ret == _("Upload to AnkiWeb"):
            self.thread.fullSyncChoice = "upload"
        elif ret == _("Download from AnkiWeb"):
            self.thread.fullSyncChoice = "download"
        else:
            self.thread.fullSyncChoice = "cancel"

    def _clockOff(self):
        showWarning(_("""\
Syncing requires the clock on your computer to be set correctly. Please \
fix the clock and try again."""))

    def _checkFailed(self):
        showWarning(_("""\
Your collection is in an inconsistent state. Please run Tools>\
Check Database, then sync again."""))

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
        # init this first so an early crash doesn't cause an error
        # in the main thread
        self.syncMsg = ""
        self.uname = ""
        try:
            self.col = Collection(self.path, log=True)
        except:
            self.fireEvent("corrupt")
            return
        self.server = RemoteServer(self.hkey)
        self.client = Syncer(self.col, self.server)
        self.sentTotal = 0
        self.recvTotal = 0
        # throttle updates; qt doesn't handle lots of posted events well
        self.byteUpdate = time.time()
        def syncEvent(type):
            self.fireEvent("sync", type)
        def syncMsg(msg):
            self.fireEvent("syncMsg", msg)
        def canPost():
            if (time.time() - self.byteUpdate) > 0.1:
                self.byteUpdate = time.time()
                return True
        def sendEvent(bytes):
            self.sentTotal += bytes
            if canPost():
                self.fireEvent("send", self.sentTotal)
        def recvEvent(bytes):
            self.recvTotal += bytes
            if canPost():
                self.fireEvent("recv", self.recvTotal)
        addHook("sync", syncEvent)
        addHook("syncMsg", syncMsg)
        addHook("httpSend", sendEvent)
        addHook("httpRecv", recvEvent)
        # run sync and catch any errors
        try:
            self._sync()
        except:
            err = traceback.format_exc()
            if not isinstance(err, unicode):
                err = unicode(err, "utf8", "replace")
            self.fireEvent("error", err)
        finally:
            # don't bump mod time unless we explicitly save
            self.col.close(save=False)
            remHook("sync", syncEvent)
            remHook("syncMsg", syncMsg)
            remHook("httpSend", sendEvent)
            remHook("httpRecv", recvEvent)

    def _sync(self):
        if self.auth:
            # need to authenticate and obtain host key
            self.hkey = self.server.hostKey(*self.auth)
            if not self.hkey:
                # provided details were invalid
                return self.fireEvent("badAuth")
            else:
                # write new details and tell calling thread to save
                self.fireEvent("newKey", self.hkey)
        # run sync and check state
        try:
            ret = self.client.sync()
        except Exception, e:
            log = traceback.format_exc()
            err = repr(str(e))
            if ("Unable to find the server" in err or
                "Errno 2" in err):
                self.fireEvent("offline")
            else:
                if not err:
                    err = log
                if not isinstance(err, unicode):
                    err = unicode(err, "utf8", "replace")
                self.fireEvent("error", err)
            return
        if ret == "badAuth":
            return self.fireEvent("badAuth")
        elif ret == "clockOff":
            return self.fireEvent("clockOff")
        elif ret == "basicCheckFailed" or ret == "sanityCheckFailed":
            return self.fireEvent("checkFailed")
        # full sync?
        if ret == "fullSync":
            return self._fullSync()
        # save and note success state
        if ret == "noChanges":
            self.fireEvent("noChanges")
        elif ret == "success":
            self.fireEvent("success")
        elif ret == "serverAbort":
            pass
        else:
            self.fireEvent("error", "Unknown sync return code.")
        self.syncMsg = self.client.syncMsg
        self.uname = self.client.uname
        # then move on to media sync
        self._syncMedia()

    def _fullSync(self):
        # if the local deck is empty, assume user is trying to download
        if self.col.isEmpty():
            f = "download"
        else:
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
            if not self.client.upload():
                self.fireEvent("upbad")
        else:
            self.client.download()
        # reopen db and move on to media sync
        self.col.reopen()
        self._syncMedia()

    def _syncMedia(self):
        if not self.media:
            return
        self.server = RemoteMediaServer(self.col, self.hkey, self.server.con)
        self.client = MediaSyncer(self.col, self.server)
        ret = self.client.sync()
        if ret == "noChanges":
            self.fireEvent("noMediaChanges")
        elif ret == "sanityCheckFailed":
            self.fireEvent("mediaSanity")
        else:
            self.fireEvent("mediaSuccess")

    def fireEvent(self, *args):
        self.emit(SIGNAL("event"), *args)


# Monkey-patch httplib & httplib2 so we can get progress info
######################################################################

CHUNK_SIZE = 65536
import httplib, httplib2
from cStringIO import StringIO
from anki.hooks import runHook

# sending in httplib
def _incrementalSend(self, data):
    """Send `data' to the server."""
    if self.sock is None:
        if self.auto_open:
            self.connect()
        else:
            raise httplib.NotConnected()
    # if it's not a file object, make it one
    if not hasattr(data, 'read'):
        if isinstance(data, unicode):
            data = data.encode("utf8")
        data = StringIO(data)
    while 1:
        block = data.read(CHUNK_SIZE)
        if not block:
            break
        self.sock.sendall(block)
        runHook("httpSend", len(block))

httplib.HTTPConnection.send = _incrementalSend

# receiving in httplib2
# this is an augmented version of httplib's request routine that:
# - doesn't assume requests will be tried more than once
# - calls a hook for each chunk of data so we can update the gui
# - retries only when keep-alive connection is closed
def _conn_request(self, conn, request_uri, method, body, headers):
    for i in range(2):
        try:
            if conn.sock is None:
              conn.connect()
            conn.request(method, request_uri, body, headers)
        except socket.timeout:
            raise
        except socket.gaierror:
            conn.close()
            raise httplib2.ServerNotFoundError(
                "Unable to find the server at %s" % conn.host)
        except httplib2.ssl_SSLError:
            conn.close()
            raise
        except socket.error, e:
            conn.close()
            raise
        except httplib.HTTPException:
            conn.close()
            raise
        try:
            response = conn.getresponse()
        except httplib.BadStatusLine:
            print "retry bad line"
            conn.close()
            conn.connect()
            continue
        except (socket.error, httplib.HTTPException):
            raise
        else:
            content = ""
            if method == "HEAD":
                response.close()
            else:
                buf = StringIO()
                while 1:
                    data = response.read(CHUNK_SIZE)
                    if not data:
                        break
                    buf.write(data)
                    runHook("httpRecv", len(data))
                content = buf.getvalue()
            response = httplib2.Response(response)
            if method != "HEAD":
                content = httplib2._decompressContent(response, content)
        return (response, content)

httplib2.Http._conn_request = _conn_request
