load("//ts:copy.bzl", "copy_files")

"Rule to copy flot subset from node_modules to vendor folder."

_include = [
    "dist/es5/jquery.flot.js",
]

_unwanted_prefix = "external/npm/node_modules/flot/dist/es5/"

def _copy_flot_impl(ctx):
    wanted = []
    for f in ctx.attr.flot.files.to_list():
        path = f.path
        want = True

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_flot = rule(
    implementation = _copy_flot_impl,
    attrs = {
        "flot": attr.label(default = "@npm//flot:flot__files"),
    },
)
