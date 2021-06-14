# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import io
import json
import os
import re
import zipfile
from collections import defaultdict
from concurrent.futures import Future
from dataclasses import dataclass
from datetime import datetime
from typing import IO, Any, Callable, Dict, Iterable, List, Optional, Tuple, Union
from urllib.parse import parse_qs, urlparse
from zipfile import ZipFile

import jsonschema
import markdown
from jsonschema.exceptions import ValidationError
from send2trash import send2trash

import anki
import aqt
import aqt.forms
from anki.httpclient import HttpClient
from anki.lang import without_unicode_isolation
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    askUser,
    disable_help_button,
    getFile,
    isWin,
    openFolder,
    openLink,
    restoreGeom,
    restoreSplitter,
    saveGeom,
    saveSplitter,
    showInfo,
    showWarning,
    tooltip,
    tr,
)


class AbortAddonImport(Exception):
    """If raised during add-on import, Anki will silently ignore this exception.
    This allows you to terminate loading without an error being shown."""


@dataclass
class InstallOk:
    name: str
    conflicts: List[str]
    compatible: bool


@dataclass
class InstallError:
    errmsg: str


@dataclass
class DownloadOk:
    data: bytes
    filename: str
    mod_time: int
    min_point_version: int
    max_point_version: int
    branch_index: int


@dataclass
class DownloadError:
    # set if result was not 200
    status_code: Optional[int] = None
    # set if an exception occurred
    exception: Optional[Exception] = None


# first arg is add-on id
DownloadLogEntry = Tuple[int, Union[DownloadError, InstallError, InstallOk]]


@dataclass
class UpdateInfo:
    id: int
    suitable_branch_last_modified: int
    current_branch_last_modified: int
    current_branch_min_point_ver: int
    current_branch_max_point_ver: int


ANKIWEB_ID_RE = re.compile(r"^\d+$")

current_point_version = anki.utils.pointVersion()


@dataclass
class AddonMeta:
    dir_name: str
    provided_name: Optional[str]
    enabled: bool
    installed_at: int
    conflicts: List[str]
    min_point_version: int
    max_point_version: int
    branch_index: int
    human_version: Optional[str]
    update_enabled: bool

    def human_name(self) -> str:
        return self.provided_name or self.dir_name

    def ankiweb_id(self) -> Optional[int]:
        m = ANKIWEB_ID_RE.match(self.dir_name)
        if m:
            return int(m.group(0))
        else:
            return None

    def compatible(self) -> bool:
        min = self.min_point_version
        if min is not None and current_point_version < min:
            return False
        max = self.max_point_version
        if max is not None and max < 0 and current_point_version > abs(max):
            return False
        return True

    def is_latest(self, server_update_time: int) -> bool:
        return self.installed_at >= server_update_time

    @staticmethod
    def from_json_meta(dir_name: str, json_meta: Dict[str, Any]) -> AddonMeta:
        return AddonMeta(
            dir_name=dir_name,
            provided_name=json_meta.get("name"),
            enabled=not json_meta.get("disabled"),
            installed_at=json_meta.get("mod", 0),
            conflicts=json_meta.get("conflicts", []),
            min_point_version=json_meta.get("min_point_version", 0) or 0,
            max_point_version=json_meta.get("max_point_version", 0) or 0,
            branch_index=json_meta.get("branch_index", 0) or 0,
            human_version=json_meta.get("human_version"),
            update_enabled=json_meta.get("update_enabled", True),
        )


