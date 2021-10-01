load("@npm//svelte-check:index.bzl", _svelte_check = "svelte_check_test")
load("@build_bazel_rules_nodejs//:providers.bzl", "DeclarationInfo", "declaration_info")
load("@io_bazel_rules_sass//:defs.bzl", "SassInfo")
load("@build_bazel_rules_nodejs//:index.bzl", "js_library")

def _get_dep_sources(dep):
    if SassInfo in dep:
        return dep[SassInfo].transitive_sources
    elif DeclarationInfo in dep:
        return dep[DeclarationInfo].transitive_declarations
    else:
        return []

def _get_sources(deps):
    return depset([], transitive = [_get_dep_sources(dep) for dep in deps])

def _svelte(ctx):
    args = ctx.actions.args()
    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")

    args.add(ctx.file.entry_point.path)
    args.add(ctx.outputs.mjs.path)
    args.add(ctx.outputs.dts.path)
    args.add(ctx.outputs.css.path)
    args.add(ctx.var["BINDIR"])
    args.add(ctx.var["GENDIR"])

    ctx.actions.run(
        execution_requirements = {"supports-workers": "1"},
        executable = ctx.executable._svelte_bin,
        outputs = [ctx.outputs.mjs, ctx.outputs.dts, ctx.outputs.css],
        inputs = [ctx.file.entry_point],
        mnemonic = "Svelte",
        arguments = [args],
    )

    return [
        declaration_info(depset([ctx.outputs.dts]), deps = []),
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
    },
    outputs = {
        "mjs": "%{name}.svelte.mjs",
        "dts": "%{name}.svelte.d.ts",
        "css": "%{name}.svelte.css",
    },
)

def compile_svelte(name = "svelte", srcs = None, visibility = ["//visibility:private"]):
    if not srcs:
        srcs = native.glob(["*.svelte"])
    for src in srcs:
        svelte(
            name = src.replace(".svelte", ""),
            entry_point = src,
            visibility = visibility,
        )

    js_library(
        name = name,
        srcs = [s.replace(".svelte", "") for s in srcs],
        visibility = visibility,
    )

def svelte_check(name = "svelte_check", srcs = []):
    _svelte_check(
        name = name,
        args = [
            "--workspace",
            native.package_name(),
            "--fail-on-warnings",
            "--fail-on-hints",
        ],
        data = [
            "//ts:tsconfig_bin",
            "@npm//sass",
        ] + srcs,
        env = {"SASS_PATH": "ts/sass"},
    )
