# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import faulthandler
import gc
import os
import re
import signal
import time
import zipfile
from argparse import Namespace
from concurrent.futures import Future
from threading import Thread
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple

import anki
import aqt
import aqt.mediasrv
import aqt.mpv
import aqt.progress
import aqt.sound
import aqt.stats
import aqt.toolbar
import aqt.webview
from anki import hooks
from anki.collection import _Collection
from anki.hooks import runHook
from anki.lang import _, ngettext
from anki.rsbackend import RustBackend
from anki.sound import AVTag, SoundOrVideoTag
from anki.storage import Collection
from anki.utils import devMode, ids2str, intTime, isMac, isWin, splitFields
from aqt import gui_hooks
from aqt.addons import DownloadLogEntry, check_and_prompt_for_updates, show_log_to_user
from aqt.legacy import install_pylib_legacy
from aqt.mediacheck import check_media_db
from aqt.mediasync import MediaSyncer
from aqt.profiles import ProfileManager as ProfileManagerType
from aqt.qt import *
from aqt.qt import sip
from aqt.taskman import TaskManager
from aqt.theme import theme_manager
from aqt.utils import (
    TR,
    askUser,
    checkInvalidFilename,
    getFile,
    getOnlyText,
    openHelp,
    openLink,
    restoreGeom,
    restoreState,
    saveGeom,
    showInfo,
    showText,
    showWarning,
    tooltip,
    tr,
)

install_pylib_legacy()


class ResetRequired:
    def __init__(self, mw: AnkiQt):
        self.mw = mw


