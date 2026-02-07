# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html


from anki._backend import RustBackend


def run_api_server(backend: RustBackend) -> None:
    from os import environ as env

    env["RUST_LOG"] = env.get("RUST_LOG", "anki=info")

    try:
        backend.api_server()
    except Exception as exc:
        print("API server failed:", exc)