# fixme: this class should not have any GUI code in it
class AddonManager:

    ext: str = ".ankiaddon"
    _manifest_schema: dict = {
        "type": "object",
        "properties": {
            # the name of the folder
            "package": {"type": "string", "meta": False},
            # the displayed name to the user
            "name": {"type": "string", "meta": True},
            # the time the add-on was last modified
            "mod": {"type": "number", "meta": True},
            # a list of other packages that conflict
            "conflicts": {"type": "array", "items": {"type": "string"}, "meta": True},
            # the minimum 2.1.x version this add-on supports
            "min_point_version": {"type": "number", "meta": True},
            # if negative, abs(n) is the maximum 2.1.x version this add-on supports
            # if positive, indicates version tested on, and is ignored
            "max_point_version": {"type": "number", "meta": True},
            # AnkiWeb sends this to indicate which branch the user downloaded.
            "branch_index": {"type": "number", "meta": True},
            # version string set by the add-on creator
            "human_version": {"type": "string", "meta": True},
        },
        "required": ["package", "name"],
    }

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        self.mw = mw
        self.dirty = False
        f = self.mw.form
        qconnect(f.actionAdd_ons.triggered, self.onAddonsDialog)
        sys.path.insert(0, self.addonsFolder())

    # in new code, you may want all_addon_meta() instead
    def allAddons(self) -> List[str]:
        l = []
        for d in os.listdir(self.addonsFolder()):
            path = self.addonsFolder(d)
            if not os.path.exists(os.path.join(path, "__init__.py")):
                continue
            l.append(d)
        l.sort()
        if os.getenv("ANKIREVADDONS", ""):
            l = list(reversed(l))
        return l

    def all_addon_meta(self) -> Iterable[AddonMeta]:
        return map(self.addon_meta, self.allAddons())

    def addonsFolder(self, dir: Optional[str] = None) -> str:
        root = self.mw.pm.addonFolder()
        if not dir:
            return root
        return os.path.join(root, dir)

    def loadAddons(self) -> None:
        for addon in self.all_addon_meta():
            if not addon.enabled:
                continue
            if not addon.compatible():
                continue
            self.dirty = True
            try:
                __import__(addon.dir_name)
            except AbortAddonImport:
                pass
            except:
                showWarning(
                    tr.addons_failed_to_load(
                        name=addon.human_name(),
                        traceback=traceback.format_exc(),
                    )
                )

    def onAddonsDialog(self) -> None:
        aqt.dialogs.open("AddonsDialog", self)

    # Metadata
    ######################################################################

    def addon_meta(self, dir_name: str) -> AddonMeta:
        """Get info about an installed add-on."""
        json_obj = self.addonMeta(dir_name)
        return AddonMeta.from_json_meta(dir_name, json_obj)

    def write_addon_meta(self, addon: AddonMeta) -> None:
        # preserve any unknown attributes
        json_obj = self.addonMeta(addon.dir_name)

        if addon.provided_name is not None:
            json_obj["name"] = addon.provided_name
        json_obj["disabled"] = not addon.enabled
        json_obj["mod"] = addon.installed_at
        json_obj["conflicts"] = addon.conflicts
        json_obj["max_point_version"] = addon.max_point_version
        json_obj["min_point_version"] = addon.min_point_version
        json_obj["branch_index"] = addon.branch_index
        if addon.human_version is not None:
            json_obj["human_version"] = addon.human_version
        json_obj["update_enabled"] = addon.update_enabled

        self.writeAddonMeta(addon.dir_name, json_obj)

    def _addonMetaPath(self, dir: str) -> str:
        return os.path.join(self.addonsFolder(dir), "meta.json")

    # in new code, use self.addon_meta() instead
    def addonMeta(self, dir: str) -> Dict[str, Any]:
        path = self._addonMetaPath(dir)
        try:
            with open(path, encoding="utf8") as f:
                return json.load(f)
        except json.JSONDecodeError as e:
            print(f"json error in add-on {dir}:\n{e}")
            return dict()
        except:
            # missing meta file, etc
            return dict()

    # in new code, use write_addon_meta() instead
    def writeAddonMeta(self, dir: str, meta: Dict[str, Any]) -> None:
        path = self._addonMetaPath(dir)
        with open(path, "w", encoding="utf8") as f:
            json.dump(meta, f)

    def toggleEnabled(self, dir: str, enable: Optional[bool] = None) -> None:
        addon = self.addon_meta(dir)
        should_enable = enable if enable is not None else not addon.enabled
        if should_enable is True:
            conflicting = self._disableConflicting(dir)
            if conflicting:
                addons = ", ".join(self.addonName(f) for f in conflicting)
                showInfo(
                    tr.addons_the_following_addons_are_incompatible_with(
                        name=addon.human_name(),
                        found=addons,
                    ),
                    textFormat="plain",
                )

        addon.enabled = should_enable
        self.write_addon_meta(addon)

    def ankiweb_addons(self) -> List[int]:
        ids = []
        for meta in self.all_addon_meta():
            if meta.ankiweb_id() is not None:
                ids.append(meta.ankiweb_id())
        return ids

    # Legacy helpers
    ######################################################################

    def isEnabled(self, dir: str) -> bool:
        return self.addon_meta(dir).enabled

    def addonName(self, dir: str) -> str:
        return self.addon_meta(dir).human_name()

    def addonConflicts(self, dir: str) -> List[str]:
        return self.addon_meta(dir).conflicts

    def annotatedName(self, dir: str) -> str:
        meta = self.addon_meta(dir)
        name = meta.human_name()
        if not meta.enabled:
            name += f" {tr.addons_disabled()}"
        return name

    # Conflict resolution
    ######################################################################

    def allAddonConflicts(self) -> Dict[str, List[str]]:
        all_conflicts: Dict[str, List[str]] = defaultdict(list)
        for addon in self.all_addon_meta():
            if not addon.enabled:
                continue
            for other_dir in addon.conflicts:
                all_conflicts[other_dir].append(addon.dir_name)
        return all_conflicts

    def _disableConflicting(self, dir: str, conflicts: List[str] = None) -> List[str]:
        conflicts = conflicts or self.addonConflicts(dir)

        installed = self.allAddons()
        found = [d for d in conflicts if d in installed and self.isEnabled(d)]
        found.extend(self.allAddonConflicts().get(dir, []))
        if not found:
            return []

        for package in found:
            self.toggleEnabled(package, enable=False)

        return found

    # Installing and deleting add-ons
    ######################################################################

    def readManifestFile(self, zfile: ZipFile) -> Dict[Any, Any]:
        try:
            with zfile.open("manifest.json") as f:
                data = json.loads(f.read())
            jsonschema.validate(data, self._manifest_schema)
            # build new manifest from recognized keys
            schema = self._manifest_schema["properties"]
            manifest = {key: data[key] for key in data.keys() & schema.keys()}
        except (KeyError, json.decoder.JSONDecodeError, ValidationError):
            # raised for missing manifest, invalid json, missing/invalid keys
            return {}
        return manifest

    def install(
        self, file: Union[IO, str], manifest: Dict[str, Any] = None
    ) -> Union[InstallOk, InstallError]:
        """Install add-on from path or file-like object. Metadata is read
        from the manifest file, with keys overriden by supplying a 'manifest'
        dictionary"""
        try:
            zfile = ZipFile(file)
        except zipfile.BadZipfile:
            return InstallError(errmsg="zip")

        with zfile:
            file_manifest = self.readManifestFile(zfile)
            if manifest:
                file_manifest.update(manifest)
            manifest = file_manifest
            if not manifest:
                return InstallError(errmsg="manifest")
            package = manifest["package"]
            conflicts = manifest.get("conflicts", [])
            found_conflicts = self._disableConflicting(package, conflicts)
            meta = self.addonMeta(package)
            self._install(package, zfile)

        schema = self._manifest_schema["properties"]
        manifest_meta = {
            k: v for k, v in manifest.items() if k in schema and schema[k]["meta"]
        }
        meta.update(manifest_meta)
        self.writeAddonMeta(package, meta)

        meta2 = self.addon_meta(package)

        return InstallOk(
            name=meta["name"], conflicts=found_conflicts, compatible=meta2.compatible()
        )

    def _install(self, dir: str, zfile: ZipFile) -> None:
        # previously installed?
        base = self.addonsFolder(dir)
        if os.path.exists(base):
            self.backupUserFiles(dir)
            if not self.deleteAddon(dir):
                self.restoreUserFiles(dir)
                return

        os.mkdir(base)
        self.restoreUserFiles(dir)

        # extract
        for n in zfile.namelist():
            if n.endswith("/"):
                # folder; ignore
                continue

            path = os.path.join(base, n)
            # skip existing user files
            if os.path.exists(path) and n.startswith("user_files/"):
                continue
            zfile.extract(n, base)

    # true on success
    def deleteAddon(self, dir: str) -> bool:
        try:
            send2trash(self.addonsFolder(dir))
            return True
        except OSError as e:
            showWarning(
                tr.addons_unable_to_update_or_delete_addon(val=str(e)),
                textFormat="plain",
            )
            return False

    # Processing local add-on files
    ######################################################################

    def processPackages(
        self, paths: List[str], parent: QWidget = None
    ) -> Tuple[List[str], List[str]]:

        log = []
        errs = []

        self.mw.progress.start(parent=parent)
        try:
            for path in paths:
                base = os.path.basename(path)
                result = self.install(path)

                if isinstance(result, InstallError):
                    errs.extend(
                        self._installationErrorReport(result, base, mode="local")
                    )
                else:
                    log.extend(
                        self._installationSuccessReport(result, base, mode="local")
                    )
        finally:
            self.mw.progress.finish()

        return log, errs

    # Installation messaging
    ######################################################################

    def _installationErrorReport(
        self, result: InstallError, base: str, mode: str = "download"
    ) -> List[str]:

        messages = {
            "zip": tr.addons_corrupt_addon_file(),
            "manifest": tr.addons_invalid_addon_manifest(),
        }

        msg = messages.get(result.errmsg, tr.addons_unknown_error(val=result.errmsg))

        if mode == "download":
            template = tr.addons_error_downloading_ids_errors(id=base, error=msg)
        else:
            template = tr.addons_error_installing_bases_errors(base=base, error=msg)

        return [template]

    def _installationSuccessReport(
        self, result: InstallOk, base: str, mode: str = "download"
    ) -> List[str]:

        name = result.name or base
        if mode == "download":
            template = tr.addons_downloaded_fnames(fname=name)
        else:
            template = tr.addons_installed_names(name=name)

        strings = [template]

        if result.conflicts:
            strings.append(
                tr.addons_the_following_conflicting_addons_were_disabled()
                + " "
                + ", ".join(self.addonName(f) for f in result.conflicts)
            )

        if not result.compatible:
            strings.append(tr.addons_this_addon_is_not_compatible_with())

        return strings

    # Updating
    ######################################################################

    def extract_update_info(self, items: List[Dict]) -> List[UpdateInfo]:
        def extract_one(item: Dict) -> UpdateInfo:
            id = item["id"]
            meta = self.addon_meta(str(id))
            branch_idx = meta.branch_index
            return extract_update_info(current_point_version, branch_idx, item)

        return list(map(extract_one, items))

    def update_supported_versions(self, items: List[UpdateInfo]) -> None:
        for item in items:
            self.update_supported_version(item)

    def update_supported_version(self, item: UpdateInfo) -> None:
        addon = self.addon_meta(str(item.id))
        updated = False
        is_latest = addon.is_latest(item.current_branch_last_modified)

        # if max different to the stored value
        cur_max = item.current_branch_max_point_ver
        if addon.max_point_version != cur_max:
            if is_latest:
                addon.max_point_version = cur_max
                updated = True
            else:
                # user is not up to date; only update if new version is stricter
                if cur_max is not None and cur_max < addon.max_point_version:
                    addon.max_point_version = cur_max
                    updated = True

        # if min different to the stored value
        cur_min = item.current_branch_min_point_ver
        if addon.min_point_version != cur_min:
            if is_latest:
                addon.min_point_version = cur_min
                updated = True
            else:
                # user is not up to date; only update if new version is stricter
                if cur_min is not None and cur_min > addon.min_point_version:
                    addon.min_point_version = cur_min
                    updated = True

        if updated:
            self.write_addon_meta(addon)

    def updates_required(self, items: List[UpdateInfo]) -> List[UpdateInfo]:
        """Return ids of add-ons requiring an update."""
        need_update = []
        for item in items:
            addon = self.addon_meta(str(item.id))
            # update if server mtime is newer
            if not addon.is_latest(item.suitable_branch_last_modified):
                need_update.append(item)
            elif not addon.compatible() and item.suitable_branch_last_modified > 0:
                # Addon is currently disabled, and a suitable branch was found on the
                # server. Ignore our stored mtime (which may have been set incorrectly
                # in the past) and require an update.
                need_update.append(item)

        return need_update

    # Add-on Config
    ######################################################################

    _configButtonActions: Dict[str, Callable[[], Optional[bool]]] = {}
    _configUpdatedActions: Dict[str, Callable[[Any], None]] = {}

    def addonConfigDefaults(self, dir: str) -> Optional[Dict[str, Any]]:
        path = os.path.join(self.addonsFolder(dir), "config.json")
        try:
            with open(path, encoding="utf8") as f:
                return json.load(f)
        except:
            return None

    def addonConfigHelp(self, dir: str) -> str:
        path = os.path.join(self.addonsFolder(dir), "config.md")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                return markdown.markdown(f.read())
        else:
            return ""

    def addonFromModule(self, module: str) -> str:
        return module.split(".")[0]

    def configAction(self, addon: str) -> Callable[[], Optional[bool]]:
        return self._configButtonActions.get(addon)

    def configUpdatedAction(self, addon: str) -> Callable[[Any], None]:
        return self._configUpdatedActions.get(addon)

    # Schema
    ######################################################################

    def _addon_schema_path(self, dir: str) -> str:
        return os.path.join(self.addonsFolder(dir), "config.schema.json")

    def _addon_schema(self, dir: str) -> Any:
        path = self._addon_schema_path(dir)
        try:
            if not os.path.exists(path):
                # True is a schema accepting everything
                return True
            with open(path, encoding="utf-8") as f:
                return json.load(f)
        except json.decoder.JSONDecodeError as e:
            print("The schema is not valid:")
            print(e)

    # Add-on Config API
    ######################################################################

    def getConfig(self, module: str) -> Optional[Dict[str, Any]]:
        addon = self.addonFromModule(module)
        # get default config
        config = self.addonConfigDefaults(addon)
        if config is None:
            return None
        # merge in user's keys
        meta = self.addonMeta(addon)
        userConf = meta.get("config", {})
        config.update(userConf)
        return config

    def setConfigAction(self, module: str, fn: Callable[[], Optional[bool]]) -> None:
        addon = self.addonFromModule(module)
        self._configButtonActions[addon] = fn

    def setConfigUpdatedAction(self, module: str, fn: Callable[[Any], None]) -> None:
        addon = self.addonFromModule(module)
        self._configUpdatedActions[addon] = fn

    def writeConfig(self, module: str, conf: dict) -> None:
        addon = self.addonFromModule(module)
        meta = self.addonMeta(addon)
        meta["config"] = conf
        self.writeAddonMeta(addon, meta)

    # user_files
    ######################################################################

    def _userFilesPath(self, sid: str) -> str:
        return os.path.join(self.addonsFolder(sid), "user_files")

    def _userFilesBackupPath(self) -> str:
        return os.path.join(self.addonsFolder(), "files_backup")

    def backupUserFiles(self, sid: str) -> None:
        p = self._userFilesPath(sid)
        if os.path.exists(p):
            os.rename(p, self._userFilesBackupPath())

    def restoreUserFiles(self, sid: str) -> None:
        p = self._userFilesPath(sid)
        bp = self._userFilesBackupPath()
        # did we back up userFiles?
        if not os.path.exists(bp):
            return
        os.rename(bp, p)

    # Web Exports
    ######################################################################

    _webExports: Dict[str, str] = {}

    def setWebExports(self, module: str, pattern: str) -> None:
        addon = self.addonFromModule(module)
        self._webExports[addon] = pattern

    def getWebExports(self, addon: str) -> str:
        return self._webExports.get(addon)


