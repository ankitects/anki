load("//ts:copy.bzl", "copy_files")

"Rule to copy css-browser-selector subset from node_modules to vendor folder."

_include = [
    "css-browser-selector.min.js",
]

_unwanted_prefix = "external/npm/node_modules/css-browser-selector/"

def _copy_browsersel(ctx):
    wanted = []
    for f in ctx.attr.browsersel.files.to_list():
        path = f.path
        want = True

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_browsersel = rule(
    implementation = _copy_browsersel_impl,
    attrs = {
        "browsersel": attr.label(default = "@npm//css-browser-selector:css_browser-selector__files"),
    },
)
