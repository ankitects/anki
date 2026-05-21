from __future__ import annotations

import argparse
import json
import re
import shutil
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Source:
    label: str
    repo: str
    source: Path
    target: Path
    summary: Path | None = None
    files: tuple[str, ...] = ("*.md",)
    archive_text: bool = False


@dataclass(frozen=True)
class Page:
    title: str
    path: str


SUMMARY_RE = re.compile(r"^\s*[-*]\s+\[(?P<title>[^\]]+)\]\((?P<path>[^)]+)\)")


def slug_title(path: Path) -> str:
    return path.stem.replace("-", " ").replace("_", " ").title()


def heading_title(content: str, fallback: str) -> str:
    match = re.search(r"^#\s+(.+)$", content, flags=re.MULTILINE)
    return match.group(1).strip() if match else fallback


def has_frontmatter(content: str) -> bool:
    return content.startswith("---\n")


def frontmatter(title: str, source: Source) -> str:
    data = {
        "title": title,
        "description": f"Migrated from {source.repo} for the Mintlify documentation POC.",
    }
    return "---\n" + "\n".join(f"{key}: {json.dumps(value)}" for key, value in data.items()) + "\n---\n\n"


def normalize_content(content: str) -> str:
    content = re.sub(r"<!--.*?-->", "", content, flags=re.DOTALL)
    content = re.sub(r"<(https?://[^>\s]+)>", r"[\1](\1)", content)
    content = fence_indented_code(content)
    replacements = {
        "{{#title ": "",
        "}}": "",
    }
    normalized = content
    for old, new in replacements.items():
        normalized = normalized.replace(old, new)
    return escape_mdx_expressions(normalized)


def fence_indented_code(content: str) -> str:
    lines = content.splitlines()
    fenced = []
    block = []
    in_fence = False
    for line in lines:
        if line.startswith("```") or line.startswith("~~~"):
            if block:
                fenced.extend(["```text", *block, "```"])
                block = []
            in_fence = not in_fence
            fenced.append(line)
            continue
        if in_fence:
            fenced.append(line)
            continue
        if line.startswith("    "):
            block.append(line[4:])
            continue
        if block:
            fenced.extend(["```text", *block, "```"])
            block = []
        fenced.append(line)
    if block:
        fenced.extend(["```text", *block, "```"])
    return "\n".join(fenced) + "\n"


