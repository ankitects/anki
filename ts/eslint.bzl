load("@npm//eslint:index.bzl", _eslint_test = "eslint_test")

def eslint_test(name = "eslint", srcs = None, exclude = []):
    if not srcs:
        srcs = native.glob([
            "**/*.ts",
            "**/*.svelte",
        ], exclude = exclude)
    _eslint_test(
        name = name,
        args = [
            "--max-warnings=0",
            "-c",
            "$(location @ankidesktop//ts:.eslintrc.js)",
        ] + [native.package_name() + "/" + f for f in srcs],
        data = [
            "@ankidesktop//ts:.eslintrc.js",
            "@ankidesktop//:package.json",
            "@npm//@typescript-eslint/parser",
            "@npm//@typescript-eslint/eslint-plugin",
            "@npm//eslint-plugin-svelte3",
            "@npm//eslint-plugin-import",
            "@npm//eslint-plugin-simple-import-sort",
            "@npm//eslint-plugin-compat",
        ] + srcs,
    )
