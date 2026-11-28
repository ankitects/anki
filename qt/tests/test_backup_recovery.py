# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from unittest.mock import MagicMock

from aqt.main import AnkiQt


def test_periodic_backup_requests_backup_normally() -> None:
    window = MagicMock(spec=AnkiQt)
    window.restoring_backup = False

    AnkiQt.on_periodic_backup_timer(window)

    window._create_backup_with_progress.assert_called_once_with(user_initiated=False)


def test_recovery_skips_automatic_backups_but_allows_manual_backup() -> None:
    window = MagicMock(spec=AnkiQt)
    window.restoring_backup = True

    AnkiQt.on_periodic_backup_timer(window)

    window._create_backup_with_progress.assert_not_called()

    AnkiQt.on_create_backup_now(window)

    window._create_backup_with_progress.assert_called_once_with(user_initiated=True)
