# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

def _rustfmt_impl(ctx):
    toolchain = ctx.toolchains["@rules_rust//rust:toolchain"]
    script_name = ctx.label.name + "_script"
    rustfmt = toolchain.rustfmt.path
    if ctx.attr.is_windows:
        script_name += ".bat"
        rustfmt = "@" + rustfmt.replace("/", "\\")
    script = ctx.actions.declare_file(script_name)

    args = [f.path for f in ctx.files.srcs]

    if ctx.attr.fix:
        mode = "--emit files"
    else:
        mode = "--check"

    ctx.actions.write(
        output = script,
        content = "{rustfmt} {mode} --edition {edition} {files}".format(
            rustfmt = rustfmt,
            edition = toolchain.default_edition,
            files = " ".join(args),
            mode = mode,
        ),
    )

    runfiles = ctx.runfiles(files = ctx.files.srcs + [toolchain.rustfmt])
    return [DefaultInfo(runfiles = runfiles, executable = script)]

_ATTRS = {
    "srcs": attr.label_list(allow_files = True),
    "is_windows": attr.bool(mandatory = True),
    "fix": attr.bool(mandatory = True),
}

_rustfmt_test = rule(
    implementation = _rustfmt_impl,
    test = True,
    toolchains = [
        "@rules_rust//rust:toolchain",
    ],
    attrs = _ATTRS,
)

_rustfmt_fix = rule(
    implementation = _rustfmt_impl,
    executable = True,
    toolchains = [
        "@rules_rust//rust:toolchain",
    ],
    attrs = _ATTRS,
)

def rustfmt_test(name, srcs, **kwargs):
    _rustfmt_test(
        name = name,
        srcs = srcs,
        testonly = True,
        fix = False,
        is_windows = select({
            "@bazel_tools//src/conditions:host_windows": True,
            "//conditions:default": False,
        }),
        **kwargs
    )

def rustfmt_fix(name, srcs, **kwargs):
    # don't match //package/...
    tags = kwargs.get("tags", [])
    tags.append("manual")

    _rustfmt_fix(
        name = name,
        srcs = srcs,
        tags = tags,
        fix = True,
        is_windows = select({
            "@bazel_tools//src/conditions:host_windows": True,
            "//conditions:default": False,
        }),
        **kwargs
    )
