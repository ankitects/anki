"""Repo setup that can't happen until after defs.bzl:setup_deps() is run."""

load("@py_deps//:requirements.bzl", "pip_install")
load("@npm//@bazel/labs:package.bzl", "npm_bazel_labs_dependencies")

def setup_late_deps():
    pip_install()
    npm_bazel_labs_dependencies()
