# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# Profile handling
##########################################################################
# - Saves in pickles rather than json to easily store Qt window state.
# - Saves in sqlite rather than a flat file so the config can't be corrupted

import random
import pickle
import shutil
import io
import locale
import re

from aqt.qt import *
from anki.db import DB
from anki.utils import isMac, isWin, intTime
import anki.lang
from aqt.utils import showWarning
from aqt import appHelpSite
import aqt.forms
from send2trash import send2trash
import anki.sound
from anki.lang import _

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
    mainWindowGeom=None,
    mainWindowState=None,
    numBackups=50,
    lastOptimize=intTime(),
    # editing
    fullSearch=False,
    searchHistory=[],
    lastColour="#00f",
    stripHTML=True,
    pastePNG=False,
    # not exposed in gui
    deleteMedia=False,
    preserveKeyboard=True,
    # syncing
    syncKey=None,
    syncMedia=True,
    autoSync=True,
    # importing
    allowHTML=False,
    importMode=1,
)

class ProfileManager:

    def __init__(self, base=None):
        self.name = None
        self.db = None
        # instantiate base folder
        self._setBaseFolder(base)

        anki.sound.setMpvConfigBase(self.base)

    def setupMeta(self):
        # load metadata
        self.firstRun = self._loadMeta()

    # profile load on startup
    def openProfile(self, profile):
        if profile:
            if profile not in self.profiles():
                QMessageBox.critical(None, "Error", "Requested profile does not exist.")
                sys.exit(1)
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
Anki could not create the folder %s. Please ensure that location is not \
read-only and you have permission to write to it. If you cannot fix this \
issue, please see the documentation for information on running Anki from \
a flash drive.""" % self.base)
            raise

    # Folder migration
    ######################################################################

    def _oldFolderLocation(self):
        if isMac:
            return os.path.expanduser("~/Documents/Anki")
        elif isWin:
            from aqt.winpaths import get_personal
            return os.path.join(get_personal(), "Anki")
        else:
            p = os.path.expanduser("~/Anki")
            if os.path.isdir(p):
                return p
            return os.path.expanduser("~/Documents/Anki")

    def maybeMigrateFolder(self):
        oldBase = self._oldFolderLocation()

        if oldBase and not os.path.exists(self.base) and os.path.isdir(oldBase):
            shutil.move(oldBase, self.base)

    # Profile load/save
    ######################################################################

    def profiles(self):
        def names():
            return self.db.list("select name from profiles where name != '_global'")

        n = names()
        if not n:
            self._ensureProfile()
            n = names()

        return n

    def _unpickle(self, data):
        class Unpickler(pickle.Unpickler):
            def find_class(self, module, name):
                if module == "PyQt5.sip":
                    try:
                        import PyQt5.sip # pylint: disable=unused-import
                    except:
                        # use old sip location
                        module = "sip"
                fn = super().find_class(module, name)
                if module == "sip" and name == "_unpickle_type":
                    def wrapper(mod, obj, args):
                        if mod.startswith("PyQt4") and obj == "QByteArray":
                            # can't trust str objects from python 2
                            return QByteArray()
                        return fn(mod, obj, args)
                    return wrapper
                else:
                    return fn
        up = Unpickler(io.BytesIO(data), errors="ignore")
        return up.load()

    def _pickle(self, obj):
        return pickle.dumps(obj, protocol=0)

    def load(self, name):
        assert name != "_global"
        data = self.db.scalar("select cast(data as blob) from profiles where name = ?", name)
        self.name = name
        try:
            self.profile = self._unpickle(data)
        except:
            QMessageBox.warning(
                None, _("Profile Corrupt"), _("""\
Anki could not read your profile data. Window sizes and your sync login \
details have been forgotten."""))

            print("resetting corrupt profile")
            self.profile = profileConf.copy()
            self.save()
        return True

    def save(self):
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, self._pickle(self.profile), self.name)
        self.db.execute(sql, self._pickle(self.meta), "_global")
        self.db.commit()

    def create(self, name):
        prof = profileConf.copy()
        self.db.execute("insert or ignore into profiles values (?, ?)",
                        name, self._pickle(prof))
        self.db.commit()

    def remove(self, name):
        p = self.profileFolder()
        if os.path.exists(p):
            send2trash(p)
        self.db.execute("delete from profiles where name = ?", name)
        self.db.commit()

    def trashCollection(self):
        p = self.collectionPath()
        if os.path.exists(p):
            send2trash(p)

    def rename(self, name):
        oldName = self.name
        oldFolder = self.profileFolder()
        self.name = name
        newFolder = self.profileFolder(create=False)
        if os.path.exists(newFolder):
            if (oldFolder != newFolder) and (
                    oldFolder.lower() == newFolder.lower()):
                # OS is telling us the folder exists because it does not take
                # case into account; use a temporary folder location
                midFolder = ''.join([oldFolder, '-temp'])
                if not os.path.exists(midFolder):
                    os.rename(oldFolder, midFolder)
                    oldFolder = midFolder
                else:
                    showWarning(_("Please remove the folder %s and try again.")
                            % midFolder)
                    self.name = oldName
                    return
            else:
                showWarning(_("Folder already exists."))
                self.name = oldName
                return

        # update name
        self.db.execute("update profiles set name = ? where name = ?",
                        name, oldName)
        # rename folder
        try:
            os.rename(oldFolder, newFolder)
        except Exception as e:
            self.db.rollback()
            if "WinError 5" in str(e):
                showWarning(_("""\
