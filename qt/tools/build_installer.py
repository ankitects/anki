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

import jinja2

installer_dir = Path("qt/installer")
app_dir = installer_dir / "app"
out_dir = Path("out/installer")
out_dir.mkdir(exist_ok=True)
env = jinja2.Environment(loader=jinja2.FileSystemLoader(app_dir))


def use_briefcase() -> bool:
    return sys.platform in ("win32", "darwin")


def normalize_wheel_path(out_dir: Path, path: str) -> str:
    path = Path(path).relative_to(out_dir.parent).as_posix()
    return f"../{path}"


def get_briefcase_template_path() -> Path | None:
    if sys.platform == "win32":
        return installer_dir / "windows-template"
    elif sys.platform == "darwin":
        return installer_dir / "mac-template"
    return None


def build_pyinstaller() -> None:
    subprocess.check_call(
        [
            sys.executable,
            "-m",
            "PyInstaller",
            "-y",
            out_dir / "pyinstaller.spec",
            "--distpath",
            out_dir / "dist",
            "--workpath",
            out_dir / "build",
        ]
    )


def package_pyinstaller(version: str) -> None:
    dist_dir = out_dir / "dist" / "anki"
    scripts_dir = installer_dir / "linux-scripts"
    for file in scripts_dir.iterdir():
        if file.name == "build.sh":
            continue
        dest_file = dist_dir / file.name
        shutil.copy2(file, dest_file)

    print("Building zip...", file=sys.stderr)
    subprocess.check_call(
        [
            "bash",
            (scripts_dir / "build.sh").absolute().as_posix(),
            version,
            dist_dir.absolute().as_posix(),
        ],
        cwd=out_dir,
    )


def build(args: argparse.Namespace) -> None:
    version = args.version
    shutil.copytree(app_dir, out_dir, dirs_exist_ok=True)

    if not use_briefcase():
        build_pyinstaller()
        return

    aqt_wheel = normalize_wheel_path(out_dir, args.aqt_wheel)
    anki_wheel = normalize_wheel_path(out_dir, args.anki_wheel)
    template_path = get_briefcase_template_path()
    template = (
        f'template = "{template_path.absolute().as_posix()}"' if template_path else ""
    )
    template = env.get_template("pyproject.toml.template").render(
        aqt_wheel=aqt_wheel,
        anki_wheel=anki_wheel,
        version=version,
        template=template,
    )
    (out_dir / "pyproject.toml").write_text(template, encoding="utf-8")
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
            "--update",
            "--update-requirements",
            "--update-resources",
            "--update-support",
            "--log",
        ],
        cwd=out_dir,
    )


def package(args: argparse.Namespace) -> None:
    version = args.version

    if not use_briefcase():
        package_pyinstaller(version)
        return

    identity = os.environ.get("SIGN_IDENTITY")
    identity_args = ["--identity", identity] if identity else ["--adhoc-sign"]
    subprocess.check_call(
        [
            sys.executable,
            "-m",
            "briefcase",
            "package",
            "--log",
            *identity_args,
        ],
        cwd=out_dir,
    )
    platform_suffix = ""
    if sys.platform == "win32":
        platform_suffix = "-windows"
    elif sys.platform == "darwin":
        arch = "apple" if platform.machine() == "arm64" else "intel"
        platform_suffix = f"-mac-{arch}"
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
    args.func(args)
