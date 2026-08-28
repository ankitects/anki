# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import argparse
import html as html_lib
import json
import re
from collections.abc import Callable, Iterable
from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path
from typing import TypeVar

from bs4 import BeautifulSoup, NavigableString

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
MARKDOWN_LINK_MD_RE = re.compile(
    r"(\[[^\]]+\]\()(?P<path>[^)\s]+?)\.md(?P<suffix>[#?][^)\s]+)?\)"
)

# Mirrors the replacement rules listed in docs-relative-links branch commits.
HTML_PATH_AND_SUFFIX_RE = r"(?P<path>.+?)\.html(?P<suffix>[#?][^)\s]+)?"
PATH_AND_SUFFIX_REPLACEMENT = r"\g<path>\g<suffix>"


def relative_link_rule(domain: str, prefix: str) -> tuple[re.Pattern[str], str]:
    pattern = re.compile(rf"https://{re.escape(domain)}/{HTML_PATH_AND_SUFFIX_RE}")
    replacement = f"/{prefix}/{PATH_AND_SUFFIX_REPLACEMENT}"
    return (pattern, replacement)


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

    content = QUICKLINK_RE.sub(replace_quicklink, content)
    content = MARKDOWN_LINK_MD_RE.sub(r"\1\g<path>\g<suffix>)", content)
    content = HEADING_ANCHOR_RE.sub(replace_heading_anchor, content)

    DOCS_RELATIVE_LINK_REPLACEMENTS = [
        relative_link_rule("docs.ankiweb.net", "manual"),
        relative_link_rule("addon-docs.ankiweb.net", "addons"),
        relative_link_rule("faqs.ankiweb.net", "faqs"),
        relative_link_rule("docs.ankimobile.net", "ankimobile"),
    ]

    for pattern, replacement in DOCS_RELATIVE_LINK_REPLACEMENTS:
        if language_code:
            replacement = f"/{language_code}{replacement}"
        content = pattern.sub(replacement, content)

    content = content.replace("{", "\{").replace("}", "\}")
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
    soup = BeautifulSoup(raw, "html.parser")

    for node in soup.find_all(string=True):
        # Skip script/style content if you want
        if node.parent and node.parent.name in {"script", "style"}:
            continue
        escaped = html_lib.escape(str(node), quote=False)  # < and > only
        node.replace_with(NavigableString(escaped))

    return str(soup)


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

    language_code = "en"
    if language_code == "en":
        language_code = ""

    DOCS_SITE_DIR = Path("docs-site")
    DOCS_FILEPATH = DOCS_SITE_DIR / "docs.json"
    LANGUAGE_DIR = DOCS_SITE_DIR / language_code
    MANUAL_DEST_DIR = LANGUAGE_DIR / "manual"
    SRC_DOCS_DIR = Path("../anki-manual/src").resolve()

    print(str(MANUAL_DEST_DIR))

    with open(DOCS_FILEPATH) as f:
        site_structure = json.load(f)

    # print(site_structure)

    default_language = find_first(
        site_structure["navigation"]["languages"],
        lambda lang: lang["language"] == "en",
        "language 'en'",
    )
    manual_tab = find_first(
        default_language["tabs"],
        lambda tab: tab["tab"] == "Manual",
        "Manual tab",
    )
    main_group = manual_tab["groups"][0]
    default_language_pages = group_pages(main_group)

    source_contents = sorted(
        path.resolve()
        for path in SRC_DOCS_DIR.rglob("*")
        if path.is_file() and path.suffix in {".md", ".mdx"}
    )

    paths: list[Page] = []
    for path in source_contents:
        relative_src = path.relative_to(SRC_DOCS_DIR)
        root_dest = Path("manual") / relative_src.with_suffix("")
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

    new_language = deepcopy(default_language)
    new_language["language"] = language_code
    new_group = new_language["tabs"][0]["groups"][0]

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
    site_structure["navigation"]["languages"] = [
        lang
        for lang in site_structure["navigation"]["languages"]
        if lang["language"] != language_code
    ]
    new_language["tabs"][0]["groups"] = [new_group]
    # Manual only
    new_language["tabs"] = [new_language["tabs"][0]]
    site_structure["navigation"]["languages"].append(new_language)

    with open(DOCS_FILEPATH, "w") as f:
        json.dump(site_structure, f, indent=4)


if __name__ == "__main__":
    main()
