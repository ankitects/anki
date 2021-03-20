load("@npm//svelte-check:index.bzl", _svelte_check = "svelte_check_test")

"Implementation of the svelte rule"

load("@build_bazel_rules_nodejs//:providers.bzl", "declaration_info")

SvelteFilesInfo = provider("transitive_sources")

def get_transitive_srcs(srcs, deps):
    return depset(
        srcs,
        transitive = [dep[SvelteFilesInfo].transitive_sources for dep in deps],
    )

def _svelte(ctx):
    base = ctx.attr.name + ".svelte"
    temp = ctx.actions.declare_directory(base + ".temp")
    temptsx = temp.path + "/" + base + ".tsx"
    ctx.actions.run_shell(
        mnemonic = "Svelte",
        command = """\
{svelte} {input} {output_js} {temp} && \
{tsc} {tsc_args} {temptsx} {shims} && \
mv {temp}/{base}.d.ts {output_def} && \
rm {temptsx}""".format(
            svelte = ctx.executable._svelte.path,
            input = ctx.file.entry_point.path,
            output_js = ctx.outputs.build.path,
            tsc = ctx.executable._typescript.path,
            output_def = ctx.outputs.buildDef.path,
            temp = temp.path,
            temptsx = temptsx,
            base = base,
            tsc_args = "--jsx preserve --emitDeclarationOnly --declaration --skipLibCheck",
            shims = " ".join([f.path for f in ctx.files._shims]),
        ),
        outputs = [ctx.outputs.build, ctx.outputs.buildDef, temp],
        inputs = [ctx.file.entry_point] + ctx.files._shims,
        tools = [ctx.executable._svelte, ctx.executable._typescript],
    )

    trans_srcs = get_transitive_srcs(ctx.files.srcs + [ctx.outputs.build, ctx.outputs.buildDef], ctx.attr.deps)

    return [
        declaration_info(depset([ctx.outputs.buildDef]), deps = [ctx.attr._shims]),
        SvelteFilesInfo(transitive_sources = trans_srcs),
        DefaultInfo(files = trans_srcs),
    ]

svelte = rule(
    implementation = _svelte,
    attrs = {
        "entry_point": attr.label(allow_single_file = True),
        "deps": attr.label_list(),
        "srcs": attr.label_list(allow_files = True),
        "_svelte": attr.label(
            default = Label("//ts/svelte:svelte"),
            executable = True,
            cfg = "host",
        ),
        "_typescript": attr.label(
            default = Label("//ts/svelte:typescript"),
            executable = True,
            cfg = "host",
        ),
        "_shims": attr.label(
            default = Label("@npm//svelte2tsx:svelte2tsx__typings"),
            allow_files = True,
        ),
    },
    outputs = {
        "build": "%{name}.svelte.mjs",
        "buildDef": "%{name}.svelte.d.ts",
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
