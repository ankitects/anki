// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::os::unix::process::CommandExt;
use std::process::Command;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::thread;
use std::time::Duration;

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

    println!("Anki will start shortly.");
    println!("\x1B[1mYou can close this window.\x1B[0m\n");
    Ok(())
}

pub fn ensure_terminal_shown() -> Result<()> {
    let stdout_is_terminal = std::io::IsTerminal::is_terminal(&std::io::stdout());
    if !stdout_is_terminal {
        // If launched from GUI, relaunch in Terminal.app
        relaunch_in_terminal()?;
    }

    // Set terminal title to "Anki Launcher"
    print!("\x1b]0;Anki Launcher\x07");

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
    use std::io::Write;
    use std::io::{
        self,
    };

    // Pre-validate by running --version to trigger any Gatekeeper checks
    print!("\n\x1B[1mThis may take a few minutes. Please wait\x1B[0m");
    io::stdout().flush().unwrap();

    // Start progress indicator
    let running = Arc::new(AtomicBool::new(true));
    let running_clone = running.clone();
    let progress_thread = thread::spawn(move || {
        while running_clone.load(Ordering::Relaxed) {
            print!(".");
            io::stdout().flush().unwrap();
            thread::sleep(Duration::from_secs(1));
        }
    });

    let _ = Command::new(anki_bin)
        .env("ANKI_FIRST_RUN", "1")
        .arg("--version")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .ensure_success();

    // Stop progress indicator
    running.store(false, Ordering::Relaxed);
    progress_thread.join().unwrap();
    println!(); // New line after dots
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
