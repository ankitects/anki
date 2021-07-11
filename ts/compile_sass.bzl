load("@io_bazel_rules_sass//:defs.bzl", "sass_binary")

def compile_sass(group, srcs, deps = [], visibility = ["//visibility:private"]):
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
            deps = deps,
            visibility = visibility,
            include_paths = ["external/ankidesktop"],
        )

    native.filegroup(
        name = group,
        srcs = css_files,
        visibility = visibility,
    )
