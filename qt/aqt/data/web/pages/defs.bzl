load("@bazel_skylib//rules:copy_file.bzl", "copy_file")

def copy_page(name, package, srcs):
    outs = []
    for src in srcs:
        copy_file(
            name = src + "_copy",
            src = package + ":" + src,
            out = src,
        )

    native.filegroup(
        name = name,
        srcs = srcs,
        visibility = ["//qt:__subpackages__"],
    )
