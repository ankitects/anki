// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![windows_subsystem = "console"]

use std::env;
use std::io::stdin;
use std::process::Command;

use anyhow::Context;
use anyhow::Result;

fn main() {
    if let Err(e) = run() {
        eprintln!("Error: {e:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let current_exe = env::current_exe().context("Failed to get current executable path")?;
    let exe_dir = current_exe
        .parent()
        .context("Failed to get executable directory")?;

    let anki_exe = exe_dir.join("anki.exe");

    if !anki_exe.exists() {
        anyhow::bail!("anki.exe not found in the same directory");
    }

    // Forward all command line arguments to anki.exe
    let args: Vec<String> = env::args().skip(1).collect();

    let mut cmd = Command::new(&anki_exe);
    cmd.args(&args);

    if std::env::var("ANKI_IMPLICIT_CONSOLE").is_err() {
        // if directly invoked by the user, signal the launcher that the
        // user wants a Python console
        std::env::set_var("ANKI_CONSOLE", "1");
    }

    // Wait for the process to complete and forward its exit code
    let status = cmd.status().context("Failed to execute anki.exe")?;
    if !status.success() {
        println!("\nPress enter to close.");
        let mut input = String::new();
        let _ = stdin().read_line(&mut input);
    }

    if let Some(code) = status.code() {
        std::process::exit(code);
    } else {
        // Process was terminated by a signal
        std::process::exit(1);
    }
}
