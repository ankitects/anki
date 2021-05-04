"""Repo setup that can't happen until after defs.bzl:setup_deps() is run."""

load("@py_deps//:requirements.bzl", "pip_install")
load("@rules_rust//tools/rust_analyzer/raze:crates.bzl", "rules_rust_tools_rust_analyzer_fetch_remote_crates")

def setup_late_deps():
    pip_install()
    rules_rust_tools_rust_analyzer_fetch_remote_crates()
