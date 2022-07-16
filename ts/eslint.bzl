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
        ] + [native.package_name() + "/" + f for f in srcs],
        data = [
            "@ankidesktop//:.eslintrc.js",
            "@ankidesktop//:package.json",
            "@npm//@typescript-eslint/parser",
            "@npm//@typescript-eslint/eslint-plugin",
            "@npm//eslint-plugin-svelte",
            "@npm//eslint-plugin-import",
            "@npm//eslint-plugin-simple-import-sort",
            "@npm//eslint-plugin-compat",
            "@npm//eslint-plugin-prettier",
            "@npm//eslint-config-prettier",
        ] + srcs,
    )
