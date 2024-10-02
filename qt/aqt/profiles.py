# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import io
import os
import pickle
import random
import shutil
import traceback
from enum import Enum
from pathlib import Path
from typing import TYPE_CHECKING, Any

import anki.lang
import aqt.forms
import aqt.sound
from anki._legacy import deprecated
from anki.collection import Collection
from anki.db import DB
from anki.lang import without_unicode_isolation
from anki.sync import SyncAuth
from anki.utils import int_time, int_version, is_mac, is_win
from aqt import appHelpSite, gui_hooks
from aqt.qt import *
from aqt.qt import sip
from aqt.theme import Theme, WidgetStyle, theme_manager
from aqt.toolbar import HideMode
from aqt.utils import disable_help_button, send_to_trash, showWarning, tr

if TYPE_CHECKING:
    from aqt.browser.layout import BrowserLayout
    from aqt.editor import EditorMode


# Profile handling
##########################################################################
# - Saves in pickles rather than json to easily store Qt window state.
# - Saves in sqlite rather than a flat file so the config can't be corrupted


class VideoDriver(Enum):
    OpenGL = "auto"
    ANGLE = "angle"
    Software = "software"
    Metal = "metal"
    Vulkan = "vulkan"
    Direct3D = "d3d11"

    @staticmethod
    def default_for_platform() -> VideoDriver:
        return VideoDriver.all_for_platform()[0]

    def constrained_to_platform(self) -> VideoDriver:
        if self not in VideoDriver.all_for_platform():
            return VideoDriver.default_for_platform()
        return self

    def next(self) -> VideoDriver:
        all = VideoDriver.all_for_platform()
        try:
            idx = (all.index(self) + 1) % len(all)
        except ValueError:
            idx = 0
        return all[idx]

    @staticmethod
    def all_for_platform() -> list[VideoDriver]:
        all = []
        if qtmajor > 5:
            if is_win:
                all.append(VideoDriver.Direct3D)
            if is_mac:
                all.append(VideoDriver.Metal)
        all.append(VideoDriver.OpenGL)
        if qtmajor > 5 and not is_mac:
            all.append(VideoDriver.Vulkan)
        if is_win and qtmajor < 6:
            all.append(VideoDriver.ANGLE)
        all.append(VideoDriver.Software)

        return all


metaConf = dict(
    ver=0,
    updates=True,
    created=int_time(),
    id=random.randrange(0, 2**63),
    lastMsg=0,
    suppressUpdate=False,
    firstRun=True,
    defaultLang=None,
)

profileConf: dict[str, Any] = dict(
    # profile
    mainWindowGeom=None,
    mainWindowState=None,
    numBackups=50,
    lastOptimize=int_time(),
    # editing
    searchHistory=[],
    # syncing
    syncKey=None,
    syncMedia=True,
    autoSync=True,
    # importing
    allowHTML=False,
    importMode=1,
    # these are not used, but Anki 2.1.42 and below
    # expect these keys to exist
    lastColour="#00f",
    stripHTML=True,
    deleteMedia=False,
)


class LoadMetaResult:
    firstTime: bool
    loadError: bool


