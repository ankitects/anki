from __future__ import annotations

import json
import os
import subprocess
import sqlite3
import sys
import tomllib
import types
from pathlib import Path

import anki.cli.main as cli_main
from anki.cli.main import main


def write_prefs_db(base: Path, profiles: list[str]) -> None:
    base.mkdir(exist_ok=True)
    with sqlite3.connect(base / "prefs21.db") as db:
        db.execute(
            """
            create table profiles (
              name text primary key collate nocase,
              data blob not null
            )
            """
        )
        db.execute("insert into profiles values ('_global', X'00')")
        for profile in profiles:
            db.execute("insert into profiles values (?, X'00')", (profile,))


def write_empty_collection_file(base: Path, profile: str) -> Path:
    collection_path = base / profile / "collection.anki2"
    collection_path.parent.mkdir()
    collection_path.touch()
    return collection_path


def test_help_lists_only_implemented_command_groups(capsys):
    assert main(["--help"]) == 0

    out = capsys.readouterr().out
    assert "Local-first command line tools" in out
    assert "profile" in out
    assert "deck" in out
    assert "note" not in out
    assert "--json" in out
    assert "--base" in out
    assert "--profile" in out


def test_profile_list_outputs_names(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1", "Japanese"])

    assert main(["--base", str(tmp_path), "profile", "list"]) == 0

    assert capsys.readouterr().out.splitlines() == ["Japanese", "User 1"]


def test_profile_list_outputs_json(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])

    assert main(["--json", "--base", str(tmp_path), "profile", "list"]) == 0

    assert json.loads(capsys.readouterr().out) == {
        "ok": True,
        "base": str(tmp_path),
        "profiles": ["User 1"],
    }


def test_profile_list_missing_base_returns_empty_list(capsys, tmp_path: Path):
    missing_base = tmp_path / "missing"

    assert main(["--json", "--base", str(missing_base), "profile", "list"]) == 0

    assert json.loads(capsys.readouterr().out)["profiles"] == []


def test_profile_status_outputs_json(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])
    collection_path = tmp_path / "User 1" / "collection.anki2"
    collection_path.parent.mkdir()
    with sqlite3.connect(collection_path):
        pass

    assert (
        main(
            [
                "--json",
                "--base",
                str(tmp_path),
                "--profile",
                "User 1",
                "profile",
                "status",
            ]
        )
        == 0
    )

    assert json.loads(capsys.readouterr().out) == {
        "ok": True,
        "base": str(tmp_path),
        "profile": "User 1",
        "profile_exists": True,
        "collection_path": str(collection_path),
        "collection_exists": True,
        "locked": False,
    }


def test_profile_status_reports_missing_collection(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])

    assert (
        main(
            [
                "--json",
                "--base",
                str(tmp_path),
                "--profile",
                "User 1",
                "profile",
                "status",
            ]
        )
        == 0
    )

    out = json.loads(capsys.readouterr().out)
    assert out["profile_exists"] is True
    assert out["collection_exists"] is False
    assert out["locked"] is False


def test_profile_status_reports_locked_collection(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])
    collection_path = tmp_path / "User 1" / "collection.anki2"
    collection_path.parent.mkdir()

    holder = sqlite3.connect(collection_path, timeout=0)
    try:
        holder.execute("create table if not exists t (id integer)")
        holder.execute("begin exclusive")

        assert (
            main(
                [
                    "--json",
                    "--base",
                    str(tmp_path),
                    "--profile",
                    "User 1",
                    "profile",
                    "status",
                ]
            )
            == 0
        )
    finally:
        holder.rollback()
        holder.close()

    assert json.loads(capsys.readouterr().out)["locked"] is True


def test_profile_status_requires_profile(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])

    assert main(["--json", "--base", str(tmp_path), "profile", "status"]) == 2

    captured = capsys.readouterr()
    assert captured.err == ""
    assert json.loads(captured.out)["error"]["code"] == "profile_required"


def test_deck_list_outputs_names(capsys, monkeypatch, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])
    collection_path = write_empty_collection_file(tmp_path, "User 1")

    def deck_names(path: Path) -> list[dict[str, int | str]]:
        assert path == collection_path
        return [{"id": 1, "name": "Default"}, {"id": 123, "name": "Parent::Child"}]

    monkeypatch.setattr(cli_main, "deck_names", deck_names)

    assert (
        main(["--base", str(tmp_path), "--profile", "User 1", "deck", "list"])
        == 0
    )

    assert capsys.readouterr().out.splitlines() == ["Default", "Parent::Child"]


