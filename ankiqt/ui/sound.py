# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

import time
from anki.sound import Recorder, play

def getAudio(parent, string=""):
    "Record and return filename"
    # record first
    r = Recorder()
    mb = QMessageBox(parent)
    but = QPushButton(_("  Stop"))
    but.setIcon(QIcon(":/icons/media-playback-stop.png"))
    but.setIconSize(QSize(32, 32))
    mb.addButton(but, QMessageBox.RejectRole)
    mb.show()
    mb.setText("Recording..")
    QApplication.instance().processEvents()
    r.start()
    mb.exec_()
    r.stop()
    # process
    r.postprocess()
    return r.file()

def recordNoiseProfile(parent):
    r = Recorder()
    mb = QMessageBox(parent)
    mb.show()
    f = time.time() + 8
    r.start()
    while f > time.time():
        mb.setText("Sampling silence...%0.1f" % f - time.time())
        QApplication.instance().processEvents()
    r.stop()
