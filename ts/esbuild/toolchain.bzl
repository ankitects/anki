def _esbuild_toolchain_impl(ctx):
    return [platform_common.ToolchainInfo(
        binary = ctx.executable.binary,
    )]

esbuild_toolchain = rule(
    implementation = _esbuild_toolchain_impl,
    attrs = {
        "binary": attr.label(allow_single_file = True, executable = True, cfg = "exec"),
    },
)

_package_path = "@net_ankiweb_anki//ts/esbuild"

TOOLCHAIN = _package_path + ":toolchain_type"

_default_toolchains = [
    ["@esbuild_darwin//:bin/esbuild", "macos"],
    ["@esbuild_linux//:bin/esbuild", "linux"],
    ["@esbuild_windows//:esbuild.exe", "windows"],
]

def define_default_toolchains():
    for repo_path, platform in _default_toolchains:
        esbuild_toolchain(
            name = "esbuild_" + platform,
            binary = repo_path,
        )

        native.toolchain(
            name = "esbuild_{}_toolchain".format(platform),
            exec_compatible_with = [
                "@platforms//os:" + platform,
                "@platforms//cpu:x86_64",
            ],
            toolchain = ":esbuild_" + platform,
            toolchain_type = ":toolchain_type",
        )

def register_default_toolchains():
    for _, platform in _default_toolchains:
        native.register_toolchains(_package_path + ":esbuild_{}_toolchain".format(platform))