# Add-ons Dialog
######################################################################


class AddonsDialog(QDialog):
    def __init__(self, addonsManager: AddonManager) -> None:
        self.mgr = addonsManager
        self.mw = addonsManager.mw

        super().__init__(self.mw)

        f = self.form = aqt.forms.addons.Ui_Dialog()
        f.setupUi(self)
        qconnect(f.getAddons.clicked, self.onGetAddons)
        qconnect(f.installFromFile.clicked, self.onInstallFiles)
        qconnect(f.checkForUpdates.clicked, self.check_for_updates)
        qconnect(f.toggleEnabled.clicked, self.onToggleEnabled)
        qconnect(f.viewPage.clicked, self.onViewPage)
        qconnect(f.viewFiles.clicked, self.onViewFiles)
        qconnect(f.delete_2.clicked, self.onDelete)
        qconnect(f.config.clicked, self.onConfig)
        qconnect(self.form.addonList.itemDoubleClicked, self.onConfig)
        qconnect(self.form.addonList.currentRowChanged, self._onAddonItemSelected)
        self.setWindowTitle(tr.addons_window_title())
        disable_help_button(self)
        self.setAcceptDrops(True)
        self.redrawAddons()
        restoreGeom(self, "addons")
        gui_hooks.addons_dialog_will_show(self)
        self.show()

    def dragEnterEvent(self, event: QDragEnterEvent) -> None:
        mime = event.mimeData()
        if not mime.hasUrls():
            return None
        urls = mime.urls()
        ext = self.mgr.ext
        if all(url.toLocalFile().endswith(ext) for url in urls):
            event.acceptProposedAction()

    def dropEvent(self, event: QDropEvent) -> None:
        mime = event.mimeData()
        paths = []
        for url in mime.urls():
            path = url.toLocalFile()
            if os.path.exists(path):
                paths.append(path)
        self.onInstallFiles(paths)

    def reject(self) -> None:
        saveGeom(self, "addons")
        aqt.dialogs.markClosed("AddonsDialog")

        return QDialog.reject(self)

    silentlyClose = True

    def name_for_addon_list(self, addon: AddonMeta) -> str:
        name = addon.human_name()

        if not addon.enabled:
            return f"{name} {tr.addons_disabled2()}"
        elif not addon.compatible():
            return f"{name} {tr.addons_requires(val=self.compatible_string(addon))}"

        return name

    def compatible_string(self, addon: AddonMeta) -> str:
        min = addon.min_point_version
        if min is not None and min > current_point_version:
            return f"Anki >= 2.1.{min}"
        else:
            max = abs(addon.max_point_version)
            return f"Anki <= 2.1.{max}"

    def should_grey(self, addon: AddonMeta) -> bool:
        return not addon.enabled or not addon.compatible()

    def redrawAddons(
        self,
    ) -> None:
        addonList = self.form.addonList
        mgr = self.mgr

        self.addons = list(mgr.all_addon_meta())
        self.addons.sort(key=lambda a: a.human_name().lower())
        self.addons.sort(key=self.should_grey)

        selected = set(self.selectedAddons())
        addonList.clear()
        for addon in self.addons:
            name = self.name_for_addon_list(addon)
            item = QListWidgetItem(name, addonList)
            if self.should_grey(addon):
                item.setForeground(Qt.gray)
            if addon.dir_name in selected:
                item.setSelected(True)

        addonList.reset()

    def _onAddonItemSelected(self, row_int: int) -> None:
        try:
            addon = self.addons[row_int]
        except IndexError:
            return
        self.form.viewPage.setEnabled(addon.ankiweb_id() is not None)
        self.form.config.setEnabled(
            bool(
                self.mgr.getConfig(addon.dir_name)
                or self.mgr.configAction(addon.dir_name)
            )
        )
        gui_hooks.addons_dialog_did_change_selected_addon(self, addon)
        return

    def selectedAddons(self) -> List[str]:
        idxs = [x.row() for x in self.form.addonList.selectedIndexes()]
        return [self.addons[idx].dir_name for idx in idxs]

    def onlyOneSelected(self) -> Optional[str]:
        dirs = self.selectedAddons()
        if len(dirs) != 1:
            showInfo(tr.addons_please_select_a_single_addon_first())
            return None
        return dirs[0]

    def onToggleEnabled(self) -> None:
        for dir in self.selectedAddons():
            self.mgr.toggleEnabled(dir)
        self.redrawAddons()

    def onViewPage(self) -> None:
        addon = self.onlyOneSelected()
        if not addon:
            return
        if re.match(r"^\d+$", addon):
            openLink(f"{aqt.appShared}info/{addon}")
        else:
            showWarning(tr.addons_addon_was_not_downloaded_from_ankiweb())

    def onViewFiles(self) -> None:
        # if nothing selected, open top level folder
        selected = self.selectedAddons()
        if not selected:
            openFolder(self.mgr.addonsFolder())
            return

        # otherwise require a single selection
        addon = self.onlyOneSelected()
        if not addon:
            return
        path = self.mgr.addonsFolder(addon)
        openFolder(path)

    def onDelete(self) -> None:
        selected = self.selectedAddons()
        if not selected:
            return
        if not askUser(tr.addons_delete_the_numd_selected_addon(count=len(selected))):
            return
        gui_hooks.addons_dialog_will_delete_addons(self, selected)
        for dir in selected:
            if not self.mgr.deleteAddon(dir):
                break
        self.form.addonList.clearSelection()
        self.redrawAddons()

    def onGetAddons(self) -> None:
        obj = GetAddons(self)
        if obj.ids:
            download_addons(self, self.mgr, obj.ids, self.after_downloading)

    def after_downloading(self, log: List[DownloadLogEntry]) -> None:
        self.redrawAddons()
        if log:
            show_log_to_user(self, log)
        else:
            tooltip(tr.addons_no_updates_available())

    def onInstallFiles(self, paths: Optional[List[str]] = None) -> Optional[bool]:
        if not paths:
            key = f"{tr.addons_packaged_anki_addon()} (*{self.mgr.ext})"
            paths_ = getFile(
                self, tr.addons_install_addons(), None, key, key="addons", multi=True
            )
            paths = paths_  # type: ignore
            if not paths:
                return False

        installAddonPackages(self.mgr, paths, parent=self)

        self.redrawAddons()
        return None

    def check_for_updates(self) -> None:
        tooltip(tr.addons_checking())
        check_and_prompt_for_updates(self, self.mgr, self.after_downloading)

    def onConfig(self) -> None:
        addon = self.onlyOneSelected()
        if not addon:
            return

        # does add-on manage its own config?
        act = self.mgr.configAction(addon)
        if act:
            ret = act()
            if ret is not False:
                return

        conf = self.mgr.getConfig(addon)
        if conf is None:
            showInfo(tr.addons_addon_has_no_configuration())
            return

        ConfigEditor(self, addon, conf)


