from __future__ import annotations

import json
import os
import sqlite3
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Annotated

import typer
from typer._click import exceptions as click_exceptions


@dataclass
class State:
    json_output: bool = False
    base: Path | None = None
    profile: str | None = None


app = typer.Typer(
    add_completion=False,
    help="Local-first command line tools for Anki collections.",
)
profile_app = typer.Typer(help="Inspect local Anki profiles.")
deck_app = typer.Typer(help="Inspect decks.")
app.add_typer(profile_app, name="profile")
app.add_typer(deck_app, name="deck")


def _json(data: dict) -> None:
    typer.echo(json.dumps(data, separators=(",", ":")))


def _error_json(code: str, message: str) -> None:
    _json({"ok": False, "error": {"code": code, "message": message}})


def _error(state: State, code: str, message: str, exit_code: int = 1) -> None:
    if state.json_output:
        _error_json(code, message)
    else:
        typer.echo(f"anki-cli: {message}", err=True)
    raise typer.Exit(exit_code)


@app.callback()
def root(
    ctx: typer.Context,
    json_output: Annotated[
        bool,
        typer.Option(
            "--json",
            help="Emit machine-readable JSON.",
        ),
    ] = False,
    base: Annotated[
        Path | None,
        typer.Option(
            "--base",
            help="Anki base folder.",
            file_okay=False,
            dir_okay=True,
            resolve_path=False,
        ),
    ] = None,
    profile: Annotated[
        str | None,
        typer.Option(
            "--profile",
            help="Anki profile name.",
        ),
    ] = None,
) -> None:
    ctx.obj = State(json_output=json_output, base=base, profile=profile)


@profile_app.command("list")
def profile_list(ctx: typer.Context) -> None:
    state = command_state(ctx)
    base_folder = selected_base_folder(state)
    try:
        profiles = profile_names(base_folder)
    except RuntimeError as exc:
        _error(state, "profile_list_failed", str(exc))

    if state.json_output:
        _json({"ok": True, "base": str(base_folder), "profiles": profiles})
    else:
        for profile_name in profiles:
            typer.echo(profile_name)


@profile_app.command("status")
def profile_status(ctx: typer.Context) -> None:
    state = command_state(ctx)
    profile = require_profile(state, "profile status")
    base_folder = selected_base_folder(state)
    try:
        profiles = profile_names(base_folder)
    except RuntimeError as exc:
        _error(state, "profile_status_failed", str(exc))

    collection_path = profile_collection_path(base_folder, profile)
    profile_exists = profile in profiles
    collection_exists = collection_path.exists()
    try:
        locked = collection_locked(collection_path)
    except RuntimeError as exc:
        _error(state, "profile_status_failed", str(exc))

    if state.json_output:
        _json(
            {
                "ok": True,
                "base": str(base_folder),
                "profile": profile,
                "profile_exists": profile_exists,
                "collection_path": str(collection_path),
                "collection_exists": collection_exists,
                "locked": locked,
            }
        )
        return

    typer.echo(f"Profile: {profile}")
    typer.echo(f"Base: {base_folder}")
    typer.echo(f"Profile exists: {yes_no(profile_exists)}")
    typer.echo(f"Collection: {collection_path}")
    typer.echo(f"Collection exists: {yes_no(collection_exists)}")
    typer.echo(f"Locked: {yes_no(locked)}")


@deck_app.command("list")
def deck_list(ctx: typer.Context) -> None:
    state = command_state(ctx)
    base_folder, profile, collection_path = require_unlocked_collection(
        state,
        "deck list",
    )

    try:
        decks = deck_names(collection_path)
    except RuntimeError as exc:
        _error(state, "deck_list_failed", str(exc))

    if state.json_output:
        _json(
            {
                "ok": True,
                "base": str(base_folder),
                "profile": profile,
                "collection_path": str(collection_path),
                "decks": decks,
            }
        )
        return

    for deck in decks:
        typer.echo(deck["name"])


