load("//ts:copy.bzl", "copy_select_files")

"Rule to copy mathjax subset from node_modules to vendor folder."

_include = [
    "es5/tex-chtml.js",
    "es5/input/tex/extensions",
    "es5/output/chtml/fonts/woff-v2",
    "es5/a11y",
    "es5/sre",
]

_exclude = [
    "mathmaps_ie.js",
]

_unwanted_prefix = "external/npm/node_modules/mathjax/es5/"

def _copy_mathjax_impl(ctx):
    return copy_select_files(
        ctx,
        ctx.attr.mathjax.files,
        _include,
        _exclude,
        _unwanted_prefix,
    )

copy_mathjax = rule(
    implementation = _copy_mathjax_impl,
    attrs = {
        "mathjax": attr.label(default = "@npm//mathjax:mathjax__files"),
    },
)
