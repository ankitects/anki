# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
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
from anki.lang import _, ngettext
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    TR,
    askUser,
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

    def __init__(self, mw: aqt.main.AnkiQt):
        self.mw = mw
        self.dirty = False
        f = self.mw.form
        f.actionAdd_ons.triggered.connect(self.onAddonsDialog)
        sys.path.insert(0, self.addonsFolder())

    # in new code, you may want all_addon_meta() instead
    def allAddons(self):
        l = []
        for d in os.listdir(self.addonsFolder()):
            path = self.addonsFolder(d)
            if not os.path.exists(os.path.join(path, "__init__.py")):
                continue
            l.append(d)
        l.sort()
        if os.getenv("ANKIREVADDONS", ""):
            l = reversed(l)
        return l

    def all_addon_meta(self) -> Iterable[AddonMeta]:
        return map(self.addon_meta, self.allAddons())

    def addonsFolder(self, dir=None):
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
            except:
                showWarning(
                    tr(
                        TR.ADDONS_FAILED_TO_LOAD,
                        name=addon.human_name(),
                        traceback=traceback.format_exc(),
                    )
                )

    def onAddonsDialog(self):
        AddonsDialog(self)

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

        self.writeAddonMeta(addon.dir_name, json_obj)

    def _addonMetaPath(self, dir):
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
    def writeAddonMeta(self, dir, meta):
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
                    _(
                        "The following add-ons are incompatible with %(name)s \
and have been disabled: %(found)s"
                    )
                    % dict(name=addon.human_name(), found=addons),
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

    def addonConflicts(self, dir) -> List[str]:
        return self.addon_meta(dir).conflicts

    def annotatedName(self, dir: str) -> str:
        meta = self.addon_meta(dir)
        name = meta.human_name()
        if not meta.enabled:
            name += _(" (disabled)")
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

    def _disableConflicting(self, dir, conflicts=None):
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

    def readManifestFile(self, zfile):
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
        self, file: Union[IO, str], manifest: dict = None
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

    def _install(self, dir, zfile):
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
    def deleteAddon(self, dir):
        try:
            send2trash(self.addonsFolder(dir))
            return True
        except OSError as e:
            showWarning(
                _(
                    "Unable to update or delete add-on. Please start Anki while holding down the shift key to temporarily disable add-ons, then try again.\n\nDebug info: %s"
                )
                % e,
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

        self.mw.progress.start(immediate=True, parent=parent)
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
            "zip": _("Corrupt add-on file."),
            "manifest": _("Invalid add-on manifest."),
        }

        msg = messages.get(result.errmsg, _("Unknown error: {}".format(result.errmsg)))

        if mode == "download":  # preserve old format strings for i18n
            template = _("Error downloading <i>%(id)s</i>: %(error)s")
        else:
            template = _("Error installing <i>%(base)s</i>: %(error)s")

        name = base

        return [template % dict(base=name, id=name, error=msg)]

    def _installationSuccessReport(
        self, result: InstallOk, base: str, mode: str = "download"
    ) -> List[str]:

        if mode == "download":  # preserve old format strings for i18n
            template = _("Downloaded %(fname)s")
        else:
            template = _("Installed %(name)s")

        name = result.name or base
        strings = [template % dict(name=name, fname=name)]

        if result.conflicts:
            strings.append(
                _("The following conflicting add-ons were disabled:")
                + " "
                + ", ".join(self.addonName(f) for f in result.conflicts)
            )

        if not result.compatible:
            strings.append(
                _("This add-on is not compatible with your version of Anki.")
            )

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

    def update_supported_version(self, item: UpdateInfo):
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

    def updates_required(self, items: List[UpdateInfo]) -> List[int]:
        """Return ids of add-ons requiring an update."""
        need_update = []
        for item in items:
            addon = self.addon_meta(str(item.id))
            # update if server mtime is newer
            if not addon.is_latest(item.suitable_branch_last_modified):
                need_update.append(item.id)
            elif not addon.compatible() and item.suitable_branch_last_modified > 0:
                # Addon is currently disabled, and a suitable branch was found on the
                # server. Ignore our stored mtime (which may have been set incorrectly
                # in the past) and require an update.
                need_update.append(item.id)

        return need_update

    # Add-on Config
    ######################################################################

    _configButtonActions: Dict[str, Callable[[], Optional[bool]]] = {}
    _configUpdatedActions: Dict[str, Callable[[Any], None]] = {}

    def addonConfigDefaults(self, dir):
        path = os.path.join(self.addonsFolder(dir), "config.json")
        try:
            with open(path, encoding="utf8") as f:
                return json.load(f)
        except:
            return None

    def addonConfigHelp(self, dir):
        path = os.path.join(self.addonsFolder(dir), "config.md")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                return markdown.markdown(f.read())
        else:
            return ""

    def addonFromModule(self, module):
        return module.split(".")[0]

    def configAction(self, addon: str) -> Callable[[], Optional[bool]]:
        return self._configButtonActions.get(addon)

    def configUpdatedAction(self, addon: str) -> Callable[[Any], None]:
        return self._configUpdatedActions.get(addon)

    # Schema
    ######################################################################

    def _addon_schema_path(self, dir):
        return os.path.join(self.addonsFolder(dir), "config.schema.json")

    def _addon_schema(self, dir):
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

    def getConfig(self, module: str) -> Optional[dict]:
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

    def setConfigAction(self, module: str, fn: Callable[[], Optional[bool]]):
        addon = self.addonFromModule(module)
        self._configButtonActions[addon] = fn

    def setConfigUpdatedAction(self, module: str, fn: Callable[[Any], None]):
        addon = self.addonFromModule(module)
        self._configUpdatedActions[addon] = fn

    def writeConfig(self, module: str, conf: dict):
        addon = self.addonFromModule(module)
        meta = self.addonMeta(addon)
        meta["config"] = conf
        self.writeAddonMeta(addon, meta)

    # user_files
    ######################################################################

    def _userFilesPath(self, sid):
        return os.path.join(self.addonsFolder(sid), "user_files")

    def _userFilesBackupPath(self):
        return os.path.join(self.addonsFolder(), "files_backup")

    def backupUserFiles(self, sid):
        p = self._userFilesPath(sid)
        if os.path.exists(p):
            os.rename(p, self._userFilesBackupPath())

    def restoreUserFiles(self, sid):
        p = self._userFilesPath(sid)
        bp = self._userFilesBackupPath()
        # did we back up userFiles?
        if not os.path.exists(bp):
            return
        os.rename(bp, p)

    # Web Exports
    ######################################################################

    _webExports: Dict[str, str] = {}

    def setWebExports(self, module: str, pattern: str):
        addon = self.addonFromModule(module)
        self._webExports[addon] = pattern

    def getWebExports(self, addon):
        return self._webExports.get(addon)


