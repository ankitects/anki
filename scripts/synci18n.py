#
# A helper script to update commit references to the latest translations,
# and copy source files to the translation repos. Requires access to the
# i18n repos to run.

import subprocess
from dataclasses import dataclass
import re
import os
import sys
from typing import Optional, Tuple

repos_bzl = "repos.bzl"
working_folder = "../anki-i18n"

if not os.path.exists(repos_bzl):
    raise Exception("run from workspace root")

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
        ftl=("rslib/ftl", "core/templates"),
    ),
    Module(
        name="qtftl",
        repo="git@github.com:ankitects/anki-desktop-ftl",
        ftl=("qt/ftl", "desktop/templates"),
    ),
    # update_po_templates() expects this last
    Module(name="qtpo", repo="git@github.com:ankitects/anki-desktop-i18n"),
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
    shallow_since: str


def module_git_info(module: Module) -> GitInfo:
    folder = module.folder()
    sha = subprocess.check_output(
        ["git", "log", "-n", "1", "--pretty=format:%H"], cwd=folder
    )
    shallow = subprocess.check_output(
        ["git", "log", "-n", "1", "--pretty=format:%cd", "--date=raw"], cwd=folder
    )
    return GitInfo(sha1=sha.decode("utf8"), shallow_since=shallow.decode("utf8"))


def update_repos_bzl(commit: bool):
    # gather changes
    entries = {}
    for module in modules:
        git = module_git_info(module)
        prefix = f"{module.name}_i18n_"
        entries[prefix + "commit"] = git.sha1
        entries[prefix + "shallow_since"] = git.shallow_since

    # apply
    out = []
    path = repos_bzl
    reg = re.compile(r'(\s+)(\S+_(?:commit|shallow_since)) = "(.*)"')
    for line in open(path).readlines():
        if m := reg.match(line):
            (indent, key, _oldvalue) = m.groups()
            value = entries[key]
            line = f'{indent}{key} = "{value}"\n'
            out.append(line)
        else:
            out.append(line)
    open(path, "w").writelines(out)

    if commit:
        commit_if_changed(".")


def commit_if_changed(folder: str):
    status = subprocess.run(["git", "diff", "--exit-code"], cwd=folder, check=False)
    if status.returncode == 0:
        # no changes
        return
    subprocess.run(
        ["git", "commit", "-a", "-m", "update translations"], cwd=folder, check=True
    )


def update_ftl_templates():
    for module in modules:
        if ftl := module.ftl:
            (source, dest) = ftl
            dest = os.path.join(module.folder(), dest)
            subprocess.run(
                ["rsync", "-ai", "--delete", "--no-perms", source + "/", dest + "/"],
                check=True,
            )
            commit_if_changed(module.folder())


def update_pot_and_po_files() -> str:
    "Update .pot and .po files, returning generated folder root."
    subprocess.run(["bazel", "build", "//qt/po:po_files"], check=True)
    return "bazel-bin/qt/po/"


def update_po_templates():
    "Copy updated files into repo."
    module = modules[-1]
    src_root = update_pot_and_po_files()
    dest_root = os.path.join(module.folder(), "desktop")
    subprocess.run(
        ["rsync", "-ai", "--no-perms", src_root + "/", dest_root + "/"], check=True
    )
    commit_if_changed(module.folder())


def push_i18n_changes():
    for module in modules:
        subprocess.run(["git", "push"], cwd=module.folder(), check=True)


update_git_repos()
update_repos_bzl(commit=False)
update_ftl_templates()
if len(sys.argv) > 1 and sys.argv[1] == "all":
    update_po_templates()
else:
    print("skipping po updates")
push_i18n_changes()
# we need to run this again - the first time ensures we merge
# the latest changes into the po files, and the second time ensures
# we're up to date after we made changes to the repos
update_repos_bzl(commit=True)
