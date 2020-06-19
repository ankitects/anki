# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import logging
import os
import re
import sys
import threading
import traceback
from http import HTTPStatus

import flask
import flask_cors  # type: ignore
from waitress.server import create_server

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
        raise Exception("couldn't find web folder")


_exportFolder = _getExportFolder()
app = flask.Flask(__name__)
flask_cors.CORS(app)


class MediaServer(threading.Thread):

    _ready = threading.Event()
    daemon = True

    def __init__(self, mw, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.is_shutdown = False
        _redirectWebExports.mw = mw

    def run(self):
        try:
            if devMode:
                # idempotent if logging has already been set up
                logging.basicConfig()
            else:
                logging.getLogger("waitress").setLevel(logging.ERROR)

            self.server = create_server(app, host="127.0.0.1", port=0)
            if devMode:
                print(
                    "Serving on http://%s:%s"
                    % (self.server.effective_host, self.server.effective_port)
                )

            self._ready.set()
            self.server.run()

        except Exception:
            if not self.is_shutdown:
                raise

    def shutdown(self):
        self.is_shutdown = True
        sockets = list(self.server._map.values())
        for socket in sockets:
            socket.handle_close()
        # https://github.com/Pylons/webtest/blob/4b8a3ebf984185ff4fefb31b4d0cf82682e1fcf7/webtest/http.py#L93-L104
        self.server.task_dispatcher.shutdown()

    def getPort(self):
        self._ready.wait()
        return int(self.server.effective_port)


@app.route("/", defaults={"path": ""})
@app.route("/<path:path>")
def allroutes(path):
    directory, path = _redirectWebExports(path)
    try:
        isdir = os.path.isdir(os.path.join(directory, path))
    except ValueError:
        return flask.Response(
            "Path for '%s - %s' is too long!" % (directory, path),
            status=HTTPStatus.BAD_REQUEST,
            mimetype="text/plain",
        )

    directory = os.path.realpath(directory)
    path = os.path.normpath(path)
    fullpath = os.path.realpath(os.path.join(directory, path))

    # protect against directory transversal: https://security.openstack.org/guidelines/dg_using-file-paths.html
    if not fullpath.startswith(directory):
        return flask.Response(
            "Path for '%s - %s' is a security leak!" % (directory, path),
            status=HTTPStatus.FORBIDDEN,
            mimetype="text/plain",
        )

    if isdir:
        return flask.Response(
            "Path for '%s - %s' is a directory (not supported)!" % (directory, path),
            status=HTTPStatus.FORBIDDEN,
            mimetype="text/plain",
        )

    try:
        if devMode:
            print("Sending file '%s - %s'" % (directory, path))

        return flask.send_file(fullpath, conditional=True)

    except Exception as error:
        if devMode:
            print(
                "Caught HTTP server exception,\n%s"
                % "".join(traceback.format_exception(*sys.exc_info())),
            )

        # swallow it - user likely surfed away from
        # review screen before an image had finished
        # downloading
        return flask.Response(
            "For path '%s - %s' %s!" % (directory, path, error),
            status=HTTPStatus.INTERNAL_SERVER_ERROR,
            mimetype="text/plain",
        )


def _redirectWebExports(path):
    # catch /_anki references and rewrite them to web export folder
    targetPath = "_anki/"
    if path.startswith(targetPath):
        return _exportFolder, path[len(targetPath) :]

    # catch /_addons references and rewrite them to addons folder
    targetPath = "_addons/"
    if path.startswith(targetPath):
        addonPath = path[len(targetPath) :]

        try:
            addMgr = _redirectWebExports.mw.addonManager
        except AttributeError as error:
            if devMode:
                print("_redirectWebExports: %s" % error)
            return _exportFolder, addonPath

        try:
            addon, subPath = addonPath.split(os.path.sep, 1)
        except ValueError:
            return addMgr.addonsFolder(), path
        if not addon:
            return addMgr.addonsFolder(), path

        pattern = addMgr.getWebExports(addon)
        if not pattern:
            return addMgr.addonsFolder(), path

        if re.fullmatch(pattern, subPath):
            return addMgr.addonsFolder(), addonPath

    return _redirectWebExports.mw.col.media.dir(), path