# Fetching Add-ons
######################################################################


class GetAddons(QDialog):
    def __init__(self, dlg: AddonsDialog) -> None:
        QDialog.__init__(self, dlg)
        self.addonsDlg = dlg
        self.mgr = dlg.mgr
        self.mw = self.mgr.mw
        self.ids: List[int] = []
        self.form = aqt.forms.getaddons.Ui_Dialog()
        self.form.setupUi(self)
        b = self.form.buttonBox.addButton(
            tr.addons_browse_addons(), QDialogButtonBox.ActionRole
        )
        qconnect(b.clicked, self.onBrowse)
        disable_help_button(self)
        restoreGeom(self, "getaddons", adjustSize=True)
        self.exec_()
        saveGeom(self, "getaddons")

    def onBrowse(self) -> None:
        openLink(f"{aqt.appShared}addons/2.1")

    def accept(self) -> None:
        # get codes
        try:
            ids = [int(n) for n in self.form.code.text().split()]
        except ValueError:
            showWarning(tr.addons_invalid_code())
            return

        self.ids = ids
        QDialog.accept(self)


# Downloading
######################################################################


def download_addon(client: HttpClient, id: int) -> Union[DownloadOk, DownloadError]:
    "Fetch a single add-on from AnkiWeb."
    try:
        resp = client.get(
            f"{aqt.appShared}download/{id}?v=2.1&p={current_point_version}"
        )
        if resp.status_code != 200:
            return DownloadError(status_code=resp.status_code)

        data = client.streamContent(resp)

        fname = re.match(
            "attachment; filename=(.+)", resp.headers["content-disposition"]
        ).group(1)

        meta = extract_meta_from_download_url(resp.url)

        return DownloadOk(
            data=data,
            filename=fname,
            mod_time=meta.mod_time,
            min_point_version=meta.min_point_version,
            max_point_version=meta.max_point_version,
            branch_index=meta.branch_index,
        )
    except Exception as e:
        return DownloadError(exception=e)


