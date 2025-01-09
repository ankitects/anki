# Based on https://github.com/ziglang/zig-pypi/blob/de14cf728fa35c014821f62a4fa9abd9f4bb560e/make_wheels.py
# MIT

from __future__ import annotations

import os
import sys
from email.message import EmailMessage
from pathlib import Path
from typing import Sequence
from zipfile import ZIP_DEFLATED, ZipInfo

from wheel.wheelfile import WheelFile

def make_message(headers, payload=None):
    msg = EmailMessage()
    for name, value in headers.items():
        if name == "_dependencies":
            for dep in value:
                if isinstance(dep, ExtraRequires):
                    msg["Provides-Extra"] = dep.name
                    for inner_dep in dep.deps:
                        msg["Requires-Dist"] = f"{inner_dep}; extra == '{dep.name}'"
                else:
                    msg["Requires-Dist"] = dep
        elif isinstance(value, list):
            for value_part in value:
                msg[name] = value_part
        else:
            msg[name] = value
    if payload:
        msg.set_payload(payload)
    return msg


def write_wheel_file(filename, contents):
    with WheelFile(filename, "w") as wheel:
        for member_info, member_source in contents.items():
            if not isinstance(member_info, ZipInfo):
                member_info = ZipInfo(member_info)
                member_info.external_attr = 0o644 << 16
            member_info.file_size = len(member_source)
            member_info.compress_type = ZIP_DEFLATED
            wheel.writestr(member_info, bytes(member_source))
    return filename


def write_wheel(
    wheel_path,
    *,
    name,
    version,
    tag,
    metadata,
    description,
    contents,
    entrypoints: list[str] | None = None,
    top_level: list[str] | None = None,
):
    dist_info = f"{name}-{version}.dist-info"
    extra = {}
    if entrypoints:
        entrypoints_joined = "\n".join(entrypoints)
        text = f"[console_scripts]\n{entrypoints_joined}"
        file = f"{dist_info}/entry_points.txt"
        extra[file] = text.encode("utf8")
    if top_level:
        top_level_joined = "\n".join(top_level) + "\n"
        file = f"{dist_info}/top_level.txt"
        extra[file] = top_level_joined.encode("utf8")
    return write_wheel_file(
        wheel_path,
        {
            **contents,
            **extra,
            f"{dist_info}/METADATA": make_message(
                {
                    "Metadata-Version": "2.1",
                    "Name": name,
                    "Version": version,
                    **metadata,
                },
                description,
            ),
            f"{dist_info}/WHEEL": make_message(
                {
                    "Wheel-Version": "1.0",
                    "Generator": "anki write_wheel.py",
                    "Root-Is-Purelib": "false",
                    "Tag": tag,
                }
            ),
        },
    )


def merge_sources(contents, root, exclude):
    root = Path(root)
    for path in root.glob("**/*"):
        if path.is_dir() or exclude(path):
            continue
        path_str = str(path.relative_to(root.parent))
        if path_str.endswith(".pyc"):
            continue
        contents[path_str] = path.read_bytes()


def split_wheel_path(path: str):
    path2 = Path(path)
    components = path2.stem.split("-", maxsplit=2)
    return components


class ExtraRequires:
    def __init__(self, name, deps):
        self.name = name
        self.deps = deps


src_root = sys.argv[1]
generated_root = sys.argv[2]
wheel_path = sys.argv[3]

name, version, tag = split_wheel_path(wheel_path)


def exclude_aqt(path: Path) -> bool:
    if path.suffix in [".ui", ".scss", ".map", ".ts"]:
        return True
    if path.name.startswith("tsconfig"):
        return True
    if "/aqt/data" in str(path):
        return True
    return False


def exclude_nothing(path: Path) -> bool:
    return False


def extract_requirements(path: Path) -> list[str]:
    return path.read_text().splitlines()


if name == "aqt":
    exclude = exclude_aqt
else:
    exclude = exclude_nothing

contents: dict[str, str] = {}
merge_sources(contents, src_root, exclude)
merge_sources(contents, generated_root, exclude)
all_requires: Sequence[str | ExtraRequires]

if name == "anki":
    all_requires = extract_requirements(Path("python/requirements.anki.in"))
    entrypoints = None
    top_level = None
else:
    all_requires = extract_requirements(Path("python/requirements.aqt.in")) + [
        "anki==" + version,
                "pyqt6>=6.2",
                "pyqt6-webengine>=6.2",
    ]
    entrypoints = ["anki = aqt:run"]
    top_level = ["aqt", "_aqt"]

# reproducible builds
os.environ["SOURCE_DATE_EPOCH"] = "0"

write_wheel(
    wheel_path,
    name=name,
    version=version,
    tag=tag,
    metadata={
        "License": "AGPL-3",
        "Classifier": [
            "License :: OSI Approved :: GNU Affero General Public License v3 or later (AGPLv3+)",
        ],
        "Requires-Python": ">=3.9",
        "_dependencies": all_requires,
    },
    description="Please see https://apps.ankiweb.net\n\n",
    contents=contents,
    entrypoints=entrypoints,
    top_level=top_level,
)
