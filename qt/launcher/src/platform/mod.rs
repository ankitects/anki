// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(all(unix, not(target_os = "macos")))]
mod unix;

#[cfg(target_os = "macos")]
pub mod mac;

#[cfg(target_os = "windows")]
pub mod windows;

use std::path::PathBuf;

use anki_process::CommandExt;
use anyhow::Context;
use anyhow::Result;

pub fn get_exe_and_resources_dirs() -> Result<(PathBuf, PathBuf)> {
    let exe_dir = std::env::current_exe()
        .context("Failed to get current executable path")?
        .parent()
        .context("Failed to get executable directory")?
        .to_owned();

    let resources_dir = if cfg!(target_os = "macos") {
        // On macOS, resources are in ../Resources relative to the executable
        exe_dir
            .parent()
            .context("Failed to get parent directory")?
            .join("Resources")
    } else {
        // On other platforms, resources are in the same directory as executable
        exe_dir.clone()
    };

    Ok((exe_dir, resources_dir))
}

pub fn get_uv_binary_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "uv.exe"
    } else if cfg!(target_os = "macos") {
        "uv"
    } else if cfg!(target_arch = "x86_64") {
        "uv.amd64"
    } else {
        "uv.arm64"
    }
}

pub fn launch_anki_after_update(mut cmd: std::process::Command) -> Result<()> {
    use std::process::Stdio;

    cmd.stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const DETACHED_PROCESS: u32 = 0x00000008;
        cmd.creation_flags(CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS);
    }

    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        cmd.process_group(0);
    }

    let child = cmd.ensure_spawn()?;
    std::mem::forget(child);

    Ok(())
}

pub fn launch_anki_normally(mut cmd: std::process::Command) -> Result<()> {
    #[cfg(windows)]
    {
        crate::platform::windows::attach_to_parent_console();
        cmd.ensure_success()?;
    }
    #[cfg(unix)]
    cmd.ensure_exec()?;
    Ok(())
}

#[cfg(windows)]
pub use windows::ensure_terminal_shown;

#[cfg(unix)]
pub fn ensure_terminal_shown() -> Result<()> {
    use std::io::IsTerminal;

    let stdout_is_terminal = IsTerminal::is_terminal(&std::io::stdout());
    if !stdout_is_terminal {
        #[cfg(target_os = "macos")]
        mac::relaunch_in_terminal()?;
        #[cfg(not(target_os = "macos"))]
        unix::relaunch_in_terminal()?;
    }

    // Set terminal title to "Anki Launcher"
    print!("\x1b]2;Anki Launcher\x07");
    Ok(())
}
