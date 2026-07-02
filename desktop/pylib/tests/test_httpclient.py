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


def test_noverifyssl_disables_ssl_verification(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import importlib
    import warnings

    from urllib3.exceptions import InsecureRequestWarning

    import anki.httpclient as httpclient_module

    original_verify = httpclient_module.HttpClient.verify
    monkeypatch.setenv("ANKI_NOVERIFYSSL", "1")

    try:
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            importlib.reload(httpclient_module)
            warnings.warn("test", InsecureRequestWarning)
            warnings.warn("test", DeprecationWarning)
        assert httpclient_module.HttpClient.verify is False
    finally:
        httpclient_module.HttpClient.verify = original_verify
    categories = [w.category for w in caught]
    assert InsecureRequestWarning not in categories
    assert DeprecationWarning in categories
