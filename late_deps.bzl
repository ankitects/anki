"""Repo setup that can't happen until after defs.bzl:setup_deps() is run."""

load("@py_deps//:requirements.bzl", "pip_install")

def setup_late_deps():
    pip_install()
