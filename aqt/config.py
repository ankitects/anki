# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

# User configuration handling
##########################################################################
# The majority of the config is serialized into a string, both for easy access
# and backwards compatibility. A separate table keeps track of seen decks, so
# that multiple instances can update the recent decks list.

import os, sys, time, random, cPickle
from anki.db import DB

defaultConf = {
    'confVer': 3,
    # remove?
    'colourTimes': True,
    # too long?
    'deckBrowserRefreshPeriod': 3600,
    'factEditorAdvanced': False,
    'showStudyScreen': True,

    'recentDeckPaths': [],
    'interfaceLang': "en",

    'autoplaySounds': True,
    'checkForUpdates': True,
    'created': time.time(),
    'deleteMedia': False,
    'documentDir': u"",
    'dropboxPublicFolder': u"",
    'editFontFamily': 'Arial',
    'editFontSize': 12,
    'editLineSize': 20,
    'editorReverseOrder': False,
    'iconSize': 32,
    'id': random.randrange(0, 2**63),
    'lastMsg': -1,
    'loadLastDeck': False,
    'mainWindowGeom': None,
    'mainWindowState': None,
    'mediaLocation': "",
    'numBackups': 30,
    'optimizeSmall': False,
    'preserveKeyboard': True,
    'proxyHost': '',
    'proxyPass': '',
    'proxyPort': 8080,
    'proxyUser': '',
    'qaDivider': True,
    'recentColours': ["#000000", "#0000ff"],
    'repeatQuestionAudio': True,
    'scrollToAnswer': True,
    'showCardTimer': True,
    'showProgress': True,
    'showTimer': True,
    'showToolbar': True,
    'showTrayIcon': False,
    'splitQA': True,
    'stripHTML': True,
    'studyOptionsTab': 0,
    'suppressEstimates': False,
    'suppressUpdate': False,
    'syncDisableWhenMoved': True,
    'syncOnLoad': False,
    'syncOnProgramOpen': True,
    'syncPassword': "",
    'syncUsername': "",
}

class Config(object):
    configDbName = "ankiprefs.db"

    def __init__(self, confDir):
        self.confDir = confDir
        self._conf = {}
        if sys.platform.startswith("darwin") and (
            self.confDir == os.path.expanduser("~/.anki")):
            self.confDir = os.path.expanduser(
                    "~/Library/Application Support/Anki")
        self._addAnkiDirs()
        self.load()

    # dict interface
    def get(self, *args):
        return self._conf.get(*args)
    def __getitem__(self, key):
        return self._conf[key]
    def __setitem__(self, key, val):
        self._conf[key] = val
    def __contains__(self, key):
        return self._conf.__contains__(key)

    # load/save
    def load(self):
        path = self._dbPath()
        self.db = DB(path, text=str)
        self.db.executescript("""
create table if not exists decks (path text primary key);
create table if not exists config (conf text not null);
""")
        conf = self.db.scalar("select conf from config")
        if conf:
            self._conf.update(cPickle.loads(conf))
        else:
            self._conf.update(defaultConf)
            # ensure there's something to update
            self.db.execute("insert or ignore into config values ('')")
        self._addDefaults()

    def save(self):
        self.db.execute("update config set conf = ?",
                        cPickle.dumps(self._conf))
        self.db.commit()

    # recent deck support
    def recentDecks(self):
        "Return a list of paths to remembered decks."
        # have to convert to unicode manually because of the text factory
        return [unicode(d[0]) for d in
                self.db.execute("select path from decks")]

    def addRecentDeck(self, path):
        "Add PATH to the list of recent decks if not already. Must be unicode."
        self.db.execute("insert or ignore into decks values (?)",
                        path.encode("utf-8"))

    def delRecentDeck(self, path):
        "Remove PATH from the list if it exists. Must be unicode."
        self.db.execute("delete from decks where path = ?",
                        path.encode("utf-8"))

    # helpers
    def _addDefaults(self):
        if self.get('confVer') >= defaultConf['confVer']:
            return
        for (k,v) in defaultConf.items():
            if k not in self:
                self[k] = v

    def _dbPath(self):
        return os.path.join(self.confDir, self.configDbName)

    def _addAnkiDirs(self):
        base = self.confDir
        for x in (base,
                  os.path.join(base, "plugins"),
                  os.path.join(base, "backups")):
            try:
                os.mkdir(x)
            except:
                pass

    def _importOldData(self):
        # compatability
        def unpickleWxFont(*args):
            pass
        def pickleWxFont(*args):
            pass
