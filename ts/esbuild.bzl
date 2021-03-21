load("//ts/esbuild:upstream.bzl", _esbuild = "esbuild_macro")

def esbuild(name, **kwargs):
    _esbuild(
        name = name,
        tool = select({
            "@bazel_tools//src/conditions:darwin": "@esbuild_darwin//:bin/esbuild",
            "@bazel_tools//src/conditions:windows": "@esbuild_windows//:esbuild.exe",
            "@bazel_tools//src/conditions:linux_x86_64": "@esbuild_linux//:bin/esbuild",
        }),
        minify = select({
            "//:release": True,
            "//conditions:default": False,
        }),
        **kwargs
    )
