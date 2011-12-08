# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from aqt.qt import *
from anki.lang import langs
from aqt.utils import openFolder, showWarning, getText
import aqt

class Preferences(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.prof = self.mw.pm.profile
        self.form = aqt.forms.preferences.Ui_Preferences()
        self.form.setupUi(self)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     lambda: openHelp("Preferences"))
        self.setupLang()
        self.setupNetwork()
        self.setupBackup()
        self.setupOptions()
        self.show()

    def accept(self):
        self.updateNetwork()
        self.updateBackup()
        self.updateOptions()
        self.mw.pm.save()
        self.mw.reset()
        self.done(0)

    def reject(self):
        self.accept()

    # Language handling
    ######################################################################

    def setupLang(self):
        # interface lang
        for (lang, code) in langs:
            self.form.interfaceLang.addItem(lang)
        self.form.interfaceLang.setCurrentIndex(
            self.codeToIndex(self.prof['lang']))
        self.connect(self.form.interfaceLang,
                     SIGNAL("currentIndexChanged(QString)"),
                     self.interfaceLangChanged)

    def codeToIndex(self, code):
        n = 0
        for (lang, type) in langs:
            if code == type:
                return n
            n += 1
        # default to english
        return self.codeToIndex("en")

    def interfaceLangChanged(self):
        self.prof['lang'] = (
            langs[self.form.interfaceLang.currentIndex()])[1]
        self.mw.setupLang()
        self.form.retranslateUi(self)


    # Network
    ######################################################################

    def setupNetwork(self):
        self.form.syncOnProgramOpen.setChecked(
            self.prof['autoSync'])
        self.form.syncMedia.setChecked(
            self.prof['syncMedia'])
        if not self.prof['syncKey']:
            self.form.syncDeauth.setShown(False)
        else:
            self.connect(self.form.syncDeauth, SIGNAL("clicked()"),
                         self.onSyncDeauth)
        self.form.proxyHost.setText(self.prof['proxyHost'])
        self.form.proxyPort.setValue(self.prof['proxyPort'])
        self.form.proxyUser.setText(self.prof['proxyUser'])
        self.form.proxyPass.setText(self.prof['proxyPass'])

    def onSyncDeauth(self):
        self.prof['syncKey'] = None

    def updateNetwork(self):
        self.prof['autoSync'] = self.form.syncOnProgramOpen.isChecked()
        self.prof['syncMedia'] = self.form.syncMedia.isChecked()
        self.prof['proxyHost'] = unicode(self.form.proxyHost.text())
        self.prof['proxyPort'] = int(self.form.proxyPort.value())
        self.prof['proxyUser'] = unicode(self.form.proxyUser.text())
        self.prof['proxyPass'] = unicode(self.form.proxyPass.text())

    # Backup
    ######################################################################

    def setupBackup(self):
        self.form.numBackups.setValue(self.prof['numBackups'])
        self.connect(self.form.openBackupFolder,
                     SIGNAL("linkActivated(QString)"),
                     self.onOpenBackup)

    def onOpenBackup(self):
        openFolder(self.mw.pm.backupFolder())

    def updateBackup(self):
        self.prof['numBackups'] = self.form.numBackups.value()

    # Basic & Advanced Options
    ######################################################################

    def setupOptions(self):
        self.form.showEstimates.setChecked(self.prof['showDueTimes'])
        self.form.showProgress.setChecked(self.prof['showProgress'])
        self.form.deleteMedia.setChecked(self.prof['deleteMedia'])
        self.form.stripHTML.setChecked(self.prof['stripHTML'])
        self.form.autoplaySounds.setChecked(self.prof['autoplay'])
        self.connect(
            self.form.profilePass, SIGNAL("clicked()"),
            self.onProfilePass)

    def updateOptions(self):
        self.prof['showDueTimes'] = self.form.showEstimates.isChecked()
        self.prof['showProgress'] = self.form.showProgress.isChecked()
        self.prof['stripHTML'] = self.form.stripHTML.isChecked()
        self.prof['autoplay'] = self.form.autoplaySounds.isChecked()
        self.prof['deleteMedia'] = self.form.deleteMedia.isChecked()
        self.prof['deleteMedia'] = self.form.deleteMedia.isChecked()

    def onProfilePass(self):
        pw, ret = getText(_("""\
Lock account with password, or leave blank:"""))
        if not ret:
            return
        if not pw:
            self.prof['key'] = None
            return
        pw2, ret = getText(_("Confirm password:"))
        if not ret:
            return
        if pw != pw2:
            showWarning(_("Passwords didn't match"))
        self.prof['key'] = self.mw.pm._pwhash(pw)
