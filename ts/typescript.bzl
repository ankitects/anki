load("@npm//@bazel/typescript:index.bzl", "ts_project")
load("@build_bazel_rules_nodejs//:index.bzl", "copy_to_bin", "js_library")

def typescript(
        name,
        srcs = [],
        generated = [],
        tsconfig = "tsconfig.json",
        visibility = ["//visibility:public"],
        **kwargs):
    # all tsconfig files must be in the bazel-out folder
    copy_to_bin(
        name = tsconfig + "_bin",
        srcs = ["tsconfig.json"],
    )

    # copy sources into bin folder
    for filename in srcs:
        copy_to_bin(
            name = filename + "_bin_copy",
            srcs = [filename],
        )
    srcs = [s + "_bin_copy" for s in srcs] + generated

    ts_project(
        name = name,
        srcs = srcs,
        composite = True,
        declaration = True,
        extends = "//ts:tsconfig_bin",
        tsconfig = tsconfig + "_bin",
        visibility = visibility,
        supports_workers = False,
        # slow
        validate = False,
        **kwargs
    )

    js_library(
        name = name + "_pkg",
        deps = [name],
        visibility = visibility,
    )
