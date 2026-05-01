# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Update commit references to the latest translations,
and copy source files to the translation repos.

Rewritten based on `./ninja ftl-sync` for CI.
"""

import os
import subprocess
from dataclasses import dataclass


@dataclass
class Module:
    template_folder: str
    translation_repo: str


def sync() -> None:
    modules = [
        Module("ftl/core", "ftl/core-repo/core"),
        Module("ftl/qt", "ftl/qt-repo/desktop"),
    ]
    check_clean()
    for module in modules:
        fetch_new_translations(module)
        # FIXME: commented out to avoid pushing launcher.ftl for now
        # push_new_templates(module)
    commit(".", "Update translations")
    push(".")


def check_clean() -> None:
    out = subprocess.check_output(["git", "status", "--porcelain"])
    if out:
        raise Exception("please commit any outstanding changes first")


def fetch_new_translations(module: Module) -> None:
    subprocess.check_call(["git", "checkout", "main"], cwd=module.translation_repo)
    subprocess.check_call(["git", "pull", "origin", "main"], cwd=module.translation_repo)


def push_new_templates(module: Module) -> None:
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
        commit(module.translation_repo, "Update templates")
        push(module.translation_repo)


def push(repo: str) -> None:
    if os.environ.get("ANKI_NO_GIT_PUSH", "0") == "1":
        print("Skipping git push")
    else:
        subprocess.check_call(["git", "push", "origin", "main"], cwd=repo)


def commit(folder: str, message: str) -> None:
    subprocess.check_call(["git", "add", "ftl/"], cwd=folder)
    result = subprocess.run(
        ["git", "commit", "-m", message],
        cwd=folder,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if result.returncode == 0:
        return
    if "nothing to commit" in result.stdout:
        print(f"::notice::No changes to commit in {folder}")
    else:
        raise Exception(f"git commit failed in {folder}: {result.stdout}")


if __name__ == "__main__":
    sync()
