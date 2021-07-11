load("@npm//svelte-check:index.bzl", _svelte_check = "svelte_check_test")
load("@build_bazel_rules_nodejs//:providers.bzl", "DeclarationInfo", "declaration_info")
load("@io_bazel_rules_sass//:defs.bzl", "SassInfo")

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
    args.add_all(ctx.files._shims)

    deps = _get_sources(ctx.attr.deps).to_list()

    ctx.actions.run(
        execution_requirements = {"supports-workers": "1"},
        executable = ctx.executable._svelte_bin,
        outputs = [ctx.outputs.mjs, ctx.outputs.dts, ctx.outputs.css],
        inputs = [ctx.file.entry_point] + ctx.files._shims + deps,
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
        "deps": attr.label_list(),
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
        "css": "%{name}.svelte.css",
    },
)

def compile_svelte(name, srcs, deps = [], visibility = ["//visibility:private"]):
    for src in srcs:
        svelte(
            name = src.replace(".svelte", ""),
            entry_point = src,
            deps = deps,
            visibility = visibility,
        )

    native.filegroup(
        name = name,
        srcs = srcs,
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
            "//ts:tsconfig.json",
            "//ts/sveltelib",
            "//ts/lib",
            "@npm//sass",
        ] + srcs,
        env = {"SASS_PATH": "$(rootpath //ts:tsconfig.json)/../.."},
        # a lack of sandboxing on Windows breaks the local svelte_check
        # tests, so we need to disable them on Windows for now
        target_compatible_with = select({
            "@platforms//os:osx": [],
            "@platforms//os:linux": [],
            "//conditions:default": ["@platforms//os:linux"],
        }),
    )
