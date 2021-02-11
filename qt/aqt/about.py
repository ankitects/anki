# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import platform
import time

import aqt.forms
from anki.lang import without_unicode_isolation
from anki.utils import versionWithBuild
from aqt.addons import AddonManager, AddonMeta
from aqt.qt import *
from aqt.utils import TR, disable_help_button, supportText, tooltip, tr


class ClosableQDialog(QDialog):
    def reject(self) -> None:
        aqt.dialogs.markClosed("About")
        QDialog.reject(self)

    def accept(self) -> None:
        aqt.dialogs.markClosed("About")
        QDialog.accept(self)

    def closeWithCallback(self, callback: Callable[[], None]) -> None:
        self.reject()
        callback()


def show(mw: aqt.AnkiQt) -> QDialog:
    dialog = ClosableQDialog(mw)
    disable_help_button(dialog)
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

    def onCopy() -> None:
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
        info = f"    {'    '.join(info.splitlines(True))}"
        QApplication.clipboard().setText(info)
        tooltip(tr(TR.ABOUT_COPIED_TO_CLIPBOARD), parent=dialog)

    btn = QPushButton(tr(TR.ABOUT_COPY_DEBUG_INFO))
    qconnect(btn.clicked, onCopy)
    abt.buttonBox.addButton(btn, QDialogButtonBox.ActionRole)
    abt.buttonBox.button(QDialogButtonBox.Ok).setFocus()

    # WebView contents
    ######################################################################
    abouttext = "<center><img src='/_anki/imgs/anki-logo-thin.png'></center>"
    abouttext += f"<p>{tr(TR.ABOUT_ANKI_IS_A_FRIENDLY_INTELLIGENT_SPACED)}"
    abouttext += f"<p>{tr(TR.ABOUT_ANKI_IS_LICENSED_UNDER_THE_AGPL3)}"
    abouttext += f"<p>{tr(TR.ABOUT_VERSION, val=versionWithBuild())}<br>"
    abouttext += ("Python %s Qt %s PyQt %s<br>") % (
        platform.python_version(),
        QT_VERSION_STR,
        PYQT_VERSION_STR,
    )
    abouttext += (
        without_unicode_isolation(tr(TR.ABOUT_VISIT_WEBSITE, val=aqt.appWebsite))
        + "</span>"
    )

    # automatically sorted; add new lines at the end
    allusers = sorted(
        (
            "Aaron Harsh",
            "Alex Fraser",
            "Andreas Klauer",
            "Andrew Wright",
            "Aristotelis P.",
            "Bernhard Ibertsberger",
            "C. van Rooyen",
            "Charlene Barina",
            "Christian Krause",
            "Christian Rusche",
            "Dave Druelinger",
            "David Smith",
            "Dmitry Mikheev",
            "Dotan Cohen",
            "Emilio Wuerges",
            "Emmanuel Jarri",
            "Frank Harper",
            "Gregor Skumavc",
            "Guillem Palau Salvà",
            "H. Mijail",
            "Henrik Enggaard Hansen",
            "Houssam Salem",
            "Ian Lewis",
            "Immanuel Asmus",
            "Iroiro",
            "Jarvik7",
            "Jin Eun-Deok",
            "Jo Nakashima",
            "Johanna Lindh",
            "Joseph Lorimer",
            "Julien Baley",
            "Jussi Määttä",
            "Kieran Clancy",
            "LaC",
            "Laurent Steffan",
            "Luca Ban",
            "Luciano Esposito",
            "Marco Giancotti",
            "Marcus Rubeus",
            "Mari Egami",
            "Mark Wilbur",
            "Matthew Duggan",
            "Matthew Holtz",
            "Meelis Vasser",
            "Michael Jürges",
            "Michael Keppler",
            "Michael Montague",
            "Michael Penkov",
            "Michal Čadil",
            "Morteza Salehi",
            "Nathanael Law",
            "Nguyễn Hào Khôi",
            "Nick Cook",
            "Niklas Laxström",
            "Norbert Nagold",
            "Ole Guldberg",
            "Pcsl88",
            "Petr Michalec",
            "Piotr Kubowicz",
            "Richard Colley",
            "Roland Sieker",
            "Samson Melamed",
            "Silja Ijas",
            "Snezana Lukic",
            "Soren Bjornstad",
            "Stefaan De Pooter",
            "Susanna Björverud",
            "Sylvain Durand",
            "Tacutu",
            "Timm Preetz",
            "Timo Paulssen",
            "Ursus",
            "Victor Suba",
            "Volker Jansen",
            "Volodymyr Goncharenko",
            "Xtru",
            "Ádám Szegi",
            "赵金鹏",
            "黃文龍",
            "David Bailey",
            "Arman High",
            "Arthur Milchior",
            "Rai (Michael Pokorny)",
            "AMBOSS MD Inc.",
            "Erez Volk",
            "Tobias Predel",
            "Thomas Kahn",
            "zjosua",
            "Ijgnd",
            "Evandro Coan",
            "Alan Du",
            "ANH",
            "Junseo Park",
            "Gustavo Costa",
            "余时行",
            "叶峻峣",
        )
    )

    abouttext += "<p>" + tr(
        TR.ABOUT_WRITTEN_BY_DAMIEN_ELMES_WITH_PATCHES, cont=", ".join(allusers)
    )
    abouttext += f"<p>{tr(TR.ABOUT_IF_YOU_HAVE_CONTRIBUTED_AND_ARE)}"
    abouttext += f"<p>{tr(TR.ABOUT_A_BIG_THANKS_TO_ALL_THE)}"
    abt.label.setMinimumWidth(800)
    abt.label.setMinimumHeight(600)
    dialog.show()
    abt.label.stdHtml(abouttext, js=[])
    return dialog