class AnkiQt(QMainWindow):
    col: _Collection
    pm: ProfileManagerType
    web: aqt.webview.AnkiWebView
    bottomWeb: aqt.webview.AnkiWebView

    def __init__(
        self,
        app: QApplication,
        profileManager: ProfileManagerType,
        backend: RustBackend,
        opts: Namespace,
        args: List[Any],
    ) -> None:
        QMainWindow.__init__(self)
        self.backend = backend
        self.state = "startup"
        self.opts = opts
        self.col: Optional[_Collection] = None
        self.taskman = TaskManager()
        self.media_syncer = MediaSyncer(self)
        aqt.mw = self
        self.app = app
        self.pm = profileManager
        # init rest of app
        self.safeMode = self.app.queryKeyboardModifiers() & Qt.ShiftModifier
        try:
            self.setupUI()
            self.setupAddons(args)
        except:
            showInfo(_("Error during startup:\n%s") % traceback.format_exc())
            sys.exit(1)
        # must call this after ui set up
        if self.safeMode:
            tooltip(
                _(
                    "Shift key was held down. Skipping automatic "
                    "syncing and add-on loading."
                )
            )
        # were we given a file to import?
        if args and args[0] and not self._isAddon(args[0]):
            self.onAppMsg(args[0])
        # Load profile in a timer so we can let the window finish init and not
        # close on profile load error.
        if isWin:
            fn = self.setupProfileAfterWebviewsLoaded
        else:
            fn = self.setupProfile
        self.progress.timer(10, fn, False, requiresCollection=False)

    def setupUI(self) -> None:
        self.col = None
        self.setupCrashLog()
        self.disableGC()
        self.setupAppMsg()
        self.setupKeys()
        self.setupThreads()
        self.setupMediaServer()
        self.setupSound()
        self.setupSpellCheck()
        self.setupStyle()
        self.setupMainWindow()
        self.setupSystemSpecific()
        self.setupMenus()
        self.setupProgress()
        self.setupErrorHandler()
        self.setupSignals()
        self.setupAutoUpdate()
        self.setupHooks()
        self.setup_timers()
        self.updateTitleBar()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()

    def setupProfileAfterWebviewsLoaded(self):
        for w in (self.web, self.bottomWeb):
            if not w._domDone:
                self.progress.timer(
                    10,
                    self.setupProfileAfterWebviewsLoaded,
                    False,
                    requiresCollection=False,
                )
                return
            else:
                w.requiresCol = True

        self.setupProfile()

    # Profiles
    ##########################################################################

    class ProfileManager(QMainWindow):
        onClose = pyqtSignal()
        closeFires = True

        def closeEvent(self, evt):
            if self.closeFires:
                self.onClose.emit()
            evt.accept()

        def closeWithoutQuitting(self):
            self.closeFires = False
            self.close()
            self.closeFires = True

    def setupProfile(self) -> None:
        if self.pm.meta["firstRun"]:
            # load the new deck user profile
            self.pm.load(self.pm.profiles()[0])
            self.pm.meta["firstRun"] = False
            self.pm.save()

        self.pendingImport: Optional[str] = None
        self.restoringBackup = False
        # profile not provided on command line?
        if not self.pm.name:
            # if there's a single profile, load it automatically
            profs = self.pm.profiles()
            if len(profs) == 1:
                self.pm.load(profs[0])
        if not self.pm.name:
            self.showProfileManager()
        else:
            self.loadProfile()

    def showProfileManager(self) -> None:
        self.pm.profile = None
        self.state = "profileManager"
        d = self.profileDiag = self.ProfileManager()
        f = self.profileForm = aqt.forms.profiles.Ui_MainWindow()
        f.setupUi(d)
        f.login.clicked.connect(self.onOpenProfile)
        f.profiles.itemDoubleClicked.connect(self.onOpenProfile)
        f.openBackup.clicked.connect(self.onOpenBackup)
        f.quit.clicked.connect(d.close)
        d.onClose.connect(self.cleanupAndExit)
        f.add.clicked.connect(self.onAddProfile)
        f.rename.clicked.connect(self.onRenameProfile)
        f.delete_2.clicked.connect(self.onRemProfile)
        f.profiles.currentRowChanged.connect(self.onProfileRowChange)
        f.statusbar.setVisible(False)
        f.downgrade_button.clicked.connect(self._on_downgrade)
        # enter key opens profile
        QShortcut(QKeySequence("Return"), d, activated=self.onOpenProfile)  # type: ignore
        self.refreshProfilesList()
        # raise first, for osx testing
        d.show()
        d.activateWindow()
        d.raise_()

    def refreshProfilesList(self) -> None:
        f = self.profileForm
        f.profiles.clear()
        profs = self.pm.profiles()
        f.profiles.addItems(profs)
        try:
            idx = profs.index(self.pm.name)
        except:
            idx = 0
        f.profiles.setCurrentRow(idx)

    def onProfileRowChange(self, n: int) -> None:
        if n < 0:
            # called on .clear()
            return
        name = self.pm.profiles()[n]
        f = self.profileForm
        self.pm.load(name)

    def openProfile(self):
        name = self.pm.profiles()[self.profileForm.profiles.currentRow()]
        return self.pm.load(name)

    def onOpenProfile(self) -> None:
        self.profileDiag.hide()
        # code flow is confusing here - if load fails, profile dialog
        # will be shown again
        self.loadProfile(self.profileDiag.closeWithoutQuitting)

    def profileNameOk(self, str):
        return not checkInvalidFilename(str)

    def onAddProfile(self):
        name = getOnlyText(_("Name:")).strip()
        if name:
            if name in self.pm.profiles():
                return showWarning(_("Name exists."))
            if not self.profileNameOk(name):
                return
            self.pm.create(name)
            self.pm.name = name
            self.refreshProfilesList()

    def onRenameProfile(self):
        name = getOnlyText(_("New name:"), default=self.pm.name).strip()
        if not name:
            return
        if name == self.pm.name:
            return
        if name in self.pm.profiles():
            return showWarning(_("Name exists."))
        if not self.profileNameOk(name):
            return
        self.pm.rename(name)
        self.refreshProfilesList()

    def onRemProfile(self):
        profs = self.pm.profiles()
        if len(profs) < 2:
            return showWarning(_("There must be at least one profile."))
        # sure?
        if not askUser(
            _(
                """\
All cards, notes, and media for this profile will be deleted. \
Are you sure?"""
            ),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            return
        self.pm.remove(self.pm.name)
        self.refreshProfilesList()

    def onOpenBackup(self):
        if not askUser(
            _(
                """\
Replace your collection with an earlier backup?"""
            ),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            return

        def doOpen(path):
            self._openBackup(path)

        getFile(
            self.profileDiag,
            _("Revert to backup"),
            cb=doOpen,
            filter="*.colpkg",
            dir=self.pm.backupFolder(),
        )

    def _openBackup(self, path):
        try:
            # move the existing collection to the trash, as it may not open
            self.pm.trashCollection()
        except:
            showWarning(
                _(
                    "Unable to move existing file to trash - please try restarting your computer."
                )
            )
            return

        self.pendingImport = path
        self.restoringBackup = True

        showInfo(
            _(
                """\
Automatic syncing and backups have been disabled while restoring. To enable them again, \
close the profile or restart Anki."""
            )
        )

        self.onOpenProfile()

    def _on_downgrade(self):
        self.progress.start()
        profiles = self.pm.profiles()

        def downgrade():
            return self.pm.downgrade(profiles)

        def on_done(future):
            self.progress.finish()
            problems = future.result()
            if not problems:
                showInfo("Profiles can now be opened with an older version of Anki.")
            else:
                showWarning(
                    "The following profiles could not be downgraded: {}".format(
                        ", ".join(problems)
                    )
                )
                return
            self.profileDiag.close()

        self.taskman.run_in_background(downgrade, on_done)

    def loadProfile(self, onsuccess: Optional[Callable] = None) -> None:
        self.maybeAutoSync()

        if not self.loadCollection():
            return

        self.maybe_auto_sync_media()

        self.pm.apply_profile_options()

        # show main window
        if self.pm.profile["mainWindowState"]:
            restoreGeom(self, "mainWindow")
            restoreState(self, "mainWindow")
        # titlebar
        self.setWindowTitle(self.pm.name + " - Anki")
        # show and raise window for osx
        self.show()
        self.activateWindow()
        self.raise_()

        # import pending?
        if self.pendingImport:
            if self._isAddon(self.pendingImport):
                self.installAddon(self.pendingImport)
            else:
                self.handleImport(self.pendingImport)
            self.pendingImport = None
        gui_hooks.profile_did_open()
        if onsuccess:
            onsuccess()

    def unloadProfile(self, onsuccess: Callable) -> None:
        def callback():
            self._unloadProfile()
            onsuccess()

        # start media sync if not already running
        self.maybe_auto_sync_media()

        gui_hooks.profile_will_close()
        self.unloadCollection(callback)

    def _unloadProfile(self) -> None:
        self.pm.profile["mainWindowGeom"] = self.saveGeometry()
        self.pm.profile["mainWindowState"] = self.saveState()
        self.pm.save()
        self.hide()

        self.restoringBackup = False

        # at this point there should be no windows left
        self._checkForUnclosedWidgets()

        self.maybeAutoSync()

    def _checkForUnclosedWidgets(self) -> None:
        for w in self.app.topLevelWidgets():
            if w.isVisible():
                # windows with this property are safe to close immediately
                if getattr(w, "silentlyClose", None):
                    w.close()
                else:
                    print("Window should have been closed: {}".format(w))

    def unloadProfileAndExit(self) -> None:
        self.unloadProfile(self.cleanupAndExit)

    def unloadProfileAndShowProfileManager(self):
        self.unloadProfile(self.showProfileManager)

    def cleanupAndExit(self) -> None:
        self.errorHandler.unload()
        self.mediaServer.shutdown()
        self.app.exit(0)

    # Sound/video
    ##########################################################################

    def setupSound(self) -> None:
        aqt.sound.setup_audio(self.taskman, self.pm.base)

    def _add_play_buttons(self, text: str) -> str:
        "Return card text with play buttons added, or stripped."
        if self.pm.profile.get("showPlayButtons", True):
            return aqt.sound.av_refs_to_play_icons(text)
        else:
            return anki.sound.strip_av_refs(text)

    def prepare_card_text_for_display(self, text: str) -> str:
        text = self.col.media.escapeImages(text)
        text = self._add_play_buttons(text)
        return text

    # Collection load/unload
    ##########################################################################

    def loadCollection(self) -> bool:
        try:
            self._loadCollection()
        except Exception as e:
            if "FileTooNew" in str(e):
                showWarning(
                    "This profile requires a newer version of Anki to open. Did you forget to use the Downgrade button prior to switching Anki versions?"
                )
            else:
                showWarning(
                    tr(TR.ERRORS_UNABLE_OPEN_COLLECTION) + "\n" + traceback.format_exc()
                )
            # clean up open collection if possible
            if self.col:
                try:
                    self.col.close(save=False, downgrade=False)
                except:
                    pass
                self.col = None

            # return to profile manager
            self.hide()
            self.showProfileManager()
            return False

        # make sure we don't get into an inconsistent state if an add-on
        # has broken the deck browser or the did_load hook
        try:
            self.maybeEnableUndo()
            gui_hooks.collection_did_load(self.col)
            self.moveToState("deckBrowser")
        except Exception as e:
            # dump error to stderr so it gets picked up by errors.py
            traceback.print_exc()

        return True

    def _loadCollection(self):
        self.reopen()
        self.setEnabled(True)

    def reopen(self):
        cpath = self.pm.collectionPath()
        self.col = Collection(cpath, backend=self.backend, log=True)

    def unloadCollection(self, onsuccess: Callable) -> None:
        def callback():
            self.setEnabled(False)
            self.media_syncer.show_diag_until_finished()
            self._unloadCollection()
            onsuccess()

        self.closeAllWindows(callback)

    def _unloadCollection(self) -> None:
        if not self.col:
            return
        if self.restoringBackup:
            label = _("Closing...")
        else:
            label = _("Backing Up...")
        self.progress.start(label=label, immediate=True)
        corrupt = False
        try:
            self.maybeOptimize()
            if not devMode:
                corrupt = self.col.db.scalar("pragma integrity_check") != "ok"
        except:
            corrupt = True
        try:
            self.col.close(downgrade=False)
        except Exception as e:
            print(e)
            corrupt = True
        finally:
            self.col = None
            self.progress.finish()
        if corrupt:
            showWarning(
                _(
                    "Your collection file appears to be corrupt. \
This can happen when the file is copied or moved while Anki is open, or \
when the collection is stored on a network or cloud drive. If problems \
persist after restarting your computer, please open an automatic backup \
from the profile screen."
                )
            )
        if not corrupt and not self.restoringBackup:
            self.backup()

    # Backup and auto-optimize
    ##########################################################################

    class BackupThread(Thread):
        def __init__(self, path, data):
            Thread.__init__(self)
            self.path = path
            self.data = data
            # create the file in calling thread to ensure the same
            # file is not created twice
            open(self.path, "wb").close()

        def run(self):
            z = zipfile.ZipFile(self.path, "w", zipfile.ZIP_DEFLATED)
            z.writestr("collection.anki2", self.data)
            z.writestr("media", "{}")
            z.close()

    def backup(self) -> None:
        nbacks = self.pm.profile["numBackups"]
        if not nbacks or devMode:
            return
        dir = self.pm.backupFolder()
        path = self.pm.collectionPath()

        # do backup
        fname = time.strftime(
            "backup-%Y-%m-%d-%H.%M.%S.colpkg", time.localtime(time.time())
        )
        newpath = os.path.join(dir, fname)
        with open(path, "rb") as f:
            data = f.read()
        b = self.BackupThread(newpath, data)
        b.start()

        # find existing backups
        backups = []
        for file in os.listdir(dir):
            # only look for new-style format
            m = re.match(r"backup-\d{4}-\d{2}-.+.colpkg", file)
            if not m:
                continue
            backups.append(file)
        backups.sort()

        # remove old ones
        while len(backups) > nbacks:
            fname = backups.pop(0)
            path = os.path.join(dir, fname)
            os.unlink(path)
        gui_hooks.backup_did_complete()

    def maybeOptimize(self) -> None:
        # have two weeks passed?
        if (intTime() - self.pm.profile["lastOptimize"]) < 86400 * 14:
            return
        self.progress.start(label=_("Optimizing..."), immediate=True)
        self.col.optimize()
        self.pm.profile["lastOptimize"] = intTime()
        self.pm.save()
        self.progress.finish()

    # State machine
    ##########################################################################

    def moveToState(self, state: str, *args) -> None:
        # print("-> move from", self.state, "to", state)
        oldState = self.state or "dummy"
        cleanup = getattr(self, "_" + oldState + "Cleanup", None)
        if cleanup:
            # pylint: disable=not-callable
            cleanup(state)
        self.clearStateShortcuts()
        self.state = state
        gui_hooks.state_will_change(state, oldState)
        getattr(self, "_" + state + "State")(oldState, *args)
        if state != "resetRequired":
            self.bottomWeb.show()
        gui_hooks.state_did_change(state, oldState)

    def _deckBrowserState(self, oldState: str) -> None:
        self.maybe_check_for_addon_updates()
        self.deckBrowser.show()

    def _selectedDeck(self) -> Optional[Dict[str, Any]]:
        did = self.col.decks.selected()
        if not self.col.decks.nameOrNone(did):
            showInfo(_("Please select a deck."))
            return None
        return self.col.decks.get(did)

    def _overviewState(self, oldState: str) -> None:
        if not self._selectedDeck():
            return self.moveToState("deckBrowser")
        self.col.reset()
        self.overview.show()

    def _reviewState(self, oldState):
        self.reviewer.show()

    def _reviewCleanup(self, newState):
        if newState != "resetRequired" and newState != "review":
            self.reviewer.cleanup()

    # Resetting state
    ##########################################################################

    def reset(self, guiOnly: bool = False) -> None:
        "Called for non-trivial edits. Rebuilds queue and updates UI."
        if self.col:
            if not guiOnly:
                self.col.reset()
            gui_hooks.state_did_reset()
            self.maybeEnableUndo()
            self.moveToState(self.state)

    def requireReset(self, modal=False):
        "Signal queue needs to be rebuilt when edits are finished or by user."
        self.autosave()
        self.resetModal = modal
        if self.interactiveState():
            self.moveToState("resetRequired")

    def interactiveState(self):
        "True if not in profile manager, syncing, etc."
        return self.state in ("overview", "review", "deckBrowser")

    def maybeReset(self) -> None:
        self.autosave()
        if self.state == "resetRequired":
            self.state = self.returnState
            self.reset()

    def delayedMaybeReset(self):
        # if we redraw the page in a button click event it will often crash on
        # windows
        self.progress.timer(100, self.maybeReset, False)

    def _resetRequiredState(self, oldState: str) -> None:
        if oldState != "resetRequired":
            self.returnState = oldState
        if self.resetModal:
            # we don't have to change the webview, as we have a covering window
            return
        web_context = ResetRequired(self)
        self.web.set_bridge_command(lambda url: self.delayedMaybeReset(), web_context)
        i = _("Waiting for editing to finish.")
        b = self.button("refresh", _("Resume Now"), id="resume")
        self.web.stdHtml(
            """
<center><div style="height: 100%%">
<div style="position:relative; vertical-align: middle;">
%s<br><br>
%s</div></div></center>
<script>$('#resume').focus()</script>
"""
            % (i, b),
            context=web_context,
        )
        self.bottomWeb.hide()
        self.web.setFocus()

    # HTML helpers
    ##########################################################################

    def button(
        self,
        link: str,
        name: str,
        key: Optional[str] = None,
        class_: str = "",
        id: str = "",
        extra: str = "",
    ) -> str:
        class_ = "but " + class_
        if key:
            key = _("Shortcut key: %s") % key
        else:
            key = ""
        return """
<button id="%s" class="%s" onclick="pycmd('%s');return false;"
title="%s" %s>%s</button>""" % (
            id,
            class_,
            link,
            key,
            extra,
            name,
        )

    # Main window setup
    ##########################################################################

    def setupMainWindow(self) -> None:
        # main window
        self.form = aqt.forms.main.Ui_MainWindow()
        self.form.setupUi(self)
        # toolbar
        tweb = self.toolbarWeb = aqt.webview.AnkiWebView(title="top toolbar")
        tweb.setFocusPolicy(Qt.WheelFocus)
        self.toolbar = aqt.toolbar.Toolbar(self, tweb)
        self.toolbar.draw()
        # main area
        self.web = aqt.webview.AnkiWebView(title="main webview")
        self.web.setFocusPolicy(Qt.WheelFocus)
        self.web.setMinimumWidth(400)
        # bottom area
        sweb = self.bottomWeb = aqt.webview.AnkiWebView(title="bottom toolbar")
        sweb.setFocusPolicy(Qt.WheelFocus)
        # add in a layout
        self.mainLayout = QVBoxLayout()
        self.mainLayout.setContentsMargins(0, 0, 0, 0)
        self.mainLayout.setSpacing(0)
        self.mainLayout.addWidget(tweb)
        self.mainLayout.addWidget(self.web)
        self.mainLayout.addWidget(sweb)
        self.form.centralwidget.setLayout(self.mainLayout)

        # force webengine processes to load before cwd is changed
        if isWin:
            for o in self.web, self.bottomWeb:
                o.requiresCol = False
                o._domReady = False
                o._page.setContent(bytes("", "ascii"))

    def closeAllWindows(self, onsuccess: Callable) -> None:
        aqt.dialogs.closeAll(onsuccess)

    # Components
    ##########################################################################

    def setupSignals(self) -> None:
        signal.signal(signal.SIGINT, self.onSigInt)

    def onSigInt(self, signum, frame):
        # schedule a rollback & quit
        def quit():
            self.col.db.rollback()
            self.close()

        self.progress.timer(100, quit, False)

    def setupProgress(self) -> None:
        self.progress = aqt.progress.ProgressManager(self)

    def setupErrorHandler(self) -> None:
        import aqt.errors

        self.errorHandler = aqt.errors.ErrorHandler(self)

    def setupAddons(self, args: Optional[List]) -> None:
        import aqt.addons

        self.addonManager = aqt.addons.AddonManager(self)

        if args and args[0] and self._isAddon(args[0]):
            self.installAddon(args[0], startup=True)

        if not self.safeMode:
            self.addonManager.loadAddons()
            self.maybe_check_for_addon_updates()

    def maybe_check_for_addon_updates(self):
        last_check = self.pm.last_addon_update_check()
        elap = intTime() - last_check

        if elap > 86_400:
            check_and_prompt_for_updates(
                self, self.addonManager, self.on_updates_installed
            )
            self.pm.set_last_addon_update_check(intTime())

    def on_updates_installed(self, log: List[DownloadLogEntry]) -> None:
        if log:
            show_log_to_user(self, log)

    def setupSpellCheck(self) -> None:
        os.environ["QTWEBENGINE_DICTIONARIES_PATH"] = os.path.join(
            self.pm.base, "dictionaries"
        )

    def setupThreads(self) -> None:
        self._mainThread = QThread.currentThread()

    def inMainThread(self) -> bool:
        return self._mainThread == QThread.currentThread()

    def setupDeckBrowser(self) -> None:
        from aqt.deckbrowser import DeckBrowser

        self.deckBrowser = DeckBrowser(self)

    def setupOverview(self) -> None:
        from aqt.overview import Overview

        self.overview = Overview(self)

    def setupReviewer(self) -> None:
        from aqt.reviewer import Reviewer

        self.reviewer = Reviewer(self)

    # Syncing
    ##########################################################################

    # expects a current profile and a loaded collection; reloads
    # collection after sync completes
    def onSync(self):
        if self.media_syncer.is_syncing():
            self.media_syncer.show_sync_log()
        else:
            self.unloadCollection(self._onSync)

    def _onSync(self):
        self._sync()
        if not self.loadCollection():
            return
        self.media_syncer.start()

    # expects a current profile, but no collection loaded
    def maybeAutoSync(self) -> None:
        if (
            not self.pm.profile["syncKey"]
            or not self.pm.profile["autoSync"]
            or self.safeMode
            or self.restoringBackup
        ):
            return

        # ok to sync
        self._sync()

    def maybe_auto_sync_media(self) -> None:
        if not self.pm.profile["autoSync"] or self.safeMode or self.restoringBackup:
            return
        self.media_syncer.start()

    def _sync(self):
        from aqt.sync import SyncManager

        self.state = "sync"
        self.app.setQuitOnLastWindowClosed(False)
        self.syncer = SyncManager(self, self.pm)
        self.syncer.sync()
        self.app.setQuitOnLastWindowClosed(True)

    # Tools
    ##########################################################################

    def raiseMain(self):
        if not self.app.activeWindow():
            # make sure window is shown
            self.setWindowState(self.windowState() & ~Qt.WindowMinimized)
        return True

    def setupStyle(self) -> None:
        theme_manager.night_mode = self.pm.night_mode()
        theme_manager.apply_style(self.app)

    # Key handling
    ##########################################################################

    def setupKeys(self) -> None:
        globalShortcuts = [
            ("Ctrl+:", self.onDebug),
            ("d", lambda: self.moveToState("deckBrowser")),
            ("s", self.onStudyKey),
            ("a", self.onAddCard),
            ("b", self.onBrowse),
            ("t", self.onStats),
            ("y", self.onSync),
        ]
        self.applyShortcuts(globalShortcuts)

        self.stateShortcuts: Sequence[Tuple[str, Callable]] = []

    def applyShortcuts(
        self, shortcuts: Sequence[Tuple[str, Callable]]
    ) -> List[QShortcut]:
        qshortcuts = []
        for key, fn in shortcuts:
            scut = QShortcut(QKeySequence(key), self, activated=fn)  # type: ignore
            scut.setAutoRepeat(False)
            qshortcuts.append(scut)
        return qshortcuts

    def setStateShortcuts(self, shortcuts: List[Tuple[str, Callable]]) -> None:
        gui_hooks.state_shortcuts_will_change(self.state, shortcuts)
        # legacy hook
        runHook(self.state + "StateShortcuts", shortcuts)
        self.stateShortcuts = self.applyShortcuts(shortcuts)

    def clearStateShortcuts(self) -> None:
        for qs in self.stateShortcuts:
            sip.delete(qs)
        self.stateShortcuts = []

    def onStudyKey(self) -> None:
        if self.state == "overview":
            self.col.startTimebox()
            self.moveToState("review")
        else:
            self.moveToState("overview")

    # App exit
    ##########################################################################

    def closeEvent(self, event: QCloseEvent) -> None:
        if self.state == "profileManager":
            # if profile manager active, this event may fire via OS X menu bar's
            # quit option
            self.profileDiag.close()
            event.accept()
        else:
            # ignore the event for now, as we need time to clean up
            event.ignore()
            self.unloadProfileAndExit()

    # Undo & autosave
    ##########################################################################

    def onUndo(self) -> None:
        n = self.col.undoName()
        if not n:
            return
        cid = self.col.undo()
        if cid and self.state == "review":
            card = self.col.getCard(cid)
            self.col.sched.reset()
            self.reviewer.cardQueue.append(card)
            self.reviewer.nextCard()
            gui_hooks.review_did_undo(cid)
        else:
            self.reset()
            tooltip(_("Reverted to state prior to '%s'.") % n.lower())
            gui_hooks.state_did_revert(n)
        self.maybeEnableUndo()

    def maybeEnableUndo(self) -> None:
        if self.col and self.col.undoName():
            self.form.actionUndo.setText(_("Undo %s") % self.col.undoName())
            self.form.actionUndo.setEnabled(True)
            gui_hooks.undo_state_did_change(True)
        else:
            self.form.actionUndo.setText(_("Undo"))
            self.form.actionUndo.setEnabled(False)
            gui_hooks.undo_state_did_change(False)

    def checkpoint(self, name):
        self.col.save(name)
        self.maybeEnableUndo()

    def autosave(self) -> None:
        saved = self.col.autosave()
        self.maybeEnableUndo()
        if saved:
            self.doGC()

    # Other menu operations
    ##########################################################################

    def onAddCard(self) -> None:
        aqt.dialogs.open("AddCards", self)

    def onBrowse(self) -> None:
        aqt.dialogs.open("Browser", self)

    def onEditCurrent(self):
        aqt.dialogs.open("EditCurrent", self)

    def onDeckConf(self, deck=None):
        if not deck:
            deck = self.col.decks.current()
        if deck["dyn"]:
            import aqt.dyndeckconf

            aqt.dyndeckconf.DeckConf(self, deck=deck)
        else:
            import aqt.deckconf

            aqt.deckconf.DeckConf(self, deck)

    def onOverview(self):
        self.col.reset()
        self.moveToState("overview")

    def onStats(self):
        deck = self._selectedDeck()
        if not deck:
            return
        aqt.dialogs.open("DeckStats", self)

    def onPrefs(self):
        aqt.dialogs.open("Preferences", self)

    def onNoteTypes(self):
        import aqt.models

        aqt.models.Models(self, self, fromMain=True)

    def onAbout(self):
        aqt.dialogs.open("About", self)

    def onDonate(self):
        openLink(aqt.appDonate)

    def onDocumentation(self):
        openHelp("")

    # Importing & exporting
    ##########################################################################

    def handleImport(self, path: str) -> None:
        import aqt.importing

        if not os.path.exists(path):
            showInfo(_("Please use File>Import to import this file."))
            return None

        aqt.importing.importFile(self, path)
        return None

    def onImport(self):
        import aqt.importing

        aqt.importing.onImport(self)

    def onExport(self, did=None):
        import aqt.exporting

        aqt.exporting.ExportDialog(self, did=did)

    # Installing add-ons from CLI / mimetype handler
    ##########################################################################

    def installAddon(self, path: str, startup: bool = False):
        from aqt.addons import installAddonPackages

        installAddonPackages(
            self.addonManager,
            [path],
            warn=True,
            advise_restart=not startup,
            strictly_modal=startup,
            parent=None if startup else self,
        )

    # Cramming
    ##########################################################################

    def onCram(self, search=""):
        import aqt.dyndeckconf

        n = 1
        deck = self.col.decks.current()
        if not search:
            if not deck["dyn"]:
                search = 'deck:"%s" ' % deck["name"]
        decks = self.col.decks.allNames()
        while _("Filtered Deck %d") % n in decks:
            n += 1
        name = _("Filtered Deck %d") % n
        did = self.col.decks.newDyn(name)
        diag = aqt.dyndeckconf.DeckConf(self, first=True, search=search)
        if not diag.ok:
            # user cancelled first config
            self.col.decks.rem(did)
            self.col.decks.select(deck["id"])

    # Menu, title bar & status
    ##########################################################################

    def setupMenus(self) -> None:
        m = self.form
        m.actionSwitchProfile.triggered.connect(self.unloadProfileAndShowProfileManager)
        m.actionImport.triggered.connect(self.onImport)
        m.actionExport.triggered.connect(self.onExport)
        m.actionExit.triggered.connect(self.close)
        m.actionPreferences.triggered.connect(self.onPrefs)
        m.actionAbout.triggered.connect(self.onAbout)
        m.actionUndo.triggered.connect(self.onUndo)
        if qtminor < 11:
            m.actionUndo.setShortcut(QKeySequence("Ctrl+Alt+Z"))
        m.actionFullDatabaseCheck.triggered.connect(self.onCheckDB)
        m.actionCheckMediaDatabase.triggered.connect(self.on_check_media_db)
        m.actionDocumentation.triggered.connect(self.onDocumentation)
        m.actionDonate.triggered.connect(self.onDonate)
        m.actionStudyDeck.triggered.connect(self.onStudyDeck)
        m.actionCreateFiltered.triggered.connect(self.onCram)
        m.actionEmptyCards.triggered.connect(self.onEmptyCards)
        m.actionNoteTypes.triggered.connect(self.onNoteTypes)

    def updateTitleBar(self) -> None:
        self.setWindowTitle("Anki")

    # Auto update
    ##########################################################################

    def setupAutoUpdate(self) -> None:
        import aqt.update

        self.autoUpdate = aqt.update.LatestVersionFinder(self)
        self.autoUpdate.newVerAvail.connect(self.newVerAvail)  # type: ignore
        self.autoUpdate.newMsg.connect(self.newMsg)  # type: ignore
        self.autoUpdate.clockIsOff.connect(self.clockIsOff)  # type: ignore
        self.autoUpdate.start()

    def newVerAvail(self, ver):
        if self.pm.meta.get("suppressUpdate", None) != ver:
            aqt.update.askAndUpdate(self, ver)

    def newMsg(self, data):
        aqt.update.showMessages(self, data)

    def clockIsOff(self, diff):
        diffText = ngettext("%s second", "%s seconds", diff) % diff
        warn = (
            _(
                """\
In order to ensure your collection works correctly when moved between \
devices, Anki requires your computer's internal clock to be set correctly. \
The internal clock can be wrong even if your system is showing the correct \
local time.

Please go to the time settings on your computer and check the following:

- AM/PM
- Clock drift
- Day, month and year
- Timezone
- Daylight savings

Difference to correct time: %s."""
            )
            % diffText
        )
        showWarning(warn)
        self.app.closeAllWindows()

    # Timers
    ##########################################################################

    def setup_timers(self) -> None:
        # refresh decks every 10 minutes
        self.progress.timer(10 * 60 * 1000, self.onRefreshTimer, True)
        # check media sync every 5 minutes
        self.progress.timer(5 * 60 * 1000, self.on_autosync_timer, True)

    def onRefreshTimer(self):
        if self.state == "deckBrowser":
            self.deckBrowser.refresh()
        elif self.state == "overview":
            self.overview.refresh()

    def on_autosync_timer(self):
        elap = self.media_syncer.seconds_since_last_sync()
        # autosync if 15 minutes have elapsed since last sync
        if elap > 15 * 60:
            self.maybe_auto_sync_media()

    # Permanent libanki hooks
    ##########################################################################

    def setupHooks(self) -> None:
        hooks.schema_will_change.append(self.onSchemaMod)
        hooks.notes_will_be_deleted.append(self.onRemNotes)
        hooks.card_odue_was_invalid.append(self.onOdueInvalid)

        gui_hooks.av_player_will_play.append(self.on_av_player_will_play)
        gui_hooks.av_player_did_end_playing.append(self.on_av_player_did_end_playing)

        self._activeWindowOnPlay: Optional[QWidget] = None

    def onOdueInvalid(self):
        showWarning(
            _(
                """\
Invalid property found on card. Please use Tools>Check Database, \
and if the problem comes up again, please ask on the support site."""
            )
        )

    def _isVideo(self, tag: AVTag) -> bool:
        if isinstance(tag, SoundOrVideoTag):
            head, ext = os.path.splitext(tag.filename.lower())
            return ext in (".mp4", ".mov", ".mpg", ".mpeg", ".mkv", ".avi")

        return False

    def on_av_player_will_play(self, tag: AVTag) -> None:
        "Record active window to restore after video playing."
        if not self._isVideo(tag):
            return

        self._activeWindowOnPlay = self.app.activeWindow() or self._activeWindowOnPlay

    def on_av_player_did_end_playing(self, player: Any) -> None:
        "Restore window focus after a video was played."
        w = self._activeWindowOnPlay
        if not self.app.activeWindow() and w and not sip.isdeleted(w) and w.isVisible():
            w.activateWindow()
            w.raise_()
        self._activeWindowOnPlay = None

    # Log note deletion
    ##########################################################################

    def onRemNotes(self, col: _Collection, nids: List[int]) -> None:
        path = os.path.join(self.pm.profileFolder(), "deleted.txt")
        existed = os.path.exists(path)
        with open(path, "ab") as f:
            if not existed:
                f.write(b"nid\tmid\tfields\n")
            for id, mid, flds in col.db.execute(
                "select id, mid, flds from notes where id in %s" % ids2str(nids)
            ):
                fields = splitFields(flds)
                f.write(("\t".join([str(id), str(mid)] + fields)).encode("utf8"))
                f.write(b"\n")

    # Schema modifications
    ##########################################################################

    def onSchemaMod(self, arg):
        progress_shown = self.progress.busy()
        if progress_shown:
            self.progress.finish()
        ret = askUser(
            _(
                """\
The requested change will require a full upload of the database when \
you next synchronize your collection. If you have reviews or other changes \
waiting on another device that haven't been synchronized here yet, they \
will be lost. Continue?"""
            )
        )
        if progress_shown:
            self.progress.start()
        return ret

    # Advanced features
    ##########################################################################

    def onCheckDB(self):
        "True if no problems"
        self.progress.start()

        def onDone(future: Future):
            self.progress.finish()
            ret, ok = future.result()

            if not ok:
                showText(ret)
            else:
                tooltip(ret)

            # if an error has directed the user to check the database,
            # silently clean up any broken reset hooks which distract from
            # the underlying issue
            while True:
                try:
                    self.reset()
                    break
                except Exception as e:
                    print("swallowed exception in reset hook:", e)
                    continue

        self.taskman.run_in_background(self.col.fixIntegrity, onDone)

    def on_check_media_db(self) -> None:
        check_media_db(self)

    def onStudyDeck(self):
        from aqt.studydeck import StudyDeck

        ret = StudyDeck(self, dyn=True, current=self.col.decks.current()["name"])
        if ret.name:
            self.col.decks.select(self.col.decks.id(ret.name))
            self.moveToState("overview")

    def onEmptyCards(self):
        self.progress.start(immediate=True)
        cids = self.col.emptyCids()
        cids = gui_hooks.empty_cards_will_be_deleted(cids)
        if not cids:
            self.progress.finish()
            tooltip(_("No empty cards."))
            return
        report = self.col.emptyCardReport(cids)
        self.progress.finish()
        part1 = ngettext("%d card", "%d cards", len(cids)) % len(cids)
        part1 = _("%s to delete:") % part1
        diag, box = showText(part1 + "\n\n" + report, run=False, geomKey="emptyCards")
        box.addButton(_("Delete Cards"), QDialogButtonBox.AcceptRole)
        box.button(QDialogButtonBox.Close).setDefault(True)

        def onDelete():
            saveGeom(diag, "emptyCards")
            QDialog.accept(diag)
            self.checkpoint(_("Delete Empty"))
            self.col.remCards(cids)
            tooltip(
                ngettext("%d card deleted.", "%d cards deleted.", len(cids)) % len(cids)
            )
            self.reset()

        box.accepted.connect(onDelete)
        diag.show()

    # Debugging
    ######################################################################

    def onDebug(self):
        d = self.debugDiag = QDialog()
        d.silentlyClose = True
        frm = aqt.forms.debug.Ui_Dialog()
        frm.setupUi(d)
        font = QFontDatabase.systemFont(QFontDatabase.FixedFont)
        font.setPointSize(frm.text.font().pointSize() + 1)
        frm.text.setFont(font)
        frm.log.setFont(font)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+return"), d)
        s.activated.connect(lambda: self.onDebugRet(frm))
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+shift+return"), d)
        s.activated.connect(lambda: self.onDebugPrint(frm))
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+l"), d)
        s.activated.connect(frm.log.clear)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+shift+l"), d)
        s.activated.connect(frm.text.clear)

        def addContextMenu(ev: QCloseEvent, name: str) -> None:
            ev.accept()
            menu = frm.log.createStandardContextMenu(QCursor.pos())
            menu.addSeparator()
            if name == "log":
                a = menu.addAction("Clear Log")
                a.setShortcuts(QKeySequence("ctrl+l"))
                qconnect(a.triggered, frm.log.clear)
            elif name == "text":
                a = menu.addAction("Clear Code")
                a.setShortcuts(QKeySequence("ctrl+shift+l"))
                qconnect(a.triggered, frm.text.clear)
            menu.exec(QCursor.pos())

        frm.log.contextMenuEvent = lambda ev: addContextMenu(ev, "log")
        frm.text.contextMenuEvent = lambda ev: addContextMenu(ev, "text")
        gui_hooks.debug_console_will_show(d)
        d.show()

    def _captureOutput(self, on):
        mw = self

        class Stream:
            def write(self, data):
                mw._output += data

        if on:
            self._output = ""
            self._oldStderr = sys.stderr
            self._oldStdout = sys.stdout
            s = Stream()
            sys.stderr = s
            sys.stdout = s
        else:
            sys.stderr = self._oldStderr
            sys.stdout = self._oldStdout

    def _card_repr(self, card: anki.cards.Card) -> None:
        import pprint, copy

        if not card:
            print("no card")
            return

        print("Front:", card.question())
        print("\n")
        print("Back:", card.answer())

        print("\nNote:")
        note = copy.copy(card.note())
        for k, v in note.items():
            print(f"- {k}:", v)

        print("\n")
        del note.fields
        del note._fmap
        del note._model
        pprint.pprint(note.__dict__)

        print("\nCard:")
        c = copy.copy(card)
        c._render_output = None
        pprint.pprint(c.__dict__)

    def _debugCard(self) -> Optional[anki.cards.Card]:
        card = self.reviewer.card
        self._card_repr(card)
        return card

    def _debugBrowserCard(self) -> Optional[anki.cards.Card]:
        card = aqt.dialogs._dialogs["Browser"][1].card
        self._card_repr(card)
        return card

    def onDebugPrint(self, frm):
        cursor = frm.text.textCursor()
        position = cursor.position()
        cursor.select(QTextCursor.LineUnderCursor)
        line = cursor.selectedText()
        pfx, sfx = "pp(", ")"
        if not line.startswith(pfx):
            line = "{}{}{}".format(pfx, line, sfx)
            cursor.insertText(line)
            cursor.setPosition(position + len(pfx))
            frm.text.setTextCursor(cursor)
        self.onDebugRet(frm)

    def onDebugRet(self, frm):
        import pprint, traceback

        text = frm.text.toPlainText()
        card = self._debugCard
        bcard = self._debugBrowserCard
        mw = self
        pp = pprint.pprint
        self._captureOutput(True)
        try:
            # pylint: disable=exec-used
            exec(text)
        except:
            self._output += traceback.format_exc()
        self._captureOutput(False)
        buf = ""
        for c, line in enumerate(text.strip().split("\n")):
            if c == 0:
                buf += ">>> %s\n" % line
            else:
                buf += "... %s\n" % line
        try:
            to_append = buf + (self._output or "<no output>")
            to_append = gui_hooks.debug_console_did_evaluate_python(
                to_append, text, frm
            )
            frm.log.appendPlainText(to_append)
        except UnicodeDecodeError:
            to_append = _("<non-unicode text>")
            to_append = gui_hooks.debug_console_did_evaluate_python(
                to_append, text, frm
            )
            frm.log.appendPlainText(to_append)
        frm.log.ensureCursorVisible()

    # System specific code
    ##########################################################################

    def setupSystemSpecific(self) -> None:
        self.hideMenuAccels = False
        if isMac:
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+M", self)
            self.minimizeShortcut.activated.connect(self.onMacMinimize)  # type: ignore
            self.hideMenuAccels = True
            self.maybeHideAccelerators()
            self.hideStatusTips()
        elif isWin:
            # make sure ctypes is bundled
            from ctypes import windll, wintypes  # type: ignore

            _dummy1 = windll
            _dummy2 = wintypes

    def maybeHideAccelerators(self, tgt: Optional[Any] = None) -> None:
        if not self.hideMenuAccels:
            return
        tgt = tgt or self
        for action in tgt.findChildren(QAction):
            txt = str(action.text())
            m = re.match(r"^(.+)\(&.+\)(.+)?", txt)
            if m:
                action.setText(m.group(1) + (m.group(2) or ""))

    def hideStatusTips(self) -> None:
        for action in self.findChildren(QAction):
            action.setStatusTip("")

    def onMacMinimize(self):
        self.setWindowState(self.windowState() | Qt.WindowMinimized)

    # Single instance support
    ##########################################################################

    def setupAppMsg(self) -> None:
        self.app.appMsg.connect(self.onAppMsg)

    def onAppMsg(self, buf: str) -> Optional[QTimer]:
        is_addon = self._isAddon(buf)

        if self.state == "startup":
            # try again in a second
            return self.progress.timer(
                1000, lambda: self.onAppMsg(buf), False, requiresCollection=False
            )
        elif self.state == "profileManager":
            # can't raise window while in profile manager
            if buf == "raise":
                return None
            self.pendingImport = buf
            if is_addon:
                msg = _("Add-on will be installed when a profile is opened.")
            else:
                msg = _("Deck will be imported when a profile is opened.")
            return tooltip(msg)
        if not self.interactiveState() or self.progress.busy():
            # we can't raise the main window while in profile dialog, syncing, etc
            if buf != "raise":
                showInfo(
                    _(
                        """\
Please ensure a profile is open and Anki is not busy, then try again."""
                    ),
                    parent=None,
                )
            return None
        # raise window
        if isWin:
            # on windows we can raise the window by minimizing and restoring
            self.showMinimized()
            self.setWindowState(Qt.WindowActive)
            self.showNormal()
        else:
            # on osx we can raise the window. on unity the icon in the tray will just flash.
            self.activateWindow()
            self.raise_()
        if buf == "raise":
            return None

        # import / add-on installation
        if is_addon:
            self.installAddon(buf)
        else:
            self.handleImport(buf)

        return None

    def _isAddon(self, buf: str) -> bool:
        return buf.endswith(self.addonManager.ext)

    # GC
    ##########################################################################
    # ensure gc runs in main thread

    def setupDialogGC(self, obj: Any) -> None:
        obj.finished.connect(lambda: self.gcWindow(obj))

    def gcWindow(self, obj: Any) -> None:
        obj.deleteLater()
        self.progress.timer(1000, self.doGC, False, requiresCollection=False)

    def disableGC(self) -> None:
        gc.collect()
        gc.disable()

    def doGC(self) -> None:
        gc.collect()

    # Crash log
    ##########################################################################

    def setupCrashLog(self) -> None:
        p = os.path.join(self.pm.base, "crash.log")
        self._crashLog = open(p, "ab", 0)
        faulthandler.enable(self._crashLog)

    # Media server
    ##########################################################################

    def setupMediaServer(self) -> None:
        self.mediaServer = aqt.mediasrv.MediaServer(self)
        self.mediaServer.start()

    def baseHTML(self) -> str:
        return '<base href="%s">' % self.serverURL()

    def serverURL(self) -> str:
        return "http://127.0.0.1:%d/" % self.mediaServer.getPort()
