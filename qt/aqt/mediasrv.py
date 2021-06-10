# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import logging
import os
import re
import sys
import threading
import time
import traceback
from http import HTTPStatus
from typing import Tuple

import flask
import flask_cors  # type: ignore
from flask import Response, request
from waitress.server import create_server

import aqt
from anki import hooks
from anki.collection import GraphPreferences, OpChanges
from anki.decks import UpdateDeckConfigs
from anki.models import NotetypeNames
from anki.scheduler.v3 import NextStates
from anki.utils import devMode, from_json_bytes
from aqt.changenotetype import ChangeNotetypeDialog
from aqt.deckoptions import DeckOptionsDialog
from aqt.operations.deck import update_deck_configs
from aqt.qt import *
from aqt.utils import aqt_data_folder


def _getExportFolder() -> str:
    if not (data_folder := os.getenv("ANKI_DATA_FOLDER")):
        data_folder = aqt_data_folder()
    webInSrcFolder = os.path.abspath(os.path.join(data_folder, "web"))
    if os.path.exists(webInSrcFolder):
        return webInSrcFolder
    elif isMac:
        dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.abspath(f"{dir}/../../Resources/web")
    else:
        if os.environ.get("TEST_TARGET"):
            # running tests in bazel; we have no data
            return "."
        else:
            raise Exception("couldn't find web folder")


_exportFolder = _getExportFolder()
app = flask.Flask(__name__)
flask_cors.CORS(app)


class MediaServer(threading.Thread):

    _ready = threading.Event()
    daemon = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__()
        self.is_shutdown = False

    def run(self) -> None:
        try:
            if devMode:
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
            if devMode:
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


@app.route("/<path:pathin>", methods=["GET", "POST"])
def allroutes(pathin: str) -> Response:
    try:
        directory, path = _redirectWebExports(pathin)
    except TypeError:
        return flask.make_response(
            f"Invalid path: {pathin}",
            HTTPStatus.FORBIDDEN,
        )

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

    if devMode:
        print(f"{time.time():.3f} {flask.request.method} /{pathin}")

    try:
        if flask.request.method == "POST":
            return handle_post(path)

        if fullpath.endswith(".css"):
            # some users may have invalid mime type in the Windows registry
            mimetype = "text/css"
        elif fullpath.endswith(".js"):
            mimetype = "application/javascript"
        else:
            # autodetect
            mimetype = None
        if os.path.exists(fullpath):
            return flask.send_file(fullpath, mimetype=mimetype, conditional=True)
        else:
            print(f"Not found: {ascii(pathin)}")
            return flask.make_response(
                f"Invalid path: {pathin}",
                HTTPStatus.NOT_FOUND,
            )

    except Exception as error:
        if devMode:
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


def _redirectWebExports(path: str) -> Tuple[str, str]:
    # catch /_anki references and rewrite them to web export folder
    targetPath = "_anki/"
    if path.startswith(targetPath):
        dirname = os.path.dirname(path)
        filename = os.path.basename(path)
        addprefix = None

        # remap legacy top-level references
        if dirname == "_anki":
            base, ext = os.path.splitext(filename)
            if ext == ".css":
                addprefix = "css/"
            elif ext == ".js":
                if base in ("browsersel", "jquery-ui", "jquery", "plot"):
                    addprefix = "js/vendor/"
                else:
                    addprefix = "js/"

        elif dirname == "_anki/js/vendor":
            base, ext = os.path.splitext(filename)

            if base == "jquery":
                base = "jquery.min"
                addprefix = "js/vendor/"

            elif base == "jquery-ui":
                base = "jquery-ui.min"
                addprefix = "js/vendor/"

            elif base == "browsersel":
                base = "css_browser_selector.min"
                addprefix = "js/vendor/"

        if addprefix:
            oldpath = path
            path = f"{targetPath}{addprefix}{base}{ext}"
            print(f"legacy {oldpath} remapped to {path}")

        return _exportFolder, path[len(targetPath) :]

    # catch /_addons references and rewrite them to addons folder
    targetPath = "_addons/"
    if path.startswith(targetPath):
        addonPath = path[len(targetPath) :]

        try:
            addMgr = aqt.mw.addonManager
        except AttributeError as error:
            if devMode:
                print(f"_redirectWebExports: {error}")
            return None

        try:
            addon, subPath = addonPath.split("/", 1)
        except ValueError:
            return None
        if not addon:
            return None

        pattern = addMgr.getWebExports(addon)
        if not pattern:
            return None

        if re.fullmatch(pattern, subPath):
            return addMgr.addonsFolder(), addonPath

        print(f"couldn't locate item in add-on folder {path}")
        return None

    if not aqt.mw.col:
        print(f"collection not open, ignore request for {path}")
        return None

    path = hooks.media_file_filter(path)

    return aqt.mw.col.media.dir(), path


