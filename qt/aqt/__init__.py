# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import logging
import sys
from collections.abc import Callable
from typing import TYPE_CHECKING, Any, Union, cast

try:
    import pip_system_certs.wrapt_requests
except ModuleNotFoundError:
    print(
        "Python module pip_system_certs is not installed. System certificate store and custom SSL certificates may not work. See: https://github.com/ankitects/anki/issues/3016"
    )

if sys.version_info[0] < 3 or sys.version_info[1] < 9:
    raise Exception("Anki requires Python 3.9+")

# ensure unicode filenames are supported
try:
    "テスト".encode(sys.getfilesystemencoding())
except UnicodeEncodeError as exc:
    print("Anki requires a UTF-8 locale.")
    print("Please Google 'how to change locale on [your Linux distro]'")
    sys.exit(1)

# if sync server enabled, bypass the rest of the startup
if "--syncserver" in sys.argv:
    from anki.syncserver import run_sync_server
    from anki.utils import is_mac

    from .package import _fix_protobuf_path

    if is_mac and getattr(sys, "frozen", False):
        _fix_protobuf_path()

    # does not return
    run_sync_server()

from .package import packaged_build_setup

packaged_build_setup()

import argparse
import builtins
import cProfile
import getpass
import locale
import os
import tempfile
import traceback
from pathlib import Path

import anki.lang
from anki._backend import RustBackend
from anki.buildinfo import version as _version
from anki.collection import Collection
from anki.consts import HELP_SITE
from anki.utils import checksum, is_lin, is_mac
from aqt import gui_hooks
from aqt.log import setup_logging
from aqt.qt import *
from aqt.qt import sip
from aqt.utils import TR, tr

if TYPE_CHECKING:
    import aqt.profiles

# compat aliases
anki.version = _version  # type: ignore
anki.Collection = Collection  # type: ignore

# we want to be able to print unicode debug info to console without
# fear of a traceback on systems with the console set to ASCII
try:
    sys.stdout.reconfigure(encoding="utf-8")  # type: ignore
    sys.stderr.reconfigure(encoding="utf-8")  # type: ignore
except AttributeError:
    if is_win:
        # On Windows without console; add a mock writer. The stderr
        # writer will be overwritten when ErrorHandler is initialized.
        sys.stderr = sys.stdout = open(os.devnull, "w", encoding="utf8")

appVersion = _version
appWebsite = "https://apps.ankiweb.net/"
appWebsiteDownloadSection = "https://apps.ankiweb.net/#download"
appDonate = "https://apps.ankiweb.net/support/"
appShared = "https://ankiweb.net/shared/"
appUpdate = "https://ankiweb.net/update/desktop"
appHelpSite = HELP_SITE

from aqt.main import AnkiQt  # isort:skip
from aqt.profiles import ProfileManager, VideoDriver  # isort:skip

profiler: cProfile.Profile | None = None
mw: AnkiQt = None  # type: ignore # set on init

import aqt.forms

# Dialog manager
##########################################################################
# ensures only one copy of the window is open at once, and provides
# a way for dialogs to clean up asynchronously when collection closes

# to integrate a new window:
# - add it to _dialogs
# - define close behaviour, by either:
# -- setting silentlyClose=True to have it close immediately
# -- define a closeWithCallback() method
# - have the window opened via aqt.dialogs.open(<name>, self)
# - have a method reopen(*args), called if the user ask to open the window a second time. Arguments passed are the same than for original opening.

# - make preferences modal? cmd+q does wrong thing


from aqt import addcards, addons, browser, editcurrent, filtered_deck  # isort:skip
from aqt import stats, about, preferences, mediasync  # isort:skip


