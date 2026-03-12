# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time
from concurrent.futures import Future
from dataclasses import dataclass
from typing import Callable

from anki.api_pb2 import ApiRequest
from anki.api_pb2 import ApiResponse as ProtoApiResponse
from aqt.main import AnkiQt


@dataclass
class ApiResponse:
    body: bytes


HandlerCallback = Callable[[ApiRequest], ApiResponse]
handlers: dict[str, HandlerCallback] = {}


def register_api_route(mw: AnkiQt, path: str, handler: HandlerCallback) -> None:
    if path in handlers:
        raise Exception(f"Path {path} is already registered")
    handlers[path] = handler
    mw.backend.register_api_route(path)


def _monitor_frontend_requests(mw: AnkiQt) -> None:
    while True:
        if handlers:
            requests = mw.backend.get_pending_api_requests()
            for request in requests:
                response = handlers[request.path](request)
                mw.backend.send_api_response(
                    ProtoApiResponse(
                        id=request.id, path=request.path, body=response.body
                    )
                )
        time.sleep(1.0)


def run_api_server_in_background(mw: AnkiQt) -> None:
    def on_server_finished(future: Future) -> None:
        try:
            future.result()
        except Exception as exc:
            print("API server failed:", exc)

    def on_request_monitor_finished(future: Future) -> None:
        try:
            future.result()
        except Exception as exc:
            print("API request monitor failed:", exc)

    mw.taskman.run_in_background(
        lambda: mw.backend.run_api_server(),
        on_server_finished,
        uses_collection=False,
    )
    mw.taskman.run_in_background(
        lambda: _monitor_frontend_requests(mw),
        on_request_monitor_finished,
        uses_collection=False,
    )
