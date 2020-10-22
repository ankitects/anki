# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import enum
import os
from typing import Callable, Tuple

import aqt
from anki.lang import without_unicode_isolation
from anki.rsbackend import (
    TR,
    FullSyncProgress,
    Interrupted,
    NormalSyncProgress,
    ProgressKind,
    SyncError,
    SyncErrorKind,
    SyncOutput,
    SyncStatus,
)
from anki.utils import platDesc
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
from aqt.utils import askUser, askUserDialog, showText, showWarning, tr


class FullSyncChoice(enum.Enum):
    CANCEL = 0
    UPLOAD = 1
    DOWNLOAD = 2


def get_sync_status(mw: aqt.main.AnkiQt, callback: Callable[[SyncStatus], None]):
    auth = mw.pm.sync_auth()
    if not auth:
        return SyncStatus(required=SyncStatus.NO_CHANGES)  # pylint:disable=no-member

    def on_future_done(fut):
        try:
            out = fut.result()
        except Exception as e:
            # swallow errors
            print("sync status check failed:", str(e))
            return
        callback(out)

    mw.taskman.run_in_background(
        lambda: mw.col.backend.sync_status(auth), on_future_done
    )


def handle_sync_error(mw: aqt.main.AnkiQt, err: Exception):
    if isinstance(err, SyncError):
        if err.kind() == SyncErrorKind.AUTH_FAILED:
            mw.pm.clear_sync_auth()
    elif isinstance(err, Interrupted):
        # no message to show
        return
    showWarning(str(err))


def on_normal_sync_timer(mw: aqt.main.AnkiQt) -> None:
    progress = mw.col.latest_progress()
    if progress.kind != ProgressKind.NormalSync:
        return

    assert isinstance(progress.val, NormalSyncProgress)
    mw.progress.update(
        label=f"{progress.val.added}\n{progress.val.removed}",
        process=False,
    )
    mw.progress.set_title(progress.val.stage)

    if mw.progress.want_cancel():
        mw.col.backend.abort_sync()


def sync_collection(mw: aqt.main.AnkiQt, on_done: Callable[[], None]) -> None:
    auth = mw.pm.sync_auth()
    assert auth

    def on_timer():
        on_normal_sync_timer(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_future_done(fut):
        mw.col.db.begin()
        timer.stop()
        try:
            out: SyncOutput = fut.result()
        except Exception as err:
            handle_sync_error(mw, err)
            return on_done()

        mw.pm.set_host_number(out.host_number)
        if out.server_message:
            showText(out.server_message)
        if out.required == out.NO_CHANGES:
            # all done
            return on_done()
        else:
            full_sync(mw, out, on_done)

    if not mw.col.basicCheck():
        showWarning("Please use Tools>Check Database")
        return on_done()

    mw.col.save(trx=False)
    mw.taskman.with_progress(
        lambda: mw.col.backend.sync_collection(auth),
        on_future_done,
        label=tr(TR.SYNC_CHECKING),
        immediate=True,
    )


def full_sync(
    mw: aqt.main.AnkiQt, out: SyncOutput, on_done: Callable[[], None]
) -> None:
    if out.required == out.FULL_DOWNLOAD:
        confirm_full_download(mw, on_done)
    elif out.required == out.FULL_UPLOAD:
        full_upload(mw, on_done)
    else:
        choice = ask_user_to_decide_direction()
        if choice == FullSyncChoice.UPLOAD:
            full_upload(mw, on_done)
        elif choice == FullSyncChoice.DOWNLOAD:
            full_download(mw, on_done)
        else:
            on_done()


def confirm_full_download(mw: aqt.main.AnkiQt, on_done: Callable[[], None]) -> None:
    # confirmation step required, as some users customize their notetypes
    # in an empty collection, then want to upload them
    if not askUser(tr(TR.SYNC_CONFIRM_EMPTY_DOWNLOAD)):
        return on_done()
    else:
        mw.closeAllWindows(lambda: full_download(mw, on_done))


def on_full_sync_timer(mw: aqt.main.AnkiQt) -> None:
    progress = mw.col.latest_progress()
    if progress.kind != ProgressKind.FullSync:
        return

    assert isinstance(progress.val, FullSyncProgress)
    if progress.val.transferred == progress.val.total:
        label = tr(TR.SYNC_CHECKING)
    else:
        label = None
    mw.progress.update(
        value=progress.val.transferred,
        max=progress.val.total,
        process=False,
        label=label,
    )

    if mw.progress.want_cancel():
        mw.col.backend.abort_sync()


def full_download(mw: aqt.main.AnkiQt, on_done: Callable[[], None]) -> None:
    mw.col.close_for_full_sync()

    def on_timer():
        on_full_sync_timer(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_future_done(fut):
        timer.stop()
        mw.col.reopen(after_full_sync=True)
        mw.reset()
        try:
            fut.result()
        except Exception as err:
            handle_sync_error(mw, err)
        mw.media_syncer.start()
        return on_done()

    mw.taskman.with_progress(
        lambda: mw.col.backend.full_download(mw.pm.sync_auth()),
        on_future_done,
        label=tr(TR.SYNC_DOWNLOADING_FROM_ANKIWEB),
    )


def full_upload(mw: aqt.main.AnkiQt, on_done: Callable[[], None]) -> None:
    mw.col.close_for_full_sync()

    def on_timer():
        on_full_sync_timer(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_future_done(fut):
        timer.stop()
        mw.col.reopen(after_full_sync=True)
        mw.reset()
        try:
            fut.result()
        except Exception as err:
            handle_sync_error(mw, err)
            return on_done()
        mw.media_syncer.start()
        return on_done()

    mw.taskman.with_progress(
        lambda: mw.col.backend.full_upload(mw.pm.sync_auth()),
        on_future_done,
        label=tr(TR.SYNC_UPLOADING_TO_ANKIWEB),
    )


def sync_login(
    mw: aqt.main.AnkiQt, on_success: Callable[[], None], username="", password=""
) -> None:
    while True:
        (username, password) = get_id_and_pass_from_user(mw, username, password)
        if not username and not password:
            return
        if username and password:
            break

    def on_future_done(fut):
        try:
            auth = fut.result()
        except SyncError as e:
            if e.kind() == SyncErrorKind.AUTH_FAILED:
                showWarning(str(e))
                sync_login(mw, on_success, username, password)
                return
        except Exception as err:
            handle_sync_error(mw, err)
            return

        mw.pm.set_host_number(auth.host_number)
        mw.pm.set_sync_key(auth.hkey)
        mw.pm.set_sync_username(username)

        on_success()

    mw.taskman.with_progress(
        lambda: mw.col.backend.sync_login(username=username, password=password),
        on_future_done,
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
        without_unicode_isolation(
            tr(TR.SYNC_ACCOUNT_REQUIRED, link="https://ankiweb.net/account/register")
        )
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


# export platform version to syncing code
os.environ["PLATFORM"] = platDesc()