# Add-ons Dialog
######################################################################


class AddonsDialog(QDialog):
    def __init__(self, addonsManager: AddonManager):
        self.mgr = addonsManager
        self.mw = addonsManager.mw

        super().__init__(self.mw)

        f = self.form = aqt.forms.addons.Ui_Dialog()
        f.setupUi(self)
        f.getAddons.clicked.connect(self.onGetAddons)
        f.installFromFile.clicked.connect(self.onInstallFiles)
        f.checkForUpdates.clicked.connect(self.check_for_updates)
        f.toggleEnabled.clicked.connect(self.onToggleEnabled)
        f.viewPage.clicked.connect(self.onViewPage)
        f.viewFiles.clicked.connect(self.onViewFiles)
        f.delete_2.clicked.connect(self.onDelete)
        f.config.clicked.connect(self.onConfig)
        self.form.addonList.itemDoubleClicked.connect(self.onConfig)
        self.form.addonList.currentRowChanged.connect(self._onAddonItemSelected)
        self.setAcceptDrops(True)
        self.redrawAddons()
        restoreGeom(self, "addons")
        gui_hooks.addons_dialog_will_show(self)
        self.show()

    def dragEnterEvent(self, event):
        mime = event.mimeData()
        if not mime.hasUrls():
            return None
        urls = mime.urls()
        ext = self.mgr.ext
        if all(url.toLocalFile().endswith(ext) for url in urls):
            event.acceptProposedAction()

    def dropEvent(self, event):
        mime = event.mimeData()
        paths = []
        for url in mime.urls():
            path = url.toLocalFile()
            if os.path.exists(path):
                paths.append(path)
        self.onInstallFiles(paths)

    def reject(self):
        saveGeom(self, "addons")
        return QDialog.reject(self)

    def name_for_addon_list(self, addon: AddonMeta) -> str:
        name = addon.human_name()

        if not addon.enabled:
            return name + " " + _("(disabled)")
        elif not addon.compatible():
            return name + " " + _("(requires %s)") % self.compatible_string(addon)

        return name

    def compatible_string(self, addon: AddonMeta) -> str:
        min = addon.min_point_version
        if min is not None and min > current_point_version:
            return f"Anki >= 2.1.{min}"
        else:
            max = abs(addon.max_point_version)
            return f"Anki <= 2.1.{max}"

    def should_grey(self, addon: AddonMeta):
        return not addon.enabled or not addon.compatible()

    def redrawAddons(self,) -> None:
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

    def onlyOneSelected(self):
        dirs = self.selectedAddons()
        if len(dirs) != 1:
            showInfo(_("Please select a single add-on first."))
            return
        return dirs[0]

    def onToggleEnabled(self):
        for dir in self.selectedAddons():
            self.mgr.toggleEnabled(dir)
        self.redrawAddons()

    def onViewPage(self):
        addon = self.onlyOneSelected()
        if not addon:
            return
        if re.match(r"^\d+$", addon):
            openLink(aqt.appShared + "info/{}".format(addon))
        else:
            showWarning(_("Add-on was not downloaded from AnkiWeb."))

    def onViewFiles(self):
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

    def onDelete(self):
        selected = self.selectedAddons()
        if not selected:
            return
        if not askUser(
            ngettext(
                "Delete the %(num)d selected add-on?",
                "Delete the %(num)d selected add-ons?",
                len(selected),
            )
            % dict(num=len(selected))
        ):
            return
        for dir in selected:
            if not self.mgr.deleteAddon(dir):
                break
        self.form.addonList.clearSelection()
        self.redrawAddons()

    def onGetAddons(self):
        obj = GetAddons(self)
        if obj.ids:
            download_addons(self, self.mgr, obj.ids, self.after_downloading)

    def after_downloading(self, log: List[DownloadLogEntry]):
        self.redrawAddons()
        if log:
            show_log_to_user(self, log)
        else:
            tooltip(_("No updates available."))

    def onInstallFiles(self, paths: Optional[List[str]] = None):
        if not paths:
            key = _("Packaged Anki Add-on") + " (*{})".format(self.mgr.ext)
            paths = getFile(
                self, _("Install Add-on(s)"), None, key, key="addons", multi=True
            )
            if not paths:
                return False

        installAddonPackages(self.mgr, paths, parent=self)

        self.redrawAddons()

    def check_for_updates(self):
        tooltip(_("Checking..."))
        check_and_prompt_for_updates(self, self.mgr, self.after_downloading)

    def onConfig(self):
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
            showInfo(_("Add-on has no configuration."))
            return

        ConfigEditor(self, addon, conf)


