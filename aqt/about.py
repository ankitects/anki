# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import aqt.forms
from aqt import appVersion
from aqt.utils import openLink

def show(mw):
    dialog = QDialog(mw)
    mw.setupDialogGC(dialog)
    abt = aqt.forms.about.Ui_About()
    abt.setupUi(dialog)
    abouttext = "<center><img src='qrc:/icons/anki-logo-thin.png'></center>"
    abouttext += '<p>' + _("Anki is a friendly, intelligent spaced learning \
system. It's free and open source.")
    abouttext += "<p>"+_("Anki is licensed under the AGPL3 license. Please see "
    "the license file in the source distribution for more information.")
    abouttext += '<p>' + _("Version %s") % appVersion + '<br>'
    abouttext += ("Qt %s PyQt %s<br>") % (QT_VERSION_STR, PYQT_VERSION_STR)
    abouttext += (_("<a href='%s'>Visit website</a>") % aqt.appWebsite) + \
"</span>"
    abouttext += '<p>' + _("Written by Damien Elmes, with patches, translation,\
 testing and design from:<p>%(cont)s") % {'cont': """Aaron Harsh, Ádám Szegi, Alex Fraser,
Andreas Klauer, Andrew Wright, Aristotelis P., Bernhard Ibertsberger, C. van Rooyen, Charlene Barina,
Christian Krause, Christian Rusche, David Smith, Dave Druelinger, Dmitry Mikheev, Dotan Cohen,
Emilio Wuerges, Emmanuel Jarri, Frank Harper, Gregor Skumavc, H. Mijail, Guillem Palau Salvà, Henrik Enggaard Hansen,
Houssam Salem, Ian Lewis, Immanuel Asmus, Iroiro, Jarvik7,
Jin Eun-Deok, Jo Nakashima, Johanna Lindh, Joseph Lorimer, Julien Baley, Jussi Määttä, Kieran Clancy, LaC, Laurent Steffan,
Luca Ban, Luciano Esposito, Marco Giancotti, Marcus Rubeus, Mari Egami, Michael Jürges, Mark Wilbur,
Matthew Duggan, Matthew Holtz, Meelis Vasser, Michael Keppler, Michael
Montague, Michael Penkov, Michal Čadil, Morteza Salehi, Nathanael Law, Nick Cook, Niklas
Laxström, Nguyễn Hào Khôi, Norbert Nagold, Ole Guldberg,
Pcsl88, Petr Michalec, Piotr Kubowicz, Richard Colley, Roland Sieker, Samson Melamed,
Stefaan De Pooter, Silja Ijas, Snezana Lukic, Soren Bjornstad, Susanna Björverud, Sylvain Durand,
Tacutu, Timm Preetz, Timo Paulssen, Ursus, Victor Suba, Volker Jansen,
    Volodymyr Goncharenko, Xtru,  赵金鹏 and 黃文龍."""}
    abouttext += '<p>' + _("""\
The icons were obtained from various sources; please see the Anki source
for credits.""")
    abouttext += '<p>' + _("If you have contributed and are not on this list, \
please get in touch.")
    abouttext += '<p>' + _("A big thanks to all the people who have provided \
suggestions, bug reports and donations.")
    abt.label.setHtml(abouttext)
    dialog.adjustSize()
    dialog.show()
    dialog.exec_()
