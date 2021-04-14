# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# A helper script to update commit references to the latest translations,
# and copy source files to the translation repos. Requires access to the
# i18n repos to run.

import os
import re
import subprocess
import sys
from dataclasses import dataclass
from hashlib import sha256
from typing import Optional, Tuple

import requests

root = os.environ["BUILD_WORKSPACE_DIRECTORY"]
repos_bzl = os.path.join(root, "repos.bzl")
working_folder = os.path.join(root, "..", "anki-i18n")

if not os.path.exists(working_folder):
    os.mkdir(working_folder)


@dataclass
class Module:
    name: str
    repo: str
    # (source ftl folder, i18n templates folder)
    ftl: Optional[Tuple[str, str]] = None

    def folder(self) -> str:
        return os.path.join(working_folder, self.name)


modules = [
    Module(
        name="core",
        repo="git@github.com:ankitects/anki-core-i18n",
        ftl=(os.path.join(root, "ftl", "core"), "core/templates"),
    ),
    Module(
        name="qtftl",
        repo="git@github.com:ankitects/anki-desktop-ftl",
        ftl=(os.path.join(root, "ftl", "qt"), "desktop/templates"),
    ),
]


def update_repo(module: Module):
    subprocess.run(["git", "pull"], cwd=module.folder(), check=True)


def clone_repo(module: Module):
    subprocess.run(
        ["git", "clone", module.repo, module.name], cwd=working_folder, check=True
    )


def update_git_repos():
    for module in modules:
        if os.path.exists(module.folder()):
            update_repo(module)
        else:
            clone_repo(module)


@dataclass
class GitInfo:
    sha1: str
    zip_sha256: str


def git_url_to_zip_url(repo: str, commit: str) -> str:
    repo = repo.replace("git@github.com:", "https://github.com/")
    return f"{repo}/archive/{commit}.zip"


def get_zip_sha(zip_url: str) -> str:
    resp = requests.get(zip_url)
    resp.raise_for_status()
    return sha256(resp.content).hexdigest()


def module_git_info(module: Module) -> GitInfo:
    folder = module.folder()
    sha1 = subprocess.check_output(
        ["git", "log", "-n", "1", "--pretty=format:%H"], cwd=folder
    ).decode("utf8")
    zip_url = git_url_to_zip_url(module.repo, sha1)
    zip_sha = get_zip_sha(zip_url)

    return GitInfo(
        sha1=sha1,
        zip_sha256=zip_sha,
    )


def update_repos_bzl():
    # gather changes
    entries = {}
    for module in modules:
        git = module_git_info(module)
        prefix = f"{module.name}_i18n_"
        entries[prefix + "commit"] = git.sha1
        entries[prefix + "zip_csum"] = git.zip_sha256

    # apply
    out = []
    path = repos_bzl
    reg = re.compile(r'(\s+)(\S+_(?:commit|zip_csum)) = "(.*)"')
    for line in open(path).readlines():
        if m := reg.match(line):
            (indent, key, _oldvalue) = m.groups()
            value = entries[key]
            line = f'{indent}{key} = "{value}"\n'
            out.append(line)
        else:
            out.append(line)
    open(path, "w").writelines(out)

    commit_if_changed(root, update_label="translations")


def commit_if_changed(folder: str, update_label: str):
    status = subprocess.run(["git", "diff", "--exit-code"], cwd=folder, check=False)
    if status.returncode == 0:
        # no changes
        return
    subprocess.run(
        ["git", "commit", "-a", "-m", "update " + update_label], cwd=folder, check=True
    )


def update_ftl_templates():
    for module in modules:
        if ftl := module.ftl:
            (source, dest) = ftl
            dest = os.path.join(module.folder(), dest)
            subprocess.run(
                [
                    "rsync",
                    "-ai",
                    "--delete",
                    "--no-perms",
                    "--no-times",
                    source + "/",
                    dest + "/",
                ],
                check=True,
            )
            commit_if_changed(module.folder(), update_label="templates")


def push_i18n_changes():
    for module in modules:
        subprocess.run(["git", "push"], cwd=module.folder(), check=True)


update_git_repos()
update_ftl_templates()
push_i18n_changes()
update_repos_bzl()
