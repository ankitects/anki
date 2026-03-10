# Show available commands
default:
    @just --list

# Run all tests (Rust, Python, TypeScript)
test:
    {{ ninja }} check:rust_test check:pytest:pylib check:pytest:aqt check:vitest

# Run static checks (linting, formatting, type checking)
lint:
    {{ ninja }} \
        check:clippy \
        check:mypy \
        check:ruff \
        check:eslint \
        check:svelte \
        check:format:rust \
        check:format:python:pylib \
        check:format:python:qt \
        check:format:python:tools \
        check:format:dprint \
        check:format:prettier \
        check:format:sql

# Run minilints (copyright, contributors, licenses)
minilints:
    {{ ninja }} check:minilints

# Build the project
build:
    {{ ninja }} pylib qt

# Build wheels (needed for some platforms)
wheels:
    {{ ninja }} wheels

# Build and run all checks (lint + test) - lets ninja handle dependencies
check:
    {{ ninja }} pylib qt check

# Helper to get the right ninja command for the platform
ninja := if os() == "windows" { "tools\\ninja" } else { "./ninja" }