# Fetching Add-ons
######################################################################


class GetAddons(QDialog):
    def __init__(self, dlg):
        QDialog.__init__(self, dlg)
        self.addonsDlg = dlg
        self.mgr = dlg.mgr
        self.mw = self.mgr.mw
        self.ids: List[int] = []
        self.form = aqt.forms.getaddons.Ui_Dialog()
        self.form.setupUi(self)
        b = self.form.buttonBox.addButton(
            _("Browse Add-ons"), QDialogButtonBox.ActionRole
        )
        b.clicked.connect(self.onBrowse)
        restoreGeom(self, "getaddons", adjustSize=True)
        self.exec_()
        saveGeom(self, "getaddons")

    def onBrowse(self):
        openLink(aqt.appShared + "addons/2.1")

    def accept(self):
        # get codes
        try:
            ids = [int(n) for n in self.form.code.text().split()]
        except ValueError:
            showWarning(_("Invalid code."))
            return

        self.ids = ids
        QDialog.accept(self)


# Downloading
######################################################################


def download_addon(client: HttpClient, id: int) -> Union[DownloadOk, DownloadError]:
    "Fetch a single add-on from AnkiWeb."
    try:
        resp = client.get(
            aqt.appShared + f"download/{id}?v=2.1&p={current_point_version}"
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
                buf += _(
                    "Invalid code, or add-on not available for your version of Anki."
                )
            else:
                buf += _("Unexpected response code: %s") % entry.status_code
        else:
            buf += (
                _("Please check your internet connection.")
                + "\n\n"
                + str(entry.exception)
            )
    elif isinstance(entry, InstallError):
        buf += entry.errmsg
    else:
        buf += _("Installed successfully.")

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
    name = os.path.splitext(fname)[0]

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
        self.progressSignal.connect(self._progress_callback)  # type: ignore

        def bg_thread_progress(up, down) -> None:
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

        self.mgr.mw.progress.start(immediate=True, parent=self.parent())
        self.mgr.mw.taskman.run_in_background(self._download_all, self._download_done)

    def _progress_callback(self, up: int, down: int) -> None:
        self.dl_bytes += down
        self.mgr.mw.progress.update(
            # T: "%(a)d" is the index of the element currently
            # downloaded. "%(b)d" is the number of element to download,
            # and "%(kb)0.2f" is the number of downloaded
            # kilobytes. This lead for example to "Downloading 3/5
            # (27KB)"
            label=_("Downloading %(a)d/%(b)d (%(kb)0.2fKB)...")
            % dict(a=len(self.log) + 1, b=len(self.ids), kb=self.dl_bytes / 1024)
        )

    def _download_all(self):
        for id in self.ids:
            self.log.append(download_and_install_addon(self.mgr, self.client, id))

    def _download_done(self, future):
        self.mgr.mw.progress.finish()
        # qt gets confused if on_done() opens new windows while the progress
        # modal is still cleaning up
        self.mgr.mw.progress.timer(50, lambda: self.on_done(self.log), False)


def show_log_to_user(parent: QWidget, log: List[DownloadLogEntry]) -> None:
    have_problem = download_encountered_problem(log)

    if have_problem:
        text = _("One or more errors occurred:")
    else:
        text = _("Download complete. Please restart Anki to apply changes.")
    text += "<br><br>" + download_log_to_html(log)

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
    resp = client.get(aqt.appShared + "updates/" + ",".join(chunk) + "?v=3")
    if resp.status_code == 200:
        return resp.json()
    else:
        raise Exception(
            "Unexpected response code from AnkiWeb: {}".format(resp.status_code)
        )


def check_and_prompt_for_updates(
    parent: QWidget,
    mgr: AddonManager,
    on_done: Callable[[List[DownloadLogEntry]], None],
):
    def on_updates_received(client: HttpClient, items: List[Dict]):
        handle_update_info(parent, mgr, client, items, on_done)

    check_for_updates(mgr, on_updates_received)


def check_for_updates(
    mgr: AddonManager, on_done: Callable[[HttpClient, List[Dict]], None]
):
    client = HttpClient()

    def check():
        return fetch_update_info(client, mgr.ankiweb_addons())

    def update_info_received(future: Future):
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
) -> None:
    update_info = mgr.extract_update_info(items)
    mgr.update_supported_versions(update_info)

    updated_ids = mgr.updates_required(update_info)

    if not updated_ids:
        on_done([])
        return

    prompt_to_update(parent, mgr, client, updated_ids, on_done)


