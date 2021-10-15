def compile(name, ui_file, py_file, builder):
    native.genrule(
        name = name,
        srcs = [ui_file],
        outs = [py_file],
        cmd = "$(location {builder}) $(location {ui_file}) $(location {py_file})".format(
            builder = builder,
            ui_file = ui_file,
            py_file = py_file,
        ),
        tools = [
            builder,
        ],
        message = "Building UI",
    )

def compile_all(name, builder, srcs, suffix):
    py_files = []
    for ui_file in srcs:
        fname = ui_file.replace(".ui", "") + suffix
        py_file = fname + ".py"
        py_files.append(py_file)
        compile(fname, ui_file, py_file, builder)

    native.filegroup(
        name = name,
        srcs = py_files,
    )
