# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Wrapper for requests that adds hooks for tracking upload/download progress.

The hooks http_data_did_send and http_data_did_receive will be called for each
chunk or partial read, on the thread that is running the request.
"""

import io
import os
from typing import Any, Dict, Optional

import requests
from requests import Response

from anki import hooks

HTTP_BUF_SIZE = 64 * 1024


class AnkiRequestsClient:

    verify = True
    timeout = 60

    def __init__(self) -> None:
        self.session = requests.Session()

    def post(self, url: str, data: Any, headers: Optional[Dict[str, str]]) -> Response:
        data = _MonitoringFile(data)  # pytype: disable=wrong-arg-types
        headers["User-Agent"] = self._agentName()
        return self.session.post(
            url,
            data=data,
            headers=headers,
            stream=True,
            timeout=self.timeout,
            verify=self.verify,
        )  # pytype: disable=wrong-arg-types

    def get(self, url, headers=None) -> Response:
        if headers is None:
            headers = {}
        headers["User-Agent"] = self._agentName()
        return self.session.get(
            url, stream=True, headers=headers, timeout=self.timeout, verify=self.verify
        )

    def streamContent(self, resp) -> bytes:
        resp.raise_for_status()

        buf = io.BytesIO()
        for chunk in resp.iter_content(chunk_size=HTTP_BUF_SIZE):
            hooks.http_data_did_receive(len(chunk))
            buf.write(chunk)
        return buf.getvalue()

    def _agentName(self) -> str:
        from anki import version

        return "Anki {}".format(version)


# allow user to accept invalid certs in work/school settings
if os.environ.get("ANKI_NOVERIFYSSL"):
    AnkiRequestsClient.verify = False

    import warnings

    warnings.filterwarnings("ignore")


class _MonitoringFile(io.BufferedReader):
    def read(self, size=-1) -> bytes:
        data = io.BufferedReader.read(self, HTTP_BUF_SIZE)
        hooks.http_data_did_send(len(data))
        return data
