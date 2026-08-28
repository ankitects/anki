# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import argparse
import json
import re
from collections.abc import Callable, Iterable
from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path
from typing import TypeVar

T = TypeVar("T")

TITLE_RE = re.compile(r"# (.*?)(?:$|\{)", re.MULTILINE)
TITLE_REPLACE_RE = re.compile(r"#(.*)$", re.MULTILINE)
HEADING_ANCHOR_RE = re.compile(
    r"^(?P<hashes>#{1,6})\s+(?P<title>.*?)\s*\{#(?P<anchor>[A-Za-z0-9][A-Za-z0-9_:\-.]*)\}\s*$",
    re.MULTILINE,
)
QUICKLINK_RE = re.compile(
    r"<(?P<link>(?:https?|ftp|mailto):[^> ]+|[^<> ]+@[^<> ]+)>", re.DOTALL
)

# HTML/MDX safety normalization helpers.
HTML_TAG_RE = re.compile(r"(</?[A-Za-z][^>]*>)")
HTML_UNQUOTED_ATTR_RE = re.compile(
    r'(?P<name>[A-Za-z_:][A-Za-z0-9_:.\-]*)=(?P<value>[^\s"\'=<>`]+?)(?=(?:\s|/?>))'
)
VOID_HTML_TAG_NAME_RE = re.compile(r"^<\s*/?\s*([A-Za-z][A-Za-z0-9:-]*)")

# Markdown code regions where literal text should be preserved.
MARKDOWN_FENCED_CODE_RE = re.compile(r"(```[\s\S]*?```)")
MARKDOWN_INLINE_CODE_RE = re.compile(r"(`[^`\n]*`)")

# Markdown link cleanup helpers.
MARKDOWN_LINK_MD_RE = re.compile(
    r"(\[[^\]]+\]\()(?P<path>[^)\s]+?)\.md(?P<suffix>[#?][^)\s]+)?\)"
)
ADMONISH_BLOCK_RE = re.compile(
    r"```admonish\s+(?P<kind>[A-Za-z]+)\n(?P<body>[\s\S]*?)\n```"
)

# Mirrors the replacement rules listed in docs-relative-links branch commits.
HTML_PATH_AND_SUFFIX_RE = r"(?P<path>.+?)\.html(?P<suffix>[#?][^)\s]+)?"
PATH_AND_SUFFIX_REPLACEMENT = r"\g<path>\g<suffix>"
VOID_HTML_ELEMENTS = {
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
}


def admonish_callout_rule(
    admonish_kind: str, callout: str, heading: str = ""
) -> tuple[str, str, str]:
    return (admonish_kind, callout, heading)


ADMONISH_CALLOUT_REPLACEMENTS = [
    admonish_callout_rule("summary", "Note", "Summary"),
    admonish_callout_rule("warning", "Warning"),
    admonish_callout_rule("note", "Note"),
    admonish_callout_rule("info", "Info"),
    admonish_callout_rule("danger", "Danger"),
    admonish_callout_rule("caution", "Warning"),
    admonish_callout_rule("example", "Info"),
]


def relative_link_rule(domain: str, prefix: str) -> tuple[re.Pattern[str], str]:
    pattern = re.compile(rf"https://{re.escape(domain)}/{HTML_PATH_AND_SUFFIX_RE}")
    replacement = f"/{prefix}/{PATH_AND_SUFFIX_REPLACEMENT}"
    return (pattern, replacement)


DOCS_RELATIVE_LINK_REPLACEMENTS = [
    relative_link_rule("docs.ankiweb.net", "manual"),
    relative_link_rule("addon-docs.ankiweb.net", "addons"),
    relative_link_rule("faqs.ankiweb.net", "faqs"),
    relative_link_rule("docs.ankimobile.net", "ankimobile"),
]


