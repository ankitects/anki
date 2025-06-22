// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::os::unix::process::CommandExt;
use std::process::Command;

use anki_process::CommandExt as AnkiCommandExt;
use anyhow::Context;
use anyhow::Result;

// Re-export Unix functions that macOS uses
pub use super::unix::{
    exec_anki,
    get_anki_binary_path,
    initial_terminal_setup,
};

pub fn launch_anki_detached(anki_bin: &std::path::Path, _config: &crate::Config) -> Result<()> {
    use std::process::Stdio;

    let child = Command::new(anki_bin)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .process_group(0)
        .ensure_spawn()?;
    std::mem::forget(child);
    Ok(())
}

pub fn ensure_terminal_shown() -> Result<()> {
    let stdout_is_terminal = std::io::IsTerminal::is_terminal(&std::io::stdout());
    if !stdout_is_terminal {
        // If launched from GUI, relaunch in Terminal.app
        relaunch_in_terminal()?;
    }
    Ok(())
}

fn relaunch_in_terminal() -> Result<()> {
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;
    Command::new("open")
        .args(["-a", "Terminal"])
        .arg(current_exe)
        .ensure_spawn()?;
    std::process::exit(0);
}

pub fn handle_first_launch(anki_bin: &std::path::Path) -> Result<()> {
    // Pre-validate by running --version to trigger any Gatekeeper checks
    println!("\n\x1B[1mThis may take a few minutes. Please wait...\x1B[0m");
    let _ = Command::new(anki_bin)
        .env("ANKI_FIRST_RUN", "1")
        .arg("--version")
        .ensure_success();
    Ok(())
}

pub fn get_exe_and_resources_dirs() -> Result<(std::path::PathBuf, std::path::PathBuf)> {
    let exe_dir = std::env::current_exe()
        .context("Failed to get current executable path")?
        .parent()
        .context("Failed to get executable directory")?
        .to_owned();

    let resources_dir = exe_dir
        .parent()
        .context("Failed to get parent directory")?
        .join("Resources");

    Ok((exe_dir, resources_dir))
}

pub fn get_uv_binary_name() -> &'static str {
    // macOS uses standard uv binary name
    "uv"
}
