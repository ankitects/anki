load("//ts:copy.bzl", "copy_select_files")

"Rule to copy css-browser-selector subset from node_modules to vendor folder."

_include = [
    "css_browser_selector.min.js",
]

_base = "external/npm/node_modules/css-browser-selector/"
_unwanted_prefix = ""

def _copy_css_browser_selector_impl(ctx):
    return copy_select_files(
        ctx,
        ctx.attr.css_browser_selector.files,
        _include,
        [],
        _base,
        _unwanted_prefix,
    )

copy_css_browser_selector = rule(
    implementation = _copy_css_browser_selector_impl,
    attrs = {
        "css_browser_selector": attr.label(default = "@npm//css-browser-selector:css-browser-selector__files"),
    },
)
