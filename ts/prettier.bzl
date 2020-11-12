load(
    "@npm//prettier:index.bzl",
    _prettier_test = "prettier_test",
)

def prettier_test(name = "format_check", srcs = [], **kwargs):
    _prettier_test(
        name = name,
        args = [
            "--config",
            "$(location //ts:.prettierrc)",
            "--check",
        ] + [native.package_name() + "/" + f for f in srcs],
        data = [
            "//ts:.prettierrc",
            "@npm//prettier-plugin-svelte",
        ] + srcs,
        **kwargs
    )

def prettier(name = "format", **kwargs):
    native.sh_binary(
        name = name,
        srcs = ["format.sh"],
    )
