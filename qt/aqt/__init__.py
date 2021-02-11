# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import argparse
import builtins
import cProfile
import getpass
import locale
import os
import sys
import tempfile
import traceback
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

import anki.lang
from anki import version as _version
from anki._backend import RustBackend
from anki.consts import HELP_SITE
from anki.utils import checksum, isLin, isMac
from aqt.qt import *
from aqt.utils import TR, locale_dir, tr

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
from aqt.profiles import ProfileManager, AnkiRestart, VideoDriver  # isort:skip

profiler: Optional[cProfile.Profile] = None
mw: Optional[AnkiQt] = None  # set on init

moduleDir = os.path.split(os.path.dirname(os.path.abspath(__file__)))[0]

try:
    import aqt.forms
except ImportError as e:
    if "forms" in str(e):
        print("If you're running from git, did you run build_ui.sh?")
        print()
    raise


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


from aqt import addcards, addons, browser, editcurrent, dyndeckconf  # isort:skip
from aqt import stats, about, preferences, mediasync  # isort:skip


class DialogManager:

    _dialogs: Dict[str, list] = {
        "AddCards": [addcards.AddCards, None],
        "AddonsDialog": [addons.AddonsDialog, None],
        "Browser": [browser.Browser, None],
        "EditCurrent": [editcurrent.EditCurrent, None],
        "DynDeckConfDialog": [dyndeckconf.DeckConf, None],
        "DeckStats": [stats.DeckStats, None],
        "NewDeckStats": [stats.NewDeckStats, None],
        "About": [about.show, None],
        "Preferences": [preferences.Preferences, None],
        "sync_log": [mediasync.MediaSyncDialog, None],
    }

    def open(self, name: str, *args: Any, **kwargs: Any) -> Any:
        (creator, instance) = self._dialogs[name]
        if instance:
            if instance.windowState() & Qt.WindowMinimized:
                instance.setWindowState(instance.windowState() & ~Qt.WindowMinimized)
            instance.activateWindow()
            instance.raise_()
            if hasattr(instance, "reopen"):
                instance.reopen(*args, **kwargs)
        else:
            instance = creator(*args, **kwargs)
            self._dialogs[name][1] = instance
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

    ldir = locale_dir()
    if not firstTime:
        # set active language
        anki.lang.set_lang(lang, ldir)

    # switch direction for RTL languages
    if anki.lang.is_rtl(lang):
        app.setLayoutDirection(Qt.RightToLeft)
    else:
        app.setLayoutDirection(Qt.LeftToRight)

    # load qt translations
    _qtrans = QTranslator()

    from aqt.utils import aqt_data_folder

    if isMac and getattr(sys, "frozen", False):
        qt_dir = os.path.abspath(
            os.path.join(aqt_data_folder(), "..", "qt_translations")
        )
    else:
        qt_dir = QLibraryInfo.location(QLibraryInfo.TranslationsPath)
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

    def __init__(self, argv: List[str]) -> None:
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
        sock.connectToServer(self.KEY, QIODevice.WriteOnly)
        if not sock.waitForConnected(self.TMOUT):
            # first instance or previous instance dead
            return False
        sock.write(txt.encode("utf8"))
        if not sock.waitForBytesWritten(self.TMOUT):
            # existing instance running but hung
            QMessageBox.warning(
                None,
                tr(TR.QT_MISC_ANKI_IS_RUNNING),
                tr(TR.QT_MISC_IF_INSTANCE_IS_NOT_RESPONDING),
            )

            sys.exit(1)
        sock.disconnectFromServer()
        return True

    def onRecv(self) -> None:
        sock = self._srv.nextPendingConnection()
        if not sock.waitForReadyRead(self.TMOUT):
            sys.stderr.write(sock.errorString())
            return
        path = bytes(sock.readAll()).decode("utf8")
        self.appMsg.emit(path)  # type: ignore
        sock.disconnectFromServer()

    # OS X file/url handler
    ##################################################

    def event(self, evt: QEvent) -> bool:
        if evt.type() == QEvent.FileOpen:
            self.appMsg.emit(evt.file() or "raise")  # type: ignore
            return True
        return QApplication.event(self, evt)


