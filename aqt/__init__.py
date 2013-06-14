# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys, optparse, atexit, tempfile, __builtin__
from aqt.qt import *
import locale, gettext
import anki.lang
from anki.consts import HELP_SITE
from anki.lang import langDir
from anki.utils import isMac

appVersion="2.0.11"
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
except ImportError, e:
    if "forms" in str(e):
        print "If you're running from git, did you run build_ui.sh?"
        print
    raise

# Dialog manager - manages modeless windows
##########################################################################

class DialogManager(object):

    def __init__(self):
        from aqt import addcards, browser, editcurrent
        self._dialogs = {
            "AddCards": [addcards.AddCards, None],
            "Browser": [browser.Browser, None],
            "EditCurrent": [editcurrent.EditCurrent, None],
        }

    def open(self, name, *args):
        (creator, instance) = self._dialogs[name]
        if instance:
            instance.setWindowState(Qt.WindowActive)
            instance.activateWindow()
            instance.raise_()
            return instance
        else:
            instance = creator(*args)
            self._dialogs[name][1] = instance
            return instance

    def close(self, name):
        self._dialogs[name] = [self._dialogs[name][0], None]

    def closeAll(self):
        "True if all closed successfully."
        for (n, (creator, instance)) in self._dialogs.items():
            if instance:
                if not instance.canClose():
                    return False
                instance.forceClose = True
                instance.close()
                self.close(n)
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
    __builtin__.__dict__['_'] = _gtrans.ugettext
    __builtin__.__dict__['ngettext'] = _gtrans.ungettext
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

    KEY = "anki"
    TMOUT = 5000

    def __init__(self, argv):
        QApplication.__init__(self, argv)
        self._argv = argv
        self._shmem = QSharedMemory(self.KEY)
        self.alreadyRunning = self._shmem.attach()

    def secondInstance(self):
        if not self.alreadyRunning:
            # use a 1 byte shared memory instance to signal we exist
            if not self._shmem.create(1):
                raise Exception("shared memory not supported")
            atexit.register(self._shmem.detach)
            # and a named pipe/unix domain socket for ipc
            QLocalServer.removeServer(self.KEY)
            self._srv = QLocalServer(self)
            self.connect(self._srv, SIGNAL("newConnection()"), self.onRecv)
            self._srv.listen(self.KEY)
        else:
            print "Raising existing window."
            # we accept only one command line argument. if it's missing, send
            # a blank screen to just raise the existing window
            opts, args = parseArgs(self._argv)
            buf = "raise"
            if args and args[0]:
                buf = os.path.abspath(args[0])
            self.sendMsg(buf)
            return True

    def sendMsg(self, txt):
        sock = QLocalSocket(self)
        sock.connectToServer(self.KEY, QIODevice.WriteOnly)
        if not sock.waitForConnected(self.TMOUT):
            raise Exception("existing instance not responding")
        sock.write(txt)
        if not sock.waitForBytesWritten(self.TMOUT):
            raise Exception("existing instance not emptying")
        sock.disconnectFromServer()

    def onRecv(self):
        sock = self._srv.nextPendingConnection()
        if not sock.waitForReadyRead(self.TMOUT):
            sys.stderr.write(sock.errorString())
            return
        buf = sock.readAll()
        self.emit(SIGNAL("appMsg"), buf)
        sock.disconnectFromServer()

    # OS X file/url handler
    ##################################################

    def event(self, evt):
        if evt.type() == QEvent.FileOpen:
            self.emit(SIGNAL("appMsg"), evt.file() or "raise")
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
    global mw

    # parse args
    opts, args = parseArgs(sys.argv)
    opts.base = unicode(opts.base or "", sys.getfilesystemencoding())
    opts.profile = unicode(opts.profile or "", sys.getfilesystemencoding())

    # on osx we'll need to add the qt plugins to the search path
    if isMac and getattr(sys, 'frozen', None):
        rd = os.path.abspath(moduleDir + "/../../..")
        QCoreApplication.setLibraryPaths([rd])

    # create the app
    app = AnkiApp(sys.argv)
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

    # qt version must be up to date
    if qtmajor <= 4 and qtminor <= 6:
        QMessageBox.warning(
            None, "Error", "Your Qt version is known to be buggy. Until you "
          "upgrade to a newer Qt, you may experience issues such as images "
          "failing to show up during review.")

    # profile manager
    from aqt.profiles import ProfileManager
    pm = ProfileManager(opts.base, opts.profile)

    # i18n
    setupLang(pm, app, opts.lang)

    # remaining pm init
    pm.ensureProfile()

    # load the main window
    import aqt.main
    mw = aqt.main.AnkiQt(app, pm, args)
    app.exec_()

if __name__ == "__main__":
    run()