class DialogManager:
    _dialogs: dict[str, list] = {
        "AddCards": [addcards.AddCards, None],
        "AddonsDialog": [addons.AddonsDialog, None],
        "Browser": [browser.Browser, None],
        "EditCurrent": [editcurrent.EditCurrent, None],
        "FilteredDeckConfigDialog": [filtered_deck.FilteredDeckConfigDialog, None],
        "DeckStats": [stats.DeckStats, None],
        "NewDeckStats": [stats.NewDeckStats, None],
        "About": [about.show, None],
        "Preferences": [preferences.Preferences, None],
        "sync_log": [mediasync.MediaSyncDialog, None],
    }

    def open(self, name: str, *args: Any, **kwargs: Any) -> Any:
        (creator, instance) = self._dialogs[name]
        if instance:
            if instance.windowState() & Qt.WindowState.WindowMinimized:
                instance.setWindowState(
                    instance.windowState() & ~Qt.WindowState.WindowMinimized
                )
            instance.activateWindow()
            instance.raise_()
            if hasattr(instance, "reopen"):
                instance.reopen(*args, **kwargs)
        else:
            instance = creator(*args, **kwargs)
            self._dialogs[name][1] = instance
        gui_hooks.dialog_manager_did_open_dialog(self, name, instance)
        return instance

    def markClosed(self, name: str) -> None:
        self._dialogs[name] = [self._dialogs[name][0], None]

    def allClosed(self) -> bool:
        return not any(x[1] for x in self._dialogs.values())

    def closeAll(self, onsuccess: Callable[[], None]) -> bool | None:
        # can we close immediately?
        if self.allClosed():
            onsuccess()
            return None

        # ask all windows to close and await a reply
        for name, (creator, instance) in self._dialogs.items():
            if not instance:
                continue

            def callback() -> None:
                if self.allClosed():
                    onsuccess()
                else:
                    # still waiting for others to close
                    pass

            if getattr(instance, "silentlyClose", False):
                instance.close()
                callback()
            else:
                instance.closeWithCallback(callback)

        return True

    def register_dialog(
        self, name: str, creator: Callable | type, instance: Any | None = None
    ) -> None:
        """Allows add-ons to register a custom dialog to be managed by Anki's dialog
        manager, which ensures that only one copy of the window is open at once,
        and that the dialog cleans up asynchronously when the collection closes

        Please note that dialogs added in this manner need to define a close behavior
        by either:

            - setting `dialog.silentlyClose = True` to have it close immediately
            - define a `dialog.closeWithCallback()` method that is called when closed
              by the dialog manager

        TODO?: Implement more restrictive type check to ensure these requirements
        are met

        Arguments:
            name {str} -- Name/identifier of the dialog in question
            creator {Union[Callable, type]} -- A class or function to create new
                                               dialog instances with

        Keyword Arguments:
            instance {Optional[Any]} -- An optional existing instance of the dialog
                                        (default: {None})
        """
        self._dialogs[name] = [creator, instance]


dialogs = DialogManager()

# Language handling
##########################################################################
# Qt requires its translator to be installed before any GUI widgets are
# loaded, and we need the Qt language to match the i18n language or
# translated shortcuts will not work.

# A reference to the Qt translator needs to be held to prevent it from
# being immediately deallocated.
_qtrans: QTranslator | None = None


def setupLangAndBackend(
    pm: ProfileManager,
    app: QApplication,
    force: str | None = None,
    firstTime: bool = False,
) -> RustBackend:
    global _qtrans
    try:
        locale.setlocale(locale.LC_ALL, "")
    except Exception:
        pass

    # add _ and ngettext globals used by legacy code
    def fn__(arg) -> None:  # type: ignore
        print("".join(traceback.format_stack()[-2]))
        print("_ global will break in the future; please see anki/lang.py")
        return arg

    def fn_ngettext(a, b, c) -> None:  # type: ignore
        print("".join(traceback.format_stack()[-2]))
        print("ngettext global will break in the future; please see anki/lang.py")
        return b

    builtins.__dict__["_"] = fn__
    builtins.__dict__["ngettext"] = fn_ngettext

    # get lang and normalize into ja/zh-CN form
    if firstTime:
        lang = pm.meta["defaultLang"]
    else:
        lang = force or pm.meta["defaultLang"]
    lang = anki.lang.lang_to_disk_lang(lang)

    # set active language
    anki.lang.set_lang(lang)

    # switch direction for RTL languages
    if anki.lang.is_rtl(lang):
        app.setLayoutDirection(Qt.LayoutDirection.RightToLeft)
    else:
        app.setLayoutDirection(Qt.LayoutDirection.LeftToRight)

    # load qt translations
    _qtrans = QTranslator()

    if is_mac and getattr(sys, "frozen", False):
        qt_dir = os.path.join(sys.prefix, "../Resources/qt_translations")
    else:
        if qtmajor == 5:
            qt_dir = QLibraryInfo.location(QLibraryInfo.TranslationsPath)  # type: ignore
        else:
            qt_dir = QLibraryInfo.path(QLibraryInfo.LibraryPath.TranslationsPath)
    qt_lang = lang.replace("-", "_")
    if _qtrans.load(f"qtbase_{qt_lang}", qt_dir):
        app.installTranslator(_qtrans)

    backend = anki.lang.current_i18n
    assert backend is not None

    return backend


