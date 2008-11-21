# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import copy, sys
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki, anki.utils
from anki.facts import Fact
from anki.stdmodels import JapaneseModel
from ankiqt import ui
import ankiqt.forms

tabs = ("Display",
        "SaveAndSync",
        "Advanced")

class Preferences(QDialog):

    def __init__(self, parent, config):
        QDialog.__init__(self, parent)
        self.origConfig = config
        self.parent = parent
        self.config = copy.copy(self.origConfig)
        self.origInterfaceLang = self.config['interfaceLang']
        self.dialog = ankiqt.forms.preferences.Ui_Preferences()
        self.dialog.setupUi(self)
        self.supportedLanguages = [
            (_("English"), "en_US"),
            (_("Czech"), "cs_CZ"),
            (_("French"), "fr_FR"),
            (_("German"), "de_DE"),
            (_("Japanese"), "ja_JP"),
            (_("Korean"), "ko_KR"),
            (_("Spanish"), "es_ES"),
            (_("Italian"), "it_IT"),
            ]
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"), self.helpRequested)
        self.setupLang()
        self.setupFont()
        self.setupColour()
        self.setupSync()
        self.setupSave()
        self.setupAdvanced()
        self.show()

    def accept(self):
        self.updateSync()
        self.updateSave()
        self.updateAdvanced()
        self.config['interfaceLang'] = self.origConfig['interfaceLang']
        self.origConfig.update(self.config)
        self.origConfig.save()
        self.parent.setLang()
        self.parent.moveToState("auto")
        self.done(0)

    def reject(self):
        self.origConfig['interfaceLang'] = self.origInterfaceLang
        self.parent.setLang()
        self.done(0)

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

    fonts = (
        "interface",
        )

    def loadCurrentFonts(self):
        for font in self.fonts:
            # family init
            getattr(self.dialog, font + "Family").setCurrentFont(QFont(
                self.config[font + "FontFamily"]))
            # size init
            getattr(self.dialog, font + "Size").setValue(
                self.config[font + "FontSize"])

    def setupFont(self):
        self.loadCurrentFonts()
        for font in self.fonts:
            # family change
            family = font + "Family"
            chngFunc = lambda qfont, type=font: self.familyChanged(qfont, type)
            self.connect(getattr(self.dialog, family),
                         SIGNAL("currentFontChanged(QFont)"),
                         chngFunc)

            # size change
            size = font + "Size"
            chngFunc = lambda size, type=font: self.sizeChanged(size, type)
            self.connect(getattr(self.dialog, size),
                         SIGNAL("valueChanged(int)"),
                         chngFunc)

    def familyChanged(self, qfont, type):
        self.config[type + "FontFamily"] = unicode(qfont.family())
        getattr(self.dialog, type + "Family").setFocus()

    def sizeChanged(self, size, type):
        self.config[type + "FontSize"] = size
        getattr(self.dialog, type + "Size").setFocus()

    def setupColour(self):
        if sys.platform.startswith("darwin"):
            # mac widgets don't show colours
            self.plastiqueStyle = QStyleFactory.create("plastique")
        for c in ("interface", "background"):
            colour = c + "Colour"
            button = getattr(self.dialog, colour)
            if sys.platform.startswith("darwin"):
                button.setStyle(self.plastiqueStyle)
            button.setPalette(QPalette(QColor(
                self.config[colour])))
            self.connect(button, SIGNAL("clicked()"),
                         lambda b=button, t=c, : self.colourClicked(b, t))

    def colourClicked(self, button, type):
        new = QColorDialog.getColor(button.palette().window().color(), self)
        if new.isValid():
            self.config[type + "Colour"] = str(new.name())
            button.setPalette(QPalette(new))

    def setupSync(self):
        self.dialog.syncOnOpen.setChecked(self.config['syncOnLoad'])
        self.dialog.syncOnClose.setChecked(self.config['syncOnClose'])
        self.dialog.syncUser.setText(self.config['syncUsername'])
        self.dialog.syncPass.setText(self.config['syncPassword'])

    def updateSync(self):
        self.config['syncOnLoad'] = self.dialog.syncOnOpen.isChecked()
        self.config['syncOnClose'] = self.dialog.syncOnClose.isChecked()
        self.config['syncUsername'] = unicode(self.dialog.syncUser.text())
        self.config['syncPassword'] = unicode(self.dialog.syncPass.text())

    def setupSave(self):
        self.dialog.saveAfterEveryNum.setValue(self.config['saveAfterAnswerNum'])
        self.dialog.saveAfterEvery.setChecked(self.config['saveAfterAnswer'])
        self.dialog.saveAfterAdding.setChecked(self.config['saveAfterAdding'])
        self.dialog.saveAfterAddingNum.setValue(self.config['saveAfterAddingNum'])
        self.dialog.saveWhenClosing.setChecked(self.config['saveOnClose'])

    def updateSave(self):
        self.config['saveAfterAnswer'] = self.dialog.saveAfterEvery.isChecked()
        self.config['saveAfterAnswerNum'] = self.dialog.saveAfterEveryNum.value()
        self.config['saveAfterAdding'] = self.dialog.saveAfterAdding.isChecked()
        self.config['saveAfterAddingNum'] = self.dialog.saveAfterAddingNum.value()
        self.config['saveOnClose'] = self.dialog.saveWhenClosing.isChecked()

    def setupAdvanced(self):
        self.dialog.showToolbar.setChecked(self.config['showToolbar'])
        self.dialog.tallButtons.setChecked(
            self.config['easeButtonHeight'] != 'standard')
        self.dialog.suppressEstimates.setChecked(self.config['suppressEstimates'])
        self.dialog.showLastCardInterval.setChecked(self.config['showLastCardInterval'])
        self.dialog.showLastCardContent.setChecked(self.config['showLastCardContent'])
        self.dialog.showTray.setChecked(self.config['showTrayIcon'])
        self.dialog.showTimer.setChecked(self.config['showTimer'])
        self.dialog.simpleToolbar.setChecked(self.config['simpleToolbar'])
        self.dialog.editCurrentOnly.setChecked(self.config['editCurrentOnly'])
        self.dialog.toolbarIconSize.setText(str(self.config['iconSize']))

    def updateAdvanced(self):
        self.config['showToolbar'] = self.dialog.showToolbar.isChecked()
        if self.dialog.tallButtons.isChecked():
            self.config['easeButtonHeight'] = 'tall'
        else:
            self.config['easeButtonHeight'] = 'standard'
        self.config['showLastCardInterval'] = self.dialog.showLastCardInterval.isChecked()
        self.config['showLastCardContent'] = self.dialog.showLastCardContent.isChecked()
        self.config['showTrayIcon'] = self.dialog.showTray.isChecked()
        self.config['showTimer'] = self.dialog.showTimer.isChecked()
        self.config['suppressEstimates'] = self.dialog.suppressEstimates.isChecked()
        self.config['simpleToolbar'] = self.dialog.simpleToolbar.isChecked()
        self.config['editCurrentOnly'] = self.dialog.editCurrentOnly.isChecked()
        i = 32
        try:
            i = int(self.dialog.toolbarIconSize.text())
        except:
            pass
        self.config['iconSize'] = i

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
