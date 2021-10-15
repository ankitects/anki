def compile(name, ui_file, qt5_file, qt6_file, builder):
    native.genrule(
        name = name,
        srcs = [ui_file],
        outs = [qt5_file, qt6_file],
        cmd = "$(location {builder}) $(location {ui_file}) $(location {qt5_file}) $(location {qt6_file})".format(
            builder = builder,
            ui_file = ui_file,
            qt5_file = qt5_file,
            qt6_file = qt6_file,
        ),
        tools = [
            builder,
        ],
        message = "Building UI",
    )

def compile_all(name, builder, srcs):
    py_files = []
    for ui_file in srcs:
        base = ui_file.replace(".ui", "")
        qt5_file = base + "_qt5.py"
        qt6_file = base + "_qt6.py"
        py_files.extend([qt5_file, qt6_file])
        compile(base, ui_file, qt5_file, qt6_file, builder)

    native.filegroup(
        name = name,
        srcs = py_files,
    )
