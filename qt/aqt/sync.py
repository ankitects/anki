# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import enum
from typing import Callable, Tuple

import aqt
from anki.rsbackend import (
    TR,
    FullSyncProgress,
    ProgressKind,
    SyncError,
    SyncErrorKind,
    SyncOutput,
)
from aqt.qt import (
    QDialog,
    QDialogButtonBox,
    QGridLayout,
    QLabel,
    QLineEdit,
    Qt,
    QTimer,
    QVBoxLayout,
    qconnect,
)
from aqt.utils import askUser, askUserDialog, showWarning, tr


class FullSyncChoice(enum.Enum):
    CANCEL = 0
    UPLOAD = 1
    DOWNLOAD = 2


def get_sync_status(mw: aqt.main.AnkiQt, callback: Callable[[SyncOutput], None]):
    auth = mw.pm.sync_auth()
    if not auth:
        return

    def on_done(fut):
        callback(fut.result())

    mw.taskman.run_in_background(lambda: mw.col.backend.sync_status(auth), on_done)


def sync(mw: aqt.main.AnkiQt) -> None:
    auth = mw.pm.sync_auth()
    if not auth:
        login(mw, on_success=lambda: sync(mw))
        return

    def on_done(fut):
        mw.col.db.begin()
        try:
            out: SyncOutput = fut.result()
        except InterruptedError:
            return
        except Exception as e:
            showWarning(str(e))
            return

        mw.pm.set_host_number(out.host_number)
        if out.required == out.NO_CHANGES:
            # all done
            return
        else:
            full_sync(mw, out)

    if not mw.col.basicCheck():
        showWarning("Please use Tools>Check Database")
        return

    mw.col.save(trx=False)
    mw.taskman.with_progress(
        lambda: mw.col.backend.sync_collection(auth),
        on_done,
        label=tr(TR.SYNC_CHECKING),
    )


def full_sync(mw: aqt.main.AnkiQt, out: SyncOutput) -> None:
    if out.required == out.FULL_DOWNLOAD:
        confirm_full_download(mw)
    elif out.required == out.FULL_UPLOAD:
        full_upload(mw)
    else:
        choice = ask_user_to_decide_direction()
        if choice == FullSyncChoice.UPLOAD:
            full_upload(mw)
        elif choice == FullSyncChoice.DOWNLOAD:
            full_download(mw)


def confirm_full_download(mw: aqt.main.AnkiQt) -> None:
    # confirmation step required, as some users customize their notetypes
    # in an empty collection, then want to upload them
    if not askUser(tr(TR.SYNC_CONFIRM_EMPTY_DOWNLOAD)):
        return
    else:
        mw.closeAllWindows(lambda: full_download(mw))


def on_full_sync_timer(mw: aqt.main.AnkiQt) -> None:
    progress = mw.col.latest_progress()
    if progress.kind != ProgressKind.FullSync:
        return

    assert isinstance(progress.val, FullSyncProgress)
    mw.progress.update(value=progress.val.transferred, max=progress.val.total, process=False)

    if mw.progress.want_cancel():
        mw.col.backend.abort_sync()


def full_download(mw: aqt.main.AnkiQt) -> None:
    mw.col.close_for_full_sync()

    def on_timer():
        on_full_sync_timer(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_done(fut):
        timer.stop()
        mw.col.reopen(after_full_sync=True)
        mw.reset()
        try:
            fut.result()
        except Exception as e:
            showWarning(str(e))
            return

    mw.taskman.with_progress(
        lambda: mw.col.backend.full_download(mw.pm.sync_auth()),
        on_done,
        label=tr(TR.SYNC_DOWNLOADING_FROM_ANKIWEB),
    )


def full_upload(mw: aqt.main.AnkiQt) -> None:
    mw.col.close_for_full_sync()

    def on_timer():
        on_full_sync_timer(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_done(fut):
        timer.stop()
        mw.col.reopen(after_full_sync=True)
        mw.reset()
        try:
            fut.result()
        except Exception as e:
            showWarning(str(e))
            return

    mw.taskman.with_progress(
        lambda: mw.col.backend.full_upload(mw.pm.sync_auth()),
        on_done,
        label=tr(TR.SYNC_UPLOADING_TO_ANKIWEB),
    )


def login(
    mw: aqt.main.AnkiQt, on_success: Callable[[], None], username="", password=""
) -> None:
    while True:
        (username, password) = get_id_and_pass_from_user(mw, username, password)
        if not username and not password:
            return
        if username and password:
            break

    def on_done(fut):
        try:
            auth = fut.result()
        except SyncError as e:
            if e.kind() == SyncErrorKind.AUTH_FAILED:
                showWarning(str(e))
                login(mw, on_success, username, password)
                return
        except Exception as e:
            showWarning(str(e))
            return

        mw.pm.set_host_number(auth.host_number)
        mw.pm.set_sync_key(auth.hkey)
        mw.pm.set_sync_username(username)

        on_success()

    mw.taskman.with_progress(
        lambda: mw.col.backend.sync_login(username=username, password=password), on_done
    )


def ask_user_to_decide_direction() -> FullSyncChoice:
    button_labels = [
        tr(TR.SYNC_UPLOAD_TO_ANKIWEB),
        tr(TR.SYNC_DOWNLOAD_FROM_ANKIWEB),
        tr(TR.SYNC_CANCEL_BUTTON),
    ]
    diag = askUserDialog(tr(TR.SYNC_CONFLICT_EXPLANATION), button_labels)
    diag.setDefault(2)
    ret = diag.run()
    if ret == button_labels[0]:
        return FullSyncChoice.UPLOAD
    elif ret == button_labels[1]:
        return FullSyncChoice.DOWNLOAD
    else:
        return FullSyncChoice.CANCEL


def get_id_and_pass_from_user(
    mw: aqt.main.AnkiQt, username="", password=""
) -> Tuple[str, str]:
    diag = QDialog(mw)
    diag.setWindowTitle("Anki")
    diag.setWindowModality(Qt.WindowModal)
    vbox = QVBoxLayout()
    info_label = QLabel(
        tr(TR.SYNC_ACCOUNT_REQUIRED, link="https://ankiweb.net/account/login")
    )
    info_label.setOpenExternalLinks(True)
    info_label.setWordWrap(True)
    vbox.addWidget(info_label)
    vbox.addSpacing(20)
    g = QGridLayout()
    l1 = QLabel(tr(TR.SYNC_ANKIWEB_ID_LABEL))
    g.addWidget(l1, 0, 0)
    user = QLineEdit()
    user.setText(username)
    g.addWidget(user, 0, 1)
    l2 = QLabel(tr(TR.SYNC_PASSWORD_LABEL))
    g.addWidget(l2, 1, 0)
    passwd = QLineEdit()
    passwd.setText(password)
    passwd.setEchoMode(QLineEdit.Password)
    g.addWidget(passwd, 1, 1)
    vbox.addLayout(g)
    bb = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)  # type: ignore
    bb.button(QDialogButtonBox.Ok).setAutoDefault(True)
    qconnect(bb.accepted, diag.accept)
    qconnect(bb.rejected, diag.reject)
    vbox.addWidget(bb)
    diag.setLayout(vbox)
    diag.show()

    accepted = diag.exec_()
    if not accepted:
        return ("", "")
    return (user.text().strip(), passwd.text())
