// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use anyhow::Context;
use anyhow::Result;
use winapi::um::wincon;

pub fn ensure_terminal_shown() -> Result<()> {
    unsafe {
        if !wincon::GetConsoleWindow().is_null() {
            // We already have a console, no need to spawn anki-console.exe
            return Ok(());
        }
    }

    if std::env::var("ANKI_IMPLICIT_CONSOLE").is_ok() && attach_to_parent_console() {
        // Successfully attached to parent console
        reconnect_stdio_to_console();
        return Ok(());
    }

    // No console available, spawn anki-console.exe and exit
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;
    let exe_dir = current_exe
        .parent()
        .context("Failed to get executable directory")?;

    let console_exe = exe_dir.join("anki-console.exe");

    if !console_exe.exists() {
        anyhow::bail!("anki-console.exe not found in the same directory");
    }

    // Spawn anki-console.exe without waiting
    Command::new(&console_exe)
        .env("ANKI_IMPLICIT_CONSOLE", "1")
        .spawn()
        .context("Failed to spawn anki-console.exe")?;

    // Exit immediately after spawning
    std::process::exit(0);
}

pub fn attach_to_parent_console() -> bool {
    unsafe {
        if !wincon::GetConsoleWindow().is_null() {
            // we have a console already
            return false;
        }

        if wincon::AttachConsole(wincon::ATTACH_PARENT_PROCESS) != 0 {
            // successfully attached to parent
            reconnect_stdio_to_console();
            true
        } else {
            false
        }
    }
}

/// Reconnect stdin/stdout/stderr to the console.
fn reconnect_stdio_to_console() {
    use std::ffi::CString;

    use libc_stdhandle::*;

    // we launched without a console, so we'll need to open stdin/out/err
    let conin = CString::new("CONIN$").unwrap();
    let conout = CString::new("CONOUT$").unwrap();
    let r = CString::new("r").unwrap();
    let w = CString::new("w").unwrap();

    // Python uses the CRT for I/O, and it requires the descriptors are reopened.
    unsafe {
        libc::freopen(conin.as_ptr(), r.as_ptr(), stdin());
        libc::freopen(conout.as_ptr(), w.as_ptr(), stdout());
        libc::freopen(conout.as_ptr(), w.as_ptr(), stderr());
    }
}
