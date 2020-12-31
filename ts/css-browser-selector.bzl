load("//ts:copy.bzl", "copy_files")

"Rule to copy css-browser-selector subset from node_modules to vendor folder."

_include = [
    "css_browser_selector.min.js",
]

_unwanted_prefix = "external/npm/node_modules/css-browser-selector/"

def _copy_css_browser_selector_impl(ctx):
    wanted = []
    for f in ctx.attr.css_browser_selector.files.to_list():
        path = f.path
        want = True

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_css_browser_selector = rule(
    implementation = _copy_css_browser_selector_impl,
    attrs = {
        "css_browser_selector": attr.label(default = "@npm//css-browser-selector:css-browser-selector__files"),
    },
)
