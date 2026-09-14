# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Manual QtWebEngine smoke test for the CSPs that guard untrusted content.

This is intentionally not named test_*.py, as QtWebEngine tests are sensitive to
the host GUI environment. Run it manually with:

    tools/qwebengine-csp-smoke

One host page is loaded with three policies: none (like the reviewer/previewer
pages), the legacy editor CSP, and the SvelteKit editor CSP. The host embeds
untrusted media via <iframe>, <object> and <img>, and checks that the media CSP
lets a document load the presentation it ships with, but blocks script and
network access. User embeds must keep working under every policy.

The host also frames a page that runs an inline event handler. Served with the
trusted-page CSP, the page must refuse to render in a frame; served without a
CSP, it is the control and must run. The same page loaded top-level with the
untrusted SvelteKit CSP checks that inline handlers are blocked. Under the
editor CSPs a form must also fail to submit; without a CSP the submission is
the control.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import threading
import time
from collections.abc import Callable
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
from PyQt6.QtCore import QCoreApplication, QEvent, QEventLoop, QTimer, QUrl
from PyQt6.QtWebEngineCore import (
    QWebEnginePage,
    QWebEngineProfile,
    QWebEngineUrlRequestInfo,
    QWebEngineUrlRequestInterceptor,
)
from PyQt6.QtWidgets import QApplication

from aqt.mediasrv import (
    TRUSTED_PAGE_CSP,
    UNTRUSTED_MEDIA_CSP,
    _legacy_editor_content_security_policy,
    _untrusted_sveltekit_content_security_policy,
)

AUTH_TOKEN = "qwebengine-csp-smoke-token"

TRUSTED_PAGE = "trusted-page"
IO_CONTROL = "image-occlusion-unprotected"
IO_UNTRUSTED = "image-occlusion"

# 1x1 transparent png
PIXEL_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
    "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
)


@dataclass
class SmokeState:
    events: list[dict[str, Any]] = field(default_factory=list)
    api_hits: list[dict[str, Any]] = field(default_factory=list)
    script_hits: list[str] = field(default_factory=list)
    media_requests: list[str] = field(default_factory=list)
    # framed documents that were requested, and any inline-handler runs / API
    # hits attributed to them
    io_requests: list[str] = field(default_factory=list)
    io_script_hits: list[str] = field(default_factory=list)
    io_api_hits: dict[str, list[dict[str, Any]]] = field(default_factory=dict)
    remote_frame_requested: bool = False
    remote_style_requested: bool = False
    # a form in the host page was submitted
    form_probe_hit: bool = False
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

    def record_io_request(self, doc: str) -> None:
        with self.lock:
            self.io_requests.append(doc)

    def record_io_script(self, doc: str) -> None:
        with self.lock:
            self.io_script_hits.append(doc)

    def record_io_api_hit(self, doc: str, headers: dict[str, str]) -> None:
        with self.lock:
            self.io_api_hits.setdefault(doc, []).append(headers)

    def record_remote_frame_request(self) -> None:
        with self.lock:
            self.remote_frame_requested = True

    def record_remote_style_request(self) -> None:
        with self.lock:
            self.remote_style_requested = True

    def record_form_probe(self) -> None:
        with self.lock:
            self.form_probe_hit = True

    def snapshot(self) -> "SmokeSnapshot":
        with self.lock:
            return SmokeSnapshot(
                events=list(self.events),
                api_hits=list(self.api_hits),
                script_hits=list(self.script_hits),
                media_requests=list(self.media_requests),
                io_requests=list(self.io_requests),
                io_script_hits=list(self.io_script_hits),
                io_api_hits={k: list(v) for k, v in self.io_api_hits.items()},
                remote_frame_requested=self.remote_frame_requested,
                remote_style_requested=self.remote_style_requested,
                form_probe_hit=self.form_probe_hit,
                done=self.done,
            )


