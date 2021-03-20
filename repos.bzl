"""
Dependencies required to build Anki.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

def register_repos():
    "Register required dependency repos."

    # bazel
    ##########

    maybe(
        http_archive,
        name = "bazel_skylib",
        sha256 = "97e70364e9249702246c0e9444bccdc4b847bed1eb03c5a3ece4f83dfe6abc44",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.0.2/bazel-skylib-1.0.2.tar.gz",
            "https://github.com/bazelbuild/bazel-skylib/releases/download/1.0.2/bazel-skylib-1.0.2.tar.gz",
        ],
    )

    # rust
    ########

    # native.local_repository(
    #     name = "io_bazel_rules_rust",
    #     path = "../rules_rust",
    # )

    maybe(
        http_archive,
        name = "io_bazel_rules_rust",
        strip_prefix = "rules_rust-anki-2020-12-10",
        urls = [
            "https://github.com/ankitects/rules_rust/archive/anki-2020-12-10.tar.gz",
        ],
        sha256 = "80a7647c3c1992c434a462bf424b9138c3c9af6c794ac112f636ca7c8c53180e",
    )

    # python
    ##########

    # native.local_repository(
    #     name = "rules_python",
    #     path = "../rules_python",
    # )

    maybe(
        http_archive,
        name = "rules_python",
        strip_prefix = "rules_python-anki-2020-11-04",
        urls = [
            "https://github.com/ankitects/rules_python/archive/anki-2020-11-04.tar.gz",
        ],
        sha256 = "00e444dc3872a87838c2cb0cf50a15d92ca669385b72998f796d2fd6814356a3",
    )

    # native.local_repository(
    #     name = "com_github_ali5h_rules_pip",
    #     path = "../rules_pip",
    # )

    maybe(
        http_archive,
        name = "com_github_ali5h_rules_pip",
        strip_prefix = "rules_pip-anki-2020-11-30",
        urls = [
            "https://github.com/ankitects/rules_pip/archive/anki-2020-11-30.tar.gz",
        ],
        sha256 = "ab4f10967eb87985383a4172d4533dde568b3ff502aa550239eeccead249325b",
    )

    # javascript
    ##############

    # maybe(
    #     http_archive,
    #     name = "build_bazel_rules_nodejs",
    #     urls = [
    #         "file:///c:/anki/release.tar.gz",
    #         "file:///Users/dae/Work/code/dtop/release.tar.gz",
    #     ],
    # )

    http_archive(
        name = "build_bazel_rules_nodejs",
        sha256 = "55a25a762fcf9c9b88ab54436581e671bc9f4f523cb5a1bd32459ebec7be68a8",
        urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/3.2.2/rules_nodejs-3.2.2.tar.gz"],
    )

    _ESBUILD_VERSION = "0.8.48"  # reminder: update SHAs below when changing this value

    http_archive(
        name = "esbuild_darwin",
        urls = [
            "https://registry.npmjs.org/esbuild-darwin-64/-/esbuild-darwin-64-%s.tgz" % _ESBUILD_VERSION,
        ],
        strip_prefix = "package",
        build_file_content = """exports_files(["bin/esbuild"])""",
        sha256 = "d21a722873ed24586f071973b77223553fca466946f3d7e3976eeaccb14424e6",
    )

    http_archive(
        name = "esbuild_windows",
        urls = [
            "https://registry.npmjs.org/esbuild-windows-64/-/esbuild-windows-64-%s.tgz" % _ESBUILD_VERSION,
        ],
        strip_prefix = "package",
        build_file_content = """exports_files(["esbuild.exe"])""",
        sha256 = "fe5dcb97b4c47f9567012f0a45c19c655f3d2e0d76932f6dd12715dbebbd6eb0",
    )

    http_archive(
        name = "esbuild_linux",
        urls = [
            "https://registry.npmjs.org/esbuild-linux-64/-/esbuild-linux-64-%s.tgz" % _ESBUILD_VERSION,
        ],
        strip_prefix = "package",
        build_file_content = """exports_files(["bin/esbuild"])""",
        sha256 = "60dabe141e5dfcf99e7113bded6012868132068a582a102b258fb7b1cfdac14b",
    )

    # sass
    ############

    # native.local_repository(
    #     name = "io_bazel_rules_sass",
    #     path = "../rules_sass",
    # )

    maybe(
        http_archive,
        name = "io_bazel_rules_sass",
        strip_prefix = "rules_sass-anki-2020-12-23",
        urls = [
            "https://github.com/ankitects/rules_sass/archive/anki-2020-12-23.tar.gz",
        ],
        sha256 = "224ae14b8d2166b3ab4c5fa9b2ae1828f30620ac628dc152e6c0859c7853bb97",
    )

    # translations
    ################

    core_i18n_repo = "anki-core-i18n"
    core_i18n_commit = "08132e398863b7c57ac3345c34c96db7f346a385"
    core_i18n_zip_csum = "24ed30d44bd277f0b19080084cb2bc5bf2cd8f6e691362928e7cb96f33ffda4c"

    qtftl_i18n_repo = "anki-desktop-ftl"
    qtftl_i18n_commit = "3668fb523ffa8162d5de878e6037f6cf9a98f97d"
    qtftl_i18n_zip_csum = "2cb5b89d125edd1d3a6d7349b8aa2f1dc3a0a007aaf3d1f4ca08ea353e6676ee"

    i18n_build_content = """
filegroup(
    name = "files",
    srcs = glob(["**/*.ftl"]),
    visibility = ["//visibility:public"],
)
exports_files(["l10n.toml"])
"""

    maybe(
        http_archive,
        name = "rslib_ftl",
        build_file_content = i18n_build_content,
        strip_prefix = core_i18n_repo + "-" + core_i18n_commit,
        urls = [
            "https://github.com/ankitects/{}/archive/{}.zip".format(
                core_i18n_repo,
                core_i18n_commit,
            ),
        ],
        sha256 = core_i18n_zip_csum,
    )

    maybe(
        http_archive,
        name = "extra_ftl",
        build_file_content = i18n_build_content,
        strip_prefix = qtftl_i18n_repo + "-" + qtftl_i18n_commit,
        urls = [
            "https://github.com/ankitects/{}/archive/{}.zip".format(
                qtftl_i18n_repo,
                qtftl_i18n_commit,
            ),
        ],
        sha256 = qtftl_i18n_zip_csum,
    )
