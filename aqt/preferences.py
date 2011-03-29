# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os
from PyQt4.QtGui import *
from PyQt4.QtCore import *
from anki.lang import langs
from aqt.utils import openFolder
import aqt

class Preferences(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.config = mw.config
        self.origInterfaceLang = self.config['interfaceLang']
        self.dialog = aqt.forms.preferences.Ui_Preferences()
        self.dialog.setupUi(self)
        self.needDeckClose = False
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     lambda: aqt.openHelp("Preferences"))
        self.setupLang()
        self.setupNetwork()
        self.setupSave()
        self.setupAdvanced()
        self.setupMedia()
        self.show()

    def accept(self):
        self.updateNetwork()
        self.updateSave()
        self.updateAdvanced()
        self.updateMedia()
        self.config.save()
        self.mw.setupLang()
        if self.needDeckClose:
            self.mw.close()
        else:
            self.mw.reset()
        self.done(0)

    def reject(self):
        self.accept()

    def setupLang(self):
        # interface lang
        for (lang, code) in langs:
            self.dialog.interfaceLang.addItem(lang)
        self.dialog.interfaceLang.setCurrentIndex(
            self.codeToIndex(self.config['interfaceLang']))
        self.connect(self.dialog.interfaceLang,
                     SIGNAL("currentIndexChanged(QString)"),
                     self.interfaceLangChanged)

    def interfaceLangChanged(self):
        self.config['interfaceLang'] = (
            langs[self.dialog.interfaceLang.currentIndex()])[1]
        self.mw.setupLang()
        self.dialog.retranslateUi(self)

    def setupMedia(self):
        self.dialog.mediaChoice.addItems(
            QStringList([
            _("Keep media next to deck"),
            _("Keep media in DropBox"),
            _("Keep media in custom folder"),
            ]))
        if not self.config['mediaLocation']:
            idx = 0
        elif self.config['mediaLocation'] == "dropbox":
            idx = 1
        else:
            idx = 2
        self.dialog.mediaChoice.setCurrentIndex(idx)
        self.mediaChoiceChanged(idx)
        self.connect(self.dialog.mediaChoice,
                     SIGNAL("currentIndexChanged(int)"),
                     self.mediaChoiceChanged)
        self.origMediaChoice = idx

    def mediaChoiceChanged(self, idx):
        mp = self.dialog.mediaPath
        mpl = self.dialog.mediaPrefix
        if idx == 2:
            mp.setText(self.config['mediaLocation'])
            mp.setShown(True)
            mpl.setShown(True)
        else:
            mp.setShown(False)
            mpl.setShown(False)

    def setupNetwork(self):
        self.dialog.syncOnProgramOpen.setChecked(self.config['syncOnProgramOpen'])
        self.dialog.disableWhenMoved.setChecked(self.config['syncDisableWhenMoved'])
        self.dialog.syncUser.setText(self.config['syncUsername'])
        self.dialog.syncPass.setText(self.config['syncPassword'])
        self.dialog.proxyHost.setText(self.config['proxyHost'])
        self.dialog.proxyPort.setValue(self.config['proxyPort'])
        self.dialog.proxyUser.setText(self.config['proxyUser'])
        self.dialog.proxyPass.setText(self.config['proxyPass'])

    def updateNetwork(self):
        self.config['syncOnProgramOpen'] = self.dialog.syncOnProgramOpen.isChecked()
        self.config['syncDisableWhenMoved'] = self.dialog.disableWhenMoved.isChecked()
        self.config['syncUsername'] = unicode(self.dialog.syncUser.text())
        self.config['syncPassword'] = unicode(self.dialog.syncPass.text())
        self.config['proxyHost'] = unicode(self.dialog.proxyHost.text())
        self.config['proxyPort'] = int(self.dialog.proxyPort.value())
        self.config['proxyUser'] = unicode(self.dialog.proxyUser.text())
        self.config['proxyPass'] = unicode(self.dialog.proxyPass.text())

    def setupSave(self):
        self.dialog.numBackups.setValue(self.config['numBackups'])
        self.connect(self.dialog.openBackupFolder,
                     SIGNAL("linkActivated(QString)"),
                     self.onOpenBackup)

    def onOpenBackup(self):
        path = os.path.join(self.config.confDir, "backups")
        openFolder(path)

    def updateMedia(self):
        orig = self.origMediaChoice
        new = self.dialog.mediaChoice.currentIndex()
        if orig == new and orig != 2:
            return
        if new == 0:
            p = ""
        elif new == 1:
            p = "dropbox"
            # reset public folder location
            self.config['dropboxPublicFolder'] = ""
        else:
            p = unicode(self.dialog.mediaPath.text())
        self.config['mediaLocation'] = p
        self.needDeckClose = True

    def updateSave(self):
        self.config['numBackups'] = self.dialog.numBackups.value()

    def setupAdvanced(self):
        self.dialog.showEstimates.setChecked(not self.config['suppressEstimates'])
        self.dialog.centerQA.setChecked(self.config['centerQA'])
        self.dialog.showProgress.setChecked(self.config['showProgress'])
        self.dialog.openLastDeck.setChecked(self.config['loadLastDeck'])
        self.dialog.deleteMedia.setChecked(self.config['deleteMedia'])
        self.dialog.stripHTML.setChecked(self.config['stripHTML'])
        self.dialog.autoplaySounds.setChecked(self.config['autoplaySounds'])

    def updateAdvanced(self):
        self.config['suppressEstimates'] = not self.dialog.showEstimates.isChecked()
        self.config['centerQA'] = self.dialog.centerQA.isChecked()
        self.config['showProgress'] = self.dialog.showProgress.isChecked()
        self.config['stripHTML'] = self.dialog.stripHTML.isChecked()
        self.config['autoplaySounds'] = self.dialog.autoplaySounds.isChecked()
        self.config['loadLastDeck'] = self.dialog.openLastDeck.isChecked()
        self.config['deleteMedia'] = self.dialog.deleteMedia.isChecked()

    def codeToIndex(self, code):
        n = 0
        for (lang, type) in langs:
            if code == type:
                return n
            n += 1
        # default to english
        return self.codeToIndex("en")
