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
        "@ankidesktop//platforms:macos_x86_64": "@protoc_bin_macos//:bin/protoc",
        "@ankidesktop//platforms:linux_x86_64": "@protoc_bin_linux_x86_64//:bin/protoc",
        "@ankidesktop//platforms:linux_arm64": "@protoc_bin_linux_arm64//:bin/protoc"
    }),
    visibility = ["//visibility:public"]
)
""")

_setup_protoc = repository_rule(
    implementation = _impl,
    local = True,
    attrs = {},
)

def setup_protobuf_binary(name):
    maybe(
        http_archive,
        name = "protoc_bin_macos",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.14.0/protoc-3.14.0-osx-x86_64.zip",
        ],
        sha256 = "699ceee7ef0988ecf72bf1c146dee5d9d89351a19d4093d30ebea3c04008bb8c",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_linux_x86_64",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.14.0/protoc-3.14.0-linux-x86_64.zip",
        ],
        sha256 = "a2900100ef9cda17d9c0bbf6a3c3592e809f9842f2d9f0d50e3fba7f3fc864f0",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_linux_arm64",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.14.0/protoc-3.14.0-linux-aarch_64.zip",
        ],
        sha256 = "67db019c10ad0a151373278383e4e9b756dc90c3cea6c1244d5d5bd230af7c1a",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_windows",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.14.0/protoc-3.14.0-win64.zip",
        ],
        sha256 = "642554ed4dd2dba94e1afddcccdd7d832999cea309299cc5952f13db389894f8",
        build_file_content = """exports_files(["bin/protoc.exe"])""",
    )

    if not native.existing_rule(name):
        _setup_protoc(
            name = name,
        )
