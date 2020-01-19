# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Wrapper for requests that adds a callback for tracking upload/download progress.
"""

import io
import os
from typing import Any, Callable, Dict, Optional

import requests
from requests import Response

HTTP_BUF_SIZE = 64 * 1024

ProgressCallback = Callable[[int, int], None]


class HttpClient:

    verify = True
    timeout = 60
    # args are (upload_bytes_in_chunk, download_bytes_in_chunk)
    progress_hook: Optional[ProgressCallback] = None

    def __init__(self, progress_hook: Optional[ProgressCallback] = None) -> None:
        self.progress_hook = progress_hook
        self.session = requests.Session()

    def post(self, url: str, data: Any, headers: Optional[Dict[str, str]]) -> Response:
        data = _MonitoringFile(
            data, hook=self.progress_hook
        )  # pytype: disable=wrong-arg-types
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
            if self.progress_hook:
                self.progress_hook(0, len(chunk))
            buf.write(chunk)
        return buf.getvalue()

    def _agentName(self) -> str:
        from anki import version

        return "Anki {}".format(version)


# allow user to accept invalid certs in work/school settings
if os.environ.get("ANKI_NOVERIFYSSL"):
    HttpClient.verify = False

    import warnings

    warnings.filterwarnings("ignore")


class _MonitoringFile(io.BufferedReader):
    def __init__(self, raw: io.RawIOBase, hook: Optional[ProgressCallback]):
        io.BufferedReader.__init__(self, raw)
        self.hook = hook

    def read(self, size=-1) -> bytes:
        data = io.BufferedReader.read(self, HTTP_BUF_SIZE)
        if self.hook:
            self.hook(len(data), 0)
        return data
