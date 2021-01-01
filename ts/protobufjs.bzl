load("//ts:copy.bzl", "copy_select_files")

"Rule to copy protobufjs subset from node_modules to vendor folder."

_include = [
    "dist/protobuf.min.js",
]

_unwanted_prefix = "external/npm/node_modules/protobufjs/dist/"

def _copy_protobufjs_impl(ctx):
    return copy_select_files(
        ctx,
        ctx.attr.protobufjs.files,
        _include,
        [],
        _unwanted_prefix,
    )

copy_protobufjs = rule(
    implementation = _copy_protobufjs_impl,
    attrs = {
        "protobufjs": attr.label(default = "@npm//protobufjs:protobufjs__files"),
    },
)
