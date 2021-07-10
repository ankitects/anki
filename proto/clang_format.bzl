# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Exposes a clang-format binary for formatting protobuf.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("@rules_python//python:defs.bzl", "py_test")

def _impl(rctx):
    rctx.file("BUILD.bazel", """
alias(
    name = "clang_format",
    actual = select({
        "@ankidesktop//platforms:windows_x86_64": "@clang_format_windows_x86_64//:clang-format.exe",
        "@ankidesktop//platforms:macos_x86_64": "@clang_format_macos_x86_64//:clang-format",
        "@ankidesktop//platforms:linux_x86_64": "@clang_format_linux_x86_64//:clang-format",
    }),
    visibility = ["//visibility:public"]
)
""")

_setup_clang_format = repository_rule(
    attrs = {},
    local = True,
    implementation = _impl,
)

def setup_clang_format(name):
    maybe(
        http_archive,
        name = "clang_format_macos_x86_64",
        build_file_content = """exports_files(["clang-format"])""",
        sha256 = "238be68d9478163a945754f06a213483473044f5a004c4125d3d9d8d3556466e",
        urls = [
            "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_macos_x86_64.zip",
        ],
    )

    maybe(
        http_archive,
        name = "clang_format_linux_x86_64",
        build_file_content = """exports_files(["clang-format"])""",
        sha256 = "64060bc4dbca30d0d96aab9344e2783008b16e1cae019a2532f1126ca5ec5449",
        urls = [
            "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_linux_x86_64.zip",
        ],
    )

    maybe(
        http_archive,
        name = "clang_format_windows_x86_64",
        build_file_content = """exports_files(["clang-format.exe"])""",
        sha256 = "7d9f6915e3f0fb72407830f0fc37141308d2e6915daba72987a52f309fbeaccc",
        urls = [
            "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_windows_x86_64.zip",
        ],
    )

    if not native.existing_rule(name):
        _setup_clang_format(
            name = name,
        )

def proto_format(name, srcs, **kwargs):
    py_test(
        name = name,
        srcs = [
            "@ankidesktop//proto:format.py",
        ],
        data = ["@clang_format//:clang_format"] + srcs,
        args = ["$(location @clang_format//:clang_format)"] + [native.package_name() + "/" + f for f in srcs],
        **kwargs
    )
