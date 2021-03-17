# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import enum
import faulthandler
import gc
import os
import re
import signal
import time
import weakref
import zipfile
from argparse import Namespace
from concurrent.futures import Future
from threading import Thread
from typing import (
    Any,
    Callable,
    Dict,
    List,
    Literal,
    Optional,
    Protocol,
    Sequence,
    TextIO,
    Tuple,
    TypeVar,
    cast,
)

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
from anki._backend import RustBackend as _RustBackend
from anki.collection import (
    BackendUndo,
    Checkpoint,
    Collection,
    Config,
    OpChanges,
    OpChangesWithCount,
    ReviewUndo,
    UndoResult,
    UndoStatus,
)
from anki.decks import Deck
from anki.hooks import runHook
from anki.sound import AVTag, SoundOrVideoTag
from anki.types import assert_exhaustive
from anki.utils import devMode, ids2str, intTime, isMac, isWin, splitFields
from aqt import gui_hooks
from aqt.addons import DownloadLogEntry, check_and_prompt_for_updates, show_log_to_user
from aqt.dbcheck import check_db
from aqt.emptycards import show_empty_cards
from aqt.legacy import install_pylib_legacy
from aqt.mediacheck import check_media_db
from aqt.mediasync import MediaSyncer
from aqt.profiles import ProfileManager as ProfileManagerType
from aqt.qt import *
from aqt.qt import sip
from aqt.sync import sync_collection, sync_login
from aqt.taskman import TaskManager
from aqt.theme import theme_manager
from aqt.utils import (
    TR,
    HelpPage,
    KeyboardModifiersPressed,
    askUser,
    checkInvalidFilename,
    current_top_level_widget,
    disable_help_button,
    getFile,
    getOnlyText,
    openHelp,
    openLink,
    restoreGeom,
    restoreSplitter,
    restoreState,
    saveGeom,
    saveSplitter,
    showInfo,
    showWarning,
    tooltip,
    top_level_widget,
    tr,
)


class HasChangesProperty(Protocol):
    changes: OpChanges


# either an OpChanges object, or an object with .changes on it. This bound
# doesn't actually work for protobuf objects, so new protobuf objects will
# either need to be added here, or cast at call time
ResultWithChanges = TypeVar(
    "ResultWithChanges", bound=Union[OpChanges, OpChangesWithCount, HasChangesProperty]
)

PerformOpOptionalSuccessCallback = Optional[Callable[[ResultWithChanges], Any]]

install_pylib_legacy()

MainWindowState = Literal[
    "startup", "deckBrowser", "overview", "review", "resetRequired", "profileManager"
]


