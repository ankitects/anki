# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# Profile handling
##########################################################################
# - Saves in pickles rather than json to easily store Qt window state.
# - Saves in sqlite rather than a flat file so the config can't be corrupted

from aqt.qt import *
import os, sys, time, random, cPickle, re
from anki.db import DB
from anki.utils import isMac, isWin, intTime

metaConf = dict(
    ver=0,
    updates=True,
    created=intTime(),
    id=random.randrange(0, 2**63),
    lastMsg=-1,
    suppressUpdate=False,
)

profileConf = dict(
    # profile
    key=None,
    mainWindowGeom=None,
    mainWindowState=None,
    numBackups=30,
    lang="en",

    # editing
    fullSearch=False,
    searchHistory=[],
    recentColours=["#000000", "#0000ff"],
    stripHTML=True,
    editFontFamily='Arial',
    editFontSize=12,
    editLineSize=20,
    deleteMedia=False,
    preserveKeyboard=True,

    # reviewing
    autoplay=True,
    showDueTimes=True,
    showProgress=True,

    # syncing
    syncKey=None,
    proxyHost='',
    proxyPass='',
    proxyPort=8080,
    proxyUser='',
)

class ProfileManager(object):

    def __init__(self, base=None, profile=None):
        self.name = None
        # instantiate base folder
        if not base:
            base = self._defaultBase()
        if not os.path.exists(base):
            try:
                os.makedirs(base)
            except:
                QMessageBox.critical(
                    None, "Error", """\
Anki can't write to the harddisk. Please see the \
documentation for information on using a flash drive.""")
                raise
        self.base = base
        # load database and cmdline-provided profile
        self._load()
        if profile:
            try:
                self.load(profile)
            except TypeError:
                raise Exception("Provided profile does not exist.")

    # Profile load/save
    ######################################################################

    def profiles(self):
        return [x for x in self.db.scalar("select name from profiles")
                if x != "_global"]

    def load(self, name):
        self.name = name
        self.idself.profile = cPickle.loads(
            self.db.scalar("select oid, data from profiles where name = ?", name))

    def save(self):
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, cPickle.dumps(self.profile), self.name)
        self.db.execute(sql, cPickle.dumps(self.meta, "_global"))

    def create(self, name):
        assert re.match("^[A-Za-z0-9 ]+$", name)
        self.db.execute("insert into profiles values (?, ?)",
                        name, cPickle.dumps(profileConf))

    # Folder handling
    ######################################################################

    def profileFolder(self):
        return self._ensureExists(os.path.join(self.base, self.name))

    def addonFolder(self):
        return self._ensureExists(os.path.join(self.base, "addons"))

    def backupFolder(self):
        return self._ensureExists(
            os.path.join(self.profileFolder(), "backups"))

    def collectionPath(self):
        return os.path.join(self.profileFolder(), "collection.anki2")

    # Helpers
    ######################################################################

    def _ensureExists(self, path):
        if not exists(path):
            os.makedirs(path)
        return path

    def _defaultBase(self):
        if isWin:
            s = QSettings(QSettings.UserScope, "Microsoft", "Windows")
            s.beginGroup("CurrentVersion/Explorer/Shell Folders")
            d = s.value("Personal")
            return os.path.join(d, "Anki")
        elif isMac:
            return os.path.expanduser("~/Documents/Anki")
        else:
            return os.path.expanduser("~/Anki")

    def _load(self):
        path = os.path.join(self.base, "prefs.db")
        self.db = DB(path, text=str)
        self.db.execute("""
create table if not exists profiles
(name text primary key, data text not null);""")
        try:
            self.meta = self.loadProfile("_global")
        except:
            # create a default global profile
            self.meta = metaConf.copy()
            self.db.execute("insert into profiles values ('_global', ?)",
                            cPickle.dumps(metaConf))
            # and save a default user profile for later (commits)
            self.create("User 1")
