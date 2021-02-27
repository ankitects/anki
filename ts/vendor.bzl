"""
Helpers to copy runtime dependencies from node_modules.
"""

load("//ts:copy.bzl", "copy_select_files")

def _vendor_js_lib_impl(ctx):
    base = ctx.attr.base or "external/npm/node_modules/{}/".format(ctx.attr.name)
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

def _pkg_from_name(name):
    return "@npm//{0}:{0}__files".format(name)

#
# These could be defined directly in BUILD files, but defining them as
# macros allows downstream projects to reuse them.
#

def copy_jquery(name = "jquery", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name(name),
        include = [
            "dist/jquery.min.js",
        ],
        strip_prefix = "dist/",
        visibility = visibility,
    )

def copy_jquery_ui(name = "jquery-ui", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name("jquery-ui-dist"),
        base = "external/npm/node_modules/jquery-ui-dist/",
        include = [
            "jquery-ui.min.js",
        ],
        visibility = visibility,
    )

def copy_protobufjs(name = "protobufjs", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name(name),
        include = [
            "dist/protobuf.min.js",
        ],
        strip_prefix = "dist/",
        visibility = visibility,
    )

def copy_mathjax(name = "mathjax", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name(name),
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
        pkg = _pkg_from_name(name),
        include = [
            "css_browser_selector.min.js",
        ],
        visibility = visibility,
    )

def copy_bootstrap_js(name = "bootstrap-js", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name(name),
        include = [
            "dist/js/bootstrap.bundle.min.js",
        ],
        strip_prefix = "dist/js/",
        visibility = visibility,
    )

def copy_bootstrap_css(name = "bootstrap-css", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name(name),
        include = [
            "dist/css/bootstrap.min.css",
        ],
        strip_prefix = "dist/css/",
        visibility = visibility,
    )

def copy_bootstrap_icons(name = "bootstrap-icons", visibility = ["//visibility:public"]):
    vendor_js_lib(
        name = name,
        pkg = _pkg_from_name(name),
        include = [
            "font/bootstrap-icons.css",
            "font/fonts/bootstrap-icons.woff",
            "font/fonts/bootstrap-icons.woff2",
        ],
        strip_prefix = "font/",
        visibility = visibility,
    )
