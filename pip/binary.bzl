load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@py_deps//:requirements.bzl", "requirement")

def pip_binary(name, pkg = None):
    "Expose a binary from pip as an executable for linting. Does not work on Windows."

    if not pkg:
        pkg = name

    native.alias(
        name = name,
        actual = requirement(pkg).replace(":pkg", ":rules_python_wheel_entry_point_" + name),
        visibility = ["//visibility:public"],
        tags = ["manual"],
    )