@dataclass
class ExtractedDownloadMeta:
    mod_time: int
    min_point_version: int
    max_point_version: int
    branch_index: int


def extract_meta_from_download_url(url: str) -> ExtractedDownloadMeta:
    urlobj = urlparse(url)
    query = parse_qs(urlobj.query)

    meta = ExtractedDownloadMeta(
        mod_time=int(query.get("t")[0]),
        min_point_version=int(query.get("minpt")[0]),
        max_point_version=int(query.get("maxpt")[0]),
        branch_index=int(query.get("bidx")[0]),
    )

    return meta


def download_log_to_html(log: List[DownloadLogEntry]) -> str:
    return "<br>".join(map(describe_log_entry, log))


def describe_log_entry(id_and_entry: DownloadLogEntry) -> str:
    (id, entry) = id_and_entry
    buf = f"{id}: "

    if isinstance(entry, DownloadError):
        if entry.status_code is not None:
            if entry.status_code in (403, 404):
                buf += tr.addons_invalid_code_or_addon_not_available()
            else:
                buf += tr.qt_misc_unexpected_response_code(val=entry.status_code)
        else:
            buf += (
                tr.addons_please_check_your_internet_connection()
                + "\n\n"
                + str(entry.exception)
            )
    elif isinstance(entry, InstallError):
        buf += entry.errmsg
    else:
        buf += tr.addons_installed_successfully()

    return buf


def download_encountered_problem(log: List[DownloadLogEntry]) -> bool:
    return any(not isinstance(e[1], InstallOk) for e in log)


