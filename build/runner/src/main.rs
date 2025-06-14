// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! A helper for invoking one or more commands in a cross-platform way,
//! silencing their output when they succeed. Most build actions implicitly use
//! the 'run' command; we define separate commands for more complicated actions.

mod archive;
mod build;
mod paths;
mod pyenv;
mod rsync;
mod run;
mod yarn;

use anyhow::Result;
use archive::archive_command;
use archive::ArchiveArgs;
use build::run_build;
use build::BuildArgs;
use clap::Parser;
use clap::Subcommand;
use pyenv::setup_pyenv;
use pyenv::PyenvArgs;
use rsync::rsync_files;
use rsync::RsyncArgs;
use run::run_commands;
use run::RunArgs;
use yarn::setup_yarn;
use yarn::YarnArgs;

#[derive(Parser)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    Pyenv(PyenvArgs),
    Yarn(YarnArgs),
    Rsync(RsyncArgs),
    Run(RunArgs),
    Build(BuildArgs),
    #[clap(subcommand)]
    Archive(ArchiveArgs),
}

fn main() -> Result<()> {
    match Cli::parse().command {
        Command::Pyenv(args) => setup_pyenv(args),
        Command::Run(args) => run_commands(args)?,
        Command::Rsync(args) => rsync_files(args),
        Command::Yarn(args) => setup_yarn(args),
        Command::Build(args) => run_build(args),
        Command::Archive(args) => archive_command(args)?,
    };
    Ok(())
}
