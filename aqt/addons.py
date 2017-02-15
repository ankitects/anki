# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import io
import sys, os, traceback
from io import StringIO
import zipfile
from aqt.qt import *
from aqt.utils import showInfo, openFolder, isWin, openLink, \
    askUser, restoreGeom, saveGeom, showWarning, tooltip
from zipfile import ZipFile
import aqt.forms
import aqt
from aqt.downloader import download
from anki.lang import _

# in the future, it would be nice to save the addon id and unzippped file list
# to the config so that we can clear up all files and check for updates

class AddonManager:

    def __init__(self, mw):
        self.mw = mw
        f = self.mw.form
        f.actionOpenPluginFolder.triggered.connect(self.onOpenAddonFolder)
        f.actionDownloadSharedPlugin.triggered.connect(self.onGetAddons)
        self._menus = []
        if isWin:
            self.clearAddonCache()
        sys.path.insert(0, self.addonsFolder())
        if not self.mw.safeMode:
            self.loadAddons()

    def files(self):
        return [f for f in os.listdir(self.addonsFolder())
                if f.endswith(".py")]

    def directories(self):
        return [d for d in os.listdir(self.addonsFolder())
                if not d.startswith('.') and
                not d == "__pycache__" and
                os.path.isdir(os.path.join(self.addonsFolder(), d))]

    def loadAddons(self):
        for file in self.files():
            try:
                __import__(file.replace(".py", ""))
            except:
                traceback.print_exc()
        for directory in self.directories():
            try:
                __import__(directory)
            except:
                traceback.print_exc()
        self.rebuildAddonsMenu()

    # Menus
    ######################################################################

    def onOpenAddonFolder(self, checked, path=None):
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
            p = os.path.join(self.addonsFolder(), file)
            a = QAction(_("Edit..."), self.mw, triggered=lambda x, y=p: self.onEdit(y))
            m.addAction(a)
            a = QAction(_("Delete..."), self.mw, triggered=lambda x, y=p: self.onRem(y))
            m.addAction(a)

    def onEdit(self, path):
        d = QDialog(self.mw)
        frm = aqt.forms.editaddon.Ui_Dialog()
        frm.setupUi(d)
        d.setWindowTitle(os.path.basename(path))
        frm.text.setPlainText(open(path).read())
        frm.buttonBox.accepted.connect(lambda: self.onAcceptEdit(path, frm))
        d.exec_()

    def onAcceptEdit(self, path, frm):
        open(path, "wb").write(frm.text.toPlainText().encode("utf8"))
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
        showInfo("""\
Most add-ons built for Anki 2.0.x will not work on this version of Anki \
until they are updated. To avoid errors during startup, please only \
download add-ons that say they support Anki 2.1.x in the description.""")
        GetAddons(self.mw)

    def install(self, data, fname):
        if fname.endswith(".py"):
            # .py files go directly into the addon folder
            path = os.path.join(self.addonsFolder(), fname)
            open(path, "wb").write(data)
            return
        # .zip file
        try:
            z = ZipFile(io.BytesIO(data))
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
        b.clicked.connect(self.onBrowse)
        restoreGeom(self, "getaddons", adjustSize=True)
        self.exec_()
        saveGeom(self, "getaddons")

    def onBrowse(self):
        openLink(aqt.appShared + "addons/")

    def accept(self):
        QDialog.accept(self)

        # get codes
        try:
            ids = [int(n) for n in self.form.code.text().split()]
        except ValueError:
            showWarning(_("Invalid code."))
            return

        errors = []

        self.mw.progress.start(immediate=True)
        for n in ids:
            ret = download(self.mw, n)
            if ret[0] == "error":
                errors.append(_("Error downloading %(id)s: %(error)s") % dict(id=n, error=ret[1]))
                continue
            data, fname = ret
            self.mw.addonManager.install(data, fname)
        self.mw.progress.finish()
        if not errors:
            tooltip(_("Download successful. Please restart Anki."), period=3000)
        else:
            showWarning("\n".join(errors))
