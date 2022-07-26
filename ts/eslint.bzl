load("@npm//eslint:index.bzl", _eslint_test = "eslint_test")

def eslint_test(name = "eslint", srcs = None, deps = [], exclude = []):
    if not srcs:
        srcs = srcs or native.glob([
            "**/tsconfig.json",
            "**/*.ts",
            "**/*.svelte",
        ], exclude = exclude)
    _eslint_test(
        name = name,
        args = [
            "--max-warnings=0",
        ] + [
            native.package_name() + "/" + file
            for file in srcs
            if file.endswith(".ts") or file.endswith(".svelte")
        ],
        data = [
            "@ankidesktop//:.eslintrc.js",
            "@ankidesktop//:package.json",
            "@ankidesktop//ts:tsconfig.json",
            "@npm//@typescript-eslint/parser",
            "@npm//@typescript-eslint/eslint-plugin",
            "@npm//eslint-plugin-svelte",
            "@npm//eslint-plugin-import",
            "@npm//eslint-plugin-simple-import-sort",
            "@npm//eslint-plugin-compat",
            "@npm//eslint-config-prettier",
        ] + srcs + deps,
    )
