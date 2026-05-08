# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Manual QtWebEngine smoke test for untrusted media CSP handling.

This is intentionally not named test_*.py, as QtWebEngine tests are sensitive to
the host GUI environment. Run it manually with:

    tools/qwebengine-csp-smoke
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
from dataclasses import dataclass, field
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("QTWEBENGINE_DISABLE_SANDBOX", "1")

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [
    str(ROOT / "qt"),
    str(ROOT / "pylib"),
    str(ROOT / "out" / "pylib"),
    str(ROOT / "out" / "qt"),
]

from PyQt6 import sip
from PyQt6.QtCore import QEventLoop, QTimer, QUrl
from PyQt6.QtWebEngineCore import (
    QWebEnginePage,
    QWebEngineProfile,
    QWebEngineUrlRequestInfo,
    QWebEngineUrlRequestInterceptor,
)
from PyQt6.QtWidgets import QApplication

from aqt.mediasrv import UNTRUSTED_MEDIA_CSP, _editor_content_security_policy

AUTH_TOKEN = "qwebengine-csp-smoke-token"


@dataclass
class SmokeState:
    events: list[dict[str, Any]] = field(default_factory=list)
    api_hits: list[dict[str, Any]] = field(default_factory=list)
    script_hits: list[str] = field(default_factory=list)
    media_requests: list[str] = field(default_factory=list)
    remote_frame_requested: bool = False
    done: bool = False
    lock: threading.Lock = field(default_factory=threading.Lock)

    def record_event(self, event: dict[str, Any]) -> None:
        with self.lock:
            self.events.append(event)
            if event.get("type") == "done":
                self.done = True

    def record_api_hit(self, headers: dict[str, str]) -> None:
        with self.lock:
            self.api_hits.append(headers)

    def record_script_hit(self, query: str) -> None:
        with self.lock:
            self.script_hits.append(query)

    def record_media_request(self, path: str) -> None:
        with self.lock:
            self.media_requests.append(path)

    def record_remote_frame_request(self) -> None:
        with self.lock:
            self.remote_frame_requested = True

    def snapshot(self) -> "SmokeSnapshot":
        with self.lock:
            return SmokeSnapshot(
                events=list(self.events),
                api_hits=list(self.api_hits),
                script_hits=list(self.script_hits),
                media_requests=list(self.media_requests),
                remote_frame_requested=self.remote_frame_requested,
                done=self.done,
            )


@dataclass
class SmokeSnapshot:
    events: list[dict[str, Any]]
    api_hits: list[dict[str, Any]]
    script_hits: list[str]
    media_requests: list[str]
    remote_frame_requested: bool
    done: bool


class ApiAuthInterceptor(QWebEngineUrlRequestInterceptor):
    """Mirror Anki's editor profile API access for local requests."""

    def interceptRequest(self, info: QWebEngineUrlRequestInfo) -> None:
        if info.requestUrl().host() == "127.0.0.1":
            info.setHttpHeader(b"Authorization", f"Bearer {AUTH_TOKEN}".encode())


class SmokePage(QWebEnginePage):
    def __init__(self, profile: QWebEngineProfile) -> None:
        super().__init__(profile)
        self.console_messages: list[str] = []

    def javaScriptConsoleMessage(
        self,
        level: QWebEnginePage.JavaScriptConsoleMessageLevel,
        message: str,
        line_number: int,
        source_id: str,
    ) -> None:
        self.console_messages.append(
            f"{source_id}:{line_number}: {level.name}: {message}"
        )


class SmokeServer(ThreadingHTTPServer):
    def __init__(
        self,
        state: SmokeState,
        remote_port: int | None,
        handler: type[BaseHTTPRequestHandler],
    ) -> None:
        super().__init__(("127.0.0.1", 0), handler)
        self.state = state
        self.remote_port = remote_port


