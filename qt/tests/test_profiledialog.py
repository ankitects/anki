import importlib
import sys
import os

# Add the root project directory to sys.path

import unittest
from unittest.mock import Mock, patch, call



class TestProfileDialog(unittest.TestCase):
    def setUp(self):
        self.pm = Mock()
        self.pm.profiles.return_value = ["user1", "user2"]
        self.pm.name = "user1"

        # Patch mw and profileForm
        patcher1 = patch("aqt.mw")

        self.mock_mw = patcher1.start()
        self.addCleanup(patcher1.stop)
        if "aqt.profiledialog" in sys.modules:
            importlib.reload(sys.modules["aqt.profiledialog"])
        else:
            importlib.import_module("aqt.profiledialog")

        from aqt.profiledialog import ProfileDialog

        # mock profileForm
        self.mock_profiles = Mock()
        self.mock_mw.profileForm.profiles = self.mock_profiles

        # other mocks
        self.mock_mw.profileNameOk.return_value = True
        self.mock_mw.pm = self.pm
        self.mock_mw.taskman = Mock()
        self.mock_mw.progress = Mock()

        self.dialog = ProfileDialog(self.pm)

    def test_refresh_profiles_sets_correct_index(self):
        self.dialog._refreshProfiles()

        self.assertEqual(self.dialog._activeProfile, 0)
        self.mock_profiles.clear.assert_called_once()
        self.mock_profiles.addItems.assert_called_once_with(["user1", "user2"])
        self.mock_profiles.setCurrentRow.assert_called_once_with(0)

    def test_active_profile_name(self):
        self.dialog._profiles = ["x", "y"]
        self.dialog._activeProfile = 1
        self.assertEqual(self.dialog._activeProfileName(), "y")

    def test_on_open_profile_calls_load(self):
        self.dialog._profiles = ["user1"]
        self.dialog._activeProfile = 0
        self.dialog.onOpenProfile()
        self.pm.load.assert_called_once_with("user1")
        self.mock_mw.loadCollection.assert_called_once()

    def test_on_add_profile_adds_new_profile(self):
        self.dialog._profiles = ["user1"]
        with patch("aqt.profiledialog.getOnlyText", return_value="newuser"), \
                patch("aqt.profiledialog.tr") as mock_tr:
            mock_tr.actions_name.return_value = "Enter name"
            self.dialog.onAddProfile()
            self.pm.create.assert_called_once_with("newuser")
            self.assertEqual(self.pm.name, "newuser")

    def test_on_add_profile_duplicate(self):
        self.dialog._profiles = ["user1", "dupe"]
        with patch("aqt.profiledialog.getOnlyText", return_value="dupe"), \
                patch("aqt.profiledialog.tr") as mock_tr, \
                patch("aqt.profiledialog.showWarning") as mock_warn:
            mock_tr.actions_name.return_value = "Enter name"
            mock_tr.qt_misc_name_exists.return_value = "Name exists"
            self.dialog.onAddProfile()
            mock_warn.assert_called_once_with("Name exists")

    def test_on_rename_profile_valid(self):
        self.dialog._profiles = ["user1"]
        self.dialog._activeProfile = 0
        with patch("aqt.profiledialog.getOnlyText", return_value="renamed"), \
                patch("aqt.profiledialog.tr") as mock_tr:
            mock_tr.actions_new_name.return_value = "New name"
            self.dialog.onRenameProfile()
            self.pm.rename.assert_called_once_with("renamed")

    def test_on_rem_profile_confirms_and_removes(self):
        self.dialog._profiles = ["user1", "user2"]
        self.dialog._activeProfile = 0
        with patch("aqt.profiledialog.tr") as mock_tr, \
                patch("aqt.profiledialog.askUser", return_value=True):
            mock_tr.qt_misc_all_cards_notes_and_media_for2.return_value = "Delete?"
            self.dialog.onRemProfile()
            self.pm.remove.assert_called_once_with("user1")

    def test_on_profile_row_change_updates_index(self):
        self.dialog._activeProfile = 0
        self.dialog.onProfileRowChange(1)
        self.assertEqual(self.dialog._activeProfile, 1)

    def test_on_profile_row_change_negative_does_not_update(self):
        self.dialog._activeProfile = 0
        self.dialog.onProfileRowChange(-1)
        self.assertEqual(self.dialog._activeProfile, 0)

    def test_on_downgrade_runs_task(self):
        self.mock_mw.pm.downgrade.return_value = []
        future = Mock()
        future.result.return_value = []
        on_done = None

        def run_in_background(fn, cb):
            nonlocal on_done
            on_done = cb
            return fn()

        self.mock_mw.taskman.run_in_background.side_effect = run_in_background
        self.dialog.onDowngrade()
        self.assertTrue(callable(on_done))
        on_done(Mock(result=lambda: []))
        self.mock_mw.progress.start.assert_called()
        self.mock_mw.progress.finish.assert_called()
