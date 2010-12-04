# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
import ankiqt.forms
from ankiqt import appVersion

def show(parent):
    dialog = QDialog(parent)
    abt = ankiqt.forms.about.Ui_About()
    abt.setupUi(dialog)
    abouttext = "<center><img src=':/icons/anki-logo-thin.png'></center>"
    abouttext += '<p>' + _("Anki is a friendly, intelligent spaced learning \
system. It's free and open source.")
    abouttext += '<p>' + _("Version %s") % appVersion + '<br>'
    abouttext += _("<a href='http://ichi2.net/anki/'>Visit website</a>") + \
"</span>"
    abouttext += '<p>' + _("Written by Damien Elmes, with patches, translation,\
 testing and design from:<p>%(cont)s") %  {'cont': u"""

Alex Fraser, Andreas Klauer, Andrew Wright, Bernhard Ibertsberger, Charlene
Barina, Christian Rusche, David Smith, Dave Druelinger, Dotan Cohen, Emilio
Wuerges, Emmanuel Jarri, Frank Harper, H. Mijail, Ian Lewis, Iroiro, Jin
Eun-Deok, Jarvik7, Jo Nakashima, Christian Krause, LaC, Laurent Steffan, Marco
Giancotti, Marcus Rubeus, Mari Egami, Michael Jürges, Mark Wilbur, Matthew
Holtz, Meelis Vasser, Michael Penkov, Michael Keppler, Michal Čadil, Nathanael
Law, Nick Cook, Niklas Laxström, Pcsl88, Petr Michalec, Piotr Kubowicz,
Richard Colley, Samson Melamed, Stefaan Depooter, Susanna Björverud, Tacutu,
Timm Preetz, Timo Paulssen, Ursus, Victor Suba, and Xtru."""

}
    abouttext += '<p>' + _("If you have contributed and are not on this list, \
please get in touch.")
    abouttext += '<p>' + _("A big thanks to all the people who have provided \
suggestions, bug reports and donations.")
    abt.label.setText(abouttext)
    dialog.show()
    dialog.adjustSize()
    dialog.exec_()
