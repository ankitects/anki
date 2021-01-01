load("//ts:copy.bzl", "copy_select_files")

"Rule to copy jquery subset from node_modules to vendor folder."

_include = [
    "dist/jquery.min.js"
]

_unwanted_prefix = "external/npm/node_modules/jquery/dist/"

def _copy_jquery_impl(ctx):
    return copy_select_files(
        ctx,
        ctx.attr.jquery.files,
        _include,
        [],
        _unwanted_prefix,
    )

copy_jquery = rule(
    implementation = _copy_jquery_impl,
    attrs = {
        "jquery": attr.label(default = "@npm//jquery:jquery__files"),
    },
)
