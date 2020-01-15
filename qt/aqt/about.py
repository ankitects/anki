# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import platform

import aqt.forms
from anki.lang import _
from anki.utils import versionWithBuild
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
    def onCopy():
        addmgr = mw.addonManager
        addons = "\n".join(addmgr.annotatedName(d) for d in addmgr.allAddons())
        info = "\n".join((supportText(), "Add-ons:\n\n{}".format(addons)))
        QApplication.clipboard().setText(info)
        tooltip(_("Copied to clipboard"), parent=dialog)

    btn = QPushButton(_("Copy Debug Info"))
    btn.clicked.connect(onCopy)
    abt.buttonBox.addButton(btn, QDialogButtonBox.ActionRole)
    abt.buttonBox.button(QDialogButtonBox.Ok).setFocus()

    # WebView contents
    ######################################################################
    abouttext = "<center><img src='/_anki/imgs/anki-logo-thin.png'></center>"
    abouttext += "<p>" + _(
        "Anki is a friendly, intelligent spaced learning \
system. It's free and open source."
    )
    abouttext += "<p>" + _(
        "Anki is licensed under the AGPL3 license. Please see "
        "the license file in the source distribution for more information."
    )
    abouttext += "<p>" + _("Version %s") % versionWithBuild() + "<br>"
    abouttext += ("Python %s Qt %s PyQt %s<br>") % (
        platform.python_version(),
        QT_VERSION_STR,
        PYQT_VERSION_STR,
    )
    abouttext += (_("<a href='%s'>Visit website</a>") % aqt.appWebsite) + "</span>"

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
        )
    )

    abouttext += (
        "<p>"
        + _(
            "Written by Damien Elmes, with patches, translation,\
    testing and design from:<p>%(cont)s"
        )
        % {"cont": ", ".join(allusers)}
    )
    abouttext += "<p>" + _(
        "If you have contributed and are not on this list, \
please get in touch."
    )
    abouttext += "<p>" + _(
        "A big thanks to all the people who have provided \
suggestions, bug reports and donations."
    )
    abt.label.setMinimumWidth(800)
    abt.label.setMinimumHeight(600)
    dialog.show()
    abt.label.stdHtml(abouttext, js=" ")
    return dialog
