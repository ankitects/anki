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
        sha256 = "465fd9367992a9b9c4fba34a549773735da200903678b81b25f367982e8df376",
        strip_prefix = "protobuf-3.13.0",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.13.0/protobuf-all-3.13.0.tar.gz",
        ],
    )

    # rust
    ########

    maybe(
        git_repository,
        name = "io_bazel_rules_rust",
        commit = "504cde54248f518d5c98eb9f1e8db3546904ecb2",
        remote = "https://github.com/ankitects/rules_rust",
        shallow_since = "1606199575 +1000",
    )

    # native.local_repository(
    #     name = "io_bazel_rules_rust",
    #     path = "../rules_rust",
    # )

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
        commit = "5d1d7ae1b24f869062ff6bb490110a2e5a229988",
        remote = "https://github.com/ankitects/rules_pip",
        shallow_since = "1604116387 +1000",
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
        sha256 = "cd6c9880292fc83f1fd16ba33000974544b0fe0fccf3d5e15b2e3071ba011266",
        urls = ["https://github.com/ankitects/rules_nodejs/releases/download/runfiles-fix-release/release.tar.gz"],
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

    maybe(
        http_archive,
        name = "io_bazel_rules_sass",
        sha256 = "6e60fc1cf0805af2cdcce727a5eed3f238fb4df41b35ce581c57996947c0202c",
        strip_prefix = "rules_sass-1.26.12",
        url = "https://github.com/bazelbuild/rules_sass/archive/1.26.12.zip",
    )

    # svelte
    ##########

    maybe(
        git_repository,
        name = "build_bazel_rules_svelte",
        commit = "c28cd9e5d251a0ce47c68a6a2a11b075f3df8899",
        remote = "https://github.com/ankitects/rules_svelte",
        shallow_since = "1603950453 +1000",
    )

    # native.local_repository(
    #     name = "build_bazel_rules_svelte",
    #     path = "../rules_svelte",
    # )

    # translations
    ################

    core_i18n_commit = "b2e56d085e7ad649cee6c1d0444313bf2f8fe0a9"
    core_i18n_shallow_since = "1606026380 +1000"

    qtftl_i18n_commit = "3ee1e7eecc31d71fe05ea816e1595faedbd7d3f9"
    qtftl_i18n_shallow_since = "1606026380 +1000"

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
