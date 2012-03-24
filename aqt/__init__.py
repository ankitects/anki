# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys
from aqt.qt import *

appVersion="2.0-alpha9"
appWebsite="http://ankisrs.net/"
appHelpSite="http://ankisrs.net/docs/dev/manual.html"
appChanges="http://ankisrs.net/docs/dev/changes.html"
appDonate="http://ankisrs.net/support/"
appShared="http://beta.ankiweb.net/shared/"
mw = None # set on init

moduleDir = os.path.split(os.path.dirname(os.path.abspath(__file__)))[0]

# py2exe
# if hasattr(sys, "frozen"):
#     sys.path.append(moduleDir)

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
    parser.add_option("-b", "--base", help="Path to base folder")
    parser.add_option("-p", "--profile", help="Profile name to load")
    (opts, args) = parser.parse_args(sys.argv[1:])

    # profile manager
    from aqt.profiles import ProfileManager
    pm = ProfileManager(opts.base, opts.profile)

    # qt translations
    translationPath = ''
    if False: # not isWin and not isMac:
        translationPath = "/usr/share/qt4/translations/"
        long = conf['interfaceLang']
        short = long.split('_')[0]
        qtTranslator = QTranslator()
        if qtTranslator.load("qt_" + long, translationPath) or \
               qtTranslator.load("qt_" + short, translationPath):
            app.installTranslator(qtTranslator)

    import aqt.main
    mw = aqt.main.AnkiQt(app, pm)
    app.exec_()

if __name__ == "__main__":
    run()
