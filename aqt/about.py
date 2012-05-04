# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import aqt.forms
from aqt import appVersion

def show(parent):
    dialog = QDialog(parent)
    abt = aqt.forms.about.Ui_About()
    abt.setupUi(dialog)
    abouttext = "<center><img src=':/icons/anki-logo-thin.png'></center>"
    abouttext += '<p>' + _("Anki is a friendly, intelligent spaced learning \
system. It's free and open source.")
    abouttext += '<p>' + _("Version %s") % appVersion + '<br>'
    abouttext += (_("<a href='%s'>Visit website</a>") % aqt.appWebsite) + \
"</span>"
    abouttext += '<p>' + _("Written by Damien Elmes, with patches, translation,\
 testing and design from:<p>%(cont)s") % {'cont': u"""Aaron Harsh, Alex
Fraser, Andreas Klauer, Andrew Wright, Bernhard Ibertsberger, Charlene Barina,
Christian Krause, Christian Rusche, David Smith, Dave Druelinger, Dotan Cohen,
Emilio Wuerges, Emmanuel Jarri, Frank Harper, Gregor Skumavc, H. Mijail,
Ian Lewis, Immanuel Asmus, Iroiro, Jarvik7,
Jin Eun-Deok, Jo Nakashima, Johanna Lindh, Kieran Clancy, LaC, Laurent Steffan,
Luca Ban, Marco Giancotti, Marcus Rubeus, Mari Egami, Michael Jürges, Mark Wilbur,
Matthew Duggan, Matthew Holtz, Meelis Vasser, Michael Keppler, Michael
Montague, Michael Penkov, Michal Čadil, Nathanael Law, Nick Cook, Niklas
Laxström, Nguyễn Hào Khôi, Norbert Nagold, Ole Guldberg,
Pcsl88, Petr Michalec, Piotr Kubowicz, Richard Colley, Roland Sieker,
Samson Melamed, Stefaan De Pooter, Susanna Björverud, Sylvain Durand,
Tacutu, Timm Preetz, Timo Paulssen, Ursus, Victor Suba, %s
Xtru."""% _("<!--about diag--> and")}
    abouttext += '<p>' + _("""\
The icons were obtained from various sources; please see the Anki source
for credits.""")
    abouttext += '<p>' + _("If you have contributed and are not on this list, \
please get in touch.")
    abouttext += '<p>' + _("A big thanks to all the people who have provided \
suggestions, bug reports and donations.")
    abt.label.setText(abouttext)
    dialog.adjustSize()
    dialog.show()
    dialog.exec_()
