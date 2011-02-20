# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

# User configuration handling
##########################################################################

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import os, sys, cPickle, locale, types, shutil, time, re
from anki.utils import genID

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
                if (not os.path.exists(self.configPath) and
                    os.path.exists(oldDb)):
                    self.makeAnkiDir()
                    newDb = self.getDbPath()
                    shutil.copy2(oldDb, newDb)
        self.makeAnkiDir()
        self.load()

    def defaults(self):
        fields = {
            'addZeroSpace': False,
            'alternativeTheme': False,
            'autoplaySounds': True,
            'checkForUpdates': True,
            'colourTimes': True,
            'created': time.time(),
            'deckBrowserNameLength': 30,
            'deckBrowserOrder': 0,
            'deckBrowserRefreshPeriod': 3600,
            'deleteMedia': False,
            'documentDir': u"",
            'dropboxPublicFolder': u"",
            'editFontFamily': 'Arial',
            'editFontSize': 12,
            'editLineSize': 20,
            'editorReverseOrder': False,
            'extraNewCards': 5,
            'factEditorAdvanced': False,
            'forceLTR': False,
            'iconSize': 32,
            'id': genID(),
            'interfaceLang': "",
            'lastMsg': -1,
            'loadLastDeck': False,
            'mainWindowGeom': None,
            'mainWindowState': None,
            # one of empty, 'dropbox', or path used as prefix
            'mediaLocation': "",
            'mainWindowState': None,
            'numBackups': 30,
            'optimizeSmall': False,
            'preserveKeyboard': True,
            'preventEditUntilAnswer': False,
            'proxyHost': '',
            'proxyPass': '',
            'proxyPort': 8080,
            'proxyUser': '',
            'qaDivider': True,
            'randomizeOnCram': True,
            'recentColours': ["#000000", "#0000ff"],
            'recentDeckPaths': [],
            'repeatQuestionAudio': True,
            'saveAfterAdding': True,
            'saveAfterAddingNum': 1,
            'saveAfterAnswer': True,
            'saveAfterAnswerNum': 10,
            'saveOnClose': True,
            'scrollToAnswer': True,
            'showCardTimer': True,
            'showFontPreview': False,
            'showLastCardContent': False,
            'showLastCardInterval': False,
            'showProgress': True,
            'showStudyScreen': True,
            'showStudyStats': True,
            'showTimer': True,
            'showToolbar': True,
            'showTrayIcon': False,
            'sortIndex': 0,
            'splitQA': True,
            'standaloneWindows': True,
            'stripHTML': True,
            'studyOptionsScreen': 0,
            'suppressEstimates': False,
            'suppressUpdate': False,
            'syncDisableWhenMoved': True,
            'syncInMsgBox': False,
            'syncOnLoad': False,
            'syncOnProgramOpen': True,
            'syncPassword': "",
            'syncUsername': "",
            }
        # disable sync on deck load when upgrading
        if not self.has_key("syncOnProgramOpen"):
            self['syncOnLoad'] = False
            self['syncOnClose'] = False
        for (k,v) in fields.items():
            if not self.has_key(k):
                self[k] = v
        if not self['interfaceLang']:
            # guess interface and target languages
            (lang, enc) = locale.getdefaultlocale()
            self['interfaceLang'] = lang

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

    def fixLang(self, lang):
        if lang and lang not in ("pt_BR", "zh_CN", "zh_TW"):
            lang = re.sub("(.*)_.*", "\\1", lang)
        if not lang:
            lang = "en"
        return lang

    def load(self):
        base = self.configPath
        db = self.getDbPath()
        # load config
        try:
            f = open(db)
            self.update(cPickle.load(f))
        except:
            # config file was corrupted previously
            pass
        self.defaults()
        # fix old recent deck path list
        for n in range(len(self['recentDeckPaths'])):
            s = self['recentDeckPaths'][n]
            if not isinstance(s, types.UnicodeType):
                self['recentDeckPaths'][n] = unicode(s, sys.getfilesystemencoding())
        # fix locale settings
        self["interfaceLang"] = self.fixLang(self["interfaceLang"])
