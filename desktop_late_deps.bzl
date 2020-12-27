"""Repo setup that can't happen until after defs.bzl:setup_deps() is run."""

load("@npm//@bazel/labs:package.bzl", "npm_bazel_labs_dependencies")

def setup_late_deps():
    npm_bazel_labs_dependencies()
