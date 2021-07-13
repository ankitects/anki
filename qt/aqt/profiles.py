# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import io
import pickle
import random
import shutil
import traceback
import warnings
from enum import Enum
from typing import Any, Dict, List, Optional

from send2trash import send2trash

import anki.lang
import aqt.forms
import aqt.sound
from anki.collection import Collection
from anki.db import DB
from anki.lang import without_unicode_isolation
from anki.sync import SyncAuth
from anki.utils import intTime, isMac, isWin
from aqt import appHelpSite
from aqt.qt import *
from aqt.utils import disable_help_button, showWarning, tr

# Profile handling
##########################################################################
# - Saves in pickles rather than json to easily store Qt window state.
# - Saves in sqlite rather than a flat file so the config can't be corrupted


class RecordingDriver(Enum):
    PyAudio = "PyAudio"
    QtAudioInput = "Qt"


class VideoDriver(Enum):
    OpenGL = "auto"
    ANGLE = "angle"
    Software = "software"

    @staticmethod
    def default_for_platform() -> VideoDriver:
        if isMac:
            return VideoDriver.OpenGL
        else:
            return VideoDriver.Software

    def constrained_to_platform(self) -> VideoDriver:
        if self == VideoDriver.ANGLE and not isWin:
            return VideoDriver.Software
        return self

    def next(self) -> VideoDriver:
        if self == VideoDriver.Software:
            return VideoDriver.OpenGL
        elif self == VideoDriver.OpenGL and isWin:
            return VideoDriver.ANGLE
        else:
            return VideoDriver.Software

    @staticmethod
    def all_for_platform() -> List[VideoDriver]:
        all = [VideoDriver.OpenGL]
        if isWin:
            all.append(VideoDriver.ANGLE)
        all.append(VideoDriver.Software)
        return all


metaConf = dict(
    ver=0,
    updates=True,
    created=intTime(),
    id=random.randrange(0, 2 ** 63),
    lastMsg=-1,
    suppressUpdate=False,
    firstRun=True,
    defaultLang=None,
)

profileConf: Dict[str, Any] = dict(
    # profile
    mainWindowGeom=None,
    mainWindowState=None,
    numBackups=50,
    lastOptimize=intTime(),
    # editing
    searchHistory=[],
    lastColour="#00f",
    # syncing
    syncKey=None,
    syncMedia=True,
    autoSync=True,
    # importing
    allowHTML=False,
    importMode=1,
    # these are not used, but Anki 2.1.42 and below
    # expect these keys to exist
    stripHTML=True,
    deleteMedia=False,
)


class LoadMetaResult:
    firstTime: bool
    loadError: bool


class AnkiRestart(SystemExit):
    def __init__(self, exitcode: int = 0) -> None:
        self.exitcode = exitcode
        super().__init__()


