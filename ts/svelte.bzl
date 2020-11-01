load("@build_bazel_rules_svelte//:defs.bzl", "svelte")
load("@npm//svelte-check:index.bzl", _svelte_check = "svelte_check_test")

def compile_svelte(name, srcs):
    for src in srcs:
        svelte(
            name = src.replace(".svelte", ""),
            entry_point = src,
        )

    native.filegroup(
        name = name,
        srcs = srcs,
    )

def svelte_check(name = "svelte_check", srcs = []):
    _svelte_check(
        name = name,
        args = [
            "--workspace",
            native.package_name(),
        ],
        data = [
            "//ts:tsconfig.json",
            "//ts/lib",
            "//ts/lib:backend_proto",
        ] + srcs,
        link_workspace_root = True,
    )
