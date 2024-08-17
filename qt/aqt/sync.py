# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import functools
import os
from collections.abc import Callable
from concurrent.futures import Future

import aqt
import aqt.main
from anki.errors import Interrupted, SyncError, SyncErrorKind
from anki.lang import without_unicode_isolation
from anki.sync import SyncOutput, SyncStatus
from anki.sync_pb2 import SyncAuth
from anki.utils import plat_desc
from aqt import gui_hooks
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
from aqt.utils import (
    ask_user_dialog,
    disable_help_button,
    show_warning,
    showText,
    showWarning,
    tooltip,
    tr,
)


def get_sync_status(
    mw: aqt.main.AnkiQt, callback: Callable[[SyncStatus], None]
) -> None:
    auth = mw.pm.sync_auth()
    if not auth:
        callback(SyncStatus(required=SyncStatus.NO_CHANGES))  # pylint:disable=no-member
        return

    def on_future_done(fut: Future[SyncStatus]) -> None:
        try:
            out = fut.result()
        except Exception as e:
            # swallow errors
            print("sync status check failed:", str(e))
            return
        if out.new_endpoint:
            mw.pm.set_current_sync_url(out.new_endpoint)
        callback(out)

    mw.taskman.run_in_background(
        lambda: mw.col.sync_status(auth),
        on_future_done,
        # The check quickly releases the collection, and we don't need to block other callers
        uses_collection=False,
    )


def handle_sync_error(mw: aqt.main.AnkiQt, err: Exception) -> None:
    if isinstance(err, SyncError):
        if err.kind is SyncErrorKind.AUTH:
            mw.pm.clear_sync_auth()
    elif isinstance(err, Interrupted):
        # no message to show
        return
    show_warning(str(err))


def on_normal_sync_timer(mw: aqt.main.AnkiQt) -> None:
    progress = mw.col.latest_progress()
    if not progress.HasField("normal_sync"):
        return
    sync_progress = progress.normal_sync

    mw.progress.update(
        label=f"{sync_progress.added}\n{sync_progress.removed}",
        process=False,
    )
    mw.progress.set_title(sync_progress.stage)

    if mw.progress.want_cancel():
        mw.col.abort_sync()