class AnkiQt(QMainWindow):
    col: Collection
    pm: ProfileManagerType
    web: aqt.webview.AnkiWebView
    bottomWeb: aqt.webview.AnkiWebView

    def __init__(
        self,
        app: aqt.AnkiApp,
        profileManager: ProfileManagerType,
        backend: _RustBackend,
        opts: Namespace,
        args: List[Any],
    ) -> None:
        QMainWindow.__init__(self)
        self.backend = backend
        self.state: MainWindowState = "startup"
        self.opts = opts
        self.col: Optional[Collection] = None
        self.taskman = TaskManager(self)
        self.media_syncer = MediaSyncer(self)
        aqt.mw = self
        self.app = app
        self.pm = profileManager
        # init rest of app
        self.safeMode = (KeyboardModifiersPressed().shift) or self.opts.safemode
        try:
            self.setupUI()
            self.setupAddons(args)
            self.finish_ui_setup()
        except:
            showInfo(tr(TR.QT_MISC_ERROR_DURING_STARTUP, val=traceback.format_exc()))
            sys.exit(1)
        # must call this after ui set up
        if self.safeMode:
            tooltip(tr(TR.QT_MISC_SHIFT_KEY_WAS_HELD_DOWN_SKIPPING))
        # were we given a file to import?
        if args and args[0] and not self._isAddon(args[0]):
            self.onAppMsg(args[0])
        # Load profile in a timer so we can let the window finish init and not
        # close on profile load error.
        if isWin:
            fn = self.setupProfileAfterWebviewsLoaded
        else:
            fn = self.setupProfile

        def on_window_init() -> None:
            fn()
            gui_hooks.main_window_did_init()

        self.progress.timer(10, on_window_init, False, requiresCollection=False)

    def setupUI(self) -> None:
        self.col = None
        self.setupCrashLog()
        self.disable_automatic_garbage_collection()
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
        self.setup_focus()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()

    def finish_ui_setup(self) -> None:
        "Actions that are deferred until after add-on loading."
        self.toolbar.draw()

    def setupProfileAfterWebviewsLoaded(self) -> None:
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

    def weakref(self) -> AnkiQt:
        "Shortcut to create a weak reference that doesn't break code completion."
        return weakref.proxy(self)  # type: ignore

    def setup_focus(self) -> None:
        qconnect(self.app.focusChanged, self.on_focus_changed)

    def on_focus_changed(self, old: QWidget, new: QWidget) -> None:
        gui_hooks.focus_did_change(new, old)

    # Profiles
    ##########################################################################

    class ProfileManager(QMainWindow):
        onClose = pyqtSignal()
        closeFires = True

        def closeEvent(self, evt: QCloseEvent) -> None:
            if self.closeFires:
                self.onClose.emit()  # type: ignore
            evt.accept()

        def closeWithoutQuitting(self) -> None:
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
        qconnect(f.login.clicked, self.onOpenProfile)
        qconnect(f.profiles.itemDoubleClicked, self.onOpenProfile)
        qconnect(f.openBackup.clicked, self.onOpenBackup)
        qconnect(f.quit.clicked, d.close)
        qconnect(d.onClose, self.cleanupAndExit)
        qconnect(f.add.clicked, self.onAddProfile)
        qconnect(f.rename.clicked, self.onRenameProfile)
        qconnect(f.delete_2.clicked, self.onRemProfile)
        qconnect(f.profiles.currentRowChanged, self.onProfileRowChange)
        f.statusbar.setVisible(False)
        qconnect(f.downgrade_button.clicked, self._on_downgrade)
        f.downgrade_button.setText(tr(TR.PROFILES_DOWNGRADE_AND_QUIT))
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
        self.pm.load(name)

    def openProfile(self) -> None:
        name = self.pm.profiles()[self.profileForm.profiles.currentRow()]
        self.pm.load(name)
        return

    def onOpenProfile(self) -> None:
        self.profileDiag.hide()
        # code flow is confusing here - if load fails, profile dialog
        # will be shown again
        self.loadProfile(self.profileDiag.closeWithoutQuitting)

    def profileNameOk(self, name: str) -> bool:
        return not checkInvalidFilename(name) and name != "addons21"

    def onAddProfile(self) -> None:
        name = getOnlyText(tr(TR.ACTIONS_NAME)).strip()
        if name:
            if name in self.pm.profiles():
                showWarning(tr(TR.QT_MISC_NAME_EXISTS))
                return
            if not self.profileNameOk(name):
                return
            self.pm.create(name)
            self.pm.name = name
            self.refreshProfilesList()

    def onRenameProfile(self) -> None:
        name = getOnlyText(tr(TR.ACTIONS_NEW_NAME), default=self.pm.name).strip()
        if not name:
            return
        if name == self.pm.name:
            return
        if name in self.pm.profiles():
            showWarning(tr(TR.QT_MISC_NAME_EXISTS))
            return
        if not self.profileNameOk(name):
            return
        self.pm.rename(name)
        self.refreshProfilesList()

    def onRemProfile(self) -> None:
        profs = self.pm.profiles()
        if len(profs) < 2:
            showWarning(tr(TR.QT_MISC_THERE_MUST_BE_AT_LEAST_ONE))
            return
        # sure?
        if not askUser(
            tr(TR.QT_MISC_ALL_CARDS_NOTES_AND_MEDIA_FOR),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            return
        self.pm.remove(self.pm.name)
        self.refreshProfilesList()

    def onOpenBackup(self) -> None:
        if not askUser(
            tr(TR.QT_MISC_REPLACE_YOUR_COLLECTION_WITH_AN_EARLIER),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            return

        def doOpen(path: str) -> None:
            self._openBackup(path)

        getFile(
            self.profileDiag,
            tr(TR.QT_MISC_REVERT_TO_BACKUP),
            cb=doOpen,  # type: ignore
            filter="*.colpkg",
            dir=self.pm.backupFolder(),
        )

    def _openBackup(self, path: str) -> None:
        try:
            # move the existing collection to the trash, as it may not open
            self.pm.trashCollection()
        except:
            showWarning(tr(TR.QT_MISC_UNABLE_TO_MOVE_EXISTING_FILE_TO))
            return

        self.pendingImport = path
        self.restoringBackup = True

        showInfo(tr(TR.QT_MISC_AUTOMATIC_SYNCING_AND_BACKUPS_HAVE_BEEN))

        self.onOpenProfile()

    def _on_downgrade(self) -> None:
        self.progress.start()
        profiles = self.pm.profiles()

        def downgrade() -> List[str]:
            return self.pm.downgrade(profiles)

        def on_done(future: Future) -> None:
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
        if not self.loadCollection():
            return

        # show main window
        if self.pm.profile["mainWindowState"]:
            restoreGeom(self, "mainWindow")
            restoreState(self, "mainWindow")
        # titlebar
        self.setWindowTitle(f"{self.pm.name} - Anki")
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

        def _onsuccess() -> None:
            self._refresh_after_sync()
            if onsuccess:
                onsuccess()

        self.maybe_auto_sync_on_open_close(_onsuccess)

    def unloadProfile(self, onsuccess: Callable) -> None:
        def callback() -> None:
            self._unloadProfile()
            onsuccess()

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

    def _checkForUnclosedWidgets(self) -> None:
        for w in self.app.topLevelWidgets():
            if w.isVisible():
                # windows with this property are safe to close immediately
                if getattr(w, "silentlyClose", None):
                    w.close()
                else:
                    print(f"Window should have been closed: {w}")

    def unloadProfileAndExit(self) -> None:
        self.unloadProfile(self.cleanupAndExit)

    def unloadProfileAndShowProfileManager(self) -> None:
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
        if self.col.get_config_bool(Config.Bool.HIDE_AUDIO_PLAY_BUTTONS):
            return anki.sound.strip_av_refs(text)
        else:
            return aqt.sound.av_refs_to_play_icons(text)

    def prepare_card_text_for_display(self, text: str) -> str:
        text = self.col.media.escape_media_filenames(text)
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
                    f"{tr(TR.ERRORS_UNABLE_OPEN_COLLECTION)}\n{traceback.format_exc()}"
                )
            # clean up open collection if possible
            try:
                self.backend.close_collection(False)
            except Exception as e:
                print("unable to close collection:", e)
            self.col = None
            # return to profile manager
            self.hide()
            self.showProfileManager()
            return False

        # make sure we don't get into an inconsistent state if an add-on
        # has broken the deck browser or the did_load hook
        try:
            self.update_undo_actions()
            gui_hooks.collection_did_load(self.col)
            self.apply_collection_options()
            self.moveToState("deckBrowser")
        except Exception as e:
            # dump error to stderr so it gets picked up by errors.py
            traceback.print_exc()

        return True

    def _loadCollection(self) -> None:
        cpath = self.pm.collectionPath()
        self.col = Collection(cpath, backend=self.backend, log=True)
        self.setEnabled(True)

    def reopen(self) -> None:
        self.col.reopen()

    def unloadCollection(self, onsuccess: Callable) -> None:
        def after_media_sync() -> None:
            self._unloadCollection()
            onsuccess()

        def after_sync() -> None:
            self.media_syncer.show_diag_until_finished(after_media_sync)

        def before_sync() -> None:
            self.setEnabled(False)
            self.maybe_auto_sync_on_open_close(after_sync)

        self.closeAllWindows(before_sync)

    def _unloadCollection(self) -> None:
        if not self.col:
            return
        if self.restoringBackup:
            label = tr(TR.QT_MISC_CLOSING)
        else:
            label = tr(TR.QT_MISC_BACKING_UP)
        self.progress.start(label=label)
        corrupt = False
        try:
            self.maybeOptimize()
            if not devMode:
                corrupt = self.col.db.scalar("pragma quick_check") != "ok"
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
            showWarning(tr(TR.QT_MISC_YOUR_COLLECTION_FILE_APPEARS_TO_BE))
        if not corrupt and not self.restoringBackup:
            self.backup()

    def _close_for_full_download(self) -> None:
        "Backup and prepare collection to be overwritten."
        self.col.close(downgrade=False)
        self.backup()
        self.col.reopen(after_full_sync=False)
        self.col.close_for_full_sync()

    def apply_collection_options(self) -> None:
        "Setup audio after collection loaded."
        aqt.sound.av_player.interrupt_current_audio = self.col.get_config_bool(
            Config.Bool.INTERRUPT_AUDIO_WHEN_ANSWERING
        )

    # Backup and auto-optimize
    ##########################################################################

    class BackupThread(Thread):
        def __init__(self, path: str, data: bytes) -> None:
            Thread.__init__(self)
            self.path = path
            self.data = data
            # create the file in calling thread to ensure the same
            # file is not created twice
            with open(self.path, "wb") as file:
                pass

        def run(self) -> None:
            z = zipfile.ZipFile(self.path, "w", zipfile.ZIP_DEFLATED)
            z.writestr("collection.anki2", self.data)
            z.writestr("media", "{}")
            z.close()

    def backup(self) -> None:
        "Read data into memory, and complete backup on a background thread."
        assert not self.col or not self.col.db

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
        self.BackupThread(newpath, data).start()

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

        self.taskman.run_on_main(gui_hooks.backup_did_complete)

    def maybeOptimize(self) -> None:
        # have two weeks passed?
        if (intTime() - self.pm.profile["lastOptimize"]) < 86400 * 14:
            return
        self.progress.start(label=tr(TR.QT_MISC_OPTIMIZING))
        self.col.optimize()
        self.pm.profile["lastOptimize"] = intTime()
        self.pm.save()
        self.progress.finish()

    # Tracking main window state (deck browser, reviewer, etc)
    ##########################################################################

    def moveToState(self, state: MainWindowState, *args: Any) -> None:
        # print("-> move from", self.state, "to", state)
        oldState = self.state
        cleanup = getattr(self, f"_{oldState}Cleanup", None)
        if cleanup:
            # pylint: disable=not-callable
            cleanup(state)
        self.clearStateShortcuts()
        self.state = state
        gui_hooks.state_will_change(state, oldState)
        getattr(self, f"_{state}State")(oldState, *args)
        if state != "resetRequired":
            self.bottomWeb.show()
        gui_hooks.state_did_change(state, oldState)

    def _deckBrowserState(self, oldState: str) -> None:
        self.maybe_check_for_addon_updates()
        self.deckBrowser.show()

    def _selectedDeck(self) -> Optional[Deck]:
        did = self.col.decks.selected()
        if not self.col.decks.name_if_exists(did):
            showInfo(tr(TR.QT_MISC_PLEASE_SELECT_A_DECK))
            return None
        return self.col.decks.get(did)

    def _overviewState(self, oldState: str) -> None:
        if not self._selectedDeck():
            return self.moveToState("deckBrowser")
        self.overview.show()

    def _reviewState(self, oldState: str) -> None:
        self.reviewer.show()

    def _reviewCleanup(self, newState: str) -> None:
        if newState != "resetRequired" and newState != "review":
            self.reviewer.cleanup()

    # Resetting state
    ##########################################################################

    def perform_op(
        self,
        op: Callable[[], ResultWithChanges],
        *,
        success: PerformOpOptionalSuccessCallback = None,
        failure: Optional[Callable[[Exception], Any]] = None,
        after_hooks: Optional[Callable[[], None]] = None,
    ) -> None:
        """Run the provided operation on a background thread.

        op() should either return OpChanges, or an object with a 'changes'
        property. The changes will be passed to `operation_did_execute` so that
        the UI can decide whether it needs to update itself.

        - Shows progress popup for the duration of the op.
        - Ensures the browser doesn't try to redraw during the operation, which can lead
        to a frozen UI
        - Updates undo state at the end of the operation
        - Commits changes
        - Fires the `operation_(will|did)_reset` hooks
        - Fires the legacy `state_did_reset` hook

        Be careful not to call any UI routines in `op`, as that may crash Qt.
        This includes things select .selectedCards() in the browse screen.

        success() will be called with the return value of op().

        If op() throws an exception, it will be shown in a popup, or
        passed to failure() if it is provided.

        after_hooks() will be called after hooks are fired, if it is provided.
        Components can use this to ignore change notices generated by operations
        they invoke themselves.
        """

        gui_hooks.operation_will_execute()

        def wrapped_done(future: Future) -> None:
            # did something go wrong?
            if exception := future.exception():
                if isinstance(exception, Exception):
                    if failure:
                        failure(exception)
                    else:
                        showWarning(str(exception))
                    return
                else:
                    # BaseException like SystemExit; rethrow it
                    future.result()
            try:
                result = future.result()
                if success:
                    success(result)
            finally:
                # update undo status
                status = self.col.undo_status()
                self._update_undo_actions_for_status_and_save(status)
                # fire change hooks
                self._fire_change_hooks_after_op_performed(result, after_hooks)

        self.taskman.with_progress(op, wrapped_done)

    def _fire_change_hooks_after_op_performed(
        self, result: ResultWithChanges, after_hooks: Optional[Callable[[], None]]
    ) -> None:
        if isinstance(result, OpChanges):
            changes = result
        else:
            changes = result.changes

        # fire new hook
        print("op changes:")
        print(changes)
        gui_hooks.operation_did_execute(changes)
        # fire legacy hook so old code notices changes
        if self.col.op_made_changes(changes):
            gui_hooks.state_did_reset()
        if after_hooks:
            after_hooks()

    def _synthesize_op_did_execute_from_reset(self) -> None:
        """Fire the `operation_did_execute` hook with everything marked as changed,
        after legacy code has called .reset()"""
        op = OpChanges()
        for field in op.DESCRIPTOR.fields:
            if field.name != "kind":
                setattr(op, field.name, True)
        gui_hooks.operation_did_execute(op)

    def on_operation_did_execute(self, changes: OpChanges) -> None:
        "Notify current screen of changes."
        focused = current_top_level_widget() == self
        if self.state == "review":
            dirty = self.reviewer.op_executed(changes, focused)
        elif self.state == "overview":
            dirty = self.overview.op_executed(changes, focused)
        elif self.state == "deckBrowser":
            dirty = self.deckBrowser.op_executed(changes, focused)
        else:
            dirty = False

        if not focused and dirty:
            self.fade_out_webview()

    def on_focus_did_change(
        self, new_focus: Optional[QWidget], _old: Optional[QWidget]
    ) -> None:
        "If main window has received focus, ensure current UI state is updated."
        if new_focus and top_level_widget(new_focus) == self:
            if self.state == "review":
                self.reviewer.refresh_if_needed()
            elif self.state == "overview":
                self.overview.refresh_if_needed()
            elif self.state == "deckBrowser":
                self.deckBrowser.refresh_if_needed()

    def fade_out_webview(self) -> None:
        self.web.eval("document.body.style.opacity = 0.3")

    def fade_in_webview(self) -> None:
        self.web.eval("document.body.style.opacity = 1")

    def reset(self, unused_arg: bool = False) -> None:
        """Legacy method of telling UI to refresh after changes made to DB.

        New code should use mw.perform_op() instead."""
        if self.col:
            # fire new `operation_did_execute` hook first. If the overview
            # or review screen are currently open, they will rebuild the study
            # queues (via mw.col.reset())
            self._synthesize_op_did_execute_from_reset()
            # fire the old reset hook
            gui_hooks.state_did_reset()
            self.update_undo_actions()

    # legacy

    def requireReset(
        self,
        modal: bool = False,
        reason: Any = None,
        context: Any = None,
    ) -> None:
        self.reset()

    def maybeReset(self) -> None:
        pass

    def delayedMaybeReset(self) -> None:
        pass

    def _resetRequiredState(self, oldState: MainWindowState) -> None:
        pass

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
        class_ = f"but {class_}"
        if key:
            key = tr(TR.ACTIONS_SHORTCUT_KEY, val=key)
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
            for webview in self.web, self.bottomWeb:
                webview.force_load_hack()

    def closeAllWindows(self, onsuccess: Callable) -> None:
        aqt.dialogs.closeAll(onsuccess)

    # Components
    ##########################################################################

    def setupSignals(self) -> None:
        signal.signal(signal.SIGINT, self.onUnixSignal)
        signal.signal(signal.SIGTERM, self.onUnixSignal)

    def onUnixSignal(self, signum: Any, frame: Any) -> None:
        # schedule a rollback & quit
        def quit() -> None:
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

    def maybe_check_for_addon_updates(self) -> None:
        last_check = self.pm.last_addon_update_check()
        elap = intTime() - last_check

        if elap > 86_400:
            check_and_prompt_for_updates(
                self,
                self.addonManager,
                self.on_updates_installed,
                requested_by_user=False,
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

    def on_sync_button_clicked(self) -> None:
        if self.media_syncer.is_syncing():
            self.media_syncer.show_sync_log()
        else:
            auth = self.pm.sync_auth()
            if not auth:
                sync_login(
                    self,
                    lambda: self._sync_collection_and_media(self._refresh_after_sync),
                )
            else:
                self._sync_collection_and_media(self._refresh_after_sync)

    def _refresh_after_sync(self) -> None:
        self.toolbar.redraw()

    def _sync_collection_and_media(self, after_sync: Callable[[], None]) -> None:
        "Caller should ensure auth available."
        # start media sync if not already running
        if not self.media_syncer.is_syncing():
            self.media_syncer.start()

        def on_collection_sync_finished() -> None:
            self.col.clear_python_undo()
            self.col.models._clear_cache()
            gui_hooks.sync_did_finish()
            self.reset()

            after_sync()

        gui_hooks.sync_will_start()
        sync_collection(self, on_done=on_collection_sync_finished)

    def maybe_auto_sync_on_open_close(self, after_sync: Callable[[], None]) -> None:
        "If disabled, after_sync() is called immediately."
        if self.can_auto_sync():
            self._sync_collection_and_media(after_sync)
        else:
            after_sync()

    def maybe_auto_sync_media(self) -> None:
        if self.can_auto_sync():
            return
        # media_syncer takes care of media syncing preference check
        self.media_syncer.start()

    def can_auto_sync(self) -> bool:
        return (
            self.pm.auto_syncing_enabled()
            and bool(self.pm.sync_auth())
            and not self.safeMode
            and not self.restoringBackup
        )

    # legacy
    def _sync(self) -> None:
        pass

    onSync = on_sync_button_clicked

    # Tools
    ##########################################################################

    def raiseMain(self) -> bool:
        if not self.app.activeWindow():
            # make sure window is shown
            self.setWindowState(self.windowState() & ~Qt.WindowMinimized)  # type: ignore
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
            ("y", self.on_sync_button_clicked),
        ]
        self.applyShortcuts(globalShortcuts)
        self.stateShortcuts: List[QShortcut] = []

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
        runHook(f"{self.state}StateShortcuts", shortcuts)
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

    def undo(self, on_done: Optional[Callable[[UndoResult], None]]) -> None:
        def on_done_outer(fut: Future) -> None:
            result = fut.result()
            reviewing = self.state == "review"
            just_refresh_reviewer = False

            if result is None:
                # should not happen
                showInfo("nothing to undo")
                self.update_undo_actions()
                return

            elif isinstance(result, ReviewUndo):
                name = tr(TR.SCHEDULING_REVIEW)

                if reviewing:
                    # push the undone card to the top of the queue
                    cid = result.card.id
                    card = self.col.getCard(cid)
                    self.reviewer.cardQueue.append(card)

                    gui_hooks.review_did_undo(cid)

                    just_refresh_reviewer = True

            elif isinstance(result, BackendUndo):
                name = result.name

                if reviewing and self.col.sched.version == 3:
                    # new scheduler will have taken care of updating queue
                    just_refresh_reviewer = True

            elif isinstance(result, Checkpoint):
                name = result.name

            else:
                assert_exhaustive(result)
                assert False

            if just_refresh_reviewer:
                self.reviewer.nextCard()
            else:
                # full queue+gui reset required
                self.reset()

            tooltip(tr(TR.UNDO_ACTION_UNDONE, action=name))
            gui_hooks.state_did_revert(name)
            self.update_undo_actions()
            if on_done:
                on_done(result)

        self.taskman.with_progress(self.col.undo, on_done_outer)

    def update_undo_actions(self) -> None:
        """Update menu text and enable/disable menu item as appropriate.
        Plural as this may handle redo in the future too."""
        if self.col:
            status = self.col.undo_status()
            undo_action = status.undo or None
        else:
            undo_action = None

        if undo_action:
            undo_action = tr(TR.UNDO_UNDO_ACTION, val=undo_action)
            self.form.actionUndo.setText(undo_action)
            self.form.actionUndo.setEnabled(True)
            gui_hooks.undo_state_did_change(True)
        else:
            self.form.actionUndo.setText(tr(TR.UNDO_UNDO))
            self.form.actionUndo.setEnabled(False)
            gui_hooks.undo_state_did_change(False)

    def _update_undo_actions_for_status_and_save(self, status: UndoStatus) -> None:
        """Update menu text and enable/disable menu item as appropriate.
        Plural as this may handle redo in the future too."""
        undo_action = status.undo

        if undo_action:
            undo_action = tr(TR.UNDO_UNDO_ACTION, val=undo_action)
            self.form.actionUndo.setText(undo_action)
            self.form.actionUndo.setEnabled(True)
            gui_hooks.undo_state_did_change(True)
        else:
            self.form.actionUndo.setText(tr(TR.UNDO_UNDO))
            self.form.actionUndo.setEnabled(False)
            gui_hooks.undo_state_did_change(False)

        self.col.autosave()

    def checkpoint(self, name: str) -> None:
        self.col.save(name)
        self.update_undo_actions()

    def autosave(self) -> None:
        self.col.autosave()
        self.update_undo_actions()

    maybeEnableUndo = update_undo_actions
    onUndo = undo

    # Other menu operations
    ##########################################################################

    def onAddCard(self) -> None:
        aqt.dialogs.open("AddCards", self)

    def onBrowse(self) -> None:
        aqt.dialogs.open("Browser", self, card=self.reviewer.card)

    def onEditCurrent(self) -> None:
        aqt.dialogs.open("EditCurrent", self)

    def onDeckConf(self, deck: Optional[Deck] = None) -> None:
        import aqt.deckconf

        if not deck:
            deck = self.col.decks.current()
        if deck["dyn"]:
            aqt.dialogs.open("DynDeckConfDialog", self, deck=deck)
        else:
            aqt.deckconf.DeckConf(self, deck)

    def onOverview(self) -> None:
        self.col.reset()
        self.moveToState("overview")

    def onStats(self) -> None:
        deck = self._selectedDeck()
        if not deck:
            return
        want_old = KeyboardModifiersPressed().shift
        if want_old:
            aqt.dialogs.open("DeckStats", self)
        else:
            aqt.dialogs.open("NewDeckStats", self)

    def onPrefs(self) -> None:
        aqt.dialogs.open("Preferences", self)

    def onNoteTypes(self) -> None:
        import aqt.models

        aqt.models.Models(self, self, fromMain=True)

    def onAbout(self) -> None:
        aqt.dialogs.open("About", self)

    def onDonate(self) -> None:
        openLink(aqt.appDonate)

    def onDocumentation(self) -> None:
        openHelp(HelpPage.INDEX)

    # Importing & exporting
    ##########################################################################

    def handleImport(self, path: str) -> None:
        import aqt.importing

        if not os.path.exists(path):
            showInfo(tr(TR.QT_MISC_PLEASE_USE_FILEIMPORT_TO_IMPORT_THIS))
            return None

        aqt.importing.importFile(self, path)
        return None

    def onImport(self) -> None:
        import aqt.importing

        aqt.importing.onImport(self)

    def onExport(self, did: Optional[int] = None) -> None:
        import aqt.exporting

        aqt.exporting.ExportDialog(self, did=did)

    # Installing add-ons from CLI / mimetype handler
    ##########################################################################

    def installAddon(self, path: str, startup: bool = False) -> None:
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

    def onCram(self) -> None:
        aqt.dialogs.open("DynDeckConfDialog", self)

    # Menu, title bar & status
    ##########################################################################

    def setupMenus(self) -> None:
        m = self.form
        qconnect(
            m.actionSwitchProfile.triggered, self.unloadProfileAndShowProfileManager
        )
        qconnect(m.actionImport.triggered, self.onImport)
        qconnect(m.actionExport.triggered, self.onExport)
        qconnect(m.actionExit.triggered, self.close)
        qconnect(m.actionPreferences.triggered, self.onPrefs)
        qconnect(m.actionAbout.triggered, self.onAbout)
        qconnect(m.actionUndo.triggered, self.onUndo)
        if qtminor < 11:
            m.actionUndo.setShortcut(QKeySequence("Ctrl+Alt+Z"))
        qconnect(m.actionFullDatabaseCheck.triggered, self.onCheckDB)
        qconnect(m.actionCheckMediaDatabase.triggered, self.on_check_media_db)
        qconnect(m.actionDocumentation.triggered, self.onDocumentation)
        qconnect(m.actionDonate.triggered, self.onDonate)
        qconnect(m.actionStudyDeck.triggered, self.onStudyDeck)
        qconnect(m.actionCreateFiltered.triggered, self.onCram)
        qconnect(m.actionEmptyCards.triggered, self.onEmptyCards)
        qconnect(m.actionNoteTypes.triggered, self.onNoteTypes)

    def updateTitleBar(self) -> None:
        self.setWindowTitle("Anki")

    # Auto update
    ##########################################################################

    def setupAutoUpdate(self) -> None:
        import aqt.update

        self.autoUpdate = aqt.update.LatestVersionFinder(self)
        qconnect(self.autoUpdate.newVerAvail, self.newVerAvail)
        qconnect(self.autoUpdate.newMsg, self.newMsg)
        qconnect(self.autoUpdate.clockIsOff, self.clockIsOff)
        self.autoUpdate.start()

    def newVerAvail(self, ver: str) -> None:
        if self.pm.meta.get("suppressUpdate", None) != ver:
            aqt.update.askAndUpdate(self, ver)

    def newMsg(self, data: Dict) -> None:
        aqt.update.showMessages(self, data)

    def clockIsOff(self, diff: int) -> None:
        if devMode:
            print("clock is off; ignoring")
            return
        diffText = tr(TR.QT_MISC_SECOND, count=diff)
        warn = tr(TR.QT_MISC_IN_ORDER_TO_ENSURE_YOUR_COLLECTION, val="%s") % diffText
        showWarning(warn)
        self.app.closeAllWindows()

    # Timers
    ##########################################################################

    def setup_timers(self) -> None:
        # refresh decks every 10 minutes
        self.progress.timer(10 * 60 * 1000, self.onRefreshTimer, True)
        # check media sync every 5 minutes
        self.progress.timer(5 * 60 * 1000, self.on_autosync_timer, True)
        # periodic garbage collection
        self.progress.timer(15 * 60 * 1000, self.garbage_collect_now, False)
        # ensure Python interpreter runs at least once per second, so that
        # SIGINT/SIGTERM is processed without a long delay
        self.progress.timer(1000, lambda: None, True, False)

    def onRefreshTimer(self) -> None:
        if self.state == "deckBrowser":
            self.deckBrowser.refresh()
        elif self.state == "overview":
            self.overview.refresh()

    def on_autosync_timer(self) -> None:
        elap = self.media_syncer.seconds_since_last_sync()
        minutes = self.pm.auto_sync_media_minutes()
        if not minutes:
            return
        if elap > minutes * 60:
            self.maybe_auto_sync_media()

    # Permanent hooks
    ##########################################################################

    def setupHooks(self) -> None:
        hooks.schema_will_change.append(self.onSchemaMod)
        hooks.notes_will_be_deleted.append(self.onRemNotes)
        hooks.card_odue_was_invalid.append(self.onOdueInvalid)

        gui_hooks.av_player_will_play.append(self.on_av_player_will_play)
        gui_hooks.av_player_did_end_playing.append(self.on_av_player_did_end_playing)
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        gui_hooks.focus_did_change.append(self.on_focus_did_change)

        self._activeWindowOnPlay: Optional[QWidget] = None

    def onOdueInvalid(self) -> None:
        showWarning(tr(TR.QT_MISC_INVALID_PROPERTY_FOUND_ON_CARD_PLEASE))

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

    def onRemNotes(self, col: Collection, nids: Sequence[int]) -> None:
        path = os.path.join(self.pm.profileFolder(), "deleted.txt")
        existed = os.path.exists(path)
        with open(path, "ab") as f:
            if not existed:
                f.write(b"nid\tmid\tfields\n")
            for id, mid, flds in col.db.execute(
                f"select id, mid, flds from notes where id in {ids2str(nids)}"
            ):
                fields = splitFields(flds)
                f.write(("\t".join([str(id), str(mid)] + fields)).encode("utf8"))
                f.write(b"\n")

    # Schema modifications
    ##########################################################################

    # this will gradually be phased out
    def onSchemaMod(self, arg: bool) -> bool:
        assert self.inMainThread()
        progress_shown = self.progress.busy()
        if progress_shown:
            self.progress.finish()
        ret = askUser(tr(TR.QT_MISC_THE_REQUESTED_CHANGE_WILL_REQUIRE_A))
        if progress_shown:
            self.progress.start()
        return ret

    # in favour of this
    def confirm_schema_modification(self) -> bool:
        """If schema unmodified, ask user to confirm change.
        True if confirmed or already modified."""
        if self.col.schemaChanged():
            return True
        return askUser(tr(TR.QT_MISC_THE_REQUESTED_CHANGE_WILL_REQUIRE_A))

    # Advanced features
    ##########################################################################

    def onCheckDB(self) -> None:
        check_db(self)

    def on_check_media_db(self) -> None:
        gui_hooks.media_check_will_start()
        check_media_db(self)

    def onStudyDeck(self) -> None:
        from aqt.studydeck import StudyDeck

        ret = StudyDeck(self, dyn=True, current=self.col.decks.current()["name"])
        if ret.name:
            self.col.decks.select(self.col.decks.id(ret.name))
            self.moveToState("overview")

    def onEmptyCards(self) -> None:
        show_empty_cards(self)

    # Debugging
    ######################################################################

    def onDebug(self) -> None:
        frm = self.debug_diag_form = aqt.forms.debug.Ui_Dialog()

        class DebugDialog(QDialog):
            silentlyClose = True

            def reject(self) -> None:
                super().reject()
                saveSplitter(frm.splitter, "DebugConsoleWindow")
                saveGeom(self, "DebugConsoleWindow")

        d = self.debugDiag = DebugDialog()
        disable_help_button(d)
        frm.setupUi(d)
        restoreGeom(d, "DebugConsoleWindow")
        restoreSplitter(frm.splitter, "DebugConsoleWindow")
        font = QFontDatabase.systemFont(QFontDatabase.FixedFont)
        font.setPointSize(frm.text.font().pointSize() + 1)
        frm.text.setFont(font)
        frm.log.setFont(font)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+return"), d)
        qconnect(s.activated, lambda: self.onDebugRet(frm))
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+shift+return"), d)
        qconnect(s.activated, lambda: self.onDebugPrint(frm))
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+l"), d)
        qconnect(s.activated, frm.log.clear)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+shift+l"), d)
        qconnect(s.activated, frm.text.clear)

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
            menu.exec_(QCursor.pos())

        frm.log.contextMenuEvent = lambda ev: addContextMenu(ev, "log")
        frm.text.contextMenuEvent = lambda ev: addContextMenu(ev, "text")
        gui_hooks.debug_console_will_show(d)
        d.show()

    def _captureOutput(self, on: bool) -> None:
        mw2 = self

        class Stream:
            def write(self, data: str) -> None:
                mw2._output += data

        if on:
            self._output = ""
            self._oldStderr = sys.stderr
            self._oldStdout = sys.stdout
            s = cast(TextIO, Stream())
            sys.stderr = s
            sys.stdout = s
        else:
            sys.stderr = self._oldStderr
            sys.stdout = self._oldStdout

    def _card_repr(self, card: anki.cards.Card) -> None:
        import copy
        import pprint

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

    def onDebugPrint(self, frm: aqt.forms.debug.Ui_Dialog) -> None:
        cursor = frm.text.textCursor()
        position = cursor.position()
        cursor.select(QTextCursor.LineUnderCursor)
        line = cursor.selectedText()
        pfx, sfx = "pp(", ")"
        if not line.startswith(pfx):
            line = f"{pfx}{line}{sfx}"
            cursor.insertText(line)
            cursor.setPosition(position + len(pfx))
            frm.text.setTextCursor(cursor)
        self.onDebugRet(frm)

    def onDebugRet(self, frm: aqt.forms.debug.Ui_Dialog) -> None:
        import pprint
        import traceback

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
                buf += f">>> {line}\n"
            else:
                buf += f"... {line}\n"
        try:
            to_append = buf + (self._output or "<no output>")
            to_append = gui_hooks.debug_console_did_evaluate_python(
                to_append, text, frm
            )
            frm.log.appendPlainText(to_append)
        except UnicodeDecodeError:
            to_append = tr(TR.QT_MISC_NON_UNICODE_TEXT)
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
            qconnect(self.minimizeShortcut.activated, self.onMacMinimize)
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
        for action_ in tgt.findChildren(QAction):
            action = cast(QAction, action_)
            txt = str(action.text())
            m = re.match(r"^(.+)\(&.+\)(.+)?", txt)
            if m:
                action.setText(m.group(1) + (m.group(2) or ""))

    def hideStatusTips(self) -> None:
        for action in self.findChildren(QAction):
            cast(QAction, action).setStatusTip("")

    def onMacMinimize(self) -> None:
        self.setWindowState(self.windowState() | Qt.WindowMinimized)  # type: ignore

    # Single instance support
    ##########################################################################

    def setupAppMsg(self) -> None:
        qconnect(self.app.appMsg, self.onAppMsg)

    def onAppMsg(self, buf: str) -> None:
        is_addon = self._isAddon(buf)

        if self.state == "startup":
            # try again in a second
            self.progress.timer(
                1000, lambda: self.onAppMsg(buf), False, requiresCollection=False
            )
            return
        elif self.state == "profileManager":
            # can't raise window while in profile manager
            if buf == "raise":
                return None
            self.pendingImport = buf
            if is_addon:
                msg = tr(TR.QT_MISC_ADDON_WILL_BE_INSTALLED_WHEN_A)
            else:
                msg = tr(TR.QT_MISC_DECK_WILL_BE_IMPORTED_WHEN_A)
            tooltip(msg)
            return
        if not self.interactiveState() or self.progress.busy():
            # we can't raise the main window while in profile dialog, syncing, etc
            if buf != "raise":
                showInfo(
                    tr(TR.QT_MISC_PLEASE_ENSURE_A_PROFILE_IS_OPEN),
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

    def interactiveState(self) -> bool:
        "True if not in profile manager, syncing, etc."
        return self.state in ("overview", "review", "deckBrowser")

    # GC
    ##########################################################################
    # The default Python garbage collection can trigger on any thread. This can
    # cause crashes if Qt objects are garbage-collected, as Qt expects access
    # only on the main thread. So Anki disables the default GC on startup, and
    # instead runs it on a timer, and after dialog close.
    # The gc after dialog close is necessary to free up the memory and extra
    # processes that webviews spawn, as a lot of the GUI code creates ref cycles.

    def garbage_collect_on_dialog_finish(self, dialog: QDialog) -> None:
        qconnect(
            dialog.finished, lambda: self.deferred_delete_and_garbage_collect(dialog)
        )

    def deferred_delete_and_garbage_collect(self, obj: QObject) -> None:
        obj.deleteLater()
        self.progress.timer(
            1000, self.garbage_collect_now, False, requiresCollection=False
        )

    def disable_automatic_garbage_collection(self) -> None:
        gc.collect()
        gc.disable()

    def garbage_collect_now(self) -> None:
        # gc.collect() has optional arguments that will cause problems if
        # it's passed directly to a QTimer, and pylint complains if we
        # wrap it in a lambda, so we use this trivial wrapper
        gc.collect()

    # legacy aliases

    setupDialogGC = garbage_collect_on_dialog_finish
    gcWindow = deferred_delete_and_garbage_collect

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
        return f'<base href="{self.serverURL()}">'

    def serverURL(self) -> str:
        return "http://127.0.0.1:%d/" % self.mediaServer.getPort()


# legacy
class ResetReason(enum.Enum):
    Unknown = "unknown"
    AddCardsAddNote = "addCardsAddNote"
    EditCurrentInit = "editCurrentInit"
    EditorBridgeCmd = "editorBridgeCmd"
    BrowserSetDeck = "browserSetDeck"
    BrowserAddTags = "browserAddTags"
    BrowserRemoveTags = "browserRemoveTags"
    BrowserSuspend = "browserSuspend"
    BrowserReposition = "browserReposition"
    BrowserReschedule = "browserReschedule"
    BrowserFindReplace = "browserFindReplace"
    BrowserTagDupes = "browserTagDupes"
    BrowserDeleteDeck = "browserDeleteDeck"
