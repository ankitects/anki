# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import logging
import mimetypes
import os
import re
import sys
import threading
import time
import traceback
from dataclasses import dataclass
from http import HTTPStatus
from typing import Callable

import flask
import flask_cors  # type: ignore
from flask import Response, request
from waitress.server import create_server

import aqt
import aqt.main
import aqt.operations
from anki import hooks
from anki._vendor import stringcase
from anki.collection import OpChanges
from anki.decks import DeckConfigsForUpdate, UpdateDeckConfigs
from anki.scheduler.v3 import NextStates
from anki.utils import dev_mode
from aqt.changenotetype import ChangeNotetypeDialog
from aqt.deckoptions import DeckOptionsDialog
from aqt.operations.deck import update_deck_configs as update_deck_configs_op
from aqt.qt import *

app = flask.Flask(__name__, root_path="/fake")
flask_cors.CORS(app)


@dataclass
class LocalFileRequest:
    # base folder, eg media folder
    root: str
    # path to file relative to root folder
    path: str


@dataclass
class BundledFileRequest:
    # path relative to aqt data folder
    path: str


@dataclass
class NotFound:
    message: str


DynamicRequest = Callable[[], Response]


class MediaServer(threading.Thread):

    _ready = threading.Event()
    daemon = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__()
        self.is_shutdown = False
        # map of webview ids to pages
        self._page_html: dict[int, str] = {}

    def run(self) -> None:
        try:
            if dev_mode:
                # idempotent if logging has already been set up
                logging.basicConfig()
            logging.getLogger("waitress").setLevel(logging.ERROR)

            desired_host = os.getenv("ANKI_API_HOST", "127.0.0.1")
            desired_port = int(os.getenv("ANKI_API_PORT", "0"))
            self.server = create_server(
                app,
                host=desired_host,
                port=desired_port,
                clear_untrusted_proxy_headers=True,
            )
            if dev_mode:
                print(
                    "Serving on http://%s:%s"
                    % (self.server.effective_host, self.server.effective_port)  # type: ignore
                )

            self._ready.set()
            self.server.run()

        except Exception:
            if not self.is_shutdown:
                raise

    def shutdown(self) -> None:
        self.is_shutdown = True
        sockets = list(self.server._map.values())  # type: ignore
        for socket in sockets:
            socket.handle_close()
        # https://github.com/Pylons/webtest/blob/4b8a3ebf984185ff4fefb31b4d0cf82682e1fcf7/webtest/http.py#L93-L104
        self.server.task_dispatcher.shutdown()

    def getPort(self) -> int:
        self._ready.wait()
        return int(self.server.effective_port)  # type: ignore

    def set_page_html(self, id: int, html: str) -> None:
        self._page_html[id] = html

    def get_page_html(self, id: int) -> str | None:
        return self._page_html.get(id)

    def clear_page_html(self, id: int) -> None:
        try:
            del self._page_html[id]
        except KeyError:
            pass


@app.route("/favicon.ico")
def favicon() -> Response:
    request = BundledFileRequest(os.path.join("imgs", "favicon.ico"))
    return _handle_builtin_file_request(request)


def _mime_for_path(path: str) -> str:
    "Mime type for provided path/filename."
    if path.endswith(".css"):
        # some users may have invalid mime type in the Windows registry
        return "text/css"
    elif path.endswith(".js"):
        return "application/javascript"
    else:
        # autodetect
        mime, _encoding = mimetypes.guess_type(path)
        return mime or "application/octet-stream"


def _handle_local_file_request(request: LocalFileRequest) -> Response:
    directory = request.root
    path = request.path
    try:
        isdir = os.path.isdir(os.path.join(directory, path))
    except ValueError:
        return flask.make_response(
            f"Path for '{directory} - {path}' is too long!",
            HTTPStatus.BAD_REQUEST,
        )

    directory = os.path.realpath(directory)
    path = os.path.normpath(path)
    fullpath = os.path.abspath(os.path.join(directory, path))

    # protect against directory transversal: https://security.openstack.org/guidelines/dg_using-file-paths.html
    if not fullpath.startswith(directory):
        return flask.make_response(
            f"Path for '{directory} - {path}' is a security leak!",
            HTTPStatus.FORBIDDEN,
        )

    if isdir:
        return flask.make_response(
            f"Path for '{directory} - {path}' is a directory (not supported)!",
            HTTPStatus.FORBIDDEN,
        )

    try:
        mimetype = _mime_for_path(fullpath)
        if os.path.exists(fullpath):
            if fullpath.endswith(".css"):
                # caching css files prevents flicker in the webview, but we want
                # a short cache
                max_age = 10
            elif fullpath.endswith(".js"):
                # don't cache js files
                max_age = 0
            else:
                max_age = 60 * 60
            return flask.send_file(
                fullpath, mimetype=mimetype, conditional=True, max_age=max_age  # type: ignore[call-arg]
            )
        else:
            print(f"Not found: {path}")
            return flask.make_response(
                f"Invalid path: {path}",
                HTTPStatus.NOT_FOUND,
            )

    except Exception as error:
        if dev_mode:
            print(
                "Caught HTTP server exception,\n%s"
                % "".join(traceback.format_exception(*sys.exc_info())),
            )

        # swallow it - user likely surfed away from
        # review screen before an image had finished
        # downloading
        return flask.make_response(
            str(error),
            HTTPStatus.INTERNAL_SERVER_ERROR,
        )


