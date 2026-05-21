# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import argparse
import compileall
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Sequence

sys.path.extend(["pylib", "out/pylib"])

installer_dir = Path("qt/installer")
app_dir = installer_dir / "app"
out_dir = Path("out/installer").resolve()

# Anki disk-lang codes whose Chromium .pak filename differs from the disk lang.
_CHROMIUM_PAK_LANG_REMAP = {"tl": "fil"}


def normalize_wheel_path(path: str | Path) -> str:
    return Path(path).absolute().as_posix()


def chromium_paks_to_keep() -> set[str]:
    from anki.lang import lang_to_disk_lang, langs

    keep = {"en-US"}
    for _name, code in langs:
        disk = lang_to_disk_lang(code)
        keep.add(_CHROMIUM_PAK_LANG_REMAP.get(disk, disk))
        if "-" in disk:
            keep.add(disk.split("-", 1)[0])
    return keep


def prune_webengine_locales(out_dir: Path) -> None:
    keep = chromium_paks_to_keep()
    for pak in out_dir.rglob("qtwebengine_locales/*.pak"):
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


def get_briefcase_sources_path(out_dir: Path, version: str) -> Path | None:
    """
    Get the directory where Briefcase's `app`/`app_packages` directories are written.
    Make sure to update this if output formats or templates ever change.
    """
    if sys.platform == "win32":
        return out_dir / "build" / "anki" / "windows" / "app" / "src"
    elif sys.platform == "darwin":
        return (
            out_dir
            / "build"
            / "anki"
            / "macos"
            / "app"
            / "Anki.app"
            / "Contents"
            / "Resources"
        )
    elif sys.platform == "linux":
        return next((out_dir / "build" / "anki" / "linux" / "zip").glob("anki-*"), None)
    return None


def get_briefcase_config_args(args: argparse.Namespace) -> list[str]:
    version = args.version
    if aqt_wheel := getattr(args, "aqt_wheel", None):
        aqt_wheel = normalize_wheel_path(args.aqt_wheel)
    if anki_wheel := getattr(args, "anki_wheel", None):
        anki_wheel = normalize_wheel_path(args.anki_wheel)
    template_path = get_briefcase_template_path()
    config_args = [
        "-C",
        f'version="{version}"',
    ]
    requires = []
    if aqt_wheel:
        requires.append(f"{aqt_wheel}[qt,audio]")
    if anki_wheel:
        requires.append(anki_wheel)
    if requires:
        config_args.extend(
            ["-C", "requires=[" + ",".join(f'"{dep}"' for dep in requires) + "]"]
        )
    if template_path:
        config_args.extend(["-C", f'template="{template_path.absolute().as_posix()}"'])

    return config_args


def compile_sources(out_dir: Path, version: str) -> bool:
    """Compile Python sources to .pyc"""

    sources_root = get_briefcase_sources_path(out_dir, version)
    if not sources_root:
        return False
    for src_dir in (sources_root / "app", sources_root / "app_packages"):
        # legacy=True is needed to write .pyc to the same location as .py
        # so no __pycache__, which is not loaded with no sources
        if not compileall.compile_dir(src_dir, legacy=True, quiet=1):
            raise RuntimeError(f"Failed to compile Python sources in {src_dir}")
        for path in src_dir.rglob("*.py"):
            path.unlink()
    return True


def _find_fcitx_file(dirs: list[Path], pattern: str) -> Path | None:
    for d in dirs:
        for f in d.glob(pattern):
            if f.is_file():
                return f
    return None


def bundle_fcitx(out_dir: Path, version: str) -> None:
    sources = get_briefcase_sources_path(out_dir, version)
    if not sources:
        return

    machine = platform.machine()
    # Cover Debian/Ubuntu multiarch (apt or cmake with -DCMAKE_INSTALL_PREFIX=/usr)
    # and cmake's default /usr/local prefix.
    lib_dirs = [
        Path(f"/usr/lib/{machine}-linux-gnu"),
        Path("/usr/lib"),
        Path(f"/usr/local/lib/{machine}-linux-gnu"),
        Path("/usr/local/lib"),
    ]
    pic_dirs = [d / "qt6" / "plugins" / "platforminputcontexts" for d in lib_dirs]

    pic_plugin = _find_fcitx_file(pic_dirs, "libfcitx5platforminputcontextplugin.so")
    if pic_plugin is None:
        raise RuntimeError("fcitx5-qt6 plugin not found")

    pyqt6_qt6 = sources / "app_packages" / "PyQt6" / "Qt6"
    pic_dest = pyqt6_qt6 / "plugins" / "platforminputcontexts"
    dbus_dest = pyqt6_qt6 / "plugins" / "dbusaddons"
    dbus_dest.mkdir(exist_ok=True)

    shutil.copy2(pic_plugin, pic_dest)
    for lib_dir in lib_dirs:
        for lib in lib_dir.glob("libFcitx5Qt6DBusAddons.so*"):
            shutil.copy2(lib, dbus_dest)

    # libfcitx5core / libfcitx5utils are NOT bundled: they are resolved from the
    # system path and are only present when the user has fcitx5 installed.
    subprocess.check_call(
        [
            "patchelf",
            "--set-rpath",
            "$ORIGIN/../dbusaddons:$ORIGIN/../../lib",
            str(pic_dest / "libfcitx5platforminputcontextplugin.so"),
        ]
    )
    for lib in dbus_dest.iterdir():
        if lib.is_file():
            subprocess.check_call(
                ["patchelf", "--set-rpath", "$ORIGIN/../../lib", str(lib)]
            )


def build(args: argparse.Namespace) -> None:
    version = args.version
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
    compile_sources(out_dir, version)
    if sys.platform == "linux":
        bundle_fcitx(out_dir, version)


def get_platform_suffix() -> str:
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
    return platform_suffix


def get_signing_args() -> list[str]:
    identity = os.environ.get("SIGN_IDENTITY")
    return ["--identity", identity] if identity else ["--adhoc-sign"]


def package(args: argparse.Namespace) -> None:
    version = args.version
    config_args = get_briefcase_config_args(args)
    shutil.rmtree(out_dir / "dist", ignore_errors=True)
    subprocess.check_call(
        [
            sys.executable,
            "-m",
            "briefcase",
            "package",
            *get_briefcase_output_format(),
            *config_args,
            "--log",
            *get_signing_args(),
        ],
        cwd=out_dir,
    )
    package_path = next((out_dir / "dist").iterdir())
    package_path.rename(
        package_path.with_stem(f"anki-{version}{get_platform_suffix()}")
    )


def main(args: Sequence[str] | None = None) -> argparse.Namespace:
    out_dir.mkdir(exist_ok=True)
    parser = argparse.ArgumentParser(
        prog="build_installer", description="Build the Briefcase installer."
    )
    parser.add_argument("--version", help="Anki version")
    subparsers = parser.add_subparsers(help="Briefcase command (build/package)")
    build_parser = subparsers.add_parser("build", help="Compile/build app")
    build_parser.add_argument("--aqt_wheel", help="Path to the aqt wheel file")
    build_parser.add_argument("--anki_wheel", help="Path to the anki wheel file")
    build_parser.set_defaults(func=build)
    package_parser = subparsers.add_parser("package", help="Package installer")
    package_parser.set_defaults(func=package)

    parsed = parser.parse_args(args)
    parsed.func(parsed)

    return parsed


if __name__ == "__main__":  # pragma: no cover
    main()
