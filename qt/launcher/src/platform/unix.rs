// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![allow(dead_code)]

use std::io::IsTerminal;
use std::path::PathBuf;
use std::process::Command;

use anki_process::CommandExt as AnkiCommandExt;
use anyhow::Context;
use anyhow::Result;

use crate::Config;

pub fn initial_terminal_setup(_config: &mut Config) {
    // No special terminal setup needed on Unix
}

pub fn ensure_terminal_shown() -> Result<()> {
    let stdout_is_terminal = IsTerminal::is_terminal(&std::io::stdout());
    if !stdout_is_terminal {
        // If launched from GUI, try to relaunch in a terminal
        crate::platform::relaunch_in_terminal()?;
    }

    // Set terminal title to "Anki Launcher"
    print!("\x1b]2;Anki Launcher\x07");

    Ok(())
}

#[cfg(not(target_os = "macos"))]
pub fn relaunch_in_terminal() -> Result<()> {
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;

    // Try terminals in order of preference
    let terminals = [
        ("x-terminal-emulator", vec!["-e"]),
        ("gnome-terminal", vec!["--"]),
        ("konsole", vec!["-e"]),
        ("xfce4-terminal", vec!["-e"]),
        ("alacritty", vec!["-e"]),
        ("kitty", vec![]),
        ("foot", vec![]),
        ("urxvt", vec!["-e"]),
        ("xterm", vec!["-e"]),
    ];

    for (terminal_cmd, args) in &terminals {
        // Check if terminal exists
        if Command::new("which")
            .arg(terminal_cmd)
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
        {
            // Try to launch the terminal
            let mut cmd = Command::new(terminal_cmd);
            if args.is_empty() {
                cmd.arg(&current_exe);
            } else {
                cmd.args(args).arg(&current_exe);
            }

            if cmd.spawn().is_ok() {
                std::process::exit(0);
            }
        }
    }

    // If no terminal worked, continue without relaunching
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
