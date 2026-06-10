# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import inspect
import re
import runpy
import sys
from pathlib import Path
from typing import Any

DEPRECATED_PREFIX = "Deprecated. Use "
OBSOLETE_TEXT = "Obsolete, do not use."
SPHINX_SITE_URL = "https://dev-docs.ankiweb.net/en/latest/autoapi/"


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def hooks(path: str) -> list[Any]:
    root = repo_root()
    sys.path.insert(0, str(root / "pylib" / "tools"))
    namespace = runpy.run_path(str(root / path))
    return namespace["hooks"]


def doc(text: str | None) -> str:
    text = inspect.cleandoc(text or "")
    if not text:
        return "No docstring."

    def add_fences(match: re.Match) -> str:
        code = match.group(1).replace(chr(9), "")
        code = code.replace("<", "&SAFEBYPASSlt;").replace(">", "&SAFEBYPASSgt;")
        code = inspect.cleandoc(code)
        return f"\n```python\n{code}\n```\n"

    # Convert tab-indented blocks to ```python fences
    text = re.sub(
        r"(?:^|\n)((?:(?: {4}|\t|\n {4}).*(?:\n|$))+)",
        add_fences,
        text,
        flags=re.MULTILINE,
    )

    return text


def safe(text: str) -> str:
    safe_text = (
        text.replace("{{filters:..}}", "`{{filters:..}}`")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("&SAFEBYPASSlt;", "<")
        .replace("&SAFEBYPASSgt;", ">")
    )
    return "\n".join(line.rstrip() for line in safe_text.splitlines())


def replacement(text: str) -> str:
    suffix = " instead."
    first_line = text.splitlines()[0]
    if first_line.startswith(DEPRECATED_PREFIX) and first_line.endswith(suffix):
        return first_line.removeprefix(DEPRECATED_PREFIX).removesuffix(suffix)
    return ""


def status_suffix(hook: Any) -> str:
    text = doc(hook.doc)
    if text.startswith(DEPRECATED_PREFIX):
        return " _(Deprecated)_"
    if text == OBSOLETE_TEXT:
        return " _(Obsolete)_"
    return ""


def _format_type(match: re.Match) -> str:
    type_str = match.group(0)
    page = "/".join(type_str.split(".")[:-1])
    url = f"{SPHINX_SITE_URL}{page}#{type_str}"

    return f"<a href='{url}'>{type_str.strip()}</a>"


def format_type(type_string: str):
    type_string = re.sub(r"(anki|aqt)\.[a-zA-Z0-9_\.]*", _format_type, type_string)
    return type_string


def signature(hook: Any) -> str:
    def format_arg(arg: str) -> str:
        argname, argtype = arg.split(":")
        return f"<code>{argname.strip()}:{format_type(argtype)}</code>"

    args = f"**Args:** {', '.join(format_arg(arg) for arg in hook.args or [])}"
    if not hook.args:
        args = "No arguments."
    if hook.return_type:
        return f"{args}\\\n**Returns:** <code>{format_type(hook.return_type)}</code>"
    return args


def warning(hook: Any) -> str:
    text = doc(hook.doc)
    if text.startswith(DEPRECATED_PREFIX):
        return (
            f"<Warning>\n  Deprecated. Use `{replacement(text)}` instead.\n</Warning>"
        )
    if text == OBSOLETE_TEXT:
        return "<Warning>\n  Obsolete, do not use.\n</Warning>"
    return ""


def description(hook: Any) -> str:
    text = doc(hook.doc)
    if text == OBSOLETE_TEXT:
        return ""
    if text.startswith(DEPRECATED_PREFIX):
        text = "\n".join(text.splitlines()[1:]).strip()
    return safe(text)


def render_hook(hook: Any) -> str:
    parts = [
        f"### `{hook.name}`{status_suffix(hook)}",
        "",
        signature(hook),
    ]
    warn = warning(hook)
    desc = description(hook)
    if warn:
        parts.extend(["", warn])
    if desc:
        parts.extend(["", desc])
    return "\n".join(parts)


def render_hooks(path: str) -> str:
    return "\n\n---\n\n".join(render_hook(hook) for hook in hooks(path))
