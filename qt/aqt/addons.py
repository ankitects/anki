# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
import io
import json
import logging
import os
import re
import sys
import traceback
import zipfile
from collections import defaultdict
from collections.abc import Callable, Iterable, Sequence
from concurrent.futures import Future
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import IO, Any, Union
from urllib.parse import parse_qs, urlparse
from zipfile import ZipFile

import jsonschema
import markdown
from jsonschema.exceptions import ValidationError
from markdown.extensions import md_in_html

import anki
import anki.utils
import aqt
import aqt.forms
import aqt.main
from anki.collection import AddonInfo
from anki.httpclient import HttpClient
from anki.lang import without_unicode_isolation
from anki.utils import int_version_to_str
from aqt import gui_hooks
from aqt.log import ADDON_LOGGER_PREFIX, find_addon_logger, get_addon_logs_folder
from aqt.qt import *
from aqt.utils import (
    askUser,
    disable_help_button,
    getFile,
    is_win,
    openFolder,
    openLink,
    restoreGeom,
    restoreSplitter,
    saveGeom,
    saveSplitter,
    send_to_trash,
    show_info,
    showInfo,
    showText,
    showWarning,
    supportText,
    tooltip,
    tr,
)


class AbortAddonImport(Exception):
    """If raised during add-on import, Anki will silently ignore this exception.
    This allows you to terminate loading without an error being shown."""


@dataclass
class InstallOk:
    name: str
    conflicts: set[str]
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
    status_code: int | None = None
    # set if an exception occurred
    exception: Exception | None = None


# first arg is add-on id
DownloadLogEntry = tuple[int, Union[DownloadError, InstallError, InstallOk]]


ANKIWEB_ID_RE = re.compile(r"^\d+$")

_current_version = anki.utils.int_version()


@dataclass
class AddonMeta:
    dir_name: str
    provided_name: str | None
    enabled: bool
    installed_at: int
    conflicts: list[str]
    min_version: int
    max_version: int
    branch_index: int
    human_version: str | None
    update_enabled: bool
    homepage: str | None

    def human_name(self) -> str:
        return self.provided_name or self.dir_name

    def ankiweb_id(self) -> int | None:
        m = ANKIWEB_ID_RE.match(self.dir_name)
        if m:
            return int(m.group(0))
        else:
            return None

    def compatible(self) -> bool:
        min = self.min_version
        if min is not None and _current_version < min:
            return False
        max = self.max_version
        if max is not None and max < 0 and _current_version > abs(max):
            return False
        return True

    def is_latest(self, server_update_time: int) -> bool:
        return self.installed_at >= server_update_time

    def page(self) -> str | None:
        if self.ankiweb_id():
            return f"{aqt.appShared}info/{self.dir_name}"
        return self.homepage

    @staticmethod
    def from_json_meta(dir_name: str, json_meta: dict[str, Any]) -> AddonMeta:
        return AddonMeta(
            dir_name=dir_name,
            provided_name=json_meta.get("name"),
            enabled=not json_meta.get("disabled"),
            installed_at=json_meta.get("mod", 0),
            conflicts=json_meta.get("conflicts", []),
            min_version=json_meta.get("min_point_version", 0) or 0,
            max_version=json_meta.get("max_point_version", 0) or 0,
            branch_index=json_meta.get("branch_index", 0) or 0,
            human_version=json_meta.get("human_version"),
            update_enabled=json_meta.get("update_enabled", True),
            homepage=json_meta.get("homepage"),
        )


def package_name_valid(name: str) -> bool:
    # embedded /?
    base = os.path.basename(name)
    if base != name:
        return False
    # tries to escape to parent?
    root = os.getcwd()
    subfolder = os.path.abspath(os.path.join(root, name))
    if root.startswith(subfolder):
        return False
    return True


