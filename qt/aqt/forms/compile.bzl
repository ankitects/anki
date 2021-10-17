def compile_all(name, builder, srcs):
    py_files = []
    for ui_file in srcs:
        base = ui_file.replace(".ui", "")
        qt5_file = base + "_qt5.py"
        qt6_file = base + "_qt6.py"
        py_files.extend([qt5_file, qt6_file])

    native.genrule(
        name = name + "_build",
        srcs = srcs,
        outs = py_files,
        cmd = "$(location {builder}) $(location {first_ui}) $(location {first_py})".format(
            builder = builder,
            first_ui = srcs[0],
            first_py = py_files[0],
        ),
        tools = [
            builder,
        ],
        message = "Building Qt UI",
    )

    native.filegroup(
        name = name,
        srcs = py_files,
    )
