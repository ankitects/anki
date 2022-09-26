"""Repo setup that can't happen until after defs.bzl:setup_deps() is run."""

load("@py_deps//:requirements.bzl", "install_deps")
load("@build_bazel_rules_nodejs//toolchains/esbuild:esbuild_repositories.bzl", "esbuild_repositories")

def setup_late_deps():
    install_deps()
    esbuild_repositories()
