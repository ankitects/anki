load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@py_deps//:requirements.bzl", "requirement")

def pip_binary(name, pkg = None):
    "Expose a binary from pip as an executable for linting."

    if not pkg:
        pkg = name

    binary_prefix = requirement(pkg).replace(":pkg", ":bin/" + name)

    copy_file(
        name = name + "_bin",
        src = select({
            "@platforms//os:windows": binary_prefix + ".exe",
            "//conditions:default": binary_prefix,
        }),
        out = name + "_bin.py",
    )

    native.py_binary(
        name = name,
        srcs = [":" + name + "_bin.py"],
        main = ":" + name + "_bin.py",
        deps = [
            requirement(pkg),
        ],
        visibility = ["//visibility:public"],
    )
