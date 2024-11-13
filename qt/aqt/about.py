# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import platform
from collections.abc import Callable

import aqt.forms
from anki.lang import without_unicode_isolation
from anki.utils import version_with_build
from aqt.errors import addon_debug_info
from aqt.qt import *
from aqt.utils import disable_help_button, supportText, tooltip, tr


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
    mw.garbage_collect_on_dialog_finish(dialog)
    abt = aqt.forms.about.Ui_About()
    abt.setupUi(dialog)

    def on_copy() -> None:
        txt = supportText()
        if mw.addonManager.dirty:
            txt += "\n" + addon_debug_info()
        QApplication.clipboard().setText(txt)
        tooltip(tr.about_copied_to_clipboard(), parent=dialog)

    btn = QPushButton(tr.about_copy_debug_info())
    qconnect(btn.clicked, on_copy)
    abt.buttonBox.addButton(btn, QDialogButtonBox.ButtonRole.ActionRole)
    abt.buttonBox.button(QDialogButtonBox.StandardButton.Ok).setFocus()
    btnLayout = abt.buttonBox.layout()
    btnLayout.setContentsMargins(12, 12, 12, 12)

    # WebView cleanup
    ######################################################################

    def on_dialog_destroyed() -> None:
        abt.label.cleanup()
        abt.label = None

    qconnect(dialog.destroyed, on_dialog_destroyed)

    # WebView contents
    ######################################################################
    abouttext = "<center><img src='/_anki/imgs/anki-logo-thin.png'></center>"
    abouttext += f"<p>{tr.about_anki_is_a_friendly_intelligent_spaced()}"
    abouttext += f"<p>{tr.about_anki_is_licensed_under_the_agpl3()}"
    abouttext += f"<p>{tr.about_version(val=version_with_build())}<br>"
    abouttext += ("Python %s Qt %s PyQt %s<br>") % (
        platform.python_version(),
        qVersion(),
        PYQT_VERSION_STR,
    )
    abouttext += (
        without_unicode_isolation(tr.about_visit_website(val=aqt.appWebsite))
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
            "Ben Nguyen",
            "Bernhard Ibertsberger",
            "C. van Rooyen",
            "Cenaris Mori",
            "Charlene Barina",
            "Christian Krause",
            "Christian Rusche",
            "Dave Druelinger",
            "David Culley",
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
            "Taylor Obyen",
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
            "Abdo",
            "Junseo Park",
            "Gustavo Costa",
            "余时行",
            "叶峻峣",
            "RumovZ",
            "学习骇客",
            "ready-research",
            "Henrik Giesel",
            "Yoonchae Lee",
            "Hikaru Yoshiga",
            "Matthias Metelka",
            "Sergio Quintero",
            "Nicholas Flint",
            "Daniel Vieira Memoria10X",
            "Luka Warren",
            "Christos Longros",
            "hafatsat anki",
            "Carlos Duarte",
            "Edgar Benavent Català",
            "Kieran Black",
            "Mateusz Wojewoda",
            "Jarrett Ye",
            "Gustavo Sales",
            "Akash Reddy",
            "Marko Sisovic",
            "Lucas Scharenbroch",
            "Antoine Q.",
            "Ian Samir Yep Manzano",
            "Asuka Minato",
            "Eros Cardoso",
            "3ter",
            "Akshara Balachandra",
            "Aleksa Sarai",
            "Alexander Bocken",
            "Alexander Giorev",
            "Alexander Presnyakov",
            "Andreas Reis",
            "Andrew Gaul",
            "AntoineQ1",
            "Antonio Cavallo",
            "aplaice",
            "Araceli Yanez",
            "Arbyste",
            "Austin Siew",
            "Bart Louwers",
            "Ben Kerman",
            "Ben Olson",
            "Ben Yip",
            "Benjamin Kulnik",
            "Brayan Oliveira",
            "brishtibheja",
            "Bruce Harris",
            "Carlo Quick",
            "Cecini",
            "chandraiyengar",
            "cherryblossom",
            "Christian Donat",
            "Christopher Woggon",
            "Cy Pokhrel",
            "Daniel Tang",
            "Daniel Wallgren",
            "Danish Prakash",
            "David Allison",
            "Derek Dang",
            "Dillon Baldwin",
            "dobefore",
            "Dominique Martinet",
            "Escape0707",
            "Euan Kemp",
            "Expertium",
            "Fabricio Duarte",
            "Foxy_null",
            "Gabriel Heinatz",
            "Gesa Stupperich",
            "git9527",
            "gnnoh",
            "Gregory Abrasaldo",
            "Gustaf Carefall",
            "Han Yeong-woo",
            "Harvey Randall",
            "Henry Tang",
            "ianki",
            "ijqq",
            "Ingemar Berg",
            "Jack Pearson",
            "jake",
            "Jakub Kaczmarzyk",
            "James Elmore",
            "Jean Khawand",
            "Joel Koen",
            "jthulhu",
            "Kaben Nanlohy",
            "Kai Knoblich ",
            "Kavel Rao",
            "Kehinde Adeleke",
            "kenden",
            "Kerrick Staley",
            "Kris Cherven",
            "Krish Shah",
            "laalsaas",
            "lolilolicon",
            "Loudwig",
            "Luc Mcgrady",
            "Lucio Sauer",
            "Luke Bartholomew",
            "lukkea",
            "Maksim Abramchuk",
            "Mani",
            "Marko Juhanne",
            "Matt Krump",
            "Meredith Derecho",
            "mgrottenthaler",
            "Michael Eliachevitch",
            "Michael Winkworth",
            "Michał Bartoszkiewicz",
            "mmjang",
            "Monty Evans",
            "neitrinoweb",
            "Nickolay Yudin",
            "Nil Admirari",
            "Park Hyunwoo",
            "Patric Cunha",
            "Pedro Lameiras",
            "Pedro Schreiber",
            "phwoo",
            "qubist-pixel-ux",
            "qxo",
            "Rai (Michal Pokorny)",
            "Rastislav Kish",
            "Ren Tatsumoto",
            "RRomeroJr",
            "Ryan Greenblatt",
            "rye761",
            "Sachin Govind",
            "Sam Bradshaw",
            "Sam Penny",
            "Sam Waechter",
            "Sander Santema",
            "Shaun Ren",
            "Shawn M Moore",
            "shunlog",
            "Simone Gaiarin",
            "snowtimeglass",
            "Spooghetti420",
            "Stefan Kangas",
            "Themis Demetriades",
            "Thomas Brownback",
            "Thore Tyborski",
            "Tomas Fabrizio Orsi",
            "TRIAEIOU",
            "Tsung-Han Yu",
            "twwn",
            "user1823",
            "Vasll",
            "Viktor Ricci",
            "virinci",
            "Voczi",
            "Vova Selin",
            "wisherhxl",
            "Wu Yi-Wei",
            "XeR",
            "Xidorn Quan",
            "yellowjello",
            "Yngve Hoiseth",
            "Yuchen Lei",
            "Yutsuten",
            "Zeno Gantner",
            "Zoom",
        )
    )

    abouttext += "<p>" + tr.about_written_by_damien_elmes_with_patches(
        cont=", ".join(allusers)
    )
    abouttext += f"<p>{tr.about_if_you_have_contributed_and_are()}"
    abouttext += f"<p>{tr.about_a_big_thanks_to_all_the()}"
    abt.label.setMinimumWidth(800)
    abt.label.setMinimumHeight(600)
    dialog.show()
    abt.label.stdHtml(abouttext, js=[])
    return dialog