def sync_collection(mw: aqt.main.AnkiQt, on_done: Callable[[], None]) -> None:
    auth = mw.pm.sync_auth()
    if not auth:
        raise Exception("expected auth")

    def on_timer() -> None:
        on_normal_sync_timer(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_future_done(fut: Future[SyncOutput]) -> None:
        # scheduler version may have changed
        mw.col._load_scheduler()
        timer.stop()
        try:
            out = fut.result()
        except Exception as err:
            handle_sync_error(mw, err)
            return on_done()

        mw.pm.set_host_number(out.host_number)
        if out.new_endpoint:
            mw.pm.set_current_sync_url(out.new_endpoint)
        if out.server_message:
            showText(out.server_message)
        if out.required == out.NO_CHANGES:
            tooltip(parent=mw, msg=tr.sync_collection_complete())
            # all done; track media progress
            mw.media_syncer.start_monitoring()
            return on_done()
        else:
            full_sync(mw, out, on_done)

    mw.taskman.with_progress(
        lambda: mw.col.sync_collection(auth, mw.pm.media_syncing_enabled()),
        on_future_done,
        label=tr.sync_checking(),
        immediate=True,
    )


def full_sync(
    mw: aqt.main.AnkiQt, out: SyncOutput, on_done: Callable[[], None]
) -> None:
    server_usn = out.server_media_usn if mw.pm.media_syncing_enabled() else None
    if out.required == out.FULL_DOWNLOAD:
        confirm_full_download(mw, server_usn, on_done)
    elif out.required == out.FULL_UPLOAD:
        confirm_full_upload(mw, server_usn, on_done)
    else:
        button_labels: list[str] = [
            tr.sync_upload_to_ankiweb(),
            tr.sync_download_from_ankiweb(),
            tr.sync_cancel_button(),
        ]

        def callback(choice: int) -> None:
            if choice == 0:
                full_upload(mw, server_usn, on_done)
            elif choice == 1:
                full_download(mw, server_usn, on_done)
            else:
                on_done()

        ask_user_dialog(
            tr.sync_conflict_explanation2(),
            callback=callback,
            buttons=button_labels,
            default_button=2,
            parent=mw,
            textFormat=Qt.TextFormat.MarkdownText,
        )


def confirm_full_download(
    mw: aqt.main.AnkiQt, server_usn: int, on_done: Callable[[], None]
) -> None:
    # confirmation step required, as some users customize their notetypes
    # in an empty collection, then want to upload them
    def callback(choice: int) -> None:
        if choice:
            on_done()
        else:
            mw.closeAllWindows(lambda: full_download(mw, server_usn, on_done))

    ask_user_dialog(
        tr.sync_confirm_empty_download(), callback=callback, default_button=0, parent=mw
    )


def confirm_full_upload(
    mw: aqt.main.AnkiQt, server_usn: int, on_done: Callable[[], None]
) -> None:
    # confirmation step required, as some users have reported an upload
    # happening despite having their AnkiWeb collection not being empty
    # (not reproducible - maybe a compiler bug?)
    def callback(choice: int) -> None:
        if choice:
            on_done()
        else:
            mw.closeAllWindows(lambda: full_upload(mw, server_usn, on_done))

    ask_user_dialog(
        tr.sync_confirm_empty_upload(), callback=callback, default_button=0, parent=mw
    )


def on_full_sync_timer(mw: aqt.main.AnkiQt, label: str) -> None:
    progress = mw.col.latest_progress()
    if not progress.HasField("full_sync"):
        return
    sync_progress = progress.full_sync

    if sync_progress.transferred == sync_progress.total:
        label = tr.sync_checking()
    mw.progress.update(
        value=sync_progress.transferred,
        max=sync_progress.total,
        process=False,
        label=label,
    )

    if mw.progress.want_cancel():
        mw.col.abort_sync()


def full_download(
    mw: aqt.main.AnkiQt, server_usn: int, on_done: Callable[[], None]
) -> None:
    label = tr.sync_downloading_from_ankiweb()

    def on_timer() -> None:
        on_full_sync_timer(mw, label)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    # hook needs to be called early, on the main thread
    gui_hooks.collection_will_temporarily_close(mw.col)

    def download() -> None:
        mw.create_backup_now()
        mw.col.close_for_full_sync()
        mw.col.full_upload_or_download(
            auth=mw.pm.sync_auth(), server_usn=server_usn, upload=False
        )

    def on_future_done(fut: Future) -> None:
        timer.stop()
        mw.reopen(after_full_sync=True)
        mw.reset()
        try:
            fut.result()
        except Exception as err:
            handle_sync_error(mw, err)
        mw.media_syncer.start_monitoring()
        return on_done()

    mw.taskman.with_progress(
        download,
        on_future_done,
    )


def full_upload(
    mw: aqt.main.AnkiQt, server_usn: int | None, on_done: Callable[[], None]
) -> None:
    gui_hooks.collection_will_temporarily_close(mw.col)
    mw.col.close_for_full_sync()

    label = tr.sync_uploading_to_ankiweb()

    def on_timer() -> None:
        on_full_sync_timer(mw, label)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(150)

    def on_future_done(fut: Future) -> None:
        timer.stop()
        mw.reopen(after_full_sync=True)
        mw.reset()
        try:
            fut.result()
        except Exception as err:
            handle_sync_error(mw, err)
            return on_done()
        mw.media_syncer.start_monitoring()
        return on_done()

    mw.taskman.with_progress(
        lambda: mw.col.full_upload_or_download(
            auth=mw.pm.sync_auth(), server_usn=server_usn, upload=True
        ),
        on_future_done,
    )


def sync_login(
    mw: aqt.main.AnkiQt,
    on_success: Callable[[], None],
    username: str = "",
    password: str = "",
) -> None:

    def on_future_done(fut: Future[SyncAuth], username: str, password: str) -> None:
        try:
            auth = fut.result()
        except SyncError as e:
            if e.kind is SyncErrorKind.AUTH:
                showWarning(str(e))
                sync_login(mw, on_success, username, password)
            else:
                handle_sync_error(mw, e)
            return
        except Exception as err:
            handle_sync_error(mw, err)
            return

        mw.pm.set_sync_key(auth.hkey)
        mw.pm.set_sync_username(username)

        on_success()

    def callback(username: str, password: str) -> None:
        if not username and not password:
            return
        if username and password:
            mw.taskman.with_progress(
                lambda: mw.col.sync_login(
                    username=username, password=password, endpoint=mw.pm.sync_endpoint()
                ),
                functools.partial(on_future_done, username=username, password=password),
                parent=mw,
            )
        else:
            sync_login(mw, on_success, username, password)

    get_id_and_pass_from_user(mw, callback, username, password)


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
    vbox = QVBoxLayout()
    info_label = QLabel(
        without_unicode_isolation(
            tr.sync_account_required(link="https://ankiweb.net/account/register")
        )
    )
    info_label.setOpenExternalLinks(True)
    info_label.setWordWrap(True)
    vbox.addWidget(info_label)
    vbox.addSpacing(20)
    g = QGridLayout()
    l1 = QLabel(tr.sync_ankiweb_id_label())
    g.addWidget(l1, 0, 0)
    user = QLineEdit()
    user.setText(username)
    g.addWidget(user, 0, 1)
    l1.setBuddy(user)
    l2 = QLabel(tr.sync_password_label())
    g.addWidget(l2, 1, 0)
    passwd = QLineEdit()
    passwd.setText(password)
    passwd.setEchoMode(QLineEdit.EchoMode.Password)
    g.addWidget(passwd, 1, 1)
    l2.setBuddy(passwd)
    vbox.addLayout(g)
    bb = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)  # type: ignore
    bb.button(QDialogButtonBox.StandardButton.Ok).setAutoDefault(True)
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


# export platform version to syncing code
os.environ["PLATFORM"] = plat_desc()
