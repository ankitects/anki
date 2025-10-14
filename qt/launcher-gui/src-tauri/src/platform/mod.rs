// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(all(unix, not(target_os = "macos")))]
pub mod unix;

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

pub fn launch_anki_normally(mut cmd: std::process::Command) -> Result<()> {
    #[cfg(windows)]
    {
        crate::platform::windows::prepare_to_launch_normally();
        cmd.ensure_spawn()?;
    }
    #[cfg(unix)]
    cmd.ensure_spawn()?;
    Ok(())
}

pub fn ensure_os_supported() -> Result<()> {
    #[cfg(all(unix, not(target_os = "macos")))]
    unix::ensure_glibc_supported()?;

    #[cfg(target_os = "windows")]
    windows::ensure_windows_version_supported()?;

    Ok(())
}
