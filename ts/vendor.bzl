"""
Helpers to copy runtime dependencies from node_modules.
"""

load("//ts:copy.bzl", "copy_select_files")

def _npm_base_from_name(name):
    return "external/npm/node_modules/{}/".format(name)

def _vendor_js_lib_impl(ctx):
    base = ctx.attr.base or _npm_base_from_name(ctx.attr.name)
    return copy_select_files(
        ctx = ctx,
        files = ctx.attr.pkg.files,
        include = ctx.attr.include,
        exclude = ctx.attr.exclude,
        base = base,
        unwanted_prefix = ctx.attr.strip_prefix,
    )

vendor_js_lib = rule(
    implementation = _vendor_js_lib_impl,
    attrs = {
        "pkg": attr.label(),
        "include": attr.string_list(),
        "exclude": attr.string_list(default = []),
        "base": attr.string(default = ""),
        "strip_prefix": attr.string(default = ""),
    },
)

def pkg_from_name(name):
    tail = name.split("/")[-1]
    return "@npm//{0}:{1}__files".format(name, tail)

#
# These could be defined directly in BUILD files, but defining them as
# macros allows downstream projects to reuse them.
#

def copy_jquery(name = "jquery", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name(name),
        include = [
            "dist/jquery.min.js",
        ],
        strip_prefix = "dist/",
        visibility = visibility,
    )

def copy_jquery_ui(name = "jquery-ui", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name("jquery-ui-dist"),
        base = "external/npm/node_modules/jquery-ui-dist/",
        include = [
            "jquery-ui.min.js",
        ],
        visibility = visibility,
    )

def copy_mathjax(name = "mathjax", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name(name),
        include = [
            "es5/tex-chtml.js",
            "es5/input/tex/extensions",
            "es5/output/chtml/fonts/woff-v2",
            "es5/a11y",
            "es5/sre",
        ],
        exclude = [
            "es5/sre/mathmaps/mathmaps_ie.js",
        ],
        strip_prefix = "es5/",
        visibility = visibility,
    )

def copy_css_browser_selector(name = "css-browser-selector", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name(name),
        include = [
            "css_browser_selector.min.js",
        ],
        visibility = visibility,
    )

def copy_bootstrap_js(name = "bootstrap-js", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name("bootstrap"),
        include = [
            "dist/js/bootstrap.bundle.min.js",
        ],
        strip_prefix = "dist/js/",
        visibility = visibility,
        base = "external/npm/node_modules/bootstrap/",
    )

def copy_bootstrap_css(name = "bootstrap-css", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name("bootstrap"),
        include = [
            "dist/css/bootstrap.min.css",
        ],
        strip_prefix = "dist/css/",
        visibility = visibility,
    )

def copy_bootstrap_icons(name = "bootstrap-icons", icons = [], visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name(name),
        include = ["icons/{}".format(icon) for icon in icons],
        strip_prefix = "icons/",
        visibility = visibility,
    )

def copy_mdi_icons(name = "mdi-icons", icons = [], visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = pkg_from_name("@mdi/svg"),
        base = _npm_base_from_name("@mdi/svg"),
        include = ["svg/{}".format(icon) for icon in icons],
        strip_prefix = "svg/",
        visibility = visibility,
    )
