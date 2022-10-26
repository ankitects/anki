"""
Replace @com_google_protobuf with a platform binary.

Avoids the long initial compile, but will fail if anything depends on the protobuf library.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

def _impl(rctx):
    rctx.file("BUILD.bazel", """
alias(
    name = "protoc",
    actual = select({
        "@ankidesktop//platforms:windows_x86_64": "@protoc_bin_windows//:bin/protoc.exe",
        "@ankidesktop//platforms:macos_arm64": "@protoc_bin_macos//:bin/protoc",
        "@ankidesktop//platforms:macos_x86_64": "@protoc_bin_macos//:bin/protoc",
        "@ankidesktop//platforms:linux_x86_64": "@protoc_bin_linux_x86_64//:bin/protoc",
        "@ankidesktop//platforms:linux_arm64": "@protoc_bin_linux_arm64//:bin/protoc"
    }),
    visibility = ["//visibility:public"]
)
""")

_setup_protoc = repository_rule(
    implementation = _impl,
    local = False,
    attrs = {},
)

def setup_protobuf_binary(name):
    maybe(
        http_archive,
        name = "protoc_bin_macos",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v21.8/protoc-21.8-osx-universal_binary.zip",
        ],
        sha256 = "e3324d3bc2e9bc967a0bec2472e0ec73b26f952c7c87f2403197414f780c3c6c",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_linux_x86_64",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v21.8/protoc-21.8-linux-x86_64.zip",
        ],
        sha256 = "f90d0dd59065fef94374745627336d622702b67f0319f96cee894d41a974d47a",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_linux_arm64",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v21.8/protoc-21.8-linux-aarch_64.zip",
        ],
        sha256 = "f3d8eb5839d6186392d8c7b54fbeabbb6fcdd90618a500b77cb2e24faa245cad",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_windows",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v21.8/protoc-21.8-win64.zip",
        ],
        sha256 = "3657053024faa439ff5f8c1dd2ee06bac0f9b9a3d660e99944f015a7451e87ec",
        build_file_content = """exports_files(["bin/protoc.exe"])""",
    )

    if not native.existing_rule(name):
        _setup_protoc(
            name = name,
        )