def parseArgs(argv: List[str]) -> Tuple[argparse.Namespace, List[str]]:
    "Returns (opts, args)."
    # py2app fails to strip this in some instances, then anki dies
    # as there's no such profile
    if isMac and len(argv) > 1 and argv[1].startswith("-psn"):
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
    if isMac:
        return

    driver = pm.video_driver()

    # work around pyqt loading wrong GL library
    if isLin:
        import ctypes

        ctypes.CDLL("libGL.so.1", ctypes.RTLD_GLOBAL)

    # catch opengl errors
    def msgHandler(category: Any, ctx: Any, msg: Any) -> None:
        if category == QtDebugMsg:
            category = "debug"
        elif category == QtInfoMsg:
            category = "info"
        elif category == QtWarningMsg:
            category = "warning"
        elif category == QtCriticalMsg:
            category = "critical"
        elif category == QtDebugMsg:
            category = "debug"
        elif category == QtFatalMsg:
            category = "fatal"
        elif category == QtSystemMsg:
            category = "system"
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
        if "Failed to create OpenGL context" in msg:
            QMessageBox.critical(
                None,
                tr(TR.QT_MISC_ERROR),
                tr(
                    TR.QT_MISC_ERROR_LOADING_GRAPHICS_DRIVER,
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
        pass
    else:
        if isWin:
            os.environ["QT_OPENGL"] = driver.value
        elif isMac:
            QCoreApplication.setAttribute(Qt.AA_UseSoftwareOpenGL)
        elif isLin:
            os.environ["QT_XCB_FORCE_SOFTWARE_OPENGL"] = "1"


PROFILE_CODE = os.environ.get("ANKI_PROFILE_CODE")


def write_profile_results() -> None:

    profiler.disable()
    profiler.dump_stats("anki.prof")
    print("profile stats written to anki.prof")
    print("use 'bazel run qt:profile' to explore")


def run() -> None:
    try:
        _run()
    except Exception as e:
        traceback.print_exc()
        QMessageBox.critical(
            None,
            "Startup Error",
            f"Please notify support of this error:\n\n{traceback.format_exc()}",
        )


def _run(argv: Optional[List[str]] = None, exec: bool = True) -> Optional[AnkiApp]:
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
    elif opts.syncserver:
        from anki.syncserver import serve

        serve()
        return None

    if PROFILE_CODE:

        profiler = cProfile.Profile()
        profiler.enable()

    # default to specified/system language before getting user's preference so that we can localize some more strings
    lang = anki.lang.get_def_lang(opts.lang)
    anki.lang.set_lang(lang[1], locale_dir())

    # profile manager
    pm = None
    try:
        pm = ProfileManager(opts.base)
        pmLoadResult = pm.setupMeta()
    except AnkiRestart as error:
        if error.exitcode:
            sys.exit(error.exitcode)
        return None
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
    if not os.environ.get("ANKI_NOHIGHDPI"):
        QCoreApplication.setAttribute(Qt.AA_EnableHighDpiScaling)
        QCoreApplication.setAttribute(Qt.AA_UseHighDpiPixmaps)
        os.environ["QT_ENABLE_HIGHDPI_SCALING"] = "1"
        os.environ["QT_SCALE_FACTOR_ROUNDING_POLICY"] = "PassThrough"

    # Opt into software rendering. Useful for buggy systems.
    if os.environ.get("ANKI_SOFTWAREOPENGL"):
        QCoreApplication.setAttribute(Qt.AA_UseSoftwareOpenGL)

    if (
        isWin
        and (qtminor == 14 or (qtminor == 15 and qtpoint == 0))
        and "QT_QPA_PLATFORM" not in os.environ
    ):
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
            tr(TR.QT_MISC_ERROR),
            tr(TR.PROFILES_COULD_NOT_CREATE_DATA_FOLDER),
        )
        return None

    # disable icons on mac; this must be done before window created
    if isMac:
        app.setAttribute(Qt.AA_DontShowIconsInMenus)

    # disable help button in title bar on qt versions that support it
    if isWin and qtminor >= 10:
        QApplication.setAttribute(Qt.AA_DisableWindowContextHelpButton)

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
        proxy.setType(QNetworkProxy.NoProxy)
        QNetworkProxy.setApplicationProxy(proxy)

    # we must have a usable temp dir
    try:
        tempfile.gettempdir()
    except:
        QMessageBox.critical(
            None,
            tr(TR.QT_MISC_ERROR),
            tr(TR.QT_MISC_NO_TEMP_FOLDER),
        )
        return None

    if pmLoadResult.firstTime:
        pm.setDefaultLang(lang[0])

    if pmLoadResult.loadError:
        QMessageBox.warning(
            None,
            tr(TR.PROFILES_PREFS_CORRUPT_TITLE),
            tr(TR.PROFILES_PREFS_FILE_IS_CORRUPT),
        )

    if opts.profile:
        pm.openProfile(opts.profile)

    # i18n & backend
    backend = setupLangAndBackend(pm, app, opts.lang, pmLoadResult.firstTime)

    driver = pm.video_driver()
    if isLin and driver == VideoDriver.OpenGL:
        from aqt.utils import gfxDriverIsBroken

        if gfxDriverIsBroken():
            pm.set_video_driver(driver.next())
            QMessageBox.critical(
                None,
                tr(TR.QT_MISC_ERROR),
                tr(TR.QT_MISC_INCOMPATIBLE_VIDEO_DRIVER),
            )
            sys.exit(1)

    # load the main window
    import aqt.main

    mw = aqt.main.AnkiQt(app, pm, backend, opts, args)
    if exec:
        app.exec()
    else:
        return app

    if PROFILE_CODE:
        write_profile_results()

    return None
