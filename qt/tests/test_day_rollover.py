# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable
from unittest.mock import MagicMock

import pytest

import aqt.main
from anki.collection import OpChanges
from aqt import gui_hooks
from aqt.main import AnkiQt

HOUR = 3600
DAY = 24 * HOUR


class FakeClock:
    def __init__(self, now: int) -> None:
        self.now = now


class FakeScheduler:
    """Days start at `rollover` o'clock (UTC), as "Next day starts at" does."""

    def __init__(self, clock: FakeClock, rollover: int) -> None:
        self.clock = clock
        self.rollover = rollover

    @property
    def today(self) -> int:
        return (self.clock.now - self.rollover * HOUR) // DAY

    @property
    def day_cutoff(self) -> int:
        return (self.today + 1) * DAY + self.rollover * HOUR


class FakeTimer:
    def __init__(self, due: int, func: Callable[[], None]) -> None:
        self.due = due
        self.func = func
        self.active = True

    def stop(self) -> None:
        self.active = False

    def deleteLater(self) -> None:
        self.active = False


class FakeProgress:
    def __init__(self, clock: FakeClock) -> None:
        self.clock = clock
        self.timers: list[FakeTimer] = []

    def timer(
        self,
        ms: int,
        func: Callable[[], None],
        repeat: bool,
        requiresCollection: bool = True,
        *,
        parent: object = None,
    ) -> FakeTimer:
        timer = FakeTimer(self.clock.now + ms // 1000, func)
        self.timers.append(timer)
        return timer

    def run_until(self, until: int) -> None:
        while due := [t for t in self.timers if t.active and t.due <= until]:
            timer = min(due, key=lambda t: t.due)
            timer.active = False
            self.clock.now = timer.due
            timer.func()
        self.clock.now = until


class FakeMainWindow:
    _refresh_reviewer_on_day_rollover_change = (
        AnkiQt._refresh_reviewer_on_day_rollover_change
    )
    on_operation_did_execute = AnkiQt.on_operation_did_execute

    def __init__(self, clock: FakeClock, rollover: int) -> None:
        self.col = MagicMock()
        self.col.sched = FakeScheduler(clock, rollover)
        self.state = "review"
        self.progress = FakeProgress(clock)
        self.reviewer = MagicMock()
        self.fade_out_webview = MagicMock()
        self._reviewer_refresh_timer: FakeTimer | None = None
        self._last_sched_today: int | None = None

    def open_profile(self) -> None:
        # as loadProfile() does
        self._last_sched_today = self.col.sched.today
        self._refresh_reviewer_on_day_rollover_change()

    def change_rollover(self, rollover: int) -> None:
        # as saving the preferences does
        self.col.sched.rollover = rollover
        self.on_operation_did_execute(OpChanges(config=True), None)

    def timer_delay(self) -> int:
        assert self._reviewer_refresh_timer
        return self._reviewer_refresh_timer.due - self.progress.clock.now


@pytest.fixture
def clock(monkeypatch: pytest.MonkeyPatch) -> FakeClock:
    clock = FakeClock(now=10 * DAY + 12 * HOUR + 55 * 60)
    monkeypatch.setattr(aqt.main, "int_time", lambda: clock.now)
    monkeypatch.setattr(aqt.main, "current_window", lambda: None)
    return clock


@pytest.fixture
def day_did_change(monkeypatch: pytest.MonkeyPatch) -> MagicMock:
    hook = MagicMock()
    monkeypatch.setattr(gui_hooks, "day_did_change", hook)
    return hook


def test_day_did_change_fires_when_day_changes_while_reviewing(
    clock: FakeClock, day_did_change: MagicMock
) -> None:
    mw = FakeMainWindow(clock, rollover=13)
    mw.open_profile()

    mw.progress.run_until(10 * DAY + 13 * HOUR)

    day_did_change.assert_called_once_with()
    mw.reviewer.refresh_if_needed.assert_called_once_with()


def test_day_rollover_follows_changed_next_day_starts_at(
    clock: FakeClock, day_did_change: MagicMock
) -> None:
    mw = FakeMainWindow(clock, rollover=4)
    mw.open_profile()
    mw.change_rollover(13)
    day_did_change.reset_mock()
    mw.reviewer.reset_mock()

    assert mw.timer_delay() == 5 * 60
    mw.progress.run_until(10 * DAY + 13 * HOUR)

    day_did_change.assert_called_once_with()
    mw.reviewer.refresh_if_needed.assert_called_once_with()


def test_next_day_starts_at_change_within_same_day_only_moves_timer(
    clock: FakeClock, day_did_change: MagicMock
) -> None:
    mw = FakeMainWindow(clock, rollover=4)
    mw.open_profile()

    mw.change_rollover(12)

    day_did_change.assert_not_called()
    mw.reviewer.refresh_if_needed.assert_not_called()
    assert mw.timer_delay() == 23 * HOUR + 5 * 60