def _builtin_data(path: str) -> bytes:
    """Return data from file in aqt/data folder.
    Path must use forward slash separators."""
    # overriden location?
    if data_folder := os.getenv("ANKI_DATA_FOLDER"):
        full_path = os.path.join(data_folder, path)
        with open(full_path, "rb") as f:
            return f.read()
    else:
        if is_win and not getattr(sys, "frozen", False):
            # default Python resource loader expects backslashes on Windows
            path = path.replace("/", "\\")
        reader = aqt.__loader__.get_resource_reader("aqt")  # type: ignore
        with reader.open_resource(path) as f:
            return f.read()


def _handle_builtin_file_request(request: BundledFileRequest) -> Response:
    path = request.path
    mimetype = _mime_for_path(path)
    data_path = f"data/web/{path}"
    try:
        data = _builtin_data(data_path)
        return Response(data, mimetype=mimetype)
    except FileNotFoundError:
        return flask.make_response(
            f"Invalid path: {path}",
            HTTPStatus.NOT_FOUND,
        )
    except Exception as error:
        if dev_mode:
            print(
                "Caught HTTP server exception,\n%s"
                % "".join(traceback.format_exception(*sys.exc_info())),
            )

        # swallow it - user likely surfed away from
        # review screen before an image had finished
        # downloading
        return flask.make_response(
            str(error),
            HTTPStatus.INTERNAL_SERVER_ERROR,
        )


@app.route("/<path:pathin>", methods=["GET", "POST"])
def handle_request(pathin: str) -> Response:
    request = _extract_request(pathin)
    if dev_mode:
        print(f"{time.time():.3f} {flask.request.method} /{pathin}")

    if isinstance(request, NotFound):
        print(request.message)
        return flask.make_response(
            f"Invalid path: {pathin}",
            HTTPStatus.NOT_FOUND,
        )
    elif callable(request):
        return _handle_dynamic_request(request)
    elif isinstance(request, BundledFileRequest):
        return _handle_builtin_file_request(request)
    elif isinstance(request, LocalFileRequest):
        return _handle_local_file_request(request)
    else:
        return flask.make_response(
            f"unexpected request: {pathin}",
            HTTPStatus.FORBIDDEN,
        )


def _extract_internal_request(
    path: str,
) -> BundledFileRequest | DynamicRequest | NotFound | None:
    "Catch /_anki references and rewrite them to web export folder."
    prefix = "_anki/"
    if not path.startswith(prefix):
        return None

    dirname = os.path.dirname(path)
    filename = os.path.basename(path)
    additional_prefix = None

    if dirname == "_anki":
        if flask.request.method == "POST":
            return _extract_collection_post_request(filename)
        elif get_handler := _extract_dynamic_get_request(filename):
            return get_handler

        # remap legacy top-level references
        base, ext = os.path.splitext(filename)
        if ext == ".css":
            additional_prefix = "css/"
        elif ext == ".js":
            if base in ("browsersel", "jquery-ui", "jquery", "plot"):
                additional_prefix = "js/vendor/"
            else:
                additional_prefix = "js/"
    # handle requests for vendored libraries
    elif dirname == "_anki/js/vendor":
        base, ext = os.path.splitext(filename)

        if base == "jquery":
            base = "jquery.min"
            additional_prefix = "js/vendor/"

        elif base == "jquery-ui":
            base = "jquery-ui.min"
            additional_prefix = "js/vendor/"

        elif base == "browsersel":
            base = "css_browser_selector.min"
            additional_prefix = "js/vendor/"

    if additional_prefix:
        oldpath = path
        path = f"{prefix}{additional_prefix}{base}{ext}"
        print(f"legacy {oldpath} remapped to {path}")

    return BundledFileRequest(path=path[len(prefix) :])


def _extract_addon_request(path: str) -> LocalFileRequest | NotFound | None:
    "Catch /_addons references and rewrite them to addons folder."
    prefix = "_addons/"
    if not path.startswith(prefix):
        return None

    addon_path = path[len(prefix) :]

    try:
        manager = aqt.mw.addonManager
    except AttributeError as error:
        if dev_mode:
            print(f"_redirectWebExports: {error}")
        return None

    try:
        addon, sub_path = addon_path.split("/", 1)
    except ValueError:
        return None
    if not addon:
        return None

    pattern = manager.getWebExports(addon)
    if not pattern:
        return None

    if re.fullmatch(pattern, sub_path):
        return LocalFileRequest(root=manager.addonsFolder(), path=addon_path)

    return NotFound(message=f"couldn't locate item in add-on folder {path}")


