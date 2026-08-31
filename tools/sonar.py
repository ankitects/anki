# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Trusted preparation for workflow_run scans. Never import or run PR code."""

import io
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any

# Keep aligned with sonar.sources/sonar.tests. Only these languages are staged;
# manifests, dependency trees, build outputs and scanner caches are not inputs.
SOURCE_ROOTS = (
    "pylib/anki",
    "qt/aqt",
    "qt/tools",
    "ts",
    "rslib",
    "tools",
    "pylib/tests",
    "qt/tests",
)
SOURCE_SUFFIXES = {
    ".py",
    ".pyi",
    ".rs",
    ".js",
    ".jsx",
    ".mjs",
    ".cjs",
    ".ts",
    ".tsx",
    ".css",
    ".scss",
    ".sass",
    ".html",
    ".vue",
    ".svelte",
}
REPORT_NAMES = {
    "python-pylib/coverage.xml",
    "python-qt/coverage.xml",
    "typescript/lcov.info",
    "rust/lcov.info",
    "rust/clippy.json",
}
MAX_REPORT_BYTES = 64 * 1024 * 1024
MAX_ARCHIVE_BYTES = 128 * 1024 * 1024


def api(path: str) -> bytes:
    # gh handles authentication and cross-host redirects for artifact downloads.
    # shell=False: no repository/branch/PR value is interpreted by a shell.
    return subprocess.check_output(["gh", "api", path])


