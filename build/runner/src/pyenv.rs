// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::process::Command;

use camino::Utf8Path;
use clap::Args;

use crate::run::run_command;

#[derive(Args)]
pub struct PyenvArgs {
    uv_bin: String,
    pyenv_folder: String,
    #[arg(trailing_var_arg = true)]
    extra_args: Vec<String>,
}

/// Set up a venv if one doesn't already exist, and then sync packages with
/// provided requirements file.
pub fn setup_pyenv(args: PyenvArgs) {
    let pyenv_folder = Utf8Path::new(&args.pyenv_folder);

    // On first run, ninja creates an empty bin/ folder which breaks the initial
    // install. But we don't want to indiscriminately remove the folder, or
    // macOS Gatekeeper needs to rescan the files each time.
    if pyenv_folder.exists() {
        let cache_tag = pyenv_folder.join("CACHEDIR.TAG");
        if !cache_tag.exists() {
            fs::remove_dir_all(pyenv_folder).expect("Failed to remove existing pyenv folder");
        }
    }

    run_command(
        Command::new(args.uv_bin)
            .env("UV_PROJECT_ENVIRONMENT", args.pyenv_folder.clone())
            .args(["sync"])
            .args(args.extra_args),
    );

    // Write empty stamp file
    fs::write(pyenv_folder.join(".stamp"), "").expect("Failed to write stamp file");
}
