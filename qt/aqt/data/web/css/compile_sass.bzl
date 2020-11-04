load("@io_bazel_rules_sass//:defs.bzl", "sass_binary")

def compile_sass(group, srcs, visibility):
    css_files = []
    for scss_file in srcs:
        base = scss_file.replace(".scss", "")
        name = base + "_sass"
        css_file = base + ".css"
        css_files.append(css_file)

        sass_binary(
            name = name,
            src = scss_file,
            sourcemap = False,
            deps = ["//ts/sass:core_lib"],
        )

    native.filegroup(
        name = group,
        srcs = css_files,
        visibility = visibility,
    )
