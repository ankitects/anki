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
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.19.4/protoc-3.19.4-osx-x86_64.zip",
        ],
        sha256 = "d8b55cf1e887917dd43c447d77bd5bd213faff1e18ac3a176b35558d86f7ffff",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_linux_x86_64",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.19.4/protoc-3.19.4-linux-x86_64.zip",
        ],
        sha256 = "058d29255a08f8661c8096c92961f3676218704cbd516d3916ec468e139cbd87",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_linux_arm64",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.19.4/protoc-3.19.4-linux-aarch_64.zip",
        ],
        sha256 = "95584939e733bdd6ffb8245616b2071f565cd4c28163b6c21c8f936a9ee20861",
        build_file_content = """exports_files(["bin/protoc"])""",
    )

    maybe(
        http_archive,
        name = "protoc_bin_windows",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.19.4/protoc-3.19.4-win64.zip",
        ],
        sha256 = "828d2bdfe410e988cfc46462bcabd34ffdda8cc172867989ec647eadc55b03b5",
        build_file_content = """exports_files(["bin/protoc.exe"])""",
    )

    if not native.existing_rule(name):
        _setup_protoc(
            name = name,
        )
