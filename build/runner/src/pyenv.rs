// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use camino::Utf8Path;
use clap::Args;

use crate::run::run_silent;

#[derive(Args)]
pub struct PyenvArgs {
    python_bin: String,
    pyenv_folder: String,
    initial_reqs: String,
    reqs: Vec<String>,
    #[arg(long, allow_hyphen_values(true))]
    venv_args: Vec<String>,
}

/// Set up a venv if one doesn't already exist, and then sync packages with
/// provided requirements file.
pub fn setup_pyenv(args: PyenvArgs) {
    let pyenv_folder = Utf8Path::new(&args.pyenv_folder);

    let pyenv_bin_folder = pyenv_folder.join(if cfg!(windows) { "scripts" } else { "bin" });
    let pyenv_python = pyenv_bin_folder.join("python");
    let pip_sync = pyenv_bin_folder.join("pip-sync");

    if !pyenv_python.exists() {
        run_silent(
            Command::new(&args.python_bin)
                .args(["-m", "venv"])
                .args(args.venv_args)
                .arg(pyenv_folder),
        );

        if cfg!(windows) {
            // the first install on Windows throws an error the first time pip is upgraded,
            // so we install it twice and swallow the first error
            let _output = Command::new(&pyenv_python)
                .args(["-m", "pip", "install", "-r", &args.initial_reqs])
                .output()
                .unwrap();
        }

        run_silent(Command::new(pyenv_python).args([
            "-m",
            "pip",
            "install",
            "-r",
            &args.initial_reqs,
        ]));
    }

    run_silent(Command::new(pip_sync).args(&args.reqs));
}
