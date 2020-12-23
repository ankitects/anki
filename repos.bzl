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

    # protobuf
    ############

    maybe(
        http_archive,
        name = "com_google_protobuf",
        sha256 = "6dd0f6b20094910fbb7f1f7908688df01af2d4f6c5c21331b9f636048674aebf",
        strip_prefix = "protobuf-3.14.0",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.14.0/protobuf-all-3.14.0.tar.gz",
        ],
    )

    # rust
    ########

    # native.local_repository(
    #     name = "io_bazel_rules_rust",
    #     path = "../rules_rust",
    # )

    maybe(
        git_repository,
        name = "io_bazel_rules_rust",
        commit = "dfd1200fcdcc0d56d725818ed3a66316517f20a6",
        remote = "https://github.com/ankitects/rules_rust",
        shallow_since = "1607578413 +1000",
    )

    # python
    ##########

    maybe(
        git_repository,
        name = "rules_python",
        commit = "3927c9bce90f629eb5ab08bbc99a3d3bda1d95c0",
        remote = "https://github.com/ankitects/rules_python",
        shallow_since = "1604408056 +1000",
    )

    # native.local_repository(
    #     name = "rules_python",
    #     path = "../rules_python",
    # )

    maybe(
        git_repository,
        name = "com_github_ali5h_rules_pip",
        commit = "73953e06fdacb565f224c66f0683a7d8d0ede223",
        remote = "https://github.com/ankitects/rules_pip",
        shallow_since = "1606453171 +1000",
    )

    # native.local_repository(
    #     name = "com_github_ali5h_rules_pip",
    #     path = "../rules_pip",
    # )

    # javascript
    ##############

    maybe(
        http_archive,
        name = "build_bazel_rules_nodejs",
        sha256 = "6142e9586162b179fdd570a55e50d1332e7d9c030efd853453438d607569721d",
        urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/3.0.0/rules_nodejs-3.0.0.tar.gz"],
    )

    # maybe(
    #     http_archive,
    #     name = "build_bazel_rules_nodejs",
    #     #        sha256 = "64a71a64ac58b8969bb19b1c9258a973b6433913e958964da698943fb5521d98",
    #     urls = [
    #         "file:///c:/anki/release.tar.gz",
    #         "file:///Users/dae/Work/code/dtop/release.tar.gz",
    #     ],
    # )

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

    # svelte
    ##########

    # native.local_repository(
    #     name = "build_bazel_rules_svelte",
    #     path = "../rules_svelte",
    # )

    maybe(
        http_archive,
        name = "build_bazel_rules_svelte",
        strip_prefix = "rules_svelte-anki-2020-12-23",
        urls = [
            "https://github.com/ankitects/rules_svelte/archive/anki-2020-12-23.tar.gz",
        ],
        sha256 = "eb0e910579b71242b44480b5dcc34c63d9a530d6fb7913139759ef397ff30bb2",
    )

    # translations
    ################

    core_i18n_commit = "b1c03cebb554e8568529e293756ac36cdf62341a"
    core_i18n_shallow_since = "1608607833 +1000"

    qtftl_i18n_commit = "e7dda1058c0510665f2ea8d8ffd74e506e578f7a"
    qtftl_i18n_shallow_since = "1608607833 +1000"

    i18n_build_content = """
filegroup(
    name = "files",
    srcs = glob(["**/*.ftl"]),
    visibility = ["//visibility:public"],
)
exports_files(["l10n.toml"])
"""

    maybe(
        new_git_repository,
        name = "rslib_ftl",
        build_file_content = i18n_build_content,
        commit = core_i18n_commit,
        shallow_since = core_i18n_shallow_since,
        remote = "https://github.com/ankitects/anki-core-i18n",
    )

    maybe(
        new_git_repository,
        name = "extra_ftl",
        build_file_content = i18n_build_content,
        commit = qtftl_i18n_commit,
        shallow_since = qtftl_i18n_shallow_since,
        remote = "https://github.com/ankitects/anki-desktop-ftl",
    )
