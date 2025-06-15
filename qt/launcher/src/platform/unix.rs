// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![allow(dead_code)]

use std::path::PathBuf;
use std::process::Command;

use anki_process::CommandExt as AnkiCommandExt;
use anyhow::Context;
use anyhow::Result;

use crate::Config;

pub fn initial_terminal_setup(_config: &mut Config) {
    // No special terminal setup needed on Unix
}

pub fn handle_terminal_launch() -> Result<()> {
    print!("\x1B[2J\x1B[H"); // Clear screen and move cursor to top
    println!("\x1B[1mPreparing to start Anki...\x1B[0m\n");
    // Skip terminal relaunch on non-macOS Unix systems as we don't know which
    // terminal is installed
    Ok(())
}

pub fn get_anki_binary_path(uv_install_root: &std::path::Path) -> PathBuf {
    uv_install_root.join(".venv/bin/anki")
}

pub fn launch_anki_detached(anki_bin: &std::path::Path, config: &Config) -> Result<()> {
    // On non-macOS Unix systems, we don't need to detach since we never spawned a
    // terminal
    exec_anki(anki_bin, config)
}

pub fn handle_first_launch(_anki_bin: &std::path::Path) -> Result<()> {
    // No special first launch handling needed for generic Unix systems
    Ok(())
}

pub fn exec_anki(anki_bin: &std::path::Path, _config: &Config) -> Result<()> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    Command::new(anki_bin)
        .args(args)
        .ensure_exec()
        .map_err(anyhow::Error::new)
}

pub fn get_exe_and_resources_dirs() -> Result<(PathBuf, PathBuf)> {
    let exe_dir = std::env::current_exe()
        .context("Failed to get current executable path")?
        .parent()
        .context("Failed to get executable directory")?
        .to_owned();

    // On generic Unix systems, assume resources are in the same directory as
    // executable
    let resources_dir = exe_dir.clone();

    Ok((exe_dir, resources_dir))
}

pub fn get_uv_binary_name() -> &'static str {
    // Use architecture-specific uv binary for non-Mac Unix systems
    if cfg!(target_arch = "x86_64") {
        "uv.amd64"
    } else if cfg!(target_arch = "aarch64") {
        "uv.arm64"
    } else {
        // Fallback to generic uv for other architectures
        "uv"
    }
}