def format_page(content: str, language_code: str = "") -> str:
    title = TITLE_RE.findall(content)
    content = TITLE_REPLACE_RE.sub("", content, 1)
    if title:
        title = title[0]
        content = (
            f"""---
title: "{title}"
---\n"""
            + content
        )
    else:
        print(f"WARN: could not find title in {content[:5]}")

    def replace_heading_anchor(match: re.Match[str]) -> str:
        return (
            f'<a id="{match.group("anchor")}"></a>\n'
            f"{match.group('hashes')} {match.group('title').strip()}"
        )

    def replace_quicklink(match: re.Match[str]) -> str:
        link = match.group("link")
        if "@" in link and not link.startswith("mailto:"):
            return f"[{link}](mailto:{link})"
        return f"[{link}]({link})"

    def replace_admonish(match: re.Match[str]) -> str:
        kind = match.group("kind").lower()
        body = match.group("body")
        for admonish_kind, callout, heading in ADMONISH_CALLOUT_REPLACEMENTS:
            if kind != admonish_kind:
                continue
            title = f"**{heading}**\n\n" if heading else ""
            return f"<{callout}>\n{title}{body}\n</{callout}>"
        return match.group(0)

    content = ADMONISH_BLOCK_RE.sub(replace_admonish, content)
    content = QUICKLINK_RE.sub(replace_quicklink, content)
    # Convert intra-manual links like foo.md or foo.md#bar to extensionless links.
    content = MARKDOWN_LINK_MD_RE.sub(r"\1\g<path>\g<suffix>)", content)
    content = HEADING_ANCHOR_RE.sub(replace_heading_anchor, content)

    for pattern, replacement in DOCS_RELATIVE_LINK_REPLACEMENTS:
        # Language pages receive a language prefix, e.g. /ar/manual/...
        if language_code:
            replacement = f"/{language_code}{replacement}"
        content = pattern.sub(replacement, content)

    content = content.replace("{", "\\{").replace("}", "\\}")
    content = content.replace("<!--", "{/*").replace("-->", "*/}")
    # Escape plain text nodes for MDX while preserving actual HTML tags.
    content = escape_text_preserve_html(content)

    return content


def group_pages(group: dict | str) -> list[str]:
    if isinstance(group, str):
        return [group]

    pages = group["pages"]
    return [page for p in pages for page in group_pages(p)]


def find_first(
    items: Iterable[T], predicate: Callable[[T], bool], item_name: str = "item"
) -> T:
    for item in items:
        if predicate(item):
            return item
    raise ValueError(f"could not find {item_name}")


@dataclass
class Page:
    src: Path
    root_dest: Path
    dest: Path


def escape_text_preserve_html(raw: str) -> str:
    # In indented code blocks, always escape angle brackets as literal text.
    lines = raw.splitlines(keepends=True)
    in_fenced_code_block = False
    for idx, line in enumerate(lines):
        if line.lstrip().startswith("```"):
            in_fenced_code_block = not in_fenced_code_block
            continue
        if in_fenced_code_block:
            continue
        if line.startswith("    ") or line.startswith("\t"):
            lines[idx] = line.replace("<", "&lt;").replace(">", "&gt;")

    raw = "".join(lines)

    # Split into HTML tags and non-tag text so they can be normalized separately.
    parts = HTML_TAG_RE.split(raw)
    for idx, part in enumerate(parts):
        if part.startswith("<") and part.endswith(">"):
            # Normalize HTML attributes for MDX parser compatibility.
            tag = HTML_UNQUOTED_ATTR_RE.sub(r'\g<name>="\g<value>"', part)
            tag_name_match = VOID_HTML_TAG_NAME_RE.match(tag)
            if (
                tag_name_match
                and tag.startswith("</")
                and tag_name_match.group(1).lower() in VOID_HTML_ELEMENTS
            ):
                # Drop invalid closing tags for void elements like </br>.
                parts[idx] = ""
                continue
            if (
                tag_name_match
                and not tag.startswith("</")
                and not tag.rstrip().endswith("/>")
                and tag_name_match.group(1).lower() in VOID_HTML_ELEMENTS
            ):
                # Ensure void elements are self-closing, e.g. <br />.
                tag = tag[:-1].rstrip() + " />"
            parts[idx] = tag
            continue

        # Escape only outside Markdown code spans/fences.
        fenced_parts = MARKDOWN_FENCED_CODE_RE.split(part)
        for f_idx, fenced_part in enumerate(fenced_parts):
            if MARKDOWN_FENCED_CODE_RE.fullmatch(fenced_part):
                continue

            inline_parts = MARKDOWN_INLINE_CODE_RE.split(fenced_part)
            for i_idx, inline_part in enumerate(inline_parts):
                if MARKDOWN_INLINE_CODE_RE.fullmatch(inline_part):
                    continue
                inline_parts[i_idx] = inline_part.replace("<", "&lt;").replace(
                    ">", "&gt;"
                )
            fenced_parts[f_idx] = "".join(inline_parts)

        parts[idx] = "".join(fenced_parts)

    return "".join(parts)


