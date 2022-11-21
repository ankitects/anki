# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future

import aqt
import aqt.main
from aqt.qt import *
from aqt.utils import showText, tooltip


def on_progress(mw: aqt.main.AnkiQt) -> None:
    progress = mw.col.latest_progress()
    if not progress.HasField("database_check"):
        return
    dbprogress = progress.database_check
    mw.progress.update(
        process=False,
        label=dbprogress.stage,
        value=dbprogress.stage_current,
        max=dbprogress.stage_total,
    )


def check_db(mw: aqt.AnkiQt) -> None:
    def on_timer() -> None:
        on_progress(mw)

    timer = QTimer(mw)
    qconnect(timer.timeout, on_timer)
    timer.start(100)

    def do_check() -> tuple[str, bool]:
        mw.create_backup_now()
        return mw.col.fix_integrity()

    def on_future_done(fut: Future) -> None:
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

    mw.taskman.with_progress(do_check, on_future_done)
