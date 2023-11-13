# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import functools
import re

import anki.lang
import aqt
import aqt.forms
import aqt.operations
from anki.collection import OpChanges
from aqt import AnkiQt
from aqt.operations.collection import set_preferences
from aqt.profiles import VideoDriver
from aqt.qt import *
from aqt.theme import Theme
from aqt.utils import (
    HelpPage,
    disable_help_button,
    is_win,
    openHelp,
    showInfo,
    showWarning,
    tr,
)


class Preferences(QDialog):
    def __init__(self, mw: AnkiQt) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.prof = self.mw.pm.profile
        self.form = aqt.forms.preferences.Ui_Preferences()
        self.form.setupUi(self)
        disable_help_button(self)
        self.form.buttonBox.button(QDialogButtonBox.StandardButton.Help).setAutoDefault(
            False
        )
        self.form.buttonBox.button(
            QDialogButtonBox.StandardButton.Close
        ).setAutoDefault(False)
        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.PREFERENCES)
        )
        self.silentlyClose = True
        self.setup_collection()
        self.setup_profile()
        self.setup_global()
        self.setup_configurable_answer_keys()
        self.show()

    def setup_configurable_answer_keys(self):
        """
        Create a group box in Preferences with widgets that let the user edit answer keys.
        """
        ease_labels = (
            (1, tr.studying_again()),
            (2, tr.studying_hard()),
            (3, tr.studying_good()),
            (4, tr.studying_easy()),
        )
        group = self.form.preferences_answer_keys
        group.setLayout(layout := QFormLayout())
        for ease, label in ease_labels:
            layout.addRow(
                label,
                line_edit := QLineEdit(self.mw.pm.get_answer_key(ease) or ""),
            )
            qconnect(
                line_edit.textChanged,
                functools.partial(self.mw.pm.set_answer_key, ease),
            )
            line_edit.setPlaceholderText(tr.preferences_shortcut_placeholder())

    def accept(self) -> None:
        # avoid exception if main window is already closed
        if not self.mw.col:
            return

        def after_collection_update() -> None:
            self.update_profile()
            self.update_global()
            self.mw.pm.save()
            self.done(0)
            aqt.dialogs.markClosed("Preferences")

        self.update_collection(after_collection_update)

    def reject(self) -> None:
        self.accept()

    # Preferences stored in the collection
    ######################################################################

    def setup_collection(self) -> None:
        self.prefs = self.mw.col.get_preferences()

        form = self.form

        scheduling = self.prefs.scheduling

        form.lrnCutoff.setValue(int(scheduling.learn_ahead_secs / 60.0))
        form.dayOffset.setValue(scheduling.rollover)

        reviewing = self.prefs.reviewing
        form.timeLimit.setValue(int(reviewing.time_limit_secs / 60.0))
        form.showEstimates.setChecked(reviewing.show_intervals_on_buttons)
        form.showProgress.setChecked(reviewing.show_remaining_due_counts)
        form.showPlayButtons.setChecked(not reviewing.hide_audio_play_buttons)
        form.interrupt_audio.setChecked(reviewing.interrupt_audio_when_answering)

        editing = self.prefs.editing
        form.useCurrent.setCurrentIndex(
            0 if editing.adding_defaults_to_current_deck else 1
        )
        form.paste_strips_formatting.setChecked(editing.paste_strips_formatting)
        form.ignore_accents_in_search.setChecked(editing.ignore_accents_in_search)
        form.pastePNG.setChecked(editing.paste_images_as_png)
        form.default_search_text.setText(editing.default_search_text)

        form.backup_explanation.setText(
            anki.lang.with_collapsed_whitespace(tr.preferences_backup_explanation())
        )
        form.daily_backups.setValue(self.prefs.backups.daily)
        form.weekly_backups.setValue(self.prefs.backups.weekly)
        form.monthly_backups.setValue(self.prefs.backups.monthly)
        form.minutes_between_backups.setValue(self.prefs.backups.minimum_interval_mins)

    def update_collection(self, on_done: Callable[[], None]) -> None:
        form = self.form

        scheduling = self.prefs.scheduling
        scheduling.learn_ahead_secs = form.lrnCutoff.value() * 60
        scheduling.rollover = form.dayOffset.value()

        reviewing = self.prefs.reviewing
        reviewing.show_remaining_due_counts = form.showProgress.isChecked()
        reviewing.show_intervals_on_buttons = form.showEstimates.isChecked()
        reviewing.time_limit_secs = form.timeLimit.value() * 60
        reviewing.hide_audio_play_buttons = not self.form.showPlayButtons.isChecked()
        reviewing.interrupt_audio_when_answering = self.form.interrupt_audio.isChecked()

        editing = self.prefs.editing
        editing.adding_defaults_to_current_deck = not form.useCurrent.currentIndex()
        editing.paste_images_as_png = self.form.pastePNG.isChecked()
        editing.paste_strips_formatting = self.form.paste_strips_formatting.isChecked()
        editing.default_search_text = self.form.default_search_text.text()
        editing.ignore_accents_in_search = (
            self.form.ignore_accents_in_search.isChecked()
        )

        self.prefs.backups.daily = form.daily_backups.value()
        self.prefs.backups.weekly = form.weekly_backups.value()
        self.prefs.backups.monthly = form.monthly_backups.value()
        self.prefs.backups.minimum_interval_mins = form.minutes_between_backups.value()

        def after_prefs_update(changes: OpChanges) -> None:
            self.mw.apply_collection_options()
            on_done()

        set_preferences(parent=self, preferences=self.prefs).success(
            after_prefs_update
        ).run_in_background()

    # Preferences stored in the profile
    ######################################################################

    def setup_profile(self) -> None:
        "Setup options stored in the user profile."
        self.setup_network()

    def update_profile(self) -> None:
        self.update_network()

    # Profile: network
    ######################################################################

    def setup_network(self) -> None:
        self.form.media_log.setText(tr.sync_media_log_button())
        qconnect(self.form.media_log.clicked, self.on_media_log)
        self.form.syncOnProgramOpen.setChecked(self.mw.pm.auto_syncing_enabled())
        self.form.syncMedia.setChecked(self.mw.pm.media_syncing_enabled())
        self.form.autoSyncMedia.setChecked(self.mw.pm.auto_sync_media_minutes() != 0)
        if not self.prof.get("syncKey"):
            self._hide_sync_auth_settings()
        else:
            self.form.syncUser.setText(self.prof.get("syncUser", ""))
            qconnect(self.form.syncDeauth.clicked, self.sync_logout)
        self.form.syncDeauth.setText(tr.sync_log_out_button())
        self.form.custom_sync_url.setText(self.mw.pm.custom_sync_url())
        self.form.network_timeout.setValue(self.mw.pm.network_timeout())

    def on_media_log(self) -> None:
        self.mw.media_syncer.show_sync_log()

    def _hide_sync_auth_settings(self) -> None:
        self.form.syncDeauth.setVisible(False)
        self.form.syncUser.setText("")
        self.form.syncLabel.setText(
            tr.preferences_synchronizationnot_currently_enabled_click_the_sync()
        )

    def sync_logout(self) -> None:
        if self.mw.media_syncer.is_syncing():
            showWarning("Can't log out while sync in progress.")
            return
        self.prof["syncKey"] = None
        self.mw.col.media.force_resync()
        self._hide_sync_auth_settings()

    def update_network(self) -> None:
        self.prof["autoSync"] = self.form.syncOnProgramOpen.isChecked()
        self.prof["syncMedia"] = self.form.syncMedia.isChecked()
        self.mw.pm.set_auto_sync_media_minutes(
            self.form.autoSyncMedia.isChecked() and 15 or 0
        )
        if self.form.fullSync.isChecked():
            self.mw.col.mod_schema(check=False)
        self.mw.pm.set_custom_sync_url(self.form.custom_sync_url.text())
        self.mw.pm.set_network_timeout(self.form.network_timeout.value())

    # Global preferences
    ######################################################################

    def setup_global(self) -> None:
        "Setup options global to all profiles."
        self.form.reduce_motion.setChecked(self.mw.pm.reduce_motion())
        qconnect(self.form.reduce_motion.stateChanged, self.mw.pm.set_reduce_motion)

        self.form.minimalist_mode.setChecked(self.mw.pm.minimalist_mode())
        qconnect(self.form.minimalist_mode.stateChanged, self.mw.pm.set_minimalist_mode)

        self.form.spacebar_rates_card.setChecked(self.mw.pm.spacebar_rates_card())
        qconnect(
            self.form.spacebar_rates_card.stateChanged,
            self.mw.pm.set_spacebar_rates_card,
        )

        hide_choices = [tr.preferences_full_screen_only(), tr.preferences_always()]

        self.form.hide_top_bar.setChecked(self.mw.pm.hide_top_bar())
        qconnect(self.form.hide_top_bar.stateChanged, self.mw.pm.set_hide_top_bar)
        qconnect(
            self.form.hide_top_bar.stateChanged,
            self.form.topBarComboBox.setVisible,
        )
        self.form.topBarComboBox.addItems(hide_choices)
        self.form.topBarComboBox.setCurrentIndex(self.mw.pm.top_bar_hide_mode())
        self.form.topBarComboBox.setVisible(self.form.hide_top_bar.isChecked())

        qconnect(
            self.form.topBarComboBox.currentIndexChanged,
            self.mw.pm.set_top_bar_hide_mode,
        )

        self.form.hide_bottom_bar.setChecked(self.mw.pm.hide_bottom_bar())
        qconnect(self.form.hide_bottom_bar.stateChanged, self.mw.pm.set_hide_bottom_bar)
        qconnect(
            self.form.hide_bottom_bar.stateChanged,
            self.form.bottomBarComboBox.setVisible,
        )
        self.form.bottomBarComboBox.addItems(hide_choices)
        self.form.bottomBarComboBox.setCurrentIndex(self.mw.pm.bottom_bar_hide_mode())
        self.form.bottomBarComboBox.setVisible(self.form.hide_bottom_bar.isChecked())

        qconnect(
            self.form.bottomBarComboBox.currentIndexChanged,
            self.mw.pm.set_bottom_bar_hide_mode,
        )

        self.form.uiScale.setValue(int(self.mw.pm.uiScale() * 100))
        themes = [
            tr.preferences_theme_follow_system(),
            tr.preferences_theme_light(),
            tr.preferences_theme_dark(),
        ]
        self.form.theme.addItems(themes)
        self.form.theme.setCurrentIndex(self.mw.pm.theme().value)
        qconnect(self.form.theme.currentIndexChanged, self.on_theme_changed)

        self.form.styleComboBox.addItems(["Anki"] + (["Native"] if not is_win else []))
        self.form.styleComboBox.setCurrentIndex(self.mw.pm.get_widget_style())
        qconnect(
            self.form.styleComboBox.currentIndexChanged,
            self.mw.pm.set_widget_style,
        )
        self.form.styleLabel.setVisible(not is_win)
        self.form.styleComboBox.setVisible(not is_win)
        self.form.legacy_import_export.setChecked(self.mw.pm.legacy_import_export())
        qconnect(self.form.resetWindowSizes.clicked, self.on_reset_window_sizes)

        self.setup_language()
        self.setup_video_driver()

        self.setupOptions()

    def update_global(self) -> None:
        restart_required = False

        self.update_video_driver()

        newScale = self.form.uiScale.value() / 100
        if newScale != self.mw.pm.uiScale():
            self.mw.pm.setUiScale(newScale)
            restart_required = True

        self.mw.pm.set_legacy_import_export(self.form.legacy_import_export.isChecked())

        if restart_required:
            showInfo(tr.preferences_changes_will_take_effect_when_you())

        self.updateOptions()

    def on_theme_changed(self, index: int) -> None:
        self.mw.set_theme(Theme(index))

    def on_reset_window_sizes(self) -> None:
        regexp = re.compile(r"(Geom(etry)?|State|Splitter|Header)(\d+.\d+)?$")
        for key in list(self.prof.keys()):
            if regexp.search(key):
                del self.prof[key]
        showInfo(tr.preferences_reset_window_sizes_complete())

    # legacy - one of Henrik's add-ons is currently wrapping them

    def setupOptions(self) -> None:
        pass

    def updateOptions(self) -> None:
        pass

    # Global: language
    ######################################################################

    def setup_language(self) -> None:
        f = self.form
        f.lang.addItems([x[0] for x in anki.lang.langs])
        f.lang.setCurrentIndex(self.current_lang_index())
        qconnect(f.lang.currentIndexChanged, self.on_language_index_changed)

    def current_lang_index(self) -> int:
        codes = [x[1] for x in anki.lang.langs]
        lang = anki.lang.current_lang
        if lang in anki.lang.compatMap:
            lang = anki.lang.compatMap[lang]
        else:
            lang = lang.replace("-", "_")
        try:
            return codes.index(lang)
        except:
            return codes.index("en_US")

    def on_language_index_changed(self, idx: int) -> None:
        code = anki.lang.langs[idx][1]
        self.mw.pm.setLang(code)
        showInfo(tr.preferences_please_restart_anki_to_complete_language(), parent=self)

    # Global: video driver
    ######################################################################

    def setup_video_driver(self) -> None:
        self.video_drivers = VideoDriver.all_for_platform()
        names = [video_driver_name_for_platform(d) for d in self.video_drivers]
        self.form.video_driver.addItems(names)
        self.form.video_driver.setCurrentIndex(
            self.video_drivers.index(self.mw.pm.video_driver())
        )

    def update_video_driver(self) -> None:
        new_driver = self.video_drivers[self.form.video_driver.currentIndex()]
        if new_driver != self.mw.pm.video_driver():
            self.mw.pm.set_video_driver(new_driver)
            showInfo(tr.preferences_changes_will_take_effect_when_you())


def video_driver_name_for_platform(driver: VideoDriver) -> str:
    if qtmajor < 6:
        if driver == VideoDriver.ANGLE:
            return tr.preferences_video_driver_angle()
        elif driver == VideoDriver.Software:
            if is_mac:
                return tr.preferences_video_driver_software_mac()
            else:
                return tr.preferences_video_driver_software_other()
        elif driver == VideoDriver.OpenGL:
            if is_mac:
                return tr.preferences_video_driver_opengl_mac()
            else:
                return tr.preferences_video_driver_opengl_other()

    label = driver.name
    if driver == VideoDriver.default_for_platform():
        label += f" ({tr.preferences_video_driver_default()})"

    return label
