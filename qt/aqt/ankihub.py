# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future
from typing import Callable

import aqt
import aqt.main
from anki.collection import AnkiHubLoginResponse
from anki.lang import without_unicode_isolation
from aqt.qt import (
    QDialog,
    QDialogButtonBox,
    QGridLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    Qt,
    QVBoxLayout,
    qconnect,
)
from aqt.utils import disable_help_button, showWarning, tr


def ankihub_login(
    mw: aqt.main.AnkiQt,
    on_success: Callable[[], None],
    username: str = "",
    password: str = "",
    from_prefs_screen: bool = False,
) -> None:
    while True:
        (username, password) = get_id_and_pass_from_user(
            mw, username, password, from_prefs_screen
        )
        if not username and not password:
            return
        if username and password:
            break

    def on_future_done(fut: Future[AnkiHubLoginResponse]) -> None:
        try:
            resp = fut.result()
        except Exception as exc:
            showWarning(str(exc))
            return

        if not resp.token and resp.server_errors:
            showWarning(
                tr.sync_ankihub_server_error()
                + "<br><br>"
                + "<br>".join(resp.server_errors),
                parent=mw,
            )
            ankihub_login(mw, on_success, username, password)
            return
        else:
            mw.pm.set_ankihub_token(resp.token)
            mw.pm.set_ankihub_username(username)

        on_success()

    mw.taskman.with_progress(
        lambda: mw.col.ankihub_login(username=username, password=password),
        on_future_done,
        parent=mw,
    )


def ankihub_logout(
    mw: aqt.main.AnkiQt,
    on_success: Callable[[], None],
    token: str,
) -> None:
    mw.taskman.with_progress(
        lambda: mw.col.ankihub_logout(token=token),
        # We don't need to wait for the response
        lambda _: on_success(),
        parent=mw,
    )


def get_id_and_pass_from_user(
    mw: aqt.main.AnkiQt,
    username: str = "",
    password: str = "",
    from_prefs_screen: bool = False,
) -> tuple[str, str]:
    diag = QDialog(mw)
    diag.setWindowTitle("Anki")
    disable_help_button(diag)
    diag.setWindowModality(Qt.WindowModality.WindowModal)
    vbox = QVBoxLayout()
    info_label = QLabel(
        f"<h1>{tr.sync_ankihub_dialog_heading()}</h1>{tr.preferences_ankihub_intro()}"
    )
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
    sign_in_label = QLabel(
        without_unicode_isolation(
            tr.sync_ankihub_signup_label(
                signup_link="https://app.ankihub.net/accounts/signup/",
                password_reset_link="https://app.ankihub.net/accounts/password/reset/",
            )
        )
    )
    sign_in_label.setOpenExternalLinks(True)
    sign_in_label.setWordWrap(True)
    vbox.addWidget(sign_in_label)

    vbox.addSpacing(20)
    bb = QDialogButtonBox()  # type: ignore
    sign_in_button = QPushButton(tr.sync_ankihub_sign_in_button_label())
    sign_in_button.setAutoDefault(True)
    bb.addButton(
        QPushButton(tr.actions_cancel() if from_prefs_screen else tr.actions_skip()),
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
    diag.show()
    user.setFocus()

    accepted = diag.exec()
    if not accepted:
        return ("", "")
    return (user.text().strip(), passwd.text())
