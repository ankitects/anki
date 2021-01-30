load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")
load("@bazel_skylib//lib:versions.bzl", "versions")
load("@io_bazel_rules_rust//rust:repositories.bzl", "rust_repositories")
load("@net_ankiweb_anki//cargo:crates.bzl", "raze_fetch_remote_crates")
load(":python.bzl", "setup_local_python")
load(":protobuf.bzl", "setup_protobuf_binary")
load("//rslib:clang_format.bzl", "setup_clang_format")
load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "yarn_install")
load("@io_bazel_rules_sass//:defs.bzl", "sass_repositories")
load("@build_bazel_rules_svelte//:defs.bzl", "rules_svelte_dependencies")
load("@com_github_ali5h_rules_pip//:defs.bzl", "pip_import")
load("//pip/pyqt5:defs.bzl", "install_pyqt5")

anki_version = "2.1.40"

def setup_deps():
    bazel_skylib_workspace()

    versions.check(minimum_bazel_version = "3.7.0")

    rust_repositories(
        edition = "2018",
        version = "1.48.0",
    )

    raze_fetch_remote_crates()

    setup_local_python(name = "python")

    setup_protobuf_binary(name = "com_google_protobuf")

    setup_clang_format(name = "clang_format")

    native.register_toolchains("@python//:python3_toolchain")

    pip_import(
        name = "py_deps",
        requirements = "@net_ankiweb_anki//pip:requirements.txt",
        python_runtime = "@python//:python",
    )

    install_pyqt5(
        name = "pyqt5",
        python_runtime = "@python//:python",
    )

    node_repositories(package_json = ["@net_ankiweb_anki//ts:package.json"])

    yarn_install(
        name = "npm",
        package_json = "@net_ankiweb_anki//ts:package.json",
        yarn_lock = "@net_ankiweb_anki//ts:yarn.lock",
    )

    sass_repositories()

    rules_svelte_dependencies()
