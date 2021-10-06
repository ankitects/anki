load("@npm//@bazel/esbuild:index.bzl", _esbuild = "esbuild")

def esbuild(name, **kwargs):
    args = kwargs.get("args", {})
    if "resolveExtension" not in args:
        args["resolveExtensions"] = [
            ".mjs",
            ".js",
        ]
    if "logLevel" not in args:
        args["logLevel"] = "warning"
    kwargs["args"] = args

    _esbuild(
        name = name,
        minify = select({
            "//:release": True,
            "//conditions:default": False,
        }),
        **kwargs
    )