def read_zip(data: bytes, names: set[str], limit: int) -> dict[str, bytes]:
    """Read only exact allowlisted regular members; never extract an archive."""
    if len(data) > MAX_ARCHIVE_BYTES:
        raise ValueError("Artifact archive is too large")
    result = {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        members = archive.infolist()
        if len(members) != len(names):
            raise ValueError("Unexpected or duplicate artifact members")
        if sum(member.file_size for member in members) > MAX_ARCHIVE_BYTES:
            raise ValueError("Expanded artifact is too large")
        for member in members:
            kind = stat.S_IFMT(member.external_attr >> 16)
            if (
                member.filename not in names
                or member.filename in result
                or kind not in (0, stat.S_IFREG)
                or member.file_size > limit
            ):
                raise ValueError("Unsafe artifact member")
            with archive.open(member) as stream:
                content = stream.read(limit + 1)
            if len(content) > limit:
                raise ValueError("Expanded artifact member is too large")
            result[member.filename] = content
    if result.keys() != names:
        raise ValueError("Missing artifact member")
    return result


def artifact(
    repo: str, run_id: int, name: str, names: set[str], limit: int
) -> dict[str, bytes]:
    listing = json.loads(
        api(f"repos/{repo}/actions/runs/{run_id}/artifacts?per_page=100")
    )
    matches = [item for item in listing["artifacts"] if item["name"] == name]
    if len(matches) != 1 or matches[0]["expired"]:
        raise ValueError("Required CI artifact is missing, expired or ambiguous")
    item = matches[0]
    if item["size_in_bytes"] > MAX_ARCHIVE_BYTES:
        raise ValueError("CI artifact is too large")
    artifact_id = int(item["id"])
    return read_zip(
        api(f"repos/{repo}/actions/artifacts/{artifact_id}/zip"), names, limit
    )


def pr_number(value: str) -> int:
    if not re.fullmatch(r"[1-9][0-9]{0,9}", value.strip()):
        raise ValueError("Invalid PR number")
    return int(value.strip())


def validate_context(
    event: dict[str, Any], number: int | None, pr: dict[str, Any] | None
) -> dict[str, Any]:
    run = event["workflow_run"]
    repo = event["repository"]["full_name"]
    sha = run["head_sha"]
    if (
        run["conclusion"] != "success"
        or run["repository"]["full_name"] != repo
        or not re.fullmatch(r"[0-9a-f]{40}", sha)
    ):
        raise ValueError("Unexpected CI run identity or conclusion")
    context = {"sha": sha, "repository": repo}
    if run["event"] == "push":
        branch = run["head_branch"]
        if run["head_repository"]["full_name"] != repo or not (
            branch == "main" or branch.startswith("release/")
        ):
            raise ValueError("Unexpected push repository or branch")
        return {**context, "ref": sha, "branch": branch}
    if run["event"] != "pull_request" or not pr or not number:
        raise ValueError("Missing PR context; refusing branch analysis")
    if (
        pr["number"] != number
        or pr["state"] != "open"
        or pr["head"]["sha"] != sha
        or pr["head"]["repo"]["full_name"] != run["head_repository"]["full_name"]
        or pr["head"]["ref"] != run["head_branch"]
        or pr["base"]["repo"]["full_name"] != repo
        or pr["base"]["ref"] != "main"
    ):
        raise ValueError(
            "PR does not match the successful CI run, or is no longer open"
        )
    return {
        **context,
        "ref": f"refs/pull/{number}/head",
        "number": number,
        "branch": pr["head"]["ref"],
        "base": pr["base"]["ref"],
    }


def prepare_context(event: dict[str, Any], destination: Path) -> dict[str, Any]:
    repo = event["repository"]["full_name"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise ValueError("Invalid repository name")
    run = event["workflow_run"]
    run_id = int(run["id"])
    number = None
    pr = None
    if run["event"] == "pull_request":
        candidates = run.get("pull_requests", [])
        if candidates:
            number = pr_number(str(candidates[0]["number"]))
        else:
            files = artifact(repo, run_id, "sonar-pr-context", {"pr-number.txt"}, 64)
            number = pr_number(files["pr-number.txt"].decode("ascii"))
        pr = json.loads(api(f"repos/{repo}/pulls/{number}"))
    context = validate_context(event, number, pr)
    reports = artifact(repo, run_id, "coverage-reports", REPORT_NAMES, MAX_REPORT_BYTES)
    # A fresh directory outside both checkouts. Artifact filenames never choose
    # where anything is written: only REPORT_NAMES are used as destinations.
    destination.mkdir()
    for name in REPORT_NAMES:
        path = destination / "reports" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(reports[name])
    (destination / "context.json").write_text(json.dumps(context), encoding="utf-8")
    return context


def source_path(name: str) -> bool:
    path = PurePosixPath(name)
    return (
        not path.is_absolute()
        and not any(
            part.startswith(".") or part in {"node_modules", "out", "target"}
            for part in path.parts
        )
        and "\\" not in name
        and not any(ord(char) < 32 for char in name)
        and any(name.startswith(root + "/") for root in SOURCE_ROOTS)
        and path.suffix in SOURCE_SUFFIXES
        and ".config." not in path.name
    )


def stage_sources(
    source: Path, destination: Path, sha: str, base: str | None
) -> set[str]:
    def git(*args: str) -> bytes:
        return subprocess.check_output(["git", "-C", str(source), *args])

    if git("rev-parse", "HEAD").decode().strip() != sha:
        raise ValueError(
            "PR changed while checking out; refusing to scan an untested commit"
        )
    if base:
        git("rev-parse", "--verify", f"refs/remotes/origin/{base}")
    destination.mkdir()
    for root in SOURCE_ROOTS:
        (destination / root).mkdir(parents=True, exist_ok=True)
    copied = set()
    for entry in git("ls-tree", "-rz", "--full-tree", "HEAD").split(b"\0"):
        if not entry:
            continue
        metadata, raw_name = entry.split(b"\t", 1)
        mode, kind, _ = metadata.split()
        name = raw_name.decode("utf-8")
        if (
            mode not in (b"100644", b"100755")
            or kind != b"blob"
            or not source_path(name)
        ):
            continue
        original = source / name
        if original.is_symlink() or not original.resolve().is_relative_to(
            source.resolve()
        ):
            raise ValueError("Source path escapes checkout")
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        # Do not copy executable permissions or follow symlinks.
        shutil.copyfile(original, target, follow_symlinks=False)
        copied.add(name)
    # Only Git metadata created by actions/checkout, never an uploaded .git.
    shutil.copytree(source / ".git", destination / ".git")
    return copied


def report_path(value: str, repository: str) -> str:
    # The reports come from check-linux's root checkout, not the scan checkout.
    name = repository.split("/")[1]
    prefix = f"/home/runner/work/{name}/{name}/"
    value = value.removeprefix(prefix)
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "\\" in value or ":" in value:
        raise ValueError("Report refers to a path outside the CI checkout")
    return str(path)


def normalize_report(
    name: str, data: bytes, repository: str, sources: set[str]
) -> bytes:
    text = data.decode("utf-8-sig")
    if name.endswith(".xml"):
        if "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper():
            raise ValueError(
                "XML declarations/entities are not allowed in coverage reports"
            )
        root = ET.fromstring(text)
        if root.tag != "coverage":
            raise ValueError("Expected a Cobertura coverage report")
        for element in root.findall("sources/source"):
            element.text = "."
        for classes in root.findall("packages/package/classes"):
            for item in list(classes):
                filename = report_path(item.attrib["filename"], repository)
                if filename not in sources:
                    classes.remove(item)
                else:
                    item.set("filename", filename)
        return ET.tostring(root, encoding="utf-8", xml_declaration=True)
    if name.endswith(".info"):
        records = []
        for record in re.split(r"(?m)^end_of_record\r?$", text):
            lines = record.strip().splitlines()
            filenames = [line[3:] for line in lines if line.startswith("SF:")]
            if not lines:
                continue
            if len(filenames) != 1:
                raise ValueError("Malformed LCOV record")
            filename = report_path(filenames[0], repository)
            if filename in sources:
                records.append(
                    "\n".join(
                        f"SF:{filename}" if line.startswith("SF:") else line
                        for line in lines
                    )
                )
        return "".join(record + "\nend_of_record\n" for record in records).encode()
    # Cargo emits JSON lines. Only diagnostics are relevant, not executable paths
    # or environment values in compiler-artifact/build-script-executed records.
    diagnostics = []
    for line in text.splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        if item.get("reason") != "compiler-message":
            continue
        normalize_clippy_paths(item, repository)
        spans = item["message"]["spans"]
        if any(span["file_name"] in sources for span in spans):
            diagnostics.append(json.dumps(item))
    return ("\n".join(diagnostics) + "\n").encode()


def normalize_clippy_paths(value: Any, repository: str) -> None:
    # Also validate secondary locations, macro expansions and target paths.
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {"file_name", "src_path"} and isinstance(child, str):
                value[key] = report_path(child, repository)
            else:
                normalize_clippy_paths(child, repository)
    elif isinstance(value, list):
        for child in value:
            normalize_clippy_paths(child, repository)


def property_value(value: str) -> str:
    """Encode Java properties, without shell/CLI argument parsing of PR refs."""
    escaped = []
    units = value.encode("utf-16-be")
    for offset in range(0, len(units), 2):
        code = int.from_bytes(units[offset : offset + 2], "big")
        char = chr(code)
        if code < 32 or code > 126:
            escaped.append(f"\\u{code:04x}")
        else:
            escaped.append(("\\" if char in "\\ =:#!" else "") + char)
    return "".join(escaped)


def prepare_scan(source: Path, destination: Path, inputs: Path, trusted: Path) -> None:
    context = json.loads((inputs / "context.json").read_text())
    sources = stage_sources(source, destination, context["sha"], context.get("base"))
    for name in REPORT_NAMES:
        report = destination / "out/coverage" / name
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_bytes(
            normalize_report(
                name,
                (inputs / "reports" / name).read_bytes(),
                context["repository"],
                sources,
            )
        )
    # No PR tsconfig, extends, plugins, package.json, node_modules, or Svelte build.
    tsconfig = {
        "compilerOptions": {
            "target": "ES2020",
            "module": "ESNext",
            "moduleResolution": "node",
            "allowJs": True,
            "noEmit": True,
            "skipLibCheck": True,
            "baseUrl": ".",
            "paths": {"$lib/*": ["ts/lib/*"], "@tslib/*": ["ts/lib/tslib/*"]},
        },
        "include": ["ts/**/*.ts", "ts/**/*.tsx", "ts/**/*.js", "ts/**/*.jsx"],
    }
    (destination / "tsconfig.sonar.json").write_text(
        json.dumps(tsconfig), encoding="utf-8"
    )
    settings = {"sonar.scm.revision": context["sha"]}
    if "number" in context:
        settings.update(
            {
                "sonar.pullrequest.key": str(context["number"]),
                "sonar.pullrequest.branch": context["branch"],
                "sonar.pullrequest.base": context["base"],
            }
        )
    else:
        settings["sonar.branch.name"] = context["branch"]
    properties = (trusted / "sonar-project.properties").read_text(encoding="utf-8")
    properties += (
        "\n"
        + "\n".join(f"{key}={property_value(value)}" for key, value in settings.items())
        + "\n"
    )
    (destination / "sonar-project.properties").write_text(properties, encoding="utf-8")


def main() -> None:
    inputs = Path(os.environ["RUNNER_TEMP"]) / "sonar-inputs"
    if sys.argv[1:] == ["context"]:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        context = prepare_context(event, inputs)
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f"ref={context['ref']}\n")
    elif sys.argv[1:] == ["prepare"]:
        workspace = Path(os.environ["GITHUB_WORKSPACE"])
        prepare_scan(workspace / "source", workspace / "scan", inputs, Path.cwd())
    else:
        raise ValueError("Use just sonar-context or just sonar-prepare")


if __name__ == "__main__":
    main()
