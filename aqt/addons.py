# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys, os, traceback
from cStringIO import StringIO
import zipfile
from aqt.qt import *
from aqt.utils import showInfo, openFolder, isWin, openLink, \
    askUser, restoreGeom, saveGeom, showWarning
from zipfile import ZipFile
import aqt.forms
import aqt
from aqt.downloader import download
from anki.lang import _

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
        if not self.mw.safeMode:
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
            open(path, "wb").write(data)
            return
        # .zip file
        try:
            z = ZipFile(StringIO(data))
        except zipfile.BadZipfile:
            showWarning(_("The download was corrupt. Please try again."))
            return
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
        restoreGeom(self, "getaddons", adjustSize=True)
        self.exec_()
        saveGeom(self, "getaddons")

    def onBrowse(self):
        openLink(aqt.appShared + "addons/")

    def accept(self):
        QDialog.accept(self)
        # create downloader thread
        ret = download(self.mw, self.form.code.text())
        if not ret:
            return
        data, fname = ret
        self.mw.addonManager.install(data, fname)
        self.mw.progress.finish()
        showInfo(_("Download successful. Please restart Anki."))
