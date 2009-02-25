# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, shutil
from PyQt4.QtCore import *
from PyQt4.QtGui import *

appName="Anki"
appVersion="0.9.9.6"
appWebsite="http://ichi2.net/anki/download/"
appWiki="http://ichi2.net/anki/wiki/"
appHelpSite="http://ichi2.net/anki/wiki/AnkiWiki"
appIssueTracker="http://code.google.com/p/anki/issues/list"
appForum="http://groups.google.com/group/ankisrs/topics"
appReleaseNotes="http://ichi2.net/anki/download/index.html#changes"
appMoreDecks="http://ichi2.net/anki/wiki/PreMadeDecks"
appDonate="http://ichi2.net/anki/donate.html"

modDir=os.path.dirname(os.path.abspath(__file__))
runningDir=os.path.split(modDir)[0]
# py2exe
if hasattr(sys, "frozen"):
    sys.path.append(modDir)
    modDir = os.path.dirname(sys.argv[0])

# we bundle icons_rc as part of the anki source
sys.path.append(os.path.dirname(__file__))

# App initialisation
##########################################################################

def run():
    import config

    # home on win32 is broken
    if sys.platform == "win32":
        if 'APPDATA' in os.environ:
            oldConf = os.path.expanduser("~/.anki/config.db")
            oldPlugins = os.path.expanduser("~/.anki/plugins")
            os.environ['HOME'] = os.environ['APPDATA']
        else:
            oldConf = None
            os.environ['HOME'] = "c:\\anki"
        try:
            os.makedirs(os.path.expanduser("~/.anki"))
        except OSError:
            pass
        if os.path.exists(oldConf):
            shutil.copy2(oldConf,
                         os.path.expanduser("~/.anki/config.db"))
            shutil.copytree(oldPlugins,
                         os.path.expanduser("~/.anki/plugins"))
            os.rename(oldConf, oldConf.replace("config.db", "config.db.old"))

    app = QApplication(sys.argv)

    # Create a pixmap - not needed if you have your own.
    import forms
    import ui
    pixmap = QPixmap(":/icons/anki-logo.png")
    ui.splash = QSplashScreen(pixmap)
    ui.splash.show()


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
    import optparse
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

    if conf['alternativeTheme']:
        app.setStyle("plastique")

    # load main window
    ui.importAll()

    ui.dialogs.registerDialogs()
    mw = ui.main.AnkiQt(app, conf, args)
    try:
        styleFile = open(os.path.join(opts.config, "style.css"))
        mw.setStyleSheet(styleFile.read())
    except (IOError, OSError):
        pass

    app.exec_()

if __name__ == "__main__":
    run()
