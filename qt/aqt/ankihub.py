# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import functools
from concurrent.futures import Future
from typing import Callable

import aqt
import aqt.main
from aqt.addons import (
    AddonManager,
    DownloadLogEntry,
    install_or_update_addon,
    show_log_to_user,
)
from aqt.qt import (
    QDialog,
    QDialogButtonBox,
    QGridLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    Qt,
    QVBoxLayout,
    QWidget,
    qconnect,
)
from aqt.utils import disable_help_button, showWarning, tr


def ankihub_login(
    mw: aqt.main.AnkiQt,
    on_success: Callable[[], None],
    username: str = "",
    password: str = "",
) -> None:

    def on_future_done(fut: Future[str], username: str, password: str) -> None:
        try:
            token = fut.result()
        except Exception as exc:
            showWarning(str(exc))
            return

        if not token:
            showWarning(tr.sync_ankihub_login_failed(), parent=mw)
            ankihub_login(mw, on_success, username, password)
            return
        mw.pm.set_ankihub_token(token)
        mw.pm.set_ankihub_username(username)
        install_ankihub_addon(mw, mw.addonManager)
        on_success()

    def callback(username: str, password: str) -> None:
        if not username and not password:
            return
        if username and password:
            mw.taskman.with_progress(
                lambda: mw.col.ankihub_login(id=username, password=password),
                functools.partial(on_future_done, username=username, password=password),
                parent=mw,
            )
        else:
            ankihub_login(mw, on_success, username, password)

    get_id_and_pass_from_user(mw, callback, username, password)


def ankihub_logout(
    mw: aqt.main.AnkiQt,
    on_success: Callable[[], None],
    token: str,
) -> None:

    def logout() -> None:
        mw.pm.set_ankihub_username(None)
        mw.pm.set_ankihub_token(None)
        mw.col.ankihub_logout(token=token)

    mw.taskman.with_progress(
        logout,
        # We don't need to wait for the response
        lambda _: on_success(),
        parent=mw,
    )


def get_id_and_pass_from_user(
    mw: aqt.main.AnkiQt,
    callback: Callable[[str, str], None],
    username: str = "",
    password: str = "",
) -> None:
    diag = QDialog(mw)
    diag.setWindowTitle("Anki")
    disable_help_button(diag)
    diag.setWindowModality(Qt.WindowModality.WindowModal)
    diag.setMinimumWidth(600)
    vbox = QVBoxLayout()
    info_label = QLabel(f"<h1>{tr.sync_ankihub_dialog_heading()}</h1>")
    info_label.setOpenExternalLinks(True)
    info_label.setWordWrap(True)
    vbox.addWidget(info_label)
    vbox.addSpacing(20)
    g = QGridLayout()
    l1 = QLabel(tr.sync_ankihub_username_label())
    g.addWidget(l1, 0, 0)
    user = QLineEdit()
    user.setText(username)
    g.addWidget(user, 0, 1)
    l2 = QLabel(tr.sync_password_label())
    g.addWidget(l2, 1, 0)
    passwd = QLineEdit()
    passwd.setText(password)
    passwd.setEchoMode(QLineEdit.EchoMode.Password)
    g.addWidget(passwd, 1, 1)
    vbox.addLayout(g)

    vbox.addSpacing(20)
    bb = QDialogButtonBox()  # type: ignore
    sign_in_button = QPushButton(tr.sync_sign_in())
    sign_in_button.setAutoDefault(True)
    bb.addButton(
        QPushButton(tr.actions_cancel()),
        QDialogButtonBox.ButtonRole.RejectRole,
    )
    bb.addButton(
        sign_in_button,
        QDialogButtonBox.ButtonRole.AcceptRole,
    )
    qconnect(bb.accepted, diag.accept)
    qconnect(bb.rejected, diag.reject)
    vbox.addWidget(bb)

    diag.setLayout(vbox)
    diag.adjustSize()
    diag.show()
    user.setFocus()

    def on_finished(result: int) -> None:
        if result == QDialog.DialogCode.Rejected:
            callback("", "")
        else:
            callback(user.text().strip(), passwd.text())

    qconnect(diag.finished, on_finished)
    diag.open()


def install_ankihub_addon(parent: QWidget, mgr: AddonManager) -> None:
    def on_done(log: list[DownloadLogEntry]) -> None:
        if log:
            show_log_to_user(parent, log, title=tr.sync_ankihub_addon_installation())

    install_or_update_addon(parent, mgr, 1322529746, on_done)
