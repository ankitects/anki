# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import aqt

class DeckOptions(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.d = mw.deck
        self.form = aqt.forms.deckopts.Ui_Dialog()
        self.form.setupUi(self)
        self.setup()
        self.exec_()

    def setup(self):
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        self.connect(self.form.modelsAdd, SIGNAL("clicked()"), self.onAdd)
        self.connect(self.form.modelsEdit, SIGNAL("clicked()"), self.onEdit)
        self.connect(self.form.modelsDelete, SIGNAL("clicked()"), self.onDelete)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.helpRequested)
        # syncing
        self.form.doSync.setChecked(self.d.syncingEnabled())
        self.form.mediaURL.setText(self.d.conf['mediaURL'])
        # latex
        self.form.latexHeader.setText(self.d.conf['latexPre'])
        self.form.latexFooter.setText(self.d.conf['latexPost'])
        # models
        self.updateModelsList()

    def updateModelsList(self):
        idx = self.form.modelsList.currentRow()
        self.form.modelsList.clear()
        self.models = []
        for model in self.d.models().values():
            self.models.append((model.name, model))
        self.models.sort()
        for (name, model) in self.models:
            item = QListWidgetItem(name)
            self.form.modelsList.addItem(item)
            cm = self.d.currentModel
            try:
                if aqt.mw.currentCard:
                    cm = aqt.mw.currentCard.fact.model
            except:
                # model has been deleted
                pass
            if model == cm:
                self.form.modelsList.setCurrentItem(item)

    def onAdd(self):
        m = ui.modelchooser.AddModel(self, self.parent, self.d).getModel()
        if m:
            self.d.addModel(m)
            self.updateModelsList()

    def onEdit(self):
        model = self.selectedModel()
        if not model:
            return
        # set to current
        self.d.currentModel = model
        ui.modelproperties.ModelProperties(self, self.d, model, self.parent,
                                           onFinish=self.updateModelsList)

    def onDelete(self):
        model = self.selectedModel()
        row = self.form.modelsList.currentRow()
        if not model:
            return
        if len(self.d.models) < 2:
            ui.utils.showWarning(_("Please add another model first."),
                                 parent=self)
            return
        if self.d.s.scalar("select 1 from sources where id=:id",
                           id=model.source):
            ui.utils.showWarning(_("This model is used by deck source:\n"
                                   "%s\nYou will need to remove the source "
                                   "first.") % hexifyID(model.source))
            return
        count = self.d.modelUseCount(model)
        if count:
            if not ui.utils.askUser(
                _("This model is used by %d facts.\n"
                  "Are you sure you want to delete it?\n"
                  "If you delete it, these cards will be lost.")
                % count, parent=self):
                return
        self.d.deleteModel(model)
        self.updateModelsList()
        self.form.modelsList.setCurrentRow(row)
        aqt.mw.reset()

    def selectedModel(self):
        row = self.form.modelsList.currentRow()
        if row == -1:
            return None
        return self.models[self.form.modelsList.currentRow()][1]

    def helpRequested(self):
        aqt.openHelp("DeckOptions")

    def reject(self):
        needSync = False
        # syncing
        if self.form.doSync.isChecked():
            old = self.d.syncName
            self.d.enableSyncing()
            if self.d.syncName != old:
                needSync = True
        else:
            self.d.disableSyncing()
        url = unicode(self.form.mediaURL.text())
        if url:
            if not re.match("^(http|https|ftp)://", url, re.I):
                url = "http://" + url
            if not url.endswith("/"):
                url += "/"
        self.d.conf['mediaURL'] = url
        # latex
        self.d.conf['latexPre'] = unicode(self.form.latexHeader.toPlainText())
        self.d.conf['latexPost'] = unicode(self.form.latexFooter.toPlainText())
        QDialog.reject(self)
        if needSync:
            aqt.mw.syncDeck(interactive=-1)
