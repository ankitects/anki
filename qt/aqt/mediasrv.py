# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import asyncio
import enum
import logging
import mimetypes
import os
import re
import secrets
import sys
import threading
import traceback
from collections.abc import Callable
from dataclasses import dataclass
from errno import EPROTOTYPE
from http import HTTPStatus
from typing import Any, Generic, cast

import flask
import flask_cors
import stringcase
import waitress.wasyncore
from flask import Response, abort, request
from waitress.server import create_server

import aqt
import aqt.main
import aqt.operations
from anki import frontend_pb2, generic_pb2, hooks
from anki.collection import OpChanges, OpChangesOnly, Progress, SearchNode
from anki.decks import UpdateDeckConfigs
from anki.scheduler.v3 import SchedulingStatesWithContext, SetSchedulingStatesRequest
from anki.utils import dev_mode, from_json_bytes, to_json_bytes
from aqt.changenotetype import ChangeNotetypeDialog
from aqt.deckoptions import DeckOptionsDialog
from aqt.operations import on_op_finished
from aqt.operations.deck import update_deck_configs as update_deck_configs_op
from aqt.progress import ProgressUpdate
from aqt.qt import *
from aqt.utils import (
    aqt_data_path,
    askUser,
    openLink,
    show_info,
    show_warning,
    tr,
)

# https://forums.ankiweb.net/t/anki-crash-when-using-a-specific-deck/22266
waitress.wasyncore._DISCONNECTED = waitress.wasyncore._DISCONNECTED.union({EPROTOTYPE})  # type: ignore

logger = logging.getLogger(__name__)
app = flask.Flask(__name__, root_path="/fake")
flask_cors.CORS(app, resources={r"/*": {"origins": "127.0.0.1"}})


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


class PageContext(enum.IntEnum):
    UNKNOWN = enum.auto()
    EDITOR = enum.auto()
    REVIEWER = enum.auto()
    PREVIEWER = enum.auto()
    CARD_LAYOUT = enum.auto()
    DECK_OPTIONS = enum.auto()
    # something in /_anki/pages/
    NON_LEGACY_PAGE = enum.auto()
    # Do not use this if you present user content (e.g. content from cards), as it's a
    # security issue.
    ADDON_PAGE = enum.auto()


@dataclass
class LegacyPage:
    html: str
    context: PageContext


