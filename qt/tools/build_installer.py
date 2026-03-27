# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

import jinja2
from PIL import Image

installer_dir = Path("qt/installer")
app_dir = installer_dir / "app"
env = jinja2.Environment(loader=jinja2.FileSystemLoader(app_dir))


def normalize_wheel_path(out_dir: Path, path: str) -> str:
    path = Path(path).relative_to(out_dir.parent).as_posix()
    return f"../{path}"


ICON_SIZES = (16, 32, 48, 64, 128, 256, 512)


def generate_scaled_icons(out_dir: Path) -> None:
    """Generate scaled PNG icons from anki.png into out_dir/resources."""

    src = app_dir / "resources" / "anki.png"
    resources_dir = out_dir / "resources"
    with Image.open(src) as img:
        img.load()
        for size in ICON_SIZES:
            scaled = img.resize((size, size), Image.Resampling.LANCZOS)
            scaled.save(resources_dir / f"anki-{size}.png", "PNG")


def get_briefcase_template_path() -> Path | None:
    if sys.platform == "win32":
        return installer_dir / "windows-template"
    return None


def main(version: str, aqt_wheel: str, anki_wheel: str, out_dir: Path) -> None:
    shutil.rmtree(out_dir, ignore_errors=True)
    shutil.copytree(app_dir, out_dir)

    if sys.platform == "linux":
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
        return

    aqt_wheel = normalize_wheel_path(out_dir, aqt_wheel)
    anki_wheel = normalize_wheel_path(out_dir, anki_wheel)
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
    generate_scaled_icons(out_dir)
    (out_dir / "pyproject.toml").write_text(template, encoding="utf-8")
    shutil.copy("LICENSE", out_dir / "LICENSE")
    (out_dir / "CHANGELOG").write_text(
        "Please see https://apps.ankiweb.net/", encoding="utf-8"
    )
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the Briefcase installer.")
    parser.add_argument("--version", help="Anki version")
    parser.add_argument("--aqt_wheel", help="Path to the aqt wheel file")
    parser.add_argument("--anki_wheel", help="Path to the anki wheel file")
    parser.add_argument(
        "--out_dir",
        type=Path,
        help="Output directory for the Briefcase app",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    main(args.version, args.aqt_wheel, args.anki_wheel, args.out_dir)
