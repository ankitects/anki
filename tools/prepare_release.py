# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Prepare an Anki release by validating the version, checking CI status,
ensuring no duplicate tag/release exists, syncing translations, and
committing + pushing the updated .version file.

Usage:
    python3 tools/prepare_release.py <version> [--skip-ci-check]

Examples:
    python3 tools/prepare_release.py 26.06
    python3 tools/prepare_release.py 26.06b1 --skip-ci-check
"""

import argparse
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
GH_REPO = ["--repo", "ankitects/anki"]


@dataclass
class TranslationModule:
    template_folder: str
    translation_repo: str


def run(args: list[str], *, cwd: Path = REPO_ROOT) -> str:
    """Run a command and return stdout as a string."""
    out = subprocess.check_output(args, cwd=cwd, text=True)
    return out.strip()


def check_clean() -> None:
    out = subprocess.check_output(["git", "status", "--porcelain"])
    if out:
        raise Exception("please commit any outstanding changes first")


def commit(folder: str, message: str, pathspec: str) -> None:
    subprocess.check_call(["git", "add", pathspec], cwd=folder)
    result = subprocess.run(
        ["git", "commit", "-m", message],
        cwd=folder,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        return
    if "nothing to commit" in result.stdout:
        print(f"No changes to commit in {folder}")
    else:
        raise Exception(f"git commit failed in {folder}: {result.stdout}")


def fetch_new_translations(module: TranslationModule) -> None:
    subprocess.check_call(["git", "checkout", "main"], cwd=module.translation_repo)
    subprocess.check_call(
        ["git", "pull", "origin", "main"], cwd=module.translation_repo
    )


def push_new_templates(module: TranslationModule) -> None:
    subprocess.check_call(
        [
            "rsync",
            "-ai",
            "--delete",
            "--no-perms",
            "--no-times",
            "-c",
            f"{module.template_folder}/",
            f"{module.translation_repo}/templates/",
        ]
    )
    changes_pending = subprocess.Popen(
        ["git", "diff", "--exit-code"], cwd=module.translation_repo
    ).wait()
    if changes_pending:
        commit(module.translation_repo, "Update templates", "templates/")
        subprocess.check_call(
            ["git", "push", "origin", "main"], cwd=module.translation_repo
        )


def sync_translations() -> None:
    modules = [
        TranslationModule("ftl/core", "ftl/core-repo/core"),
        TranslationModule("ftl/qt", "ftl/qt-repo/desktop"),
    ]
    for module in modules:
        fetch_new_translations(module)
        push_new_templates(module)
    commit(".", "Update translations", "ftl/")


def check_ci_passed(commit_sha: str) -> None:
    print(f"Checking CI status for {commit_sha}...")
    output = run(
        [
            "gh",
            *GH_REPO,
            "run",
            "list",
            "--workflow=ci.yml",
            "--commit",
            commit_sha,
            "--limit",
            "5",
            "--json",
            "conclusion,event",
            "--jq",
            '[.[] | select(.event == "push" or .event == "workflow_dispatch")][0].conclusion',
        ]
    )
    if not output:
        print(
            f"Error: could not determine CI status for commit {commit_sha}",
            file=sys.stderr,
        )
        sys.exit(1)
    if output != "success":
        print(
            f"Error: CI for commit {commit_sha} concluded with '{output}'",
            file=sys.stderr,
        )
        sys.exit(1)
    print("CI check passed.")


def check_no_duplicate(version: str) -> None:
    print(f"Checking for duplicate tag or release '{version}'...")
    run(["git", "fetch", "--tags", "origin"])

    result = subprocess.run(
        ["git", "rev-parse", f"refs/tags/{version}"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        print(f"Error: tag '{version}' already exists", file=sys.stderr)
        sys.exit(1)

    result = subprocess.run(
        ["gh", *GH_REPO, "release", "view", version],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        print(f"Error: GitHub release '{version}' already exists", file=sys.stderr)
        sys.exit(1)

    print("No duplicate tag or release found.")


def update_version_and_commit(version: str) -> None:
    print(f"Writing '{version}' to .version and committing...")
    (REPO_ROOT / ".version").write_text(version)
    run(["git", "add", ".version"])
    run(["git", "commit", "-m", f"Prepare release {version}"])


def push_branch(branch: str) -> None:
    print(f"Pushing to origin/{branch}...")
    run(["git", "push", "origin", f"HEAD:refs/heads/{branch}"])


def release_branch(version_str: str) -> str:
    """Return the release branch corresponding to a version, e.g. 26.08b1 -> release/26.08"""
    from packaging.version import Version

    version = Version(version_str)
    release = [str(p) for p in Version(version.base_version).release]
    release[1] = release[1].zfill(2)
    return "release/" + ".".join(release)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare an Anki release commit.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "version",
        help="Version string, e.g. 26.04, 26.04b1, 26.04rc1",
    )
    parser.add_argument(
        "--skip-ci-check",
        action="store_true",
        help="Skip the CI status check (for hotfix releases from non-main branches)",
    )
    args = parser.parse_args()

    os.chdir(REPO_ROOT)
    check_clean()

    # Validate version format and ensure it's newer than the current one.
    sys.path.insert(0, str(REPO_ROOT))
    from tools.validate_version import validate_version

    current_version = (REPO_ROOT / ".version").read_text().strip()
    print(f"Current .version: {current_version}")
    try:
        validate_version(args.version, current_version)
    except ValueError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    print(f"Version '{args.version}' is valid.")

    branch = release_branch(args.version)
    run(["git", "checkout", branch])

    if not args.skip_ci_check:
        commit_sha = run(["git", "rev-parse", "HEAD"])
        check_ci_passed(commit_sha)
    else:
        print("Skipping CI check.")

    check_no_duplicate(args.version)

    # Sync translations (pulls latest from submodule repos, pushes updated templates).
    print("Syncing translations...")
    sync_translations()
    print("Translations synced.")

    update_version_and_commit(args.version)
    push_branch(branch)

    print(f"Done. Release {args.version} is prepared.")


if __name__ == "__main__":
    main()
