from __future__ import annotations

from concurrent.futures import Future

from PyQt6.QtWidgets import QMessageBox, QMainWindow

from aqt import mw
from aqt.profiles import ProfileManager as ProfileManagerType
from aqt.utils import askUser, checkInvalidFilename, getOnlyText, showInfo, showWarning, tr


class ProfileDialog:
    _activeProfile: int = 0
    _profiles = []

    def __init__(self, pm: ProfileManagerType):
        self.pm = pm

    def _refreshProfiles(self):
        self._profiles = self.pm.profiles()
        try:
            self._activeProfile = self._profiles.index(self.pm.name)
        except Exception:
            self._activeProfile = 0
        mw.profileForm.profiles.clear()
        mw.profileForm.profiles.addItems(self._profiles)
        mw.profileForm.profiles.setCurrentRow(self._activeProfile)

    def _profileNameOk(self, name: str) -> bool:
        return not checkInvalidFilename(name) and name != "addons21"

    def _activeProfileName(self):
        return self._profiles[self._activeProfile]

    def onOpenProfile(self) -> None:
        self.pm.load(self._activeProfileName())
        if mw.loadCollection():
            self.d.hide()

    def onAddProfile(self) -> None:
        name = getOnlyText(tr.actions_name()).strip()
        if name:
            if name in self._profiles:
                showWarning(tr.qt_misc_name_exists())
                return
            if not self._profileNameOk(name):
                return
            self.pm.create(name)
            self.pm.name = name
            self._refreshProfiles()

    def onOpenBackup(self) -> None:
        from aqt.openbackup import (
            restore_backup,
            restore_backup_with_confirm,
            choose_and_restore_backup
        )

        def success():
            self.pm.load(self._activeProfileName())
            if mw.loadCollection():
                self.d.hide()

        def error(e: Exception):
            showWarning("Backup could not be restored\n" + str(e))

        choose_and_restore_backup(success, error)

    def onQuit(self) -> None:
        from aqt.utils import cleanup_and_exit

        cleanup_and_exit()

    def onRenameProfile(self) -> None:
        name = getOnlyText(
            tr.actions_new_name(), default=self._activeProfileName()
        ).strip()
        if not name:
            return
        if name == self._activeProfileName():
            return
        if name in self._profiles:
            showWarning(tr.qt_misc_name_exists())
            return
        if not self._profileNameOk(name):
            return
        self.pm.rename(name)
        self._refreshProfiles()

    def onRemProfile(self) -> None:
        if len(self._profiles) < 2:
            showWarning(tr.qt_misc_there_must_be_at_least_one())
            return
        # sure?
        if not askUser(
            tr.qt_misc_all_cards_notes_and_media_for2(name=self._activeProfileName()),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            return
        self.pm.remove(self._activeProfileName())
        self._refreshProfiles()

    def onProfileRowChange(self, n: int) -> None:
        if n < 0:
            # called on .clear()
            return

        self._activeProfile = n

    def onDowngrade(self) -> None:
        mw.progress.start()
        profiles = mw.pm.profiles()

        def downgrade() -> list[str]:
            return mw.pm.downgrade(profiles)

        def on_done(future: Future) -> None:
            mw.progress.finish()
            problems = future.result()
            if not problems:
                showInfo("Profiles can now be opened with an older version of Anki.")
            else:
                showWarning(
                    "The following profiles could not be downgraded: {}".format(
                        ", ".join(problems)
                    )
                )
                return
            from aqt.utils import cleanup_and_exit

            cleanup_and_exit()

        mw.taskman.run_in_background(downgrade, on_done)

    def show(self):
        import aqt
        from aqt.qt import QKeySequence, QShortcut, qconnect
        from aqt.utils import tr

        d = self.d = QMainWindow()
        f = mw.profileForm = aqt.forms.profiles.Ui_MainWindow()
        f.setupUi(d)
        qconnect(f.login.clicked, self.onOpenProfile)
        qconnect(f.profiles.itemDoubleClicked, self.onOpenProfile)
        qconnect(f.openBackup.clicked, self.onOpenBackup)
        qconnect(f.quit.clicked, self.onQuit)
        qconnect(f.add.clicked, self.onAddProfile)
        qconnect(f.rename.clicked, self.onRenameProfile)
        d.closeEvent = lambda ev: self.onQuit()
        qconnect(f.delete_2.clicked, self.onRemProfile)
        qconnect(f.profiles.currentRowChanged, self.onProfileRowChange)
        f.statusbar.setVisible(False)
        qconnect(f.downgrade_button.clicked, self.onDowngrade)
        f.downgrade_button.setText(tr.profiles_downgrade_and_quit())
        # enter key opens profile
        QShortcut(QKeySequence("Return"), d, activated=self.onOpenProfile)  # type: ignore
        self._refreshProfiles()
        # raise first, for osx testing
        d.show()
        d.activateWindow()
        d.raise_()

