// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! A helper for invoking one or more commands in a cross-platform way,
//! silencing their output when they succeed. Most build actions implicitly use
//! the 'run' command; we define separate commands for more complicated actions.

mod build;
mod bundle;
mod paths;
mod pyenv;
mod rsync;
mod run;
mod yarn;

use std::error::Error;

use build::run_build;
use build::BuildArgs;
use bundle::artifacts::build_artifacts;
use bundle::artifacts::BuildArtifactsArgs;
use bundle::binary::build_bundle_binary;
use bundle::folder::build_dist_folder;
use bundle::folder::BuildDistFolderArgs;
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

pub type Result<T, E = Box<dyn Error>> = std::result::Result<T, E>;

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
    BuildArtifacts(BuildArtifactsArgs),
    BuildBundleBinary,
    BuildDistFolder(BuildDistFolderArgs),
}

fn main() {
    match Cli::parse().command {
        Command::Pyenv(args) => setup_pyenv(args),
        Command::Run(args) => run_commands(args),
        Command::Rsync(args) => rsync_files(args),
        Command::Yarn(args) => setup_yarn(args),
        Command::Build(args) => run_build(args),
        Command::BuildArtifacts(args) => build_artifacts(args),
        Command::BuildBundleBinary => build_bundle_binary(),
        Command::BuildDistFolder(args) => build_dist_folder(args),
    };
}
