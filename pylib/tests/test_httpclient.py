# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from types import SimpleNamespace

import pytest
import requests

from anki.httpclient import HttpClient, _SystemStoreHTTPAdapter


def test_http_client_mounts_system_store_adapter() -> None:
    client = HttpClient()
    try:
        adapter = client.session.get_adapter("https://sync.ankiweb.net/")
        assert isinstance(adapter, _SystemStoreHTTPAdapter)
    finally:
        client.close()


def test_system_store_adapter_does_not_use_certifi_for_default_verification(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def fail_if_parent_verification_is_used(*_args, **_kwargs) -> None:
        raise AssertionError("requests' default certifi verification should be skipped")

    monkeypatch.setattr(
        requests.adapters.HTTPAdapter,
        "cert_verify",
        fail_if_parent_verification_is_used,
    )

    conn = SimpleNamespace(cert_reqs=None, ca_certs="certifi.pem", ca_cert_dir="certs")
    _SystemStoreHTTPAdapter().cert_verify(
        conn, "https://sync.ankiweb.net/", verify=True, cert=None
    )

    assert conn.cert_reqs == "CERT_REQUIRED"
    assert conn.ca_certs is None
    assert conn.ca_cert_dir is None
