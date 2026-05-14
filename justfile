set windows-shell := ["cmd.exe", "/c"]

mod release

# Show available commands
default:
    @just --list

# Build the project
build:
    {{ ninja }} pylib qt

# Build wheels (needed for some platforms)
wheels:
    {{ ninja }} wheels

# Build and run all checks (lint + test) - lets ninja handle dependencies
check:
    {{ ninja }} pylib qt check

# Run all tests (Rust, Python, TypeScript)
test:
    {{ ninja }} check:rust_test check:pytest check:vitest

# Run Python tests (pylib + qt). Pass --coverage to enforce coverage, and --html to include HTML reports.
[arg("coverage", long="coverage", value="--coverage")]
[arg("html", long="html", value="--html")]
test-py coverage='' html='':
    just {{ if coverage == "--coverage" { "_coverage-py " + html } else { "_test-py" } }}

[private]
_test-py:
    {{ ninja }} check:pytest

[private]
_coverage-py html='':
    {{ ninja }} pylib qt
    just _coverage-py-pylib {{ html }}
    just _coverage-py-qt {{ html }}

[private]
_coverage-py-pylib html='':
    mkdir -p out/coverage/python-pylib
    PYTHONPATH=out/pylib ANKI_TEST_MODE=1 out/pyenv/bin/python -m coverage run --source=pylib/anki --data-file=out/coverage/python-pylib/.coverage -m pytest -p no:cacheprovider pylib/tests
    out/pyenv/bin/python -m coverage json --data-file=out/coverage/python-pylib/.coverage -o out/coverage/python-pylib/coverage-summary.json
    out/pyenv/bin/python -m coverage report --data-file=out/coverage/python-pylib/.coverage --fail-under=65
    {{ if html == "--html" { "out/pyenv/bin/python -m coverage html --data-file=out/coverage/python-pylib/.coverage -d out/coverage/python-pylib/html --fail-under=65 && echo 'Python pylib coverage report: out/coverage/python-pylib/html/index.html'" } else { "true" } }}

[private]
_coverage-py-qt html='':
    mkdir -p out/coverage/python-qt
    PYTHONPATH=pylib:out/pylib:out/qt ANKI_TEST_MODE=1 out/pyenv/bin/python -m coverage run --source=qt/aqt --data-file=out/coverage/python-qt/.coverage -m pytest -p no:cacheprovider qt/tests
    out/pyenv/bin/python -m coverage json --data-file=out/coverage/python-qt/.coverage -o out/coverage/python-qt/coverage-summary.json
    out/pyenv/bin/python -m coverage report --data-file=out/coverage/python-qt/.coverage --fail-under=20
    {{ if html == "--html" { "out/pyenv/bin/python -m coverage html --data-file=out/coverage/python-qt/.coverage -d out/coverage/python-qt/html --fail-under=20 && echo 'Python qt coverage report: out/coverage/python-qt/html/index.html'" } else { "true" } }}

# Check formatting (fast, no build needed)
fmt:
    {{ ninja }} check:format

# Fix formatting
fix-fmt:
    {{ ninja }} format

# Run linting and type checking (requires build outputs)
lint:
    {{ ninja }} \
        check:clippy \
        check:mypy \
        check:ruff \
        check:eslint \
        check:svelte \
        check:typescript

# Fix auto-fixable lint issues (ruff + eslint)
fix-lint:
    {{ ninja }} fix:ruff fix:eslint

# Run minilints (copyright, contributors, licenses)
minilints:
    {{ ninja }} check:minilints

# Fix minilints (update licenses.json)
fix-minilints:
    {{ ninja }} fix:minilints

# Sync translation files
ftl-sync:
    {{ ninja }} ftl-sync

# Deprecate translation strings
ftl-deprecate:
    {{ ninja }} ftl-deprecate

# Build documentation site
docs:
    uv run --group docs sphinx-build -b html docs out/docs/html
    @echo "Docs built at out/docs/html/index.html"

# Build and serve documentation site
docs-serve:
    uv run --group docs sphinx-autobuild docs out/docs/html --host 127.0.0.1 --port 8000

# Build Rust API docs
docs-rust:
    cargo doc --open

# Dispatch CI workflow on a given branch or tag
ci branch:
    gh workflow run ci.yml --ref {{ branch }}

# Helper to get the right ninja command for the platform
ninja := if os() == "windows" { "tools\\ninja" } else { "./ninja" }
