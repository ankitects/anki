# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import io
import json
import os
import stat
import subprocess
import zipfile
from pathlib import Path
from types import SimpleNamespace
from typing import Any

import pytest

from tools import sonar

SHA = "a" * 40
REPO = "ankitects/anki"


def event(
    source_repo: str = "contributor/anki", kind: str = "pull_request"
) -> dict[str, Any]:
    return {
        "repository": {"full_name": REPO},
        "workflow_run": {
            "id": 42,
            "repository": {"full_name": REPO},
            "conclusion": "success",
            "event": kind,
            "head_sha": SHA,
            "head_branch": "feature/test",
            "head_repository": {"full_name": source_repo},
            "pull_requests": [],
        },
    }


def pull(source_repo: str = "contributor/anki") -> dict[str, Any]:
    return {
        "number": 123,
        "state": "open",
        "head": {"sha": SHA, "ref": "feature/test", "repo": {"full_name": source_repo}},
        "base": {"ref": "main", "repo": {"full_name": REPO}},
    }


def zipped(files: dict[str, bytes]) -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        for name, content in files.items():
            member = zipfile.ZipInfo(name)
            # Preserve malformed names instead of letting ZipInfo normalize them
            # on Windows or truncate them at NUL bytes when building the fixture.
            member.filename = name
            archive.writestr(member, content)
    return stream.getvalue()


@pytest.fixture(params=[("/", None), ("\\", "/")], ids=["posix", "windows"])
def zip_path_separators(
    request: pytest.FixtureRequest, monkeypatch: pytest.MonkeyPatch
) -> None:
    # Exercise both platforms' ZIP normalization without changing the OS module
    # used by pytest, pathlib, or the rest of the application.
    zip_os = SimpleNamespace(**vars(os))
    zip_os.sep, zip_os.altsep = request.param
    monkeypatch.setattr(zipfile, "os", zip_os)


def reports() -> dict[str, bytes]:
    xml = b'<coverage><sources><source>/old/workspace</source></sources><packages><package><classes><class filename="pylib/anki/example.py"><lines><line number="1" hits="1"/></lines></class></classes></package></packages></coverage>'
    return {
        "python-pylib/coverage.xml": xml,
        "python-qt/coverage.xml": xml,
        "typescript/lcov.info": b"SF:lib/example.ts\nDA:1,1\nend_of_record\n",
        "rust/lcov.info": b"SF:/home/runner/work/anki/anki/rslib/example.rs\nDA:1,1\nend_of_record\n",
    }


@pytest.mark.parametrize("source_repo", [REPO, "contributor/anki"])
@pytest.mark.parametrize("payload_number", [True, False])
def test_context_uses_api_for_internal_and_fork_prs(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    source_repo: str,
    payload_number: bool,
) -> None:
    payload = event(source_repo)
    if payload_number:
        payload["workflow_run"]["pull_requests"] = [{"number": 123}]
    responses = {
        f"repos/{REPO}/pulls/123": json.dumps(pull(source_repo)).encode(),
        f"repos/{REPO}/actions/runs/42/artifacts?per_page=100": json.dumps(
            {
                "artifacts": [
                    {
                        "id": 1,
                        "name": "sonar-pr-context",
                        "expired": False,
                        "size_in_bytes": 200,
                    },
                    {
                        "id": 2,
                        "name": "coverage-reports",
                        "expired": False,
                        "size_in_bytes": 2000,
                    },
                ]
            }
        ).encode(),
        f"repos/{REPO}/actions/artifacts/1/zip": zipped({"pr-number.txt": b"123\n"}),
        f"repos/{REPO}/actions/artifacts/2/zip": zipped(reports()),
    }
    calls = []

    def api(path: str) -> bytes:
        calls.append(path)
        return responses[path]

    monkeypatch.setattr(sonar, "api", api)
    context = sonar.prepare_context(payload, tmp_path / "inputs")
    assert context["ref"] == "refs/pull/123/head"
    assert context["sha"] == SHA
    assert context["base"] == "main"
    assert f"repos/{REPO}/pulls/123" in calls
    assert (f"repos/{REPO}/actions/artifacts/1/zip" in calls) != payload_number
    assert (tmp_path / "inputs/reports/typescript/lcov.info").is_file()


@pytest.mark.parametrize(
    "field,value",
    [
        ("number", 456),
        ("state", "closed"),
        ("head.sha", "b" * 40),
        ("head.ref", "different"),
        ("head.repo.full_name", "attacker/anki"),
        ("base.ref", "release/other"),
        ("base.repo.full_name", "other/anki"),
    ],
)
def test_rejects_mismatched_or_stale_pr(field: str, value: Any) -> None:
    pr = pull()
    current = pr
    parts = field.split(".")
    for part in parts[:-1]:
        current = current[part]
    current[parts[-1]] = value
    with pytest.raises(ValueError, match="does not match"):
        sonar.validate_context(event(), 123, pr)


