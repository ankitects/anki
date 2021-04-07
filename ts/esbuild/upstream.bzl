"""
esbuild rule
"""

load("@build_bazel_rules_nodejs//:providers.bzl", "JSEcmaScriptModuleInfo", "JSModuleInfo", "NpmPackageInfo", "node_modules_aspect")
load("@build_bazel_rules_nodejs//internal/linker:link_node_modules.bzl", "MODULE_MAPPINGS_ASPECT_RESULTS_NAME", "module_mappings_aspect")
load(":helpers.bzl", "filter_files", "generate_path_mapping", "resolve_js_input", "write_jsconfig_file")

def _esbuild_impl(ctx):
    # For each dep, JSEcmaScriptModuleInfo is used if found, then JSModuleInfo and finally
    # the DefaultInfo files are used if the former providers are not found.
    deps_depsets = []

    # Path alias mapings are used to create a jsconfig with mappings so that esbuild
    # how to resolve custom package or module names
    path_alias_mappings = dict()
    npm_workspaces = []

    if (ctx.attr.link_workspace_root):
        path_alias_mappings.update(generate_path_mapping(ctx.workspace_name, "."))

    for dep in ctx.attr.deps:
        if JSEcmaScriptModuleInfo in dep:
            deps_depsets.append(dep[JSEcmaScriptModuleInfo].sources)

        if JSModuleInfo in dep:
            deps_depsets.append(dep[JSModuleInfo].sources)
        elif hasattr(dep, "files"):
            deps_depsets.append(dep.files)

        if DefaultInfo in dep:
            deps_depsets.append(dep[DefaultInfo].data_runfiles.files)

        if NpmPackageInfo in dep:
            deps_depsets.append(dep[NpmPackageInfo].sources)
            npm_workspaces.append(dep[NpmPackageInfo].workspace)

        # Collect the path alias mapping to resolve packages correctly
        if hasattr(dep, MODULE_MAPPINGS_ASPECT_RESULTS_NAME):
            for key, value in getattr(dep, MODULE_MAPPINGS_ASPECT_RESULTS_NAME).items():
                path_alias_mappings.update(generate_path_mapping(key, value[1].replace(ctx.bin_dir.path + "/", "")))

    node_modules_mappings = [
        "../../../external/%s/node_modules/*" % workspace
        for workspace in depset(npm_workspaces).to_list()
    ]
    path_alias_mappings.update({"*": node_modules_mappings})

    deps_inputs = depset(transitive = deps_depsets).to_list()
    inputs = filter_files(ctx.files.entry_point, [".mjs", ".js"]) + ctx.files.srcs + deps_inputs

    metafile = ctx.actions.declare_file("%s_metadata.json" % ctx.attr.name)
    outputs = [metafile]

    entry_point = resolve_js_input(ctx.file.entry_point, inputs)

    args = ctx.actions.args()

    args.add("--bundle", entry_point.path)

    if len(ctx.attr.sourcemap) > 0:
        args.add_joined(["--sourcemap", ctx.attr.sourcemap], join_with = "=")
    else:
        args.add("--sourcemap")

    args.add("--preserve-symlinks")
    args.add_joined(["--platform", ctx.attr.platform], join_with = "=")
    args.add_joined(["--target", ctx.attr.target], join_with = "=")
    args.add_joined(["--log-level", "info"], join_with = "=")
    args.add_joined(["--metafile", metafile.path], join_with = "=")
    args.add_all(ctx.attr.define, format_each = "--define:%s")
    args.add_all(ctx.attr.external, format_each = "--external:%s")

    # disable the log limit and show all logs
    args.add_joined(["--log-limit", "0"], join_with = "=")

    if ctx.attr.minify:
        args.add("--minify")
    else:
        # by default, esbuild will tree-shake 'pure' functions
        # disable this unless also minifying
        args.add_joined(["--tree-shaking", "ignore-annotations"], join_with = "=")

    if ctx.attr.sources_content:
        args.add("--sources-content=true")
    else:
        args.add("--sources-content=false")

    if ctx.attr.output_dir:
        js_out = ctx.actions.declare_directory("%s" % ctx.attr.name)
        outputs.append(js_out)

        args.add("--splitting")
        args.add_joined(["--format", "esm"], join_with = "=")
        args.add_joined(["--outdir", js_out.path], join_with = "=")
    else:
        js_out = ctx.outputs.output
        outputs.append(js_out)

        js_out_map = ctx.outputs.output_map
        if ctx.attr.sourcemap != "inline":
            if js_out_map == None:
                fail("output_map must be specified if sourcemap is not set to 'inline'")
            outputs.append(js_out_map)

        if ctx.outputs.output_css:
            outputs.append(ctx.outputs.output_css)

        if ctx.attr.format:
            args.add_joined(["--format", ctx.attr.format], join_with = "=")

        args.add_joined(["--outfile", js_out.path], join_with = "=")

    jsconfig_file = write_jsconfig_file(ctx, path_alias_mappings)
    args.add_joined(["--tsconfig", jsconfig_file.path], join_with = "=")
    inputs.append(jsconfig_file)

    args.add_all([ctx.expand_location(arg) for arg in ctx.attr.args])

    env = {}
    if ctx.attr.max_threads > 0:
        env["GOMAXPROCS"] = str(ctx.attr.max_threads)

    execution_requirements = {}
    if "no-remote-exec" in ctx.attr.tags:
        execution_requirements = {"no-remote-exec": "1"}

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable.tool,
        arguments = [args],
        progress_message = "%s Javascript %s [esbuild]" % ("Bundling" if not ctx.attr.output_dir else "Splitting", entry_point.short_path),
        execution_requirements = execution_requirements,
        mnemonic = "esbuild",
        env = env,
    )

    return [
        DefaultInfo(files = depset(outputs + [jsconfig_file])),
    ]