def download_and_install_addon(
    mgr: AddonManager, client: HttpClient, id: int
) -> DownloadLogEntry:
    "Download and install a single add-on."
    result = download_addon(client, id)
    if isinstance(result, DownloadError):
        return (id, result)

    fname = result.filename.replace("_", " ")
    name = os.path.splitext(fname)[0].strip()
    if not name:
        name = str(id)

    manifest = dict(
        package=str(id),
        name=name,
        mod=result.mod_time,
        min_point_version=result.min_point_version,
        max_point_version=result.max_point_version,
        branch_index=result.branch_index,
    )

    result2 = mgr.install(io.BytesIO(result.data), manifest=manifest)

    return (id, result2)


class DownloaderInstaller(QObject):
    progressSignal = pyqtSignal(int, int)

    def __init__(self, parent: QWidget, mgr: AddonManager, client: HttpClient) -> None:
        QObject.__init__(self, parent)
        self.mgr = mgr
        self.client = client
        qconnect(self.progressSignal, self._progress_callback)

        def bg_thread_progress(up: int, down: int) -> None:
            self.progressSignal.emit(up, down)  # type: ignore

        self.client.progress_hook = bg_thread_progress

    def download(
        self, ids: List[int], on_done: Callable[[List[DownloadLogEntry]], None]
    ) -> None:
        self.ids = ids
        self.log: List[DownloadLogEntry] = []

        self.dl_bytes = 0
        self.last_tooltip = 0

        self.on_done = on_done

        parent = self.parent()
        assert isinstance(parent, QWidget)
        self.mgr.mw.progress.start(immediate=True, parent=parent)
        self.mgr.mw.taskman.run_in_background(self._download_all, self._download_done)

    def _progress_callback(self, up: int, down: int) -> None:
        self.dl_bytes += down
        self.mgr.mw.progress.update(
            label=tr.addons_downloading_adbd_kb02fkb(
                part=len(self.log) + 1,
                total=len(self.ids),
                kilobytes=self.dl_bytes // 1024,
            )
        )

    def _download_all(self) -> None:
        for id in self.ids:
            self.log.append(download_and_install_addon(self.mgr, self.client, id))

    def _download_done(self, future: Future) -> None:
        self.mgr.mw.progress.finish()
        # qt gets confused if on_done() opens new windows while the progress
        # modal is still cleaning up
        self.mgr.mw.progress.timer(50, lambda: self.on_done(self.log), False)


def show_log_to_user(parent: QWidget, log: List[DownloadLogEntry]) -> None:
    have_problem = download_encountered_problem(log)

    if have_problem:
        text = tr.addons_one_or_more_errors_occurred()
    else:
        text = tr.addons_download_complete_please_restart_anki_to()
    text += f"<br><br>{download_log_to_html(log)}"

    if have_problem:
        showWarning(text, textFormat="rich", parent=parent)
    else:
        showInfo(text, parent=parent)


def download_addons(
    parent: QWidget,
    mgr: AddonManager,
    ids: List[int],
    on_done: Callable[[List[DownloadLogEntry]], None],
    client: Optional[HttpClient] = None,
) -> None:
    if client is None:
        client = HttpClient()
    downloader = DownloaderInstaller(parent, mgr, client)
    downloader.download(ids, on_done=on_done)


# Update checking
######################################################################


class ChooseAddonsToUpdateList(QListWidget):
    ADDON_ID_ROLE = 101

    def __init__(
        self,
        parent: QWidget,
        mgr: AddonManager,
        updated_addons: List[UpdateInfo],
    ) -> None:
        QListWidget.__init__(self, parent)
        self.mgr = mgr
        self.updated_addons = sorted(
            updated_addons, key=lambda addon: addon.suitable_branch_last_modified
        )
        self.ignore_check_evt = False
        self.setup()
        self.setContextMenuPolicy(Qt.CustomContextMenu)
        qconnect(self.itemClicked, self.on_click)
        qconnect(self.itemChanged, self.on_check)
        qconnect(self.itemDoubleClicked, self.on_double_click)
        qconnect(self.customContextMenuRequested, self.on_context_menu)

    def setup(self) -> None:
        header_item = QListWidgetItem(tr.addons_choose_update_update_all(), self)
        header_item.setFlags(Qt.ItemFlag(Qt.ItemIsUserCheckable | Qt.ItemIsEnabled))
        self.header_item = header_item
        for update_info in self.updated_addons:
            addon_id = update_info.id
            addon_meta = self.mgr.addon_meta(str(addon_id))
            update_enabled = addon_meta.update_enabled
            addon_name = addon_meta.human_name()
            update_timestamp = update_info.suitable_branch_last_modified
            update_time = datetime.fromtimestamp(update_timestamp)

            addon_label = f"{update_time:%Y-%m-%d}   {addon_name}"
            item = QListWidgetItem(addon_label, self)
            # Not user checkable because it overlaps with itemClicked signal
            item.setFlags(Qt.ItemFlag(Qt.ItemIsEnabled))
            if update_enabled:
                item.setCheckState(Qt.Checked)
            else:
                item.setCheckState(Qt.Unchecked)
            item.setData(self.ADDON_ID_ROLE, addon_id)
        self.refresh_header_check_state()

    def bool_to_check(self, check_bool: bool) -> Qt.CheckState:
        if check_bool:
            return Qt.Checked
        else:
            return Qt.Unchecked

    def checked(self, item: QListWidgetItem) -> bool:
        return item.checkState() == Qt.Checked

    def on_click(self, item: QListWidgetItem) -> None:
        if item == self.header_item:
            return
        checked = self.checked(item)
        self.check_item(item, self.bool_to_check(not checked))
        self.refresh_header_check_state()

    def on_check(self, item: QListWidgetItem) -> None:
        if self.ignore_check_evt:
            return
        if item == self.header_item:
            self.header_checked(item.checkState())

    def on_double_click(self, item: QListWidgetItem) -> None:
        if item == self.header_item:
            checked = self.checked(item)
            self.check_item(self.header_item, self.bool_to_check(not checked))
            self.header_checked(self.bool_to_check(not checked))

    def on_context_menu(self, point: QPoint) -> None:
        item = self.itemAt(point)
        addon_id = item.data(self.ADDON_ID_ROLE)
        m = QMenu()
        a = m.addAction(tr.addons_view_addon_page())
        qconnect(a.triggered, lambda _: openLink(f"{aqt.appShared}info/{addon_id}"))
        m.exec_(QCursor.pos())

    def check_item(self, item: QListWidgetItem, check: Qt.CheckState) -> None:
        "call item.setCheckState without triggering on_check"
        self.ignore_check_evt = True
        item.setCheckState(check)
        self.ignore_check_evt = False

    def header_checked(self, check: Qt.CheckState) -> None:
        for i in range(1, self.count()):
            self.check_item(self.item(i), check)

    def refresh_header_check_state(self) -> None:
        for i in range(1, self.count()):
            item = self.item(i)
            if not self.checked(item):
                self.check_item(self.header_item, Qt.Unchecked)
                return
        self.check_item(self.header_item, Qt.Checked)

    def get_selected_addon_ids(self) -> List[int]:
        addon_ids = []
        for i in range(1, self.count()):
            item = self.item(i)
            if self.checked(item):
                addon_id = item.data(self.ADDON_ID_ROLE)
                addon_ids.append(addon_id)
        return addon_ids

    def save_check_state(self) -> None:
        for i in range(1, self.count()):
            item = self.item(i)
            addon_id = item.data(self.ADDON_ID_ROLE)
            addon_meta = self.mgr.addon_meta(str(addon_id))
            addon_meta.update_enabled = self.checked(item)
            self.mgr.write_addon_meta(addon_meta)


