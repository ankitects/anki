# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import argparse
import builtins
import getpass
import gettext
import locale
import os
import sys
import tempfile
import traceback
from typing import Any, Callable, Dict, Optional, Union

import anki.buildinfo
import anki.lang
import aqt.buildinfo
from anki import version as _version
from anki.consts import HELP_SITE
from anki.rsbackend import RustBackend
from anki.utils import checksum, isLin, isMac
from aqt.qt import *
from aqt.utils import locale_dir

assert anki.buildinfo.buildhash == aqt.buildinfo.buildhash

appVersion = _version
appWebsite = "https://apps.ankiweb.net/"
appChanges = "https://apps.ankiweb.net/docs/changes.html"
appDonate = "https://apps.ankiweb.net/support/"
appShared = "https://ankiweb.net/shared/"
appUpdate = "https://ankiweb.net/update/desktop"
appHelpSite = HELP_SITE

from aqt.main import AnkiQt  # isort:skip
from aqt.profiles import ProfileManager  # isort:skip

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


from aqt import addcards, browser, editcurrent  # isort:skip
from aqt import stats, about, preferences, mediasync  # isort:skip


class DialogManager:

    _dialogs: Dict[str, list] = {
        "AddCards": [addcards.AddCards, None],
        "Browser": [browser.Browser, None],
        "EditCurrent": [editcurrent.EditCurrent, None],
        "DeckStats": [stats.DeckStats, None],
        "About": [about.show, None],
        "Preferences": [preferences.Preferences, None],
        "sync_log": [mediasync.MediaSyncDialog, None],
    }

    def open(self, name: str, *args: Any) -> Any:
        (creator, instance) = self._dialogs[name]
        if instance:
            if instance.windowState() & Qt.WindowMinimized:
                instance.setWindowState(instance.windowState() & ~Qt.WindowMinimized)
            instance.activateWindow()
            instance.raise_()
            if hasattr(instance, "reopen"):
                instance.reopen(*args)
            return instance
        else:
            instance = creator(*args)
            self._dialogs[name][1] = instance
            return instance

    def markClosed(self, name: str):
        self._dialogs[name] = [self._dialogs[name][0], None]

    def allClosed(self):
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

            def callback():
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
    ):
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
# loaded, and we need the Qt language to match the gettext language or
# translated shortcuts will not work.

# A reference to the Qt translator needs to be held to prevent it from
# being immediately deallocated.
_qtrans: Optional[QTranslator] = None


def setupLangAndBackend(
    pm: ProfileManager, app: QApplication, force: Optional[str] = None
) -> RustBackend:
    global _qtrans
    try:
        locale.setlocale(locale.LC_ALL, "")
    except:
        pass

    # add _ and ngettext globals used by legacy code
    def fn__(arg):
        print("accessing _ without importing from anki.lang will break in the future")
        print("".join(traceback.format_stack()[-2]))
        from anki.lang import _

        return _(arg)

    def fn_ngettext(a, b, c):
        print(
            "accessing ngettext without importing from anki.lang will break in the future"
        )
        print("".join(traceback.format_stack()[-2]))
        from anki.lang import ngettext

        return ngettext(a, b, c)

    builtins.__dict__["_"] = fn__
    builtins.__dict__["ngettext"] = fn_ngettext

    # get lang and normalize into ja/zh-CN form
    lang = force or pm.meta["defaultLang"]
    lang = anki.lang.lang_to_disk_lang(lang)

    # load gettext catalog
    ldir = locale_dir()
    anki.lang.set_lang(lang, ldir)

    # switch direction for RTL languages
    if lang in ("he", "ar", "fa"):
        app.setLayoutDirection(Qt.RightToLeft)
    else:
        app.setLayoutDirection(Qt.LeftToRight)

    # load qt translations
    _qtrans = QTranslator()
    qt_dir = os.path.join(ldir, "qt")
    qt_lang = lang.replace("-", "_")
    if _qtrans.load("qtbase_" + qt_lang, qt_dir):
        app.installTranslator(_qtrans)

    return anki.lang.current_i18n


# App initialisation
##########################################################################


class AnkiApp(QApplication):

    # Single instance support on Win32/Linux
    ##################################################

    appMsg = pyqtSignal(str)

    KEY = "anki" + checksum(getpass.getuser())
    TMOUT = 30000

    def __init__(self, argv):
        QApplication.__init__(self, argv)
        self._argv = argv

    def secondInstance(self):
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
            self._srv.newConnection.connect(self.onRecv)
            self._srv.listen(self.KEY)
            return False

    def sendMsg(self, txt):
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
                "Anki Already Running",
                "If the existing instance of Anki is not responding, please close it using your task manager, or restart your computer.",
            )

            sys.exit(1)
        sock.disconnectFromServer()
        return True

    def onRecv(self):
        sock = self._srv.nextPendingConnection()
        if not sock.waitForReadyRead(self.TMOUT):
            sys.stderr.write(sock.errorString())
            return
        path = bytes(sock.readAll()).decode("utf8")
        self.appMsg.emit(path)
        sock.disconnectFromServer()

    # OS X file/url handler
    ##################################################

    def event(self, evt):
        if evt.type() == QEvent.FileOpen:
            self.appMsg.emit(evt.file() or "raise")
            return True
        return QApplication.event(self, evt)