def require_profile(state: State, command: str) -> str:
    profile = state.profile
    if not profile:
        _error(
            state,
            "profile_required",
            f"`{command}` requires --profile NAME.",
            exit_code=2,
        )
    return profile


def require_unlocked_collection(state: State, command: str) -> tuple[Path, str, Path]:
    profile = require_profile(state, command)
    base_folder = selected_base_folder(state)
    collection_path = profile_collection_path(base_folder, profile)
    if not collection_path.exists():
        _error(
            state,
            "collection_missing",
            f"collection not found: {collection_path}",
        )

    try:
        locked = collection_locked(collection_path)
    except RuntimeError as exc:
        _error(state, f"{command.replace(' ', '_')}_failed", str(exc))
    if locked:
        _error(
            state,
            "collection_locked",
            "collection is locked; close Anki or wait for sync to finish.",
            exit_code=3,
        )

    return base_folder, profile, collection_path


def command_state(ctx: typer.Context) -> State:
    state = ctx.find_root().obj
    if isinstance(state, State):
        return state
    return State()


def default_base_folder() -> Path:
    if base := os.environ.get("ANKI_BASE"):
        return Path(base).expanduser()

    if sys.platform == "win32":
        appdata = os.environ.get("APPDATA")
        if appdata:
            return Path(appdata) / "Anki2"
        return Path.home() / "AppData" / "Roaming" / "Anki2"

    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "Anki2"

    xdg_data = os.environ.get("XDG_DATA_HOME")
    if xdg_data:
        return Path(xdg_data).expanduser() / "Anki2"
    return Path.home() / ".local" / "share" / "Anki2"


def selected_base_folder(state: State) -> Path:
    if state.base is not None:
        return state.base.expanduser()
    return default_base_folder()


def profile_collection_path(base_folder: Path, profile: str) -> Path:
    return base_folder / profile / "collection.anki2"


def profile_names(base_folder: Path) -> list[str]:
    prefs_path = base_folder / "prefs21.db"
    if not prefs_path.exists():
        return []

    try:
        with sqlite3.connect(f"file:{prefs_path}?mode=ro", uri=True) as db:
            rows = db.execute(
                "select name from profiles where name != '_global' order by name"
            )
            return [row[0] for row in rows]
    except sqlite3.Error as exc:
        raise RuntimeError(f"could not read {prefs_path}: {exc}") from exc


def deck_names(collection_path: Path) -> list[dict[str, int | str]]:
    try:
        from anki.collection import Collection
    except ImportError as exc:
        raise RuntimeError(f"could not import Anki collection backend: {exc}") from exc

    col = Collection(str(collection_path))
    try:
        return [
            {"id": int(deck.id), "name": str(deck.name)}
            for deck in col.decks.all_names_and_ids()
        ]
    finally:
        col.close(downgrade=False)


def collection_locked(collection_path: Path) -> bool:
    if not collection_path.exists():
        return False

    try:
        with sqlite3.connect(
            f"file:{collection_path}?mode=rw", uri=True, timeout=0
        ) as db:
            db.execute("pragma busy_timeout=0")
            db.execute("begin immediate")
            db.rollback()
        return False
    except sqlite3.OperationalError as exc:
        message = str(exc).lower()
        if "locked" in message or "busy" in message:
            return True
        raise RuntimeError(f"could not inspect {collection_path}: {exc}") from exc
    except sqlite3.Error as exc:
        raise RuntimeError(f"could not inspect {collection_path}: {exc}") from exc


def yes_no(value: bool) -> str:
    return "yes" if value else "no"


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    json_requested = "--json" in args

    try:
        result = app(args=args, standalone_mode=False)
    except click_exceptions.ClickException as exc:
        if json_requested:
            _error_json("usage_error", exc.format_message())
        else:
            exc.show()
        return exc.exit_code
    except click_exceptions.Abort:
        if json_requested:
            _error_json("aborted", "Aborted.")
        else:
            typer.echo("Aborted.", err=True)
        return 1
    except click_exceptions.Exit as exc:
        return int(exc.exit_code)

    if isinstance(result, int):
        return result
    return 0
