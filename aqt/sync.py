# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import os, types, socket, time, traceback, gc
import aqt
from anki import Collection
from anki.sync import Syncer, RemoteServer, FullSyncer, MediaSyncer, \
    RemoteMediaServer
from anki.hooks import addHook, remHook
from aqt.utils import tooltip, askUserDialog, showWarning, showText

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
        self.label = _("Connecting...")
        self.mw.progress.start(immediate=True, label=self.label)
        self.sentBytes = self.recvBytes = 0
        self._updateLabel()
        self.thread.start()
        while not self.thread.isFinished():
            self.mw.app.processEvents()
            self.thread.wait(100)
        self.mw.progress.finish()

    def _updateLabel(self):
        self.mw.progress.update(label="%s\n%s" % (
            self.label,
            _("%(a)dkB up, %(b)dkB down") % dict(
                a=self.sentBytes/1024,
                b=self.recvBytes/1024)))

    def onEvent(self, evt, *args):
        pu = self.mw.progress.update
        if evt == "badAuth":
            tooltip(
                _("AnkiWeb ID or password was incorrect; please try again."),
                parent=self.mw)
            # blank the key so we prompt user again
            self.pm.profile['syncKey'] = None
            self.pm.save()
        elif evt == "newKey":
            self.pm.profile['syncKey'] = args[0]
            self.pm.save()
        elif evt == "offline":
            tooltip(_("Syncing failed; internet offline."))
        elif evt == "sync":
            m = None; t = args[0]
            if t == "login":
                m = _("Syncing...")
            elif t == "upload":
                m = _("Uploading to AnkiWeb...")
            elif t == "download":
                m = _("Downloading from AnkiWeb...")
            elif t == "sanity":
                m = _("Checking...")
            elif t == "findMedia":
                m = _("Syncing Media...")
            if m:
                self.label = m
                self._updateLabel()
        elif evt == "error":
            showText(_("Syncing failed:\n%s")%
                     self._rewriteError(args[0]))
        elif evt == "clockOff":
            self._clockOff()
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
        elif "timed out" in err:
            return _("""\
The connection to AnkiWeb timed out. Please check your network \
connection and try again.""")
        elif "500" in err:
            return _("""\
AnkiWeb encountered an error. Please try again in a few minutes, and if \
the problem persists, please file a bug report.""")
        elif "501" in err:
            return _("""\
Please upgrade to the latest version of Anki.""")
        # 502 is technically due to the server restarting, but we reuse the
        # error message
        elif "502" in err or "503" in err or "504" in err:
            return _("""\
AnkiWeb is too busy at the moment. Please try again in a few minutes.""")
        return err

    def _getUserPass(self):
        d = QDialog(self.mw)
        d.setWindowTitle("Anki")
        vbox = QVBoxLayout()
        l = QLabel(_("""\
<h1>Account Required</h1>
A free account is required to keep your collection synchronized. Please \
<a href="%s">sign up</a> for an account, then \
enter your details below.""") %
                   "http://beta.ankiweb.net/account/login")
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
Your decks here and on AnkiWeb differ in such a way that they can't \
be merged together, so it's necessary to overwrite the decks on one \
side with the decks from the other.

Do you want to upload the decks from here, or download the decks \
from AnkiWeb?"""),
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
        self.sentTotal = 0
        self.recvTotal = 0
        # throttle updates; qt doesn't handle lots of posted events well
        self.byteUpdate = time.time()
        def syncEvent(type):
            self.fireEvent("sync", type)
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
            err = unicode(e)
            if "Unable to find the server" in err:
                self.fireEvent("offline")
            else:
                self.fireEvent("error", err)
            return
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
        else:
            self.client.download()
        # reopen db and move on to media sync
        self.col.reopen()
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


# Monkey-patch httplib & httplib2 so we can get progress info
######################################################################

CHUNK_SIZE = 65536
import httplib, httplib2, socket, errno
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
        data = StringIO(data)
    while 1:
        block = data.read(CHUNK_SIZE)
        if not block:
            break
        self.sock.sendall(block)
        runHook("httpSend", len(block))

httplib.HTTPConnection.send = _incrementalSend

# receiving in httplib2
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
            err = 0
            if hasattr(e, 'args'):
                err = getattr(e, 'args')[0]
            else:
                err = e.errno
            if err == errno.ECONNREFUSED: # Connection refused
                raise
        except httplib.HTTPException:
            # Just because the server closed the connection doesn't apparently mean
            # that the server didn't send a response.
            if conn.sock is None:
                if i == 0:
                    conn.close()
                    conn.connect()
                    continue
                else:
                    conn.close()
                    raise
            if i == 0:
                conn.close()
                conn.connect()
                continue
            pass
        try:
            response = conn.getresponse()
        except (socket.error, httplib.HTTPException):
            if i == 0:
                conn.close()
                conn.connect()
                continue
            else:
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
        break
    return (response, content)

httplib2.Http._conn_request = _conn_request
