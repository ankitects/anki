# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import aqt
from aqt.utils import saveGeom, restoreGeom, askUser, getText

class Templates(QDialog):

    def __init__(self, mw, model, parent=None):
        self.parent = parent or mw
        QDialog.__init__(self, self.parent, Qt.Window)
        self.mw = aqt.mw
        self.model = model
        self.form = aqt.forms.templates.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("%s Templates") % self.model.name)
        self.setupTemplates()
        self.updateTemplates()
        self.exec_()

    # Templates
    ##########################################################################

    def setupTemplates(self):
        f = self.form; c = self.connect; s = SIGNAL("clicked()")
        c(f.templateList, SIGNAL("currentRowChanged(int)"),
                     self.templateRowChanged)
        c(f.templateList, SIGNAL("itemDoubleClicked(QListWidgetItem*)"),
                     self.renameTemplate)
        c(f.templateAdd, s, self.addTemplate)
        c(f.templateDelete, s, self.deleteTemplate)
        c(f.templateUp, s, self.moveTemplateUp)
        c(f.templateDown, s, self.moveTemplateDown)

    def renameTemplate(self, item):
        txt = getText(_("New name?"), default=self.template['name'])
        if txt[0]:
            self.template['name'] = txt[0]

    def updateTemplates(self, row = None):
        row = self.form.templateList.currentRow() or 0
        if row == -1:
            row = 0
        self.form.templateList.clear()
        for template in self.model.templates:
            item = QListWidgetItem(template['name'])
            self.form.templateList.addItem(item)
        count = self.form.templateList.count()
        self.form.templateList.setCurrentRow(row)

    def templateRowChanged(self):
        self.template = self.model.templates[self.form.templateList.currentRow()]
        self.enableTemplateMoveButtons()

    def enableTemplateMoveButtons(self):
        f = self.form
        row = f.templateList.currentRow()
        f.templateUp.setEnabled(row >= 1)
        f.templateDown.setEnabled(row < (f.templateList.count() - 1))

    def addTemplate(self):
        templates = len(self.model.templates)
        t = self.model.newTemplate()
        t['name'] = _("Template %d") % (templates+1)
        fields = self.model.fields
        t['qfmt'] = "{{%s}}" % fields[0]['name']
        if len(fields) > 1:
            t['afmt'] = "{{%s}}" % fields[1]['name']
        else:
            t['afmt'] = ""
        self.model.addTemplate(t)
        self.updateTemplates()

    def deleteTemplate(self):
        if len (self.model.templates) < 2:
            ui.utils.showWarning(
                _("Please add a new template first."),
                parent=self)
            return
        if not askUser(
            _("Delete this template and all cards that use it?")):
            return
        self.model.delTemplate(self.template)
        self.updateTemplates()

    def moveTemplateUp(self):
        row = self.form.templateList.currentRow()
        if row == 0:
            return
        self.mw.progress.start()
        self.model.moveTemplate(self.template, row-1)
        self.mw.progress.finish()
        self.updateTemplates()
        self.form.templateList.setCurrentRow(row-1)

    def moveTemplateDown(self):
        row = self.form.templateList.currentRow()
        if row == len(self.model.templates) - 1:
            return
        self.mw.progress.start()
        self.model.moveTemplate(self.template, row+1)
        self.mw.progress.finish()
        self.updateTemplates()
        self.form.templateList.setCurrentRow(row+1)
