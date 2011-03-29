# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt, simplejson
from aqt.utils import showInfo

class GroupConf(QDialog):
    def __init__(self, mw, gcid, parent=None):
        QDialog.__init__(self, parent or mw)
        self.mw = mw
        self.gcid = gcid
        self.form = aqt.forms.groupconf.Ui_Dialog()
        self.form.setupUi(self)
        (self.name, self.conf) = self.mw.deck.db.first(
            "select name, conf from gconf where id = ?", self.gcid)
        self.conf = simplejson.loads(self.conf)
        self.setWindowTitle(self.name)
        self.setupNew()
        self.setupLapse()
        self.setupRev()
        self.setupCram()
        self.setupGeneral()
        self.connect(self.form.buttonBox,
                     SIGNAL("helpRequested()"),
                     lambda: QDesktopServices.openUrl(QUrl(
            aqt.appWiki + "GroupOptions")))
        self.exec_()

    def listToUser(self, l):
        return " ".join([str(x) for x in l])

    def setupNew(self):
        c = self.conf['new']
        f = self.form
        f.lrnSteps.setText(self.listToUser(c['delays']))
        f.lrnGradInt.setValue(c['ints'][0])
        f.lrnEasyInt.setValue(c['ints'][2])
        f.lrnFirstInt.setValue(c['ints'][1])
        f.lrnFactor.setValue(c['initialFactor'])

    def setupLapse(self):
        c = self.conf['lapse']
        f = self.form
        f.lapSteps.setText(self.listToUser(c['delays']))
        f.lapMult.setValue(c['mult'])
        f.lapMinInt.setValue(c['minInt'])
        f.leechThreshold.setValue(c['leechFails'])
        f.leechAction.setCurrentIndex(c['leechAction'][0])
        f.lapRelearn.setChecked(c['relearn'])

    def setupRev(self):
        c = self.conf['rev']
        f = self.form
        f.revSpace.setValue(c['fuzz']*100)
        f.revMinSpace.setValue(c['minSpace'])
        f.easyBonus.setValue(c['ease4']*100)

    def setupCram(self):
        c = self.conf['cram']
        f = self.form
        f.cramSteps.setText(self.listToUser(c['delays']))
        f.cramBoost.setChecked(c['resched'])
        f.cramReset.setChecked(c['reset'])
        f.cramMult.setValue(c['mult'])
        f.cramMinInt.setValue(c['minInt'])

    def setupGeneral(self):
        c = self.conf
        f = self.form
        f.maxTaken.setValue(c['maxTaken'])

class GroupConfSelector(QDialog):
    def __init__(self, mw, gids, parent=None):
        QDialog.__init__(self, parent or mw)
        self.mw = mw
        self.gids = gids
        self.form = aqt.forms.groupconfsel.Ui_Dialog()
        self.form.setupUi(self)
        self.connect(self.form.list, SIGNAL("itemChanged(QListWidgetItem*)"),
                     self.onRename)
        self.reload()
        self.addButtons()
        self.exec_()

    def reload(self):
        self.confs = self.mw.deck.groupConfs()
        self.form.list.clear()
        item1 = None
        for c in self.confs:
            item = QListWidgetItem(c[0])
            item.setFlags(item.flags() | Qt.ItemIsEditable)
            self.form.list.addItem(item)
            if not item1:
                item1 = item
        self.form.list.setCurrentItem(item1)

    def addButtons(self):
        box = self.form.buttonBox
        def button(name, func, type=QDialogButtonBox.ActionRole):
            b = box.addButton(name, type)
            b.connect(b, SIGNAL("clicked()"), func)
            return b
        button(_("Edit..."), self.onEdit).setShortcut("e")
        button(_("Copy"), self.onCopy).setShortcut("c")
        button(_("Delete"), self.onDelete)

    def gcid(self):
        return self.confs[self.form.list.currentRow()][1]

    def onRename(self, item):
        gcid = self.gcid()
        self.mw.deck.db.execute("update gconf set name = ? where id = ?",
                                unicode(item.text()), gcid)

    def onEdit(self):
        GroupConf(self.mw, self.gcid(), self)

    def onCopy(self):
        gcid = self.gcid()
        gc = list(self.mw.deck.db.first("select * from gconf where id = ?", gcid))
        gc[0] = self.mw.deck.nextID("gcid")
        gc[2] = _("%s copy")%gc[2]
        self.mw.deck.db.execute("insert into gconf values (?,?,?,?)", *gc)
        self.reload()

    def onDelete(self):
        gcid = self.gcid()
        if gcid == 1:
            showInfo(_("The default configuration can't be removed."), self)
        else:
            self.mw.deck.save(_("Delete Group Config"))
            self.mw.deck.db.execute(
                "update groups set gcid = 1 where gcid = ?", gcid)
            self.mw.deck.db.execute(
                "delete from gconf where id = ?", gcid)
            self.reload()
