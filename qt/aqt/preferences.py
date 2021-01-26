# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import anki.lang
import aqt
from aqt import AnkiQt
from aqt.profiles import RecordingDriver, VideoDriver
from aqt.qt import *
from aqt.utils import (
    TR,
    HelpPage,
    askUser,
    disable_help_button,
    openHelp,
    showInfo,
    showWarning,
    tr,
)


def video_driver_name_for_platform(driver: VideoDriver) -> str:
    if driver == VideoDriver.ANGLE:
        return tr(TR.PREFERENCES_VIDEO_DRIVER_ANGLE)
    elif driver == VideoDriver.Software:
        if isMac:
            return tr(TR.PREFERENCES_VIDEO_DRIVER_SOFTWARE_MAC)
        else:
            return tr(TR.PREFERENCES_VIDEO_DRIVER_SOFTWARE_OTHER)
    else:
        if isMac:
            return tr(TR.PREFERENCES_VIDEO_DRIVER_OPENGL_MAC)
        else:
            return tr(TR.PREFERENCES_VIDEO_DRIVER_OPENGL_OTHER)


class Preferences(QDialog):
    def __init__(self, mw: AnkiQt):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.prof = self.mw.pm.profile
        self.form = aqt.forms.preferences.Ui_Preferences()
        self.form.setupUi(self)
        disable_help_button(self)
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.PREFERENCES)
        )
        self.silentlyClose = True
        self.prefs = self.mw.col.backend.get_preferences()
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
        qconnect(f.lang.currentIndexChanged, self.onLangIdxChanged)

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
        showInfo(
            tr(TR.PREFERENCES_PLEASE_RESTART_ANKI_TO_COMPLETE_LANGUAGE), parent=self
        )

    # Collection options
    ######################################################################

    def setupCollection(self):
        import anki.consts as c

        f = self.form
        qc = self.mw.col.conf

        self.setup_video_driver()

        f.newSpread.addItems(list(c.newCardSchedulingLabels(self.mw.col).values()))

        f.useCurrent.setCurrentIndex(int(not qc.get("addToCur", True)))

        s = self.prefs
        f.lrnCutoff.setValue(int(s.learn_ahead_secs / 60.0))
        f.timeLimit.setValue(int(s.time_limit_secs / 60.0))
        f.showEstimates.setChecked(s.show_intervals_on_buttons)
        f.showProgress.setChecked(s.show_remaining_due_counts)
        f.newSpread.setCurrentIndex(s.new_review_mix)
        f.dayLearnFirst.setChecked(s.day_learn_first)
        f.dayOffset.setValue(s.rollover)

        if s.scheduler_version < 2:
            f.dayLearnFirst.setVisible(False)
            f.new_timezone.setVisible(False)
        else:
            f.newSched.setChecked(True)
            f.new_timezone.setChecked(s.new_timezone)

    def setup_video_driver(self):
        self.video_drivers = VideoDriver.all_for_platform()
        names = [
            tr(TR.PREFERENCES_VIDEO_DRIVER, driver=video_driver_name_for_platform(d))
            for d in self.video_drivers
        ]
        self.form.video_driver.addItems(names)
        self.form.video_driver.setCurrentIndex(
            self.video_drivers.index(self.mw.pm.video_driver())
        )

    def update_video_driver(self):
        new_driver = self.video_drivers[self.form.video_driver.currentIndex()]
        if new_driver != self.mw.pm.video_driver():
            self.mw.pm.set_video_driver(new_driver)
            showInfo(tr(TR.PREFERENCES_CHANGES_WILL_TAKE_EFFECT_WHEN_YOU))

    def updateCollection(self):
        f = self.form
        d = self.mw.col

        self.update_video_driver()

        qc = d.conf
        qc["addToCur"] = not f.useCurrent.currentIndex()

        s = self.prefs
        s.show_remaining_due_counts = f.showProgress.isChecked()
        s.show_intervals_on_buttons = f.showEstimates.isChecked()
        s.new_review_mix = f.newSpread.currentIndex()
        s.time_limit_secs = f.timeLimit.value() * 60
        s.learn_ahead_secs = f.lrnCutoff.value() * 60
        s.day_learn_first = f.dayLearnFirst.isChecked()
        s.rollover = f.dayOffset.value()
        s.new_timezone = f.new_timezone.isChecked()

        # if moving this, make sure scheduler change is moved to Rust or
        # happens afterwards
        self.mw.col.backend.set_preferences(self.prefs)

        self._updateSchedVer(f.newSched.isChecked())
        d.setMod()

    # Scheduler version
    ######################################################################

    def _updateSchedVer(self, wantNew):
        haveNew = self.mw.col.schedVer() == 2

        # nothing to do?
        if haveNew == wantNew:
            return

        if not askUser(tr(TR.PREFERENCES_THIS_WILL_RESET_ANY_CARDS_IN)):
            return

        if wantNew:
            self.mw.col.changeSchedulerVer(2)
        else:
            self.mw.col.changeSchedulerVer(1)

    # Network
    ######################################################################

    def setupNetwork(self):
        self.form.media_log.setText(tr(TR.SYNC_MEDIA_LOG_BUTTON))
        qconnect(self.form.media_log.clicked, self.on_media_log)
        self.form.syncOnProgramOpen.setChecked(self.prof["autoSync"])
        self.form.syncMedia.setChecked(self.prof["syncMedia"])
        self.form.autoSyncMedia.setChecked(self.mw.pm.auto_sync_media_minutes() != 0)
        if not self.prof["syncKey"]:
            self._hideAuth()
        else:
            self.form.syncUser.setText(self.prof.get("syncUser", ""))
            qconnect(self.form.syncDeauth.clicked, self.onSyncDeauth)
        self.form.syncDeauth.setText(tr(TR.SYNC_LOG_OUT_BUTTON))

    def on_media_log(self):
        self.mw.media_syncer.show_sync_log()

    def _hideAuth(self):
        self.form.syncDeauth.setVisible(False)
        self.form.syncUser.setText("")
        self.form.syncLabel.setText(
            tr(TR.PREFERENCES_SYNCHRONIZATIONNOT_CURRENTLY_ENABLED_CLICK_THE_SYNC)
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
        self.mw.pm.set_auto_sync_media_minutes(
            self.form.autoSyncMedia.isChecked() and 15 or 0
        )
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
        self.form.uiScale.setValue(int(self.mw.pm.uiScale() * 100))
        self.form.pasteInvert.setChecked(self.prof.get("pasteInvert", False))
        self.form.showPlayButtons.setChecked(self.prof.get("showPlayButtons", True))
        self.form.nightMode.setChecked(self.mw.pm.night_mode())
        self.form.interrupt_audio.setChecked(self.mw.pm.interrupt_audio())
        self._recording_drivers = [
            RecordingDriver.QtAudioInput,
            RecordingDriver.PyAudio,
        ]
        # The plan is to phase out PyAudio soon, so will hold off on
        # making this string translatable for now.
        self.form.recording_driver.addItems(
            [
                f"Voice recording driver: {driver.value}"
                for driver in self._recording_drivers
            ]
        )
        self.form.recording_driver.setCurrentIndex(
            self._recording_drivers.index(self.mw.pm.recording_driver())
        )

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

        new_audio_driver = self._recording_drivers[
            self.form.recording_driver.currentIndex()
        ]
        if self.mw.pm.recording_driver() != new_audio_driver:
            self.mw.pm.set_recording_driver(new_audio_driver)
            if new_audio_driver == RecordingDriver.PyAudio:
                showInfo(
                    """\
The PyAudio driver will likely be removed in a future update. If you find it works better \
for you than the default driver, please let us know on the Anki forums."""
                )

        if restart_required:
            showInfo(tr(TR.PREFERENCES_CHANGES_WILL_TAKE_EFFECT_WHEN_YOU))
