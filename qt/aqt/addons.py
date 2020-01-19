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
from anki.utils import intTime
from aqt.qt import *
from aqt.utils import (
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
)


@dataclass
class InstallOk:
    name: str
    conflicts: List[str]


@dataclass
class InstallError:
    errmsg: str


@dataclass
class DownloadOk:
    data: bytes
    filename: str


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
    last_updated: int
    max_point_version: Optional[int]


ANKIWEB_ID_RE = re.compile(r"^\d+$")

pointVersion = anki.utils.pointVersion()


@dataclass
class AddonMeta:
    dir_name: str
    provided_name: Optional[str]
    enabled: bool
    installed_at: int
    conflicts: List[str]
    max_point_version: Optional[int]

    def human_name(self) -> str:
        return self.provided_name or self.dir_name

    def ankiweb_id(self) -> Optional[int]:
        m = ANKIWEB_ID_RE.match(self.dir_name)
        if m:
            return int(m.group(0))
        else:
            return None

    def compatible(self) -> bool:
        if self.max_point_version is None:
            return True
        return pointVersion <= self.max_point_version


def addon_meta(dir_name: str, json_meta: Dict[str, Any]) -> AddonMeta:
    return AddonMeta(
        dir_name=dir_name,
        provided_name=json_meta.get("name", dir_name),
        enabled=not json_meta.get("disabled"),
        installed_at=json_meta.get("mod", 0),
        conflicts=json_meta.get("conflicts", []),
        max_point_version=json_meta.get("max_point_version"),
    )


