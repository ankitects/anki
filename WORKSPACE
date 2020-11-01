workspace(
    name = "anki",
    managed_directories = {"@npm": [
        "ts/node_modules",
    ]},
)

load(":packages.bzl", "register_deps")

register_deps()

load(":setup.bzl", "setup_deps")

setup_deps()

load("@py_deps//:requirements.bzl", "pip_install")

pip_install()

load("@npm//@bazel/labs:package.bzl", "npm_bazel_labs_dependencies")

npm_bazel_labs_dependencies()
