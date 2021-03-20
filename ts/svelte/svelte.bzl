load("@npm//svelte-check:index.bzl", _svelte_check = "svelte_check_test")
load("@build_bazel_rules_nodejs//:providers.bzl", "declaration_info", "run_node")

def _svelte(ctx):
    args = ctx.actions.args()
    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")

    args.add(ctx.file.entry_point.path)
    args.add(ctx.outputs.mjs.path)
    args.add(ctx.outputs.dts.path)
    args.add_all(ctx.files._shims)

    ctx.actions.run(
        execution_requirements = {"supports-workers": "1"},
        executable = ctx.executable._svelte_bin,
        outputs = [ctx.outputs.mjs, ctx.outputs.dts],
        inputs = [ctx.file.entry_point] + ctx.files._shims,
        mnemonic = "Svelte",
        arguments = [args],
    )

    return [
        declaration_info(depset([ctx.outputs.dts]), deps = [ctx.attr._shims]),
    ]

svelte = rule(
    implementation = _svelte,
    attrs = {
        "entry_point": attr.label(allow_single_file = True),
        "_svelte_bin": attr.label(
            default = Label("//ts/svelte:svelte_bin"),
            executable = True,
            cfg = "host",
        ),
        "_shims": attr.label(
            default = Label("@npm//svelte2tsx:svelte2tsx__typings"),
            allow_files = True,
        ),
    },
    outputs = {
        "mjs": "%{name}.svelte.mjs",
        "dts": "%{name}.svelte.d.ts",
    },
)

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
            "@npm//sass",
        ] + srcs,
        link_workspace_root = True,
    )