class ChooseAddonsToUpdateDialog(QDialog):
    def __init__(
        self, parent: QWidget, mgr: AddonManager, updated_addons: List[UpdateInfo]
    ) -> None:
        QDialog.__init__(self, parent)
        self.setWindowTitle(tr.addons_choose_update_window_title())
        self.setWindowModality(Qt.WindowModal)
        self.mgr = mgr
        self.updated_addons = updated_addons
        self.setup()
        restoreGeom(self, "addonsChooseUpdate")

    def setup(self) -> None:
        layout = QVBoxLayout()
        label = QLabel(tr.addons_the_following_addons_have_updates_available())
        layout.addWidget(label)
        addons_list_widget = ChooseAddonsToUpdateList(
            self, self.mgr, self.updated_addons
        )
        layout.addWidget(addons_list_widget)
        self.addons_list_widget = addons_list_widget

        button_box = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)  # type: ignore
        qconnect(button_box.button(QDialogButtonBox.Ok).clicked, self.accept)
        qconnect(button_box.button(QDialogButtonBox.Cancel).clicked, self.reject)
        layout.addWidget(button_box)
        self.setLayout(layout)

    def ask(self) -> List[int]:
        "Returns a list of selected addons' ids"
        ret = self.exec_()
        saveGeom(self, "addonsChooseUpdate")
        self.addons_list_widget.save_check_state()
        if ret == QDialog.Accepted:
            return self.addons_list_widget.get_selected_addon_ids()
        else:
            return []


def fetch_update_info(client: HttpClient, ids: List[int]) -> List[Dict]:
    """Fetch update info from AnkiWeb in one or more batches."""
    all_info: List[Dict] = []

    while ids:
        # get another chunk
        chunk = ids[:25]
        del ids[:25]

        batch_results = _fetch_update_info_batch(client, map(str, chunk))
        all_info.extend(batch_results)

    return all_info


def _fetch_update_info_batch(
    client: HttpClient, chunk: Iterable[str]
) -> Iterable[Dict]:
    """Get update info from AnkiWeb.

    Chunk must not contain more than 25 ids."""
    resp = client.get(f"{aqt.appShared}updates/{','.join(chunk)}?v=3")
    if resp.status_code == 200:
        return resp.json()
    else:
        raise Exception(f"Unexpected response code from AnkiWeb: {resp.status_code}")


def check_and_prompt_for_updates(
    parent: QWidget,
    mgr: AddonManager,
    on_done: Callable[[List[DownloadLogEntry]], None],
    requested_by_user: bool = True,
) -> None:
    def on_updates_received(client: HttpClient, items: List[Dict]) -> None:
        handle_update_info(parent, mgr, client, items, on_done, requested_by_user)

    check_for_updates(mgr, on_updates_received)


def check_for_updates(
    mgr: AddonManager, on_done: Callable[[HttpClient, List[Dict]], None]
) -> None:
    client = HttpClient()

    def check() -> List[Dict]:
        return fetch_update_info(client, mgr.ankiweb_addons())

    def update_info_received(future: Future) -> None:
        # if syncing/in profile screen, defer message delivery
        if not mgr.mw.col:
            mgr.mw.progress.timer(
                1000,
                lambda: update_info_received(future),
                False,
                requiresCollection=False,
            )
            return

        if future.exception():
            # swallow network errors
            print(str(future.exception()))
            result = []
        else:
            result = future.result()

        on_done(client, result)

    mgr.mw.taskman.run_in_background(check, update_info_received)


def extract_update_info(
    current_point_version: int, current_branch_idx: int, info_json: Dict
) -> UpdateInfo:
    "Process branches to determine the updated mod time and min/max versions."
    branches = info_json["branches"]
    try:
        current = branches[current_branch_idx]
    except IndexError:
        current = branches[0]

    last_mod = 0
    for branch in branches:
        if branch["minpt"] > current_point_version:
            continue
        if branch["maxpt"] < 0 and abs(branch["maxpt"]) < current_point_version:
            continue
        last_mod = branch["fmod"]

    return UpdateInfo(
        id=info_json["id"],
        suitable_branch_last_modified=last_mod,
        current_branch_last_modified=current["fmod"],
        current_branch_min_point_ver=current["minpt"],
        current_branch_max_point_ver=current["maxpt"],
    )