@dataclass
class SmokeSnapshot:
    events: list[dict[str, Any]]
    api_hits: list[dict[str, Any]]
    script_hits: list[str]
    media_requests: list[str]
    io_requests: list[str]
    io_script_hits: list[str]
    io_api_hits: dict[str, list[dict[str, Any]]]
    remote_frame_requested: bool
    remote_style_requested: bool
    form_probe_hit: bool
    done: bool

    def latest_done(self) -> dict[str, Any]:
        done_events = [e for e in self.events if e.get("type") == "done"]
        return done_events[-1] if done_events else {}

    def loaded_ids(self) -> set[str]:
        from_events = {
            e["id"] for e in self.events if e.get("type") == "load" and "id" in e
        }
        from_done = {
            k for k, v in self.latest_done().get("results", {}).items() if v == "load"
        }
        return from_events | from_done

    def errored_ids(self) -> set[str]:
        return {
            k for k, v in self.latest_done().get("results", {}).items() if v == "error"
        }

    def io_hit_with_token(self, doc: str) -> bool:
        return any(
            hit.get("Authorization") == f"Bearer {AUTH_TOKEN}"
            for hit in self.io_api_hits.get(doc, [])
        )


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
        # replaced for each variant
        self.state = state
        self.remote_port = remote_port