class MediaServer(threading.Thread):
    _ready = threading.Event()
    daemon = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__()
        self.is_shutdown = False
        # map of webview ids to pages
        self._legacy_pages: dict[int, LegacyPage] = {}

    def run(self) -> None:
        try:
            desired_host = os.getenv("ANKI_API_HOST", "127.0.0.1")
            desired_port = int(os.getenv("ANKI_API_PORT") or 0)
            self.server = create_server(
                app,
                host=desired_host,
                port=desired_port,
                clear_untrusted_proxy_headers=True,
            )
            logger.info(
                "Serving on http://%s:%s",
                self.server.effective_host,  # type: ignore[union-attr]
                self.server.effective_port,  # type: ignore[union-attr]
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

    def set_page_html(
        self, id: int, html: str, context: PageContext = PageContext.UNKNOWN
    ) -> None:
        self._legacy_pages[id] = LegacyPage(html, context)

    def get_page(self, id: int) -> LegacyPage | None:
        return self._legacy_pages.get(id)

    def get_page_html(self, id: int) -> str | None:
        if page := self.get_page(id):
            return page.html
        else:
            return None

    def get_page_context(self, id: int) -> PageContext | None:
        if page := self.get_page(id):
            return page.context
        else:
            return None

    def clear_page_html(self, id: int) -> None:
        try:
            del self._legacy_pages[id]
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
    elif path.endswith(".js") or path.endswith(".mjs"):
        return "application/javascript"
    else:
        # autodetect
        mime, _encoding = mimetypes.guess_type(path)
        return mime or "application/octet-stream"


def _text_response(code: HTTPStatus, text: str) -> Response:
    """Return an error message.

    Response is returned as text/plain, so no escaping of untrusted
    input is required."""
    resp = flask.make_response(text, code)
    resp.headers["Content-type"] = "text/plain"
    return resp


def _handle_local_file_request(request: LocalFileRequest) -> Response:
    directory = request.root
    path = request.path
    try:
        isdir = os.path.isdir(os.path.join(directory, path))
    except ValueError:
        return _text_response(
            HTTPStatus.BAD_REQUEST, f"Path for '{directory} - {path}' is too long!"
        )

    directory = os.path.realpath(directory)
    path = os.path.normpath(path)
    fullpath = os.path.abspath(os.path.join(directory, path))

    # protect against directory transversal: https://security.openstack.org/guidelines/dg_using-file-paths.html
    if not fullpath.startswith(directory):
        return _text_response(
            HTTPStatus.FORBIDDEN, f"Path for '{directory} - {path}' is a security leak!"
        )

    if isdir:
        return _text_response(
            HTTPStatus.FORBIDDEN,
            f"Path for '{directory} - {path}' is a directory (not supported)!",
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
                fullpath,
                mimetype=mimetype,
                conditional=True,
                max_age=max_age,
                download_name="foo",  # type: ignore[call-arg]
            )
        else:
            print(f"Not found: {path}")
            return _text_response(HTTPStatus.NOT_FOUND, f"Invalid path: {path}")

    except Exception as error:
        if dev_mode:
            print(
                "Caught HTTP server exception,\n%s"
                % "".join(traceback.format_exception(*sys.exc_info())),
            )

        # swallow it - user likely surfed away from
        # review screen before an image had finished
        # downloading
        return _text_response(HTTPStatus.INTERNAL_SERVER_ERROR, str(error))


def _builtin_data(path: str) -> bytes:
    """Return data from file in aqt/data folder.
    Path must use forward slash separators."""
    full_path = aqt_data_path() / ".." / path
    return full_path.read_bytes()


def _handle_builtin_file_request(request: BundledFileRequest) -> Response:
    path = request.path
    # do we need to serve the fallback page?
    immutable = "immutable" in path
    if path.startswith("sveltekit/") and not immutable:
        path = "sveltekit/index.html"
    mimetype = _mime_for_path(path)
    data_path = f"data/web/{path}"
    try:
        data = _builtin_data(data_path)
        response = Response(data, mimetype=mimetype)
        if immutable:
            response.headers["Cache-Control"] = "max-age=31536000"
        return response
    except FileNotFoundError:
        if dev_mode:
            print(f"404: {data_path}")
        resp = _text_response(HTTPStatus.NOT_FOUND, f"Invalid path: {path}")
        # we're including the path verbatim in our response, so we need to either use
        # plain text, or escape HTML characters to avoid reflecting untrusted input
        resp.headers["Content-type"] = "text/plain"
        return resp
    except Exception as error:
        if dev_mode:
            print(
                "Caught HTTP server exception,\n%s"
                % "".join(traceback.format_exception(*sys.exc_info())),
            )

        # swallow it - user likely surfed away from
        # review screen before an image had finished
        # downloading
        return _text_response(HTTPStatus.INTERNAL_SERVER_ERROR, str(error))


@app.route("/<path:pathin>", methods=["GET", "POST"])
def handle_request(pathin: str) -> Response:
    host = request.headers.get("Host", "").lower()
    allowed_prefixes = ("127.0.0.1:", "localhost:", "[::1]:")
    if not any(host.startswith(prefix) for prefix in allowed_prefixes):
        # while we only bind to localhost, this request may have come from a local browser
        # via a DNS rebinding attack; deny it unless we're doing non-local testing
        if os.environ.get("ANKI_API_HOST") != "0.0.0.0":
            print("deny non-local host", host)
            abort(403)

    req = _extract_request(pathin)
    logger.debug("%s /%s", flask.request.method, pathin)

    if isinstance(req, NotFound):
        print(req.message)
        return _text_response(HTTPStatus.NOT_FOUND, f"Invalid path: {pathin}")
    elif callable(req):
        return _handle_dynamic_request(req)
    elif isinstance(req, BundledFileRequest):
        return _handle_builtin_file_request(req)
    elif isinstance(req, LocalFileRequest):
        return _handle_local_file_request(req)
    else:
        return _text_response(HTTPStatus.FORBIDDEN, f"unexpected request: {pathin}")


def is_sveltekit_page(path: str) -> bool:
    page_name = path.split("/")[0]
    return page_name in [
        "graphs",
        "congrats",
        "card-info",
        "change-notetype",
        "deck-options",
        "import-anki-package",
        "import-csv",
        "import-page",
        "image-occlusion",
        "editor",
    ]


def _extract_internal_request(
    path: str,
) -> BundledFileRequest | DynamicRequest | NotFound | None:
    "Catch /_anki references and rewrite them to web export folder."
    if is_sveltekit_page(path):
        path = f"_anki/sveltekit/_app/{path}"
    if path.startswith("_app/"):
        path = path.replace("_app", "_anki/sveltekit/_app")

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
            if base in ("jquery-ui", "jquery", "plot"):
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
        aqt.mw.taskman.run_on_main(lambda: aqt.mw.moveToState("overview"))
    return raw_backend_request("congrats_info")()


def get_deck_configs_for_update() -> bytes:
    return aqt.mw.col._backend.get_deck_configs_for_update_raw(request.data)


def update_deck_configs() -> bytes:
    # the regular change tracking machinery expects to be started on the main
    # thread and uses a callback on success, so we need to run this op on
    # main, and return immediately from the web request

    input = UpdateDeckConfigs()
    input.ParseFromString(request.data)

    def on_progress(progress: Progress, update: ProgressUpdate) -> None:
        if progress.HasField("compute_memory"):
            val = progress.compute_memory
            update.max = val.total_cards
            update.value = val.current_cards
            update.label = val.label
        elif progress.HasField("compute_params"):
            val2 = progress.compute_params
            # prevent an indeterminate progress bar from appearing at the start of each preset
            update.max = max(val2.total, 1)
            update.value = val2.current
            pct = str(int(val2.current / val2.total * 100) if val2.total > 0 else 0)
            label = tr.deck_config_optimizing_preset(
                current_count=val2.current_preset, total_count=val2.total_presets
            )
            if val2.reviews:
                reviews = tr.deck_config_percent_of_reviews(
                    pct=pct, reviews=val2.reviews
                )
            else:
                reviews = tr.qt_misc_processing()

            update.label = label + "\n" + reviews
        else:
            return
        if update.user_wants_abort:
            update.abort = True

    def on_success(changes: OpChanges) -> None:
        if isinstance(window := aqt.mw.app.activeWindow(), DeckOptionsDialog):
            window.reject()

    def handle_on_main() -> None:
        update_deck_configs_op(parent=aqt.mw, input=input).success(
            on_success
        ).with_backend_progress(on_progress).run_in_background()

    aqt.mw.taskman.run_on_main(handle_on_main)
    return b""


def get_scheduling_states_with_context() -> bytes:
    return SchedulingStatesWithContext(
        states=aqt.mw.reviewer.get_scheduling_states(),
        context=aqt.mw.reviewer.get_scheduling_context(),
    ).SerializeToString()


def set_scheduling_states() -> bytes:
    states = SetSchedulingStatesRequest()
    states.ParseFromString(request.data)
    aqt.mw.reviewer.set_scheduling_states(states)
    return b""


def import_done() -> bytes:
    def update_window_modality() -> None:
        if window := aqt.mw.app.activeWindow():
            from aqt.import_export.import_dialog import ImportDialog

            if isinstance(window, ImportDialog):
                window.hide()
                window.setWindowModality(Qt.WindowModality.NonModal)
                window.show()

    aqt.mw.taskman.run_on_main(update_window_modality)
    return b""


def import_request(endpoint: str) -> bytes:
    output = raw_backend_request(endpoint)()
    response = OpChangesOnly()
    response.ParseFromString(output)

    def handle_on_main() -> None:
        window = aqt.mw.app.activeWindow()
        on_op_finished(aqt.mw, response, window)

    aqt.mw.taskman.run_on_main(handle_on_main)

    return output


def import_csv() -> bytes:
    return import_request("import_csv")


def import_anki_package() -> bytes:
    return import_request("import_anki_package")


def import_json_file() -> bytes:
    return import_request("import_json_file")


def import_json_string() -> bytes:
    return import_request("import_json_string")


def search_in_browser() -> bytes:
    node = SearchNode()
    node.ParseFromString(request.data)

    def handle_on_main() -> None:
        aqt.dialogs.open("Browser", aqt.mw, search=(node,))

    aqt.mw.taskman.run_on_main(handle_on_main)

    return b""


def change_notetype() -> bytes:
    data = request.data

    def handle_on_main() -> None:
        window = aqt.mw.app.activeWindow()
        if isinstance(window, ChangeNotetypeDialog):
            window.save(data)

    aqt.mw.taskman.run_on_main(handle_on_main)
    return b""


def deck_options_require_close() -> bytes:
    def handle_on_main() -> None:
        window = aqt.mw.app.activeWindow()
        if isinstance(window, DeckOptionsDialog):
            window.require_close()

    # on certain linux systems, askUser's QMessageBox.question unsets the active window
    # so we wait for the next event loop before querying the next current active window
    aqt.mw.taskman.run_on_main(lambda: QTimer.singleShot(0, handle_on_main))
    return b""


def deck_options_ready() -> bytes:
    def handle_on_main() -> None:
        window = aqt.mw.app.activeWindow()
        if isinstance(window, DeckOptionsDialog):
            window.set_ready()

    aqt.mw.taskman.run_on_main(handle_on_main)
    return b""


def editor_op_changes_request(endpoint: str) -> bytes:
    output = raw_backend_request(endpoint)()
    response = OpChanges()
    response.ParseFromString(output)

    def handle_on_main() -> None:
        from aqt.editor import NewEditor

        handler = aqt.mw.app.activeWindow()
        if handler and isinstance(getattr(handler, "editor", None), NewEditor):
            handler = handler.editor  # type: ignore
        on_op_finished(aqt.mw, response, handler)

    aqt.mw.taskman.run_on_main(handle_on_main)

    return output


def update_editor_note() -> bytes:
    return editor_op_changes_request("update_notes")


def update_editor_notetype() -> bytes:
    return editor_op_changes_request("update_notetype")


def add_editor_note() -> bytes:
    return editor_op_changes_request("add_note")


def get_setting_json(getter: Callable[[str], Any]) -> bytes:
    req = generic_pb2.String()
    req.ParseFromString(request.data)
    value = getter(req.val)
    output = generic_pb2.Json(json=to_json_bytes(value)).SerializeToString()
    return output


def set_setting_json(setter: Callable[[str, Any], Any]) -> bytes:
    req = frontend_pb2.SetSettingJsonRequest()
    req.ParseFromString(request.data)
    setter(req.key, from_json_bytes(req.value_json))
    return b""


def get_profile_config_json() -> bytes:
    assert aqt.mw.pm.profile is not None
    return get_setting_json(aqt.mw.pm.profile.get)


def set_profile_config_json() -> bytes:
    assert aqt.mw.pm.profile is not None
    return set_setting_json(aqt.mw.pm.profile.__setitem__)


def get_meta_json() -> bytes:
    return get_setting_json(aqt.mw.pm.meta.get)


def set_meta_json() -> bytes:
    return set_setting_json(aqt.mw.pm.meta.__setitem__)


def get_config_json() -> bytes:
    try:
        return get_setting_json(aqt.mw.col.conf.get_immutable)
    except KeyError:
        return generic_pb2.Json(json=b"null").SerializeToString()


def set_config_json() -> bytes:
    return set_setting_json(aqt.mw.col.set_config)


def convert_pasted_image() -> bytes:
    req = frontend_pb2.ConvertPastedImageRequest()
    req.ParseFromString(request.data)
    image = QImage.fromData(req.data)
    buffer = QBuffer()
    buffer.open(QBuffer.OpenModeFlag.ReadWrite)
    if req.ext == "png":
        quality = 50
    else:
        quality = 80
    image.save(buffer, req.ext, quality)
    buffer.reset()
    data = bytes(cast(bytes, buffer.readAll()))
    return frontend_pb2.ConvertPastedImageResponse(data=data).SerializeToString()


def retrieve_url() -> bytes:
    from aqt.utils import retrieve_url

    req = generic_pb2.String()
    req.ParseFromString(request.data)
    url = req.val
    filename, error = retrieve_url(url)
    return frontend_pb2.RetrieveUrlResponse(
        filename=filename, error=error
    ).SerializeToString()


AsyncRequestReturnType = TypeVar("AsyncRequestReturnType")


class AsyncRequestHandler(Generic[AsyncRequestReturnType]):
    def __init__(self, callback: Callable[[AsyncRequestHandler], None]) -> None:
        self.callback = callback
        self.loop = asyncio.get_event_loop()
        self.future = self.loop.create_future()

    def run(self) -> None:
        aqt.mw.taskman.run_on_main(lambda: self.callback(self))

    def set_result(self, result: AsyncRequestReturnType) -> None:
        self.loop.call_soon_threadsafe(self.future.set_result, result)

    async def get_result(self) -> AsyncRequestReturnType:
        return await self.future


async def open_file_picker() -> bytes:
    req = frontend_pb2.openFilePickerRequest()
    req.ParseFromString(request.data)

    def callback(request_handler: AsyncRequestHandler) -> None:
        from aqt.utils import getFile

        def cb(filename: str | None) -> None:
            request_handler.set_result(filename)

        window = aqt.mw.app.activeWindow()
        assert window is not None
        getFile(
            parent=window,
            title=req.title,
            cb=cast(Callable[[Any], None], cb),
            filter=f"{req.filter_description} ({' '.join(f'*.{ext}' for ext in req.extensions)})",
            key=req.key,
        )

    request_handler: AsyncRequestHandler[str | None] = AsyncRequestHandler(callback)
    request_handler.run()
    filename = await request_handler.get_result()

    return generic_pb2.String(val=filename if filename else "").SerializeToString()


def open_media() -> bytes:
    from aqt.utils import openFolder

    req = generic_pb2.String()
    req.ParseFromString(request.data)
    path = os.path.join(aqt.mw.col.media.dir(), req.val)
    aqt.mw.taskman.run_on_main(lambda: openFolder(path))

    return b""


def show_in_media_folder() -> bytes:
    from aqt.utils import show_in_folder

    req = generic_pb2.String()
    req.ParseFromString(request.data)
    path = os.path.join(aqt.mw.col.media.dir(), req.val)
    aqt.mw.taskman.run_on_main(lambda: show_in_folder(path))

    return b""


async def record_audio() -> bytes:
    def callback(request_handler: AsyncRequestHandler) -> None:
        from aqt.sound import record_audio

        def cb(path: str | None) -> None:
            request_handler.set_result(path)

        window = aqt.mw.app.activeWindow()
        assert window is not None
        record_audio(window, aqt.mw, True, cb)

    request_handler: AsyncRequestHandler[str | None] = AsyncRequestHandler(callback)
    request_handler.run()
    path = await request_handler.get_result()

    return generic_pb2.String(val=path if path else "").SerializeToString()


def read_clipboard() -> bytes:
    req = frontend_pb2.ReadClipboardRequest()
    req.ParseFromString(request.data)
    data = {}
    clipboard = aqt.mw.app.clipboard()
    assert clipboard is not None
    mime_data = clipboard.mimeData(QClipboard.Mode.Clipboard)
    assert mime_data is not None
    for type in req.types:
        data[type] = bytes(mime_data.data(type))  # type: ignore

    return frontend_pb2.ReadClipboardResponse(data=data).SerializeToString()


def write_clipboard() -> bytes:
    req = frontend_pb2.WriteClipboardRequest()
    req.ParseFromString(request.data)
    clipboard = aqt.mw.app.clipboard()
    assert clipboard is not None
    mime_data = clipboard.mimeData(QClipboard.Mode.Clipboard)
    assert mime_data is not None
    for type, data in req.data.items():
        mime_data.setData(type, data)
    return b""


def close_add_cards() -> bytes:
    req = generic_pb2.Bool()
    req.ParseFromString(request.data)

    def handle_on_main() -> None:
        from aqt.addcards import NewAddCards

        window = aqt.mw.app.activeWindow()
        if isinstance(window, NewAddCards):
            window._close_if_user_wants_to_discard_changes(req.val)

    aqt.mw.taskman.run_on_main(lambda: QTimer.singleShot(0, handle_on_main))
    return b""


def close_edit_current() -> bytes:
    def handle_on_main() -> None:
        from aqt.editcurrent import NewEditCurrent

        window = aqt.mw.app.activeWindow()
        if isinstance(window, NewEditCurrent):
            window.close()

    aqt.mw.taskman.run_on_main(lambda: QTimer.singleShot(0, handle_on_main))
    return b""


def open_link() -> bytes:
    req = generic_pb2.String()
    req.ParseFromString(request.data)
    url = req.val
    aqt.mw.taskman.run_on_main(lambda: openLink(url))
    return b""


async def ask_user() -> bytes:
    req = frontend_pb2.AskUserRequest()
    req.ParseFromString(request.data)

    def callback(request_handler: AsyncRequestHandler) -> None:
        kwargs: dict[str, Any] = dict(text=req.text)
        if req.HasField("help"):
            help_arg: Any
            if req.help.WhichOneof("value") == "help_page":
                help_arg = req.help.help_page
            else:
                help_arg = req.help.help_link
            kwargs["help"] = help_arg
        if req.HasField("title"):
            kwargs["title"] = req.title
        if req.HasField("default_no"):
            kwargs["defaultno"] = req.default_no
        answer = askUser(**kwargs)
        request_handler.set_result(answer)

    request_handler: AsyncRequestHandler[bool] = AsyncRequestHandler(callback)
    request_handler.run()
    answer = await request_handler.get_result()

    return generic_pb2.Bool(val=answer).SerializeToString()


async def show_message_box() -> bytes:
    req = frontend_pb2.ShowMessageBoxRequest()
    req.ParseFromString(request.data)

    def callback(request_handler: AsyncRequestHandler) -> None:
        kwargs: dict[str, Any] = dict(text=req.text)
        if req.type == frontend_pb2.MessageBoxType.INFO:
            icon = QMessageBox.Icon.Information
        elif req.type == frontend_pb2.MessageBoxType.WARNING:
            icon = QMessageBox.Icon.Warning
        elif req.type == frontend_pb2.MessageBoxType.CRITICAL:
            icon = QMessageBox.Icon.Critical
        kwargs["icon"] = icon
        if req.HasField("help"):
            help_arg: Any
            if req.help.WhichOneof("value") == "help_page":
                help_arg = req.help.help_page
            else:
                help_arg = req.help.help_link
            kwargs["help"] = help_arg
        if req.HasField("title"):
            kwargs["title"] = req.title
        if req.HasField("text_format"):
            kwargs["text_format"] = req.text_format
        show_info(**kwargs)
        request_handler.set_result(True)

    request_handler: AsyncRequestHandler[bool] = AsyncRequestHandler(callback)
    request_handler.run()
    answer = await request_handler.get_result()

    return generic_pb2.Bool(val=answer).SerializeToString()


post_handler_list = [
    congrats_info,
    get_deck_configs_for_update,
    update_deck_configs,
    get_scheduling_states_with_context,
    set_scheduling_states,
    change_notetype,
    import_done,
    import_csv,
    import_anki_package,
    import_json_file,
    import_json_string,
    search_in_browser,
    deck_options_require_close,
    deck_options_ready,
    update_editor_note,
    update_editor_notetype,
    add_editor_note,
    get_profile_config_json,
    set_profile_config_json,
    get_meta_json,
    set_meta_json,
    get_config_json,
    convert_pasted_image,
    retrieve_url,
    open_file_picker,
    open_media,
    show_in_media_folder,
    record_audio,
    read_clipboard,
    write_clipboard,
    close_add_cards,
    close_edit_current,
    open_link,
    ask_user,
    show_message_box,
]


exposed_backend_list = [
    # CollectionService
    "latest_progress",
    # DeckService
    "get_deck_names",
    # I18nService
    "i18n_resources",
    # ImportExportService
    "get_csv_metadata",
    "get_import_anki_package_presets",
    # NotesService
    "get_field_names",
    "get_note",
    "new_note",
    "note_fields_check",
    # NotetypesService
    "get_notetype",
    "get_notetype_names",
    "get_change_notetype_info",
    "get_cloze_field_ords",
    # StatsService
    "card_stats",
    "get_review_logs",
    "graphs",
    "get_graph_preferences",
    "set_graph_preferences",
    # TagsService
    "complete_tag",
    # ImageOcclusionService
    "get_image_for_occlusion",
    "add_image_occlusion_note",
    "get_image_occlusion_note",
    "update_image_occlusion_note",
    "get_image_occlusion_fields",
    # SchedulerService
    "compute_fsrs_params",
    "compute_optimal_retention",
    "set_wants_abort",
    "evaluate_params_legacy",
    "get_optimal_retention_parameters",
    "simulate_fsrs_review",
    "simulate_fsrs_workload",
    # DeckConfigService
    "get_ignored_before_count",
    "get_retention_workload",
    # CardRenderingService
    "encode_iri_paths",
    "decode_iri_paths",
    "html_to_text_line",
    # ConfigService
    "set_config_json",
    "get_config_bool",
    # MediaService
    "add_media_file",
    "add_media_from_path",
    "get_absolute_media_path",
    "extract_media_files",
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
                import inspect

                if inspect.iscoroutinefunction(handler):
                    try:
                        loop = asyncio.get_event_loop()
                        if loop.is_running():
                            import concurrent.futures

                            with concurrent.futures.ThreadPoolExecutor() as executor:
                                future = executor.submit(asyncio.run, handler())
                                data = future.result()
                        else:
                            data = loop.run_until_complete(handler())
                    except RuntimeError:
                        data = asyncio.run(handler())
                else:
                    result = handler()
                    data = result
                if data:
                    response = flask.make_response(data)
                    response.headers["Content-Type"] = "application/binary"
                else:
                    response = _text_response(HTTPStatus.NO_CONTENT, "")
            except Exception as exc:
                print(traceback.format_exc())
                response = _text_response(HTTPStatus.INTERNAL_SERVER_ERROR, str(exc))
            return response

        return wrapped
    else:
        return NotFound(message=f"{path} not found")


def _check_dynamic_request_permissions():
    if request.method == "GET":
        return

    def warn() -> None:
        show_warning(
            "Unexpected API access. Please report this message on the Anki forums."
        )

    # check content type header to ensure this isn't an opaque request from another origin
    if request.headers["Content-type"] != "application/binary":
        aqt.mw.taskman.run_on_main(warn)
        abort(403)

    # does page have access to entire API?
    if _have_api_access():
        return

    # whitelisted API endpoints for reviewer/previewer
    if request.path in (
        "/_anki/getSchedulingStatesWithContext",
        "/_anki/setSchedulingStates",
        "/_anki/i18nResources",
        "/_anki/congratsInfo",
    ):
        pass
    else:
        # other legacy pages may contain third-party JS, so we do not
        # allow them to access our API
        aqt.mw.taskman.run_on_main(warn)
        abort(403)


def _handle_dynamic_request(req: DynamicRequest) -> Response:
    _check_dynamic_request_permissions()
    try:
        return req()
    except Exception as e:
        return _text_response(HTTPStatus.INTERNAL_SERVER_ERROR, str(e))


def legacy_page_data() -> Response:
    id = int(request.args["id"])
    page = aqt.mw.mediaServer.get_page(id)
    if page:
        response = Response(page.html, mimetype="text/html")
        # Prevent JS in field content from being executed in the editor, as it would
        # have access to our internal API, and is a security risk.
        if page.context == PageContext.EDITOR:
            port = aqt.mw.mediaServer.getPort()
            csp_paths = (
                f"http://127.0.0.1:{port}/_anki/",
                f"http://127.0.0.1:{port}/_addons/",
            )
            response.headers["Content-Security-Policy"] = (
                f"script-src {' '.join(csp_paths)}"
            )
        return response
    else:
        return _text_response(HTTPStatus.NOT_FOUND, "page not found")


_APIKEY = secrets.token_urlsafe(32)


def _have_api_access() -> bool:
    return (
        request.headers.get("Authorization") == f"Bearer {_APIKEY}"
        or os.environ.get("ANKI_API_HOST") == "0.0.0.0"
    )


# this currently only handles a single method; in the future, idempotent
# requests like i18nResources should probably be moved here
def _extract_dynamic_get_request(path: str) -> DynamicRequest | None:
    if path == "legacyPageData":
        return legacy_page_data
    else:
        return None
