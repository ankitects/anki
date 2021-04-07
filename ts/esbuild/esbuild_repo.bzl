""" Generated code; do not edit
Update by running yarn update-esbuild-versions

Helper macro for fetching esbuild versions for internal tests and examples in rules_nodejs
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

_VERSION = "0.11.5"

def esbuild_dependencies():
    """Helper to install required dependencies for the esbuild rules"""

    version = _VERSION

    http_archive(
        name = "esbuild_darwin",
        urls = [
            "https://registry.npmjs.org/esbuild-darwin-64/-/esbuild-darwin-64-%s.tgz" % version,
        ],
        strip_prefix = "package",
        build_file_content = """exports_files(["bin/esbuild"])""",
        sha256 = "98436890727bdb0d4beddd9c9e07d0aeff0e8dfe0169f85e568eca0dd43f665e",
    )
    http_archive(
        name = "esbuild_windows",
        urls = [
            "https://registry.npmjs.org/esbuild-windows-64/-/esbuild-windows-64-%s.tgz" % version,
        ],
        strip_prefix = "package",
        build_file_content = """exports_files(["esbuild.exe"])""",
        sha256 = "589c8ff97210bd41de106e6317ce88f9e88d2cacfd8178ae1217f2b857ff6c3a",
    )
    http_archive(
        name = "esbuild_linux",
        urls = [
            "https://registry.npmjs.org/esbuild-linux-64/-/esbuild-linux-64-%s.tgz" % version,
        ],
        strip_prefix = "package",
        build_file_content = """exports_files(["bin/esbuild"])""",
        sha256 = "113c2e84895f4422a3676db4e15d9f01b2b4fac041edab25284fdb9574ba58a0",
    )