class MainRequestHandler(BaseHTTPRequestHandler):
    server: SmokeServer

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        port = self.server.server_port
        if parsed.path == "/reviewer":
            self._send_host_page(None)
        elif parsed.path == "/editor":
            self._send_host_page(_legacy_editor_content_security_policy(port))
        elif parsed.path == "/editor-sveltekit":
            self._send_host_page(
                _untrusted_sveltekit_content_security_policy(port, None)
            )
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
        elif parsed.path == "/media/styled.svg":
            self.server.state.record_media_request(parsed.path)
            assert self.server.remote_port is not None
            remote_css = f"http://127.0.0.1:{self.server.remote_port}/remote-style.css"
            self._send_untrusted_media(
                f"""<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/css" href="styled.css"?>
<?xml-stylesheet type="text/css" href="{remote_css}"?>
<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40">
  <style>@import url("inline-import.css");</style>
  <image href="pixel.png" width="10" height="10"/>
  <script><![CDATA[
  new Image().src = '/__script-ran?doc=styled-svg';
  ]]></script>
  <rect width="80" height="40" fill="#2f7dd1" style="opacity: 0.5"/>
</svg>
""".encode(),
                "image/svg+xml",
            )
        elif parsed.path in ("/media/styled.css", "/media/inline-import.css"):
            self.server.state.record_media_request(parsed.path)
            self._send_untrusted_media(b"rect { opacity: 1 }", "text/css")
        elif parsed.path == "/media/pixel.png":
            self.server.state.record_media_request(parsed.path)
            self._send_untrusted_media(PIXEL_PNG, "image/png")
        elif parsed.path == "/__script-ran":
            self.server.state.record_script_hit(parsed.query)
            self._send_bytes(b"", "text/plain")
        elif parsed.path == "/__form-probe":
            self.server.state.record_form_probe()
            self._send_bytes(b"", "text/plain")
        elif parsed.path == f"/{TRUSTED_PAGE}/note":
            # served like a trusted internal route
            self.server.state.record_io_request(TRUSTED_PAGE)
            self._send_io_document(TRUSTED_PAGE, 42, csp=TRUSTED_PAGE_CSP)
        elif parsed.path == f"/{IO_CONTROL}/note":
            # control: the same document with no policy
            self.server.state.record_io_request(IO_CONTROL)
            self._send_io_document(IO_CONTROL, 43, csp=None)
        elif parsed.path == f"/{IO_UNTRUSTED}/note":
            self.server.state.record_io_request(IO_UNTRUSTED)
            self._send_io_document(
                IO_UNTRUSTED,
                44,
                csp=_untrusted_sveltekit_content_security_policy(port, None),
            )
        elif parsed.path == "/__io-ran":
            self.server.state.record_io_script(
                parse_qs(parsed.query).get("doc", [""])[0]
            )
            self._send_bytes(b"", "text/plain")
        elif parsed.path == "/_anki/io-bundle.js":
            self._send_io_bundle()
        else:
            self.send_error(HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0") or "0")
        body = self.rfile.read(length)

        if parsed.path == "/_anki/__events":
            event = json.loads(body.decode())
            self.server.state.record_event(event)
            self._send_bytes(b"{}", "application/json")
        elif parsed.path == "/_anki/getImageForOcclusion":
            io_doc = self.headers.get("X-Io-Doc")
            if io_doc:
                self.server.state.record_io_api_hit(io_doc, dict(self.headers))
            else:
                self.server.state.record_api_hit(dict(self.headers))
            self._send_bytes(b"{}", "application/json")
        else:
            self.send_error(HTTPStatus.NOT_FOUND)

    def log_message(self, format: str, *args: object) -> None:
        pass

    def _send_host_page(self, csp: str | None) -> None:
        assert self.server.remote_port is not None
        html = f"""<!doctype html>
<meta charset="utf-8">
<body>
  <script src="/_anki/smoke.js?remote_port={self.server.remote_port}"></script>
</body>
"""
        headers = {"Content-Security-Policy": csp} if csp else {}
        self._send_bytes(html.encode(), "text/html", headers)

    def _send_js(self) -> None:
        remote_port = parse_qs(urlparse(self.path).query)["remote_port"][0]
        js = f"""
const remotePort = {remote_port};
const results = {{}};

function record(event) {{
    fetch('/_anki/__events', {{
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
    return element;
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
addElement('object', {{
    id: 'styled-svg-object',
    data: '/media/styled.svg',
    type: 'image/svg+xml',
}});
addElement('iframe', {{
    id: 'remote-iframe',
    src: `http://127.0.0.1:${{remotePort}}/remote-frame`,
}});
addElement('iframe', {{
    id: 'trusted-page-iframe',
    src: '/{TRUSTED_PAGE}/note',
}});
addElement('iframe', {{
    id: 'io-control-iframe',
    src: '/{IO_CONTROL}/note',
}});

// submitted into a frame so the host page stays put
addElement('iframe', {{id: 'form-target', name: 'form-target'}});
const form = document.createElement('form');
form.action = '/__form-probe';
form.target = 'form-target';
document.body.appendChild(form);
form.submit();

setTimeout(() => {{
    const img = document.getElementById('benign-svg-img');
    const sameOrigin = !!document.getElementById('styled-svg-object').contentDocument;
    record({{
        type: 'done',
        results,
        imgComplete: img.complete,
        imgNaturalWidth: img.naturalWidth,
        sameOrigin,
    }});
}}, 3000);
"""
        self._send_bytes(js.encode(), "application/javascript")

    def _send_io_document(self, doc: str, marker: int, csp: str | None) -> None:
        """Serve a document whose bundle injects an inline event handler.

        The bundle is an external script from /_anki/, so it runs under the
        untrusted page CSP; the inline handler it injects must not.
        """
        html = f"""<!doctype html>
<meta charset="utf-8">
<body>
  <div id="io-field"></div>
  <script src="/_anki/io-bundle.js?marker={marker}&doc={doc}"></script>
</body>
"""
        headers = {"Content-Security-Policy": csp} if csp else {}
        self._send_bytes(html.encode(), "text/html", headers)

    def _send_io_bundle(self) -> None:
        js = """
const params = new URL(document.currentScript.src).searchParams;
const marker = Number(params.get('marker'));
const doc = params.get('doc');

window.__ioProbe = function() {
    new Image().src = '/__io-ran?doc=' + encodeURIComponent(doc);
    fetch('/_anki/getImageForOcclusion', {
        method: 'POST',
        headers: {'Content-Type': 'application/binary', 'X-Io-Doc': doc},
        body: new Uint8Array([marker, marker, marker]),
    }).catch(() => {});
};

document.getElementById('io-field').innerHTML =
    '<img src="broken-' + doc + '" onerror="__ioProbe()">';

// Only when loaded top-level; framed copies must not end the host's run.
if (window.top === window) {
    setTimeout(() => {
        fetch('/_anki/__events', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({type: 'done', doc}),
        }).catch(() => {});
    }, 1500);
}
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
        if urlparse(self.path).path == "/remote-style.css":
            self.server.state.record_remote_style_request()
            body = b"rect { opacity: 1 }"
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/css")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif urlparse(self.path).path == "/remote-frame":
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


def _run_qwebengine_page(
    app: QCoreApplication,
    profile: QWebEngineProfile,
    url: str,
    timeout_secs: float,
    state: SmokeState,
) -> SmokePage:
    page = SmokePage(profile)
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


def _delete_page(app: QCoreApplication, page: SmokePage) -> None:
    if not sip.isdeleted(page):
        page.deleteLater()
    # outside an event loop, deleteLater() needs an explicit flush
    app.sendPostedEvents(None, QEvent.Type.DeferredDelete.value)
    app.processEvents()


def _check_host(snapshot: SmokeSnapshot, editor_csp: bool) -> list[str]:
    """Checks shared by the CSP-free host and the editor hosts.

    User embeds must work the same way under every policy; only the form
    submission differs.
    """
    errors: list[str] = []
    latest_done = snapshot.latest_done()

    expected_loads = {
        "malicious-html-iframe",
        "malicious-svg-object",
        "benign-svg-img",
        "benign-svg-object",
        "styled-svg-object",
        "remote-iframe",
    }
    missing_loads = expected_loads - snapshot.loaded_ids()
    expected_media_requests = {
        "/media/malicious.html",
        "/media/malicious.svg",
        "/media/benign.svg?via=img",
        "/media/benign.svg?via=object",
        # an embedded document must be able to load the passive resources it
        # ships with, eg an SVG and the stylesheet sitting next to it
        "/media/styled.svg",
        "/media/styled.css",
        "/media/inline-import.css",
        "/media/pixel.png",
    }
    missing_media_requests = expected_media_requests - set(snapshot.media_requests)

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
        errors.append("host smoke script did not report completion")
    if errored := snapshot.errored_ids():
        errors.append(
            f"expected elements fired error instead of load: {sorted(errored)}"
        )
    if missing_loads:
        errors.append(f"missing expected load events: {sorted(missing_loads)}")
    if missing_media_requests:
        errors.append(
            f"missing expected media requests: {sorted(missing_media_requests)}"
        )
    if not snapshot.remote_frame_requested:
        errors.append("different-origin frame request was not observed")
    if snapshot.remote_style_requested:
        errors.append("untrusted media loaded a stylesheet from a remote origin")
    if latest_done and not latest_done.get("sameOrigin"):
        errors.append(
            "embedded media landed in an opaque origin"
            " (sandbox is missing allow-same-origin)"
        )
    if latest_done and not latest_done.get("imgComplete"):
        errors.append("SVG loaded via <img> did not complete")
    if latest_done and latest_done.get("imgNaturalWidth", 0) <= 0:
        errors.append("SVG loaded via <img> did not report a natural width")

    if editor_csp and snapshot.form_probe_hit:
        errors.append("editor CSP let a form submit (form-action)")
    if not editor_csp and not snapshot.form_probe_hit:
        errors.append("control: form submission was not observed without a CSP")

    io_script_docs = set(snapshot.io_script_hits)
    if IO_CONTROL not in snapshot.io_requests:
        errors.append("control: host could not frame the unprotected route")
    elif not snapshot.io_hit_with_token(IO_CONTROL):
        errors.append(
            "control: framed unprotected route did not reach"
            " /_anki/getImageForOcclusion with the token"
            + (
                " (inline handler ran, request blocked)"
                if IO_CONTROL in io_script_docs
                else " (inline handler did not run)"
            )
        )
    # The trusted route may be fetched, but must not render in a frame.
    if snapshot.io_hit_with_token(TRUSTED_PAGE):
        errors.append(
            "framed trusted route reached /_anki/getImageForOcclusion with the token"
        )
    elif snapshot.io_api_hits.get(TRUSTED_PAGE) or TRUSTED_PAGE in io_script_docs:
        errors.append("framed trusted route ran its inline handler")
    return errors


def _check_inline_handler_blocked(snapshot: SmokeSnapshot) -> list[str]:
    """Loaded top-level with the untrusted page CSP: the bundle runs, but the
    inline handler must not."""
    errors: list[str] = []
    if not snapshot.done:
        errors.append("bundle did not report completion")
    if snapshot.io_api_hits or snapshot.io_script_hits:
        errors.append(
            "Untrusted SvelteKit CSP did not block the inline handler: "
            + json.dumps(snapshot.io_api_hits, indent=2)
        )
    return errors


def _report_failure(
    variant: str, errors: list[str], snapshot: SmokeSnapshot, page: SmokePage
) -> None:
    print(f"QtWebEngine CSP smoke test failed ({variant}):", file=sys.stderr)
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
    print(
        "\nFramed document requests:",
        json.dumps(snapshot.io_requests, indent=2),
        file=sys.stderr,
    )
    print(
        "\nFramed document API hits:",
        json.dumps(snapshot.io_api_hits, indent=2),
        file=sys.stderr,
    )
    print(file=sys.stderr)


VARIANTS: list[tuple[str, str, Callable[[SmokeSnapshot], list[str]]]] = [
    ("reviewer", "/reviewer", lambda s: _check_host(s, editor_csp=False)),
    ("editor", "/editor", lambda s: _check_host(s, editor_csp=True)),
    (
        "editor-sveltekit",
        "/editor-sveltekit",
        lambda s: _check_host(s, editor_csp=True),
    ),
    (
        "image-occlusion-csp-inline",
        f"/{IO_UNTRUSTED}/note",
        _check_inline_handler_blocked,
    ),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=float, default=8.0)
    args = parser.parse_args()

    remote_server = SmokeServer(SmokeState(), None, RemoteRequestHandler)
    _start_server(remote_server)
    main_server = SmokeServer(
        SmokeState(), remote_server.server_port, MainRequestHandler
    )
    _start_server(main_server)

    app = QApplication.instance() or QApplication(["qwebengine-csp-smoke"])
    # one profile for all variants; QtWebEngine crashes if a profile goes away
    # while a page of it is still being torn down
    profile = QWebEngineProfile()
    interceptor = ApiAuthInterceptor()
    profile.setUrlRequestInterceptor(interceptor)

    failed: list[str] = []
    try:
        for variant, path, check in VARIANTS:
            state = SmokeState()
            main_server.state = remote_server.state = state
            page = _run_qwebengine_page(
                app,
                profile,
                f"http://127.0.0.1:{main_server.server_port}{path}",
                args.timeout,
                state,
            )
            try:
                snapshot = state.snapshot()
                if errors := check(snapshot):
                    failed.append(variant)
                    _report_failure(variant, errors, snapshot, page)
            finally:
                _delete_page(app, page)
    finally:
        main_server.shutdown()
        remote_server.shutdown()

    if failed:
        print(f"Failed variants: {', '.join(failed)}", file=sys.stderr)
        raise SystemExit(1)
    variants = ", ".join(variant for variant, _, _ in VARIANTS)
    print(f"QtWebEngine CSP smoke test passed ({variants}).")


if __name__ == "__main__":
    main()