class ProfileManager:
    default_answer_keys = {ease_num: str(ease_num) for ease_num in range(1, 5)}
    last_run_version: int = 0

    def __init__(self, base: Path) -> None:  #
        "base should be retrieved via ProfileMangager.get_created_base_folder"
        ## Settings which should be forgotten each Anki restart
        self.session: dict[str, Any] = {}
        self.name: str | None = None
        self.db: DB | None = None
        self.profile: dict | None = None
        self.invalid_profile_provided_on_commandline = False
        self.base = str(base)

    def setupMeta(self) -> LoadMetaResult:
        # load metadata
        res = self._loadMeta()
        self.firstRun = res.firstTime
        self.last_run_version = self.meta.get("last_run_version", self.last_run_version)
        self.meta["last_run_version"] = int_version()
        return res

    # -p profile provided on command line.
    def openProfile(self, profile: str) -> None:
        if profile not in self.profiles():
            self.invalid_profile_provided_on_commandline = True
        else:
            try:
                self.load(profile)
            except Exception as exc:
                self.invalid_profile_provided_on_commandline = True

    # Profile load/save
    ######################################################################

    def profiles(self) -> list[str]:
        def names() -> list[str]:
            return self.db.list("select name from profiles where name != '_global'")

        n = names()
        if not n:
            self._ensureProfile()
            n = names()

        return n

    def _unpickle(self, data: bytes) -> Any:
        class Unpickler(pickle.Unpickler):
            def find_class(self, class_module: str, name: str) -> Any:
                # handle sip lookup ourselves, mapping to current Qt version
                if class_module == "sip" or class_module.endswith(".sip"):

                    def unpickle_type(module: str, klass: str, args: Any) -> Any:
                        if qtmajor > 5:
                            module = module.replace("Qt5", "Qt6")
                        else:
                            module = module.replace("Qt6", "Qt5")
                        if klass == "QByteArray":
                            if module.startswith("PyQt4"):
                                # can't trust str objects from python 2
                                return QByteArray()
                            else:
                                # return the bytes directly
                                return args[0]
                        elif name == "_unpickle_enum":
                            if qtmajor == 5:
                                return sip._unpickle_enum(module, klass, args)  # type: ignore
                            else:
                                # old style enums can't be unpickled
                                return None
                        else:
                            return sip._unpickle_type(module, klass, args)  # type: ignore

                    return unpickle_type
                else:
                    return super().find_class(class_module, name)

        up = Unpickler(io.BytesIO(data), errors="ignore")
        return up.load()

    def _pickle(self, obj: Any) -> bytes:
        for key, val in obj.items():
            if isinstance(val, QByteArray):
                obj[key] = bytes(val)  # type: ignore

        return pickle.dumps(obj, protocol=4)

    def load(self, name: str) -> bool:
        if name == "_global":
            raise Exception("_global is not a valid name")
        data = self.db.scalar(
            "select cast(data as blob) from profiles where name = ? collate nocase",
            name,
        )
        self.name = name
        try:
            self.profile = self._unpickle(data)
        except Exception:
            print(traceback.format_exc())
            QMessageBox.warning(
                None,
                tr.profiles_profile_corrupt(),
                tr.profiles_anki_could_not_read_your_profile(),
            )
            print("resetting corrupt profile")
            self.profile = profileConf.copy()
            self.save()
        self.set_last_loaded_profile_name(name)
        return True

    def save(self) -> None:
        sql = "update profiles set data = ? where name = ? collate nocase"
        self.db.execute(sql, self._pickle(self.profile), self.name)
        self.db.execute(sql, self._pickle(self.meta), "_global")
        self.db.commit()

    def create(self, name: str) -> None:
        prof = profileConf.copy()
        if self.db.scalar("select 1 from profiles where name = ? collate nocase", name):
            return
        self.db.execute(
            "insert or ignore into profiles values (?, ?)",
            name,
            self._pickle(prof),
        )
        self.db.commit()

    def remove(self, name: str) -> None:
        path = self.profileFolder(create=False)
        send_to_trash(Path(path))
        self.db.execute("delete from profiles where name = ? collate nocase", name)
        self.db.commit()

    def trashCollection(self) -> None:
        path = self.collectionPath()
        send_to_trash(Path(path))

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
        self.db.execute(
            "update profiles set name = ? where name = ? collate nocase", name, oldName
        )
        # rename folder
        try:
            os.rename(oldFolder, newFolder)
        except Exception as e:
            self.db.rollback()
            if "WinError 5" in str(e):
                showWarning(tr.profiles_anki_could_not_rename_your_profile())
            else:
                raise
        except BaseException:
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

    def addon_logs(self) -> str:
        return self._ensureExists(os.path.join(self.base, "logs"))

    # Downgrade
    ######################################################################

    def downgrade(self, profiles: list[str]) -> list[str]:
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
                c.close(downgrade=True)
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

    @staticmethod
    def get_created_base_folder(path_override: str | None) -> Path:
        "Create the base folder and return it, using provided path or default."
        path = Path(
            path_override
            or os.environ.get("ANKI_BASE")
            or ProfileManager._default_base()
        )
        path.mkdir(parents=True, exist_ok=True)
        return path.resolve()

    @staticmethod
    def _default_base() -> str:
        if is_win:
            from aqt.winpaths import get_appdata

            return os.path.join(get_appdata(), "Anki2")
        elif is_mac:
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
                except Exception:
                    pass
            for suffix in ("", "-journal"):
                fpath = path + suffix
                if os.path.exists(fpath):
                    os.unlink(fpath)

        # open DB file and read data
        try:
            self.db = DB(path)
            if not self.db.scalar("pragma integrity_check") == "ok":
                raise Exception("corrupt db")
            self.db.execute(
                """
create table if not exists profiles
(name text primary key collate nocase, data blob not null);"""
            )
            data = self.db.scalar(
                "select cast(data as blob) from profiles where name = '_global'"
            )
        except Exception:
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
            except Exception:
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
                + "\n"
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
        d.exec()

    def _onLangSelected(self) -> None:
        f = self.langForm
        obj = anki.lang.langs[f.lang.currentRow()]
        code = obj[1]
        name = obj[0]
        r = QMessageBox.question(
            None, "Anki", tr.profiles_confirm_lang_choice(lang=name), QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No, QMessageBox.StandardButton.No  # type: ignore
        )
        if r != QMessageBox.StandardButton.Yes:
            return self.setDefaultLang(f.lang.currentRow())
        self.setLang(code)

    def setLang(self, code: str) -> None:
        self.meta["defaultLang"] = code
        sql = "update profiles set data = ? where name = ? collate nocase"
        self.db.execute(sql, self._pickle(self.meta), "_global")
        self.db.commit()
        anki.lang.set_lang(code)

    # OpenGL
    ######################################################################

    def _gldriver_path(self) -> str:
        if qtmajor < 6:
            fname = "gldriver"
        else:
            fname = "gldriver6"
        return os.path.join(self.base, fname)

    def video_driver(self) -> VideoDriver:
        path = self._gldriver_path()
        try:
            with open(path, encoding="utf8") as file:
                text = file.read().strip()
                return VideoDriver(text).constrained_to_platform()
        except (ValueError, OSError):
            return VideoDriver.default_for_platform()

    def set_video_driver(self, driver: VideoDriver) -> None:
        with open(self._gldriver_path(), "w", encoding="utf8") as file:
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

    def reduce_motion(self) -> bool:
        return self.meta.get("reduce_motion", True)

    def set_reduce_motion(self, on: bool) -> None:
        self.meta["reduce_motion"] = on
        gui_hooks.body_classes_need_update()

    def minimalist_mode(self) -> bool:
        return self.meta.get("minimalist_mode", False)

    def set_minimalist_mode(self, on: bool) -> None:
        self.meta["minimalist_mode"] = on
        gui_hooks.body_classes_need_update()

    def spacebar_rates_card(self) -> bool:
        return self.meta.get("spacebar_rates_card", True)

    def set_spacebar_rates_card(self, on: bool) -> None:
        self.meta["spacebar_rates_card"] = on

    def get_answer_key(self, ease: int) -> str | None:
        return self.meta.setdefault("answer_keys", self.default_answer_keys).get(ease)

    def set_answer_key(self, ease: int, key: str):
        self.meta.setdefault("answer_keys", self.default_answer_keys)[ease] = key

    def hide_top_bar(self) -> bool:
        return self.meta.get("hide_top_bar", False)

    def set_hide_top_bar(self, on: bool) -> None:
        self.meta["hide_top_bar"] = on
        gui_hooks.body_classes_need_update()

    def top_bar_hide_mode(self) -> HideMode:
        return self.meta.get("top_bar_hide_mode", HideMode.FULLSCREEN)

    def set_top_bar_hide_mode(self, mode: HideMode) -> None:
        self.meta["top_bar_hide_mode"] = mode
        gui_hooks.body_classes_need_update()

    def hide_bottom_bar(self) -> bool:
        return self.meta.get("hide_bottom_bar", False)

    def set_hide_bottom_bar(self, on: bool) -> None:
        self.meta["hide_bottom_bar"] = on
        gui_hooks.body_classes_need_update()

    def bottom_bar_hide_mode(self) -> HideMode:
        return self.meta.get("bottom_bar_hide_mode", HideMode.FULLSCREEN)

    def set_bottom_bar_hide_mode(self, mode: HideMode) -> None:
        self.meta["bottom_bar_hide_mode"] = mode
        gui_hooks.body_classes_need_update()

    def last_addon_update_check(self) -> int:
        return self.meta.get("last_addon_update_check", 0)

    def set_last_addon_update_check(self, secs: int) -> None:
        self.meta["last_addon_update_check"] = secs

    @deprecated(info="use theme_manager.night_mode")
    def night_mode(self) -> bool:
        return theme_manager.night_mode

    def theme(self) -> Theme:
        return Theme(self.meta.get("theme", 0))

    def set_theme(self, theme: Theme) -> None:
        self.meta["theme"] = theme.value

    def set_widget_style(self, style: WidgetStyle) -> None:
        self.meta["widget_style"] = style
        theme_manager.apply_style()

    def get_widget_style(self) -> WidgetStyle:
        return self.meta.get(
            "widget_style", WidgetStyle.NATIVE if is_mac else WidgetStyle.ANKI
        )

    def browser_layout(self) -> BrowserLayout:
        from aqt.browser.layout import BrowserLayout

        return BrowserLayout(self.meta.get("browser_layout", "auto"))

    def set_browser_layout(self, layout: BrowserLayout) -> None:
        self.meta["browser_layout"] = layout.value

    def editor_key(self, mode: EditorMode) -> str:
        from aqt.editor import EditorMode

        return {
            EditorMode.ADD_CARDS: "add",
            EditorMode.BROWSER: "browser",
            EditorMode.EDIT_CURRENT: "current",
        }[mode]

    def tags_collapsed(self, mode: EditorMode) -> bool:
        return self.meta.get(f"{self.editor_key(mode)}TagsCollapsed", False)

    def set_tags_collapsed(self, mode: EditorMode, collapsed: bool) -> None:
        self.meta[f"{self.editor_key(mode)}TagsCollapsed"] = collapsed

    def legacy_import_export(self) -> bool:
        return self.meta.get("legacy_import", False)

    def set_legacy_import_export(self, enabled: bool) -> None:
        self.meta["legacy_import"] = enabled

    def last_loaded_profile_name(self) -> str | None:
        return self.meta.get("last_loaded_profile_name")

    def set_last_loaded_profile_name(self, name: str) -> None:
        self.meta["last_loaded_profile_name"] = name

    # Profile-specific
    ######################################################################

    def set_sync_key(self, val: str | None) -> None:
        self.profile["syncKey"] = val

    def set_sync_username(self, val: str | None) -> None:
        self.profile["syncUser"] = val

    def set_host_number(self, val: int | None) -> None:
        self.profile["hostNum"] = val or 0

    def check_for_updates(self) -> bool:
        return self.meta.get("check_for_updates", True)

    def set_update_check(self, on: bool) -> None:
        self.meta["check_for_updates"] = on

    def media_syncing_enabled(self) -> bool:
        return self.profile.get("syncMedia", True)

    def auto_syncing_enabled(self) -> bool:
        "True if syncing on startup/shutdown enabled."
        return self.profile.get("autoSync", True)

    def sync_auth(self) -> SyncAuth | None:
        if not (hkey := self.profile.get("syncKey")):
            return None
        return SyncAuth(
            hkey=hkey,
            endpoint=self.sync_endpoint(),
            io_timeout_secs=self.network_timeout(),
        )

    def clear_sync_auth(self) -> None:
        self.set_sync_key(None)
        self.set_sync_username(None)
        self.set_host_number(None)
        self.set_current_sync_url(None)

    def sync_endpoint(self) -> str | None:
        return self._current_sync_url() or self.custom_sync_url() or None

    def _current_sync_url(self) -> str | None:
        """The last endpoint the server redirected us to."""
        return self.profile.get("currentSyncUrl")

    def set_current_sync_url(self, url: str | None) -> None:
        self.profile["currentSyncUrl"] = url

    def custom_sync_url(self) -> str | None:
        """A custom server provided by the user."""
        return self.profile.get("customSyncUrl")

    def set_custom_sync_url(self, url: str | None) -> None:
        if url != self.custom_sync_url():
            self.set_current_sync_url(None)
            self.profile["customSyncUrl"] = url

    def periodic_sync_media_minutes(self) -> int:
        return self.profile.get("autoSyncMediaMinutes", 15)

    def set_periodic_sync_media_minutes(self, val: int) -> None:
        self.profile["autoSyncMediaMinutes"] = val

    def show_browser_table_tooltips(self) -> bool:
        return self.profile.get("browserTableTooltips", True)

    def set_show_browser_table_tooltips(self, val: bool) -> None:
        self.profile["browserTableTooltips"] = val

    def set_network_timeout(self, timeout_secs: int) -> None:
        self.profile["networkTimeout"] = timeout_secs

    def network_timeout(self) -> int:
        return self.profile.get("networkTimeout") or 60

    def set_ankihub_token(self, val: str | None) -> None:
        self.profile["thirdPartyAnkiHubToken"] = val

    def ankihub_token(self) -> str | None:
        return self.profile.get("thirdPartyAnkiHubToken")

    def set_ankihub_username(self, val: str | None) -> None:
        self.profile["thirdPartyAnkiHubUsername"] = val

    def ankihub_username(self) -> str | None:
        return self.profile.get("thirdPartyAnkiHubUsername")
