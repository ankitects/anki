# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki import version as _version

import getpass
import sys
import optparse
import tempfile
import builtins
import locale
import gettext

from aqt.qt import *
import anki.lang
from anki.consts import HELP_SITE
from anki.lang import langDir
from anki.utils import isMac, isLin

appVersion=_version
appWebsite="http://ankisrs.net/"
appChanges="http://ankisrs.net/docs/changes.html"
appDonate="http://ankisrs.net/support/"
appShared="https://ankiweb.net/shared/"
appUpdate="https://ankiweb.net/update/desktop"
appHelpSite=HELP_SITE
mw = None # set on init

moduleDir = os.path.split(os.path.dirname(os.path.abspath(__file__)))[0]

try:
    import aqt.forms
except ImportError as e:
    if "forms" in str(e):
        print("If you're running from git, did you run build_ui.sh?")
        print()
    raise

from anki.utils import checksum

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

#- make preferences modal? cmd+q does wrong thing


from aqt import addcards, browser, editcurrent, stats, about, \
    preferences

class DialogManager:

    _dialogs = {
        "AddCards": [addcards.AddCards, None],
        "Browser": [browser.Browser, None],
        "EditCurrent": [editcurrent.EditCurrent, None],
        "DeckStats": [stats.DeckStats, None],
        "About": [about.show, None],
        "Preferences": [preferences.Preferences, None],
    }

    def open(self, name, *args):
        (creator, instance) = self._dialogs[name]
        if instance:
            instance.setWindowState(Qt.WindowNoState)
            instance.activateWindow()
            instance.raise_()
            return instance
        else:
            instance = creator(*args)
            self._dialogs[name][1] = instance
            return instance

    def markClosed(self, name):
        self._dialogs[name] = [self._dialogs[name][0], None]

    def allClosed(self):
        return not any(x[1] for x in self._dialogs.values())

    def closeAll(self, onsuccess):
        # can we close immediately?
        if self.allClosed():
            onsuccess()
            return

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

dialogs = DialogManager()

# Language handling
##########################################################################
# Qt requires its translator to be installed before any GUI widgets are
# loaded, and we need the Qt language to match the gettext language or
# translated shortcuts will not work.

_gtrans = None
_qtrans = None

def setupLang(pm, app, force=None):
    global _gtrans, _qtrans
    try:
        locale.setlocale(locale.LC_ALL, '')
    except:
        pass
    lang = force or pm.meta["defaultLang"]
    dir = langDir()
    # gettext
    _gtrans = gettext.translation(
        'anki', dir, languages=[lang], fallback=True)
    builtins.__dict__['_'] = _gtrans.gettext
    builtins.__dict__['ngettext'] = _gtrans.ngettext
    anki.lang.setLang(lang, local=False)
    if lang in ("he","ar","fa"):
        app.setLayoutDirection(Qt.RightToLeft)
    else:
        app.setLayoutDirection(Qt.LeftToRight)
    # qt
    _qtrans = QTranslator()
    if _qtrans.load("qt_" + lang, dir):
        app.installTranslator(_qtrans)

# App initialisation
##########################################################################

class AnkiApp(QApplication):

    # Single instance support on Win32/Linux
    ##################################################

    appMsg = pyqtSignal(str)

    KEY = "anki"+checksum(getpass.getuser())
    TMOUT = 5000

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
            return False
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
    parser = optparse.OptionParser(version="%prog " + appVersion)
    parser.usage = "%prog [OPTIONS] [file to import]"
    parser.add_option("-b", "--base", help="path to base folder")
    parser.add_option("-p", "--profile", help="profile name to load")
    parser.add_option("-l", "--lang", help="interface language (en, de, etc)")
    return parser.parse_args(argv[1:])

def run():
    try:
        _run()
    except Exception as e:
        QMessageBox.critical(None, "Startup Error",
                             "Please notify support of this error:\n\n"+
                             traceback.format_exc())

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
    opts.base = opts.base or ""
    opts.profile = opts.profile or ""

    # work around pyqt loading wrong GL library
    if isLin:
        import ctypes
        ctypes.CDLL('libGL.so.1', ctypes.RTLD_GLOBAL)

    # opt in to full hidpi support?
    if not os.environ.get("ANKI_NOHIGHDPI"):
        QCoreApplication.setAttribute(Qt.AA_EnableHighDpiScaling)

    # create the app
    app = AnkiApp(argv)
    QCoreApplication.setApplicationName("Anki")
    if app.secondInstance():
        # we've signaled the primary instance, so we should close
        return

    # disable icons on mac; this must be done before window created
    if isMac:
        app.setAttribute(Qt.AA_DontShowIconsInMenus)

    # we must have a usable temp dir
    try:
        tempfile.gettempdir()
    except:
        QMessageBox.critical(
            None, "Error", """\
No usable temporary folder found. Make sure C:\\temp exists or TEMP in your \
environment points to a valid, writable folder.""")
        return

    # profile manager
    from aqt.profiles import ProfileManager
    pm = ProfileManager(opts.base, opts.profile)

    # i18n
    setupLang(pm, app, opts.lang)

    # remaining pm init
    pm.ensureProfile()

    print("This is an BETA build - please do not package it up for Linux distributions")

    # load the main window
    import aqt.main
    mw = aqt.main.AnkiQt(app, pm, opts, args)
    if exec:
        app.exec()
    else:
        return app
