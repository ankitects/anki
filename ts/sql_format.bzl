load("@npm//@bazel/typescript:index.bzl", "ts_library")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_test")

def sql_format_setup():
    ts_library(
        name = "sql_format_lib",
        srcs = ["//ts:sql_format.ts"],
        deps = [
            "@npm//@sqltools/formatter",
            "@npm//@types/node",
            "@npm//@types/diff",
            "@npm//diff",
        ],
        visibility = ["//visibility:public"],
    )

def sql_format(name = "sql_format", srcs = [], **kwargs):
    nodejs_test(
        name = name,
        entry_point = "//ts:sql_format.ts",
        args = [native.package_name() + "/" + f for f in srcs],
        data = ["//ts:sql_format_lib", "@npm//tslib", "@npm//diff"] + srcs,
        **kwargs
    )
