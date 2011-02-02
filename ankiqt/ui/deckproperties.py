# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re, time
import ankiqt.forms
import anki
from ankiqt import ui
from anki.utils import parseTags
from anki.deck import newCardOrderLabels, newCardSchedulingLabels
from anki.deck import revCardOrderLabels
from anki.utils import hexifyID, dehexifyID
import ankiqt

class DeckProperties(QDialog):

    def __init__(self, parent, deck, onFinish=None):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.d = deck
        self.onFinish = onFinish
        self.origMod = self.d.modified
        self.dialog = ankiqt.forms.deckproperties.Ui_DeckProperties()
        self.dialog.setupUi(self)
        self.dialog.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.dialog.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        self.readData()
        self.connect(self.dialog.modelsAdd, SIGNAL("clicked()"), self.onAdd)
        self.connect(self.dialog.modelsEdit, SIGNAL("clicked()"), self.onEdit)
        self.connect(self.dialog.modelsDelete, SIGNAL("clicked()"), self.onDelete)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"), self.helpRequested)
        self.show()

    def readData(self):
        # syncing
        if self.d.syncName:
            self.dialog.doSync.setCheckState(Qt.Checked)
        else:
            self.dialog.doSync.setCheckState(Qt.Unchecked)
        self.dialog.mediaURL.setText(self.d.getVar("mediaURL") or "")
        # priorities
        self.dialog.highPriority.setText(self.d.highPriority)
        self.dialog.medPriority.setText(self.d.medPriority)
        self.dialog.lowPriority.setText(self.d.lowPriority)
        # latex
        self.dialog.latexHeader.setText(self.d.getVar("latexPre"))
        self.dialog.latexFooter.setText(self.d.getVar("latexPost"))
        # scheduling
        for type in ("hard", "mid", "easy"):
            v = getattr(self.d, type + "IntervalMin")
            getattr(self.dialog, type + "Min").setText(str(v))
            v = getattr(self.d, type + "IntervalMax")
            getattr(self.dialog, type + "Max").setText(str(v))
        self.dialog.delay0.setText(unicode(self.d.delay0/60.0))
        self.dialog.delay1.setText(unicode(self.d.delay1))
        self.dialog.delay2.setText(unicode(int(self.d.delay2*100)))
        self.dialog.collapse.setCheckState(self.d.collapseTime
                                           and Qt.Checked or Qt.Unchecked)
        self.dialog.perDay.setCheckState(self.d.getBool("perDay")
                                         and Qt.Checked or Qt.Unchecked)
        # models
        self.updateModelsList()
        # hour shift
        self.dialog.timeOffset.setText(str(
            (self.d.utcOffset - time.timezone) / 60.0 / 60.0))
        # leeches
        self.dialog.suspendLeeches.setChecked(self.d.getBool("suspendLeeches"))
        self.dialog.leechFails.setValue(self.d.getInt("leechFails"))
        # spacing
        self.dialog.newSpacing.setText(unicode(self.d.getFloat("newSpacing")/60.0))
        self.dialog.revSpacing.setText(unicode(self.d.getFloat("revSpacing")*100))

    def updateModelsList(self):
        idx = self.dialog.modelsList.currentRow()
        self.dialog.modelsList.clear()
        self.models = []
        for model in self.d.models:
            name = ngettext("%(name)s [%(facts)d fact]",
                "%(name)s [%(facts)d facts]", self.d.modelUseCount(model)) % {
                    'name': model.name,
                    'facts': self.d.modelUseCount(model),
                }
            self.models.append((name, model))
        self.models.sort()
        for (name, model) in self.models:
            item = QListWidgetItem(name)
            self.dialog.modelsList.addItem(item)
            cm = self.d.currentModel
            try:
                if ankiqt.mw.currentCard:
                    cm = ankiqt.mw.currentCard.fact.model
            except:
                # model has been deleted
                pass
            if model == cm:
                self.dialog.modelsList.setCurrentItem(item)

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
        row = self.dialog.modelsList.currentRow()
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
        self.dialog.modelsList.setCurrentRow(row)
        ankiqt.mw.reset()

    def selectedModel(self):
        row = self.dialog.modelsList.currentRow()
        if row == -1:
            return None
        return self.models[self.dialog.modelsList.currentRow()][1]

    def updateField(self, obj, field, value):
        if getattr(obj, field) != value:
            setattr(obj, field, value)
            self.d.setModified()

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "DeckProperties"))

    def reject(self):
        n = _("Deck Properties")
        self.d.startProgress()
        self.d.setUndoStart(n)
        needSync = False
        # syncing
        if self.dialog.doSync.checkState() == Qt.Checked:
            old = self.d.syncName
            oldSync = self.d.lastSync
            self.d.enableSyncing()
            if self.d.syncName != old:
                needSync = True
            else:
                # put it back
                self.d.lastSync = oldSync
        else:
            self.d.disableSyncing()
        url = unicode(self.dialog.mediaURL.text())
        if url:
            if not re.match("^(http|https|ftp)://", url, re.I):
                url = "http://" + url
            if not url.endswith("/"):
                url += "/"
            old = self.d.getVar("mediaURL") or ""
            if old != url:
                self.d.setVar("mediaURL", url)
        # latex
        self.d.setVar('latexPre', unicode(self.dialog.latexHeader.toPlainText()))
        self.d.setVar('latexPost', unicode(self.dialog.latexFooter.toPlainText()))
        # scheduling
        minmax = ("Min", "Max")
        for type in ("hard", "mid", "easy"):
            v = getattr(self.dialog, type + "Min").text()
            try:
                v = float(v)
            except ValueError:
                continue
            self.updateField(self.d, type + "IntervalMin", v)
            v = getattr(self.dialog, type + "Max").text()
            try:
                v = float(v)
            except ValueError:
                continue
            self.updateField(self.d, type + "IntervalMax", v)
        try:
            v = float(self.dialog.delay0.text()) * 60.0
            self.updateField(self.d, 'delay0', v)
            v2 = int(self.dialog.delay1.text())
            v2 = max(0, v2)
            self.updateField(self.d, 'delay1', v2)
            v = float(self.dialog.delay2.text()) / 100.0
            self.updateField(self.d, 'delay2', max(0, min(100, v)))
        except ValueError:
            pass
        try:
            self.d.setVar("suspendLeeches",
                          not not self.dialog.suspendLeeches.isChecked())
            self.d.setVar("leechFails",
                          int(self.dialog.leechFails.value()))
        except ValueError:
            pass
        try:
            self.d.setVar("newSpacing", float(self.dialog.newSpacing.text()) * 60)
            self.d.setVar("revSpacing", float(self.dialog.revSpacing.text()) / 100.0)
        except ValueError:
            pass
        # hour shift
        try:
            offset = float(str(self.dialog.timeOffset.text()))
            offset = max(min(offset, 24), -24)
            self.updateField(self.d, 'utcOffset', offset*60*60+time.timezone)
        except:
            pass
        was = self.d.modified
        self.updateField(self.d, 'collapseTime',
                         self.dialog.collapse.isChecked() and 1 or 0)
        if self.dialog.perDay.isChecked() != self.d.getBool("perDay"):
            self.d.setVar('perDay', self.dialog.perDay.isChecked())
        self.updateField(self.d,
                         "highPriority",
                         unicode(self.dialog.highPriority.text()))
        self.updateField(self.d,
                         "medPriority",
                         unicode(self.dialog.medPriority.text()))
        self.updateField(self.d,
                         "lowPriority",
                         unicode(self.dialog.lowPriority.text()))
        prioritiesChanged = was != self.d.modified
        # mark deck dirty and close
        if self.origMod != self.d.modified:
            if prioritiesChanged:
                self.d.updateAllPriorities()
            ankiqt.mw.deck.updateCutoff()
            ankiqt.mw.reset()
        self.d.setUndoEnd(n)
        self.d.finishProgress()
        if self.onFinish:
            self.onFinish()
        QDialog.reject(self)
        if needSync:
            ankiqt.mw.syncDeck(interactive=-1)