def prompt_to_update(
    parent: QWidget,
    mgr: AddonManager,
    client: HttpClient,
    ids: List[int],
    on_done: Callable[[List[DownloadLogEntry]], None],
) -> None:
    names = map(lambda x: mgr.addonName(str(x)), ids)
    if not askUser(
        _("The following add-ons have updates available. Install them now?")
        + "\n\n"
        + "\n".join(names)
    ):
        # on_done is not called if the user cancels
        return

    download_addons(parent, mgr, ids, on_done, client)


# Editing config
######################################################################


class ConfigEditor(QDialog):
    def __init__(self, dlg, addon, conf):
        super().__init__(dlg)
        self.addon = addon
        self.conf = conf
        self.mgr = dlg.mgr
        self.form = aqt.forms.addonconf.Ui_Dialog()
        self.form.setupUi(self)
        restore = self.form.buttonBox.button(QDialogButtonBox.RestoreDefaults)
        restore.clicked.connect(self.onRestoreDefaults)
        self.setupFonts()
        self.updateHelp()
        self.updateText(self.conf)
        restoreGeom(self, "addonconf")
        restoreSplitter(self.form.splitter, "addonconf")
        self.setWindowTitle(
            tr(
                TR.ADDONS_CONFIG_WINDOW_TITLE,
                name=self.mgr.addon_meta(addon).human_name(),
            )
        )
        self.show()

    def onRestoreDefaults(self):
        default_conf = self.mgr.addonConfigDefaults(self.addon)
        self.updateText(default_conf)
        tooltip(_("Restored defaults"), parent=self)

    def setupFonts(self):
        font_mono = QFontDatabase.systemFont(QFontDatabase.FixedFont)
        font_mono.setPointSize(font_mono.pointSize() + 1)
        self.form.editor.setFont(font_mono)

    def updateHelp(self):
        txt = self.mgr.addonConfigHelp(self.addon)
        if txt:
            self.form.label.setText(txt)
        else:
            self.form.scrollArea.setVisible(False)

    def updateText(self, conf):
        text = json.dumps(
            conf, ensure_ascii=False, sort_keys=True, indent=4, separators=(",", ": "),
        )
        text = gui_hooks.addon_config_editor_will_display_json(text)
        self.form.editor.setPlainText(text)

    def onClose(self):
        saveGeom(self, "addonconf")
        saveSplitter(self.form.splitter, "addonconf")

    def reject(self):
        self.onClose()
        super().reject()

    def accept(self):
        txt = self.form.editor.toPlainText()
        txt = gui_hooks.addon_config_editor_will_save_json(txt)
        try:
            new_conf = json.loads(txt)
            jsonschema.validate(new_conf, self.parent().mgr._addon_schema(self.addon))
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
                msg = tr(
                    TR.ADDONS_CONFIG_VALIDATION_ERROR,
                    problem=e.message,
                    path=path,
                    schema=str(schema),
                )
            showInfo(msg)
            return
        except Exception as e:
            showInfo(_("Invalid configuration: ") + repr(e))
            return

        if not isinstance(new_conf, dict):
            showInfo(_("Invalid configuration: top level object must be a map"))
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
        q = _(
            "<b>Important</b>: As add-ons are programs downloaded from the internet, "
            "they are potentially malicious."
            "<b>You should only install add-ons you trust.</b><br><br>"
            "Are you sure you want to proceed with the installation of the "
            "following Anki add-on(s)?<br><br>%(names)s"
        ) % dict(names=names)
        if (
            not showInfo(
                q,
                parent=parent,
                title=_("Install Anki add-on"),
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
            log_html += "<br><br>" + _(
                "<b>Please restart Anki to complete the installation.</b>"
            )
        if len(log) == 1 and not strictly_modal:
            tooltip(log_html, parent=parent)
        else:
            showInfo(
                log_html,
                parent=parent,
                textFormat="rich",
                title=_("Installation complete"),
            )
    if errs:
        msg = _("Please report this to the respective add-on author(s).")
        showWarning(
            "<br><br>".join(errs + [msg]),
            parent=parent,
            textFormat="rich",
            title=_("Add-on installation error"),
        )

    return not errs
