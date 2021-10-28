# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Please see /docs/syncserver.md
#

from __future__ import annotations

import gzip
import os
import socket
import sys
import time
from http import HTTPStatus
from io import BytesIO
from tempfile import NamedTemporaryFile
from typing import Iterable, Optional

try:
    import flask
    from waitress.server import create_server
except ImportError as error:
    print(error, "- to use the server, 'pip install anki[syncserver]'")
    sys.exit(1)


from flask import Response

from anki.collection import Collection
from anki.sync_pb2 import SyncServerMethodRequest

Method = SyncServerMethodRequest.Method  # pylint: disable=no-member

app = flask.Flask(__name__, root_path="/fake")
col: Collection
trace = os.getenv("TRACE")


def get_request_data() -> bytes:
    buf = BytesIO()
    flask.request.files["data"].save(buf)
    buf.seek(0)
    zip = gzip.GzipFile(mode="rb", fileobj=buf)
    return zip.read()


def get_request_data_into_file() -> bytes:
    "Returns the utf8 path to the resulting file."
    # this could be optimized to stream the data into a file
    # in the future
    data = get_request_data()
    tempobj = NamedTemporaryFile(dir=folder(), delete=False)
    tempobj.write(data)
    tempobj.close()
    return tempobj.name.encode("utf8")


def handle_sync_request(method_str: str) -> Response:
    method = get_method(method_str)
    if method is None:
        raise Exception(f"unknown method: {method_str}")

    if method == Method.FULL_UPLOAD:
        data = get_request_data_into_file()
    else:
        data = get_request_data()
        if trace:
            print("-->", data)

    full = method in (Method.FULL_UPLOAD, Method.FULL_DOWNLOAD)
    if full:
        col.close_for_full_sync()
    try:
        outdata = col._backend.sync_server_method(method=method, data=data)
    except Exception as error:
        if method == Method.META:
            # if parallel syncing requests come in, block them
            print("exception in meta", error)
            return flask.make_response("Conflict", 409)
        else:
            raise
    finally:
        if full:
            after_full_sync()

    resp = None
    if method == Method.FULL_UPLOAD:
        # upload call expects a raw string literal returned
        outdata = b"OK"
    elif method == Method.FULL_DOWNLOAD:
        path = outdata.decode("utf8")

        def stream_reply() -> Iterable[bytes]:
            with open(path, "rb") as file:
                while chunk := file.read(16 * 1024):
                    yield chunk
                os.unlink(path)

        resp = Response(stream_reply())
    else:
        if trace:
            print("<--", outdata)

    if not resp:
        resp = flask.make_response(outdata)
    resp.headers["Content-Type"] = "application/binary"
    return resp


def after_full_sync() -> None:
    # the server methods do not reopen the collection after a full sync,
    # so we need to
    col.reopen(after_full_sync=False)
    col.db.rollback()


def get_method(
    method_str: str,
) -> SyncServerMethodRequest.Method.V | None:  # pylint: disable=no-member
    if method_str == "hostKey":
        return Method.HOST_KEY
    elif method_str == "meta":
        return Method.META
    elif method_str == "start":
        return Method.START
    elif method_str == "applyGraves":
        return Method.APPLY_GRAVES
    elif method_str == "applyChanges":
        return Method.APPLY_CHANGES
    elif method_str == "chunk":
        return Method.CHUNK
    elif method_str == "applyChunk":
        return Method.APPLY_CHUNK
    elif method_str == "sanityCheck2":
        return Method.SANITY_CHECK
    elif method_str == "finish":
        return Method.FINISH
    elif method_str == "abort":
        return Method.ABORT
    elif method_str == "upload":
        return Method.FULL_UPLOAD
    elif method_str == "download":
        return Method.FULL_DOWNLOAD
    else:
        return None


@app.route("/<path:pathin>", methods=["POST"])
def handle_request(pathin: str) -> Response:
    path = pathin
    print(int(time.time()), flask.request.remote_addr, path)

    if path.startswith("sync/"):
        return handle_sync_request(path.split("/", maxsplit=1)[1])
    else:
        return flask.make_response("not found", HTTPStatus.NOT_FOUND)


def folder() -> str:
    folder = os.getenv("FOLDER", os.path.expanduser("~/.syncserver"))
    if not os.path.exists(folder):
        print("creating", folder)
        os.mkdir(folder)
    return folder


def col_path() -> str:
    return os.path.join(folder(), "collection.server.anki2")


def serve() -> None:
    global col  # pylint: disable=invalid-name

    col = Collection(col_path(), server=True)
    # don't hold an outer transaction open
    col.db.rollback()
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8080"))

    server = create_server(
        app,
        host=host,
        port=port,
        clear_untrusted_proxy_headers=True,
    )

    effective_port = server.effective_port  # type: ignore
    print(f"Sync server listening on http://{host}:{effective_port}/sync/")
    if host == "0.0.0.0":
        ip = socket.gethostbyname(socket.gethostname())
        print(f"Replace 0.0.0.0 with your machine's IP address (perhaps {ip})")
    print(
        "For more info, see https://github.com/ankitects/anki/blob/master/docs/syncserver.md"
    )
    server.run()