class ProfileManager:
    def __init__(self, base: Optional[str] = None) -> None:  #
        ## Settings which should be forgotten each Anki restart
        self.session: Dict[str, Any] = {}
        self.name: Optional[str] = None
        self.db: Optional[DB] = None
        self.profile: Optional[Dict] = None
        # instantiate base folder
        self.base: str
        self._setBaseFolder(base)

    def setupMeta(self) -> LoadMetaResult:
        # load metadata
        res = self._loadMeta()
        self.firstRun = res.firstTime
        return res

    # profile load on startup
    def openProfile(self, profile: str) -> None:
        if profile:
            if profile not in self.profiles():
                QMessageBox.critical(
                    None, tr.qt_misc_error(), tr.profiles_profile_does_not_exist()
                )
                sys.exit(1)
            try:
                self.load(profile)
            except TypeError as exc:
                raise Exception("Provided profile does not exist.") from exc

    # Base creation
    ######################################################################

    def ensureBaseExists(self) -> None:
        self._ensureExists(self.base)

    # Folder migration
    ######################################################################

    def _oldFolderLocation(self) -> str:
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

    def maybeMigrateFolder(self) -> None:
        newBase = self.base
        oldBase = self._oldFolderLocation()

        if oldBase and not os.path.exists(self.base) and os.path.isdir(oldBase):
            try:
                # if anything goes wrong with UI, reset to the old behavior of always migrating
                self._tryToMigrateFolder(oldBase)
            except AnkiRestart:
                raise
            except:
                print("migration failed")
                self.base = newBase
                shutil.move(oldBase, self.base)

    def _tryToMigrateFolder(self, oldBase: str) -> None:
        from PyQt5 import QtGui, QtWidgets

        app = QtWidgets.QApplication([])
        icon = QtGui.QIcon()
        icon.addPixmap(
            QtGui.QPixmap(":/icons/anki.png"),
            QtGui.QIcon.Normal,
            QtGui.QIcon.Off,
        )
        window_title = "Data Folder Migration"
        migration_directories = f"\n\n    {oldBase}\n\nto\n\n    {self.base}"

        confirmation = QMessageBox()
        confirmation.setIcon(QMessageBox.Warning)
        confirmation.setWindowIcon(icon)
        confirmation.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)  # type: ignore
        confirmation.setWindowTitle(window_title)
        confirmation.setText(
            "Anki needs to move its data folder from Documents/Anki to a new location. Proceed?"
        )
        retval = confirmation.exec_()

        if retval == QMessageBox.Ok:
            progress = QMessageBox()
            progress.setIcon(QMessageBox.Information)
            progress.setStandardButtons(QMessageBox.NoButton)
            progress.setWindowIcon(icon)
            progress.setWindowTitle(window_title)
            progress.setText("Please wait...")
            progress.show()
            app.processEvents()  # type: ignore

            shutil.move(oldBase, self.base)
            progress.hide()

            completion = QMessageBox()
            completion.setIcon(QMessageBox.Information)
            completion.setStandardButtons(QMessageBox.Ok)
            completion.setWindowIcon(icon)
            completion.setWindowTitle(window_title)
            completion.setText("Migration complete. Please start Anki again.")
            completion.show()
            completion.exec_()
        else:
            diag = QMessageBox()
            diag.setIcon(QMessageBox.Warning)
            diag.setWindowIcon(icon)
            diag.setStandardButtons(QMessageBox.Ok)
            diag.setWindowTitle(window_title)
            diag.setText(
                "Migration aborted. If you would like to keep the old folder location, please "
                "see the Startup Options section of the manual. Anki will now quit."
            )
            diag.exec_()

        raise AnkiRestart(exitcode=0)

    # Profile load/save
    ######################################################################

    def profiles(self) -> List:
        def names() -> List:
            return self.db.list("select name from profiles where name != '_global'")

        n = names()
        if not n:
            self._ensureProfile()
            n = names()

        return n

    def _unpickle(self, data: bytes) -> Any:
        class Unpickler(pickle.Unpickler):
            def find_class(self, module: str, name: str) -> Any:
                if module == "PyQt5.sip":
                    try:
                        import PyQt5.sip  # pylint: disable=unused-import
                    except:
                        # use old sip location
                        module = "sip"
                fn = super().find_class(module, name)
                if module == "sip" and name == "_unpickle_type":

                    def wrapper(mod, obj, args) -> Any:  # type: ignore
                        if mod.startswith("PyQt4") and obj == "QByteArray":
                            # can't trust str objects from python 2
                            return QByteArray()
                        return fn(mod, obj, args)

                    return wrapper
                else:
                    return fn

        up = Unpickler(io.BytesIO(data), errors="ignore")
        return up.load()

    def _pickle(self, obj: Any) -> bytes:
        # pyqt needs to be updated to fix
        # 'PY_SSIZE_T_CLEAN will be required for '#' formats' warning
        # check if this is still required for pyqt6
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            return pickle.dumps(obj, protocol=4)

    def load(self, name: str) -> bool:
        assert name != "_global"
        data = self.db.scalar(
            "select cast(data as blob) from profiles where name = ?", name
        )
        self.name = name
        try:
            self.profile = self._unpickle(data)
        except:
            QMessageBox.warning(
                None,
                tr.profiles_profile_corrupt(),
                tr.profiles_anki_could_not_read_your_profile(),
            )
            traceback.print_stack()
            print("resetting corrupt profile")
            self.profile = profileConf.copy()
            self.save()
        return True

    def save(self) -> None:
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, self._pickle(self.profile), self.name)
        self.db.execute(sql, self._pickle(self.meta), "_global")
        self.db.commit()

    def create(self, name: str) -> None:
        prof = profileConf.copy()
        self.db.execute(
            "insert or ignore into profiles values (?, ?)", name, self._pickle(prof)
        )
        self.db.commit()

    def remove(self, name: str) -> None:
        p = self.profileFolder()
        if os.path.exists(p):
            send2trash(p)
        self.db.execute("delete from profiles where name = ?", name)
        self.db.commit()

    def trashCollection(self) -> None:
        p = self.collectionPath()
        if os.path.exists(p):
            send2trash(p)

    def rename(self, name: str) -> None:
        oldName = self.name
        oldFolder = self.profileFolder()
        self.name = name
        newFolder = self.profileFolder(create=False)
        if os.path.exists(newFolder):
            if (oldFolder != newFolder) and (oldFolder.lower() == newFolder.lower()):
                # OS is telling us the folder exists because it does not take
                # case into account; use a temporary folder location
                midFolder = "".join([oldFolder, "-temp"])
                if not os.path.exists(midFolder):
                    os.rename(oldFolder, midFolder)
                    oldFolder = midFolder
                else:
                    showWarning(tr.profiles_please_remove_the_folder_and(val=midFolder))
                    self.name = oldName
                    return
            else:
                showWarning(tr.profiles_folder_already_exists())
                self.name = oldName
                return

        # update name
        self.db.execute("update profiles set name = ? where name = ?", name, oldName)
        # rename folder
        try:
            os.rename(oldFolder, newFolder)
        except Exception as e:
            self.db.rollback()
            if "WinError 5" in str(e):
                showWarning(tr.profiles_anki_could_not_rename_your_profile())
            else:
                raise
        except:
            self.db.rollback()
            raise
        else:
            self.db.commit()

    # Folder handling
    ######################################################################

    def profileFolder(self, create: bool = True) -> str:
        path = os.path.join(self.base, self.name)
        if create:
            self._ensureExists(path)
        return path

    def addonFolder(self) -> str:
        return self._ensureExists(os.path.join(self.base, "addons21"))

    def backupFolder(self) -> str:
        return self._ensureExists(os.path.join(self.profileFolder(), "backups"))

    def collectionPath(self) -> str:
        return os.path.join(self.profileFolder(), "collection.anki2")

    # Downgrade
    ######################################################################

    def downgrade(self, profiles: List[str]) -> List[str]:
        "Downgrade all profiles. Return a list of profiles that couldn't be opened."
        problem_profiles = []
        for name in profiles:
            path = os.path.join(self.base, name, "collection.anki2")
            if not os.path.exists(path):
                continue
            with DB(path) as db:
                if db.scalar("select ver from col") == 11:
                    # nothing to do
                    continue
            try:
                c = Collection(path)
                c.close(save=False, downgrade=True)
            except Exception as e:
                print(e)
                problem_profiles.append(name)
        return problem_profiles

    # Helpers
    ######################################################################

    def _ensureExists(self, path: str) -> str:
        if not os.path.exists(path):
            os.makedirs(path)
        return path

    def _setBaseFolder(self, cmdlineBase: Optional[str]) -> None:
        if cmdlineBase:
            self.base = os.path.abspath(cmdlineBase)
        elif os.environ.get("ANKI_BASE"):
            self.base = os.path.abspath(os.environ["ANKI_BASE"])
        else:
            self.base = self._defaultBase()
            self.maybeMigrateFolder()
        self.ensureBaseExists()

    def _defaultBase(self) -> str:
        if isWin:
            from aqt.winpaths import get_appdata

            return os.path.join(get_appdata(), "Anki2")
        elif isMac:
            return os.path.expanduser("~/Library/Application Support/Anki2")
        else:
            dataDir = os.environ.get(
                "XDG_DATA_HOME", os.path.expanduser("~/.local/share")
            )
            if not os.path.exists(dataDir):
                os.makedirs(dataDir)
            return os.path.join(dataDir, "Anki2")

    def _loadMeta(self, retrying: bool = False) -> LoadMetaResult:
        result = LoadMetaResult()
        result.firstTime = False
        result.loadError = retrying

        opath = os.path.join(self.base, "prefs.db")
        path = os.path.join(self.base, "prefs21.db")
        if not retrying and os.path.exists(opath) and not os.path.exists(path):
            shutil.copy(opath, path)

        result.firstTime = not os.path.exists(path)

        def recover() -> None:
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

        # open DB file and read data
        try:
            self.db = DB(path)
            assert self.db.scalar("pragma integrity_check") == "ok"
            self.db.execute(
                """
create table if not exists profiles
(name text primary key, data blob not null);"""
            )
            data = self.db.scalar(
                "select cast(data as blob) from profiles where name = '_global'"
            )
        except:
            traceback.print_stack()
            if result.loadError:
                # already failed, prevent infinite loop
                raise
            # delete files and try again
            recover()
            return self._loadMeta(retrying=True)

        # try to read data
        if not result.firstTime:
            try:
                self.meta = self._unpickle(data)
                return result
            except:
                traceback.print_stack()
                print("resetting corrupt _global")
                result.loadError = True
                result.firstTime = True

        # if new or read failed, create a default global profile
        self.meta = metaConf.copy()
        self.db.execute(
            "insert or replace into profiles values ('_global', ?)",
            self._pickle(metaConf),
        )
        return result

    def _ensureProfile(self) -> None:
        "Create a new profile if none exists."
        self.create(tr.profiles_user_1())
        p = os.path.join(self.base, "README.txt")
        with open(p, "w", encoding="utf8") as file:
            file.write(
                without_unicode_isolation(
                    tr.profiles_folder_readme(
                        link=f"{appHelpSite}files#startup-options",
                    )
                )
            )

    # Default language
    ######################################################################
    # On first run, allow the user to choose the default language

    def setDefaultLang(self, idx: int) -> None:
        # create dialog
        class NoCloseDiag(QDialog):
            def reject(self) -> None:
                pass

        d = self.langDiag = NoCloseDiag()
        f = self.langForm = aqt.forms.setlang.Ui_Dialog()
        f.setupUi(d)
        disable_help_button(d)
        qconnect(d.accepted, self._onLangSelected)
        qconnect(d.rejected, lambda: True)
        # update list
        f.lang.addItems([x[0] for x in anki.lang.langs])
        f.lang.setCurrentRow(idx)
        d.exec_()

    def _onLangSelected(self) -> None:
        f = self.langForm
        obj = anki.lang.langs[f.lang.currentRow()]
        code = obj[1]
        name = obj[0]
        r = QMessageBox.question(
            None, "Anki", tr.profiles_confirm_lang_choice(lang=name), QMessageBox.Yes | QMessageBox.No, QMessageBox.No  # type: ignore
        )
        if r != QMessageBox.Yes:
            return self.setDefaultLang(f.lang.currentRow())
        self.setLang(code)

    def setLang(self, code: str) -> None:
        self.meta["defaultLang"] = code
        sql = "update profiles set data = ? where name = ?"
        self.db.execute(sql, self._pickle(self.meta), "_global")
        self.db.commit()
        anki.lang.set_lang(code)

    # OpenGL
    ######################################################################

    def _gldriver_path(self) -> str:
        return os.path.join(self.base, "gldriver")

    def video_driver(self) -> VideoDriver:
        path = self._gldriver_path()
        try:
            with open(path) as file:
                text = file.read().strip()
                return VideoDriver(text).constrained_to_platform()
        except (ValueError, OSError):
            return VideoDriver.default_for_platform()

    def set_video_driver(self, driver: VideoDriver) -> None:
        with open(self._gldriver_path(), "w") as file:
            file.write(driver.value)

    def set_next_video_driver(self) -> None:
        self.set_video_driver(self.video_driver().next())

    # Shared options
    ######################################################################

    def uiScale(self) -> float:
        scale = self.meta.get("uiScale", 1.0)
        return max(scale, 1)

    def setUiScale(self, scale: float) -> None:
        self.meta["uiScale"] = scale

    def last_addon_update_check(self) -> int:
        return self.meta.get("last_addon_update_check", 0)

    def set_last_addon_update_check(self, secs: int) -> None:
        self.meta["last_addon_update_check"] = secs

    def night_mode(self) -> bool:
        return self.meta.get("night_mode", False)

    def set_night_mode(self, on: bool) -> None:
        self.meta["night_mode"] = on

    def dark_mode_widgets(self) -> bool:
        return self.meta.get("dark_mode_widgets", False)

    # Profile-specific
    ######################################################################

    def set_sync_key(self, val: Optional[str]) -> None:
        self.profile["syncKey"] = val

    def set_sync_username(self, val: Optional[str]) -> None:
        self.profile["syncUser"] = val

    def set_host_number(self, val: Optional[int]) -> None:
        self.profile["hostNum"] = val or 0

    def media_syncing_enabled(self) -> bool:
        return self.profile["syncMedia"]

    def auto_syncing_enabled(self) -> bool:
        return self.profile["autoSync"]

    def sync_auth(self) -> Optional[SyncAuth]:
        hkey = self.profile.get("syncKey")
        if not hkey:
            return None
        return SyncAuth(hkey=hkey, host_number=self.profile.get("hostNum", 0))

    def clear_sync_auth(self) -> None:
        self.profile["syncKey"] = None
        self.profile["syncUser"] = None
        self.profile["hostNum"] = 0

    def auto_sync_media_minutes(self) -> int:
        return self.profile.get("autoSyncMediaMinutes", 15)

    def set_auto_sync_media_minutes(self, val: int) -> None:
        self.profile["autoSyncMediaMinutes"] = val

    def recording_driver(self) -> RecordingDriver:
        if driver := self.profile.get("recordingDriver"):
            try:
                return RecordingDriver(driver)
            except ValueError:
                # revert to default
                pass
        return RecordingDriver.QtAudioInput

    def set_recording_driver(self, driver: RecordingDriver) -> None:
        self.profile["recordingDriver"] = driver.value
