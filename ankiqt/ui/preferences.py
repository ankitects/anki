# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import copy, sys, os
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki, anki.utils
from anki.facts import Fact
from ankiqt import ui
import ankiqt.forms

tabs = ("Display",
        "Network",
        "Saving",
        "Advanced")

class Preferences(QDialog):

    def __init__(self, parent, config):
        QDialog.__init__(self, parent, Qt.Window)
        self.origConfig = config
        self.parent = parent
        self.config = copy.copy(self.origConfig)
        self.origInterfaceLang = self.config['interfaceLang']
        self.dialog = ankiqt.forms.preferences.Ui_Preferences()
        self.dialog.setupUi(self)
        self.needDeckClose = False
        self.supportedLanguages = [
            (u"Bahasa Melayu", "ms"),
            (u"Dansk", "da"),
            (u"Deutsch", "de"),
            (u"Eesti", "et"),
            (u"English", "en"),
            (u"Español", "es"),
            (u"Esperanto", "eo"),
            (u"Français", "fr"),
            (u"Italiano", "it"),
            (u"Latviešu Valoda", "lv"),
            (u"Magyar", "hu"),
            (u"Nederlands","nl"),
            (u"Norsk","nb"),
            (u"Occitan","oc"),
            (u"Polski", "pl"),
            (u"Português Brasileiro", "pt_BR"),
            (u"Português", "pt"),
            (u"Româneşte", "ro"),
            (u"Slovenščina", "sl"),
            (u"Suomi", "fi"),
            (u"Svenska", "sv"),
            (u"Tiếng Việt", "vi"),
            (u"Türkçe", "tr"),
            (u"Čeština", "cs"),
            (u"Ελληνικά", "el"),
            (u"Български", "bg"),
            (u"Монгол хэл","mn"),
            (u"русский язык", "ru"),
            (u"українська мова", "uk"),
            (u"עִבְרִית", "he"),
            (u"العربية", "ar"),
            (u"فارسی", "fa"),
            (u"日本語", "ja"),
            (u"简体中文", "zh_CN"),
            (u"繁體中文", "zh_TW"),
            (u"한국어", "ko"),
            ]
        self.supportedLanguages.sort()
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"), self.helpRequested)
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
        self.config['interfaceLang'] = self.origConfig['interfaceLang']
        self.origConfig.update(self.config)
        self.origConfig.save()
        self.parent.setLang()
        if self.needDeckClose:
            self.parent.saveAndClose(parent=self)
        else:
            self.parent.reset()
        self.done(0)

    def reject(self):
        self.accept()

    def setupLang(self):
        # interface lang
        for (lang, code) in self.supportedLanguages:
            self.dialog.interfaceLang.addItem(lang)
        self.connect(self.dialog.interfaceLang,
                     SIGNAL("currentIndexChanged(QString)"),
                     self.interfaceLangChanged)
        self.dialog.interfaceLang.setCurrentIndex(self.codeToIndex(self.config['interfaceLang']))

    def interfaceLangChanged(self):
        self.origConfig['interfaceLang'] = (
            self.supportedLanguages[self.dialog.interfaceLang.currentIndex()])[1]
        self.parent.setLang()
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
        self.dialog.syncOnOpen.setChecked(self.config['syncOnLoad'])
        self.dialog.syncOnProgramOpen.setChecked(self.config['syncOnProgramOpen'])
        self.dialog.disableWhenMoved.setChecked(self.config['syncDisableWhenMoved'])
        self.dialog.syncUser.setText(self.config['syncUsername'])
        self.dialog.syncPass.setText(self.config['syncPassword'])
        self.dialog.proxyHost.setText(self.config['proxyHost'])
        self.dialog.proxyPort.setMinimum(1)
        self.dialog.proxyPort.setMaximum(65535)
        self.dialog.proxyPort.setValue(self.config['proxyPort'])
        self.dialog.proxyUser.setText(self.config['proxyUser'])
        self.dialog.proxyPass.setText(self.config['proxyPass'])

    def updateNetwork(self):
        self.config['syncOnLoad'] = self.dialog.syncOnOpen.isChecked()
        self.config['syncOnProgramOpen'] = self.dialog.syncOnProgramOpen.isChecked()
        self.config['syncDisableWhenMoved'] = self.dialog.disableWhenMoved.isChecked()
        self.config['syncUsername'] = unicode(self.dialog.syncUser.text())
        self.config['syncPassword'] = unicode(self.dialog.syncPass.text())
        self.config['proxyHost'] = unicode(self.dialog.proxyHost.text())
        self.config['proxyPort'] = int(self.dialog.proxyPort.value())
        self.config['proxyUser'] = unicode(self.dialog.proxyUser.text())
        self.config['proxyPass'] = unicode(self.dialog.proxyPass.text())

    def setupSave(self):
        self.dialog.saveAfterEveryNum.setValue(self.config['saveAfterAnswerNum'])
        self.dialog.saveAfterEvery.setChecked(self.config['saveAfterAnswer'])
        self.dialog.saveAfterAdding.setChecked(self.config['saveAfterAdding'])
        self.dialog.saveAfterAddingNum.setValue(self.config['saveAfterAddingNum'])
        self.dialog.saveWhenClosing.setChecked(self.config['saveOnClose'])
        self.dialog.numBackups.setValue(self.config['numBackups'])
        self.connect(self.dialog.openBackupFolder,
                     SIGNAL("linkActivated(QString)"),
                     self.onOpenBackup)

    def onOpenBackup(self):
        path = os.path.join(self.config.configPath, "backups")
        if sys.platform == "win32":
            anki.utils.call(["explorer", path.encode(
                sys.getfilesystemencoding())],
                            wait=False)
        else:
            QDesktopServices.openUrl(QUrl("file://" + path))

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
        self.config['saveAfterAnswer'] = self.dialog.saveAfterEvery.isChecked()
        self.config['saveAfterAnswerNum'] = self.dialog.saveAfterEveryNum.value()
        self.config['saveAfterAdding'] = self.dialog.saveAfterAdding.isChecked()
        self.config['saveAfterAddingNum'] = self.dialog.saveAfterAddingNum.value()
        self.config['saveOnClose'] = self.dialog.saveWhenClosing.isChecked()
        self.config['numBackups'] = self.dialog.numBackups.value()

    def setupAdvanced(self):
        self.dialog.colourTimes.setChecked(self.config['colourTimes'])
        self.dialog.showEstimates.setChecked(not self.config['suppressEstimates'])
        self.dialog.showStudyOptions.setChecked(self.config['showStudyScreen'])
        self.dialog.showTray.setChecked(self.config['showTrayIcon'])
        self.dialog.showTimer.setChecked(self.config['showTimer'])
        self.dialog.showDivider.setChecked(self.config['qaDivider'])
        self.dialog.splitQA.setChecked(self.config['splitQA'])
        self.dialog.addZeroSpace.setChecked(self.config['addZeroSpace'])
        self.dialog.alternativeTheme.setChecked(self.config['alternativeTheme'])
        self.dialog.showProgress.setChecked(self.config['showProgress'])
        self.dialog.openLastDeck.setChecked(self.config['loadLastDeck'])
        self.dialog.deckBrowserOrder.setChecked(self.config['deckBrowserOrder'])
        self.dialog.deleteMedia.setChecked(self.config['deleteMedia'])
        self.dialog.stripHTML.setChecked(self.config['stripHTML'])
        self.dialog.autoplaySounds.setChecked(self.config['autoplaySounds'])
        self.dialog.deckBrowserLen.setValue(self.config['deckBrowserNameLength'])
        self.dialog.optimizeSmall.setChecked(self.config['optimizeSmall'])

    def updateAdvanced(self):
        self.config['colourTimes'] = self.dialog.colourTimes.isChecked()
        self.config['showTrayIcon'] = self.dialog.showTray.isChecked()
        self.config['showTimer'] = self.dialog.showTimer.isChecked()
        self.config['suppressEstimates'] = not self.dialog.showEstimates.isChecked()
        self.config['showStudyScreen'] = self.dialog.showStudyOptions.isChecked()
        self.config['qaDivider'] = self.dialog.showDivider.isChecked()
        self.config['splitQA'] = self.dialog.splitQA.isChecked()
        self.config['addZeroSpace'] = self.dialog.addZeroSpace.isChecked()
        self.config['alternativeTheme'] = self.dialog.alternativeTheme.isChecked()
        self.config['showProgress'] = self.dialog.showProgress.isChecked()
        self.config['preventEditUntilAnswer'] = self.dialog.preventEdits.isChecked()
        self.config['stripHTML'] = self.dialog.stripHTML.isChecked()
        self.config['autoplaySounds'] = self.dialog.autoplaySounds.isChecked()
        self.config['loadLastDeck'] = self.dialog.openLastDeck.isChecked()
        self.config['optimizeSmall'] = self.dialog.optimizeSmall.isChecked()
        if self.dialog.deckBrowserOrder.isChecked():
            self.config['deckBrowserOrder'] = 1
        else:
            self.config['deckBrowserOrder'] = 0
        self.config['deleteMedia'] = self.dialog.deleteMedia.isChecked()
        self.config['deckBrowserNameLength'] = self.dialog.deckBrowserLen.value()

    def codeToIndex(self, code):
        n = 0
        for (lang, type) in self.supportedLanguages:
            if code == type:
                return n
            n += 1
        # default to english
        return self.codeToIndex("en")

    def helpRequested(self):
        idx = self.dialog.tabWidget.currentIndex()
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "Preferences#" +
                                      tabs[idx]))
