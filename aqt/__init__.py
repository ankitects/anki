# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys, __builtin__
from aqt.qt import *
import locale, gettext
import anki.lang
from anki.consts import HELP_SITE as appHelpSite

appVersion="2.0-beta13"
appWebsite="http://ankisrs.net/"
appChanges="http://ankisrs.net/docs/dev/changes.html"
appDonate="http://ankisrs.net/support/"
appShared="http://beta.ankiweb.net/shared/"
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
        from aqt import addcards, browser
        self._dialogs = {
            "AddCards": [addcards.AddCards, None],
            "Browser": [browser.Browser, None],
        }

    def open(self, name, *args):
        (creator, instance) = self._dialogs[name]
        if instance:
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
        for (n, (creator, instance)) in self._dialogs.items():
            if instance:
                instance.forceClose = True
                instance.close()
                self.close(n)

dialogs = DialogManager()

# Language handling
##########################################################################
# Qt requires its translator to be installed before any GUI widgets are
# loaded, and we need the Qt language to match the gettext language or
# translated shortcuts will not work.

_gtrans = None
_qtrans = None

def langDir():
    dir = os.path.join(moduleDir,  "aqt", "locale")
    if not os.path.exists(dir):
        dir = os.path.join(os.path.dirname(sys.argv[0]), "locale")
    return dir

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
        'ankiqt', dir, languages=[lang], fallback=True)
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

    def event(self, evt):
        from anki.hooks import runHook
        if evt.type() == QEvent.FileOpen:
            runHook("macLoadEvent", unicode(evt.file()))
            return True
        return QApplication.event(self, evt)

def run():
    global mw
    from anki.utils import isWin, isMac

    # on osx we'll need to add the qt plugins to the search path
    if isMac and getattr(sys, 'frozen', None):
        rd = os.path.abspath(moduleDir + "/../../..")
        QCoreApplication.setLibraryPaths([rd])

    # create the app
    app = AnkiApp(sys.argv)
    QCoreApplication.setApplicationName("Anki")

    # parse args
    import optparse
    parser = optparse.OptionParser()
    parser.usage = "%prog [OPTIONS]"
    parser.add_option("-b", "--base", help="path to base folder")
    parser.add_option("-p", "--profile", help="profile name to load")
    parser.add_option("-l", "--lang", help="interface language (en, de, etc)")
    (opts, args) = parser.parse_args(sys.argv[1:])
    opts.base = unicode(opts.base or "", sys.getfilesystemencoding())
    opts.profile = unicode(opts.profile or "", sys.getfilesystemencoding())

    # profile manager
    from aqt.profiles import ProfileManager
    pm = ProfileManager(opts.base, opts.profile)

    # i18n
    setupLang(pm, app, opts.lang)

    # remaining pm init
    pm.checkPid()
    pm.ensureProfile()

    # load the main window
    import aqt.main
    mw = aqt.main.AnkiQt(app, pm)
    app.exec_()

if __name__ == "__main__":
    run()
