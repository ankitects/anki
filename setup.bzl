load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")
load("@io_bazel_rules_rust//rust:repositories.bzl", "rust_repositories")
load("@io_bazel_rules_rust//:workspace.bzl", "bazel_version")
load("@anki//cargo:crates.bzl", "raze_fetch_remote_crates")
load(":python.bzl", "setup_local_python")
load("@rules_python//python:repositories.bzl", "py_repositories")
load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "yarn_install")
load("@io_bazel_rules_sass//:defs.bzl", "sass_repositories")
load("@build_bazel_rules_svelte//:defs.bzl", "rules_svelte_dependencies")
load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")
load("@com_github_ali5h_rules_pip//:defs.bzl", "pip_import")
load("//pyqt5:defs.bzl", "install_pyqt5")

def setup_deps():
    bazel_skylib_workspace()

    rust_repositories(
        edition = "2018",
        # use_worker = True,
        version = "1.47.0",
    )

    bazel_version(name = "io_bazel_rules_rust_bazel_version")

    raze_fetch_remote_crates()

    setup_local_python(name = "python")

    native.register_toolchains("@python//:python3_toolchain")

    py_repositories()

    # pip_install(
    #     name = "py_deps",
    #     python_interpreter_target = "@python//:python",
    #     requirements = "@anki//pip:requirements.txt",
    # )

    pip_import(
        name = "py_deps",
        requirements = "@anki//pip:requirements.txt",
        python_runtime = "@python//:python",
        compile = True,
    )

    install_pyqt5(
        name = "pyqt5",
        python_runtime = "@python//:python",
    )

    node_repositories(package_json = ["//ts:package.json"])

    yarn_install(
        name = "npm",
        package_json = "//ts:package.json",
        # strict_visibility = True,
        yarn_lock = "//ts:yarn.lock",
    )

    sass_repositories()

    rules_svelte_dependencies()

    protobuf_deps()
