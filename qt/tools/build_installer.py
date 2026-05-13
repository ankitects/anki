# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.extend(["pylib", "out/pylib"])

from anki.lang import lang_to_disk_lang, langs

installer_dir = Path("qt/installer")
app_dir = installer_dir / "app"
out_dir = Path("out/installer").resolve()

# Anki disk-lang codes whose Chromium .pak filename differs from the disk lang.
_CHROMIUM_PAK_LANG_REMAP = {"tl": "fil"}


def normalize_wheel_path(out_dir: Path, path: str) -> str:
    path = Path(path).absolute().relative_to(out_dir.parent).as_posix()
    return f"../{path}"


def chromium_paks_to_keep() -> set[str]:
    keep = {"en-US"}
    for _name, code in langs:
        disk = lang_to_disk_lang(code)
        keep.add(_CHROMIUM_PAK_LANG_REMAP.get(disk, disk))
        if "-" in disk:
            keep.add(disk.split("-", 1)[0])
    return keep


def prune_webengine_locales(bundle_root: Path) -> None:
    keep = chromium_paks_to_keep()
    for pak in bundle_root.rglob("qtwebengine_locales/*.pak"):
        if pak.stem not in keep:
            pak.unlink()


def get_briefcase_template_path() -> Path | None:
    if sys.platform == "win32":
        return installer_dir / "windows-template"
    elif sys.platform == "darwin":
        return installer_dir / "mac-template"
    elif sys.platform == "linux":
        return installer_dir / "linux-template"
    return None


def get_briefcase_output_format() -> list[str]:
    if sys.platform == "linux":
        return ["linux", "zip"]
    # Use default format for platform
    return []


def get_briefcase_config_args(args: argparse.Namespace) -> list[str]:
    version = args.version
    if aqt_wheel := getattr(args, "aqt_wheel", None):
        aqt_wheel = normalize_wheel_path(out_dir, args.aqt_wheel)
    if anki_wheel := getattr(args, "aqt_wheel", None):
        anki_wheel = normalize_wheel_path(out_dir, args.anki_wheel)
    template_path = get_briefcase_template_path()
    config_args = [
        "-C",
        f'version="{version}"',
    ]
    if aqt_wheel:
        config_args.extend(
            ["-C", f'requires=["{aqt_wheel}[qt,audio]", "{anki_wheel}"]']
        )
    if template_path:
        config_args.extend(["-C", f'template="{template_path.absolute().as_posix()}"'])

    return config_args


def build(args: argparse.Namespace) -> None:
    shutil.copytree(app_dir, out_dir, dirs_exist_ok=True)
    config_args = get_briefcase_config_args(args)
    shutil.copy("LICENSE", out_dir / "LICENSE")
    (out_dir / "CHANGELOG").write_text(
        "Please see https://apps.ankiweb.net/", encoding="utf-8"
    )
    subprocess.check_call(
        [
            sys.executable,
            "-m",
            "briefcase",
            "build",
            *get_briefcase_output_format(),
            *config_args,
            "--update",
            "--update-requirements",
            "--update-resources",
            "--update-support",
            "--log",
        ],
        cwd=out_dir,
    )
    prune_webengine_locales(out_dir)


def package(args: argparse.Namespace) -> None:
    version = args.version
    config_args = get_briefcase_config_args(args)
    shutil.rmtree(out_dir / "dist", ignore_errors=True)
    identity = os.environ.get("SIGN_IDENTITY")
    identity_args = ["--identity", identity] if identity else ["--adhoc-sign"]
    subprocess.check_call(
        [
            sys.executable,
            "-m",
            "briefcase",
            "package",
            *get_briefcase_output_format(),
            *config_args,
            "--log",
            *identity_args,
        ],
        cwd=out_dir,
    )
    platform_suffix = ""
    if sys.platform == "win32":
        arch = "arm64" if platform.machine() == "ARM64" else "x64"
        platform_suffix = f"-win-{arch}"
    elif sys.platform == "darwin":
        arch = "apple" if platform.machine() == "arm64" else "intel"
        platform_suffix = f"-mac-{arch}"
    elif sys.platform == "linux":
        arch = platform.machine()
        platform_suffix = f"-linux-{arch}"
    package_path = next((out_dir / "dist").iterdir())
    package_path.rename(package_path.with_stem(f"anki-{version}{platform_suffix}"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the Briefcase installer.")
    parser.add_argument("--version", help="Anki version")
    subparsers = parser.add_subparsers(help="Briefcase command (build/package)")
    build_parser = subparsers.add_parser("build", help="Compile/build app")
    build_parser.add_argument("--aqt_wheel", help="Path to the aqt wheel file")
    build_parser.add_argument("--anki_wheel", help="Path to the anki wheel file")
    build_parser.set_defaults(func=build)
    package_parser = subparsers.add_parser("package", help="Package installer")
    package_parser.set_defaults(func=package)

    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    out_dir.mkdir(exist_ok=True)
    args.func(args)
