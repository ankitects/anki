load("//ts:copy.bzl", "copy_files")

"Rule to copy jquery subset from node_modules to vendor folder."

_include = [
    "dist/jquery.min.js"
]

_unwanted_prefix = "external/npm/node_modules/jquery/dist/"

def _copy_jquery_impl(ctx):
    wanted = []
    for f in ctx.attr.jquery.files.to_list():
        path = f.path
        want = True

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_jquery = rule(
    implementation = _copy_jquery_impl,
    attrs = {
        "jquery": attr.label(default = "@npm//jquery:jquery__files"),
    },
)
