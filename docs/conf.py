from pathlib import Path

project = "Anki"
copyright = "Ankitects Pty Ltd and contributors"
author = "Ankitects Pty Ltd and contributors"

ROOT = Path(__file__).resolve().parents[1]

extensions = [
    "myst_parser",
    "sphinx.ext.intersphinx",
    "autoapi.extension",
]

source_suffix = {
    ".rst": "restructuredtext",
    ".md": "markdown",
}

myst_enable_extensions = ["colon_fence", "deflist"]
html_theme = "sphinx_book_theme"
html_theme_options = {
    "repository_url": "https://github.com/ankitects/anki",
    "use_repository_button": True,
    "use_issues_button": True,
    "use_edit_page_button": True,
    "path_to_docs": "docs",
    "repository_branch": "main",
}

autoapi_type = "python"
autoapi_dirs = [str(ROOT / "pylib" / "anki"), str(ROOT / "qt" / "aqt")]
autoapi_root = "autoapi"
autoapi_keep_files = True
autoapi_add_toctree_entry = False
autoapi_python_use_implicit_namespaces = True
autoapi_options = [
    "members",
    "undoc-members",
    "show-inheritance",
    "show-module-summary",
]

exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
