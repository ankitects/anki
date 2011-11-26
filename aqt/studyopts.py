# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import datetime, time, aqt

class StudyOptions(QDialog):
    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.studyopts.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowModality(Qt.WindowModal)
        self.setup()
        self.load()
        self.exec_()

    def setup(self):
        import anki.consts as c
        self.form.newOrder.insertItems(
            0, c.newCardOrderLabels().values())
        self.form.newSpread.insertItems(
            0, c.newCardSchedulingLabels().values())
        self.form.revOrder.insertItems(
            0, c.revCardOrderLabels().values())
        self.connect(self.form.buttonBox,
                     SIGNAL("helpRequested()"),
                     lambda: aqt.openHelp("StudyOptions"))

    def load(self):
        f = self.form
        d = self.mw.col
        qc = d.conf
        f.newPerDay.setValue(qc['newPerDay'])
        f.newOrder.setCurrentIndex(qc['newOrder'])
        f.newSpread.setCurrentIndex(qc['newSpread'])
        f.revOrder.setCurrentIndex(qc['revOrder'])
        f.timeLimit.setValue(qc['timeLim']/60.0)
        f.questionLimit.setValue(qc['repLim'])
        self.startDate = datetime.datetime.fromtimestamp(d.crt)
        f.dayOffset.setValue(self.startDate.hour)
        f.lrnCutoff.setValue(qc['collapseTime']/60.0)

    def reject(self):
        self.accept()

    def accept(self):
        f = self.form
        d = self.mw.col
        qc = d.conf
        old = qc['newOrder']
        qc['newOrder'] = f.newOrder.currentIndex()
        self.updateNewOrder(old, qc['newOrder'])
        qc['newSpread'] = f.newSpread.currentIndex()
        qc['revOrder'] = f.revOrder.currentIndex()
        qc['newPerDay'] = f.newPerDay.value()
        qc['timeLim'] = f.timeLimit.value()*60
        qc['repLim'] = f.questionLimit.value()
        qc['collapseTime'] = f.lrnCutoff.value()*60
        hrs = f.dayOffset.value()
        old = self.startDate
        date = datetime.datetime(
            old.year, old.month, old.day, hrs)
        d.crt = int(time.mktime(date.timetuple()))
        self.mw.reset()
        QDialog.accept(self)

    def updateNewOrder(self, old, new):
        if old == new:
            return
        self.mw.progress.start()
        if new == 1:
            self.mw.col.sched.orderCards()
        else:
            self.mw.col.sched.randomizeCards()
        self.mw.progress.finish()