# App initialisation
##########################################################################


class NativeEventFilter(QAbstractNativeEventFilter):
    def nativeEventFilter(
        self, eventType: Any, message: Any
    ) -> tuple[bool, sip.voidptr | None]:

        if eventType == "windows_generic_MSG":
            import ctypes.wintypes

            msg = ctypes.wintypes.MSG.from_address(int(message))
            if msg.message == 17:  # WM_QUERYENDSESSION
                assert mw is not None
                if mw.can_auto_sync():
                    mw.app._set_windows_shutdown_block_reason(tr.sync_syncing())
                    mw.progress.single_shot(100, mw.unloadProfileAndExit)
                    return (True, 0)
        return (False, 0)


class AnkiApp(QApplication):
    # Single instance support on Win32/Linux
    ##################################################

    appMsg = pyqtSignal(str)

    KEY = f"anki{checksum(getpass.getuser())}"
    TMOUT = 30000

    def __init__(self, argv: list[str]) -> None:
        QApplication.__init__(self, argv)
        self.installEventFilter(self)
        self._argv = argv
        self._native_event_filter = NativeEventFilter()
        if is_win:
            self.installNativeEventFilter(self._native_event_filter)

    def _set_windows_shutdown_block_reason(self, reason: str) -> None:
        if is_win:
            import ctypes
            from ctypes import windll, wintypes  # type: ignore

            assert mw is not None
            windll.user32.ShutdownBlockReasonCreate(
                wintypes.HWND.from_param(int(mw.effectiveWinId())),
                ctypes.c_wchar_p(reason),
            )

    def _unset_windows_shutdown_block_reason(self) -> None:
        if is_win:
            from ctypes import windll, wintypes  # type: ignore

            assert mw is not None
            windll.user32.ShutdownBlockReasonDestroy(
                wintypes.HWND.from_param(int(mw.effectiveWinId())),
            )

    def secondInstance(self) -> bool:
        # we accept only one command line argument. if it's missing, send
        # a blank screen to just raise the existing window
        opts, args = parseArgs(self._argv)
        buf = "raise"
        if args and args[0]:
            buf = os.path.abspath(args[0])
        if self.sendMsg(buf):
            print("Already running; reusing existing instance.")
            return True
        else:
            # send failed, so we're the first instance or the
            # previous instance died
            QLocalServer.removeServer(self.KEY)
            self._srv = QLocalServer(self)
            qconnect(self._srv.newConnection, self.onRecv)
            self._srv.listen(self.KEY)
            return False

    def sendMsg(self, txt: str) -> bool:
        sock = QLocalSocket(self)
        sock.connectToServer(self.KEY, QIODevice.OpenModeFlag.WriteOnly)
        if not sock.waitForConnected(self.TMOUT):
            # first instance or previous instance dead
            return False
        sock.write(txt.encode("utf8"))
        if not sock.waitForBytesWritten(self.TMOUT):
            # existing instance running but hung
            QMessageBox.warning(
                None,
                tr.qt_misc_anki_is_running(),
                tr.qt_misc_if_instance_is_not_responding(),
            )

            sys.exit(1)
        sock.disconnectFromServer()
        return True

    def onRecv(self) -> None:
        sock = self._srv.nextPendingConnection()
        if not sock.waitForReadyRead(self.TMOUT):
            sys.stderr.write(sock.errorString())
            return
        path = bytes(cast(bytes, sock.readAll())).decode("utf8")
        self.appMsg.emit(path)  # type: ignore
        sock.disconnectFromServer()

    # OS X file/url handler
    ##################################################

    def event(self, evt: QEvent | None) -> bool:
        assert evt is not None

        if evt.type() == QEvent.Type.FileOpen:
            self.appMsg.emit(evt.file() or "raise")  # type: ignore
            return True
        return QApplication.event(self, evt)

    # Global cursor: pointer for Qt buttons
    ##################################################

    def eventFilter(self, src: Any, evt: QEvent | None) -> bool:
        assert evt is not None

        pointer_classes = (
            QPushButton,
            QCheckBox,
            QRadioButton,
            QMenu,
            QSlider,
            # classes with PyQt5 compatibility proxy
            without_qt5_compat_wrapper(QToolButton),
            without_qt5_compat_wrapper(QTabBar),
        )
        if evt.type() in [QEvent.Type.Enter, QEvent.Type.HoverEnter]:
            if (isinstance(src, pointer_classes) and src.isEnabled()) or (
                isinstance(src, without_qt5_compat_wrapper(QComboBox))
                and not src.isEditable()
            ):
                self.setOverrideCursor(QCursor(Qt.CursorShape.PointingHandCursor))
            else:
                self.restoreOverrideCursor()
            return False

        elif evt.type() in [QEvent.Type.HoverLeave, QEvent.Type.Leave] or isinstance(
            evt, QCloseEvent
        ):
            self.restoreOverrideCursor()
            return False

        return False


