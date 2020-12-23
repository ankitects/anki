def _impl(rctx):
    # locate python on path, and export it
    names = [
        # prefer 3.8 over 3.9, as pylint currently fails on 3.9
        # (due to issues like https://github.com/PyCQA/pylint/pull/3890)
        "python3.8",
        "python3",
        "python.exe",
    ]
    path = None
    if rctx.os.environ.get("PYTHON_SYS_EXECUTABLE"):
        path = rctx.os.environ.get("PYTHON_SYS_EXECUTABLE")
    else:
        for name in names:
            path = rctx.which(name)
            if path:
                break

    if not path:
        fail("python3 or python.exe not found on path, and PYTHON_SYS_EXECUTABLE not set")

    rctx.symlink(path, "python")
    rctx.file("BUILD.bazel", """
load("@rules_python//python:defs.bzl", "py_runtime_pair")

py_runtime(
    name = "python_runtime",
    interpreter_path = "{path}",
    python_version = "PY3",
    visibility = ["//visibility:public"],
)

py_runtime_pair(
    name = "python3_runtime_pair",
    py2_runtime = None,
    py3_runtime = ":python_runtime",
)

toolchain(
    name = "python3_toolchain",
    toolchain = ":python3_runtime_pair",
    toolchain_type = "@bazel_tools//tools/python:toolchain_type",
    visibility = ["//visibility:public"],

)

exports_files(["python"])
""".format(path = path))

setup_local_python = repository_rule(
    implementation = _impl,
    local = True,
    attrs = {},
)
