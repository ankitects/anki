def compile(name, ui_file, py_file):
    native.genrule(
        name = name,
        srcs = [ui_file],
        outs = [py_file],
        cmd = "$(location build_ui) $(location {ui_file}) $(location {py_file})".format(
            ui_file = ui_file,
            py_file = py_file,
        ),
        tools = [
            "build_ui",
        ],
        message = "Building UI",
    )

def compile_all(group, srcs, visibility):
    py_files = []
    for ui_file in srcs:
        name = ui_file.replace(".ui", "")
        py_file = name + ".py"
        py_files.append(py_file)
        compile(name, ui_file, py_file)

    native.filegroup(
        name = group,
        srcs = py_files + ["__init__.py"],
        visibility = visibility,
    )
