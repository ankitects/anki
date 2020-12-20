load("//ts:copy.bzl", "copy_files")

"Rule to copy mathjax subset from node_modules to vendor folder."

_exclude = [
    "mathmaps_ie.js",
]

_include = [
    "es5/tex-chtml.js",
    "es5/input/tex/extensions",
    "es5/output/chtml/fonts/woff-v2",
    "es5/a11y",
    "es5/sre",
]

_unwanted_prefix = "external/npm/node_modules/mathjax/es5/"

def _copy_mathjax_impl(ctx):
    wanted = []
    for f in ctx.attr.mathjax.files.to_list():
        path = f.path
        want = True
        for substr in _exclude:
            if substr in path:
                want = False
                continue
        if not want:
            continue

        for substr in _include:
            if substr in path:
                output = path.replace(_unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)

copy_mathjax = rule(
    implementation = _copy_mathjax_impl,
    attrs = {
        "mathjax": attr.label(default = "@npm//mathjax:mathjax__files"),
    },
)