class MainRequestHandler(BaseHTTPRequestHandler):
    server: SmokeServer

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/editor":
            self._send_editor_page()
        elif parsed.path == "/_anki/smoke.js":
            self._send_js()
        elif parsed.path == "/media/malicious.html":
            self.server.state.record_media_request(parsed.path)
            self._send_untrusted_media(
                b"""<!doctype html>
<script>
new Image().src = '/__script-ran?doc=html';
fetch('/_anki/getImageForOcclusion', {
    method: 'POST',
    headers: {'Content-Type': 'application/binary'},
    body: new Uint8Array([1, 2, 3]),
}).catch(() => {});
try {
    parent.fetch('/_anki/getImageForOcclusion', {
        method: 'POST',
        headers: {'Content-Type': 'application/binary'},
        body: new Uint8Array([10, 11, 12]),
    }).catch(() => {});
} catch (error) {}
</script>
<p>malicious html</p>
""",
                "text/html",
            )
        elif parsed.path == "/media/malicious.svg":
            self.server.state.record_media_request(parsed.path)
            self._send_untrusted_media(
                b"""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40"
    onload="fetch('/_anki/getImageForOcclusion', {method: 'POST', headers: {'Content-Type': 'application/binary'}, body: new Uint8Array([4, 5, 6])})">
  <script><![CDATA[
  new Image().src = '/__script-ran?doc=svg';
  fetch('/_anki/getImageForOcclusion', {
      method: 'POST',
      headers: {'Content-Type': 'application/binary'},
      body: new Uint8Array([7, 8, 9]),
  }).catch(() => {});
  try {
      parent.fetch('/_anki/getImageForOcclusion', {
          method: 'POST',
          headers: {'Content-Type': 'application/binary'},
          body: new Uint8Array([13, 14, 15]),
      }).catch(() => {});
  } catch (error) {}
  ]]></script>
  <rect width="80" height="40" fill="#2f7dd1"/>
</svg>
""",
                "image/svg+xml",
            )
        elif parsed.path == "/media/benign.svg":
            self.server.state.record_media_request(f"{parsed.path}?{parsed.query}")
            self._send_untrusted_media(
                b"""<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40">
  <rect width="80" height="40" fill="#2f7dd1"/>
</svg>
""",
                "image/svg+xml",
            )
        elif parsed.path == "/__script-ran":
            self.server.state.record_script_hit(parsed.query)
            self._send_bytes(b"", "text/plain")
        else:
            self.send_error(HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0") or "0")
        body = self.rfile.read(length)

        if parsed.path == "/__events":
            event = json.loads(body.decode())
            self.server.state.record_event(event)
            self._send_bytes(b"{}", "application/json")
        elif parsed.path == "/_anki/getImageForOcclusion":
            self.server.state.record_api_hit(dict(self.headers))
            self._send_bytes(b"{}", "application/json")
        else:
            self.send_error(HTTPStatus.NOT_FOUND)

    def log_message(self, format: str, *args: object) -> None:
        pass

    def _send_editor_page(self) -> None:
        assert self.server.remote_port is not None
        html = f"""<!doctype html>
<meta charset="utf-8">
<body>
  <script src="/_anki/smoke.js?remote_port={self.server.remote_port}"></script>
</body>
"""
        self._send_bytes(
            html.encode(),
            "text/html",
            {
                "Content-Security-Policy": _editor_content_security_policy(
                    self.server.server_port
                )
            },
        )

    def _send_js(self) -> None:
        remote_port = parse_qs(urlparse(self.path).query)["remote_port"][0]
        js = f"""
const remotePort = {remote_port};
const results = {{}};

function record(event) {{
    fetch('/__events', {{
        method: 'POST',
        headers: {{'Content-Type': 'application/json'}},
        body: JSON.stringify(event),
    }}).catch(() => {{}});
}}

function addElement(kind, attrs) {{
    const element = document.createElement(kind);
    for (const [key, value] of Object.entries(attrs)) {{
        element.setAttribute(key, value);
    }}
    element.style.width = '80px';
    element.style.height = '40px';
    element.addEventListener('load', () => {{
        results[element.id] = 'load';
        record({{type: 'load', id: element.id}});
    }});
    element.addEventListener('error', () => {{
        results[element.id] = 'error';
        record({{type: 'error', id: element.id}});
    }});
    document.body.appendChild(element);
}}

addElement('iframe', {{
    id: 'malicious-html-iframe',
    src: '/media/malicious.html',
}});
addElement('object', {{
    id: 'malicious-svg-object',
    data: '/media/malicious.svg',
    type: 'image/svg+xml',
}});
addElement('img', {{
    id: 'benign-svg-img',
    src: '/media/benign.svg?via=img',
}});
addElement('object', {{
    id: 'benign-svg-object',
    data: '/media/benign.svg?via=object',
    type: 'image/svg+xml',
}});
addElement('iframe', {{
    id: 'remote-iframe',
    src: `http://127.0.0.1:${{remotePort}}/remote-frame`,
}});

setTimeout(() => {{
    const img = document.getElementById('benign-svg-img');
    record({{
        type: 'done',
        results,
        imgComplete: img.complete,
        imgNaturalWidth: img.naturalWidth,
    }});
}}, 1500);
"""
        self._send_bytes(js.encode(), "application/javascript")

    def _send_untrusted_media(self, body: bytes, content_type: str) -> None:
        self._send_bytes(
            body,
            content_type,
            {"Content-Security-Policy": UNTRUSTED_MEDIA_CSP},
        )

    def _send_bytes(
        self,
        body: bytes,
        content_type: str,
        headers: dict[str, str] | None = None,
    ) -> None:
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)