def parseArgs(argv: list[str]) -> tuple[argparse.Namespace, list[str]]:
    "Returns (opts, args)."
    # py2app fails to strip this in some instances, then anki dies
    # as there's no such profile
    if is_mac and len(argv) > 1 and argv[1].startswith("-psn"):
        argv = [argv[0]]
    parser = argparse.ArgumentParser(description=f"Anki {appVersion}")
    parser.usage = "%(prog)s [OPTIONS] [file to import/add-on to install]"
    parser.add_argument("-b", "--base", help="path to base folder", default="")
    parser.add_argument("-p", "--profile", help="profile name to load", default="")
    parser.add_argument("-l", "--lang", help="interface language (en, de, etc)")
    parser.add_argument(
        "-v", "--version", help="print the Anki version and exit", action="store_true"
    )
    parser.add_argument(
        "--safemode", help="disable add-ons and automatic syncing", action="store_true"
    )
    parser.add_argument(
        "--syncserver",
        help="skip GUI and start a local sync server",
        action="store_true",
    )
    return parser.parse_known_args(argv[1:])


def setupGL(pm: aqt.profiles.ProfileManager) -> None:
    driver = pm.video_driver()
    # RHI errors are emitted multiple times so make sure we only handle them once
    driver_failed = False

    # work around pyqt loading wrong GL library
    if is_lin and not sys.platform.startswith("freebsd"):
        import ctypes

        ctypes.CDLL("libGL.so.1", ctypes.RTLD_GLOBAL)

    # catch opengl errors
    def msgHandler(category: Any, ctx: Any, msg: Any) -> None:
        if category == QtMsgType.QtDebugMsg:
            category = "debug"
        elif category == QtMsgType.QtInfoMsg:
            category = "info"
        elif category == QtMsgType.QtWarningMsg:
            category = "warning"
        elif category == QtMsgType.QtCriticalMsg:
            category = "critical"
        elif category == QtMsgType.QtDebugMsg:
            category = "debug"
        elif category == QtMsgType.QtFatalMsg:
            category = "fatal"
        else:
            category = "unknown"
        context = ""
        if ctx.file:
            context += f"{ctx.file}:"
        if ctx.line:
            context += f"{ctx.line},"
        if ctx.function:
            context += f"{ctx.function}"
        if context:
            context = f"'{context}'"

        nonlocal driver_failed
        if not driver_failed and (
            "Failed to create OpenGL context" in msg
            # Based on the message Qt6 shows to the user; have not tested whether
            # we can actually capture this or not.
            or "Failed to initialize graphics backend" in msg
            # RHI backend
            or "Failed to create QRhi" in msg
            or "Failed to get a QRhi" in msg
        ):
            QMessageBox.critical(
                None,
                tr.qt_misc_error(),
                tr.qt_misc_error_loading_graphics_driver(
                    mode=driver.value,
                    context=context,
                ),
            )
            pm.set_video_driver(driver.next())
            driver_failed = True
            return
        else:
            print(f"Qt {category}: {msg} {context}")

    qInstallMessageHandler(msgHandler)

    if driver == VideoDriver.OpenGL:
        # Leaving QT_OPENGL unset appears to sometimes produce different results
        # to explicitly setting it to 'auto'; the former seems to be more compatible.
        if qtmajor > 5:
            QQuickWindow.setGraphicsApi(QSGRendererInterface.GraphicsApi.OpenGL)
    elif driver in (VideoDriver.Software, VideoDriver.ANGLE):
        if is_win:
            # on Windows, this appears to be sufficient on Qt5/Qt6.
            # On Qt6, ANGLE is excluded by the enum.
            os.environ["QT_OPENGL"] = driver.value
        elif is_mac:
            QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_UseSoftwareOpenGL)
        elif is_lin:
            # Qt5 only
            os.environ["QT_XCB_FORCE_SOFTWARE_OPENGL"] = "1"
            # Required on Qt6
            if "QTWEBENGINE_CHROMIUM_FLAGS" not in os.environ:
                os.environ["QTWEBENGINE_CHROMIUM_FLAGS"] = "--disable-gpu"
        if qtmajor > 5:
            QQuickWindow.setGraphicsApi(QSGRendererInterface.GraphicsApi.Software)
    elif driver == VideoDriver.Metal:
        QQuickWindow.setGraphicsApi(QSGRendererInterface.GraphicsApi.Metal)
    elif driver == VideoDriver.Vulkan:
        QQuickWindow.setGraphicsApi(QSGRendererInterface.GraphicsApi.Vulkan)
    elif driver == VideoDriver.Direct3D:
        QQuickWindow.setGraphicsApi(QSGRendererInterface.GraphicsApi.Direct3D11)