def _extract_request(
    path: str,
) -> LocalFileRequest | BundledFileRequest | DynamicRequest | NotFound:
    if internal := _extract_internal_request(path):
        return internal
    elif addon := _extract_addon_request(path):
        return addon

    if not aqt.mw.col:
        return NotFound(message=f"collection not open, ignore request for {path}")

    path = hooks.media_file_filter(path)
    return LocalFileRequest(root=aqt.mw.col.media.dir(), path=path)


def congrats_info() -> bytes:
    if not aqt.mw.col.sched._is_finished():
        aqt.mw.taskman.run_on_main(lambda: aqt.mw.moveToState("review"))
    return raw_backend_request("congrats_info")()


def get_deck_configs_for_update() -> bytes:
    config_bytes = aqt.mw.col._backend.get_deck_configs_for_update_raw(request.data)
    configs = DeckConfigsForUpdate.FromString(config_bytes)
    configs.have_addons = aqt.mw.addonManager.dirty
    return configs.SerializeToString()


def update_deck_configs() -> bytes:
    # the regular change tracking machinery expects to be started on the main
    # thread and uses a callback on success, so we need to run this op on
    # main, and return immediately from the web request

    input = UpdateDeckConfigs()
    input.ParseFromString(request.data)

    def on_success(changes: OpChanges) -> None:
        if isinstance(window := aqt.mw.app.activeWindow(), DeckOptionsDialog):
            window.reject()

    def handle_on_main() -> None:
        update_deck_configs_op(parent=aqt.mw, input=input).success(
            on_success
        ).run_in_background()

    aqt.mw.taskman.run_on_main(handle_on_main)
    return b""


def next_card_states() -> bytes:
    if states := aqt.mw.reviewer.get_next_states():
        return states.SerializeToString()
    else:
        return b""


def set_next_card_states() -> bytes:
    key = request.headers.get("key", "")
    input = NextStates()
    input.ParseFromString(request.data)
    aqt.mw.reviewer.set_next_states(key, input)
    return b""


def change_notetype() -> bytes:
    data = request.data

    def handle_on_main() -> None:
        window = aqt.mw.app.activeWindow()
        if isinstance(window, ChangeNotetypeDialog):
            window.save(data)

    aqt.mw.taskman.run_on_main(handle_on_main)
    return b""


post_handler_list = [
    congrats_info,
    get_deck_configs_for_update,
    update_deck_configs,
    next_card_states,
    set_next_card_states,
    change_notetype,
]


exposed_backend_list = [
    # I18nService
    "i18n_resources",
    # NotesService
    "get_note",
    # NotetypesService
    "get_notetype_names",
    "get_change_notetype_info",
    # StatsService
    "card_stats",
    "graphs",
    "get_graph_preferences",
    "set_graph_preferences",
    # TagsService
    "complete_tag",
]


def raw_backend_request(endpoint: str) -> Callable[[], bytes]:
    # check for key at startup
    from anki._backend import RustBackend

    assert hasattr(RustBackend, f"{endpoint}_raw")

    return lambda: getattr(aqt.mw.col._backend, f"{endpoint}_raw")(request.data)


# all methods in here require a collection
post_handlers = {
    stringcase.camelcase(handler.__name__): handler for handler in post_handler_list
} | {
    stringcase.camelcase(handler): raw_backend_request(handler)
    for handler in exposed_backend_list
}


def _extract_collection_post_request(path: str) -> DynamicRequest | NotFound:
    if not aqt.mw.col:
        return NotFound(message=f"collection not open, ignore request for {path}")
    if handler := post_handlers.get(path):
        # convert bytes/None into response
        def wrapped() -> Response:
            try:
                if data := handler():
                    response = flask.make_response(data)
                    response.headers["Content-Type"] = "application/binary"
                else:
                    response = flask.make_response("", HTTPStatus.NO_CONTENT)
            except:
                print(traceback.format_exc())
                response = flask.make_response("", HTTPStatus.INTERNAL_SERVER_ERROR)
            return response

        return wrapped
    else:
        return NotFound(message=f"{path} not found")


def _handle_dynamic_request(request: DynamicRequest) -> Response:
    try:
        return request()
    except Exception as e:
        return flask.make_response(str(e), HTTPStatus.INTERNAL_SERVER_ERROR)


def legacy_page_data() -> Response:
    id = int(request.args["id"])
    if html := aqt.mw.mediaServer.get_page_html(id):
        return Response(html, mimetype="text/html")
    else:
        return flask.make_response("page not found", HTTPStatus.NOT_FOUND)


# this currently only handles a single method; in the future, idempotent
# requests like i18nResources should probably be moved here
def _extract_dynamic_get_request(path: str) -> DynamicRequest | None:
    if path == "legacyPageData":
        return legacy_page_data
    else:
        return None
