# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt, simplejson
from aqt.utils import showInfo, showWarning

# Configuration editing
##########################################################################

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
        self.setup()
        self.connect(self.form.buttonBox,
                     SIGNAL("helpRequested()"),
                     lambda: aqt.openHelp("GroupOptions"))
        self.connect(self.form.buttonBox.button(QDialogButtonBox.RestoreDefaults),
                     SIGNAL("clicked()"),
                     self.onRestore)
        self.exec_()

    def accept(self):
        self.save()
        QDialog.accept(self)

    # Loading
    ##################################################

    def listToUser(self, l):
        return " ".join([str(x) for x in l])

    def setup(self):
        # new
        c = self.conf['new']
        f = self.form
        f.lrnSteps.setText(self.listToUser(c['delays']))
        f.lrnGradInt.setValue(c['ints'][0])
        f.lrnEasyInt.setValue(c['ints'][2])
        f.lrnFirstInt.setValue(c['ints'][1])
        f.lrnFactor.setValue(c['initialFactor']*100)
        # lapse
        c = self.conf['lapse']
        f.lapSteps.setText(self.listToUser(c['delays']))
        f.lapMult.setValue(c['mult']*100)
        f.lapMinInt.setValue(c['minInt'])
        f.leechThreshold.setValue(c['leechFails'])
        f.leechAction.setCurrentIndex(c['leechAction'][0])
        f.lapRelearn.setChecked(c['relearn'])
        # rev
        c = self.conf['rev']
        f.revSpace.setValue(c['fuzz']*100)
        f.revMinSpace.setValue(c['minSpace'])
        f.easyBonus.setValue(c['ease4']*100)
        # cram
        c = self.conf['cram']
        f.cramSteps.setText(self.listToUser(c['delays']))
        f.cramBoost.setChecked(c['resched'])
        f.cramReset.setChecked(c['reset'])
        f.cramMult.setValue(c['mult']*100)
        f.cramMinInt.setValue(c['minInt'])
        # general
        c = self.conf
        f.maxTaken.setValue(c['maxTaken'])

    def onRestore(self):
        from anki.groups import defaultConf
        self.conf = defaultConf.copy()
        self.setup()

    # Saving
    ##################################################

    def updateList(self, conf, key, w):
        items = unicode(w.text()).split(" ")
        ret = []
        for i in items:
            try:
                i = float(i)
                assert i > 0
                if i == int(i):
                    i = int(i)
                ret.append(i)
            except:
                # invalid, don't update
                showWarning(_("Steps must be numbers."))
                return
        conf[key] = ret

    def save(self):
        # new
        c = self.conf['new']
        f = self.form
        self.updateList(c, 'delays', f.lrnSteps)
        c['ints'][0] = f.lrnGradInt.value()
        c['ints'][2] = f.lrnEasyInt.value()
        c['ints'][1] = f.lrnFirstInt.value()
        c['initialFactor'] = f.lrnFactor.value()/100.0
        # lapse
        c = self.conf['lapse']
        self.updateList(c, 'delays', f.lapSteps)
        c['mult'] = f.lapMult.value()/100.0
        c['minInt'] = f.lapMinInt.value()
        c['leechFails'] = f.leechThreshold.value()
        c['leechAction'][0] = f.leechAction.currentIndex()
        c['relearn'] = f.lapRelearn.isChecked()
        # rev
        c = self.conf['rev']
        c['fuzz'] = f.revSpace.value()/100.0
        c['minSpace'] = f.revMinSpace.value()
        c['ease4'] = f.easyBonus.value()/100.0
        # cram
        c = self.conf['cram']
        self.updateList(c, 'delays', f.cramSteps)
        c['resched'] = f.cramBoost.isChecked()
        c['reset'] = f.cramReset.isChecked()
        c['mult'] = f.cramMult.value()/100.0
        c['minInt'] = f.cramMinInt.value()
        # general
        c = self.conf
        c['maxTaken'] = f.maxTaken.value()
        # update db
        self.mw.deck.save(_("Group Options"))
        self.mw.deck.db.execute(
            "update gconf set conf = ? where id = ?",
            simplejson.dumps(self.conf), self.gcid)

# Managing configurations
##########################################################################

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
