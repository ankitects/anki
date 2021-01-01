load("//ts:copy.bzl", "copy_select_files")

"Rule to copy jquery-ui subset from node_modules to vendor folder."

_include = [
    "jquery-ui.min.js",
]

_base = "external/npm/node_modules/jquery-ui-dist/"
_unwanted_prefix = ""

def _copy_jquery_ui_impl(ctx):
    return copy_select_files(
        ctx,
        ctx.attr.jquery_ui.files,
        _include,
        [],
        _base,
        _unwanted_prefix,
    )

copy_jquery_ui = rule(
    implementation = _copy_jquery_ui_impl,
    attrs = {
        "jquery_ui": attr.label(default = "@npm//jquery-ui-dist:jquery-ui-dist__files"),
    },
)
