# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys, os, re, traceback
from aqt.qt import *
from aqt.utils import showInfo, showWarning, openFolder, isWin
from anki.hooks import runHook

class AddonManager(object):

    def __init__(self, mw):
        self.mw = mw
        f = self.mw.form; s = SIGNAL("triggered()")
        self.mw.connect(f.actionOpenPluginFolder, s, self.onOpenAddonFolder)
        self.mw.connect(f.actionEnableAllPlugins, s, self.onEnableAllAddons)
        self.mw.connect(f.actionDisableAllPlugins, s, self.onDisableAllAddons)
        if isWin:
            self.clearAddonCache()
        sys.path.insert(0, self.addonsFolder())
        self.loadAddons()

    def loadAddons(self):
        on, off = self.files()
        for file in on:
            try:
                __import__(file.replace(".py", ""))
            except:
                traceback.print_exc()
        self.rebuildAddonsMenu()

    # Menus
    ######################################################################

    def rebuildAddonsMenu(self):
        if getattr(self, "addonActions", None) is None:
            self.addonActions = []
        for action in self.addonActions:
            self.mw.form.menuStartup.removeAction(action)
        self.addonActions = []
        on, off = self.files()
        def addObjs(l, enabled):
            l.sort()
            for file in l:
                p = re.sub("\.py(\.off)?", "", file)
                a = QAction(p, self.mw)
                a.setCheckable(True)
                a.setChecked(enabled)
                self.mw.connect(a, SIGNAL("triggered()"),
                                lambda f=file: self.toggleAddon(f))
                self.mw.form.menuStartup.addAction(a)
                self.addonActions.append(a)
        addObjs(on, True)
        addObjs(off, False)

    def onOpenAddonFolder(self, path=None):
        if path is None:
            path = self.addonsFolder()
        openFolder(path)

    # Enabled/disabled list
    ######################################################################

    def files(self):
        on = []
        off = []
        for f in os.listdir(self.addonsFolder()):
            if not f.endswith(".py"):
                continue
            if f in self.mw.pm.meta['disabledAddons']:
                off.append(f)
            else:
                on.append(f)
        return on, off

    def onEnableAllAddons(self):
        self.mw.pm.meta['disabledAddons'] = []
        self.mw.pm.save()
        self.rebuildAddonsMenu()

    def onDisableAllAddons(self):
        on, off = self.files()
        self.mw.pm.meta['disabledAddons'] = on + off
        self.rebuildAddonsMenu()

    def toggleAddon(self, file):
        if file in self.mw.pm.meta['disabledAddons']:
            self.mw.pm.meta['disabledAddons'].remove(file)
        else:
            self.mw.pm.meta['disabledAddons'].append(file)
        self.mw.pm.save()
        self.rebuildAddonsMenu()

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
