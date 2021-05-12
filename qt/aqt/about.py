# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import platform
import time

import aqt.forms
from anki.lang import _
from anki.utils import versionWithBuild
from aqt.addons import AddonManager, AddonMeta
from aqt.qt import *
from aqt.utils import supportText, tooltip


class ClosableQDialog(QDialog):
    def reject(self):
        aqt.dialogs.markClosed("About")
        QDialog.reject(self)

    def accept(self):
        aqt.dialogs.markClosed("About")
        QDialog.accept(self)

    def closeWithCallback(self, callback):
        self.reject()
        callback()


def show(mw):
    dialog = ClosableQDialog(mw)
    mw.setupDialogGC(dialog)
    abt = aqt.forms.about.Ui_About()
    abt.setupUi(dialog)

    # Copy debug info
    ######################################################################

    def addon_fmt(addmgr: AddonManager, addon: AddonMeta) -> str:
        if addon.installed_at:
            installed = time.strftime(
                "%Y-%m-%dT%H:%M", time.localtime(addon.installed_at)
            )
        else:
            installed = "0"
        if addon.provided_name:
            name = addon.provided_name
        else:
            name = "''"
        user = addmgr.getConfig(addon.dir_name)
        default = addmgr.addonConfigDefaults(addon.dir_name)
        if user == default:
            modified = "''"
        else:
            modified = "mod"
        return f"{name} ['{addon.dir_name}', {installed}, '{addon.human_version}', {modified}]"

    def onCopy():
        addmgr = mw.addonManager
        active = []
        activeids = []
        inactive = []
        for addon in addmgr.all_addon_meta():
            if addon.enabled:
                active.append(addon_fmt(addmgr, addon))
                if addon.ankiweb_id():
                    activeids.append(addon.dir_name)
            else:
                inactive.append(addon_fmt(addmgr, addon))
        newline = "\n"
        info = f"""
{supportText()}

===Add-ons (active)===
(add-on provided name [Add-on folder, installed at, version, is config changed])
{newline.join(sorted(active))}

===IDs of active AnkiWeb add-ons===
{" ".join(activeids)}

===Add-ons (inactive)===
(add-on provided name [Add-on folder, installed at, version, is config changed])
{newline.join(sorted(inactive))}
"""
        info = "    " + "    ".join(info.splitlines(True))
        QApplication.clipboard().setText(info)
        tooltip(_("Copied to clipboard"), parent=dialog)

    btn = QPushButton(_("Copy Debug Info"))
    qconnect(btn.clicked, onCopy)
    abt.buttonBox.addButton(btn, QDialogButtonBox.ActionRole)
    abt.buttonBox.button(QDialogButtonBox.Ok).setFocus()

    # WebView contents
    ######################################################################
    abouttext = "<center><img src='/_anki/imgs/anki-logo-thin.png'></center>"
    abouttext += "<p>" + _(
        "AnkiCode is a fork of Anki. It's free and open source."
    )
    abouttext += "<p>" + _(
        "AnkiCode is licensed under the AGPL3 license. Please see "
        "the license file in the source distribution for more information."
    )
    abouttext += "<p>" + _("Version %s") % versionWithBuild() + "<br>"
    abouttext += ("Python %s Qt %s PyQt %s<br>") % (
        platform.python_version(),
        QT_VERSION_STR,
        PYQT_VERSION_STR,
    )
    abouttext += (_("<a href='%s'>Visit website</a>") % aqt.appWebsite) + "</span>"

    abt.label.setMinimumWidth(800)
    abt.label.setMinimumHeight(600)
    dialog.show()
    abt.label.stdHtml(abouttext, js=[])
    return dialog
