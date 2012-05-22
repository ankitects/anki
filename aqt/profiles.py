# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# Profile handling
##########################################################################
# - Saves in pickles rather than json to easily store Qt window state.
# - Saves in sqlite rather than a flat file so the config can't be corrupted

from aqt.qt import *
import os, sys, time, random, cPickle, shutil, locale, re, atexit, urllib
from anki.db import DB
from anki.utils import isMac, isWin, intTime, checksum
from anki.lang import langs
from aqt.utils import showWarning, fontForPlatform
import anki.sync
import aqt.forms

metaConf = dict(
    ver=0,
    updates=True,
    created=intTime(),
    id=random.randrange(0, 2**63),
    lastMsg=-1,
    suppressUpdate=False,
    firstRun=True,
    defaultLang=None,
    disabledAddons=[],
)

profileConf = dict(
    # profile
    key=None,
    mainWindowGeom=None,
    mainWindowState=None,
    numBackups=30,
    lastOptimize=intTime(),

    # editing
    fullSearch=False,
    searchHistory=[],
    lastColour="#00f",
    stripHTML=True,
    editFontFamily=fontForPlatform(),
    editFontSize=12,
    editLineSize=20,
    pastePNG=False,
    # not exposed in gui
    deleteMedia=False,
    preserveKeyboard=True,

    # syncing
    syncKey=None,
    syncMedia=True,
    autoSync=True,
)

class ProfileManager(object):

    def __init__(self, base=None, profile=None):
        self.name = None
        # instantiate base folder
        self.base = base or self._defaultBase()
        self.ensureBaseExists()
        # load metadata
        self.firstRun = self._loadMeta()
        # did the user request a profile to start up with?
        if profile:
            try:
                self.load(profile)
            except TypeError:
                raise Exception("Provided profile does not exist.")

    # Base creation
    ######################################################################

    def ensureBaseExists(self):
        try:
            self._ensureExists(self.base)
        except:
            # can't translate, as lang not initialized
            QMessageBox.critical(
                None, "Error", """\
Anki can't write to the harddisk. Please see the \
documentation for information on using a flash drive.""")
            raise

    # Pid checking
    ######################################################################

    def checkPid(self):
        p = os.path.join(self.base, "pid")
        # check if an existing instance is running
        if os.path.exists(p):
            pid = int(open(p).read())
            exists = False
            if isWin:
                # no posix on windows, sigh
                from win32process import EnumProcesses as enum
                if pid in enum():
                    exists = True
            else:
                try:
                    os.kill(pid, 0)
                    exists = True
                except OSError:
                    pass
            if exists:
                QMessageBox.warning(
                    None, "Error", _("""\
Anki is already running. Please close the existing copy or restart your \
computer."""))
                raise Exception("Already running")
        # write out pid to the file
        open(p, "w").write(str(os.getpid()))
        # add handler to cleanup on exit
        def cleanup():
            os.unlink(p)
        atexit.register(cleanup)

    # Profile load/save
    ######################################################################

    def profiles(self):
        return sorted(
            unicode(x, "utf8") for x in
            self.db.list("select name from profiles")
            if x != "_global")

    def load(self, name, passwd=None):
        prof = cPickle.loads(
            self.db.scalar("select data from profiles where name = ?",
                           name.encode("utf8")))
        if prof['key'] and prof['key'] != self._pwhash(passwd):
            self.name = None
            return False
        if name != "_global":
            self.name = name
            self.profile = prof
        return True

    def save(self):
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, cPickle.dumps(self.profile),
                        self.name.encode("utf8"))
        self.db.execute(sql, cPickle.dumps(self.meta), "_global")
        self.db.commit()

    def create(self, name):
        prof = profileConf.copy()
        self.db.execute("insert into profiles values (?, ?)",
                        name.encode("utf8"), cPickle.dumps(prof))
        self.db.commit()

    def remove(self, name):
        shutil.rmtree(self.profileFolder())
        self.db.execute("delete from profiles where name = ?",
                        name.encode("utf8"))
        self.db.commit()

    def rename(self, name):
        oldFolder = self.profileFolder()
        # update name
        self.db.execute("update profiles set name = ? where name = ?",
                        name.encode("utf8"), self.name.encode("utf-8"))
        # rename folder
        self.name = name
        newFolder = self.profileFolder()
        os.rmdir(newFolder)
        os.rename(oldFolder, newFolder)
        self.db.commit()

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
        if not os.path.exists(path):
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

    def _loadMeta(self):
        path = os.path.join(self.base, "prefs.db")
        new = not os.path.exists(path)
        self.db = DB(path, text=str)
        self.db.execute("""
create table if not exists profiles
(name text primary key, data text not null);""")
        if new:
            # create a default global profile
            self.meta = metaConf.copy()
            self.db.execute("insert into profiles values ('_global', ?)",
                            cPickle.dumps(metaConf))
            self._setDefaultLang()
            return True
        else:
            # load previously created
            self.meta = cPickle.loads(
                self.db.scalar(
                    "select data from profiles where name = '_global'"))

    def ensureProfile(self):
        "Create a new profile if none exists."
        if self.firstRun:
            self.create(_("User 1"))

    def _pwhash(self, passwd):
        return checksum(unicode(self.meta['id'])+unicode(passwd))

    # Default language
    ######################################################################
    # On first run, allow the user to choose the default language

    def _setDefaultLang(self):
        # the dialog expects _ to be defined, but we're running before
        # setupLang() has been called. so we create a dummy op for now
        import __builtin__
        __builtin__.__dict__['_'] = lambda x: x
        # create dialog
        class NoCloseDiag(QDialog):
            def reject(self):
                pass
        d = self.langDiag = NoCloseDiag()
        f = self.langForm = aqt.forms.setlang.Ui_Dialog()
        f.setupUi(d)
        d.connect(d, SIGNAL("accepted()"), self._onLangSelected)
        d.connect(d, SIGNAL("rejected()"), lambda: True)
        # default to the system language
        try:
            (lang, enc) = locale.getdefaultlocale()
        except:
            # fails on osx
            lang = "en"
        if lang and lang not in ("pt_BR", "zh_CN", "zh_TW"):
            lang = re.sub("(.*)_.*", "\\1", lang)
        # find index
        idx = None
        en = None
        for c, (name, code) in enumerate(langs):
            if code == "en":
                en = c
            if code == lang:
                idx = c
        # if the system language isn't available, revert to english
        if idx is None:
            idx = en
        # update list
        f.lang.addItems([x[0] for x in langs])
        f.lang.setCurrentRow(idx)
        d.exec_()

    def _onLangSelected(self):
        f = self.langForm
        code = langs[f.lang.currentRow()][1]
        self.meta['defaultLang'] = code
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, cPickle.dumps(self.meta), "_global")
        self.db.commit()
