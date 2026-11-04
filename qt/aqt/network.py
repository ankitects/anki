# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import TYPE_CHECKING

from aqt.qt import QNetworkInformation

if TYPE_CHECKING:
    from aqt.profiles import ProfileManager


def setup_network_information() -> None:
    # Initialize on the GUI thread, before the first sync. The native backend
    # updates its cached state as connectivity changes; no network probe is needed.
    # Qt 6.2 lacks loadDefaultBackend(), so keep its existing sync behavior.
    if hasattr(QNetworkInformation, "loadDefaultBackend"):
        QNetworkInformation.loadDefaultBackend()


def is_sync_offline(pm: ProfileManager) -> bool:
    # Custom servers may be reachable over loopback or a local network even when
    # the OS reports no internet connection. Let their requests decide.
    if pm.custom_sync_url():
        return False
    information = QNetworkInformation.instance()
    # Unknown/unsupported reachability must not prevent syncing.
    return (
        information is not None
        and information.reachability() == QNetworkInformation.Reachability.Disconnected
    )