@pytest.mark.parametrize("branch", ["main", "release/25.09"])
def test_push_context(branch: str) -> None:
    payload = event(REPO, "push")
    payload["workflow_run"]["head_branch"] = branch
    context = sonar.validate_context(payload, None, None)
    assert context["ref"] == SHA
    assert context["branch"] == branch
    assert "number" not in context


@pytest.mark.parametrize(
    "kind", ["pull_request", "workflow_dispatch", "pull_request_target"]
)
def test_never_falls_back_to_branch_analysis(kind: str) -> None:
    with pytest.raises(ValueError, match="Missing PR context"):
        sonar.validate_context(event(kind=kind), None, None)


def test_rejects_failed_ci_and_fork_push() -> None:
    payload = event()
    payload["workflow_run"]["conclusion"] = "failure"
    with pytest.raises(ValueError, match="CI run"):
        sonar.validate_context(payload, 123, pull())
    payload = event(kind="push")
    payload["workflow_run"]["head_branch"] = "main"
    with pytest.raises(ValueError, match="Unexpected push"):
        sonar.validate_context(payload, None, None)


@pytest.mark.parametrize(
    "value", ["", "0", "-1", "123\n456", "123;echo bad", "1" * 100]
)
def test_invalid_pr_number(value: str) -> None:
    with pytest.raises(ValueError, match="PR number"):
        sonar.pr_number(value)


@pytest.mark.parametrize(
    "name",
    [
        "../sonar-project.properties",
        "/tmp/settings",
        "rust/../../settings",
        "rust\\lcov.info",
        "rust/lcov.info\x00ignored",
    ],
)
def test_rejects_archive_path_traversal(name: str, zip_path_separators: None) -> None:
    data = zipped({name: b"bad"})
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        assert archive.infolist()[0].orig_filename == name
    with pytest.raises(ValueError, match="Unsafe artifact"):
        sonar.read_zip(data, {"rust/lcov.info"}, 64)


def test_accepts_archive_with_exact_names(zip_path_separators: None) -> None:
    files = {"rust/lcov.info": b"{}"}
    assert sonar.read_zip(zipped(files), set(files), 64) == files


def test_rejects_archive_symlink_duplicate_and_oversize() -> None:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        link = zipfile.ZipInfo("pr-number.txt")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        archive.writestr(link, "/proc/self/environ")
    with pytest.raises(ValueError, match="Unsafe artifact"):
        sonar.read_zip(stream.getvalue(), {"pr-number.txt"}, 64)
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr("pr-number.txt", "123")
        with pytest.warns(UserWarning):
            archive.writestr("pr-number.txt", "456")
    with pytest.raises(ValueError, match="duplicate"):
        sonar.read_zip(stream.getvalue(), {"pr-number.txt"}, 64)
    with pytest.raises(ValueError, match="Unsafe artifact"):
        sonar.read_zip(zipped({"pr-number.txt": b"1" * 65}), {"pr-number.txt"}, 64)


def test_rejects_missing_report() -> None:
    files = reports()
    del files["rust/lcov.info"]
    with pytest.raises(ValueError, match="members"):
        sonar.read_zip(zipped(files), sonar.REPORT_NAMES, sonar.MAX_REPORT_BYTES)


@pytest.mark.parametrize(
    "name",
    [
        "tools/.scannerwork/bridge.js",
        "ts/node_modules/evil/index.js",
        "tools/../secrets.py",
        "ts/eslint.config.js",
        "ts/tsconfig.json",
        "Cargo.toml",
        "sonar-project.properties",
        "tools/link\\outside.py",
    ],
)
def test_excludes_executable_configuration_and_unsafe_paths(name: str) -> None:
    assert not sonar.source_path(name)


def test_report_normalization() -> None:
    sources = {"pylib/anki/example.py", "rslib/example.rs", "ts/lib/example.ts"}
    files = reports()
    xml = sonar.normalize_report(
        "coverage.xml", files["python-pylib/coverage.xml"], REPO, sources
    )
    assert b"<source>.</source>" in xml
    lcov = sonar.normalize_report(
        "rust/lcov.info", files["rust/lcov.info"], REPO, sources
    )
    assert lcov.startswith(b"SF:rslib/example.rs\n")
    assert b"/home/runner" not in lcov
    typescript = sonar.normalize_report(
        "typescript/lcov.info", files["typescript/lcov.info"], REPO, sources
    )
    assert typescript.startswith(b"SF:ts/lib/example.ts\n")
    assert (
        sonar.normalize_report("rust/lcov.info", files["rust/lcov.info"], REPO, set())
        == b""
    )


def test_rejects_silently_empty_typescript_coverage() -> None:
    with pytest.raises(ValueError, match="no staged source paths"):
        sonar.normalize_report(
            "typescript/lcov.info",
            reports()["typescript/lcov.info"],
            REPO,
            {"ts/lib/other.ts"},
        )


