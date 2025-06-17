// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use anki_io::create_dir_all;
use anki_io::write_file;
use anki_process::CommandExt;
use anyhow::Result;
use clap::Args;

#[derive(Args)]
pub struct RunArgs {
    #[arg(long)]
    stamp: Option<String>,
    #[arg(long, value_parser = split_env)]
    env: Vec<(String, String)>,
    #[arg(long)]
    cwd: Option<String>,
    #[arg(long)]
    mkdir: Vec<String>,
    #[arg(trailing_var_arg = true)]
    args: Vec<String>,
}

/// Run one or more commands separated by `&&`, optionally stamping or setting
/// extra env vars.
pub fn run_commands(args: RunArgs) -> Result<()> {
    let commands = split_args(args.args);
    for dir in args.mkdir {
        create_dir_all(&dir)?;
    }
    for command in commands {
        run_command(&mut build_command(command, &args.env, &args.cwd));
    }
    if let Some(stamp_file) = args.stamp {
        write_file(stamp_file, b"")?;
    }
    Ok(())
}

fn split_env(s: &str) -> Result<(String, String), std::io::Error> {
    if let Some((k, v)) = s.split_once('=') {
        Ok((k.into(), v.into()))
    } else {
        Err(std::io::Error::other("invalid env var"))
    }
}

fn build_command(
    command_and_args: Vec<String>,
    env: &[(String, String)],
    cwd: &Option<String>,
) -> Command {
    let mut command = Command::new(&command_and_args[0]);
    command.args(&command_and_args[1..]);
    for (k, v) in env {
        command.env(k, v);
    }
    if let Some(cwd) = cwd {
        command.current_dir(cwd);
    }
    command
}

/// If multiple commands have been provided separated by &&, split them up.
fn split_args(args: Vec<String>) -> Vec<Vec<String>> {
    let mut commands = vec![];
    let mut current_command = vec![];
    for arg in args.into_iter() {
        if arg == "&&" {
            commands.push(current_command);
            current_command = vec![];
        } else {
            current_command.push(arg)
        }
    }
    if !current_command.is_empty() {
        commands.push(current_command)
    }
    commands
}

pub fn run_command(command: &mut Command) {
    if let Err(err) = command.ensure_success() {
        println!("{}", err);
        std::process::exit(1);
    }
}
