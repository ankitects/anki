# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from PyQt4.QtGui import *
from PyQt4.QtCore import *
from anki.lang import langs
from aqt.utils import openFolder, showWarning
import aqt

class Preferences(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.config = mw.config
        self.form = aqt.forms.preferences.Ui_Preferences()
        self.form.setupUi(self)
        self.needDeckClose = False
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     lambda: aqt.openHelp("Preferences"))
        self.setupLang()
        self.setupNetwork()
        self.setupBackup()
        self.setupOptions()
        self.setupMedia()
        self.show()

    def accept(self):
        self.updateNetwork()
        self.updateBackup()
        self.updateOptions()
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
            self.form.interfaceLang.addItem(lang)
        self.form.interfaceLang.setCurrentIndex(
            self.codeToIndex(self.config['interfaceLang']))
        self.connect(self.form.interfaceLang,
                     SIGNAL("currentIndexChanged(QString)"),
                     self.interfaceLangChanged)

    def interfaceLangChanged(self):
        self.config['interfaceLang'] = (
            langs[self.form.interfaceLang.currentIndex()])[1]
        self.mw.setupLang()
        self.form.retranslateUi(self)

    def setupMedia(self):
        self.form.mediaChoice.addItems(
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
        self.form.mediaChoice.setCurrentIndex(idx)
        self.mediaChoiceChanged(idx)
        self.connect(self.form.mediaChoice,
                     SIGNAL("currentIndexChanged(int)"),
                     self.mediaChoiceChanged)
        self.origMediaChoice = idx

    def mediaChoiceChanged(self, idx):
        mp = self.form.mediaPath
        mpl = self.form.mediaPrefix
        if idx == 2:
            mp.setText(self.config['mediaLocation'])
            mp.setShown(True)
            mpl.setShown(True)
        else:
            mp.setShown(False)
            mpl.setShown(False)

    def setupNetwork(self):
        self.form.syncOnProgramOpen.setChecked(self.config['syncOnProgramOpen'])
        self.form.disableWhenMoved.setChecked(self.config['syncDisableWhenMoved'])
        self.form.syncUser.setText(self.config['syncUsername'])
        self.form.syncPass.setText(self.config['syncPassword'])
        self.form.proxyHost.setText(self.config['proxyHost'])
        self.form.proxyPort.setValue(self.config['proxyPort'])
        self.form.proxyUser.setText(self.config['proxyUser'])
        self.form.proxyPass.setText(self.config['proxyPass'])

    def updateNetwork(self):
        self.config['syncOnProgramOpen'] = self.form.syncOnProgramOpen.isChecked()
        self.config['syncDisableWhenMoved'] = self.form.disableWhenMoved.isChecked()
        self.config['syncUsername'] = unicode(self.form.syncUser.text())
        self.config['syncPassword'] = unicode(self.form.syncPass.text())
        self.config['proxyHost'] = unicode(self.form.proxyHost.text())
        self.config['proxyPort'] = int(self.form.proxyPort.value())
        self.config['proxyUser'] = unicode(self.form.proxyUser.text())
        self.config['proxyPass'] = unicode(self.form.proxyPass.text())

    def setupBackup(self):
        self.form.numBackups.setValue(self.config['numBackups'])
        self.connect(self.form.openBackupFolder,
                     SIGNAL("linkActivated(QString)"),
                     self.onOpenBackup)

    def onOpenBackup(self):
        path = os.path.join(self.config.confDir, "backups")
        openFolder(path)

    def updateMedia(self):
        orig = self.origMediaChoice
        new = self.form.mediaChoice.currentIndex()
        if orig == new and orig != 2:
            return
        if new == 0:
            p = ""
        elif new == 1:
            p = "dropbox"
            # reset public folder location
            self.config['dropboxPublicFolder'] = ""
        else:
            p = unicode(self.form.mediaPath.text())
        self.config['mediaLocation'] = p
        self.needDeckClose = True

    def updateBackup(self):
        self.config['numBackups'] = self.form.numBackups.value()

    def setupOptions(self):
        self.form.showEstimates.setChecked(not self.config['suppressEstimates'])
        self.form.centerQA.setChecked(self.config['centerQA'])
        self.form.showProgress.setChecked(self.config['showProgress'])
        self.form.openLastDeck.setChecked(self.config['loadLastDeck'])
        self.form.deleteMedia.setChecked(self.config['deleteMedia'])
        self.form.stripHTML.setChecked(self.config['stripHTML'])
        self.form.autoplaySounds.setChecked(self.config['autoplaySounds'])
        self.connect(self.form.documentFolder,
                     SIGNAL("clicked()"),
                     self.onChangeFolder)

    def updateOptions(self):
        self.config['suppressEstimates'] = not self.form.showEstimates.isChecked()
        self.config['centerQA'] = self.form.centerQA.isChecked()
        self.config['showProgress'] = self.form.showProgress.isChecked()
        self.config['stripHTML'] = self.form.stripHTML.isChecked()
        self.config['autoplaySounds'] = self.form.autoplaySounds.isChecked()
        self.config['loadLastDeck'] = self.form.openLastDeck.isChecked()
        self.config['deleteMedia'] = self.form.deleteMedia.isChecked()

    def codeToIndex(self, code):
        n = 0
        for (lang, type) in langs:
            if code == type:
                return n
            n += 1
        # default to english
        return self.codeToIndex("en")

    def onChangeFolder(self):
        d = QFileDialog(self)
        d.setWindowModality(Qt.WindowModal)
        d.setFileMode(QFileDialog.Directory)
        d.setOption(QFileDialog.ShowDirsOnly, True)
        d.setDirectory(self.config['documentDir'])
        if d.exec_():
            dir = unicode(list(d.selectedFiles())[0])
            # make sure we can write into it
            try:
                f = os.path.join(dir, "test.txt")
                open(f, "w").write("test")
                os.unlink(f)
            except (OSError, IOError):
                return
            self.config['documentDir'] = dir