def test_lcov_record_separator_is_not_a_function_name() -> None:
    data = b"SF:rslib/example.rs\nFN:1,end_of_record\nDA:1,1\nend_of_record\n"
    assert (
        sonar.normalize_report("rust/lcov.info", data, REPO, {"rslib/example.rs"})
        == data
    )


@pytest.mark.parametrize(
    "path", ["/etc/passwd", "../../secret", "C:\\secret", "/proc/self/environ"]
)
def test_rejects_report_paths_outside_checkout(path: str) -> None:
    with pytest.raises(ValueError, match="outside"):
        sonar.report_path(path, REPO)


def test_rejects_xml_entities() -> None:
    data = b'<!DOCTYPE coverage [<!ENTITY secret SYSTEM "file:///proc/self/environ">]><coverage>&secret;</coverage>'
    with pytest.raises(ValueError, match="entities"):
        sonar.normalize_report("coverage.xml", data, REPO, set())


def test_property_values_are_not_arguments_or_new_properties() -> None:
    assert sonar.property_value('feature/"quoted"') == 'feature/"quoted"'
    assert (
        sonar.property_value("branch\nsonar.host.url=evil")
        == r"branch\u000asonar.host.url\=evil"
    )
    assert sonar.property_value("feature/café") == r"feature/caf\u00e9"
    assert sonar.property_value("trailing\\") == "trailing\\\\"


@pytest.mark.skipif(
    os.name == "nt", reason="The Sonar snapshot runs on Linux and uses symlinks"
)
def test_snapshot_keeps_code_as_data_and_uses_trusted_config(tmp_path: Path) -> None:
    source = tmp_path / "source"
    source.mkdir()
    env = {**os.environ, "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull}

    def git(*args: str) -> bytes:
        return subprocess.check_output(["git", "-C", str(source), *args], env=env)

    git("init", "--template=", "--initial-branch=main")
    git("config", "user.name", "Test")
    git("config", "user.email", "test@example.invalid")
    canary = tmp_path / "executed"
    code = f"from pathlib import Path\nPath({str(canary)!r}).touch()\n"
    files = {
        "pylib/anki/example.py": code,
        "tools/sonar.py": code,
        "rslib/example.rs": "fn main() {}",
        "ts/lib/example.ts": "const value = 1;",
        "sonar-project.properties": "sonar.host.url=https://attacker.invalid",
        "Cargo.toml": "malicious manifest",
        "ts/eslint.config.js": "throw 1",
        "ts/node_modules/evil/index.js": "throw 1",
        "tools/.scannerwork/evil.js": "throw 1",
    }
    for name, content in files.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    (source / "pylib/anki/link.py").symlink_to(tmp_path / "secret")
    (source / "pylib/anki/example.py").chmod(0o755)
    git("add", ".")
    git("-c", f"core.hooksPath={os.devnull}", "commit", "-m", "fixture")
    sha = git("rev-parse", "HEAD").decode().strip()
    git("update-ref", "refs/remotes/origin/main", sha)
    inputs = tmp_path / "inputs"
    inputs.mkdir()
    context = sonar.validate_context(event(), 123, pull())
    context["sha"] = sha
    context["branch"] = 'feature/"quoted"'
    (inputs / "context.json").write_text(json.dumps(context))
    for name, report_content in reports().items():
        path = inputs / "reports" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(report_content)
    trusted = tmp_path / "trusted"
    trusted.mkdir()
    (trusted / "sonar-project.properties").write_text(
        "sonar.host.url=https://sonarcloud.io\nsonar.rust.clippy.enable=false\n"
    )
    destination = tmp_path / "scan"
    sonar.prepare_scan(source, destination, inputs, trusted)
    assert not canary.exists()
    assert (destination / "tools/sonar.py").read_text() == code
    assert not (destination / "pylib/anki/link.py").exists()
    assert not (destination / "pylib/anki/example.py").stat().st_mode & 0o111
    for name in (
        "Cargo.toml",
        "ts/eslint.config.js",
        "ts/node_modules",
        "tools/.scannerwork",
    ):
        assert not (destination / name).exists()
    properties = (destination / "sonar-project.properties").read_text()
    assert "attacker" not in properties
    assert "sonar.pullrequest.key=123" in properties
    assert 'sonar.pullrequest.branch=feature/"quoted"' in properties
    assert "sonar.branch.name=" not in properties
    assert (destination / ".git/HEAD").is_file()
    stale = copy.deepcopy(context)
    stale["sha"] = "b" * 40
    (inputs / "context.json").write_text(json.dumps(stale))
    with pytest.raises(ValueError, match="untested commit"):
        sonar.prepare_scan(source, tmp_path / "stale", inputs, trusted)
