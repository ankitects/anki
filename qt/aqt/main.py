# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import enum
import gc
import os
import re
import signal
import sys
import traceback
import weakref
from argparse import Namespace
from collections.abc import Callable, Sequence
from concurrent.futures import Future
from typing import Any, Literal, TypeVar, cast

import anki
import anki.cards
import anki.sound
import aqt
import aqt.forms
import aqt.mediasrv
import aqt.mpv
import aqt.operations
import aqt.progress
import aqt.sound
import aqt.stats
import aqt.toolbar
import aqt.webview
from anki import hooks
from anki._backend import RustBackend as _RustBackend
from anki._legacy import deprecated
from anki.collection import Collection, Config, OpChanges, UndoStatus
from anki.decks import DeckDict, DeckId
from anki.hooks import runHook
from anki.notes import NoteId
from anki.sound import AVTag, SoundOrVideoTag
from anki.utils import (
    dev_mode,
    ids2str,
    int_time,
    int_version,
    is_lin,
    is_mac,
    is_win,
    split_fields,
)
from aqt import gui_hooks
from aqt.addons import DownloadLogEntry, check_and_prompt_for_updates, show_log_to_user
from aqt.dbcheck import check_db
from aqt.debug_console import show_debug_console
from aqt.emptycards import show_empty_cards
from aqt.flags import FlagManager
from aqt.import_export.exporting import ExportDialog
from aqt.import_export.importing import (
    import_collection_package_op,
    import_file,
    prompt_for_file_then_import,
)
from aqt.legacy import install_pylib_legacy
from aqt.mediacheck import check_media_db
from aqt.mediasync import MediaSyncer
from aqt.operations import QueryOp
from aqt.operations.collection import redo, undo
from aqt.operations.deck import set_current_deck
from aqt.profiles import ProfileManager as ProfileManagerType
from aqt.qt import *
from aqt.qt import sip
from aqt.sync import sync_collection, sync_login
from aqt.taskman import TaskManager
from aqt.theme import Theme, theme_manager
from aqt.toolbar import BottomWebView, Toolbar, TopWebView
from aqt.undo import UndoActionsInfo
from aqt.utils import (
    HelpPage,
    KeyboardModifiersPressed,
    askUser,
    checkInvalidFilename,
    current_window,
    disallow_full_screen,
    getFile,
    getOnlyText,
    openHelp,
    openLink,
    restoreGeom,
    restoreState,
    saveGeom,
    saveState,
    showInfo,
    showWarning,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView, AnkiWebViewKind

install_pylib_legacy()

MainWindowState = Literal[
    "startup", "deckBrowser", "overview", "review", "resetRequired", "profileManager"
]


T = TypeVar("T")


class MainWebView(AnkiWebView):
    def __init__(self, mw: AnkiQt) -> None:
        AnkiWebView.__init__(self, kind=AnkiWebViewKind.MAIN)
        self.mw = mw
        self.setFocusPolicy(Qt.FocusPolicy.WheelFocus)
        self.setMinimumWidth(400)
        self.setAcceptDrops(True)

    # Importing files via drag & drop
    ##########################################################################

    def dragEnterEvent(self, event: QDragEnterEvent) -> None:
        if self.mw.state != "deckBrowser":
            return super().dragEnterEvent(event)
        mime = event.mimeData()
        if not mime.hasUrls():
            return
        for url in mime.urls():
            path = url.toLocalFile()
            if not os.path.exists(path) or os.path.isdir(path):
                return
        event.accept()

    def dropEvent(self, event: QDropEvent) -> None:
        import aqt.importing

        if self.mw.state != "deckBrowser":
            return super().dropEvent(event)
        mime = event.mimeData()
        paths = [url.toLocalFile() for url in mime.urls()]
        deck_paths = filter(lambda p: not p.endswith(".colpkg"), paths)
        for path in deck_paths:
            if not self.mw.pm.legacy_import_export():
                import_file(self.mw, path)
            else:
                aqt.importing.importFile(self.mw, path)

            # importing continues after the above call returns, so it is not
            # currently safe for us to import more than one file at once
            return

    # Main webview specific event handling
    def eventFilter(self, obj: QObject | None, evt: QEvent | None) -> bool:
        if handled := super().eventFilter(obj, evt):
            return handled

        if evt.type() == QEvent.Type.Leave:
            handled_leave = False

            # Show menubar when mouse moves outside main webview in fullscreen
            if self.mw.fullscreen:
                self.mw.show_menubar()
                handled_leave = True

            # Show toolbar when mouse moves outside main webview
            # and automatically hide it with delay after mouse has entered again
            # The toolbar's hide timer will also trigger menubar hiding when in fullscreen mode
            if self.mw.pm.hide_top_bar() or self.mw.pm.hide_bottom_bar():
                self.mw.toolbarWeb.show()
                self.mw.bottomWeb.show()
                handled_leave = True

            return handled_leave

        if evt.type() == QEvent.Type.Enter:
            self.mw.toolbarWeb.hide_timer.start()
            self.mw.bottomWeb.hide_timer.start()
            return True

        return False


class AnkiQt(QMainWindow):
    col: Collection
    pm: ProfileManagerType
    web: MainWebView
    bottomWeb: BottomWebView

    def __init__(
        self,
        app: aqt.AnkiApp,
        profileManager: ProfileManagerType,
        backend: _RustBackend,
        opts: Namespace,
        args: list[Any],
    ) -> None:
        QMainWindow.__init__(self)
        self.backend = backend
        self.state: MainWindowState = "startup"
        self.opts = opts
        self.col: Collection | None = None
        self.taskman = TaskManager(self)
        self.media_syncer = MediaSyncer(self)
        aqt.mw = self
        self.app = app
        self.pm = profileManager
        self.fullscreen = False
        # init rest of app
        self.safeMode = (
            bool(self.app.queryKeyboardModifiers() & Qt.KeyboardModifier.ShiftModifier)
            or self.opts.safemode
        )
        try:
            self.setupUI()
            self.setupAddons(args)
            self.finish_ui_setup()
        except Exception:
            showInfo(tr.qt_misc_error_during_startup(val=traceback.format_exc()))
            sys.exit(1)
        # must call this after ui set up
        if self.safeMode:
            tooltip(tr.qt_misc_shift_key_was_held_down_skipping())
        # were we given a file to import?
        if args and args[0] and not self._isAddon(args[0]):
            self.onAppMsg(args[0])
        # Load profile in a timer so we can let the window finish init and not
        # close on profile load error.
        if is_win:
            fn = self.setupProfileAfterWebviewsLoaded
        else:
            fn = self.setupProfile

        def on_window_init() -> None:
            fn()
            gui_hooks.main_window_did_init()

        self.progress.single_shot(10, on_window_init, False)

    def setupUI(self) -> None:
        self.col = None
        self.disable_automatic_garbage_collection()
        self.setupAppMsg()
        self.setupKeys()
        self.setupThreads()
        self.setupMediaServer()
        self.setupSpellCheck()
        self.setupProgress()
        self.setupStyle()
        self.setupMainWindow()
        self.setupSystemSpecific()
        self.setupMenus()
        self.setupErrorHandler()
        self.setupSignals()
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
        # add-ons are only available here after setupAddons
        gui_hooks.reviewer_did_init(self.reviewer)

    def setupProfileAfterWebviewsLoaded(self) -> None:
        for w in (self.web, self.bottomWeb):
            if not w._domDone:
                self.progress.single_shot(
                    10,
                    self.setupProfileAfterWebviewsLoaded,
                    False,
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

        self.pendingImport: str | None = None
        self.restoring_backup = False
        # - if a valid profile was provided on commandline, we load it
        # - if an invalid profile was provided, we skip this step and show the picker
        # - if no profile was provided, we use this step
        if not self.pm.name and not self.pm.invalid_profile_provided_on_commandline:
            profs = self.pm.profiles()
            name = self.pm.last_loaded_profile_name()
            if len(profs) == 1:
                self.pm.load(profs[0])
            elif name in profs:
                self.pm.load(name)

        if not self.pm.name:
            self.showProfileManager()
        else:
            self.loadProfile()

    def showProfileManager(self) -> None:
        self.pm.profile = None
        self.moveToState("profileManager")
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
        f.downgrade_button.setText(tr.profiles_downgrade_and_quit())
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
        except Exception:
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

    def onOpenProfile(self, *, callback: Callable[[], None] | None = None) -> None:
        def on_done() -> None:
            self.profileDiag.closeWithoutQuitting()
            if callback:
                callback()

        self.profileDiag.hide()
        # code flow is confusing here - if load fails, profile dialog
        # will be shown again
        self.loadProfile(on_done)

    def profileNameOk(self, name: str) -> bool:
        return not checkInvalidFilename(name) and name != "addons21"

    def onAddProfile(self) -> None:
        name = getOnlyText(tr.actions_name()).strip()
        if name:
            if name in self.pm.profiles():
                showWarning(tr.qt_misc_name_exists())
                return
            if not self.profileNameOk(name):
                return
            self.pm.create(name)
            self.pm.name = name
            self.refreshProfilesList()

    def onRenameProfile(self) -> None:
        name = getOnlyText(tr.actions_new_name(), default=self.pm.name).strip()
        if not name:
            return
        if name == self.pm.name:
            return
        if name in self.pm.profiles():
            showWarning(tr.qt_misc_name_exists())
            return
        if not self.profileNameOk(name):
            return
        self.pm.rename(name)
        self.refreshProfilesList()

    def onRemProfile(self) -> None:
        profs = self.pm.profiles()
        if len(profs) < 2:
            showWarning(tr.qt_misc_there_must_be_at_least_one())
            return
        # sure?
        if not askUser(
            tr.qt_misc_all_cards_notes_and_media_for2(name=self.pm.name),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            return
        self.pm.remove(self.pm.name)
        self.refreshProfilesList()

    def _handle_load_backup_success(self) -> None:
        """
        Actions that occur when profile backup has been loaded successfully
        """
        if self.state == "profileManager":
            self.profileDiag.closeWithoutQuitting()

        self.loadProfile()

    def _handle_load_backup_failure(self, error: Exception) -> None:
        """
        Actions that occur when a profile has loaded unsuccessfully
        """
        showWarning(str(error))
        if self.state != "profileManager":
            self.loadProfile()

    def onOpenBackup(self) -> None:
        def do_open(path: str) -> None:
            if not askUser(
                tr.qt_misc_replace_your_collection_with_an_earlier2(
                    os.path.basename(path)
                ),
                msgfunc=QMessageBox.warning,
                defaultno=True,
            ):
                return

            showInfo(tr.qt_misc_automatic_syncing_and_backups_have_been())

            # Collection is still loaded if called from main window, so we unload. This is already
            # unloaded if called from the ProfileManager window.
            if self.col:
                self.unloadProfile(lambda: self._start_restore_backup(path))
                return

            self._start_restore_backup(path)

        getFile(
            self.profileDiag if self.state == "profileManager" else self,
            tr.qt_misc_revert_to_backup(),
            cb=do_open,  # type: ignore
            filter="*.colpkg",
            dir=self.pm.backupFolder(),
        )

    def _start_restore_backup(self, path: str):
        self.restoring_backup = True

        import_collection_package_op(
            self, path, success=self._handle_load_backup_success
        ).failure(self._handle_load_backup_failure).run_in_background()

    def _on_downgrade(self) -> None:
        self.progress.start()
        profiles = self.pm.profiles()

        def downgrade() -> list[str]:
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

    def loadProfile(self, onsuccess: Callable | None = None) -> None:
        if not self.loadCollection():
            return

        self.setup_sound()
        self.flags = FlagManager(self)
        # show main window
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

        def _onsuccess(synced: bool) -> None:
            if synced:
                self._refresh_after_sync()
            if onsuccess:
                onsuccess()
            if not self.safeMode:
                self.maybe_check_for_addon_updates(self.setup_auto_update)

        last_day_cutoff = self.col.sched.day_cutoff

        def refresh_reviewer_on_day_rollover_change():
            from aqt.reviewer import RefreshNeeded

            # need to refresh?
            nonlocal last_day_cutoff
            current_cutoff = self.col.sched.day_cutoff
            if self.state == "review" and last_day_cutoff != current_cutoff:
                last_day_cutoff = self.col.sched.day_cutoff
                self.reviewer._refresh_needed = RefreshNeeded.QUEUES
                self.reviewer.refresh_if_needed()
            if last_day_cutoff != current_cutoff:
                gui_hooks.day_did_change()

            # schedule another check
            secs_until_cutoff = current_cutoff - int_time()
            self._reviewer_refresh_timer = self.progress.timer(
                secs_until_cutoff * 1000,
                refresh_reviewer_on_day_rollover_change,
                repeat=False,
                parent=self,
            )

        refresh_reviewer_on_day_rollover_change()
        gui_hooks.profile_did_open()
        self.maybe_auto_sync_on_open_close(_onsuccess)

    def unloadProfile(self, onsuccess: Callable) -> None:
        def callback() -> None:
            self._unloadProfile()
            onsuccess()

        gui_hooks.profile_will_close()
        self.unloadCollection(callback)

    def _unloadProfile(self) -> None:
        self.cleanup_sound()
        saveGeom(self, "mainWindow")
        saveState(self, "mainWindow")
        self.pm.save()
        self.hide()

        self.restoring_backup = False

        # at this point there should be no windows left
        self._checkForUnclosedWidgets()
        self._reviewer_refresh_timer.deleteLater()

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
        # Rust background jobs are not awaited implicitly
        self.backend.await_backup_completion()
        self.deleteLater()
        app = self.app
        app._unset_windows_shutdown_block_reason()

        def exit():
            # try to ensure Qt objects are deleted in a logical order,
            # to prevent crashes on shutdown
            gc.collect()
            app.exit(0)

        self.progress.single_shot(100, exit, False)

    # Sound/video
    ##########################################################################

    def setup_sound(self) -> None:
        aqt.sound.setup_audio(self.taskman, self.pm.base, self.col.media.dir())

    def cleanup_sound(self) -> None:
        aqt.sound.cleanup_audio()

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
                    f"{tr.errors_unable_open_collection()}\n{traceback.format_exc()}"
                )
            # clean up open collection if possible
            try:
                self.backend.close_collection(downgrade_to_schema11=False)
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
        except Exception:
            # dump error to stderr so it gets picked up by errors.py
            traceback.print_exc()

        return True

    def _loadCollection(self) -> None:
        cpath = self.pm.collectionPath()
        self.col = Collection(cpath, backend=self.backend)
        self.setEnabled(True)

    def reopen(self, after_full_sync: bool = False) -> None:
        self.col.reopen(after_full_sync=after_full_sync)
        gui_hooks.collection_did_temporarily_close(self.col)

    def unloadCollection(self, onsuccess: Callable) -> None:
        def after_media_sync() -> None:
            self._unloadCollection()
            onsuccess()

        def after_sync(synced: bool) -> None:
            self.media_syncer.show_diag_until_finished(after_media_sync)

        def before_sync() -> None:
            self.setEnabled(False)
            self.maybe_auto_sync_on_open_close(after_sync)

        self.closeAllWindows(before_sync)

    def _unloadCollection(self) -> None:
        if not self.col:
            return

        label = (
            tr.qt_misc_closing() if self.restoring_backup else tr.qt_misc_backing_up()
        )
        self.progress.start(label=label)

        corrupt = False

        try:
            self.maybeOptimize()
            if not dev_mode:
                corrupt = self.col.db.scalar("pragma quick_check") != "ok"
        except Exception:
            corrupt = True

        try:
            if not corrupt and not dev_mode and not self.restoring_backup:
                try:
                    # default 5 minute throttle
                    self.col.create_backup(
                        backup_folder=self.pm.backupFolder(),
                        force=False,
                        wait_for_completion=False,
                    )
                except Exception:
                    print("backup on close failed")
            self.col.close(downgrade=False)
        except Exception as e:
            print(e)
            corrupt = True
        finally:
            self.col = None
            self.progress.finish()

        if corrupt:
            showWarning(tr.qt_misc_your_collection_file_appears_to_be())

    def apply_collection_options(self) -> None:
        "Setup audio after collection loaded."
        aqt.sound.av_player.interrupt_current_audio = self.col.get_config_bool(
            Config.Bool.INTERRUPT_AUDIO_WHEN_ANSWERING
        )

    # Auto-optimize
    ##########################################################################

    def maybeOptimize(self) -> None:
        # have two weeks passed?
        if (last_optimize := self.pm.profile.get("lastOptimize")) is not None:
            if (int_time() - last_optimize) < 86400 * 14:
                return
        self.progress.start(label=tr.qt_misc_optimizing())
        self.col.optimize()
        self.pm.profile["lastOptimize"] = int_time()
        self.pm.save()
        self.progress.finish()

    # Tracking main window state (deck browser, reviewer, etc)
    ##########################################################################

    def moveToState(self, state: MainWindowState, *args: Any) -> None:
        # print("-> move from", self.state, "to", state)
        oldState = self.state
        cleanup = getattr(self, f"_{oldState}Cleanup", None)
        if cleanup:
            cleanup(state)
        self.clearStateShortcuts()
        self.state = state
        gui_hooks.state_will_change(state, oldState)
        getattr(self, f"_{state}State", lambda *_: None)(oldState, *args)
        if state != "resetRequired":
            self.bottomWeb.adjustHeightToFit()
        gui_hooks.state_did_change(state, oldState)

    def _deckBrowserState(self, oldState: MainWindowState) -> None:
        self.deckBrowser.show()

    def _selectedDeck(self) -> DeckDict | None:
        did = self.col.decks.selected()
        if not self.col.decks.name_if_exists(did):
            showInfo(tr.qt_misc_please_select_a_deck())
            return None
        return self.col.decks.get(did)

    def _overviewState(self, oldState: MainWindowState) -> None:
        if not self._selectedDeck():
            return self.moveToState("deckBrowser")
        self.overview.show()

    def _reviewState(self, oldState: MainWindowState) -> None:
        self.reviewer.show()

        fullscreen_was_checked = False

        if self.pm.hide_top_bar():
            self.toolbarWeb.hide_timer.setInterval(500)
            self.toolbarWeb.hide_timer.start()

            # check the `hide_if_allowed` method in `qt/aqt/toolbar.py`
            fullscreen_was_checked = True
        else:
            self.toolbarWeb.flatten()

        if not fullscreen_was_checked and self.fullscreen:
            self.hide_menubar()

        if self.pm.hide_bottom_bar():
            self.bottomWeb.hide_timer.setInterval(500)
            self.bottomWeb.hide_timer.start()

    def _reviewCleanup(self, newState: MainWindowState) -> None:
        if newState not in {"resetRequired", "review"}:
            self.reviewer.auto_advance_enabled = False
            self.reviewer.cleanup()
            self.toolbarWeb.elevate()
            self.toolbarWeb.show()
            self.bottomWeb.show()

    # Resetting state
    ##########################################################################

    def _increase_background_ops(self) -> None:
        if not self._background_op_count:
            gui_hooks.backend_will_block()
        self._background_op_count += 1

    def _decrease_background_ops(self) -> None:
        self._background_op_count -= 1
        if not self._background_op_count:
            gui_hooks.backend_did_block()
        if self._background_op_count < 0:
            raise Exception("no background ops active")

    def _synthesize_op_did_execute_from_reset(self) -> None:
        """Fire the `operation_did_execute` hook with everything marked as changed,
        after legacy code has called .reset()"""
        op = OpChanges()
        for field in op.DESCRIPTOR.fields:
            if field.name != "kind":
                setattr(op, field.name, True)
        gui_hooks.operation_did_execute(op, None)

    def on_operation_did_execute(
        self, changes: OpChanges, handler: object | None
    ) -> None:
        "Notify current screen of changes."
        focused = current_window() == self
        if self.state == "review":
            dirty = self.reviewer.op_executed(changes, handler, focused)
        elif self.state == "overview":
            dirty = self.overview.op_executed(changes, handler, focused)
        elif self.state == "deckBrowser":
            dirty = self.deckBrowser.op_executed(changes, handler, focused)
        else:
            dirty = False

        if not focused and dirty:
            self.fade_out_webview()

        if changes.mtime:
            self.toolbar.update_sync_status()

        if changes.notetype:
            self.col.models._clear_cache()

    def on_focus_did_change(
        self, new_focus: QWidget | None, _old: QWidget | None
    ) -> None:
        "If main window has received focus, ensure current UI state is updated."
        if new_focus and new_focus.window() == self:
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

        New code should use CollectionOp() instead."""
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
        reason: Any | None = None,
        context: Any | None = None,
    ) -> None:
        traceback.print_stack(file=sys.stdout)
        print("requireReset() is obsolete; please use CollectionOp()")
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
        key: str | None = None,
        class_: str = "",
        id: str = "",
        extra: str = "",
    ) -> str:
        class_ = f"but {class_}"
        if key:
            key = tr.actions_shortcut_key(val=key)
        else:
            key = ""
        return """
<button id="{}" class="{}" onclick="pycmd('{}');return false;"
title="{}" {}>{}</button>""".format(
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
        tweb = self.toolbarWeb = TopWebView(self)
        self.toolbar = Toolbar(self, tweb)
        # main area
        self.web = MainWebView(self)
        # bottom area
        sweb = self.bottomWeb = BottomWebView(self)
        sweb.setFocusPolicy(Qt.FocusPolicy.WheelFocus)
        sweb.disable_zoom()
        # add in a layout
        self.mainLayout = QVBoxLayout()
        self.mainLayout.setContentsMargins(0, 0, 0, 0)
        self.mainLayout.setSpacing(0)
        self.mainLayout.addWidget(tweb)
        self.mainLayout.addWidget(self.web)
        self.mainLayout.addWidget(sweb)
        self.form.centralwidget.setLayout(self.mainLayout)

        # force webengine processes to load before cwd is changed
        if is_win:
            for webview in self.web, self.bottomWeb:
                webview.force_load_hack()

        gui_hooks.card_review_webview_did_init(self.web, AnkiWebViewKind.MAIN)

    def closeAllWindows(self, onsuccess: Callable) -> None:
        aqt.dialogs.closeAll(onsuccess)

    # Components
    ##########################################################################

    def setupSignals(self) -> None:
        signal.signal(signal.SIGINT, self.onUnixSignal)
        signal.signal(signal.SIGTERM, self.onUnixSignal)

    def onUnixSignal(self, signum: Any, frame: Any) -> None:
        def quit() -> None:
            self.close()

        self.progress.single_shot(100, quit)

    def setupProgress(self) -> None:
        self.progress = aqt.progress.ProgressManager(self)

    def setupErrorHandler(self) -> None:
        import aqt.errors

        self.errorHandler = aqt.errors.ErrorHandler(self)

    def setupAddons(self, args: list | None) -> None:
        import aqt.addons

        self.addonManager = aqt.addons.AddonManager(self)

        if args and args[0] and self._isAddon(args[0]):
            self.installAddon(args[0], startup=True)

        if not self.safeMode:
            self.addonManager.loadAddons()

    def maybe_check_for_addon_updates(
        self, on_done: Callable[[list[DownloadLogEntry]], None] | None = None
    ) -> None:
        last_check = self.pm.last_addon_update_check()
        elap = int_time() - last_check

        if elap > 86_400 or self.pm.last_run_version != int_version():
            self.check_for_addon_updates(by_user=False, on_done=on_done)
        elif on_done:
            on_done([])

    def check_for_addon_updates(
        self,
        by_user: bool,
        on_done: Callable[[list[DownloadLogEntry]], None] | None = None,
    ) -> None:
        def wrap_on_updates_installed(log: list[DownloadLogEntry]) -> None:
            self.on_updates_installed(log)
            self.pm.set_last_addon_update_check(int_time())
            if on_done:
                on_done(log)

        check_and_prompt_for_updates(
            self,
            self.addonManager,
            wrap_on_updates_installed,
            requested_by_user=by_user,
        )

    def on_updates_installed(self, log: list[DownloadLogEntry]) -> None:
        if log:
            show_log_to_user(self, log)

    def setupSpellCheck(self) -> None:
        os.environ["QTWEBENGINE_DICTIONARIES_PATH"] = os.path.join(
            self.pm.base, "dictionaries"
        )

    def setupThreads(self) -> None:
        self._mainThread = QThread.currentThread()
        self._background_op_count = 0

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
        self.flags.require_refresh()

    def _sync_collection_and_media(self, after_sync: Callable[[], None]) -> None:
        "Caller should ensure auth available."

        def on_collection_sync_finished() -> None:
            self.col.models._clear_cache()
            gui_hooks.sync_did_finish()
            self.reset()

            after_sync()

        gui_hooks.sync_will_start()
        sync_collection(self, on_done=on_collection_sync_finished)

    def maybe_auto_sync_on_open_close(self, after_sync: Callable[[bool], None]) -> None:
        "If disabled, after_sync() is called immediately."
        if self.can_auto_sync():
            self._sync_collection_and_media(lambda: after_sync(True))
        else:
            after_sync(False)

    def can_auto_sync(self) -> bool:
        "True if syncing on startup/shutdown enabled."
        return self._can_sync_unattended() and self.pm.auto_syncing_enabled()

    def _can_sync_unattended(self) -> bool:
        return (
            bool(self.pm.sync_auth())
            and not self.safeMode
            and not self.restoring_backup
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
            self.setWindowState(self.windowState() & ~Qt.WindowState.WindowMinimized)  # type: ignore
        return True

    def setupStyle(self) -> None:
        theme_manager.apply_style()
        if is_lin:
            # On Linux, the check requires invoking an external binary,
            # and can potentially produce verbose logs on systems where
            # the preferred theme cannot be determined,
            # which we don't want to be doing frequently
            interval_secs = 300
        else:
            interval_secs = 2
        self.progress.timer(
            interval_secs * 1000,
            theme_manager.apply_style,
            True,
            False,
            parent=self,
        )

    def set_theme(self, theme: Theme) -> None:
        self.pm.set_theme(theme)
        self.setupStyle()

    # Key handling
    ##########################################################################

    def setupKeys(self) -> None:
        globalShortcuts = [
            ("Ctrl+:", show_debug_console),
            ("d", lambda: self.moveToState("deckBrowser")),
            ("s", self.onStudyKey),
            ("a", self.onAddCard),
            ("b", self.onBrowse),
            ("t", self.onStats),
            ("Shift+t", self.onStats),
            ("y", self.on_sync_button_clicked),
        ]
        self.applyShortcuts(globalShortcuts)
        self.stateShortcuts: list[QShortcut] = []

    def _normalize_shortcuts(
        self, shortcuts: Sequence[tuple[str, Callable]]
    ) -> Sequence[tuple[QKeySequence, Callable]]:
        """
        Remove duplicate shortcuts (possibly added by add-ons)
        by normalizing them and filtering through a dictionary.
        The last duplicate shortcut wins, so add-ons will override
        standard shortcuts if they append to the shortcut list.
        """
        return tuple({QKeySequence(key): fn for key, fn in shortcuts}.items())

    def applyShortcuts(
        self, shortcuts: Sequence[tuple[str, Callable]]
    ) -> list[QShortcut]:
        qshortcuts = []
        for key, fn in self._normalize_shortcuts(shortcuts):
            scut = QShortcut(key, self, activated=fn)  # type: ignore
            scut.setAutoRepeat(False)
            qshortcuts.append(scut)
        return qshortcuts

    def setStateShortcuts(self, shortcuts: list[tuple[str, Callable]]) -> None:
        gui_hooks.state_shortcuts_will_change(self.state, shortcuts)
        # legacy hook
        runHook(f"{self.state}StateShortcuts", shortcuts)
        self.stateShortcuts = self.applyShortcuts(shortcuts)

    def clearStateShortcuts(self) -> None:
        for qs in self.stateShortcuts:
            sip.delete(qs)  # type: ignore
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

    def undo(self) -> None:
        "Call operations/collection.py:undo() directly instead."
        undo(parent=self)

    def redo(self) -> None:
        "Call operations/collection.py:redo() directly instead."
        redo(parent=self)

    def undo_actions_info(self) -> UndoActionsInfo:
        "Info about the current undo/redo state for updating menus."
        status = self.col.undo_status() if self.col else UndoStatus()
        return UndoActionsInfo.from_undo_status(status)

    def update_undo_actions(self) -> None:
        """Tell the UI to redraw the undo/redo menu actions based on the current state.

        Usually you do not need to call this directly; it is called when a
        CollectionOp is run, and will be called when the legacy .reset() or
        .checkpoint() methods are used."""
        info = self.undo_actions_info()
        self.form.actionUndo.setText(info.undo_text)
        self.form.actionUndo.setEnabled(info.can_undo)
        self.form.actionRedo.setText(info.redo_text)
        self.form.actionRedo.setEnabled(info.can_redo)
        self.form.actionRedo.setVisible(info.show_redo)
        gui_hooks.undo_state_did_change(info)

    @deprecated(info="checkpoints are no longer supported")
    def checkpoint(self, name: str) -> None:
        pass

    @deprecated(info="saving is automatic")
    def autosave(self) -> None:
        pass

    onUndo = undo

    # Other menu operations
    ##########################################################################

    def onAddCard(self) -> None:
        aqt.dialogs.open("AddCards", self)

    def onBrowse(self) -> None:
        aqt.dialogs.open("Browser", self, card=self.reviewer.card)

    def onEditCurrent(self) -> None:
        aqt.dialogs.open("EditCurrent", self)

    def onOverview(self) -> None:
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

    def on_upgrade_downgrade(self) -> None:
        if not askUser(tr.qt_misc_open_anki_launcher()):
            return

        from aqt.package import update_and_restart

        update_and_restart()

    def onNoteTypes(self) -> None:
        import aqt.models

        aqt.models.Models(self, self, fromMain=True)

    def onAbout(self) -> None:
        aqt.dialogs.open("About", self)

    def onDonate(self) -> None:
        openLink(aqt.appDonate)

    def onDocumentation(self) -> None:
        openHelp(HelpPage.INDEX)

    # legacy

    def onDeckConf(self, deck: DeckDict | None = None) -> None:
        pass

    # Importing & exporting
    ##########################################################################

    def handleImport(self, path: str) -> None:
        "Importing triggered via file double-click, or dragging file onto Anki icon."
        import aqt.importing

        if not os.path.exists(path):
            # there were instances in the distant past where the received filename was not
            # valid (encoding issues?), so this was added to direct users to try
            # file>import instead.
            showInfo(f"{tr.qt_misc_please_use_fileimport_to_import_this()} ({path})")
            return None

        if not self.pm.legacy_import_export():
            import_file(self, path)
        else:
            aqt.importing.importFile(self, path)

    def onImport(self) -> None:
        "Importing triggered via File>Import."
        import aqt.importing

        if not self.pm.legacy_import_export():
            prompt_for_file_then_import(self)
        else:
            aqt.importing.onImport(self)

    def onExport(self, did: DeckId | None = None) -> None:
        import aqt.exporting

        if not self.pm.legacy_import_export():
            ExportDialog(self, did=did)
        else:
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
            force_enable=True,
        )

    # Cramming
    ##########################################################################

    def onCram(self) -> None:
        aqt.dialogs.open("FilteredDeckConfigDialog", self)

    # Menu, title bar & status
    ##########################################################################

    def setupMenus(self) -> None:
        from aqt.package import launcher_executable

        m = self.form

        # File
        qconnect(
            m.actionSwitchProfile.triggered, self.unloadProfileAndShowProfileManager
        )
        qconnect(m.actionImport.triggered, self.onImport)
        qconnect(m.actionExport.triggered, self.onExport)
        qconnect(m.action_create_backup.triggered, self.on_create_backup_now)
        qconnect(m.action_open_backup.triggered, self.onOpenBackup)
        qconnect(m.actionExit.triggered, self.close)

        # Help
        qconnect(m.actionDocumentation.triggered, self.onDocumentation)
        qconnect(m.actionDonate.triggered, self.onDonate)
        qconnect(m.actionAbout.triggered, self.onAbout)
        m.actionAbout.setText(tr.qt_accel_about_mac())

        # Edit
        qconnect(m.actionUndo.triggered, self.undo)
        qconnect(m.actionRedo.triggered, self.redo)

        # Tools
        qconnect(m.actionFullDatabaseCheck.triggered, self.onCheckDB)
        qconnect(m.actionCheckMediaDatabase.triggered, self.on_check_media_db)
        qconnect(m.actionStudyDeck.triggered, self.onStudyDeck)
        qconnect(m.actionCreateFiltered.triggered, self.onCram)
        qconnect(m.actionEmptyCards.triggered, self.onEmptyCards)
        qconnect(m.actionNoteTypes.triggered, self.onNoteTypes)
        qconnect(m.action_upgrade_downgrade.triggered, self.on_upgrade_downgrade)
        if not launcher_executable():
            m.action_upgrade_downgrade.setVisible(False)
        qconnect(m.actionPreferences.triggered, self.onPrefs)

        # View
        qconnect(
            m.actionZoomIn.triggered,
            lambda: self.web.setZoomFactor(self.web.zoomFactor() + 0.1),
        )
        qconnect(
            m.actionZoomOut.triggered,
            lambda: self.web.setZoomFactor(self.web.zoomFactor() - 0.1),
        )
        qconnect(m.actionResetZoom.triggered, lambda: self.web.setZoomFactor(1))
        # app-wide shortcut
        qconnect(m.actionFullScreen.triggered, self.on_toggle_full_screen)
        m.actionFullScreen.setShortcut(
            QKeySequence("F11") if is_lin else QKeySequence.StandardKey.FullScreen
        )
        m.actionFullScreen.setShortcutContext(Qt.ShortcutContext.ApplicationShortcut)

    def updateTitleBar(self) -> None:
        self.setWindowTitle("Anki")

    # View
    ##########################################################################

    def on_toggle_full_screen(self) -> None:
        if disallow_full_screen():
            showWarning(
                tr.actions_fullscreen_unsupported(),
                parent=self,
                help=HelpPage.FULL_SCREEN_ISSUE,
            )
            return
        else:
            window = self.app.activeWindow()
            window.setWindowState(
                window.windowState() ^ Qt.WindowState.WindowFullScreen
            )

        # Hide Menubar on Windows and Linux
        if window.windowState() & Qt.WindowState.WindowFullScreen and not is_mac:
            self.fullscreen = True
            self.hide_menubar()
        else:
            self.fullscreen = False
            self.show_menubar()

        # Update Toolbar states
        self.toolbarWeb.hide_if_allowed()
        self.bottomWeb.hide_if_allowed()

    def hide_menubar(self) -> None:
        self.form.menubar.setFixedHeight(0)

    def show_menubar(self) -> None:
        self.form.menubar.setMaximumSize(QWIDGETSIZE_MAX, QWIDGETSIZE_MAX)
        self.form.menubar.setMinimumSize(0, 0)

    # Auto update
    ##########################################################################

    def setup_auto_update(self, _log: list[DownloadLogEntry]) -> None:
        from aqt.update import check_for_update

        if aqt.mw.pm.check_for_updates():
            check_for_update()

    # Timers
    ##########################################################################

    def setup_timers(self) -> None:
        # refresh decks every 10 minutes
        self.progress.timer(10 * 60 * 1000, self.onRefreshTimer, True, parent=self)
        # check media sync every 5 minutes
        self.progress.timer(
            5 * 60 * 1000, self.on_periodic_sync_timer, True, parent=self
        )
        # periodic garbage collection
        self.progress.timer(
            15 * 60 * 1000, self.garbage_collect_now, True, False, parent=self
        )
        # ensure Python interpreter runs at least once per second, so that
        # SIGINT/SIGTERM is processed without a long delay
        self.progress.timer(1000, lambda: None, True, False, parent=self)
        # periodic backups are checked every 5 minutes
        self.progress.timer(
            5 * 60 * 1000,
            self.on_periodic_backup_timer,
            True,
            parent=self,
        )

    def onRefreshTimer(self) -> None:
        if self.state == "deckBrowser":
            self.deckBrowser.refresh()
        elif self.state == "overview":
            self.overview.refresh()

    def on_periodic_sync_timer(self) -> None:
        elap = self.media_syncer.seconds_since_last_sync()
        minutes = self.pm.periodic_sync_media_minutes()
        if not minutes:
            return
        if elap > minutes * 60:
            if not self._can_sync_unattended():
                return
            # media_syncer takes care of media syncing preference check
            self.media_syncer.start(True)

    # Backups
    ##########################################################################

    def on_periodic_backup_timer(self) -> None:
        """Create a backup if enough time has elapsed and collection changed."""
        self._create_backup_with_progress(user_initiated=False)

    def on_create_backup_now(self) -> None:
        self._create_backup_with_progress(user_initiated=True)

    def create_backup_now(self) -> None:
        """Create a backup immediately, regardless of when the last one was created.
        Waits until the backup completes. Intended to be used as part of a longer-running
        CollectionOp/QueryOp."""
        self.col.create_backup(
            backup_folder=self.pm.backupFolder(),
            force=True,
            wait_for_completion=True,
        )

    def _create_backup_with_progress(self, user_initiated: bool) -> None:
        # The initial copy will display a progress window if it takes too long
        def backup(col: Collection) -> bool:
            return col.create_backup(
                backup_folder=self.pm.backupFolder(),
                force=user_initiated,
                wait_for_completion=False,
            )

        def on_success(val: None) -> None:
            if user_initiated:
                tooltip(tr.profiles_backup_created(), parent=self)

        def on_failure(exc: Exception) -> None:
            showWarning(
                tr.profiles_backup_creation_failed(reason=str(exc)), parent=self
            )

        def after_backup_started(created: bool) -> None:
            self.update_undo_actions()

            if user_initiated and not created:
                tooltip(tr.profiles_backup_unchanged(), parent=self)
                return

            # We await backup completion to confirm it was successful, but this step
            # does not block collection access, so we don't need to show the progress
            # window anymore.
            QueryOp(
                parent=self,
                op=lambda col: col.await_backup_completion(),
                success=on_success,
            ).failure(on_failure).without_collection().run_in_background()

        QueryOp(parent=self, op=backup, success=after_backup_started).failure(
            on_failure
        ).with_progress(tr.profiles_creating_backup()).run_in_background()

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

        self._activeWindowOnPlay: QWidget | None = None

    def onOdueInvalid(self) -> None:
        showWarning(tr.qt_misc_invalid_property_found_on_card_please())

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

    def onRemNotes(self, col: Collection, nids: Sequence[NoteId]) -> None:
        path = os.path.join(self.pm.profileFolder(), "deleted.txt")
        existed = os.path.exists(path)
        with open(path, "ab") as f:
            if not existed:
                f.write(b"nid\tmid\tfields\n")
            for id, mid, flds in col.db.execute(
                f"select id, mid, flds from notes where id in {ids2str(nids)}"
            ):
                fields = split_fields(flds)
                f.write(("\t".join([str(id), str(mid)] + fields)).encode("utf8"))
                f.write(b"\n")

    # Schema modifications
    ##########################################################################

    # this will gradually be phased out
    def onSchemaMod(self, arg: bool) -> bool:
        if not self.inMainThread():
            raise Exception("not in main thread")
        progress_shown = self.progress.busy()
        if progress_shown:
            self.progress.finish()
        ret = askUser(tr.qt_misc_the_requested_change_will_require_a())
        if progress_shown:
            self.progress.start()
        return ret

    # in favour of this
    def confirm_schema_modification(self) -> bool:
        """If schema unmodified, ask user to confirm change.
        True if confirmed or already modified."""
        if self.col.schema_changed():
            return True
        return askUser(tr.qt_misc_the_requested_change_will_require_a())

    # Advanced features
    ##########################################################################

    def onCheckDB(self) -> None:
        check_db(self)

    def on_check_media_db(self) -> None:
        gui_hooks.media_check_will_start()
        check_media_db(self)

    def onStudyDeck(self) -> None:
        from aqt.studydeck import StudyDeck

        def callback(ret: StudyDeck) -> None:
            if not ret.name:
                return
            deck_id = self.col.decks.id(ret.name)
            set_current_deck(parent=self, deck_id=deck_id).success(
                lambda out: self.moveToState("overview")
            ).run_in_background()

        StudyDeck(
            self,
            parent=self,
            dyn=True,
            current=self.col.decks.current()["name"],
            callback=callback,
        )

    def onEmptyCards(self) -> None:
        show_empty_cards(self)

    # System specific code
    ##########################################################################

    def setupSystemSpecific(self) -> None:
        self.hideMenuAccels = False
        if is_mac:
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+M", self)
            qconnect(self.minimizeShortcut.activated, self.onMacMinimize)
            self.hideMenuAccels = True
            self.maybeHideAccelerators()
            self.hideStatusTips()
        elif is_win:
            self._setupWin32()

    def _setupWin32(self):
        """Fix taskbar display/pinning"""
        if sys.platform != "win32":
            return

        launcher_path = os.environ.get("ANKI_LAUNCHER")
        if not launcher_path:
            return

        from win32com.propsys import propsys, pscon
        from win32com.propsys.propsys import PROPVARIANTType

        hwnd = int(self.winId())
        prop_store = propsys.SHGetPropertyStoreForWindow(hwnd)  # type: ignore[call-arg]
        prop_store.SetValue(
            pscon.PKEY_AppUserModel_ID, PROPVARIANTType("Ankitects.Anki")
        )
        prop_store.SetValue(
            pscon.PKEY_AppUserModel_RelaunchCommand,
            PROPVARIANTType(f'"{launcher_path}"'),
        )
        prop_store.SetValue(
            pscon.PKEY_AppUserModel_RelaunchDisplayNameResource, PROPVARIANTType("Anki")
        )
        prop_store.SetValue(
            pscon.PKEY_AppUserModel_RelaunchIconResource,
            PROPVARIANTType(f"{launcher_path},0"),
        )
        prop_store.Commit()

    def maybeHideAccelerators(self, tgt: Any | None = None) -> None:
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
            # On Windows, this next line gives a 'redundant cast' error after moving to
            # PyQt6.5.2.
            cast(QAction, action).setStatusTip("")  # type: ignore

    def onMacMinimize(self) -> None:
        self.setWindowState(self.windowState() | Qt.WindowState.WindowMinimized)  # type: ignore

    # Single instance support
    ##########################################################################

    def setupAppMsg(self) -> None:
        qconnect(self.app.appMsg, self.onAppMsg)

    def onAppMsg(self, buf: str) -> None:
        is_addon = self._isAddon(buf)

        if self.state == "startup":
            # try again in a second
            self.progress.single_shot(
                1000,
                lambda: self.onAppMsg(buf),
                False,
            )
            return
        elif self.state == "profileManager":
            # can't raise window while in profile manager
            if buf == "raise":
                return None
            self.pendingImport = buf
            if is_addon:
                msg = tr.qt_misc_addon_will_be_installed_when_a()
            else:
                msg = tr.qt_misc_deck_will_be_imported_when_a()
            tooltip(msg)
            return
        if not self.interactiveState() or self.progress.busy():
            # we can't raise the main window while in profile dialog, syncing, etc
            if buf != "raise":
                showInfo(
                    tr.qt_misc_please_ensure_a_profile_is_open(),
                    parent=None,
                )
            return None
        # raise window
        if is_win:
            # on windows we can raise the window by minimizing and restoring
            self.showMinimized()
            self.setWindowState(Qt.WindowState.WindowActive)
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
        # only accept primary extension here to avoid conflicts with deck packages
        return buf.endswith(self.addonManager.exts[0])

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
        self.progress.single_shot(1000, self.garbage_collect_now, False)

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
