# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import copy, sys, os
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki, anki.utils
from anki.facts import Fact
from anki.stdmodels import JapaneseModel
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
        self.supportedLanguages = [
            (_("English"), "en_US"),
            (_("Brazillian Portuguese"), "pt_BR"),
            (_("Chinese - Simplified"), "zh_CN"),
            (_("Chinese - Traditional"), "zh_TW"),
            (_("Czech"), "cs_CZ"),
            (_("Estonian"), "ee_EE"),
            (_("Finnish"), "fi_FI"),
            (_("French"), "fr_FR"),
            (_("German"), "de_DE"),
            (_("Italian"), "it_IT"),
            (_("Japanese"), "ja_JP"),
            (_("Korean"), "ko_KR"),
	    (_("Mongolian"),"mn_MN"),
	    (_("Norwegian"),"nb_NO"),
            (_("Polish"), "pl_PL"),
            (_("Spanish"), "es_ES"),
            (_("Swedish"), "sv_SE"),
            ]
        self.supportedLanguages.sort()
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"), self.helpRequested)
        self.setupLang()
        self.setupNetwork()
        self.setupSave()
        self.setupAdvanced()
        self.show()

    def accept(self):
        self.updateNetwork()
        self.updateSave()
        self.updateAdvanced()
        self.config['interfaceLang'] = self.origConfig['interfaceLang']
        self.origConfig.update(self.config)
        self.origConfig.save()
        self.parent.setLang()
        self.parent.moveToState("auto")
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

    def setupNetwork(self):
        self.dialog.syncOnOpen.setChecked(self.config['syncOnLoad'])
        self.dialog.syncOnClose.setChecked(self.config['syncOnClose'])
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
        self.config['syncOnClose'] = self.dialog.syncOnClose.isChecked()
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
            anki.latex.call(["explorer", path.encode(
                sys.getfilesystemencoding())],
                            wait=False)
        else:
            QDesktopServices.openUrl(QUrl("file://" + path))

    def updateSave(self):
        self.config['saveAfterAnswer'] = self.dialog.saveAfterEvery.isChecked()
        self.config['saveAfterAnswerNum'] = self.dialog.saveAfterEveryNum.value()
        self.config['saveAfterAdding'] = self.dialog.saveAfterAdding.isChecked()
        self.config['saveAfterAddingNum'] = self.dialog.saveAfterAddingNum.value()
        self.config['saveOnClose'] = self.dialog.saveWhenClosing.isChecked()
        self.config['numBackups'] = self.dialog.numBackups.value()

    def setupAdvanced(self):
        self.dialog.showEstimates.setChecked(not self.config['suppressEstimates'])
        self.dialog.showStudyOptions.setChecked(self.config['showStudyScreen'])
        self.dialog.showTray.setChecked(self.config['showTrayIcon'])
        self.dialog.showTimer.setChecked(self.config['showTimer'])
        self.dialog.showDivider.setChecked(self.config['qaDivider'])
        self.dialog.splitQA.setChecked(self.config['splitQA'])
        self.dialog.addZeroSpace.setChecked(self.config['addZeroSpace'])
        self.dialog.alternativeTheme.setChecked(self.config['alternativeTheme'])
        self.dialog.showProgress.setChecked(self.config['showProgress'])
        self.dialog.preventEdits.setChecked(self.config['preventEditUntilAnswer'])

    def updateAdvanced(self):
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

    def codeToIndex(self, code):
        n = 0
        for (lang, type) in self.supportedLanguages:
            if code == type:
                return n
            n += 1
        # default to english
        return self.codeToIndex("en_US")

    def helpRequested(self):
        idx = self.dialog.tabWidget.currentIndex()
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "Preferences#" +
                                      tabs[idx]))
