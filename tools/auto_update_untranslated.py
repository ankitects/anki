# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re

from tools.docs import UNTRANSLATED_MARKER

INTERNAL_DOCS_LINK_RE = re.compile(
    r'(?P<prefix>\]\(|href=["\"])\/(?P<path>(?:manual|ankimobile|faqs|addons|developers|translators|releases|index|automatic\-backups)(?:[/?#][^\s)"\']*)?)'
)

HEADER = """
<div style="text-align: left !important; direction: ltr !important;">
    <Warning>
        <h3>Untranslated</h3>
        This page is currently untranslated. If you want to help, see [the translation guide](/translators/anki/manual).
    </Warning>
</div>
"""


def localize_internal_links(content: str, target_locale: str) -> str:
    def replace_link(match: re.Match[str]) -> str:
        return f"{match.group('prefix')}/{target_locale}/{match.group('path')}"

    return INTERNAL_DOCS_LINK_RE.sub(replace_link, content)


def auto_update_untranslated(path: str, target_locale: str) -> str:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
        content = content.split("---\n", 2)[-1]  # Remove front matter if present
        content = (
            "\n" + UNTRANSLATED_MARKER + content
        )  # Used if just docs-release is run locally.
        if content.endswith("```\n"):
            content = content + "\n"
        content = HEADER + content
        return localize_internal_links(content, target_locale)
