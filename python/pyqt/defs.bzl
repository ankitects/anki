# based off https://github.com/ali5h/rules_pip/blob/master/defs.bzl

def _execute(repository_ctx, arguments, quiet = False):
    return repository_ctx.execute(arguments, environment = {}, quiet = quiet)

def _install_pyqt_impl(repository_ctx):
    python_interpreter = repository_ctx.attr.python_interpreter
    if repository_ctx.attr.python_runtime:
        python_interpreter = repository_ctx.path(repository_ctx.attr.python_runtime)

    args = [
        python_interpreter,
        repository_ctx.path(repository_ctx.attr._script),
        repository_ctx.path("."),
        repository_ctx.path(repository_ctx.attr.requirements),
    ]

    result = _execute(repository_ctx, args, quiet = repository_ctx.attr.quiet)
    if result.return_code:
        fail("failed: %s (%s)" % (result.stdout, result.stderr))

install_pyqt = repository_rule(
    attrs = {
        "python_interpreter": attr.string(default = "python", doc = """
The command to run the Python interpreter used to invoke pip and unpack the
wheels.
"""),
        "python_runtime": attr.label(doc = """
The label to the Python run-time interpreted used to invoke pip and unpack the wheels.
If the label is specified it will overwrite the python_interpreter attribute.
"""),
        "_script": attr.label(
            executable = True,
            default = Label("//python/pyqt:install.py"),
            cfg = "host",
        ),
        "requirements": attr.label(allow_files = True),
        "quiet": attr.bool(
            default = True,
            doc = "If stdout and stderr should be printed to the terminal.",
        ),
    },
    implementation = _install_pyqt_impl,
)
