# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Prepare an Anki release by validating the version, checking CI status,
ensuring no duplicate tag/release exists, syncing translations, and
committing + pushing the updated .version file.

Usage:
    python3 .github/scripts/prepare_release.py <version> [--skip-ci-check]

Examples:
    python3 .github/scripts/prepare_release.py 26.06
    python3 .github/scripts/prepare_release.py 26.06b1 --skip-ci-check
"""

import argparse
import subprocess
import sys
from pathlib import Path

# Repo root is two levels up from this script (.github/scripts/)
REPO_ROOT = Path(__file__).resolve().parent.parent.parent


def run(args: list[str], *, cwd: Path = REPO_ROOT) -> str:
    """Run a command and return stdout as a string."""
    out = subprocess.check_output(args, cwd=cwd, text=True)
    return out.strip()


def check_ci_passed(commit_sha: str) -> None:
    print(f"Checking CI status for {commit_sha}...")
    output = run(
        [
            "gh",
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
    )
    if result.returncode == 0:
        print(f"Error: tag '{version}' already exists", file=sys.stderr)
        sys.exit(1)

    result = subprocess.run(
        ["gh", "release", "view", version],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
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
    print("Committed.")


def push_current_branch() -> None:
    branch = run(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    print(f"Pushing to origin/{branch}...")
    run(["git", "push", "origin", f"HEAD:refs/heads/{branch}"])
    print("Pushed.")


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

    # Validate version format and ensure it's newer than the current one.
    sys.path.insert(0, str(REPO_ROOT / ".github" / "scripts"))
    from validate_version import validate_version

    current_version = (REPO_ROOT / ".version").read_text().strip()
    print(f"Current .version: {current_version}")
    try:
        validate_version(args.version, current_version)
    except ValueError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    print(f"Version '{args.version}' is valid.")

    if not args.skip_ci_check:
        commit_sha = run(["git", "rev-parse", "HEAD"])
        check_ci_passed(commit_sha)
    else:
        print("Skipping CI check.")

    check_no_duplicate(args.version)

    # Sync translations (pulls latest from submodule repos, pushes updated templates).
    print("Syncing translations...")
    from sync_translations import sync

    sync()
    print("Translations synced.")

    update_version_and_commit(args.version)
    push_current_branch()

    print(f"\nDone. Release {args.version} is prepared.")


if __name__ == "__main__":
    main()
