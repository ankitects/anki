"""
Dependencies required to build Anki.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
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
    #     name = "rules_rust",
    #     path = "../rules_rust",
    # )

    maybe(
        http_archive,
        name = "rules_rust",
        strip_prefix = "rules_rust-adf2790f3ff063d909acd70aacdd2832756113a5",
        urls = [
            "https://github.com/bazelbuild/rules_rust/archive/adf2790f3ff063d909acd70aacdd2832756113a5.zip",
        ],
        sha256 = "7277e9e58ec157c233fa571e27f684402c1c0711370ef8bf379af63bd31cbe8b",
    )

    # maybe(
    #     http_archive,
    #     name = "rules_rust",
    #     strip_prefix = "rules_rust-anki-2021-12-20",
    #     urls = [
    #         "https://github.com/ankitects/rules_rust/archive/refs/tags/anki-2021-12-20.zip",
    #     ],
    #     sha256 = "c9300cb1d1eddc5b85d5ced35f4c332b08afc7a439d2b75e696d3282b80677af",
    # )

    # python
    ##########

    # native.local_repository(
    #     name = "rules_python",
    #     path = "../rules_python",
    # )

    maybe(
        http_archive,
        name = "rules_python",
        sha256 = "a30abdfc7126d497a7698c29c46ea9901c6392d6ed315171a6df5ce433aa4502",
        strip_prefix = "rules_python-0.6.0",
        url = "https://github.com/bazelbuild/rules_python/archive/0.6.0.tar.gz",
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

    # when updating, remember to update pinned versions in package.json
    maybe(
        http_archive,
        name = "build_bazel_rules_nodejs",
        sha256 = "2644a66772938db8d8c760334a252f1687455daa7e188073f2d46283f2f6fbb7",
        urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/4.6.2/rules_nodejs-4.6.2.tar.gz"],
    )

    maybe(
        http_archive,
        name = "rules_nodejs",
        sha256 = "f596117040134b9497a1049efe7a785924b4ff22557669780a0fa37e22b827bd",
        urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/4.6.2/rules_nodejs-core-4.6.2.tar.gz"],
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
        strip_prefix = "rules_sass-d0cda2205a6e9706ded30f7dd7d30c82b1301fbe",
        urls = [
            "https://github.com/bazelbuild/rules_sass/archive/d0cda2205a6e9706ded30f7dd7d30c82b1301fbe.zip",
        ],
        sha256 = "640ad20f878a6656968e35f35343359446db91a773224ddf52ae110f1e48bb20",
    )

    # translations
    ################

    core_i18n_repo = "anki-core-i18n"
    core_i18n_commit = "790e9c4d1730e4773b70640be84dab2d7e1aa993"
    core_i18n_zip_csum = "f3aaf9f7b33cab6a1677b3979514e480c7e45955459a62a4f2ed3811c8652a97"

    qtftl_i18n_repo = "anki-desktop-ftl"
    qtftl_i18n_commit = "12549835bd13c5a7565d01f118c05a12126471e0"
    qtftl_i18n_zip_csum = "9bd1a418b3bc92551e6543d68b639aa17b26048b735a2ee7b2becf36fd6003a4"

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

    # binary bundle
    ################

    maybe(
        http_archive,
        name = "pyoxidizer",
        sha256 = "9f7951473d88c7989dc80199146f82020226a3b2425474fd33b6bcbd8fdd1b1c",
        urls = [
            # when changing this, the commit hash needs to be updated in qt/bundle/Cargo.toml
            "https://github.com/ankitects/PyOxidizer/archive/refs/tags/anki-2021-12-08.tar.gz",
        ],
        strip_prefix = "PyOxidizer-anki-2021-12-08",
        build_file_content = " ",
    )

    maybe(
        http_archive,
        name = "bundle_extras_linux_amd64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/qt-plugins-linux-amd64.tar.gz",
        ],
        sha256 = "cbfb41fb750ae19b381f8137bd307e1167fdc68420052977f6e1887537a131b0",
    )

    maybe(
        http_archive,
        name = "audio_win_amd64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/audio-win-amd64.tar.gz",
        ],
        sha256 = "0815a601baba05e03bc36b568cdc2332b1cf4aa17125fc33c69de125f8dd687f",
    )

    maybe(
        http_archive,
        name = "protobuf_wheel_mac_arm64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/protobuf-wheel-mac-arm64.tar",
        ],
        sha256 = "401d1cd6d949af463b3945f0d5dc887185b27fa5478cb6847bf94f680ea797b4",
    )

    maybe(
        http_archive,
        name = "audio_mac_amd64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/audio-mac-amd64.tar.gz",
        ],
        sha256 = "d9310cbd6bed09d6d36deb8b7611bffbd161628512b1bf8d7becfdf78b5cd1dd",
    )

    maybe(
        http_archive,
        name = "audio_mac_arm64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/audio-mac-arm64.tar.gz",
        ],
        sha256 = "c30a772132a16fa79d9a1e60f5dce2f91fe8077e2709a8f39ef499d49f6a4b0e",
    )

    maybe(
        http_archive,
        name = "pyqt6.2_mac_bundle_amd64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/pyqt6.2-mac-amd64.tar.gz",
        ],
        sha256 = "c7bf899eee33fcb3b5848f5d3e5fc390012efc05c2308e4349b7bbd5939c85f0",
    )

    maybe(
        http_archive,
        name = "pyqt6.2_mac_bundle_arm64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/pyqt6.2-mac-arm64.tar.gz",
        ],
        sha256 = "7a4b7d5bd65c83fd16cf7e56929183ef0d1d7bb67f9deea8f2482d7378e0ea02",
    )

    maybe(
        http_archive,
        name = "pyqt5.14_mac_bundle_amd64",
        build_file_content = " ",
        urls = [
            "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/pyqt5.14-mac-amd64.tar.gz",
        ],
        sha256 = "474951bed79ddb9570ee4c5a6079041772551ea77e77171d9e33d6f5e7877ec1",
    )