def escape_mdx_expressions(content: str) -> str:
    escaped_lines = []
    in_fence = False
    for line in content.splitlines():
        if line.startswith("```") or line.startswith("~~~"):
            in_fence = not in_fence
            escaped_lines.append(line)
            continue
        if in_fence:
            escaped_lines.append(line)
            continue
        escaped_lines.append(
            line.replace("{", "&#123;")
            .replace("}", "&#125;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        )
    return "\n".join(escaped_lines) + "\n"


def archive_content(path: Path, source: Source) -> str:
    title = slug_title(path)
    content = path.read_text()
    return f"{frontmatter(title, source)}# {title}\n\n```text\n{content}\n```\n"


def migrated_content(path: Path, source: Source) -> str:
    if source.archive_text and path.suffix == ".txt":
        return archive_content(path, source)
    content = normalize_content(path.read_text())
    title = heading_title(content, slug_title(path))
    return content if has_frontmatter(content) else frontmatter(title, source) + content


def relative_page(root: Path, path: Path) -> str:
    return path.relative_to(root).with_suffix("").as_posix()


def parse_summary(source: Source, root: Path) -> list[Page]:
    if source.summary is None or not source.summary.exists():
        return []
    summary = source.summary.read_text()
    pages = []
    for line in summary.splitlines():
        match = SUMMARY_RE.match(line)
        if match is None:
            continue
        page_path = match.group("path").split("#", maxsplit=1)[0]
        if not page_path.endswith(".md"):
            continue
        target_path = source.target / Path(page_path).with_suffix(".mdx")
        pages.append(Page(match.group("title"), relative_page(root, target_path)))
    return pages


def markdown_paths(source: Source, pattern: str) -> list[Path]:
    if "**" in pattern:
        return sorted(source.source.glob(pattern))
    return sorted(source.source.rglob(pattern))


def copy_markdown(source: Source, root: Path) -> list[Page]:
    source.target.mkdir(parents=True, exist_ok=True)
    pages = []
    for pattern in source.files:
        for path in markdown_paths(source, pattern):
            if path.name == "SUMMARY.md":
                continue
            relative = path.relative_to(source.source).with_suffix(".mdx")
            target = source.target / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(migrated_content(path, source))
            pages.append(Page(heading_title(target.read_text(), slug_title(target)), relative_page(root, target)))
    return parse_summary(source, root) or pages


def copy_assets(source: Path, target: Path) -> None:
    if not source.exists():
        return
    shutil.copytree(source, target, dirs_exist_ok=True)


def index_page(title: str, description: str, pages: list[Page]) -> str:
    links = "\n".join(f"- [{page.title}](/{page.path})" for page in pages[:12])
    more = "\n\nMore pages are available in the sidebar." if len(pages) > 12 else ""
    return f"""---
title: "{title}"
description: "{description}"
---

# {title}

{description}

{links}{more}
"""


def write_index(root: Path, section: str, title: str, description: str, pages: list[Page]) -> Page:
    path = root / section / "index.mdx"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(index_page(title, description, pages))
    return Page(title, relative_page(root, path))


def navigation_group(group: str, index: Page, pages: list[Page]) -> dict[str, object]:
    return {"group": group, "pages": [index.path, *[page.path for page in pages]]}


def docs_json(groups: dict[str, tuple[Page, list[Page]]]) -> dict[str, object]:
    tabs = [
        ("Manual", "Desktop Manual", "manual"),
        ("AnkiMobile", "AnkiMobile Manual", "ankimobile"),
        ("FAQs", "Support FAQs", "faqs"),
        ("Add-ons", "Add-on Development", "addons"),
        ("Developers", "Core Development", "developers"),
        ("Translators", "Translation Guide", "translators"),
        ("Releases", "Release Notes", "releases"),
        ("Legacy", "Legacy Archive", "legacy"),
    ]
    return {
        "$schema": "https://mintlify.com/docs.json",
        "name": "Anki Docs",
        "theme": "mint",
        "colors": {"primary": "#0F766E", "light": "#14B8A6", "dark": "#0F172A"},
        "favicon": "/images/favicon.svg",
        "navigation": {
            "tabs": [
                {
                    "tab": tab,
                    "groups": [navigation_group(group, *groups[key])],
                }
                for tab, group, key in tabs
            ],
            "global": {
                "anchors": [
                    {"anchor": "Anki", "href": "https://apps.ankiweb.net", "icon": "house"},
                    {"anchor": "GitHub", "href": "https://github.com/ankitects/anki", "icon": "github"},
                ]
            },
        },
    }


def top_level_index() -> str:
    return """---
title: "Anki Docs"
description: "A unified Mintlify proof of concept for Anki manuals, FAQs, contributor docs, translation docs, and release notes."
---

# Anki Docs

This proof of concept brings Ankitects documentation sources into a single Mintlify site rooted in this repository.

<CardGroup cols={2}>
  <Card title="Manual" href="/manual/intro">
    Desktop Anki user documentation.
  </Card>
  <Card title="AnkiMobile" href="/ankimobile/intro">
    iPhone and iPad documentation.
  </Card>
  <Card title="FAQs" href="/faqs/getting-help">
    Support and troubleshooting articles.
  </Card>
  <Card title="Add-ons" href="/addons/intro">
    Add-on author documentation.
  </Card>
  <Card title="Developers" href="/developers/development">
    Core development, build, architecture, and API docs.
  </Card>
  <Card title="Translators" href="/translators/intro">
    Translation contributor guide.
  </Card>
</CardGroup>
"""


def sources(repo_root: Path, source_root: Path, docs_root: Path) -> tuple[Source, ...]:
    return (
        Source("Manual", "ankitects/anki-manual", source_root / "anki-manual/src", docs_root / "manual", source_root / "anki-manual/src/SUMMARY.md"),
        Source("AnkiMobile", "ankitects/ankimobile-docs", source_root / "ankimobile-docs/src", docs_root / "ankimobile", source_root / "ankimobile-docs/src/SUMMARY.md"),
        Source("FAQs", "ankitects/faqs", source_root / "faqs/src", docs_root / "faqs", source_root / "faqs/src/SUMMARY.md"),
        Source("Add-ons", "ankitects/addon-docs", source_root / "addon-docs/src", docs_root / "addons", source_root / "addon-docs/src/SUMMARY.md"),
        Source("Translators", "ankitects/translating", source_root / "translating/src", docs_root / "translators", source_root / "translating/src/SUMMARY.md"),
        Source("Changes", "ankitects/anki-changes", source_root / "anki-changes/src", docs_root / "releases/changes", source_root / "anki-changes/src/SUMMARY.md"),
        Source("Betas", "ankitects/anki-betas", source_root / "anki-betas/src", docs_root / "releases/betas", source_root / "anki-betas/src/SUMMARY.md"),
        Source("Developers", "ankitects/anki", repo_root / "docs", docs_root / "developers", None, ("*.md",)),
        Source("Legacy", "ankitects/anki-docs", source_root / "anki-docs", docs_root / "legacy/anki-docs", None, ("*.txt", "*.md"), True),
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--docs-root", type=Path, default=Path("docs-site"))
    args = parser.parse_args()

    repo_root = Path.cwd()
    docs_root = repo_root / args.docs_root
    shutil.rmtree(docs_root, ignore_errors=True)
    docs_root.mkdir()
    (docs_root / "index.mdx").write_text(top_level_index())

    copied = {source.label: copy_markdown(source, docs_root) for source in sources(repo_root, args.source_root, docs_root)}
    copy_assets(repo_root / "docs/_static", docs_root / "images")

    release_pages = [*copied["Changes"], *copied["Betas"]]
    legacy_pages = copied["Legacy"]
    groups = {
        "manual": (write_index(docs_root, "manual", "Desktop Manual", "User documentation for desktop Anki.", copied["Manual"]), copied["Manual"]),
        "ankimobile": (write_index(docs_root, "ankimobile", "AnkiMobile Manual", "User documentation for AnkiMobile on iPhone and iPad.", copied["AnkiMobile"]), copied["AnkiMobile"]),
        "faqs": (write_index(docs_root, "faqs", "Support FAQs", "Troubleshooting articles and support answers.", copied["FAQs"]), copied["FAQs"]),
        "addons": (write_index(docs_root, "addons", "Add-on Development", "Documentation for writing and maintaining Anki add-ons.", copied["Add-ons"]), copied["Add-ons"]),
        "developers": (write_index(docs_root, "developers", "Core Development", "Build, contribution, architecture, and generated API documentation.", copied["Developers"]), copied["Developers"]),
        "translators": (write_index(docs_root, "translators", "Translation Guide", "Documentation for translating Anki and related docs.", copied["Translators"]), copied["Translators"]),
        "releases": (write_index(docs_root, "releases", "Release Notes", "Stable change notes and beta documentation.", release_pages), release_pages),
        "legacy": (write_index(docs_root, "legacy", "Legacy Archive", "Older documentation sources kept for migration reference.", legacy_pages), legacy_pages),
    }
    (docs_root / "docs.json").write_text(json.dumps(docs_json(groups), indent=2) + "\n")


if __name__ == "__main__":
    main()