class RemoteRequestHandler(BaseHTTPRequestHandler):
    server: SmokeServer

    def do_GET(self) -> None:
        if urlparse(self.path).path == "/remote-frame":
            self.server.state.record_remote_frame_request()
            body = b"<!doctype html><p>remote frame</p>"
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(HTTPStatus.NOT_FOUND)

    def log_message(self, format: str, *args: object) -> None:
        pass


def _start_server(server: SmokeServer) -> threading.Thread:
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return thread


def _run_qwebengine_page(url: str, timeout_secs: float, state: SmokeState) -> SmokePage:
    app = QApplication.instance() or QApplication(["qwebengine-csp-smoke"])
    profile = QWebEngineProfile()
    interceptor = ApiAuthInterceptor()
    profile.setUrlRequestInterceptor(interceptor)
    page = SmokePage(profile)
    page._smoke_profile = profile  # type: ignore[attr-defined]
    page._smoke_interceptor = interceptor  # type: ignore[attr-defined]
    loop = QEventLoop()
    timer = QTimer()
    timer.setInterval(50)
    deadline = time.monotonic() + timeout_secs

    def poll() -> None:
        if state.snapshot().done or time.monotonic() >= deadline:
            loop.quit()

    timer.timeout.connect(poll)
    timer.start()
    page.load(QUrl(url))
    loop.exec()
    timer.stop()
    app.processEvents()
    return page


def _delete_page(page: SmokePage) -> None:
    if not sip.isdeleted(page):
        page.deleteLater()
    if app := QApplication.instance():
        app.processEvents()


def _assert_expectations(snapshot: SmokeSnapshot, page: SmokePage) -> None:
    load_events = {
        event["id"] for event in snapshot.events if event.get("type") == "load"
    }
    done_events = [event for event in snapshot.events if event.get("type") == "done"]
    latest_done = done_events[-1] if done_events else {}
    done_results = latest_done.get("results", {})

    expected_loads = {
        "malicious-html-iframe",
        "malicious-svg-object",
        "benign-svg-img",
        "benign-svg-object",
        "remote-iframe",
    }
    done_loads = {k for k, v in done_results.items() if v == "load"}
    done_errors = {k for k, v in done_results.items() if v == "error"}
    missing_loads = expected_loads - load_events - done_loads
    expected_media_requests = {
        "/media/malicious.html",
        "/media/malicious.svg",
        "/media/benign.svg?via=img",
        "/media/benign.svg?via=object",
    }
    missing_media_requests = expected_media_requests - set(snapshot.media_requests)

    errors: list[str] = []
    if snapshot.api_hits:
        errors.append(
            "embedded untrusted media reached /_anki/getImageForOcclusion: "
            + json.dumps(snapshot.api_hits, indent=2)
        )
    if snapshot.script_hits:
        errors.append(
            "embedded untrusted media executed script: "
            + json.dumps(snapshot.script_hits, indent=2)
        )
    if not snapshot.done:
        errors.append("editor smoke script did not report completion")
    if done_errors:
        errors.append(f"expected elements fired error instead of load: {sorted(done_errors)}")
    if missing_loads:
        errors.append(f"missing expected load events: {sorted(missing_loads)}")
    if missing_media_requests:
        errors.append(
            f"missing expected media requests: {sorted(missing_media_requests)}"
        )
    if not snapshot.remote_frame_requested:
        errors.append("different-origin frame request was not observed")
    if latest_done and not latest_done.get("imgComplete"):
        errors.append("SVG loaded via <img> did not complete")
    if latest_done and latest_done.get("imgNaturalWidth", 0) <= 0:
        errors.append("SVG loaded via <img> did not report a natural width")

    if errors:
        print("QtWebEngine CSP smoke test failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        if page.console_messages:
            print("\nConsole messages:", file=sys.stderr)
            for message in page.console_messages:
                print(f"- {message}", file=sys.stderr)
        print("\nEvents:", json.dumps(snapshot.events, indent=2), file=sys.stderr)
        print(
            "\nMedia requests:",
            json.dumps(snapshot.media_requests, indent=2),
            file=sys.stderr,
        )
        print(
            "\nScript hits:",
            json.dumps(snapshot.script_hits, indent=2),
            file=sys.stderr,
        )
        raise SystemExit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=float, default=8.0)
    args = parser.parse_args()

    state = SmokeState()
    page: SmokePage | None = None
    remote_server = SmokeServer(state, None, RemoteRequestHandler)
    _start_server(remote_server)
    main_server = SmokeServer(state, remote_server.server_port, MainRequestHandler)
    _start_server(main_server)

    try:
        page = _run_qwebengine_page(
            f"http://127.0.0.1:{main_server.server_port}/editor",
            args.timeout,
            state,
        )
        _assert_expectations(state.snapshot(), page)
    finally:
        if page is not None:
            _delete_page(page)
        main_server.shutdown()
        remote_server.shutdown()

    print("QtWebEngine CSP smoke test passed.")


if __name__ == "__main__":
    main()
