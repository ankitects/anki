// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io;
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use anki_process::CommandExt as AnkiCommandExt;
use anyhow::Context;
use anyhow::Result;

pub fn prepare_for_launch_after_update(mut cmd: Command, root: &Path) -> Result<()> {
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

    let _ = cmd
        .env("ANKI_FIRST_RUN", "1")
        .arg("--version")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .ensure_success();

    if cfg!(target_os = "macos") {
        // older Anki versions had a short mpv timeout and didn't support
        // ANKI_FIRST_RUN, so we need to ensure mpv passes Gatekeeper
        // validation prior to launch
        let mpv_path = root.join(".venv/lib/python3.9/site-packages/anki_audio/mpv");
        if mpv_path.exists() {
            let _ = Command::new(&mpv_path)
                .arg("--version")
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .ensure_success();
        }
    }

    // Stop progress indicator
    running.store(false, Ordering::Relaxed);
    progress_thread.join().unwrap();
    println!(); // New line after dots
    Ok(())
}

pub fn relaunch_in_terminal() -> Result<()> {
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;
    Command::new("open")
        .args(["-a", "Terminal"])
        .arg(current_exe)
        .ensure_spawn()?;
    std::process::exit(0);
}
