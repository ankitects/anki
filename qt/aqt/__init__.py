# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys

if sys.version_info[0] < 3 or sys.version_info[1] < 9:
    raise Exception("Anki requires Python 3.9+")

# ensure unicode filenames are supported
try:
    "テスト".encode(sys.getfilesystemencoding())
except UnicodeEncodeError as exc:
    raise Exception("Anki requires a UTF-8 locale.") from exc

from .package import packaged_build_setup

packaged_build_setup()

# syncserver needs to be run before Qt loaded
if "--syncserver" in sys.argv:
    from anki.syncserver import serve

    serve()
    sys.exit(0)


import argparse
import builtins
import cProfile
import getpass
import locale
import os
import tempfile
import traceback
from typing import TYPE_CHECKING, Any, Callable, Optional, cast

import anki.lang
from anki._backend import RustBackend
from anki.buildinfo import version as _version
from anki.collection import Collection
from anki.consts import HELP_SITE
from anki.utils import checksum, is_lin, is_mac
from aqt import gui_hooks
from aqt.qt import *
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
    # on Windows without console, NullWriter doesn't support this
    pass

appVersion = _version
appWebsite = "https://apps.ankiweb.net/"
appDonate = "https://apps.ankiweb.net/support/"
appShared = "https://ankiweb.net/shared/"
appUpdate = "https://ankiweb.net/update/desktop"
appHelpSite = HELP_SITE

from aqt.main import AnkiQt  # isort:skip
from aqt.profiles import ProfileManager, VideoDriver  # isort:skip

profiler: Optional[cProfile.Profile] = None
mw: Optional[AnkiQt] = None  # set on init

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

    def closeAll(self, onsuccess: Callable[[], None]) -> Optional[bool]:
        # can we close immediately?
        if self.allClosed():
            onsuccess()
            return None

        # ask all windows to close and await a reply
        for (name, (creator, instance)) in self._dialogs.items():
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
        self, name: str, creator: Union[Callable, type], instance: Optional[Any] = None
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
_qtrans: Optional[QTranslator] = None


def setupLangAndBackend(
    pm: ProfileManager,
    app: QApplication,
    force: Optional[str] = None,
    firstTime: bool = False,
) -> RustBackend:
    global _qtrans
    try:
        locale.setlocale(locale.LC_ALL, "")
    except:
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

    if not firstTime:
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

    return anki.lang.current_i18n


# App initialisation
##########################################################################


class AnkiApp(QApplication):

    # Single instance support on Win32/Linux
    ##################################################

    appMsg = pyqtSignal(str)

    KEY = f"anki{checksum(getpass.getuser())}"
    TMOUT = 30000

    def __init__(self, argv: list[str]) -> None:
        QApplication.__init__(self, argv)
        self._argv = argv

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

    def event(self, evt: QEvent) -> bool:
        if evt.type() == QEvent.Type.FileOpen:
            self.appMsg.emit(evt.file() or "raise")  # type: ignore
            return True
        return QApplication.event(self, evt)


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

    # work around pyqt loading wrong GL library
    if is_lin:
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
        if (
            "Failed to create OpenGL context" in msg
            # Based on the message Qt6 shows to the user; have not tested whether
            # we can actually capture this or not.
            or "Failed to initialize graphics backend" in msg
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
            return
        else:
            print(f"Qt {category}: {msg} {context}")

    qInstallMessageHandler(msgHandler)

    if driver == VideoDriver.OpenGL:
        # Leaving QT_OPENGL unset appears to sometimes produce different results
        # to explicitly setting it to 'auto'; the former seems to be more compatible.
        pass
    else:
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


PROFILE_CODE = os.environ.get("ANKI_PROFILE_CODE")


def write_profile_results() -> None:

    profiler.disable()
    profile = os.path.join(os.environ.get("BUILD_WORKSPACE_DIRECTORY", ""), "anki.prof")
    profiler.dump_stats(profile)
    profiler.dump_stats("anki.prof")
    print("profile stats written to anki.prof")
    print("use 'bazel run qt:profile' to explore")


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


def _run(argv: Optional[list[str]] = None, exec: bool = True) -> Optional[AnkiApp]:
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

    if (
        getattr(sys, "frozen", False)
        and os.getenv("QT_QPA_PLATFORM") == "wayland"
        and not os.getenv("ANKI_WAYLAND")
    ):
        # users need to opt in to wayland support, given the issues it has
        print("Wayland support is disabled by default due to bugs:")
        print("https://github.com/ankitects/anki/issues/1767")
        print("You can force it on with an env var: ANKI_WAYLAND=1")
        os.environ["QT_QPA_PLATFORM"] = "xcb"

    # default to specified/system language before getting user's preference so that we can localize some more strings
    lang = anki.lang.get_def_lang(opts.lang)
    anki.lang.set_lang(lang[1])

    # profile manager
    pm = None
    try:
        pm = ProfileManager(opts.base)
        pmLoadResult = pm.setupMeta()
    except:
        # will handle below
        traceback.print_exc()
        pm = None

    if pm:
        # gl workarounds
        setupGL(pm)
        # apply user-provided scale factor
        os.environ["QT_SCALE_FACTOR"] = str(pm.uiScale())

    # opt in to full hidpi support?
    if not os.environ.get("ANKI_NOHIGHDPI") and qtmajor == 5:
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_EnableHighDpiScaling)  # type: ignore
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_UseHighDpiPixmaps)  # type: ignore
        os.environ["QT_ENABLE_HIGHDPI_SCALING"] = "1"
        os.environ["QT_SCALE_FACTOR_ROUNDING_POLICY"] = "PassThrough"

    # Opt into software rendering. Useful for buggy systems.
    if os.environ.get("ANKI_SOFTWAREOPENGL"):
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_UseSoftwareOpenGL)

    # fix an issue on Windows, where Ctrl+Alt shortcuts are triggered by AltGr,
    # preventing users from typing things like "@" through AltGr+Q on a German
    # keyboard.
    if is_win and "QT_QPA_PLATFORM" not in os.environ:
        os.environ["QT_QPA_PLATFORM"] = "windows:altgr"

    # create the app
    QCoreApplication.setApplicationName("Anki")
    QGuiApplication.setDesktopFileName("anki.desktop")
    app = AnkiApp(argv)
    if app.secondInstance():
        # we've signaled the primary instance, so we should close
        return None

    if not pm:
        QMessageBox.critical(
            None,
            tr.qt_misc_error(),
            tr.profiles_could_not_create_data_folder(),
        )
        return None

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
    except:
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
