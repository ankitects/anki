# based off https://github.com/ali5h/rules_pip/blob/master/defs.bzl

pip_vendor_label = Label("@com_github_ali5h_rules_pip//:third_party/py/easy_install.py")

def _execute(repository_ctx, arguments, quiet = False):
    pip_vendor = str(repository_ctx.path(pip_vendor_label).dirname)
    return repository_ctx.execute(arguments, environment = {
        "PYTHONPATH": pip_vendor,
    }, quiet = quiet)

def _install_pyqt5_impl(repository_ctx):
    python_interpreter = repository_ctx.attr.python_interpreter
    if repository_ctx.attr.python_runtime:
        python_interpreter = repository_ctx.path(repository_ctx.attr.python_runtime)

    args = [
        python_interpreter,
        repository_ctx.path(repository_ctx.attr._script),
        repository_ctx.path("."),
    ]

    result = _execute(repository_ctx, args, quiet = repository_ctx.attr.quiet)
    if result.return_code:
        fail("failed: %s (%s)" % (result.stdout, result.stderr))

install_pyqt5 = repository_rule(
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
            default = Label("//pip/pyqt5:install_pyqt5.py"),
            cfg = "host",
        ),
        "quiet": attr.bool(
            default = True,
            doc = "If stdout and stderr should be printed to the terminal.",
        ),
    },
    implementation = _install_pyqt5_impl,
)