PROFILE_CODE = os.environ.get("ANKI_PROFILE_CODE")


def write_profile_results() -> None:
    assert profiler is not None

    profiler.disable()
    profile = "out/anki.prof"
    profiler.dump_stats(profile)


def run() -> None:
    print("Preparing to run...")
    try:
        _run()
    except Exception as e:
        traceback.print_exc()
        QMessageBox.critical(
            None,
            "Startup Error",
            f"Please notify support of this error:\n\n{traceback.format_exc()}",
        )


def _run(argv: list[str] | None = None, exec: bool = True) -> AnkiApp | None:
    """Start AnkiQt application or reuse an existing instance if one exists.

    If the function is invoked with exec=False, the AnkiQt will not enter
    the main event loop - instead the application object will be returned.

    The 'exec' and 'argv' arguments will be useful for testing purposes.

    If no 'argv' is supplied then 'sys.argv' will be used.
    """
    global mw
    global profiler

    if argv is None:
        argv = sys.argv

    # parse args
    opts, args = parseArgs(argv)

    if opts.version:
        print(f"Anki {appVersion}")
        return None

    if PROFILE_CODE:
        profiler = cProfile.Profile()
        profiler.enable()

    packaged = getattr(sys, "frozen", False)
    x11_available = os.getenv("DISPLAY")
    wayland_configured = qtmajor > 5 and (
        os.getenv("QT_QPA_PLATFORM") == "wayland" or os.getenv("WAYLAND_DISPLAY")
    )
    wayland_forced = os.getenv("ANKI_WAYLAND")

    if packaged and wayland_configured:
        if wayland_forced or not x11_available:
            # Work around broken fractional scaling in Wayland
            # https://bugreports.qt.io/browse/QTBUG-113574
            os.environ["QT_SCALE_FACTOR_ROUNDING_POLICY"] = "RoundPreferFloor"
            if not x11_available:
                print(
                    "Trying to use X11, but it is not available. Falling back to Wayland, which has some bugs:"
                )
                print("https://github.com/ankitects/anki/issues/1767")
        else:
            # users need to opt in to wayland support, given the issues it has
            print("Wayland support is disabled by default due to bugs:")
            print("https://github.com/ankitects/anki/issues/1767")
            print("You can force it on with an env var: ANKI_WAYLAND=1")
            os.environ["QT_QPA_PLATFORM"] = "xcb"

    # profile manager
    i18n_setup = False
    pm = None
    try:
        base_folder = ProfileManager.get_created_base_folder(opts.base)

        # default to specified/system language before getting user's preference so that we can localize some more strings
        lang = anki.lang.get_def_lang(opts.lang)
        anki.lang.set_lang(lang[1])
        i18n_setup = True

        pm = ProfileManager(base_folder)
        pmLoadResult = pm.setupMeta()

        Collection.initialize_backend_logging()
    except Exception:
        # will handle below
        traceback.print_exc()
        pm = None

    if pm:
        # gl workarounds
        setupGL(pm)
        # apply user-provided scale factor
        os.environ["QT_SCALE_FACTOR"] = str(pm.uiScale())

    # Opt-in to full HiDPI support?
    if not os.environ.get("ANKI_NOHIGHDPI") and qtmajor == 5:
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_EnableHighDpiScaling)  # type: ignore
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_UseHighDpiPixmaps)  # type: ignore
        os.environ["QT_ENABLE_HIGHDPI_SCALING"] = "1"
        os.environ["QT_SCALE_FACTOR_ROUNDING_POLICY"] = "PassThrough"

    # Opt-in to software rendering?
    if os.environ.get("ANKI_SOFTWAREOPENGL"):
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_UseSoftwareOpenGL)

    # Fix an issue on Windows, where Ctrl+Alt shortcuts are triggered by AltGr,
    # preventing users from typing things like "@" through AltGr+Q on a German
    # keyboard.
    if is_win and "QT_QPA_PLATFORM" not in os.environ:
        os.environ["QT_QPA_PLATFORM"] = "windows:altgr"

    # Disable sandbox on Qt5 PyPi/packaged builds, as it causes blank screens on modern
    # glibc versions. We check for specific patch versions, because distros may have
    # fixed the issue in their own Qt builds.
    if is_lin and qtfullversion in ([5, 15, 2], [5, 14, 1]):
        os.environ["QTWEBENGINE_DISABLE_SANDBOX"] = "1"

    # create the app
    QCoreApplication.setApplicationName("Anki")
    QGuiApplication.setDesktopFileName("anki")
    app = AnkiApp(argv)
    if app.secondInstance():
        # we've signaled the primary instance, so we should close
        return None

    if not pm:
        if i18n_setup:
            QMessageBox.critical(
                None,
                tr.qt_misc_error(),
                tr.profiles_could_not_create_data_folder(),
            )
        else:
            QMessageBox.critical(None, "Startup Failed", "Unable to create data folder")
        return None

    setup_logging(
        pm.addon_logs(),
        level=logging.DEBUG if int(os.getenv("ANKIDEV", "0")) else logging.INFO,
    )

    # disable icons on mac; this must be done before window created
    if is_mac:
        app.setAttribute(Qt.ApplicationAttribute.AA_DontShowIconsInMenus)

    # disable help button in title bar on qt versions that support it
    if is_win and qtmajor == 5 and qtminor >= 10:
        QApplication.setAttribute(Qt.AA_DisableWindowContextHelpButton)  # type: ignore

    # proxy configured?
    from urllib.request import getproxies, proxy_bypass

    disable_proxies = False
    try:
        if "http" in getproxies():
            # if it's not set up to bypass localhost, we'll
            # need to disable proxies in the webviews
            if not proxy_bypass("127.0.0.1"):
                disable_proxies = True
    except UnicodeDecodeError:
        # proxy_bypass can't handle unicode in hostnames; assume we need
        # to disable proxies
        disable_proxies = True

    if disable_proxies:
        print("webview proxy use disabled")
        proxy = QNetworkProxy()
        proxy.setType(QNetworkProxy.ProxyType.NoProxy)
        QNetworkProxy.setApplicationProxy(proxy)

    # we must have a usable temp dir
    try:
        tempfile.gettempdir()
    except Exception:
        QMessageBox.critical(
            None,
            tr.qt_misc_error(),
            tr.qt_misc_no_temp_folder(),
        )
        return None

    # make image resources available
    from aqt.utils import aqt_data_folder

    QDir.addSearchPath("icons", os.path.join(aqt_data_folder(), "qt", "icons"))

    if pmLoadResult.firstTime:
        pm.setDefaultLang(lang[0])

    if pmLoadResult.loadError:
        QMessageBox.warning(
            None,
            tr.profiles_prefs_corrupt_title(),
            tr.profiles_prefs_file_is_corrupt(),
        )

    if opts.profile:
        pm.openProfile(opts.profile)

    # i18n & backend
    backend = setupLangAndBackend(pm, app, opts.lang, pmLoadResult.firstTime)

    driver = pm.video_driver()
    if is_lin and driver == VideoDriver.OpenGL:
        from aqt.utils import gfxDriverIsBroken

        if gfxDriverIsBroken():
            pm.set_video_driver(driver.next())
            QMessageBox.critical(
                None,
                tr.qt_misc_error(),
                tr.qt_misc_incompatible_video_driver(),
            )
            sys.exit(1)

    # load the main window
    import aqt.main

    mw = aqt.main.AnkiQt(app, pm, backend, opts, args)
    if exec:
        print("Starting main loop...")
        app.exec()
    else:
        return app

    if PROFILE_CODE:
        write_profile_results()

    return None