def parseArgs(argv):
    "Returns (opts, args)."
    # py2app fails to strip this in some instances, then anki dies
    # as there's no such profile
    if isMac and len(argv) > 1 and argv[1].startswith("-psn"):
        argv = [argv[0]]
    parser = argparse.ArgumentParser(description="Anki " + appVersion)
    parser.usage = "%(prog)s [OPTIONS] [file to import/add-on to install]"
    parser.add_argument("-b", "--base", help="path to base folder", default="")
    parser.add_argument("-p", "--profile", help="profile name to load", default="")
    parser.add_argument("-l", "--lang", help="interface language (en, de, etc)")
    return parser.parse_known_args(argv[1:])


def setupGL(pm):
    if isMac:
        return

    mode = pm.glMode()

    # work around pyqt loading wrong GL library
    if isLin:
        import ctypes

        ctypes.CDLL("libGL.so.1", ctypes.RTLD_GLOBAL)

    # catch opengl errors
    def msgHandler(type, ctx, msg):
        if "Failed to create OpenGL context" in msg:
            QMessageBox.critical(
                None,
                "Error",
                "Error loading '%s' graphics driver. Please start Anki again to try next driver."
                % mode,
            )
            pm.nextGlMode()
            return
        else:
            print("qt:", msg)

    qInstallMessageHandler(msgHandler)

    if mode == "auto":
        return
    elif isLin:
        os.environ["QT_XCB_FORCE_SOFTWARE_OPENGL"] = "1"
    else:
        os.environ["QT_OPENGL"] = mode


def run():
    try:
        _run()
    except Exception as e:
        traceback.print_exc()
        QMessageBox.critical(
            None,
            "Startup Error",
            "Please notify support of this error:\n\n" + traceback.format_exc(),
        )


def _run(argv=None, exec=True):
    """Start AnkiQt application or reuse an existing instance if one exists.

    If the function is invoked with exec=False, the AnkiQt will not enter
    the main event loop - instead the application object will be returned.

    The 'exec' and 'argv' arguments will be useful for testing purposes.

    If no 'argv' is supplied then 'sys.argv' will be used.
    """
    global mw

    if argv is None:
        argv = sys.argv

    # parse args
    opts, args = parseArgs(argv)

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
    if not os.environ.get("ANKI_NOHIGHDPI"):
        QCoreApplication.setAttribute(Qt.AA_EnableHighDpiScaling)
        os.environ["QT_ENABLE_HIGHDPI_SCALING"] = "1"
        os.environ["QT_SCALE_FACTOR_ROUNDING_POLICY"] = "PassThrough"

    # Opt into software rendering. Useful for buggy systems.
    if os.environ.get("ANKI_SOFTWAREOPENGL"):
        QCoreApplication.setAttribute(Qt.AA_UseSoftwareOpenGL)

    # create the app
    QCoreApplication.setApplicationName("Anki")
    QGuiApplication.setDesktopFileName("anki.desktop")
    app = AnkiApp(argv)
    if app.secondInstance():
        # we've signaled the primary instance, so we should close
        return

    if not pm:
        QMessageBox.critical(
            None,
            "Error",
            """\
Anki could not create its data folder. Please see the File Locations \
section of the manual, and ensure that location is not read-only.""",
        )
        return

    # disable icons on mac; this must be done before window created
    if isMac:
        app.setAttribute(Qt.AA_DontShowIconsInMenus)

    # disable help button in title bar on qt versions that support it
    if isWin and qtminor >= 10:
        QApplication.setAttribute(Qt.AA_DisableWindowContextHelpButton)

    # proxy configured?
    from urllib.request import proxy_bypass, getproxies

    if "http" in getproxies():
        # if it's not set up to bypass localhost, we'll
        # need to disable proxies in the webviews
        if not proxy_bypass("127.0.0.1"):
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
            "Error",
            """\
No usable temporary folder found. Make sure C:\\temp exists or TEMP in your \
environment points to a valid, writable folder.""",
        )
        return

    if pmLoadResult.firstTime:
        pm.setDefaultLang()

    if pmLoadResult.loadError:
        QMessageBox.warning(
            None,
            "Preferences Corrupt",
            """Anki's prefs21.db file was corrupt and has been recreated. If you were using multiple \
    profiles, please add them back using the same names to recover your cards.""",
        )

    if opts.profile:
        pm.openProfile(opts.profile)

    # i18n & backend
    backend = setupLangAndBackend(pm, app, opts.lang)

    if isLin and pm.glMode() == "auto":
        from aqt.utils import gfxDriverIsBroken

        if gfxDriverIsBroken():
            pm.nextGlMode()
            QMessageBox.critical(
                None,
                "Error",
                "Your video driver is incompatible. Please start Anki again, and Anki will switch to a slower, more compatible mode.",
            )
            sys.exit(1)

    # load the main window
    import aqt.main

    mw = aqt.main.AnkiQt(app, pm, backend, opts, args)
    if exec:
        app.exec()
    else:
        return app
