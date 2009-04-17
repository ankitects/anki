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
    abt.label.setText(_("""
<center><img src=":/icons/anki-logo-thin.png"></center>
<p>
Anki is a friendly, intelligent spaced learning system. It's free and open
source.<p>
Version %(ver)s<br>
<a href="http://ichi2.net/anki/">Visit website</a></span>
<p>
Written by Damien Elmes, with patches, translation, testing and design from:<p>%(cont)s
<p>
If you have contributed and are not on this list, please get in touch.
<p>
A big thanks to all the people who have provided suggestions, bug reports and
donations.""") % {
    'cont': u"""

Alex Fraser, Andreas Klauer, Andrew Wright, Bananeweizen, Bernhard
Ibertsberger, Christian Rusche, David Smith, Dave Druelinger, Emmanuel Jarri,
Frank Harper, H. Mijail, Ian Lewis, Iroiro, Jin Eun-Deok, Jo Nakashima, Krause
Chr, LaC, Laurent Steffan, Marco Giancotti, Mark Wilbur, Meelis Vasser,
Michael Penkov, Michal Čadil, Nathanael Law, Nick Cook, Niklas Laxström,
Pcsl88, Piotr Kubowicz, Richard Colley, Samson Melamed, Susanna Björverud,
Timm Preetz, Timo Paulssen, Victor Suba, and Xtru.

""",
    'ver': appVersion})
    dialog.show()
    dialog.adjustSize()
    dialog.exec_()