# fixme: this class should not have any GUI code in it
class AddonManager:

    ext: str = ".ankiaddon"
    _manifest_schema: dict = {
        "type": "object",
        "properties": {
            "package": {"type": "string", "meta": False},
            "name": {"type": "string", "meta": True},
            "mod": {"type": "number", "meta": True},
            "conflicts": {"type": "array", "items": {"type": "string"}, "meta": True},
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
                    _(
                        """\
An add-on you installed failed to load. If problems persist, please \
go to the Tools>Add-ons menu, and disable or delete the add-on.

When loading '%(name)s':
%(traceback)s
"""
                    )
                    % dict(name=addon.human_name(), traceback=traceback.format_exc())
                )

    def onAddonsDialog(self):
        AddonsDialog(self)

    # Metadata
    ######################################################################

    def addon_meta(self, dir_name: str) -> AddonMeta:
        """Get info about an installed add-on."""
        json_obj = self.addonMeta(dir_name)
        return addon_meta(dir_name, json_obj)

    def write_addon_meta(self, addon: AddonMeta) -> None:
        # preserve any unknown attributes
        json_obj = self.addonMeta(addon.dir_name)

        if addon.provided_name is not None:
            json_obj["name"] = addon.provided_name
        json_obj["disabled"] = not addon.enabled
        json_obj["mod"] = addon.installed_at
        json_obj["conflicts"] = addon.conflicts
        json_obj["max_point_version"] = addon.max_point_version

        self.writeAddonMeta(addon.dir_name, json_obj)

    def _addonMetaPath(self, dir):
        return os.path.join(self.addonsFolder(dir), "meta.json")

    # in new code, use addon_meta() instead
    def addonMeta(self, dir: str) -> Dict[str, Any]:
        path = self._addonMetaPath(dir)
        try:
            with open(path, encoding="utf8") as f:
                return json.load(f)
        except:
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

    def enabled_ankiweb_addons(self) -> List[int]:
        ids = []
        for meta in self.all_addon_meta():
            if meta.ankiweb_id() is not None and meta.enabled:
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

        return InstallOk(name=meta["name"], conflicts=found_conflicts)

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

        return strings

    # Updating
    ######################################################################

    def update_max_supported_versions(self, items: List[UpdateInfo]) -> None:
        for item in items:
            self.update_max_supported_version(item)

    def update_max_supported_version(self, item: UpdateInfo):
        addon = self.addon_meta(str(item.id))

        # if different to the stored value
        if addon.max_point_version != item.max_point_version:
            # max version currently specified?
            if item.max_point_version is not None:
                addon.max_point_version = item.max_point_version
                self.write_addon_meta(addon)
            else:
                # no max currently specified. we can clear any
                # existing record provided the user is up to date
                if self.addon_is_latest(item.id, item.last_updated):
                    addon.max_point_version = item.max_point_version
                    self.write_addon_meta(addon)

    def updates_required(self, items: List[UpdateInfo]) -> List[int]:
        """Return ids of add-ons requiring an update."""
        need_update = []
        for item in items:
            if not self.addon_is_latest(item.id, item.last_updated):
                need_update.append(item.id)

        return need_update

    def addon_is_latest(self, id: int, server_update: int) -> bool:
        meta = self.addon_meta(str(id))
        return meta.installed_at >= server_update

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
            return name + " " + _("(not compatible)")

        return name

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
        resp = client.get(aqt.appShared + f"download/{id}?v=2.1&p={pointVersion}")
        if resp.status_code != 200:
            return DownloadError(status_code=resp.status_code)

        data = client.streamContent(resp)

        fname = re.match(
            "attachment; filename=(.+)", resp.headers["content-disposition"]
        ).group(1)

        return DownloadOk(data=data, filename=fname)
    except Exception as e:
        return DownloadError(exception=e)


def download_log_to_html(log: List[DownloadLogEntry]) -> str:
    return "\n".join(map(describe_log_entry, log))


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

    result2 = mgr.install(
        io.BytesIO(result.data),
        manifest={"package": str(id), "name": name, "mod": intTime()},
    )

    return (id, result2)


class DownloaderInstaller(QObject):
    progressSignal = pyqtSignal(int, int)

    def __init__(self, parent: QWidget, mgr: AddonManager, client: HttpClient) -> None:
        QObject.__init__(self, parent)
        self.mgr = mgr
        self.client = client
        self.progressSignal.connect(self._progress_callback)  # type: ignore

        def bg_thread_progress(up, down):
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
        self.mgr.mw.taskman.run(self._download_all, self._download_done)

    def _progress_callback(self, up: int, down: int) -> None:
        self.dl_bytes += down
        self.mgr.mw.progress.update(
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


def fetch_update_info(client: HttpClient, ids: List[int]) -> List[UpdateInfo]:
    """Fetch update info from AnkiWeb in one or more batches."""
    all_info: List[UpdateInfo] = []

    while ids:
        # get another chunk
        chunk = ids[:25]
        del ids[:25]

        batch_results = _fetch_update_info_batch(client, map(str, chunk))
        all_info.extend(batch_results)

    return all_info


def _fetch_update_info_batch(
    client: HttpClient, chunk: Iterable[str]
) -> Iterable[UpdateInfo]:
    """Get update info from AnkiWeb.

    Chunk must not contain more than 25 ids."""
    resp = client.get(aqt.appShared + "updates/" + ",".join(chunk) + "?v=2")
    if resp.status_code == 200:
        return json_update_info_to_native(resp.json())
    else:
        raise Exception(
            "Unexpected response code from AnkiWeb: {}".format(resp.status_code)
        )


def json_update_info_to_native(json_obj: List[Dict]) -> Iterable[UpdateInfo]:
    def from_json(d: Dict[str, Any]) -> UpdateInfo:
        return UpdateInfo(
            id=d["id"], last_updated=d["updated"], max_point_version=d["maxver"]
        )

    return map(from_json, json_obj)


def check_and_prompt_for_updates(
    parent: QWidget,
    mgr: AddonManager,
    on_done: Callable[[List[DownloadLogEntry]], None],
):
    def on_updates_received(client: HttpClient, items: List[UpdateInfo]):
        handle_update_info(parent, mgr, client, items, on_done)

    check_for_updates(mgr, on_updates_received)


def check_for_updates(
    mgr: AddonManager, on_done: Callable[[HttpClient, List[UpdateInfo]], None]
):
    client = HttpClient()

    def check():
        return fetch_update_info(client, mgr.enabled_ankiweb_addons())

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

    mgr.mw.taskman.run(check, update_info_received)


def handle_update_info(
    parent: QWidget,
    mgr: AddonManager,
    client: HttpClient,
    items: List[UpdateInfo],
    on_done: Callable[[List[DownloadLogEntry]], None],
) -> None:
    # record maximum supported versions
    mgr.update_max_supported_versions(items)

    updated_ids = mgr.updates_required(items)

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
        self.form.editor.setPlainText(
            json.dumps(
                conf,
                ensure_ascii=False,
                sort_keys=True,
                indent=4,
                separators=(",", ": "),
            )
        )

    def onClose(self):
        saveGeom(self, "addonconf")
        saveSplitter(self.form.splitter, "addonconf")

    def reject(self):
        self.onClose()
        super().reject()

    def accept(self):
        txt = self.form.editor.toPlainText()
        try:
            new_conf = json.loads(txt)
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
