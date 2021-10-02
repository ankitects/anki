load("@npm//svelte-check:index.bzl", _svelte_check = "svelte_check_test")
load("@build_bazel_rules_nodejs//:providers.bzl", "DeclarationInfo", "declaration_info")
load("@io_bazel_rules_sass//:defs.bzl", "SassInfo")
load("@build_bazel_rules_nodejs//:index.bzl", "js_library")
load("@bazel_skylib//lib:paths.bzl", "paths")

def _get_declarations(dep):
    if SassInfo in dep:
        return dep[SassInfo].transitive_sources
    elif DeclarationInfo in dep:
        return dep[DeclarationInfo].transitive_declarations
    else:
        fail("unexpected dep", dep)

def _svelte(ctx):
    args = ctx.actions.args()
    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")

    # path to bin folder, for sass
    args.add(ctx.var["BINDIR"])
    args.add(ctx.var["GENDIR"])

    # svelte and ts sources
    outputs = []
    dts_only = []
    nondts_only = []
    for src in ctx.files.srcs:
        args.add(src.path)

        if src.path.endswith(".svelte"):
            # strip off external/ankidesktop if invoked from external workspace
            path = src.path
            if src.path.startswith("external/ankidesktop/"):
                path = path[len("external/ankidesktop/"):]

            # strip off package prefix, eg ts/editor/mathjax/Foo.svelte -> mathjax/Foo.svelte
            base = path[len(ctx.label.package) + 1:]
            for ext in ("d.ts", "css", "mjs"):
                out = ctx.actions.declare_file(base.replace(".svelte", ".svelte." + ext))
                args.add(out)
                outputs.append(out)
                if ext == "d.ts":
                    dts_only.append(out)
                else:
                    nondts_only.append(out)

    # dependencies
    deps = depset([], transitive = [_get_declarations(dep) for dep in ctx.attr.deps])
    args.add_all(deps)

    ctx.actions.run(
        execution_requirements = {"supports-workers": "1"},
        executable = ctx.executable._svelte_bin,
        outputs = outputs,
        inputs = ctx.files.srcs + deps.to_list(),
        mnemonic = "Svelte",
        progress_message = "Compiling Svelte {}:{}".format(ctx.label.package, ctx.attr.name),
        arguments = [args],
    )

    return [
        declaration_info(depset(dts_only), deps = ctx.attr.deps),
        DefaultInfo(
            files = depset(nondts_only),
            runfiles = ctx.runfiles(files = outputs, transitive_files = deps),
        ),
    ]

svelte = rule(
    implementation = _svelte,
    attrs = {
        "srcs": attr.label_list(allow_files = True, doc = ".ts and .svelte files"),
        "deps": attr.label_list(),
        "_svelte_bin": attr.label(
            default = Label("//ts/svelte:svelte_bin"),
            executable = True,
            cfg = "host",
        ),
    },
)

def compile_svelte(name = "svelte", srcs = None, deps = [], visibility = ["//visibility:private"]):
    if not srcs:
        srcs = native.glob([
            "**/*.svelte",
            "**/*.ts",
        ])

    for src in srcs:
        svelte(
            name = src.replace(".svelte", ""),
            entry_point = src,
            deps = deps,
            visibility = visibility,
        )

    svelte(
        name = name,
        srcs = srcs,
        deps = deps + [
            "@npm//svelte2tsx",
        ],
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
            "@npm//sass",
        ] + srcs,
        env = {"SASS_PATH": "sass"},
    )