esbuild = rule(
    attrs = {
        "args": attr.string_list(
            default = [],
            doc = """A list of extra arguments that are included in the call to esbuild.
    $(location ...) can be used to resolve the path to a Bazel target.""",
        ),
        "define": attr.string_list(
            default = [],
            doc = """A list of global identifier replacements.
Example:
```python
esbuild(
    name = "bundle",
    define = [
        "process.env.NODE_ENV=\\"production\\""
    ],
)
```

See https://esbuild.github.io/api/#define for more details
            """,
        ),
        "deps": attr.label_list(
            default = [],
            aspects = [module_mappings_aspect, node_modules_aspect],
            doc = "A list of direct dependencies that are required to build the bundle",
        ),
        "entry_point": attr.label(
            mandatory = True,
            allow_single_file = True,
            doc = "The bundle's entry point (e.g. your main.js or app.js or index.js)",
        ),
        "external": attr.string_list(
            default = [],
            doc = """A list of module names that are treated as external and not included in the resulting bundle

See https://esbuild.github.io/api/#external for more details
            """,
        ),
        "format": attr.string(
            values = ["iife", "cjs", "esm", ""],
            mandatory = False,
            doc = """The output format of the bundle, defaults to iife when platform is browser
and cjs when platform is node. If performing code splitting, defaults to esm.

See https://esbuild.github.io/api/#format for more details
        """,
        ),
        "link_workspace_root": attr.bool(
            doc = """Link the workspace root to the bin_dir to support absolute requires like 'my_wksp/path/to/file'.
    If source files need to be required then they can be copied to the bin_dir with copy_to_bin.""",
        ),
        "max_threads": attr.int(
            mandatory = False,
            doc = """Sets the `GOMAXPROCS` variable to limit the number of threads that esbuild can run with.
This can be useful if running many esbuild rule invocations in parallel, which has the potential to cause slowdown.
For general use, leave this attribute unset.
            """,
        ),
        "minify": attr.bool(
            default = False,
            doc = """Minifies the bundle with the built in minification.
Removes whitespace, shortens identifieres and uses equivalent but shorter syntax.

Sets all --minify-* flags

See https://esbuild.github.io/api/#minify for more details
            """,
        ),
        "output": attr.output(
            mandatory = False,
            doc = "Name of the output file when bundling",
        ),
        "output_dir": attr.bool(
            default = False,
            doc = """If true, esbuild produces an output directory containing all the output files from code splitting

See https://esbuild.github.io/api/#splitting for more details
            """,
        ),
        "output_map": attr.output(
            mandatory = False,
            doc = "Name of the output source map when bundling",
        ),
        "output_css": attr.output(
            mandatory = False,
            doc = "Name of the output css file when bundling",
        ),
        "platform": attr.string(
            default = "browser",
            values = ["node", "browser", "neutral", ""],
            doc = """The platform to bundle for.

See https://esbuild.github.io/api/#platform for more details
            """,
        ),
        "sourcemap": attr.string(
            values = ["external", "inline", "both", ""],
            mandatory = False,
            doc = """Defines where sourcemaps are output and how they are included in the bundle. By default, a separate `.js.map` file is generated and referenced by the bundle. If 'external', a separate `.js.map` file is generated but not referenced by the bundle. If 'inline', a sourcemap is generated and its contents are inlined into the bundle (and no external sourcemap file is created). If 'both', a sourcemap is inlined and a `.js.map` file is created.

See https://esbuild.github.io/api/#sourcemap for more details
            """,
        ),
        "sources_content": attr.bool(
            mandatory = False,
            default = False,
            doc = """If False, omits the `sourcesContent` field from generated source maps

See https://esbuild.github.io/api/#sources-content for more details
            """,
        ),
        "srcs": attr.label_list(
            allow_files = True,
            default = [],
            doc = """Non-entry point JavaScript source files from the workspace.

You must not repeat file(s) passed to entry_point""",
        ),
        "target": attr.string(
            default = "es2015",
            doc = """Environment target (e.g. es2017, chrome58, firefox57, safari11, 
edge16, node10, default esnext)

See https://esbuild.github.io/api/#target for more details
            """,
        ),
        "tool": attr.label(
            allow_single_file = True,
            mandatory = True,
            executable = True,
            cfg = "exec",
            doc = "An executable for the esbuild binary",
        ),
    },
    implementation = _esbuild_impl,
    doc = """Runs the esbuild bundler under Bazel

For further information about esbuild, see https://esbuild.github.io/
    """,
)

def esbuild_macro(name, output_dir = False, output_css = False, **kwargs):
    """esbuild helper macro around the `esbuild_bundle` rule

    For a full list of attributes, see the `esbuild_bundle` rule

    Args:
        name: The name used for this rule and output files
        output_dir: If `True`, produce a code split bundle in an output directory
        output_css: If `True`, declare name.css as an output, which is the
                    case when your code imports a css file.
        **kwargs: All other args from `esbuild_bundle`
    """

    if output_dir == True:
        esbuild(
            name = name,
            output_dir = True,
            **kwargs
        )
    else:
        output = "%s.js" % name
        if "output" in kwargs:
            output = kwargs.pop("output")

        output_map = None
        sourcemap = kwargs.get("sourcemap", None)
        if sourcemap != "inline":
            output_map = "%s.map" % output

        esbuild(
            name = name,
            output = output,
            output_map = output_map,
            output_css = None if not output_css else "%s.css" % name,
            **kwargs
        )
