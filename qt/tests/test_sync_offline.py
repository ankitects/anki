# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future
from typing import cast
from unittest.mock import MagicMock, patch

import pytest

import anki.lang
from anki.sync import SyncStatus
from anki.sync_pb2 import SyncAuth
from aqt.main import AnkiQt
from aqt.mediasync import MediaSyncer
from aqt.network import is_sync_offline
from aqt.profiles import ProfileManager
from aqt.qt import QNetworkInformation
from aqt.sync import get_sync_status, sync_collection


@pytest.fixture
def mw(monkeypatch) -> AnkiQt:
    monkeypatch.setattr(anki.lang, "current_i18n", None)
    monkeypatch.setattr(anki.lang, "current_lang", "en")
    monkeypatch.setattr(anki.lang.tr_legacyglobal, "backend", None)
    anki.lang.set_lang("en")
    window = MagicMock(spec=AnkiQt)
    window.pm = MagicMock(spec=ProfileManager)
    window.pm.custom_sync_url.return_value = None
    window.pm.sync_auth.return_value = SyncAuth(hkey="test-only")
    window.pm.auto_syncing_enabled.return_value = True
    window.media_syncer = MagicMock()
    window.media_syncer.is_syncing.return_value = False
    window.col = MagicMock()
    window.taskman = MagicMock()
    window.safeMode = False
    window.restoring_backup = False
    window._can_sync_unattended.side_effect = lambda: AnkiQt._can_sync_unattended(
        window
    )
    window.can_auto_sync.side_effect = lambda: AnkiQt.can_auto_sync(window)
    return cast(AnkiQt, window)


@pytest.fixture
def offline():
    with patch("aqt.network.QNetworkInformation.instance") as instance:
        instance.return_value.reachability.return_value = (
            QNetworkInformation.Reachability.Disconnected
        )
        yield instance


def test_auto_sync_continues_open_or_close_immediately_when_offline(mw, offline):
    after_sync = MagicMock()

    AnkiQt.maybe_auto_sync_on_open_close(mw, after_sync)

    after_sync.assert_called_once_with(False)
    mw._sync_collection_and_media.assert_not_called()


@patch("aqt.main.show_warning")
def test_sync_button_reports_offline_without_starting_sync(warning, mw, offline):
    AnkiQt.on_sync_button_clicked(mw)

    warning.assert_called_once_with("Please check your internet connection.", parent=mw)
    mw._sync_collection_and_media.assert_not_called()
    mw.taskman.with_progress.assert_not_called()


@patch("aqt.sync.show_warning")
@patch("aqt.sync.QTimer")
def test_collection_sync_finishes_without_network_work_when_offline(
    timer, warning, mw, offline
):
    done = MagicMock()

    sync_collection(mw, done)

    done.assert_called_once_with()
    mw.taskman.with_progress.assert_not_called()
    mw.col.sync_collection.assert_not_called()
    mw.pm.clear_sync_auth.assert_not_called()
    warning.assert_called_once_with("Please check your internet connection.", parent=mw)


@pytest.mark.parametrize("required", [SyncStatus.NORMAL_SYNC, SyncStatus.FULL_SYNC])
def test_status_check_reports_pending_changes_when_offline(mw, offline, required):
    callback = MagicMock()
    status = SyncStatus(required=required)
    mw.col.sync_status.return_value = status

    def run_in_background(task, on_done, **kwargs):
        future = Future()
        future.set_result(task())
        on_done(future)

    mw.taskman.run_in_background.side_effect = run_in_background

    get_sync_status(mw, callback)

    callback.assert_called_once_with(status)


def test_periodic_media_sync_does_not_start_when_offline(mw, offline):
    syncer = MagicMock(spec=MediaSyncer)
    syncer.mw = mw
    with patch("aqt.mediasync.QueryOp") as query:
        MediaSyncer.start(syncer, is_periodic_sync=True)

    query.assert_not_called()
    syncer.start_monitoring.assert_not_called()


def test_auto_sync_resumes_after_reconnecting(mw, offline):
    assert not AnkiQt.can_auto_sync(mw)

    offline.return_value.reachability.return_value = (
        QNetworkInformation.Reachability.Online
    )

    assert AnkiQt.can_auto_sync(mw)


@pytest.mark.parametrize(
    "state",
    [
        QNetworkInformation.Reachability.Unknown,
        QNetworkInformation.Reachability.Local,
        QNetworkInformation.Reachability.Site,
        QNetworkInformation.Reachability.Online,
    ],
)
def test_sync_is_allowed_without_a_definite_offline_signal(mw, offline, state):
    offline.return_value.reachability.return_value = state

    assert AnkiQt.can_auto_sync(mw)


def test_sync_is_allowed_without_a_network_backend(mw, offline):
    offline.return_value = None

    assert AnkiQt.can_auto_sync(mw)


@pytest.mark.parametrize(
    "endpoint",
    [
        "http://localhost:27701/",
        "http://127.0.0.1:27701/",
        "http://[::1]:27701/",
        "http://192.168.1.2:27701/",
        "https://sync.example.com/",
    ],
)
def test_custom_servers_remain_available_when_offline(mw, offline, endpoint):
    mw.pm.custom_sync_url.return_value = endpoint

    assert not is_sync_offline(mw.pm)
    assert AnkiQt.can_auto_sync(mw)
