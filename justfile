set windows-shell := ["cmd.exe", "/c"]

# Show available commands
default:
    @just --list

# Run all tests (Rust, Python, TypeScript)
test:
    {{ ninja }} check:rust_test check:pytest check:vitest

# Run format checks only (fast, no build needed)
fmt:
    {{ ninja }} check:format

# Run linting and type checking (requires build outputs)
lint:
    {{ ninja }} \
        check:clippy \
        check:mypy \
        check:ruff \
        check:eslint \
        check:svelte \
        check:typescript

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
