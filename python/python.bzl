_python_distros = {
    "macos_amd64": struct(
        url = "https://github.com/indygreg/python-build-standalone/releases/download/20211012/cpython-3.9.7-x86_64-apple-darwin-install_only-20211011T1926.tar.gz",
        sha256 = "43cb1a83919f49b1ce95e42f9c461d8a9fb00ff3957bebef9cffe61a5f362377",
    ),
    "macos_arm64": struct(
        url = "https://github.com/indygreg/python-build-standalone/releases/download/20211012/cpython-3.9.7-aarch64-apple-darwin-install_only-20211011T1926.tar.gz",
        sha256 = "213d64f08f82184c9ad398537becf9b341ee85b1c406cfb2b4cbcb78cdb27a2c",
    ),
    "linux_amd64": struct(
        # pending https://github.com/indygreg/python-build-standalone/issues/95
        url = "https://github.com/ankitects/python-build-standalone/releases/download/anki-2022-02-18/cpython-3.9.10-x86_64-unknown-linux-gnu-install_only-20220218T1329.tar.gz",
        sha256 = "1f5d63c9099da6e51d121ca0eee8d32fb4f084bfc51643fad39be0c14dfbf530",
    ),
    "linux_arm64": struct(
        # pending https://github.com/indygreg/python-build-standalone/issues/95
        url = "https://github.com/ankitects/python-build-standalone/releases/download/anki-2022-02-18/cpython-3.9.10-aarch64-unknown-linux-gnu-install_only-20220218T1329.tar.gz",
        sha256 = "39070f9b9492dce3085c8c98916940434bb65663e6665b2c87bef86025532c1a",
    ),
    "windows_amd64": struct(
        url = "https://github.com/indygreg/python-build-standalone/releases/download/20211012/cpython-3.9.7-x86_64-pc-windows-msvc-shared-install_only-20211011T1926.tar.gz",
        sha256 = "80370f232fd63d5cb3ff9418121acb87276228b0dafbeee3c57af143aca11f89",
    ),
}

def _unix_arch(rctx):
    result = rctx.execute(["uname", "-m"])
    if result.return_code:
        fail("invoking arch failed", result.stderr)
    return result.stdout.strip()

def _get_platform(rctx):
    if rctx.os.name == "mac os x":
        arch = _unix_arch(rctx)
        if arch == "x86_64":
            return "macos_amd64"
        elif arch == "arm64":
            return "macos_arm64"
        else:
            fail("unexpected arch", arch)
    elif rctx.os.name == "linux":
        arch = _unix_arch(rctx)
        if arch == "x86_64":
            return "linux_amd64"
        elif arch == "aarch64":
            return "linux_arm64"
        else:
            fail("unexpected arch", arch)
    elif rctx.os.name.startswith("windows"):
        return "windows_amd64"
    else:
        fail("unexpected platform", rctx.os.name)

def _impl(rctx):
    # bundled python overriden?
    if rctx.os.environ.get("PYO3_PYTHON"):
        path = rctx.os.environ.get("PYO3_PYTHON")
        rctx.symlink(path, "python")
    else:
        platform = _get_platform(rctx)
        distro = _python_distros.get(platform)
        rctx.download_and_extract(
            url = distro.url,
            sha256 = distro.sha256,
            stripPrefix = "python",
        )
        if platform.startswith("windows"):
            path = rctx.path("python.exe")
        else:
            path = rctx.path("bin/python3")
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
    local = False,
    attrs = {},
)
