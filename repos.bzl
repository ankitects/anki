"""
Dependencies required to build Anki.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")

def register_repos():
    "Register required dependency repos."

    # bazel
    ##########

    http_archive(
        name = "bazel_skylib",
        sha256 = "97e70364e9249702246c0e9444bccdc4b847bed1eb03c5a3ece4f83dfe6abc44",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.0.2/bazel-skylib-1.0.2.tar.gz",
            "https://github.com/bazelbuild/bazel-skylib/releases/download/1.0.2/bazel-skylib-1.0.2.tar.gz",
        ],
    )

    # protobuf
    ############

    http_archive(
        name = "com_google_protobuf",
        sha256 = "465fd9367992a9b9c4fba34a549773735da200903678b81b25f367982e8df376",
        strip_prefix = "protobuf-3.13.0",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v3.13.0/protobuf-all-3.13.0.tar.gz",
        ],
    )

    # rust
    ########

    git_repository(
        name = "io_bazel_rules_rust",
        commit = "a364ded42d9788144cd26b6e98d6b4038753bfa9",
        remote = "https://github.com/ankitects/rules_rust",
        shallow_since = "1604550071 +1000",
    )

    # local_repository(
    #     name = "io_bazel_rules_rust",
    #     path = "../rules_rust",
    # )

    # python
    ##########

    git_repository(
        name = "rules_python",
        commit = "3927c9bce90f629eb5ab08bbc99a3d3bda1d95c0",
        remote = "https://github.com/ankitects/rules_python",
        shallow_since = "1604408056 +1000",
    )

    # native.local_repository(
    #     name = "rules_python",
    #     path = "../rules_python",
    # )

    git_repository(
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

    http_archive(
        name = "build_bazel_rules_nodejs",
        sha256 = "cd6c9880292fc83f1fd16ba33000974544b0fe0fccf3d5e15b2e3071ba011266",
        urls = ["https://github.com/ankitects/rules_nodejs/releases/download/runfiles-fix-release/release.tar.gz"],
    )

    # http_archive(
    #     name = "build_bazel_rules_nodejs",
    #     #        sha256 = "64a71a64ac58b8969bb19b1c9258a973b6433913e958964da698943fb5521d98",
    #     urls = [
    #         "file:///c:/anki/release.tar.gz",
    #         "file:///Users/dae/Work/code/dtop/release.tar.gz",
    #     ],
    # )

    # sass
    ############

    http_archive(
        name = "io_bazel_rules_sass",
        sha256 = "6e60fc1cf0805af2cdcce727a5eed3f238fb4df41b35ce581c57996947c0202c",
        strip_prefix = "rules_sass-1.26.12",
        url = "https://github.com/bazelbuild/rules_sass/archive/1.26.12.zip",
    )

    # svelte
    ##########

    git_repository(
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

    core_i18n_commit = "b9fdeadef0b92a6d9acadbce01e43cba53739df6"
    core_i18n_shallow_since = "1602372775 +0000"

    qtftl_i18n_commit = "51320cdc51fbfb3d60791467879069ea3a8188a6"
    qtftl_i18n_shallow_since = "1600900614 +0000"

    qtpo_i18n_commit = "710be3864b356ddf90253034c6acfcb420dffeff"
    qtpo_i18n_shallow_since = "1603966644 +0000"

    new_git_repository(
        name = "rslib_ftl",
        build_file_content = """
filegroup(
    name = "files",
    srcs = glob(["**/*.ftl"]),
    visibility = ["//visibility:public"],
)

exports_files(["l10n.toml"])
    """,
        commit = core_i18n_commit,
        shallow_since = core_i18n_shallow_since,
        remote = "https://github.com/ankitects/anki-core-i18n",
    )

    if not native.existing_rule("extra_ftl"):
        new_git_repository(
            name = "extra_ftl",
            build_file_content = """
filegroup(
    name = "files",
    srcs = glob(["**/*.ftl"]),
    visibility = ["//visibility:public"],
)

exports_files(["l10n.toml"])
""",
            commit = qtftl_i18n_commit,
            shallow_since = qtftl_i18n_shallow_since,
            remote = "https://github.com/ankitects/anki-desktop-ftl",
        )

    new_git_repository(
        name = "aqt_po",
        build_file_content = """
exports_files(glob(["**/*.pot", "**/*.po"]),
    visibility = ["//visibility:public"],
)    
    """,
        commit = qtpo_i18n_commit,
        shallow_since = qtpo_i18n_shallow_since,
        remote = "https://github.com/ankitects/anki-desktop-i18n",
    )
