load("@bazel_skylib//lib:paths.bzl", "paths")

def _copy_python_lib_impl(ctx):
    desired_file = None
    target_name = ctx.label.name
    if ctx.attr.is_windows:
        target_name += ".pyd"
        for file in ctx.files.src:
            if file.extension == "dll":
                desired_file = file
                break
    else:
        target_name += ".so"
        desired_file = ctx.files.src[0]

    file = ctx.actions.declare_file(target_name)
    ctx.actions.symlink(output = file, target_file = desired_file)

    return [DefaultInfo(files = depset([file]))]

_copy_python_lib = rule(
    implementation = _copy_python_lib_impl,
    attrs = {
        "src": attr.label(),
        "is_windows": attr.bool(mandatory = True),
    },
)

def copy_python_lib(name, src):
    "Copy a dynamic library, renaming it to the extension Python expects."
    _copy_python_lib(
        name = name,
        src = src,
        is_windows = select({
            "@bazel_tools//src/conditions:host_windows": True,
            "//conditions:default": False,
        }),
    )
