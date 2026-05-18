# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Wrapper for requests that adds a callback for tracking upload/download progress.
"""

from __future__ import annotations

import io
import os
from collections.abc import Callable
from typing import Any

import requests
from requests import Response

from anki._legacy import DeprecatedNamesMixin

HTTP_BUF_SIZE = 64 * 1024

ProgressCallback = Callable[[int, int], None]


class SystemStoreHTTPAdapter(requests.adapters.HTTPAdapter):
    """Opt out of Requests' default certifi bundle."""

    def cert_verify(self, conn: Any, url: str, verify: bool | str, cert: Any) -> None:
        if verify is True and url.lower().startswith("https"):
            conn.cert_reqs = "CERT_REQUIRED"
            conn.ca_certs = None
            conn.ca_cert_dir = None
            self._configure_client_cert(conn, cert)
        else:
            super().cert_verify(conn, url, verify, cert)

    def _configure_client_cert(self, conn: Any, cert: Any) -> None:
        if not cert:
            return

        if isinstance(cert, tuple):
            conn.cert_file = cert[0]
            conn.key_file = cert[1]
        else:
            conn.cert_file = cert
            conn.key_file = None

        if conn.cert_file and not os.path.exists(conn.cert_file):
            raise OSError(
                f"Could not find the TLS certificate file, invalid path: {conn.cert_file}"
            )
        if conn.key_file and not os.path.exists(conn.key_file):
            raise OSError(
                f"Could not find the TLS key file, invalid path: {conn.key_file}"
            )


class HttpClient(DeprecatedNamesMixin):
    verify = True
    timeout = 60
    # args are (upload_bytes_in_chunk, download_bytes_in_chunk)
    progress_hook: ProgressCallback | None = None

    def __init__(self, progress_hook: ProgressCallback | None = None) -> None:
        self.progress_hook = progress_hook
        self.session = requests.Session()
        self.session.mount("https://", SystemStoreHTTPAdapter())

    def __enter__(self) -> HttpClient:
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()

    def close(self) -> None:
        if self.session:
            self.session.close()
            self.session = None

    def __del__(self) -> None:
        self.close()

    def post(self, url: str, data: bytes, headers: dict[str, str] | None) -> Response:
        headers["User-Agent"] = self._agent_name()
        return self.session.post(
            url,
            data=data,
            headers=headers,
            stream=True,
            timeout=self.timeout,
            verify=self.verify,
        )  # pytype: disable=wrong-arg-types

    def get(self, url: str, headers: dict[str, str] | None = None) -> Response:
        if headers is None:
            headers = {}
        headers["User-Agent"] = self._agent_name()
        return self.session.get(
            url, stream=True, headers=headers, timeout=self.timeout, verify=self.verify
        )

    def stream_content(self, resp: Response) -> bytes:
        resp.raise_for_status()

        buf = io.BytesIO()
        for chunk in resp.iter_content(chunk_size=HTTP_BUF_SIZE):
            if self.progress_hook:
                self.progress_hook(0, len(chunk))
            buf.write(chunk)
        return buf.getvalue()

    def _agent_name(self) -> str:
        from anki.buildinfo import version

        return f"Anki {version}"


# allow user to accept invalid certs in work/school settings
if os.environ.get("ANKI_NOVERIFYSSL"):
    HttpClient.verify = False

    import warnings

    warnings.filterwarnings("ignore")
