def compile_ts(group, srcs):
    css_files = []
    for ts_file in srcs:
        name = ts_file.replace(".ts", "") + "_ts"
        css_file = name + ".css"
        css_files.append(css_file)

        sass_binary(
            name = name,
            src = ts_file,
            sourcemap = False,
            deps = ["//ts/sass:core_lib"],
        )

    native.filegroup(
        name = group,
        srcs = css_files,
        visibility = ["//qt:__subpackages__"],
    )
