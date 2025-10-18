// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(all(unix, not(target_os = "macos")))]
pub mod unix;

// #[cfg(target_os = "macos")]
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

pub fn respawn_launcher() -> Result<()> {
    use std::process::Stdio;

    let mut launcher_cmd = if cfg!(target_os = "macos") {
        // On macOS, we need to launch the .app bundle, not the executable directly
        let current_exe =
            std::env::current_exe().context("Failed to get current executable path")?;

        // Navigate from Contents/MacOS/launcher to the .app bundle
        let app_bundle = current_exe
            .parent() // MacOS
            .and_then(|p| p.parent()) // Contents
            .and_then(|p| p.parent()) // .app
            .context("Failed to find .app bundle")?;

        let mut cmd = std::process::Command::new("open");
        cmd.arg(app_bundle);
        cmd
    } else {
        let current_exe =
            std::env::current_exe().context("Failed to get current executable path")?;
        std::process::Command::new(current_exe)
    };

    launcher_cmd
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    // TODO: remove
    launcher_cmd.env("ANKI_LAUNCHER_SKIP", "1");

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const DETACHED_PROCESS: u32 = 0x00000008;
        launcher_cmd.creation_flags(CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS);
    }

    #[cfg(all(unix, not(target_os = "macos")))]
    {
        use std::os::unix::process::CommandExt;
        launcher_cmd.process_group(0);
    }

    let child = launcher_cmd.ensure_spawn()?;
    std::mem::forget(child);

    Ok(())
}

pub fn launch_anki_normally(mut cmd: std::process::Command) -> Result<()> {
    #[cfg(windows)]
    {
        crate::platform::windows::prepare_to_launch_normally();
        cmd.ensure_spawn()?;
    }
    #[cfg(unix)]
    cmd.ensure_exec()?;
    Ok(())
}

pub fn ensure_os_supported() -> Result<()> {
    #[cfg(all(unix, not(target_os = "macos")))]
    unix::ensure_glibc_supported()?;

    #[cfg(target_os = "windows")]
    windows::ensure_windows_version_supported()?;

    Ok(())
}
