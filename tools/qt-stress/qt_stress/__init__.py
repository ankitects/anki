# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Qt/QtWebEngine UI latency stress test (issue #5661 / PR #5663).

Times the user-facing operations that were reported as laggy (startup, deck
list, reviewer card transitions, browser row switching, previewer, dialogs,
window resizing) plus raw webview frame pacing and main-thread stalls.

Every timed operation ends with a probe that waits for the page's pending
async work, then two requestAnimationFrame callbacks, then a pycmd back to
Python. So the numbers include Python work, IPC, JS rendering and the
compositor delivering a frame, which is roughly what the user perceives.

Two modes:
- Automated: when QT_STRESS_OUT is set (by bench.py), the suite runs after
  the profile opens, writes JSON to that path and quits Anki.
- Manual: Tools > Qt Stress Test runs the suite on the open collection and
  saves/shows the results. It is read-only: cards are shown but never
  answered, and notes are never edited.
"""

from __future__ import annotations

import json
import os
import platform
import struct
import sys
import time
import traceback
from collections.abc import Callable, Generator
from typing import Any

import aqt
from aqt import gui_hooks, mw
from aqt.qt import (
    QAction,
    QApplication,
    QDialog,
    QPlainTextEdit,
    QPushButton,
    Qt,
    QTimer,
    QVBoxLayout,
    qconnect,
)

OUT_PATH = os.environ.get("QT_STRESS_OUT")
ITERS = int(os.environ.get("QT_STRESS_ITERS", "40"))
WARMUP = int(os.environ.get("QT_STRESS_WARMUP", "5"))
FRAME_TEST_MS = int(os.environ.get("QT_STRESS_FRAME_MS", "4000"))
WAIT_TIMEOUT_MS = 30_000
STALL_TICK_MS = 10
STALL_THRESHOLD_MS = 50
WINDOW_SIZE = (1200, 820)


def _process_start_time() -> float | None:
    """Wall-clock time this process was started, so startup includes Python
    and Qt initialisation that happens before add-ons load."""
    try:
        if sys.platform == "darwin":
            import ctypes

            libc = ctypes.CDLL(None)
            # CTL_KERN, KERN_PROC, KERN_PROC_PID, pid
            mib = (ctypes.c_int * 4)(1, 14, 1, os.getpid())
            size = ctypes.c_size_t(0)
            libc.sysctl(mib, 4, None, ctypes.byref(size), None, 0)
            buf = ctypes.create_string_buffer(size.value)
            if libc.sysctl(mib, 4, buf, ctypes.byref(size), None, 0) != 0:
                return None
            # kinfo_proc.kp_proc.p_starttime is a timeval at offset 0
            sec, usec = struct.unpack_from("qi", buf.raw, 0)
            return sec + usec / 1e6
        if sys.platform.startswith("linux"):
            with open("/proc/self/stat") as f:
                fields = f.read().rsplit(")", 1)[1].split()
            ticks = int(fields[19])
            with open("/proc/stat") as f:
                btime = next(int(l.split()[1]) for l in f if l.startswith("btime"))
            return btime + ticks / os.sysconf("SC_CLK_TCK")
    except Exception:
        traceback.print_exc()
    return None


PROCESS_START = _process_start_time()

# Waitables
##########################################################################


class Timeout(Exception):
    pass


class Waitable:
    timeout_ms = WAIT_TIMEOUT_MS

    def start(self, resume: Callable[[Any], None]) -> None:
        raise NotImplementedError

    def cancel(self) -> None:
        pass

    def describe(self) -> str:
        return type(self).__name__


class Sleep(Waitable):
    def __init__(self, ms: int) -> None:
        self.ms = ms

    def start(self, resume: Callable[[Any], None]) -> None:
        QTimer.singleShot(self.ms, lambda: resume(None))


class Until(Waitable):
    """Polls a predicate. Only used for untimed bookkeeping steps."""

    def __init__(self, predicate: Callable[[], bool]) -> None:
        self.predicate = predicate
        self.timer: QTimer | None = None

    def start(self, resume: Callable[[Any], None]) -> None:
        def check() -> None:
            if self.predicate():
                self.cancel()
                resume(None)

        self.timer = QTimer()
        qconnect(self.timer.timeout, check)
        self.timer.start(10)

    def cancel(self) -> None:
        if self.timer:
            self.timer.stop()
            self.timer = None


def _dialog_closed(name: str) -> Callable[[], bool]:
    return lambda: aqt.dialogs._dialogs[name][1] is None


_pending_tokens: dict[str, Callable[[str], None]] = {}
_token_counter = 0


def _new_token(cb: Callable[[str], None]) -> str:
    global _token_counter
    _token_counter += 1
    token = str(_token_counter)
    _pending_tokens[token] = cb
    return token


def _on_js_message(handled: tuple[bool, Any], message: str, context: Any) -> Any:
    if not message.startswith("qtstress:"):
        return handled
    token, _, payload = message[len("qtstress:") :].partition(":")
    if cb := _pending_tokens.pop(token, None):
        cb(payload)
    return (True, None)


gui_hooks.webview_did_receive_js_message.append(_on_js_message)

PROBE_JS = """
(async () => {
    try { await (%(wait)s); } catch (e) {}
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    (window.pycmd || window.bridgeCommand)("qtstress:%(token)s:");
})();
"""

# waits for the reviewer/previewer's queued _showQuestion/_showAnswer work
REVIEWER_WAIT = "new Promise((r) => _queueAction(r))"
EDITOR_WAIT = 'require("anki/ui").loaded'


class Probe(Waitable):
    """Resolves once every given webview has finished its pending work and
    painted two more frames."""

    def __init__(self, *webs: Any, wait: str = "null") -> None:
        self.webs = webs
        self.wait = wait
        self.tokens: list[str] = []

    def start(self, resume: Callable[[Any], None]) -> None:
        self.waiting = {id(web): web for web in self.webs}

        def make_cb(web: Any) -> Callable[[str], None]:
            def on_done(_payload: str) -> None:
                self.waiting.pop(id(web), None)
                if not self.waiting:
                    resume(None)

            return on_done

        for web in self.webs:
            token = _new_token(make_cb(web))
            self.tokens.append(token)
            web.eval(PROBE_JS % dict(wait=self.wait, token=token))

    def describe(self) -> str:
        names = [
            f"{type(w).__name__}(kind={getattr(w, 'kind', '?')})"
            for w in self.waiting.values()
        ]
        return f"Probe waiting on {', '.join(names)}"

    def cancel(self) -> None:
        for token in self.tokens:
            _pending_tokens.pop(token, None)


FRAME_JS = """
(async () => {
    const filler = document.createElement("div");
    filler.style.cssText = "position:absolute;left:0;top:0;width:10px;height:20000px;";
    const spinner = document.createElement("div");
    spinner.style.cssText = "position:fixed;right:20px;bottom:20px;width:60px;height:60px;"
        + "background:linear-gradient(45deg,#c33,#33c);z-index:99999;";
    spinner.animate([{ transform: "rotate(0deg)" }, { transform: "rotate(360deg)" }],
        { duration: 1000, iterations: Infinity });
    if (%(scroll)s) document.body.appendChild(filler);
    document.body.appendChild(spinner);
    const intervals = [];
    const start = performance.now();
    let last = start;
    await new Promise((resolve) => {
        function frame(t) {
            intervals.push(t - last);
            last = t;
            if (t - start < %(duration)d) {
                if (%(scroll)s) window.scrollBy(0, 4);
                requestAnimationFrame(frame);
            } else {
                resolve();
            }
        }
        requestAnimationFrame(frame);
    });
    spinner.remove();
    filler.remove();
    window.scrollTo(0, 0);
    (window.pycmd || window.bridgeCommand)("qtstress:%(token)s:" + JSON.stringify(intervals.slice(1)));
})();
"""


class FrameTrace(Waitable):
    """Collects requestAnimationFrame intervals while animating (and
    optionally scrolling) the page."""

    timeout_ms = WAIT_TIMEOUT_MS + FRAME_TEST_MS

    def __init__(self, web: Any, scroll: bool) -> None:
        self.web = web
        self.scroll = scroll
        self.token = ""

    def start(self, resume: Callable[[Any], None]) -> None:
        self.token = _new_token(lambda payload: resume(json.loads(payload)))
        self.web.eval(
            FRAME_JS
            % dict(
                scroll="true" if self.scroll else "false",
                duration=FRAME_TEST_MS,
                token=self.token,
            )
        )

    def cancel(self) -> None:
        _pending_tokens.pop(self.token, None)


class Act(Waitable):
    """Runs an action, then waits until each of the given hooks has fired."""

    def __init__(self, action: Callable[[], Any], *hooks: Any) -> None:
        self.action = action
        self.hooks = hooks
        self.callbacks: list[tuple[Any, Callable]] = []

    def start(self, resume: Callable[[Any], None]) -> None:
        remaining = len(self.hooks)
        result: Any = None

        def make_cb(hook: Any) -> Callable:
            def cb(*args: Any) -> None:
                nonlocal remaining
                hook.remove(cb)
                remaining -= 1
                if remaining == 0:
                    QTimer.singleShot(0, lambda: resume(result))

            return cb

        for hook in self.hooks:
            cb = make_cb(hook)
            self.callbacks.append((hook, cb))
            hook.append(cb)
        result = self.action()
        if not self.hooks:
            QTimer.singleShot(0, lambda: resume(result))

    def cancel(self) -> None:
        for hook, cb in self.callbacks:
            try:
                hook.remove(cb)
            except ValueError:
                pass


Task = Generator[Waitable, Any, Any]


class Driver:
    """Steps a generator, resuming it whenever the yielded waitable resolves."""

    def __init__(self, gen: Task, on_done: Callable[[], None]) -> None:
        self.gen = gen
        self.on_done = on_done
        self.current: Waitable | None = None
        self.timer: QTimer | None = None
        self.generation = 0

    def start(self) -> None:
        self._advance(lambda: self.gen.send(None))

    def _advance(self, step: Callable[[], Waitable]) -> None:
        try:
            waitable = step()
        except StopIteration:
            self.on_done()
            return
        except Exception:
            traceback.print_exc()
            self.on_done()
            return
        self.generation += 1
        generation = self.generation
        self.current = waitable

        def resume(value: Any) -> None:
            if generation != self.generation:
                return
            self._finish_wait()
            self._advance(lambda: self.gen.send(value))

        def timeout() -> None:
            if generation != self.generation:
                return
            self._finish_wait()
            self._advance(
                lambda: self.gen.throw(Timeout(f"{waitable.describe()} timed out"))
            )

        self.timer = QTimer()
        self.timer.setSingleShot(True)
        qconnect(self.timer.timeout, timeout)
        self.timer.start(waitable.timeout_ms)
        waitable.start(resume)

    def _finish_wait(self) -> None:
        self.generation += 1
        if self.timer:
            self.timer.stop()
            self.timer = None
        if self.current:
            self.current.cancel()
            self.current = None


# Main-thread stall monitor
##########################################################################


class StallMonitor:
    """A 10ms timer on the GUI thread; late ticks mean the event loop was
    blocked (beachball territory when large)."""

    def __init__(self) -> None:
        self.phase = "startup"
        self.stalls: dict[str, list[float]] = {}
        self.last = time.perf_counter()
        self.timer = QTimer()
        self.timer.setTimerType(Qt.TimerType.PreciseTimer)
        qconnect(self.timer.timeout, self._tick)

    def start(self) -> None:
        self.last = time.perf_counter()
        self.timer.start(STALL_TICK_MS)

    def stop(self) -> None:
        self.timer.stop()

    def set_phase(self, phase: str) -> None:
        self._tick()
        self.phase = phase

    def _tick(self) -> None:
        now = time.perf_counter()
        gap = (now - self.last) * 1000
        self.last = now
        if gap > STALL_THRESHOLD_MS:
            self.stalls.setdefault(self.phase, []).append(gap)


# The suite
##########################################################################


class Suite:
    def __init__(self, automated: bool) -> None:
        self.automated = automated
        self.results: dict[str, Any] = {}
        self.samples: dict[str, list[float]] = {}
        self.errors: dict[str, str] = {}
        self.monitor = StallMonitor()

    # helpers

    def record(self, name: str, ms: float) -> None:
        self.samples.setdefault(name, []).append(ms)

    def timed(self, name: str, *waits: Waitable) -> Task:
        start = time.perf_counter()
        for w in waits:
            yield w
        self.record(name, (time.perf_counter() - start) * 1000)

    def repeat(self, name: str, fn: Callable[[int], Task], iters: int = ITERS) -> Task:
        """Runs fn(i) WARMUP + iters times; the first WARMUP are discarded."""
        for i in range(WARMUP + iters):
            start = time.perf_counter()
            yield from fn(i)
            if i >= WARMUP:
                self.record(name, (time.perf_counter() - start) * 1000)

    def phase(self, name: str, gen_fn: Callable[[], Task]) -> Task:
        self.monitor.set_phase(name)
        print(f"qt-stress: {name}")
        try:
            yield from gen_fn()
        except Exception as e:
            traceback.print_exc()
            self.errors[name] = f"{type(e).__name__}: {e}"
        # let any trailing work settle so it isn't attributed to the next phase
        yield Sleep(300)

    # phases

    def run(self) -> Task:
        self.monitor.start()
        mw.showNormal()
        mw.resize(*WINDOW_SIZE)
        mw.raise_()
        mw.activateWindow()
        if mw.state != "deckBrowser":
            yield Act(
                lambda: mw.moveToState("deckBrowser"), gui_hooks.deck_browser_did_render
            )
        yield Probe(mw.web)
        yield Sleep(1000)

        yield from self.phase("frames_idle", lambda: self.frames(scroll=False))
        yield from self.phase("frames_scroll", lambda: self.frames(scroll=True))
        yield from self.phase("js_roundtrip", self.js_roundtrip)
        yield from self.phase("deck_browser_refresh", self.deck_browser)
        yield from self.phase("overview_refresh", self.overview)
        yield from self.phase("reviewer", self.reviewer)
        yield from self.phase("resize", self.resize)
        yield from self.phase("browser", self.browser)
        yield from self.phase("add_dialog_open", self.add_dialog)
        self.monitor.set_phase("done")
        self.monitor.stop()

    def frames(self, scroll: bool) -> Task:
        intervals = yield FrameTrace(mw.web, scroll)
        self.samples["frames_scroll" if scroll else "frames_idle"] = intervals

    def js_roundtrip(self) -> Task:
        def step(_i: int) -> Task:
            yield Probe(mw.web)

        yield from self.repeat("js_roundtrip", step)

    def deck_browser(self) -> Task:
        def step(_i: int) -> Task:
            yield Act(mw.deckBrowser.refresh, gui_hooks.deck_browser_did_render)
            yield Probe(mw.web)

        yield from self.repeat("deck_browser_refresh", step)

    def overview(self) -> Task:
        yield Act(lambda: mw.moveToState("overview"), gui_hooks.overview_did_refresh)
        yield Probe(mw.web, mw.bottomWeb)

        def step(_i: int) -> Task:
            yield Act(mw.overview.refresh, gui_hooks.overview_did_refresh)
            yield Probe(mw.web, mw.bottomWeb)

        yield from self.repeat("overview_refresh", step)

    def reviewer(self) -> Task:
        from anki.cards import Card, CardId

        queued = mw.col.sched.get_queued_cards()  # type: ignore[union-attr]
        if not queued.cards:
            self.errors["reviewer"] = "skipped: no cards due in the current deck"
            return
        did = mw.col.decks.current()["id"]
        cids = sorted(mw.col.find_cards(f"did:{did}"))[: WARMUP + ITERS] or [
            CardId(queued.cards[0].card.id)
        ]

        start = time.perf_counter()
        yield Act(lambda: mw.moveToState("review"))
        yield Probe(mw.web, mw.bottomWeb, wait=REVIEWER_WAIT)
        self.record("reviewer_enter", (time.perf_counter() - start) * 1000)

        rev = mw.reviewer

        def show_question(i: int) -> None:
            # Swap in a different card without answering the current one, so
            # the collection isn't modified. Bottom bar states stay those of
            # the queued card, which doesn't matter for rendering.
            rev.card = Card(mw.col, cids[i % len(cids)])
            rev._showQuestion()

        def step(i: int) -> Task:
            q_start = time.perf_counter()
            yield Act(lambda: show_question(i))
            yield Probe(mw.web, mw.bottomWeb, wait=REVIEWER_WAIT)
            if i >= WARMUP:
                self.record(
                    "reviewer_show_question", (time.perf_counter() - q_start) * 1000
                )
            a_start = time.perf_counter()
            yield Act(rev._showAnswer)
            yield Probe(mw.web, mw.bottomWeb, wait=REVIEWER_WAIT)
            if i >= WARMUP:
                self.record(
                    "reviewer_show_answer", (time.perf_counter() - a_start) * 1000
                )

        for i in range(WARMUP + ITERS):
            yield from step(i)

        yield Act(
            lambda: mw.moveToState("deckBrowser"), gui_hooks.deck_browser_did_render
        )
        yield Probe(mw.web)

    def resize(self) -> Task:
        w, h = WINDOW_SIZE
        sizes = [(w, h), (w - 300, h - 200)]

        def step(i: int) -> Task:
            yield Act(lambda: mw.resize(*sizes[(i + 1) % 2]))
            yield Probe(mw.web, mw.toolbarWeb, mw.bottomWeb)

        yield from self.repeat("resize", step)
        mw.resize(*WINDOW_SIZE)
        yield Probe(mw.web)

    def browser(self) -> Task:
        def open_browser() -> Any:
            return aqt.dialogs.open("Browser", mw, search=("deck:current",))

        def close_browser(b: Any) -> Task:
            yield Act(b.close)
            yield Until(_dialog_closed("Browser"))
            yield Sleep(200)

        # open/close cycles, measured until the editor has rendered
        open_iters = max(3, ITERS // 8)
        for i in range(WARMUP // 2 + open_iters):
            start = time.perf_counter()
            b = yield Act(open_browser)
            if b.table.len_selection() != 1:
                yield Act(b.table.to_first_row, gui_hooks.editor_did_load_note)
            yield Probe(b.editor.web, wait=EDITOR_WAIT)
            if i >= WARMUP // 2:
                self.record("browser_open", (time.perf_counter() - start) * 1000)
            if i < WARMUP // 2 + open_iters - 1:
                yield from close_browser(b)

        n_rows = b.table.len()
        if n_rows < 2:
            raise Exception("browser needs at least 2 cards in the current deck")

        def next_row() -> None:
            if b.table._current().row() >= n_rows - 1:
                b.table.to_first_row()
            else:
                b.table.to_next_row()

        def row_step(_i: int) -> Task:
            yield Act(next_row, gui_hooks.editor_did_load_note)
            yield Probe(b.editor.web, wait=EDITOR_WAIT)

        yield from self.repeat("browser_switch_row", row_step)

        # previewer open + row switching with the previewer showing
        start = time.perf_counter()
        yield Act(b.onTogglePreview)
        previewer = b._previewer
        yield Probe(previewer._web, wait=REVIEWER_WAIT)
        self.record("previewer_open", (time.perf_counter() - start) * 1000)

        def preview_step(_i: int) -> Task:
            # bypass the previewer's 300ms render throttle
            previewer._last_render = 0
            yield Act(next_row, gui_hooks.editor_did_load_note)
            yield Probe(b.editor.web, wait=EDITOR_WAIT)
            yield Probe(previewer._web, wait=REVIEWER_WAIT)

        yield from self.repeat("browser_switch_row_with_preview", preview_step)
        yield Act(b.onTogglePreview)
        yield from close_browser(b)

    def add_dialog(self) -> Task:
        def current() -> Any:
            dialogs = aqt.dialogs._dialogs
            return dialogs["AddCards"][1] or dialogs["NewAddCards"][1]

        iters = max(3, ITERS // 8)
        for i in range(WARMUP // 2 + iters):
            start = time.perf_counter()
            # same entry point as the Add button, legacy or new editor
            yield Act(mw.onAddCard)
            dialog = current()
            yield Probe(dialog.editor.web, wait=EDITOR_WAIT)
            if i >= WARMUP // 2:
                self.record("add_dialog_open", (time.perf_counter() - start) * 1000)
            yield Act(dialog.close)
            yield Until(lambda: current() is None)
            yield Sleep(200)

    # output

    def metadata(self) -> dict[str, Any]:
        from PyQt6.QtCore import qVersion
        from PyQt6.QtWebEngineCore import (
            qWebEngineChromiumVersion,
            qWebEngineVersion,
        )

        from anki.buildinfo import version as anki_version

        screen = mw.screen()
        meta: dict[str, Any] = dict(
            anki=anki_version,
            qt=qVersion(),
            qtwebengine=qWebEngineVersion(),
            chromium=qWebEngineChromiumVersion(),
            python=platform.python_version(),
            platform=platform.platform(),
            machine=platform.machine(),
            mac_ver=platform.mac_ver()[0],
            video_driver=str(mw.pm.video_driver()),
            screen_refresh_hz=screen.refreshRate() if screen else None,
            device_pixel_ratio=screen.devicePixelRatio() if screen else None,
            iters=ITERS,
            warmup=WARMUP,
            frame_test_ms=FRAME_TEST_MS,
            card_count=mw.col.card_count(),
            automated=self.automated,
            timestamp=time.time(),
        )
        return meta

    def to_json(self) -> dict[str, Any]:
        return dict(
            meta=self.metadata(),
            samples=self.samples,
            stalls=self.monitor.stalls,
            errors=self.errors,
        )


# Summary formatting shared with bench.py's style
##########################################################################


def _pct(values: list[float], p: float) -> float:
    s = sorted(values)
    k = (len(s) - 1) * p
    lo = int(k)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def summarize(data: dict[str, Any]) -> str:
    meta = data["meta"]
    lines = [
        f"Anki {meta['anki']}  Qt {meta['qt']}  QtWebEngine {meta['qtwebengine']}"
        f"  Chromium {meta['chromium']}",
        f"{meta['platform']}  macOS {meta['mac_ver'] or '-'}  driver {meta['video_driver']}"
        f"  screen {meta['screen_refresh_hz']}Hz@{meta['device_pixel_ratio']}x"
        f"  cards {meta['card_count']}",
        "",
        f"{'operation':<34}{'n':>5}{'median':>10}{'p90':>10}{'max':>10}  (ms)",
    ]
    for name, values in data["samples"].items():
        if not values:
            continue
        if name.startswith("frames_"):
            continue
        lines.append(
            f"{name:<34}{len(values):>5}{_pct(values, 0.5):>10.1f}"
            f"{_pct(values, 0.9):>10.1f}{max(values):>10.1f}"
        )
    for name in ("frames_idle", "frames_scroll"):
        values = data["samples"].get(name)
        if values:
            med = _pct(values, 0.5)
            janky = sum(1 for v in values if v > med * 1.5) / len(values) * 100
            fps = len(values) / (sum(values) / 1000)
            lines.append(
                f"{name:<34}{len(values):>5}  fps {fps:6.1f}  frame p99 "
                f"{_pct(values, 0.99):6.1f}ms  janky {janky:5.1f}%"
            )
    lines.append("")
    lines.append("main-thread stalls (>50ms): phase: count / worst ms / total ms")
    for phase, stalls in data["stalls"].items():
        lines.append(
            f"  {phase}: {len(stalls)} / {max(stalls):.0f} / {sum(stalls):.0f}"
        )
    for name, err in data["errors"].items():
        lines.append(f"error in {name}: {err}")
    return "\n".join(lines)


# Entry points
##########################################################################

_driver: Driver | None = None


def _run_suite(automated: bool, on_finished: Callable[[Suite], None]) -> None:
    global _driver
    suite = Suite(automated)

    def done() -> None:
        global _driver
        _driver = None
        on_finished(suite)

    _driver = Driver(suite.run(), done)
    _driver.start()


def _measure_startup() -> Task:
    """Time from process start until the deck list is painted. The deck
    browser may render several times during startup and each setHtml()
    discards queued evals, so a fresh probe is queued after every render and
    the first one to report back wins."""
    probes: list[Probe] = []
    result: dict[str, Any] = {}

    class FirstPaint(Waitable):
        timeout_ms = 120_000

        def start(self, resume: Callable[[Any], None]) -> None:
            self.resume = resume
            gui_hooks.deck_browser_did_render.append(self.on_render)
            if mw.state == "deckBrowser":
                self.on_render()

        def on_render(self, *_args: Any) -> None:
            probe = Probe(mw.web)
            probes.append(probe)
            probe.start(self.resume)

        def cancel(self) -> None:
            gui_hooks.deck_browser_did_render.remove(self.on_render)
            for p in probes:
                p.cancel()

    yield FirstPaint()
    result["painted"] = time.time()
    return result


def _start_automated() -> None:
    samples: dict[str, list[float]] = {}

    def startup_task() -> Task:
        r = yield from _measure_startup()
        if PROCESS_START:
            samples["startup_to_deck_list"] = [(r["painted"] - PROCESS_START) * 1000]

    def after_startup() -> None:
        QTimer.singleShot(2000, lambda: _run_suite(True, finished))

    def finished(suite: Suite) -> None:
        data = suite.to_json()
        data["samples"] = samples | data["samples"]
        assert OUT_PATH
        with open(OUT_PATH, "w", encoding="utf8") as f:
            json.dump(data, f, indent=1)
        print(summarize(data))
        mw.unloadProfileAndExit()

    Driver(startup_task(), after_startup).start()


_started = False


def _on_profile_did_open() -> None:
    global _started
    if OUT_PATH and not _started:
        _started = True
        _start_automated()


def _on_menu() -> None:
    if _driver:
        return
    if not aqt.utils.askUser(
        "This will drive the Anki window for a few minutes, opening the "
        "browser, reviewer and other screens. Your collection is not "
        "modified.\n\nPlease don't use the computer or cover the Anki window "
        "until it finishes.\n\nStart?"
    ):
        return

    def finished(suite: Suite) -> None:
        data = suite.to_json()
        user_files = os.path.join(os.path.dirname(__file__), "user_files")
        os.makedirs(user_files, exist_ok=True)
        path = os.path.join(user_files, time.strftime("qt-stress-%Y%m%d-%H%M%S.json"))
        with open(path, "w", encoding="utf8") as f:
            json.dump(data, f, indent=1)
        _show_report(summarize(data), path)

    _run_suite(False, finished)


def _show_report(text: str, path: str) -> None:
    dialog = QDialog(mw)
    dialog.setWindowTitle("Qt Stress Test Results")
    dialog.resize(820, 560)
    layout = QVBoxLayout(dialog)
    edit = QPlainTextEdit(text + f"\n\nFull results: {path}")
    edit.setReadOnly(True)
    font = edit.font()
    font.setFamily("Menlo" if sys.platform == "darwin" else "monospace")
    edit.setFont(font)
    layout.addWidget(edit)
    copy = QPushButton("Copy to Clipboard")
    qconnect(copy.clicked, lambda: QApplication.clipboard().setText(edit.toPlainText()))
    layout.addWidget(copy)
    dialog.show()


gui_hooks.profile_did_open.append(_on_profile_did_open)

if not OUT_PATH:
    _action = QAction("Qt Stress Test", mw)
    qconnect(_action.triggered, _on_menu)
    mw.form.menuTools.addAction(_action)
