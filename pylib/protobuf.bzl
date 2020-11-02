load("@bazel_skylib//lib:paths.bzl", "paths")

def _py_proto_library_impl(ctx):
    basename = ctx.file.src.basename
    outs = [
        ctx.actions.declare_file(paths.replace_extension(basename, "_pb2.py")),
        ctx.actions.declare_file(paths.replace_extension(basename, "_pb2.pyi")),
    ]
    ctx.actions.run(
        outputs = outs,
        inputs = [ctx.file.src],
        executable = ctx.executable.protoc_wrapper,
        arguments = [
            ctx.executable.protoc.path,
            ctx.executable.mypy_protobuf.path,
            ctx.file.src.path,
            paths.dirname(outs[0].path),
        ],
        tools = [
            ctx.executable.protoc,
            ctx.executable.mypy_protobuf,
        ],
        use_default_shell_env = True,
    )
    return [
        DefaultInfo(files = depset(direct = outs), data_runfiles = ctx.runfiles(files = outs)),
    ]

py_proto_library_typed = rule(
    implementation = _py_proto_library_impl,
    attrs = {
        "src": attr.label(allow_single_file = [".proto"]),
        "protoc_wrapper": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//pylib/tools:protoc_wrapper"),
        ),
        "protoc": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
            default = Label("@com_google_protobuf//:protoc"),
        ),
        "mypy_protobuf": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//pylib/tools:mypy_protobuf"),
        ),
    },
)