# fixme: this class should not have any GUI code in it
class AddonManager:
    exts: list[str] = [".ankiaddon", ".zip"]
    _manifest_schema: dict = {
        "type": "object",
        "properties": {
            # the name of the folder
            "package": {"type": "string", "minLength": 1, "meta": False},
            # the displayed name to the user
            "name": {"type": "string", "meta": True},
            # the time the add-on was last modified
            "mod": {"type": "number", "meta": True},
            # a list of other packages that conflict
            "conflicts": {"type": "array", "items": {"type": "string"}, "meta": True},
            # x for anki 2.1.x; int_version() for more recent releases
            "min_point_version": {"type": "number", "meta": True},
            # x for anki 2.1.x; int_version() for more recent releases
            # if negative, abs(n) is the maximum version this add-on supports
            # if positive, indicates version tested on, and is ignored
            "max_point_version": {"type": "number", "meta": True},
            # AnkiWeb sends this to indicate which branch the user downloaded.
            "branch_index": {"type": "number", "meta": True},
            # version string set by the add-on creator
            "human_version": {"type": "string", "meta": True},
            # add-on page on AnkiWeb or some other webpage
            "homepage": {"type": "string", "meta": True},
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
    def allAddons(self) -> list[str]:
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

    def addonsFolder(self, module: str | None = None) -> str:
        root = self.mw.pm.addonFolder()
        if module is None:
            return root
        return os.path.join(root, module)

    def loadAddons(self) -> None:
        from aqt import mw

        broken: list[str] = []
        error_text = ""
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
            except Exception:
                name = html.escape(addon.human_name())
                page = addon.page()
                if page:
                    broken.append(f"<a href={page}>{name}</a>")
                else:
                    broken.append(name)
                tb = traceback.format_exc()
                print(tb)
                error_text += f"When loading {name}:\n{tb}\n"

        if broken:
            addons = "\n\n- " + "\n- ".join(broken)
            error = tr.addons_failed_to_load2(
                addons=addons,
            )
            txt = f"# {tr.addons_startup_failed()}\n{error}"
            html2 = markdown.markdown(txt)
            box: QDialogButtonBox
            (diag, box) = showText(
                html2,
                type="html",
                run=False,
            )

            def on_check() -> None:
                tooltip(tr.addons_checking())

                def on_done(log: list[DownloadLogEntry]) -> None:
                    if not log:
                        tooltip(tr.addons_no_updates_available())

                mw.check_for_addon_updates(by_user=True, on_done=on_done)

            def on_copy() -> None:
                txt = supportText() + "\n" + error_text
                QApplication.clipboard().setText(txt)
                tooltip(tr.about_copied_to_clipboard(), parent=diag)

            check = box.addButton(
                tr.addons_check_for_updates(), QDialogButtonBox.ButtonRole.ActionRole
            )
            check.clicked.connect(on_check)

            copy = box.addButton(
                tr.about_copy_debug_info(), QDialogButtonBox.ButtonRole.ActionRole
            )
            copy.clicked.connect(on_copy)

            # calling show immediately appears to crash
            mw.progress.single_shot(1000, diag.show)

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
        json_obj["max_point_version"] = addon.max_version
        json_obj["min_point_version"] = addon.min_version
        json_obj["branch_index"] = addon.branch_index
        if addon.human_version is not None:
            json_obj["human_version"] = addon.human_version
        json_obj["update_enabled"] = addon.update_enabled

        self.writeAddonMeta(addon.dir_name, json_obj)

    def _addonMetaPath(self, module: str) -> str:
        return os.path.join(self.addonsFolder(module), "meta.json")

    # in new code, use self.addon_meta() instead
    def addonMeta(self, module: str) -> dict[str, Any]:
        path = self._addonMetaPath(module)
        try:
            with open(path, encoding="utf8") as f:
                return json.load(f)
        except json.JSONDecodeError as e:
            print(f"json error in add-on {module}:\n{e}")
            return dict()
        except Exception:
            # missing meta file, etc
            return dict()

    # in new code, use write_addon_meta() instead
    def writeAddonMeta(self, module: str, meta: dict[str, Any]) -> None:
        path = self._addonMetaPath(module)
        with open(path, "w", encoding="utf8") as f:
            json.dump(meta, f)

    def toggleEnabled(self, module: str, enable: bool | None = None) -> None:
        addon = self.addon_meta(module)
        should_enable = enable if enable is not None else not addon.enabled
        if should_enable is True:
            conflicting = self._disableConflicting(module)
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

    def ankiweb_addons(self) -> list[int]:
        ids = []
        for meta in self.all_addon_meta():
            if meta.ankiweb_id() is not None:
                ids.append(meta.ankiweb_id())
        return ids

    # Legacy helpers
    ######################################################################

    def isEnabled(self, module: str) -> bool:
        return self.addon_meta(module).enabled

    def addonName(self, module: str) -> str:
        return self.addon_meta(module).human_name()

    def addonConflicts(self, module: str) -> list[str]:
        return self.addon_meta(module).conflicts

    def annotatedName(self, module: str) -> str:
        meta = self.addon_meta(module)
        name = meta.human_name()
        if not meta.enabled:
            name += f" {tr.addons_disabled()}"
        return name

    # Conflict resolution
    ######################################################################

    def allAddonConflicts(self) -> dict[str, list[str]]:
        all_conflicts: dict[str, list[str]] = defaultdict(list)
        for addon in self.all_addon_meta():
            if not addon.enabled:
                continue
            for other_dir in addon.conflicts:
                all_conflicts[other_dir].append(addon.dir_name)
        return all_conflicts

    def _disableConflicting(
        self, module: str, conflicts: list[str] | None = None
    ) -> set[str]:
        if not self.isEnabled(module):
            # disabled add-ons should not trigger conflict handling
            return set()

        conflicts = conflicts or self.addonConflicts(module)

        installed = self.allAddons()
        found = {d for d in conflicts if d in installed and self.isEnabled(d)}
        found.update(self.allAddonConflicts().get(module, []))

        for package in found:
            self.toggleEnabled(package, enable=False)

        return found

    # Installing and deleting add-ons
    ######################################################################

    def readManifestFile(self, zfile: ZipFile) -> dict[Any, Any]:
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
        self,
        file: IO | str,
        manifest: dict[str, Any] | None = None,
        force_enable: bool = False,
    ) -> InstallOk | InstallError:
        """Install add-on from path or file-like object. Metadata is read
        from the manifest file, with keys overridden by supplying a 'manifest'
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
            if not package_name_valid(package):
                return InstallError(errmsg="invalid package")
            conflicts = manifest.get("conflicts", [])
            found_conflicts = self._disableConflicting(package, conflicts)
            meta = self.addonMeta(package)
            gui_hooks.addon_manager_will_install_addon(self, package)
            self._install(package, zfile)
            gui_hooks.addon_manager_did_install_addon(self, package)

        schema = self._manifest_schema["properties"]
        manifest_meta = {
            k: v for k, v in manifest.items() if k in schema and schema[k]["meta"]
        }
        meta.update(manifest_meta)

        if force_enable:
            meta["disabled"] = False

        self.writeAddonMeta(package, meta)

        meta2 = self.addon_meta(package)

        return InstallOk(
            name=meta["name"], conflicts=found_conflicts, compatible=meta2.compatible()
        )

    def _install(self, module: str, zfile: ZipFile) -> None:
        # previously installed?
        base = self.addonsFolder(module)
        if os.path.exists(base):
            self.backupUserFiles(module)
            try:
                self.deleteAddon(module)
            except Exception:
                self.restoreUserFiles(module)
                raise
        os.mkdir(base)
        self.restoreUserFiles(module)

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

    def deleteAddon(self, module: str) -> None:
        send_to_trash(Path(self.addonsFolder(module)))

    # Processing local add-on files
    ######################################################################

    def processPackages(
        self,
        paths: list[str],
        parent: QWidget | None = None,
        force_enable: bool = False,
    ) -> tuple[list[str], list[str]]:
        log = []
        errs = []

        self.mw.progress.start(parent=parent)
        try:
            for path in paths:
                base = os.path.basename(path)
                result = self.install(path, force_enable=force_enable)

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
    ) -> list[str]:
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
    ) -> list[str]:
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

    def update_supported_versions(self, items: list[AddonInfo]) -> None:
        """Adjust the supported version range after an update check.

        AnkiWeb will not have sent us any add-ons that don't support our
        version, so this cannot disable add-ons that users are using. It
        does allow the add-on author to mark an add-on as not supporting
        a future release, causing the add-on to be disabled when the user
        upgrades.
        """

        for item in items:
            addon = self.addon_meta(str(item.id))
            updated = False

            if addon.max_version != item.max_version:
                addon.max_version = item.max_version
                updated = True
            if addon.min_version != item.min_version:
                addon.min_version = item.min_version
                updated = True

            if updated:
                self.write_addon_meta(addon)

    def get_updated_addons(self, items: list[AddonInfo]) -> list[AddonInfo]:
        """Return ids of add-ons requiring an update."""
        need_update = []
        for item in items:
            addon = self.addon_meta(str(item.id))
            # update if server mtime is newer
            if not addon.is_latest(item.modified):
                need_update.append(item)
            elif not addon.compatible():
                # Addon is currently disabled, and a suitable branch was found on the
                # server. Ignore our stored mtime (which may have been set incorrectly
                # in the past) and require an update.
                need_update.append(item)

        return need_update

    # Add-on Config
    ######################################################################

    _configButtonActions: dict[str, Callable[[], bool | None]] = {}
    _configUpdatedActions: dict[str, Callable[[Any], None]] = {}
    _config_help_actions: dict[str, Callable[[], str]] = {}

    def addonConfigDefaults(self, module: str) -> dict[str, Any] | None:
        path = os.path.join(self.addonsFolder(module), "config.json")
        try:
            with open(path, encoding="utf8") as f:
                return json.load(f)
        except Exception:
            return None

    def set_config_help_action(self, module: str, action: Callable[[], str]) -> None:
        "Set a callback used to produce config help."
        addon = self.addonFromModule(module)
        self._config_help_actions[addon] = action

    def addonConfigHelp(self, module: str) -> str:
        if action := self._config_help_actions.get(module, None):
            contents = action()
        else:
            path = os.path.join(self.addonsFolder(module), "config.md")
            if os.path.exists(path):
                with open(path, encoding="utf-8") as f:
                    contents = f.read()
            else:
                return ""

        return markdown.markdown(contents, extensions=[md_in_html.makeExtension()])

    def addonFromModule(self, module: str) -> str:  # softly deprecated
        return module.split(".")[0]

    @staticmethod
    def addon_from_module(module: str) -> str:
        return module.split(".")[0]

    def configAction(self, module: str) -> Callable[[], bool | None]:
        return self._configButtonActions.get(module)

    def configUpdatedAction(self, module: str) -> Callable[[Any], None]:
        return self._configUpdatedActions.get(module)

    # Schema
    ######################################################################

    def _addon_schema_path(self, module: str) -> str:
        return os.path.join(self.addonsFolder(module), "config.schema.json")

    def _addon_schema(self, module: str) -> Any:
        path = self._addon_schema_path(module)
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

    def getConfig(self, module: str) -> dict[str, Any] | None:
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

    def setConfigAction(self, module: str, fn: Callable[[], bool | None]) -> None:
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

    def backupUserFiles(self, module: str) -> None:
        p = self._userFilesPath(module)

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

    _webExports: dict[str, str] = {}

    def setWebExports(self, module: str, pattern: str) -> None:
        addon = self.addonFromModule(module)
        self._webExports[addon] = pattern

    def getWebExports(self, module: str) -> str:
        return self._webExports.get(module)

    # Logging
    ######################################################################

    @classmethod
    def get_logger(cls, module: str) -> logging.Logger:
        """Return a logger for the given add-on module.

        NOTE: This method is static to allow it to be called outside of a
        running Anki instance, e.g. in add-on unit tests.
        """
        return logging.getLogger(
            f"{ADDON_LOGGER_PREFIX}{cls.addon_from_module(module)}"
        )

    def has_logger(self, module: str) -> bool:
        return find_addon_logger(self.addon_from_module(module)) is not None

    def is_debug_logging_enabled(self, module: str) -> bool:
        if not (logger := find_addon_logger(self.addon_from_module(module))):
            return False
        return logger.isEnabledFor(logging.DEBUG)

    def toggle_debug_logging(self, module: str, enable: bool) -> None:
        if not (logger := find_addon_logger(self.addon_from_module(module))):
            return
        logger.setLevel(logging.DEBUG if enable else logging.INFO)

    def logs_folder(self, module: str) -> Path:
        return get_addon_logs_folder(
            self.mw.pm.addon_logs(), self.addon_from_module(module)
        )


# Add-ons Dialog
######################################################################


class AddonsDialog(QDialog):
    def __init__(self, addonsManager: AddonManager) -> None:
        self.mgr = addonsManager
        self.mw = addonsManager.mw
        self._require_restart = False

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
        exts = self.mgr.exts
        if all(any(url.toLocalFile().endswith(ext) for ext in exts) for url in urls):
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
        if self._require_restart:
            tooltip(tr.addons_changes_will_take_effect_when_anki(), parent=self.mw)
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
        min = addon.min_version
        if min is not None and min > _current_version:
            ver = int_version_to_str(min)
            return f"Anki >= {ver}"
        else:
            max = abs(addon.max_version)
            ver = int_version_to_str(max)
            return f"Anki <= {ver}"

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
                item.setForeground(Qt.GlobalColor.gray)
            if addon.dir_name in selected:
                item.setSelected(True)

        addonList.reset()

    def _onAddonItemSelected(self, row_int: int) -> None:
        try:
            addon = self.addons[row_int]
        except IndexError:
            return
        self.form.viewPage.setEnabled(addon.page() is not None)
        self.form.config.setEnabled(
            bool(
                self.mgr.getConfig(addon.dir_name)
                or self.mgr.configAction(addon.dir_name)
            )
        )
        gui_hooks.addons_dialog_did_change_selected_addon(self, addon)
        return

    def selectedAddons(self) -> list[str]:
        idxs = [x.row() for x in self.form.addonList.selectedIndexes()]
        return [self.addons[idx].dir_name for idx in idxs]

    def onlyOneSelected(self) -> str | None:
        dirs = self.selectedAddons()
        if len(dirs) != 1:
            show_info(tr.addons_please_select_a_single_addon_first())
            return None
        return dirs[0]

    def selected_addon_meta(self) -> AddonMeta | None:
        idxs = [x.row() for x in self.form.addonList.selectedIndexes()]
        if len(idxs) != 1:
            show_info(tr.addons_please_select_a_single_addon_first())
            return None
        return self.addons[idxs[0]]

    def onToggleEnabled(self) -> None:
        for module in self.selectedAddons():
            self.mgr.toggleEnabled(module)
        self._require_restart = True
        self.redrawAddons()

    def onViewPage(self) -> None:
        addon = self.selected_addon_meta()
        if not addon:
            return
        if page := addon.page():
            openLink(page)

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
        try:
            for module in selected:
                # doing this before deleting, as `enabled` is always True afterwards
                if self.mgr.addon_meta(module).enabled:
                    self._require_restart = True
                self.mgr.deleteAddon(module)
        except OSError as e:
            showWarning(
                tr.addons_unable_to_update_or_delete_addon(val=str(e)),
                textFormat="plain",
            )
        self.form.addonList.clearSelection()
        self.redrawAddons()

    def onGetAddons(self) -> None:
        obj = GetAddons(self)
        if obj.ids:
            download_addons(
                self, self.mgr, obj.ids, self.after_downloading, force_enable=True
            )

    def after_downloading(self, log: list[DownloadLogEntry]) -> None:
        self.redrawAddons()
        if log:
            show_log_to_user(self, log)
        else:
            tooltip(tr.addons_no_updates_available())

    def onInstallFiles(self, paths: list[str] | None = None) -> bool | None:
        if not paths:
            filter = f"{tr.addons_packaged_anki_addon()} " + "({})".format(
                " ".join(f"*{ext}" for ext in self.mgr.exts)
            )
            paths_ = getFile(
                self, tr.addons_install_addons(), None, filter, key="addons", multi=True
            )
            paths = paths_  # type: ignore
            if not paths:
                return False

        installAddonPackages(self.mgr, paths, parent=self, force_enable=True)

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
        self.ids: list[int] = []
        self.form = aqt.forms.getaddons.Ui_Dialog()
        self.form.setupUi(self)
        b = self.form.buttonBox.addButton(
            tr.addons_browse_addons(), QDialogButtonBox.ButtonRole.ActionRole
        )
        qconnect(b.clicked, self.onBrowse)
        disable_help_button(self)
        restoreGeom(self, "getaddons", adjustSize=True)
        self.exec()
        saveGeom(self, "getaddons")

    def onBrowse(self) -> None:
        openLink(f"{aqt.appShared}addons/2.1")

    def accept(self) -> None:
        # get codes
        try:
            sids = self.form.code.text().split()
            sids = [
                re.sub(r"^https://ankiweb.net/shared/info/(\d+)$", r"\1", id_)
                for id_ in sids
            ]
            ids = [int(id_) for id_ in sids]
        except ValueError:
            showWarning(tr.addons_invalid_code())
            return

        self.ids = ids
        QDialog.accept(self)


# Downloading
######################################################################


def download_addon(client: HttpClient, id: int) -> DownloadOk | DownloadError:
    "Fetch a single add-on from AnkiWeb."
    try:
        resp = client.get(f"{aqt.appShared}download/{id}?v=2.1&p={_current_version}")
        if resp.status_code != 200:
            return DownloadError(status_code=resp.status_code)

        data = client.stream_content(resp)

        match = re.match(
            "attachment; filename=(.+)", resp.headers["content-disposition"]
        )
        assert match is not None
        fname = match.group(1)

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

    def get_first_element(elements: list[str]) -> int:
        return int(elements[0])

    meta = ExtractedDownloadMeta(
        mod_time=get_first_element(query["t"]),
        min_point_version=get_first_element(query["minpt"]),
        max_point_version=get_first_element(query["maxpt"]),
        branch_index=get_first_element(query["bidx"]),
    )

    return meta


def download_log_to_html(log: list[DownloadLogEntry]) -> str:
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


def download_encountered_problem(log: list[DownloadLogEntry]) -> bool:
    return any(not isinstance(e[1], InstallOk) for e in log)


def download_and_install_addon(
    mgr: AddonManager, client: HttpClient, id: int, force_enable: bool = False
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

    result2 = mgr.install(
        io.BytesIO(result.data), manifest=manifest, force_enable=force_enable
    )

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
        self,
        ids: list[int],
        on_done: Callable[[list[DownloadLogEntry]], None],
        force_enable: bool = False,
    ) -> None:
        self.ids = ids
        self.log: list[DownloadLogEntry] = []

        self.dl_bytes = 0
        self.last_tooltip = 0

        self.on_done = on_done

        parent = self.parent()
        assert isinstance(parent, QWidget)
        self.mgr.mw.progress.start(immediate=True, parent=parent)
        self.mgr.mw.taskman.run_in_background(
            lambda: self._download_all(force_enable), self._download_done
        )

    def _progress_callback(self, up: int, down: int) -> None:
        self.dl_bytes += down
        self.mgr.mw.progress.update(
            label=tr.addons_downloading_adbd_kb02fkb(
                part=len(self.log) + 1,
                total=len(self.ids),
                kilobytes=self.dl_bytes // 1024,
            )
        )

    def _download_all(self, force_enable: bool = False) -> None:
        for id in self.ids:
            self.log.append(
                download_and_install_addon(
                    self.mgr, self.client, id, force_enable=force_enable
                )
            )

    def _download_done(self, future: Future) -> None:
        self.mgr.mw.progress.finish()
        future.result()
        # qt gets confused if on_done() opens new windows while the progress
        # modal is still cleaning up
        self.mgr.mw.progress.single_shot(50, lambda: self.on_done(self.log))


def show_log_to_user(
    parent: QWidget, log: list[DownloadLogEntry], title: str = "Anki"
) -> None:
    have_problem = download_encountered_problem(log)

    if have_problem:
        text = tr.addons_one_or_more_errors_occurred()
    else:
        text = tr.addons_download_complete_please_restart_anki_to()
    text += f"<br><br>{download_log_to_html(log)}"

    if have_problem:
        showWarning(text, textFormat="rich", parent=parent, title=title)
    else:
        showInfo(text, parent=parent, title=title)


def download_addons(
    parent: QWidget,
    mgr: AddonManager,
    ids: list[int],
    on_done: Callable[[list[DownloadLogEntry]], None],
    client: HttpClient | None = None,
    force_enable: bool = False,
) -> None:
    if client is None:
        client = HttpClient()
    downloader = DownloaderInstaller(parent, mgr, client)
    downloader.download(ids, on_done=on_done, force_enable=force_enable)


# Update checking
######################################################################


class ChooseAddonsToUpdateList(QListWidget):
    ADDON_ID_ROLE = 101

    def __init__(
        self,
        parent: QWidget,
        mgr: AddonManager,
        updated_addons: list[AddonInfo],
    ) -> None:
        QListWidget.__init__(self, parent)
        self.mgr = mgr
        self.updated_addons = sorted(updated_addons, key=lambda addon: addon.modified)
        self.ignore_check_evt = False
        self.setup()
        self.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        qconnect(self.itemClicked, self.on_click)
        qconnect(self.itemChanged, self.on_check)
        qconnect(self.itemDoubleClicked, self.on_double_click)
        qconnect(self.customContextMenuRequested, self.on_context_menu)

    def setup(self) -> None:
        header_item = QListWidgetItem(tr.addons_choose_update_update_all(), self)
        header_item.setFlags(
            Qt.ItemFlag(Qt.ItemFlag.ItemIsUserCheckable | Qt.ItemFlag.ItemIsEnabled)
        )
        self.header_item = header_item
        for update_info in self.updated_addons:
            addon_id = update_info.id
            addon_meta = self.mgr.addon_meta(str(addon_id))
            update_enabled = addon_meta.update_enabled
            addon_name = addon_meta.human_name()
            update_timestamp = update_info.modified
            update_time = datetime.fromtimestamp(update_timestamp)

            addon_label = f"{update_time:%Y-%m-%d}   {addon_name}"
            item = QListWidgetItem(addon_label, self)
            # Not user checkable because it overlaps with itemClicked signal
            item.setFlags(Qt.ItemFlag(Qt.ItemFlag.ItemIsEnabled))
            if update_enabled:
                item.setCheckState(Qt.CheckState.Checked)
            else:
                item.setCheckState(Qt.CheckState.Unchecked)
            item.setData(self.ADDON_ID_ROLE, addon_id)
        self.refresh_header_check_state()

    def bool_to_check(self, check_bool: bool) -> Qt.CheckState:
        if check_bool:
            return Qt.CheckState.Checked
        else:
            return Qt.CheckState.Unchecked

    def checked(self, item: QListWidgetItem) -> bool:
        return item.checkState() == Qt.CheckState.Checked

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
        m.exec(QCursor.pos())

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
                self.check_item(self.header_item, Qt.CheckState.Unchecked)
                return
        self.check_item(self.header_item, Qt.CheckState.Checked)

    def get_selected_addon_ids(self) -> list[int]:
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
    _on_done: Callable[[list[int]], None]

    def __init__(
        self, parent: QWidget, mgr: AddonManager, updated_addons: list[AddonInfo]
    ) -> None:
        QDialog.__init__(self, parent)
        self.setWindowTitle(tr.addons_choose_update_window_title())
        self.setWindowModality(Qt.WindowModality.NonModal)
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

        button_box = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)  # type: ignore
        qconnect(
            button_box.button(QDialogButtonBox.StandardButton.Ok).clicked, self.accept
        )
        qconnect(
            button_box.button(QDialogButtonBox.StandardButton.Cancel).clicked,
            self.reject,
        )
        layout.addWidget(button_box)
        self.setLayout(layout)

    def ask(self, on_done: Callable[[list[int]], None]) -> None:
        self._on_done = on_done
        self.show()

    def accept(self) -> None:
        saveGeom(self, "addonsChooseUpdate")
        self.addons_list_widget.save_check_state()
        self._on_done(self.addons_list_widget.get_selected_addon_ids())
        QDialog.accept(self)


def fetch_update_info(ids: list[int]) -> list[AddonInfo]:
    """Fetch update info from AnkiWeb in one or more batches."""
    all_info: list[AddonInfo] = []

    while ids:
        # get another chunk
        chunk = ids[:25]
        del ids[:25]

        batch_results = _fetch_update_info_batch(chunk)
        all_info.extend(batch_results)

    return all_info


def _fetch_update_info_batch(chunk: Iterable[int]) -> Sequence[AddonInfo]:
    return aqt.mw.backend.get_addon_info(
        client_version=_current_version, addon_ids=chunk
    )


def check_and_prompt_for_updates(
    parent: QWidget,
    mgr: AddonManager,
    on_done: Callable[[list[DownloadLogEntry]], None],
    requested_by_user: bool = True,
) -> None:
    def on_updates_received(items: list[AddonInfo]) -> None:
        handle_update_info(parent, mgr, items, on_done, requested_by_user)

    check_for_updates(mgr, on_updates_received)


def check_for_updates(
    mgr: AddonManager, on_done: Callable[[list[AddonInfo]], None]
) -> None:
    def check() -> list[AddonInfo]:
        return fetch_update_info(mgr.ankiweb_addons())

    def update_info_received(future: Future) -> None:
        # if syncing/in profile screen, defer message delivery
        if not mgr.mw.col:
            mgr.mw.progress.single_shot(
                1000,
                lambda: update_info_received(future),
                False,
            )
            return

        if future.exception():
            # swallow network errors
            print(str(future.exception()))
            result = []
        else:
            result = future.result()

        on_done(result)

    mgr.mw.taskman.run_in_background(check, update_info_received)


def handle_update_info(
    parent: QWidget,
    mgr: AddonManager,
    items: list[AddonInfo],
    on_done: Callable[[list[DownloadLogEntry]], None],
    requested_by_user: bool = True,
) -> None:
    mgr.update_supported_versions(items)
    updated_addons = mgr.get_updated_addons(items)

    if not updated_addons:
        on_done([])
        return

    prompt_to_update(parent, mgr, updated_addons, on_done, requested_by_user)


def prompt_to_update(
    parent: QWidget,
    mgr: AddonManager,
    updated_addons: list[AddonInfo],
    on_done: Callable[[list[DownloadLogEntry]], None],
    requested_by_user: bool = True,
) -> None:
    client = HttpClient()
    if not requested_by_user:
        prompt_update = False
        for addon in updated_addons:
            if mgr.addon_meta(str(addon.id)).update_enabled:
                prompt_update = True
        if not prompt_update:
            return

    def after_choosing(ids: list[int]) -> None:
        if ids:
            download_addons(parent, mgr, ids, on_done, client)

    ChooseAddonsToUpdateDialog(parent, mgr, updated_addons).ask(after_choosing)


def install_or_update_addon(
    parent: QWidget,
    mgr: AddonManager,
    addon_id: int,
    on_done: Callable[[list[DownloadLogEntry]], None],
) -> None:
    def check() -> list[AddonInfo]:
        return fetch_update_info([addon_id])

    def update_info_received(future: Future) -> None:
        try:
            items = future.result()
            updated_addons = mgr.get_updated_addons(items)
            if not updated_addons:
                on_done([])
                return
            client = HttpClient()
            download_addons(
                parent, mgr, [addon.id for addon in updated_addons], on_done, client
            )
        except Exception as exc:
            on_done([(addon_id, DownloadError(exception=exc))])

    mgr.mw.taskman.run_in_background(check, update_info_received)


# Editing config
######################################################################


class ConfigEditor(QDialog):
    def __init__(self, dlg: AddonsDialog, addon: str, conf: dict) -> None:
        super().__init__(dlg)
        self.addon = addon
        self.conf = conf
        self.mgr = dlg.mgr
        self.form = aqt.forms.addonconf.Ui_Dialog()
        self.form.setupUi(self)
        restore = self.form.buttonBox.button(
            QDialogButtonBox.StandardButton.RestoreDefaults
        )
        qconnect(restore.clicked, self.onRestoreDefaults)
        ok = self.form.buttonBox.button(QDialogButtonBox.StandardButton.Ok)
        ok.setShortcut(QKeySequence("Ctrl+Return"))
        self.setupFonts()
        self.updateHelp()
        self.updateText(self.conf)
        restoreGeom(self, "addonconf")
        self.form.splitter.setSizes([2 * self.width() // 3, self.width() // 3])
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
        font_mono = QFont("Consolas")
        if not font_mono.exactMatch():
            font_mono = QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont)
        font_mono.setPointSize(font_mono.pointSize())
        self.form.editor.setFont(font_mono)

    def updateHelp(self) -> None:
        txt = self.mgr.addonConfigHelp(self.addon)
        if txt:
            self.form.help.stdHtml(txt, js=[], css=["css/addonconf.css"], context=self)
        else:
            self.form.help.setVisible(False)

    def updateText(self, conf: dict[str, Any]) -> None:
        text = json.dumps(
            conf,
            ensure_ascii=False,
            sort_keys=True,
            indent=4,
            separators=(",", ": "),
        )
        text = gui_hooks.addon_config_editor_will_display_json(text)
        self.form.editor.setPlainText(text)
        if is_mac:
            self.form.editor.repaint()

    def onClose(self) -> None:
        saveGeom(self, "addonconf")
        saveSplitter(self.form.splitter, "addonconf")

    def reject(self) -> None:
        self.onClose()
        super().reject()

    def accept(self) -> None:
        txt = self.form.editor.toPlainText()
        txt = gui_hooks.addon_config_editor_will_update_json(txt, self.addon)
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
    paths: list[str],
    parent: QWidget | None = None,
    warn: bool = False,
    strictly_modal: bool = False,
    advise_restart: bool = False,
    force_enable: bool = False,
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
                customBtns=[
                    QMessageBox.StandardButton.No,
                    QMessageBox.StandardButton.Yes,
                ],
            )
            == QMessageBox.StandardButton.Yes
        ):
            return False

    log, errs = addonsManager.processPackages(
        paths, parent=parent, force_enable=force_enable
    )

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