Anki could not rename your profile because it could not rename the profile \
folder on disk. Please ensure you have permission to write to Documents/Anki \
and no other programs are accessing your profile folders, then try again."""))
            else:
                raise
        except:
            self.db.rollback()
            raise
        else:
            self.db.commit()

    # Folder handling
    ######################################################################

    def profileFolder(self, create=True):
        path = os.path.join(self.base, self.name)
        if create:
            self._ensureExists(path)
        return path

    def addonFolder(self):
        return self._ensureExists(os.path.join(self.base, "addons21"))

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

    def _setBaseFolder(self, cmdlineBase):
        if cmdlineBase:
            self.base = os.path.abspath(cmdlineBase)
        elif os.environ.get("ANKI_BASE"):
            self.base = os.path.abspath(os.environ["ANKI_BASE"])
        else:
            self.base = self._defaultBase()
            self.maybeMigrateFolder()
        self.ensureBaseExists()

    def _defaultBase(self):
        if isWin:
            from aqt.winpaths import get_appdata
            return os.path.join(get_appdata(), "Anki2")
        elif isMac:
            return os.path.expanduser("~/Library/Application Support/Anki2")
        else:
            dataDir = os.environ.get(
                "XDG_DATA_HOME", os.path.expanduser("~/.local/share"))
            if not os.path.exists(dataDir):
                os.makedirs(dataDir)
            return os.path.join(dataDir, "Anki2")

    def _loadMeta(self):
        opath = os.path.join(self.base, "prefs.db")
        path = os.path.join(self.base, "prefs21.db")
        if os.path.exists(opath) and not os.path.exists(path):
            shutil.copy(opath, path)

        new = not os.path.exists(path)
        def recover():
            # if we can't load profile, start with a new one
            if self.db:
                try:
                    self.db.close()
                except:
                    pass
            for suffix in ("", "-journal"):
                fpath = path + suffix
                if os.path.exists(fpath):
                    os.unlink(fpath)
            QMessageBox.warning(
                None, "Preferences Corrupt", """\
Anki's prefs21.db file was corrupt and has been recreated. If you were using multiple \
profiles, please add them back using the same names to recover your cards.""")
        try:
            self.db = DB(path)
            assert self.db.scalar("pragma integrity_check") == "ok"
            self.db.execute("""
create table if not exists profiles
(name text primary key, data text not null);""")
            data = self.db.scalar(
                "select cast(data as blob) from profiles where name = '_global'")
        except:
            recover()
            return self._loadMeta()
        if not new:
            # load previously created data
            try:
                self.meta = self._unpickle(data)
                return
            except:
                print("resetting corrupt _global")
        # create a default global profile
        self.meta = metaConf.copy()
        self.db.execute("insert or replace into profiles values ('_global', ?)",
                        self._pickle(metaConf))
        self._setDefaultLang()
        return True

    def _ensureProfile(self):
        "Create a new profile if none exists."
        self.create(_("User 1"))
        p = os.path.join(self.base, "README.txt")
        open(p, "w", encoding="utf8").write(_("""\
This folder stores all of your Anki data in a single location,
to make backups easy. To tell Anki to use a different location,
please see:

%s
""") % (appHelpSite +  "#startupopts"))

    # Default language
    ######################################################################
    # On first run, allow the user to choose the default language

    def _setDefaultLang(self):
        # create dialog
        class NoCloseDiag(QDialog):
            def reject(self):
                pass
        d = self.langDiag = NoCloseDiag()
        f = self.langForm = aqt.forms.setlang.Ui_Dialog()
        f.setupUi(d)
        d.accepted.connect(self._onLangSelected)
        d.rejected.connect(lambda: True)
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
        for c, (name, code) in enumerate(anki.lang.langs):
            if code == "en":
                en = c
            if code == lang:
                idx = c
        # if the system language isn't available, revert to english
        if idx is None:
            idx = en
        # update list
        f.lang.addItems([x[0] for x in anki.lang.langs])
        f.lang.setCurrentRow(idx)
        d.exec_()

    def _onLangSelected(self):
        f = self.langForm
        obj = anki.lang.langs[f.lang.currentRow()]
        code = obj[1]
        name = obj[0]
        en = "Are you sure you wish to display Anki's interface in %s?"
        r = QMessageBox.question(
            None, "Anki", en%name, QMessageBox.Yes | QMessageBox.No,
            QMessageBox.No)
        if r != QMessageBox.Yes:
            return self._setDefaultLang()
        self.setLang(code)

    def setLang(self, code):
        self.meta['defaultLang'] = code
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, self._pickle(self.meta), "_global")
        self.db.commit()
        anki.lang.setLang(code, local=False)

    # OpenGL
    ######################################################################

    def _glPath(self):
        return os.path.join(self.base, "gldriver")

    def glMode(self):
        if isMac:
            return "auto"

        path = self._glPath()
        if not os.path.exists(path):
            return "software"

        mode = open(path, "r").read().strip()

        if mode == "angle" and isWin:
            return mode
        elif mode == "software":
            return mode
        return "auto"

    def setGlMode(self, mode):
        open(self._glPath(), "w").write(mode)

    def nextGlMode(self):
        mode = self.glMode()
        if mode == "software":
            self.setGlMode("auto")
        elif mode == "auto":
            if isWin:
                self.setGlMode("angle")
            else:
                self.setGlMode("software")
        elif mode == "angle":
            self.setGlMode("software")
