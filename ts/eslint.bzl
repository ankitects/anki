load("@npm//eslint:index.bzl", _eslint_test = "eslint_test")

def eslint_test(name = "eslint", srcs = []):
    _eslint_test(
        name = name,
        args = [
            "--max-warnings=0",
            "--ext",
            ".ts",
            "-c",
            "$(location //ts:.eslintrc.js)",
        ] + [native.package_name() + "/" + f for f in srcs],
        data = [
            "//ts:.eslintrc.js",
            "@npm//@typescript-eslint/parser",
            "@npm//@typescript-eslint/eslint-plugin",
        ] + srcs,
    )
