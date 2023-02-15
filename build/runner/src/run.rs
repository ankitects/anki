// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::ErrorKind;
use std::process::Command;
use std::process::Output;

use clap::Args;

#[derive(Args)]
pub struct RunArgs {
    #[arg(long)]
    stamp: Option<String>,
    #[arg(long, value_parser = split_env)]
    env: Vec<(String, String)>,
    #[arg(long)]
    cwd: Option<String>,
    #[arg(trailing_var_arg = true)]
    args: Vec<String>,
}

/// Run one or more commands separated by `&&`, optionally stamping or setting
/// extra env vars.
pub fn run_commands(args: RunArgs) {
    let commands = split_args(args.args);
    for command in commands {
        run_silent(&mut build_command(command, &args.env, &args.cwd));
    }
    if let Some(stamp_file) = args.stamp {
        std::fs::write(stamp_file, b"").expect("unable to write stamp file");
    }
}

fn split_env(s: &str) -> Result<(String, String), std::io::Error> {
    if let Some((k, v)) = s.split_once('=') {
        Ok((k.into(), v.into()))
    } else {
        Err(std::io::Error::new(ErrorKind::Other, "invalid env var"))
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

/// Log stdout/stderr and exit if command failed; return output on success.
/// If OUTPUT_SUCCESS=1 is defined, output will be shown on success.
pub fn run_silent(command: &mut Command) -> Output {
    let output = command
        .output()
        .unwrap_or_else(|e| panic!("failed to run command: {:?}: {e}", command));
    if !output.status.success() {
        println!(
            "Command failed: \n{}\n{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr),
        );
        std::process::exit(output.status.code().unwrap_or(1));
    }
    if std::env::var("OUTPUT_SUCCESS").is_ok() {
        println!(
            "{}{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
    output
}
