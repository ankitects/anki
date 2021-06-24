def _copy_stubs_impl(ctx):
    dir = ctx.actions.declare_directory("stubs")
    ctx.actions.run(
        outputs = [dir],
        inputs = ctx.files.pkgs,
        executable = ctx.executable.tool,
        arguments = [dir.path],
        use_default_shell_env = True,
    )
    return [
        DefaultInfo(files = depset([dir]), data_runfiles = ctx.runfiles(files = [dir])),
    ]

copy_stubs = rule(
    implementation = _copy_stubs_impl,
    attrs = {
        "pkgs": attr.label_list(),
        "tool": attr.label(executable = True, cfg = "host"),
    },
)
