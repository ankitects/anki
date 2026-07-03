# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html


def run_sync_server() -> None:
    import sys
    from os import environ as env

    from anki._backend import RustBackend

    env["RUST_LOG"] = env.get("RUST_LOG", "anki=info")

    try:
        RustBackend.syncserver()
    except Exception as exc:
        print("Sync server failed:", exc)
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    run_sync_server()