def handle_update_info(
    parent: QWidget,
    mgr: AddonManager,
    client: HttpClient,
    items: List[Dict],
    on_done: Callable[[List[DownloadLogEntry]], None],
    requested_by_user: bool = True,
) -> None:
    update_info = mgr.extract_update_info(items)
    mgr.update_supported_versions(update_info)

    updated_addons = mgr.updates_required(update_info)

    if not updated_addons:
        on_done([])
        return

    prompt_to_update(parent, mgr, client, updated_addons, on_done, requested_by_user)


def prompt_to_update(
    parent: QWidget,
    mgr: AddonManager,
    client: HttpClient,
    updated_addons: List[UpdateInfo],
    on_done: Callable[[List[DownloadLogEntry]], None],
    requested_by_user: bool = True,
) -> None:
    if not requested_by_user:
        prompt_update = False
        for addon in updated_addons:
            if mgr.addon_meta(str(addon.id)).update_enabled:
                prompt_update = True
        if not prompt_update:
            return

    ids = ChooseAddonsToUpdateDialog(parent, mgr, updated_addons).ask()
    if not ids:
        return
    download_addons(parent, mgr, ids, on_done, client)


# Editing config
######################################################################


class ConfigEditor(QDialog):
    def __init__(self, dlg: AddonsDialog, addon: str, conf: Dict) -> None:
        super().__init__(dlg)
        self.addon = addon
        self.conf = conf
        self.mgr = dlg.mgr
        self.form = aqt.forms.addonconf.Ui_Dialog()
        self.form.setupUi(self)
        restore = self.form.buttonBox.button(QDialogButtonBox.RestoreDefaults)
        qconnect(restore.clicked, self.onRestoreDefaults)
        self.setupFonts()
        self.updateHelp()
        self.updateText(self.conf)
        restoreGeom(self, "addonconf")
        restoreSplitter(self.form.splitter, "addonconf")
        self.setWindowTitle(
            without_unicode_isolation(
                tr.addons_config_window_title(
                    name=self.mgr.addon_meta(addon).human_name(),
                )
            )
        )
        disable_help_button(self)
        self.show()

    def onRestoreDefaults(self) -> None:
        default_conf = self.mgr.addonConfigDefaults(self.addon)
        self.updateText(default_conf)
        tooltip(tr.addons_restored_defaults(), parent=self)

    def setupFonts(self) -> None:
        font_mono = QFontDatabase.systemFont(QFontDatabase.FixedFont)
        font_mono.setPointSize(font_mono.pointSize() + 1)
        self.form.editor.setFont(font_mono)

    def updateHelp(self) -> None:
        txt = self.mgr.addonConfigHelp(self.addon)
        if txt:
            self.form.label.setText(txt)
        else:
            self.form.scrollArea.setVisible(False)

    def updateText(self, conf: Dict[str, Any]) -> None:
        text = json.dumps(
            conf,
            ensure_ascii=False,
            sort_keys=True,
            indent=4,
            separators=(",", ": "),
        )
        text = gui_hooks.addon_config_editor_will_display_json(text)
        self.form.editor.setPlainText(text)
        if isMac:
            self.form.editor.repaint()

    def onClose(self) -> None:
        saveGeom(self, "addonconf")
        saveSplitter(self.form.splitter, "addonconf")

    def reject(self) -> None:
        self.onClose()
        super().reject()

    def accept(self) -> None:
        txt = self.form.editor.toPlainText()
        txt = gui_hooks.addon_config_editor_will_save_json(txt)
        try:
            new_conf = json.loads(txt)
            jsonschema.validate(new_conf, self.mgr._addon_schema(self.addon))
        except ValidationError as e:
            # The user did edit the configuration and entered a value
            # which can not be interpreted.
            schema = e.schema
            erroneous_conf = new_conf
            for link in e.path:
                erroneous_conf = erroneous_conf[link]
            path = "/".join(str(path) for path in e.path)
            if "error_msg" in schema:
                msg = schema["error_msg"].format(
                    problem=e.message,
                    path=path,
                    schema=str(schema),
                    erroneous_conf=erroneous_conf,
                )
            else:
                msg = tr.addons_config_validation_error(
                    problem=e.message,
                    path=path,
                    schema=str(schema),
                )
            showInfo(msg)
            return
        except Exception as e:
            showInfo(f"{tr.addons_invalid_configuration()} {repr(e)}")
            return

        if not isinstance(new_conf, dict):
            showInfo(tr.addons_invalid_configuration_top_level_object_must())
            return

        if new_conf != self.conf:
            self.mgr.writeConfig(self.addon, new_conf)
            # does the add-on define an action to be fired?
            act = self.mgr.configUpdatedAction(self.addon)
            if act:
                act(new_conf)

        self.onClose()
        super().accept()


# .ankiaddon installation wizard
######################################################################


def installAddonPackages(
    addonsManager: AddonManager,
    paths: List[str],
    parent: Optional[QWidget] = None,
    warn: bool = False,
    strictly_modal: bool = False,
    advise_restart: bool = False,
) -> bool:

    if warn:
        names = ",<br>".join(f"<b>{os.path.basename(p)}</b>" for p in paths)
        q = tr.addons_important_as_addons_are_programs_downloaded() % dict(names=names)
        if (
            not showInfo(
                q,
                parent=parent,
                title=tr.addons_install_anki_addon(),
                type="warning",
                customBtns=[QMessageBox.No, QMessageBox.Yes],
            )
            == QMessageBox.Yes
        ):
            return False

    log, errs = addonsManager.processPackages(paths, parent=parent)

    if log:
        log_html = "<br>".join(log)
        if advise_restart:
            log_html += f"<br><br>{tr.addons_please_restart_anki_to_complete_the()}"
        if len(log) == 1 and not strictly_modal:
            tooltip(log_html, parent=parent)
        else:
            showInfo(
                log_html,
                parent=parent,
                textFormat="rich",
                title=tr.addons_installation_complete(),
            )
    if errs:
        msg = tr.addons_please_report_this_to_the_respective()
        showWarning(
            "<br><br>".join(errs + [msg]),
            parent=parent,
            textFormat="rich",
            title=tr.addons_addon_installation_error(),
        )

    return not errs
