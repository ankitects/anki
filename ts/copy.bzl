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
