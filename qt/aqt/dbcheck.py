# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
from anki.rsbackend import DatabaseCheckProgress, ProgressKind
from aqt.qt import *
from aqt.utils import showText, tooltip


def on_progress(mw: aqt.main.AnkiQt):
    progress = mw.col.latest_progress()
    if progress.kind != ProgressKind.DatabaseCheck:
        return

    assert isinstance(progress.val, DatabaseCheckProgress)
    mw.progress.update(
        process=False,
        label=progress.val.stage,
        value=progress.val.stage_current,
        max=progress.val.stage_total,
    )


def check_db(mw: aqt.AnkiQt) -> None:
    def on_timer():
        on_progress(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(100)

    def on_future_done(fut):
        timer.stop()
        ret, ok = fut.result()

        if not ok:
            showText(ret)
        else:
            tooltip(ret)

        # if an error has directed the user to check the database,
        # silently clean up any broken reset hooks which distract from
        # the underlying issue
        n = 0
        while n < 10:
            try:
                mw.reset()
                break
            except Exception as e:
                print("swallowed exception in reset hook:", e)
                n += 1
                continue

    mw.taskman.with_progress(mw.col.fixIntegrity, on_future_done)
