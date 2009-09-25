# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

import time
from anki.sound import Recorder, play, generateNoiseProfile
from ankiqt.ui.utils import saveGeom, restoreGeom

def getAudio(parent, string="", encode=True):
    "Record and return filename"
    # record first
    r = Recorder()
    mb = QMessageBox(parent)
    restoreGeom(mb, "audioRecorder")
    mb.setWindowTitle("Anki")
    mb.setIconPixmap(QPixmap(":/icons/media-record.png"))
    but = QPushButton(_("  Stop"))
    but.setIcon(QIcon(":/icons/media-playback-stop.png"))
    #but.setIconSize(QSize(32, 32))
    mb.addButton(but, QMessageBox.RejectRole)
    t = time.time()
    r.start()
    QApplication.instance().processEvents()
    while not mb.clickedButton():
        txt =_("Recording...<br>Time: %0.1f")
        mb.setText(txt % (time.time() - t))
        mb.show()
        QApplication.instance().processEvents()
    # ensure at least a second captured
    saveGeom(mb, "audioRecorder")
    while time.time() - t < 1:
        time.sleep(0.1)
    r.stop()
    # process
    r.postprocess(encode)
    return r.file()

def recordNoiseProfile(parent):
    r = Recorder()
    mb = QMessageBox(parent)
    mb.setStandardButtons(QMessageBox.NoButton)
    mb.setIconPixmap(QPixmap(":/icons/media-record.png"))
    mb.show()
    mb.setWindowTitle("Anki")
    QApplication.instance().processEvents()
    f = time.time() + 10
    r.start()
    while f > time.time():
        txt =_("Sampling silence...<br>Time: %0.1f")
        mb.setText(txt % (f - time.time()))
        QApplication.instance().processEvents()
        time.sleep(0.1)
    r.stop()
    generateNoiseProfile()
    mb.deleteLater()
