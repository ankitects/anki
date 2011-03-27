# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt

class GroupConf(QDialog):
    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.groupconf.Ui_Dialog()
        self.form.setupUi(self)
        self.setupNew()
        self.setupLapse()
        self.setupRev()
        self.setupCram()
        self.setupGeneral()
        self.connect(self.form.optionsHelpButton,
                     SIGNAL("clicked()"),
                     lambda: QDesktopServices.openUrl(QUrl(
            aqt.appWiki + "StudyOptions")))
        self.exec_()

    def setupNew(self):
        pass

    def setupLapse(self):
        pass

    def setupRev(self):
        pass

    def setupCram(self):
        pass

    def setupGeneral(self):
        pass
