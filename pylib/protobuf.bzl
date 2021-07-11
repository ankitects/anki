load("@bazel_skylib//lib:paths.bzl", "paths")

def _py_proto_impl(ctx):
    outs = []
    for src in ctx.files.srcs:
        base = paths.basename(src.path)
        outs.append(ctx.actions.declare_file(paths.replace_extension(base, "_pb2.py")))
        outs.append(ctx.actions.declare_file(paths.replace_extension(base, "_pb2.pyi")))

    ctx.actions.run(
        outputs = outs,
        inputs = ctx.files.srcs,
        executable = ctx.executable.protoc_wrapper,
        arguments = [
            ctx.executable.protoc.path,
            ctx.executable.mypy_protobuf.path,
            paths.dirname(outs[0].path),
        ] + [file.path for file in ctx.files.srcs],
        tools = [
            ctx.executable.protoc,
            ctx.executable.mypy_protobuf,
        ],
        use_default_shell_env = True,
    )
    return [
        DefaultInfo(files = depset(direct = outs), data_runfiles = ctx.runfiles(files = outs)),
    ]

py_proto = rule(
    implementation = _py_proto_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = [".proto"]),
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
            default = Label("//pylib/tools:protoc-gen-mypy"),
        ),
    },
)
