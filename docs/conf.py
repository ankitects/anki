from pathlib import Path

project = "Anki"
copyright = "Ankitects Pty Ltd and contributors"
author = "Ankitects Pty Ltd and contributors"

ROOT = Path(__file__).resolve().parents[1]

extensions = [
    "myst_parser",
    "sphinx.ext.intersphinx",
    "sphinxcontrib_rust",
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
autoapi_dirs = [str(ROOT / "pylib" / "anki")]
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

rust_crates = {
    "anki": str(ROOT / "rslib"),
    "anki_io": str(ROOT / "rslib" / "io"),
}
rust_doc_dir = str(Path(__file__).resolve().parent / "rust")
rust_generate_mode = "always"
rust_visibility = "pub"
rust_rustdoc_fmt = "rst"
rust_strip_src = True

exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