def main():
    parser = argparse.ArgumentParser()
    # parser.add_argument(
    #    "--source-docs",
    #    required=True,
    #    help="Path to the source documents",
    # )
    # parser.add_argument(
    #    "--language-code",
    #    required=True,
    #    help="the language code of the language",
    # )

    parser.parse_args()

    language_code = "ar"
    if language_code == "en":
        language_code = ""

    DOCS_SITE_DIR = Path("docs-site")
    DOCS_FILEPATH = DOCS_SITE_DIR / "docs.json"
    LANGUAGE_DIR = DOCS_SITE_DIR / language_code
    MANUAL_DEST_DIR = LANGUAGE_DIR / "manual"
    SRC_DOCS_DIR = Path("../anki-faqs/src").resolve()
    TAB = "FAQs"

    print(str(MANUAL_DEST_DIR))

    with open(DOCS_FILEPATH) as f:
        site_structure = json.load(f)

    # print(site_structure)

    default_language = find_first(
        site_structure["navigation"]["languages"],
        lambda lang: lang["language"] == "en",
        "language 'en'",
    )
    default_tab = find_first(
        default_language["tabs"],
        lambda tab: tab["tab"] == TAB,
        f"{TAB} tab",
    )
    main_group = default_tab["groups"][0]
    default_language_pages = group_pages(main_group)

    source_contents = sorted(
        path.resolve()
        for path in SRC_DOCS_DIR.rglob("*")
        if path.is_file() and path.suffix in {".md", ".mdx"}
    )

    paths: list[Page] = []
    for path in source_contents:
        relative_src = path.relative_to(SRC_DOCS_DIR)
        root_dest = Path(TAB.lower()) / relative_src.with_suffix("")
        dest = Path(language_code) / root_dest if language_code else root_dest
        paths.append(Page(src=path, root_dest=root_dest, dest=dest))

    to_move = [page for page in paths if str(page.root_dest) in default_language_pages]
    unmoved = [
        page.src for page in paths if str(page.root_dest) not in default_language_pages
    ]
    if unmoved:
        print(f"unimported pages: {unmoved}")

    # print(f"Source docs: {default_language}")

    for page in to_move:
        output_path = DOCS_SITE_DIR / page.dest.with_suffix(".mdx")
        output_path.parent.mkdir(parents=True, exist_ok=True)
        content = page.src.read_text(encoding="utf-8")
        output_path.write_text(format_page(content, language_code), encoding="utf-8")

    new_tab = deepcopy(default_tab)
    new_group = new_tab["groups"][0]

    def update_page_paths(group: dict | str):
        if isinstance(group, str):
            path = Path(group)
            if any(path == page.root_dest for page in to_move):
                return str(language_code / path)
            else:
                return None

        pages = group["pages"]
        group["pages"] = [update_page_paths(p) for p in pages]
        group["pages"] = [p for p in group["pages"] if p is not None]
        return group

    update_page_paths(new_group)

    target_language = next(
        (
            lang
            for lang in site_structure["navigation"]["languages"]
            if lang["language"] == language_code
        ),
        None,
    )
    if target_language is None:
        target_language = deepcopy(default_language)
        target_language["language"] = language_code
        site_structure["navigation"]["languages"].append(target_language)


    target_language["tabs"] = [
        tab for tab in target_language["tabs"] if tab.get("tab", None) != TAB
    ]
    target_language["tabs"].append(new_tab)

    with open(DOCS_FILEPATH, "w") as f:
        json.dump(site_structure, f, indent=4)


if __name__ == "__main__":
    main()
