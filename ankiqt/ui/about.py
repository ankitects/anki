# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
import ankiqt.forms
from ankiqt import appVersion

def show(parent):
    dialog = QDialog(parent)
    abt = ankiqt.forms.about.Ui_About()
    abt.setupUi(dialog)
    abt.label.setText(_("""
<h1>Anki</h1>
<img src=":/icons/anki.png">
<p>
<span>Anki is a spaced repetition flashcard program designed to maximise your
memory potential.<p/>It's free and licensed under the GPL.<p/>
Version %s<br>
<a href="http://ichi2.net/anki/">Visit website</a></span>
""") % appVersion)
    dialog.exec_()
