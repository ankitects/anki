load("//ts:copy.bzl", "copy_files")

"Rule to copy protobufjs subset from node_modules to vendor folder."

_include = [
    "dist/protobuf.min.js",
]

_unwanted_prefix = "external/npm/node_modules/protobufjs/dist/"

def _copy_protobufjs_impl(ctx):
    wanted = []
    for f in ctx.attr.protobufjs.files.to_list():
        path = f.path
        want = True

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_protobufjs = rule(
    implementation = _copy_protobufjs_impl,
    attrs = {
        "protobufjs": attr.label(default = "@npm//protobufjs:protobufjs__files"),
    },
)
