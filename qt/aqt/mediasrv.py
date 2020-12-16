# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
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

import flask
import flask_cors  # type: ignore
from flask import Response, request
from waitress.server import create_server

import aqt
from anki import hooks
from anki.rsbackend import from_json_bytes
from anki.utils import devMode
from aqt.qt import *
from aqt.utils import aqt_data_folder


def _getExportFolder():
    data_folder = aqt_data_folder()
    webInSrcFolder = os.path.abspath(os.path.join(data_folder, "web"))
    if os.path.exists(webInSrcFolder):
        return webInSrcFolder
    elif isMac:
        dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.abspath(dir + "/../../Resources/web")
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

    def __init__(self, mw: aqt.main.AnkiQt, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.is_shutdown = False

    def run(self):
        try:
            if devMode:
                # idempotent if logging has already been set up
                logging.basicConfig()
            logging.getLogger("waitress").setLevel(logging.ERROR)

            desired_port = int(os.getenv("ANKI_API_PORT", "0"))
            self.server = create_server(
                app,
                host="127.0.0.1",
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

    def shutdown(self):
        self.is_shutdown = True
        sockets = list(self.server._map.values())  # type: ignore
        for socket in sockets:
            socket.handle_close()
        # https://github.com/Pylons/webtest/blob/4b8a3ebf984185ff4fefb31b4d0cf82682e1fcf7/webtest/http.py#L93-L104
        self.server.task_dispatcher.shutdown()

    def getPort(self):
        self._ready.wait()
        return int(self.server.effective_port)  # type: ignore


@app.route("/<path:pathin>", methods=["GET", "POST"])
def allroutes(pathin):
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
            "Path for '%s - %s' is too long!" % (directory, path),
            HTTPStatus.BAD_REQUEST,
        )

    directory = os.path.realpath(directory)
    path = os.path.normpath(path)
    fullpath = os.path.abspath(os.path.join(directory, path))

    # protect against directory transversal: https://security.openstack.org/guidelines/dg_using-file-paths.html
    if not fullpath.startswith(directory):
        return flask.make_response(
            "Path for '%s - %s' is a security leak!" % (directory, path),
            HTTPStatus.FORBIDDEN,
        )

    if isdir:
        return flask.make_response(
            "Path for '%s - %s' is a directory (not supported)!" % (directory, path),
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


def _redirectWebExports(path):
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
                    addprefix = "js/js/vendor/"
                else:
                    addprefix = "js/"

            if addprefix:
                oldpath = path
                path = f"{targetPath}{addprefix}{filename}"
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
                print("_redirectWebExports: %s" % error)
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
    return aqt.mw.col.backend.graphs(search=args["search"], days=args["days"])


def congrats_info() -> bytes:
    info = aqt.mw.col.backend.congrats_info()
    return info.SerializeToString()


post_handlers = dict(
    graphData=graph_data,
    # pylint: disable=unnecessary-lambda
    i18nResources=lambda: aqt.mw.col.backend.i18n_resources(),
    congratsInfo=congrats_info,
)


def handle_post(path: str) -> Response:
    if not aqt.mw.col:
        print(f"collection not open, ignore request for {path}")
        return flask.make_response("Collection not open", HTTPStatus.NOT_FOUND)

    if path in post_handlers:
        data = post_handlers[path]()
        response = flask.make_response(data)
        response.headers["Content-Type"] = "application/binary"
        return response
    else:
        return flask.make_response(
            f"Unhandled post to {path}",
            HTTPStatus.FORBIDDEN,
        )
