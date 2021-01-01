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

def copy_select_files(ctx, files, include, exclude, unwanted_prefix):
    wanted = []
    for f in files.to_list():
        path = f.path
        want = True

        for substr in exclude:
            if substr in path:
                want = False
                continue
        if not want:
            continue

        for substr in include:
            if substr in path:
                output = path.replace(unwanted_prefix, "")
                wanted.append((f, output))

    return copy_files(ctx, wanted)
