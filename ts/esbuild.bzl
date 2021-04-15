load("@esbuild_toolchain//:esbuild.bzl", _esbuild = "esbuild_macro")

def esbuild(name, **kwargs):
    _esbuild(
        name = name,
        minify = select({
            "//:release": True,
            "//conditions:default": False,
        }),
        **kwargs
    )
