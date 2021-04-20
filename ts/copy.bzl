load("@bazel_skylib//rules:copy_file.bzl", "copy_file")

def copy_files(ctx, files):
    cmds = []
    inputs = []
    outputs = []
    for (src, dst) in files:
        inputs.append(src)
        dst = ctx.actions.declare_file(dst)
        outputs.append(dst)
        cmds.append("cp -f {} {}".format(src.path, dst.path))

    shell_fname = ctx.label.name + "-cp.sh"
    shell_file = ctx.actions.declare_file(shell_fname)
    ctx.actions.write(
        output = shell_file,
        content = "#!/bin/bash\nset -e\n" + "\n".join(cmds),
        is_executable = True,
    )
    ctx.actions.run(
        inputs = inputs,
        executable = "bash",
        tools = [shell_file],
        arguments = [shell_file.path],
        outputs = outputs,
        mnemonic = "CopyFile",
        use_default_shell_env = True,
    )

    return [DefaultInfo(files = depset(outputs))]

def remove_prefix(text, prefix):
    if text.startswith(prefix):
        return text[len(prefix):]
    return text

def copy_select_files(ctx, files, include, exclude, base, unwanted_prefix):
    wanted = []
    for f in files.to_list():
        path = remove_prefix(f.path, base)
        want = True

        for substr in exclude:
            if path.startswith(substr):
                want = False
                continue
        if not want:
            continue

        for substr in include:
            if path.startswith(substr):
                output = remove_prefix(path, unwanted_prefix)
                wanted.append((f, output))

    return copy_files(ctx, wanted)

def copy_files_into_group(name, package, srcs, dev_srcs = []):
    outs = []
    for src in srcs + dev_srcs:
        copy_file(
            name = src + "_copy",
            src = package + ":" + src,
            out = src,
        )

    native.filegroup(
        name = name,
        srcs = srcs + select({
            "//:release": [],
            "//conditions:default": dev_srcs,
        }),
        visibility = ["//qt:__subpackages__"],
    )
