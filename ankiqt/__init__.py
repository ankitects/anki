# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, optparse, re
from PyQt4.QtCore import *
from PyQt4.QtGui import *

appName="Anki"
appVersion="0.9.8.9"
appWebsite="http://ichi2.net/anki/download/"
appWiki="http://ichi2.net/anki/wiki/"
appHelpSite="http://ichi2.net/anki/wiki/AnkiWiki"
appIssueTracker="http://code.google.com/p/anki/issues/list"
appForum="http://groups.google.com/group/ankisrs/topics"
appReleaseNotes="http://ichi2.net/anki/download/index.html#changes"
appMoreDecks="http://ichi2.net/anki/wiki/ExtraDecks"

modDir=os.path.dirname(os.path.abspath(__file__))
runningDir=os.path.split(modDir)[0]
# py2exe
if hasattr(sys, "frozen"):
    sys.path.append(modDir)
    modDir = os.path.dirname(sys.argv[0])

# we bundle icons_rc as part of the anki source
sys.path.append(os.path.dirname(__file__))
import forms
import config
import ui

# App initialisation
##########################################################################

def run():
    # put anki home in c:\anki on win32 if not available
    if sys.platform == "win32":
        path = os.path.expanduser("~")
        if path[0] == "~" or not os.access(path, os.R_OK | os.W_OK):
            os.environ['HOME'] = "c:\\anki"
            try:
                os.mkdir("c:\\anki")
            except OSError:
                pass

    app = QApplication(sys.argv)
    if sys.platform.startswith("win32"):
        app.setStyle("plastique")

    # setup paths for forms, icons
    sys.path.append(modDir)
    # jpeg module
    rd = runningDir
    if sys.platform.startswith("darwin"):
        rd = os.path.abspath(runningDir + "/../../..")
        QCoreApplication.setLibraryPaths(QStringList([rd]))
    else:
        QCoreApplication.addLibraryPath(runningDir)

    import anki
    if anki.version != appVersion:
        print "You have libanki %s, but this is ankiqt %s" % (
            anki.version, appVersion)
        print "\nPlease install the latest libanki."
        return

    # parse args
    parser = optparse.OptionParser()
    parser.usage = "%prog [<deck.anki>]"
    parser.add_option("-c", "--config", help="path to config dir",
                      default=os.path.expanduser("~/.anki"))
    (opts, args) = parser.parse_args(sys.argv[1:])

    # configuration
    import ankiqt.config
    conf = ankiqt.config.Config(
        unicode(os.path.abspath(opts.config), sys.getfilesystemencoding()))

    # backups
    from anki import DeckStorage
    DeckStorage.backupDir = os.path.join(conf.configPath,
                                         "backups")

    # qt translations
    translationPath = ''
    if 'linux' in sys.platform or 'unix' in sys.platform:
        translationPath = "/usr/share/qt4/translations/"
    if translationPath:
        long = conf['interfaceLang']
        if long == "ja_JP":
            # qt is inconsistent
            long = long.lower()
        short = long.split('_')[0]
        qtTranslator = QTranslator()
        if qtTranslator.load("qt_" + long, translationPath) or \
               qtTranslator.load("qt_" + short, translationPath):
            app.installTranslator(qtTranslator)

    # load main window
    ui.importAll()
    ui.dialogs.registerDialogs()
    mw = ui.main.AnkiQt(app, conf, args)
    try:
        styleFile = open(opts.config + ".css")
        mw.setStyleSheet(styleFile.read())
    except (IOError, OSError):
        pass

    app.exec_()

if __name__ == "__main__":
    run()