def test_deck_list_outputs_json(capsys, monkeypatch, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])
    collection_path = write_empty_collection_file(tmp_path, "User 1")
    monkeypatch.setattr(
        cli_main,
        "deck_names",
        lambda path: [{"id": 1, "name": "Default"}],
    )

    assert (
        main(
            [
                "--json",
                "--base",
                str(tmp_path),
                "--profile",
                "User 1",
                "deck",
                "list",
            ]
        )
        == 0
    )

    assert json.loads(capsys.readouterr().out) == {
        "ok": True,
        "base": str(tmp_path),
        "profile": "User 1",
        "collection_path": str(collection_path),
        "decks": [{"id": 1, "name": "Default"}],
    }


def test_deck_list_requires_profile(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])

    assert main(["--json", "--base", str(tmp_path), "deck", "list"]) == 2

    captured = capsys.readouterr()
    assert captured.err == ""
    assert json.loads(captured.out)["error"]["code"] == "profile_required"


def test_deck_list_reports_missing_collection(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])

    assert (
        main(
            ["--json", "--base", str(tmp_path), "--profile", "User 1", "deck", "list"]
        )
        == 1
    )

    captured = capsys.readouterr()
    assert captured.err == ""
    assert json.loads(captured.out)["error"]["code"] == "collection_missing"


def test_deck_list_reports_locked_collection(capsys, tmp_path: Path):
    write_prefs_db(tmp_path, ["User 1"])
    collection_path = tmp_path / "User 1" / "collection.anki2"
    collection_path.parent.mkdir()

    holder = sqlite3.connect(collection_path, timeout=0)
    try:
        holder.execute("create table if not exists t (id integer)")
        holder.execute("begin exclusive")

        assert (
            main(
                [
                    "--json",
                    "--base",
                    str(tmp_path),
                    "--profile",
                    "User 1",
                    "deck",
                    "list",
                ]
            )
            == 3
        )
    finally:
        holder.rollback()
        holder.close()

    captured = capsys.readouterr()
    assert captured.err == ""
    assert json.loads(captured.out)["error"]["code"] == "collection_locked"


def test_deck_names_uses_collection_api(monkeypatch, tmp_path: Path):
    opened_paths = []
    closed = []

    class FakeDeck:
        id = 1
        name = "Default"

    class FakeDecks:
        def all_names_and_ids(self):
            return [FakeDeck()]

    class FakeCollection:
        def __init__(self, path: str):
            opened_paths.append(path)
            self.decks = FakeDecks()

        def close(self, downgrade: bool = False) -> None:
            closed.append(downgrade)

    module = types.ModuleType("anki.collection")
    module.Collection = FakeCollection
    monkeypatch.setitem(sys.modules, "anki.collection", module)

    collection_path = tmp_path / "collection.anki2"

    assert cli_main.deck_names(collection_path) == [{"id": 1, "name": "Default"}]
    assert opened_paths == [str(collection_path)]
    assert closed == [False]


def test_other_commands_are_not_exposed(capsys):
    assert main(["--json", "note", "list"]) == 2

    captured = capsys.readouterr()
    assert captured.err == ""
    assert json.loads(captured.out)["error"]["code"] == "usage_error"


def test_package_exposes_console_script():
    pyproject = Path(__file__).parents[1] / "pyproject.toml"
    data = tomllib.loads(pyproject.read_text())

    assert data["project"]["scripts"]["anki-cli"] == "anki.cli.main:main"
    assert any(
        dependency.partition(";")[0].strip().startswith("typer")
        for dependency in data["project"]["dependencies"]
    )


def test_package_entry_point_runs_help():
    env = os.environ.copy()
    pylib = str(Path(__file__).parents[1])
    if pythonpath := env.get("PYTHONPATH"):
        env["PYTHONPATH"] = f"{pylib}{os.pathsep}{pythonpath}"
    else:
        env["PYTHONPATH"] = pylib

    result = subprocess.run(
        [sys.executable, "-m", "anki.cli", "--help"],
        capture_output=True,
        env=env,
        text=True,
    )

    assert result.returncode == 0
    assert "profile" in result.stdout
    assert "deck" in result.stdout
