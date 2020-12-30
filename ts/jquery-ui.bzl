load("//ts:copy.bzl", "copy_files")

"Rule to copy jquery-ui subset from node_modules to vendor folder."

_include = [
    "jquery-ui.min.js",
]

_unwanted_prefix = "external/npm/node_modules/jquery-ui-dist/"

def _copy_jquery_ui_impl(ctx):
    wanted = []
    for f in ctx.attr.jquery-ui.files.to_list():
        path = f.path
        want = True

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_jquery_ui = rule(
    implementation = _copy_jquery_ui_impl,
    attrs = {
        "jquery-ui": attr.label(default = "@npm//jquery-ui-dist:jquery-ui-dist__files"),
    },
)
