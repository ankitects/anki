# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys, os, re, traceback, time
from cStringIO import StringIO
from aqt.qt import *
from aqt.utils import showInfo, showWarning, openFolder, isWin, openLink, \
    askUser
from anki.hooks import runHook, addHook, remHook
from aqt.webview import AnkiWebView
from zipfile import ZipFile
import aqt.forms
import aqt
from anki.sync import httpCon
import aqt.sync # monkey-patches httplib2

# in the future, it would be nice to save the addon id and unzippped file list
# to the config so that we can clear up all files and check for updates

class AddonManager(object):

    def __init__(self, mw):
        self.mw = mw
        f = self.mw.form; s = SIGNAL("triggered()")
        self.mw.connect(f.actionOpenPluginFolder, s, self.onOpenAddonFolder)
        self.mw.connect(f.actionDownloadSharedPlugin, s, self.onGetAddons)
        self._menus = []
        if isWin:
            self.clearAddonCache()
        sys.path.insert(0, self.addonsFolder())
        self.loadAddons()

    def files(self):
        return [f for f in os.listdir(self.addonsFolder())
                if f.endswith(".py")]

    def loadAddons(self):
        for file in self.files():
            try:
                __import__(file.replace(".py", ""))
            except:
                traceback.print_exc()
        self.rebuildAddonsMenu()

    # Menus
    ######################################################################

    def onOpenAddonFolder(self, path=None):
        if path is None:
            path = self.addonsFolder()
        openFolder(path)

    def rebuildAddonsMenu(self):
        for m in self._menus:
            self.mw.form.menuPlugins.removeAction(m.menuAction())
        for file in self.files():
            m = self.mw.form.menuPlugins.addMenu(
                os.path.splitext(file)[0])
            self._menus.append(m)
            a = QAction(_("Edit..."), self.mw)
            p = os.path.join(self.addonsFolder(), file)
            self.mw.connect(a, SIGNAL("triggered()"),
                            lambda p=p: self.onEdit(p))
            m.addAction(a)
            a = QAction(_("Delete..."), self.mw)
            self.mw.connect(a, SIGNAL("triggered()"),
                            lambda p=p: self.onRem(p))
            m.addAction(a)

    def onEdit(self, path):
        d = QDialog(self.mw)
        frm = aqt.forms.editaddon.Ui_Dialog()
        frm.setupUi(d)
        d.setWindowTitle(os.path.basename(path))
        frm.text.setPlainText(unicode(open(path).read(), "utf8"))
        d.connect(frm.buttonBox, SIGNAL("accepted()"),
                  lambda: self.onAcceptEdit(path, frm))
        d.exec_()

    def onAcceptEdit(self, path, frm):
        open(path, "w").write(frm.text.toPlainText().encode("utf8"))
        showInfo(_("Edits saved. Please restart Anki."))

    def onRem(self, path):
        if not askUser(_("Delete %s?") % os.path.basename(path)):
            return
        os.unlink(path)
        self.rebuildAddonsMenu()
        showInfo(_("Deleted. Please restart Anki."))

    # Tools
    ######################################################################

    def addonsFolder(self):
        dir = self.mw.pm.addonFolder()
        if isWin:
            dir = dir.encode(sys.getfilesystemencoding())
        return dir

    def clearAddonCache(self):
        "Clear .pyc files which may cause crashes if Python version updated."
        dir = self.addonsFolder()
        for curdir, dirs, files in os.walk(dir):
            for f in files:
                if not f.endswith(".pyc"):
                    continue
                os.unlink(os.path.join(curdir, f))

    def registerAddon(self, name, updateId):
        # not currently used
        return

    # Installing add-ons
    ######################################################################

    def onGetAddons(self):
        GetAddons(self.mw)

    def install(self, data, fname):
        if fname.endswith(".py"):
            # .py files go directly into the addon folder
            path = os.path.join(self.addonsFolder(), fname)
            open(path, "w").write(data)
            return
        # .zip file
        z = ZipFile(StringIO(data))
        base = self.addonsFolder()
        for n in z.namelist():
            if n.endswith("/"):
                # folder; ignore
                continue
            # write
            z.extract(n, base)

class GetAddons(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.getaddons.Ui_Dialog()
        self.form.setupUi(self)
        b = self.form.buttonBox.addButton(
            _("Browse"), QDialogButtonBox.ActionRole)
        self.connect(b, SIGNAL("clicked()"), self.onBrowse)
        self.exec_()

    def onBrowse(self):
        openLink(aqt.appShared + "addons/")

    def accept(self):
        try:
            code = int(self.form.code.text())
        except ValueError:
            showWarning(_("Invalid code."))
            return
        QDialog.accept(self)
        # create downloader thread
        self.thread = AddonDownloader(code)
        self.connect(self.thread, SIGNAL("recv"), self.onRecv)
        self.recvBytes = 0
        self.thread.start()
        self.mw.progress.start(immediate=True)
        while not self.thread.isFinished():
            self.mw.app.processEvents()
            self.thread.wait(100)
        if not self.thread.error:
            # success
            self.mw.addonManager.install(self.thread.data, self.thread.fname)
            self.mw.progress.finish()
            showInfo(_("Download successful. Please restart Anki."))
        else:
            self.mw.progress.finish()
            showWarning(_("Download failed: %s") % self.thread.error)

    def onRecv(self, total):
        self.mw.progress.update(label="%dKB downloaded" % (total/1024))

class AddonDownloader(QThread):

    def __init__(self, code):
        QThread.__init__(self)
        self.code = code
        self.error = None

    def run(self):
        # setup progress handler
        self.byteUpdate = time.time()
        self.recvTotal = 0
        def canPost():
            if (time.time() - self.byteUpdate) > 0.1:
                self.byteUpdate = time.time()
                return True
        def recvEvent(bytes):
            self.recvTotal += bytes
            if canPost():
                self.emit(SIGNAL("recv"), self.recvTotal)
        addHook("httpRecv", recvEvent)
        con =  httpCon()
        try:
            resp, cont = con.request(
                aqt.appShared + "download/%d" % self.code)
        except Exception, e:
            self.error = unicode(e, "utf8", "replace")
            return
        finally:
            remHook("httpRecv", recvEvent)
        if resp['status'] == '200':
            self.error = None
            self.fname = re.match("attachment; filename=(.+)",
                                  resp['content-disposition']).group(1)
            self.data = cont
        elif resp['status'] == '403':
            self.error = _("Invalid code.")
        else:
            self.error = _("Error downloading: %s") % resp['status']
