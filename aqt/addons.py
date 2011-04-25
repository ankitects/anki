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
        self.mw.connect(f.actionOpenPluginFolder, s, self.onOpenPluginFolder)
        self.mw.connect(f.actionEnableAllPlugins, s, self.onEnableAllPlugins)
        self.mw.connect(f.actionDisableAllPlugins, s, self.onDisableAllPlugins)
        if isWin:
            self.clearPluginCache()
        self.disableObsoletePlugins()
        plugdir = self.pluginsFolder()
        sys.path.insert(0, plugdir)
        plugins = self.enabledPlugins()
        plugins.sort()
        self.registeredPlugins = {}
        for plugin in plugins:
            try:
                nopy = plugin.replace(".py", "")
                __import__(nopy)
            except:
                print "Error in %s" % plugin
                traceback.print_exc()
        self.mw.disableCardMenuItems()
        self.rebuildPluginsMenu()
        # run the obsolete init hook
        try:
            runHook('init')
        except:
            showWarning(
                _("Broken plugin:\n\n%s") %
                unicode(traceback.format_exc(), "utf-8", "replace"))

    def pluginsFolder(self):
        dir = self.mw.config.confDir
        if isWin:
            dir = dir.encode(sys.getfilesystemencoding())
        return os.path.join(dir, "addons")

    def clearPluginCache(self):
        "Clear .pyc files which may cause crashes if Python version updated."
        dir = self.pluginsFolder()
        for curdir, dirs, files in os.walk(dir):
            for f in files:
                if not f.endswith(".pyc"):
                    continue
                os.unlink(os.path.join(curdir, f))

    def disableObsoletePlugins(self):
        dir = self.pluginsFolder()
        native = _(
            "The %s plugin has been disabled, as Anki supports "+
            "this natively now.")
        plugins = [
            ("Custom Media Directory.py",
             (native % "custom media folder") + _(""" \
Please visit Settings>Preferences.""")),
            ("Regenerate Reading Field.py", _("""\
The regenerate reading field plugin has been disabled, as the Japanese \
support plugin supports this now. Please download the latest version.""")),
            ("Sync LaTeX with iPhone client.py",
             native % "sync LaTeX"),
            ("Incremental Reading.py",
             _("""The incremental reading plugin has been disabled because \
it needs updates.""")),
            ("Learn Mode.py", _("""\
The learn mode plugin has been disabled because it needs to be rewritten \
to work with this version of Anki."""))
            ]
        for p in plugins:
            path = os.path.join(dir, p[0])
            if os.path.exists(path):
                new = path.replace(".py", ".disabled")
                if os.path.exists(new):
                    os.unlink(new)
                os.rename(path, new)
                showInfo(p[1])

    def rebuildPluginsMenu(self):
        if getattr(self, "pluginActions", None) is None:
            self.pluginActions = []
        for action in self.pluginActions:
            self.mw.form.menuStartup.removeAction(action)
        all = self.allPlugins()
        all.sort()
        for fname in all:
            enabled = fname.endswith(".py")
            p = re.sub("\.py(\.off)?", "", fname)
            if p+".py" in self.registeredPlugins:
                p = self.registeredPlugins[p+".py"]['name']
            a = QAction(p, self.mw)
            a.setCheckable(True)
            a.setChecked(enabled)
            self.mw.connect(a, SIGNAL("triggered()"),
                         lambda fname=fname: self.togglePlugin(fname))
            self.mw.form.menuStartup.addAction(a)
            self.pluginActions.append(a)

    def enabledPlugins(self):
        return [p for p in os.listdir(self.pluginsFolder())
                if p.endswith(".py")]

    def disabledPlugins(self):
        return [p for p in os.listdir(self.pluginsFolder())
                if p.endswith(".py.off")]

    def allPlugins(self):
        return [p for p in os.listdir(self.pluginsFolder())
                if p.endswith(".py.off") or p.endswith(".py")]

    def onOpenPluginFolder(self, path=None):
        if path is None:
            path = self.pluginsFolder()
        openFolder(path)

    def enablePlugin(self, p):
        pd = self.pluginsFolder()
        old = os.path.join(pd, p)
        new = os.path.join(pd, p.replace(".off", ""))
        try:
            os.unlink(new)
        except:
            pass
        os.rename(old, new)

    def disablePlugin(self, p):
        pd = self.pluginsFolder()
        old = os.path.join(pd, p)
        new = os.path.join(pd, p.replace(".py", ".py.off"))
        try:
            os.unlink(new)
        except:
            pass
        os.rename(old, new)

    def onEnableAllPlugins(self):
        for p in self.disabledPlugins():
            self.enablePlugin(p)
        self.rebuildPluginsMenu()

    def onDisableAllPlugins(self):
        for p in self.enabledPlugins():
            self.disablePlugin(p)
        self.rebuildPluginsMenu()

    def togglePlugin(self, plugin):
        if plugin.endswith(".py"):
            self.disablePlugin(plugin)
        else:
            self.enablePlugin(plugin)
        self.rebuildPluginsMenu()

    def registerPlugin(self, name, updateId):
        return
