import os

from PyQt6.QtWidgets import QMessageBox

from aqt.utils import tr
from aqt.utils import getFile, askUser, showInfo


def confirm(path):
    return askUser(
        tr.qt_misc_replace_your_collection_with_an_earlier2(os.path.basename(path)),
        msgfunc=QMessageBox.warning,
        defaultno=True,
    )

def inform():
    showInfo("Automatic syncing and backups have been disabled while restoring. To enable them again, close the profile or restart Anki.")


class RestoreBackupUseCase:
    def __init__(self):
        self.restore_func = lambda path, success, error: error(Exception("No back up function set."))
        self.inform_func = lambda: None

    def set_restore_func(self, restore_func):
        self.restore_func = restore_func

    def set_inform_func(self, inform_func):
        self.inform_func = inform_func

    def __call__(self, path: str, success, error):
        self.inform_func()
        self.restore_func(path, success, error)

class RestoreBackupWithConfirmUseCase:
    def __init__(self, open_backup_use_case: RestoreBackupUseCase):
        self.confirm_func = lambda path: False
        self.open_backup_use_case = open_backup_use_case

    def set_confirm_func(self, confirm_func):
        self.confirm_func = confirm_func

    def __call__(self, path: str, success, error):
        if self.confirm_func(path):
            self.open_backup_use_case(path, success, error)

class ChooseBackupUseCase:
    def __init__(self, restore_use_case: RestoreBackupUseCase):
        self.choose_path_func = lambda callback: None
        self.confirm_func = lambda path: False
        self.restore_use_case = restore_use_case

    def set_choose_path_func(self, choose_path_func):
        self.choose_path_func = choose_path_func

    def set_confirm_func(self, confirm_func):
        self.confirm_func = confirm_func

    def __call__(self, success, error):
        def on_path_chosen(path):
            if self.confirm_func(path):
                self.restore_use_case(path, success, error)

        self.choose_path_func(on_path_chosen)


restore_backup = RestoreBackupUseCase()

restore_backup_with_confirm = RestoreBackupWithConfirmUseCase(restore_backup)
restore_backup_with_confirm.set_confirm_func(confirm)

choose_and_restore_backup = ChooseBackupUseCase(restore_backup)
choose_and_restore_backup.set_confirm_func(confirm)
