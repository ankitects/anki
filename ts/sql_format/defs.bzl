load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_test")

def sql_format(name = "sql_format", srcs = [], **kwargs):
    nodejs_test(
        name = name,
        entry_point = "@ankidesktop//ts/sql_format:sql_format.ts",
        args = [native.package_name() + "/" + f for f in srcs],
        data = ["@ankidesktop//ts/sql_format:sql_format_lib", "@npm//tslib", "@npm//diff"] + srcs,
        **kwargs
    )
