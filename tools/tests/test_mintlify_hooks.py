from __future__ import annotations

from types import SimpleNamespace

from tools.mintlify_hooks import render_hook, safe, signature


def test_signature_includes_arguments_and_return_type() -> None:
    hook = SimpleNamespace(
        args=("text: str", "count: int"),
        return_type="str",
    )

    assert (
        signature(hook) == "**Args:** `text: str`, `count: int`\\\n**Returns:** `str`"
    )


def test_render_hook_marks_deprecated_docstring() -> None:
    hook = SimpleNamespace(
        name="old_hook",
        args=(),
        return_type=None,
        doc="Deprecated. Use new_hook instead.\nCalled after work finishes.",
    )

    assert render_hook(hook) == "\n".join(
        [
            "### `old_hook` _(Deprecated)_",
            "",
            "No arguments.",
            "",
            "<Warning>",
            "  Deprecated. Use `new_hook` instead.",
            "</Warning>",
            "",
            "Called after work finishes.",
        ]
    )


def test_render_hook_marks_obsolete_docstring() -> None:
    hook = SimpleNamespace(
        name="old_hook",
        args=(),
        return_type=None,
        doc="Obsolete, do not use.",
    )

    assert render_hook(hook) == "\n".join(
        [
            "### `old_hook` _(Obsolete)_",
            "",
            "No arguments.",
            "",
            "<Warning>",
            "  Obsolete, do not use.",
            "</Warning>",
        ]
    )


def test_safe_escapes_mdx_sensitive_text() -> None:
    assert safe("{{filters:..}}\n<show both sides>") == (
        "`{{filters:..}}`\n&lt;show both sides&gt;"
    )