def graph_data() -> bytes:
    args = from_json_bytes(request.data)
    return aqt.mw.col.graph_data(search=args["search"], days=args["days"])


def graph_preferences() -> bytes:
    return aqt.mw.col.get_graph_preferences()


def set_graph_preferences() -> None:
    prefs = GraphPreferences()
    prefs.ParseFromString(request.data)
    aqt.mw.col.set_graph_preferences(prefs)


def congrats_info() -> bytes:
    return aqt.mw.col.congrats_info()


def i18n_resources() -> bytes:
    args = from_json_bytes(request.data)
    return aqt.mw.col.i18n_resources(modules=args["modules"])


def deck_configs_for_update() -> bytes:
    args = from_json_bytes(request.data)
    msg = aqt.mw.col.decks.get_deck_configs_for_update(deck_id=args["deckId"])
    msg.have_addons = aqt.mw.addonManager.dirty
    return msg.SerializeToString()


def update_deck_configs_request() -> bytes:
    # the regular change tracking machinery expects to be started on the main
    # thread and uses a callback on success, so we need to run this op on
    # main, and return immediately from the web request

    input = UpdateDeckConfigs()
    input.ParseFromString(request.data)

    def on_success(changes: OpChanges) -> None:
        if isinstance(window := aqt.mw.app.activeWindow(), DeckOptionsDialog):
            window.reject()

    def handle_on_main() -> None:
        update_deck_configs(parent=aqt.mw, input=input).success(
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


def notetype_names() -> bytes:
    msg = NotetypeNames(entries=aqt.mw.col.models.all_names_and_ids())
    return msg.SerializeToString()


def change_notetype_info() -> bytes:
    args = from_json_bytes(request.data)
    return aqt.mw.col.models.change_notetype_info(
        old_notetype_id=args["oldNotetypeId"], new_notetype_id=args["newNotetypeId"]
    )


def change_notetype() -> bytes:
    data = request.data

    def handle_on_main() -> None:
        window = aqt.mw.app.activeWindow()
        if isinstance(window, ChangeNotetypeDialog):
            window.save(data)

    aqt.mw.taskman.run_on_main(handle_on_main)
    return b""


post_handlers = {
    "graphData": graph_data,
    "graphPreferences": graph_preferences,
    "setGraphPreferences": set_graph_preferences,
    "deckConfigsForUpdate": deck_configs_for_update,
    "updateDeckConfigs": update_deck_configs_request,
    "nextCardStates": next_card_states,
    "setNextCardStates": set_next_card_states,
    "changeNotetypeInfo": change_notetype_info,
    "notetypeNames": notetype_names,
    "changeNotetype": change_notetype,
    # pylint: disable=unnecessary-lambda
    "i18nResources": i18n_resources,
    "congratsInfo": congrats_info,
}


def handle_post(path: str) -> Response:
    if not aqt.mw.col:
        print(f"collection not open, ignore request for {path}")
        return flask.make_response("Collection not open", HTTPStatus.NOT_FOUND)

    if path in post_handlers:
        try:
            if data := post_handlers[path]():
                response = flask.make_response(data)
                response.headers["Content-Type"] = "application/binary"
            else:
                response = flask.make_response("", HTTPStatus.NO_CONTENT)
        except Exception as e:
            return flask.make_response(str(e), HTTPStatus.INTERNAL_SERVER_ERROR)
    else:
        response = flask.make_response(
            f"Unhandled post to {path}",
            HTTPStatus.FORBIDDEN,
        )

    return response
