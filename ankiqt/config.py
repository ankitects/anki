# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

# User configuration handling
##########################################################################

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import os, sys, cPickle, locale, types, shutil

# compatability
def unpickleWxFont(*args):
    pass
def pickleWxFont(*args):
    pass

class Config(dict):

    configDbName = "config.db"

    def __init__(self, configPath):
        self.configPath = configPath
        if sys.platform == "win32":
            if self.configPath.startswith("~"):
                # windows sucks
                self.configPath = "c:\\anki"
        elif sys.platform.startswith("darwin"):
            if self.configPath == os.path.expanduser("~/.anki"):
                oldDb = self.getDbPath()
                self.configPath = os.path.expanduser(
                    "~/Library/Application Support/Anki")
                # upgrade?
                if os.path.exists(oldDb):
                    self.makeAnkiDir()
                    newDb = self.getDbPath()
                    shutil.copytree(oldDb, newDb)
        self.makeAnkiDir()
        self.load()

    def defaults(self):
        fields = {
            'iconSize': 32,
            'syncOnLoad': False,
            'syncOnClose': False,
            'checkForUpdates': True,
            'interfaceLang': "",
            'syncUsername': "",
            'syncPassword': "",
            'showFontPreview': True,
            'showToolbar': True,
            'recentDeckPaths': [],
            'saveAfterAnswer': True,
            'saveAfterAnswerNum': 10,
            'saveAfterAdding': True,
            'saveAfterAddingNum': 3,
            'saveOnClose': True,
            'mainWindowGeom': None,
            'easeButtonHeight': 'standard',
            'suppressUpdate': False,
            'suppressEstimates': False,
            'showLastCardInterval': False,
            'showLastCardContent': False,
            'showTrayIcon': False,
            'showTimer': True,
            'showSuspendedCards': True,
            'simpleToolbar': True,
            }
        for (k,v) in fields.items():
            if not self.has_key(k):
                self[k] = v
        if not self['interfaceLang']:
            # guess interface and target languages
            (lang, enc) = locale.getdefaultlocale()
            self['interfaceLang'] = lang
        self.initFonts()

    def initFonts(self):
        defaultColours = {
                'lastCard': "#0077FF",
                'background': "#FFFFFF",
                'interface': "#000000",
            }
        defaultSizes = {
                'interface': 12,
                'lastCard': 14,
                'edit': 12,
                'other': 24,
            }
        # fonts
        for n in ("interface", "lastCard", "edit"):
            if not self.get(n + "FontFamily", None):
                self[n + "FontFamily"] = "Arial"
                self[n + "FontSize"] = defaultSizes.get(n, defaultSizes['other'])
        # colours
        for n in ("interface", "lastCard", "background"):
            color = n + "Colour"
            if not color in self:
                self[color] = defaultColours[n]

    def getDbPath(self):
        return os.path.join(self.configPath, self.configDbName)

    def makeAnkiDir(self):
        base = self.configPath
        for x in (base,
                  os.path.join(base, "plugins"),
                  os.path.join(base, "backups")):
            try:
                os.mkdir(x)
            except:
                pass

    def save(self):
        path = self.getDbPath()
        # write to a temp file
        from tempfile import mkstemp
        (fd, tmpname) = mkstemp(dir=os.path.dirname(path))
        tmpfile = os.fdopen(fd, 'w')
        cPickle.dump(dict(self), tmpfile)
        tmpfile.close()
        # the write was successful, delete config file (if exists) and rename
        if os.path.exists(path):
            os.unlink(path)
        os.rename(tmpname, path)

    def load(self):
        base = self.configPath
        db = self.getDbPath()
        # load config
        try:
            f = open(db)
            self.update(cPickle.load(f))
        except (IOError, EOFError):
            # config file was corrupted previously
            pass
        self.defaults()
        # fix old recent deck path list
        for n in range(len(self['recentDeckPaths'])):
            s = self['recentDeckPaths'][n]
            if not isinstance(s, types.UnicodeType):
                self['recentDeckPaths'][n] = unicode(s, sys.getfilesystemencoding())
        # fix old locale settings
        if self["interfaceLang"] == "ja":
            self["interfaceLang"]="ja_JP"
        elif self["interfaceLang"] == "fr":
            self["interfaceLang"]="fr_FR"
        elif self["interfaceLang"] == "en":
            self["interfaceLang"]="en_US"
        elif self["interfaceLang"] == "de":
            self["interfaceLang"]="de_DE"
        elif self["interfaceLang"] == "es":
            self["interfaceLang"]="es_ES"
        elif not self["interfaceLang"]:
            self["interfaceLang"]="en_US"
