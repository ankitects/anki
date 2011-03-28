# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, shutil
from PyQt4.QtCore import *
from PyQt4.QtGui import *

appName="Anki"
appVersion="1.2.8"
appWebsite="http://ankisrs.net/"
appWiki="http://ichi2.net/anki/wiki/"
appHelpSite="http://ankisrs.net/docs/"
appIssueTracker="http://code.google.com/p/anki/issues/list"
appForum="http://groups.google.com/group/ankisrs/topics"
appReleaseNotes="http://ankisrs.net/changes.html"
appDonate="http://ankisrs.net/support/"

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

class SplashScreen(object):

    def __init__(self, max=100):
        self.finished = False
        self.pixmap = QPixmap(":/icons/anki-logo.png")
        self.splash = QSplashScreen(self.pixmap)
        self.prog = QProgressBar(self.splash)
        self.prog.setMaximum(max)
        if QApplication.instance().style().objectName() != "plastique":
            self.style = QStyleFactory.create("plastique")
            self.prog.setStyle(self.style)
        self.prog.setStyleSheet("""* {
color: #ffffff;
background-color: #061824;
margin:0px;
border:0px;
padding: 0px;
text-align: center;}
*::chunk {
color: #13486c;
}
""")
        x = 8
        self.prog.setGeometry(self.splash.width()/10, 8.85*self.splash.height()/10,
                                x*self.splash.width()/10, self.splash.height()/10)
        self.splash.show()
        self.val = 1

    def update(self):
        self.prog.setValue(self.val)
        self.val += 1
        QApplication.instance().processEvents()

    def finish(self, obj):
        self.splash.finish(obj)
        self.finished = True

class AnkiApp(QApplication):

    def event(self, evt):
        from anki.hooks import runHook
        if evt.type() == QEvent.FileOpen:
            runHook("macLoadEvent", unicode(evt.file()))
            return True
        return QApplication.event(self, evt)

def run():
    import config

    mustQuit = False

    # home on win32 is broken
    if sys.platform == "win32":
        # use appdata if available
        if 'APPDATA' in os.environ:
            oldConf = os.path.expanduser("~/.anki/config.db")
            oldPlugins = os.path.expanduser("~/.anki/plugins")
            os.environ['HOME'] = os.environ['APPDATA']
        else:
            oldConf = None
            os.environ['HOME'] = "c:\\anki"
        # make and check accessible
        try:
            os.makedirs(os.path.expanduser("~/.anki"))
        except:
            pass
        try:
            os.listdir(os.path.expanduser("~/.anki"))
        except:
            oldConf = None
            os.environ['HOME'] = "c:\\anki"
        # check accessible again
        try:
            os.makedirs(os.path.expanduser("~/.anki"))
        except:
            pass
        try:
            os.listdir(os.path.expanduser("~/.anki"))
        except:
            mustQuit = True
        if (oldConf and os.path.exists(oldConf) and not os.path.exists(
            oldConf.replace("config.db", "config.db.old"))):
            try:
                shutil.copy2(oldConf,
                             os.path.expanduser("~/.anki/config.db"))
                shutil.copytree(oldPlugins,
                             os.path.expanduser("~/.anki/plugins"))
            except:
                pass
            os.rename(oldConf, oldConf.replace("config.db",
                                               "config.db.old"))
    # setup paths for forms, icons
    sys.path.append(modDir)
    # jpeg module
    rd = runningDir
    if sys.platform.startswith("darwin") and getattr(sys, 'frozen', None):
        rd = os.path.abspath(runningDir + "/../../..")
        QCoreApplication.setLibraryPaths(QStringList([rd]))

    app = AnkiApp(sys.argv)
    QCoreApplication.setApplicationName("Anki")

    if mustQuit:
        QMessageBox.warning(
            None, "Anki", "Can't open APPDATA, nor c:\\anki.\n"
            "Please try removing foreign characters from your username.")
        sys.exit(1)

    import forms
    import ui

    ui.splash = SplashScreen(3)

    import anki
    if anki.version != appVersion:
        print "You have libanki %s, but this is ankiqt %s" % (
            anki.version, appVersion)
        print "\nPlease ensure versions match."
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
    ui.splash.update()

    mw = ui.main.AnkiQt(app, conf, args)

    app.exec_()

if __name__ == "__main__":
    run()
