load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")
load("@bazel_skylib//lib:versions.bzl", "versions")
load("@rules_rust//rust:repositories.bzl", "rust_repositories")
load("@ankidesktop//cargo:crates.bzl", "raze_fetch_remote_crates")
load(":python.bzl", "setup_local_python")
load("//proto:protobuf.bzl", "setup_protobuf_binary")
load("//proto:clang_format.bzl", "setup_clang_format")
load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "yarn_install")
load("@io_bazel_rules_sass//:defs.bzl", "sass_repositories")
load("//pip/pyqt5:defs.bzl", "install_pyqt5")
load("//pip/pyqt6:defs.bzl", "install_pyqt6")
load("@rules_python//python:pip.bzl", "pip_parse")

anki_version = "2.1.49"

def setup_deps():
    bazel_skylib_workspace()

    versions.check(minimum_bazel_version = "3.7.0")

    rust_repositories(
        edition = "2018",
        include_rustc_srcs = False,
    )

    raze_fetch_remote_crates()

    setup_local_python(name = "python")

    setup_protobuf_binary(name = "com_google_protobuf")

    setup_clang_format(name = "clang_format")

    native.register_toolchains("@python//:python3_toolchain")

    pip_parse(
        name = "py_deps",
        requirements_lock = "@ankidesktop//pip:requirements.txt",
        python_interpreter_target = "@python//:python",
    )

    install_pyqt5(
        name = "pyqt5",
        python_runtime = "@python//:python",
    )

    install_pyqt6(
        name = "pyqt6",
        python_runtime = "@python//:python",
    )

    node_repositories(
        package_json = ["@ankidesktop//:package.json"],
        node_version = "16.10.0",
    )

    yarn_install(
        name = "npm",
        package_json = "@ankidesktop//:package.json",
        yarn_lock = "@ankidesktop//:yarn.lock",
    )

    sass_repositories()
