# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import datetime
import time

import anki.lang
import aqt
from anki.lang import _
from aqt import AnkiQt
from aqt.qt import *
from aqt.utils import TR, askUser, openHelp, showInfo, showWarning, tr


class Preferences(QDialog):
    def __init__(self, mw: AnkiQt):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.prof = self.mw.pm.profile
        self.form = aqt.forms.preferences.Ui_Preferences()
        self.form.setupUi(self)
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        self.form.buttonBox.helpRequested.connect(lambda: openHelp("profileprefs"))
        self.silentlyClose = True
        self.setupLang()
        self.setupCollection()
        self.setupNetwork()
        self.setupBackup()
        self.setupOptions()
        self.show()

    def accept(self):
        # avoid exception if main window is already closed
        if not self.mw.col:
            return
        self.updateCollection()
        self.updateNetwork()
        self.updateBackup()
        self.updateOptions()
        self.mw.pm.save()
        self.mw.reset()
        self.done(0)
        aqt.dialogs.markClosed("Preferences")

    def reject(self):
        self.accept()

    # Language
    ######################################################################

    def setupLang(self):
        f = self.form
        f.lang.addItems([x[0] for x in anki.lang.langs])
        f.lang.setCurrentIndex(self.langIdx())
        f.lang.currentIndexChanged.connect(self.onLangIdxChanged)

    def langIdx(self):
        codes = [x[1] for x in anki.lang.langs]
        lang = anki.lang.currentLang
        if lang in anki.lang.compatMap:
            lang = anki.lang.compatMap[lang]
        else:
            lang = lang.replace("-", "_")
        try:
            return codes.index(lang)
        except:
            return codes.index("en_US")

    def onLangIdxChanged(self, idx):
        code = anki.lang.langs[idx][1]
        self.mw.pm.setLang(code)
        showInfo(_("Please restart Anki to complete language change."), parent=self)

    # Collection options
    ######################################################################

    def setupCollection(self):
        import anki.consts as c

        f = self.form
        qc = self.mw.col.conf
        self._setupDayCutoff()
        if isMac:
            f.hwAccel.setVisible(False)
        else:
            f.hwAccel.setChecked(self.mw.pm.glMode() != "software")
        f.lrnCutoff.setValue(qc["collapseTime"] / 60.0)
        f.timeLimit.setValue(qc["timeLim"] / 60.0)
        f.showEstimates.setChecked(qc["estTimes"])
        f.showProgress.setChecked(qc["dueCounts"])
        f.newSpread.addItems(list(c.newCardSchedulingLabels().values()))
        f.newSpread.setCurrentIndex(qc["newSpread"])
        f.useCurrent.setCurrentIndex(int(not qc.get("addToCur", True)))
        f.dayLearnFirst.setChecked(qc.get("dayLearnFirst", False))
        if self.mw.col.schedVer() != 2:
            f.dayLearnFirst.setVisible(False)
            f.new_timezone.setVisible(False)
        else:
            f.newSched.setChecked(True)
            f.new_timezone.setChecked(self.mw.col.sched.new_timezone_enabled())

    def updateCollection(self):
        f = self.form
        d = self.mw.col

        if not isMac:
            wasAccel = self.mw.pm.glMode() != "software"
            wantAccel = f.hwAccel.isChecked()
            if wasAccel != wantAccel:
                if wantAccel:
                    self.mw.pm.setGlMode("auto")
                else:
                    self.mw.pm.setGlMode("software")
                showInfo(_("Changes will take effect when you restart Anki."))

        qc = d.conf
        qc["dueCounts"] = f.showProgress.isChecked()
        qc["estTimes"] = f.showEstimates.isChecked()
        qc["newSpread"] = f.newSpread.currentIndex()
        qc["timeLim"] = f.timeLimit.value() * 60
        qc["collapseTime"] = f.lrnCutoff.value() * 60
        qc["addToCur"] = not f.useCurrent.currentIndex()
        qc["dayLearnFirst"] = f.dayLearnFirst.isChecked()
        self._updateDayCutoff()
        if self.mw.col.schedVer() != 1:
            was_enabled = self.mw.col.sched.new_timezone_enabled()
            is_enabled = f.new_timezone.isChecked()
            if was_enabled != is_enabled:
                if is_enabled:
                    self.mw.col.sched.set_creation_offset()
                else:
                    self.mw.col.sched.clear_creation_offset()
        self._updateSchedVer(f.newSched.isChecked())
        d.setMod()

    # Scheduler version
    ######################################################################

    def _updateSchedVer(self, wantNew):
        haveNew = self.mw.col.schedVer() == 2

        # nothing to do?
        if haveNew == wantNew:
            return

        if not askUser(
            _(
                "This will reset any cards in learning, clear filtered decks, and change the scheduler version. Proceed?"
            )
        ):
            return

        if wantNew:
            self.mw.col.changeSchedulerVer(2)
        else:
            self.mw.col.changeSchedulerVer(1)

    # Day cutoff
    ######################################################################

    def _setupDayCutoff(self):
        if self.mw.col.schedVer() == 2:
            self._setupDayCutoffV2()
        else:
            self._setupDayCutoffV1()

    def _setupDayCutoffV1(self):
        self.startDate = datetime.datetime.fromtimestamp(self.mw.col.crt)
        self.form.dayOffset.setValue(self.startDate.hour)

    def _setupDayCutoffV2(self):
        self.form.dayOffset.setValue(self.mw.col.conf.get("rollover", 4))

    def _updateDayCutoff(self):
        if self.mw.col.schedVer() == 2:
            self._updateDayCutoffV2()
        else:
            self._updateDayCutoffV1()

    def _updateDayCutoffV1(self):
        hrs = self.form.dayOffset.value()
        old = self.startDate
        date = datetime.datetime(old.year, old.month, old.day, hrs)
        self.mw.col.crt = int(time.mktime(date.timetuple()))

    def _updateDayCutoffV2(self):
        self.mw.col.conf["rollover"] = self.form.dayOffset.value()

    # Network
    ######################################################################

    def setupNetwork(self):
        self.form.media_log.setText(tr(TR.SYNC_MEDIA_LOG_BUTTON))
        self.form.media_log.clicked.connect(self.on_media_log)
        self.form.syncOnProgramOpen.setChecked(self.prof["autoSync"])
        self.form.syncMedia.setChecked(self.prof["syncMedia"])
        if not self.prof["syncKey"]:
            self._hideAuth()
        else:
            self.form.syncUser.setText(self.prof.get("syncUser", ""))
            self.form.syncDeauth.clicked.connect(self.onSyncDeauth)

    def on_media_log(self):
        self.mw.media_syncer.show_sync_log()

    def _hideAuth(self):
        self.form.syncDeauth.setVisible(False)
        self.form.syncUser.setText("")
        self.form.syncLabel.setText(
            _(
                """\
<b>Synchronization</b><br>
Not currently enabled; click the sync button in the main window to enable."""
            )
        )

    def onSyncDeauth(self) -> None:
        if self.mw.media_syncer.is_syncing():
            showWarning("Can't log out while sync in progress.")
            return
        self.prof["syncKey"] = None
        self.mw.col.media.force_resync()
        self._hideAuth()

    def updateNetwork(self):
        self.prof["autoSync"] = self.form.syncOnProgramOpen.isChecked()
        self.prof["syncMedia"] = self.form.syncMedia.isChecked()
        if self.form.fullSync.isChecked():
            self.mw.col.modSchema(check=False)
            self.mw.col.setMod()

    # Backup
    ######################################################################

    def setupBackup(self):
        self.form.numBackups.setValue(self.prof["numBackups"])

    def updateBackup(self):
        self.prof["numBackups"] = self.form.numBackups.value()

    # Basic & Advanced Options
    ######################################################################

    def setupOptions(self):
        self.form.pastePNG.setChecked(self.prof.get("pastePNG", False))
        self.form.uiScale.setValue(self.mw.pm.uiScale() * 100)
        self.form.pasteInvert.setChecked(self.prof.get("pasteInvert", False))
        self.form.showPlayButtons.setChecked(self.prof.get("showPlayButtons", True))
        self.form.nightMode.setChecked(self.mw.pm.night_mode())
        self.form.nightMode.setChecked(self.mw.pm.night_mode())
        self.form.interrupt_audio.setChecked(self.mw.pm.interrupt_audio())

    def updateOptions(self):
        restart_required = False

        self.prof["pastePNG"] = self.form.pastePNG.isChecked()
        self.prof["pasteInvert"] = self.form.pasteInvert.isChecked()
        newScale = self.form.uiScale.value() / 100
        if newScale != self.mw.pm.uiScale():
            self.mw.pm.setUiScale(newScale)
            restart_required = True
        self.prof["showPlayButtons"] = self.form.showPlayButtons.isChecked()

        if self.mw.pm.night_mode() != self.form.nightMode.isChecked():
            self.mw.pm.set_night_mode(not self.mw.pm.night_mode())
            restart_required = True

        self.mw.pm.set_interrupt_audio(self.form.interrupt_audio.isChecked())

        if restart_required:
            showInfo(_("Changes will take effect when you restart Anki."))
